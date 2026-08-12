package com.example.spacescict;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReservationDetailActivity extends AppCompatActivity {

    public static final String EXTRA_RESERVATION_ID = "reservationId";

    static final String[] CLASS_PURPOSES = {"Lecture", "Hands-on", "Examination"};
    static final String[] ORG_PURPOSES = {"Workshop", "Training", "Meeting", "Other Activity"};
    static final String[] CLASS_STUDENT_RANGES = {"30-50", "50-60", "60-80", "80-100"};
    static final String[] ORG_STUDENT_RANGES = {"1-30", "31-50", "51-80", "81-100", "101+"};

    static final Map<String, String> EQUIPMENT_LABELS = new HashMap<>();
    static {
        EQUIPMENT_LABELS.put("projector", "Projector");
        EQUIPMENT_LABELS.put("tvDisplay", "TV Display");
        EQUIPMENT_LABELS.put("ac", "AC");
        EQUIPMENT_LABELS.put("computer", "Computer");
        EQUIPMENT_LABELS.put("smartBoard", "Smart Board");
    }

    TextView statusBadge, requesterText, coursePurposeText, roomScheduleText, audienceText,
            equipmentCapacityText, denialReasonLabel, denialReasonText, requestedOnText, editBtn,
            editDateText, editStartText, editEndText, roomAvailabilityHint;
    View viewLayout, editLayout, editCustomPurposeLayout;
    Spinner editRoomSpinner, editPurposeSpinner, editStudentRangeSpinner;
    EditText editCustomPurposeInput;
    Button cancelEditBtn, saveEditBtn;

    String reservationId;
    Map<String, Object> reservation;
    String audienceType = "";
    List<DocumentSnapshot> allRooms = new ArrayList<>();
    boolean editing = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservation_detail);

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        statusBadge = findViewById(R.id.statusBadge);
        requesterText = findViewById(R.id.requesterText);
        coursePurposeText = findViewById(R.id.coursePurposeText);
        roomScheduleText = findViewById(R.id.roomScheduleText);
        audienceText = findViewById(R.id.audienceText);
        equipmentCapacityText = findViewById(R.id.equipmentCapacityText);
        denialReasonLabel = findViewById(R.id.denialReasonLabel);
        denialReasonText = findViewById(R.id.denialReasonText);
        requestedOnText = findViewById(R.id.requestedOnText);
        editBtn = findViewById(R.id.editBtn);
        viewLayout = findViewById(R.id.viewLayout);
        editLayout = findViewById(R.id.editLayout);
        editCustomPurposeLayout = findViewById(R.id.editCustomPurposeLayout);
        editCustomPurposeInput = findViewById(R.id.editCustomPurposeInput);
        editDateText = findViewById(R.id.editDateText);
        editStartText = findViewById(R.id.editStartText);
        editEndText = findViewById(R.id.editEndText);
        editRoomSpinner = findViewById(R.id.editRoomSpinner);
        editPurposeSpinner = findViewById(R.id.editPurposeSpinner);
        editStudentRangeSpinner = findViewById(R.id.editStudentRangeSpinner);
        roomAvailabilityHint = findViewById(R.id.roomAvailabilityHint);
        cancelEditBtn = findViewById(R.id.cancelEditBtn);
        saveEditBtn = findViewById(R.id.saveEditBtn);

        reservationId = getIntent().getStringExtra(EXTRA_RESERVATION_ID);
        if (reservationId == null) {
            finish();
            return;
        }

        loadReservation();

        editBtn.setOnClickListener(v -> enterEditMode());
        cancelEditBtn.setOnClickListener(v -> exitEditMode());
        editDateText.setOnClickListener(v -> pickDate());
        editStartText.setOnClickListener(v -> pickTime(editStartText));
        editEndText.setOnClickListener(v -> pickTime(editEndText));
        saveEditBtn.setOnClickListener(v -> saveChanges());
    }

    void loadReservation() {
        FirebaseFirestore.getInstance().collection("reservationRequests").document(reservationId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Reservation not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    reservation = doc.getData();
                    audienceType = str(reservation.get("audienceType"));
                    renderView();
                });
    }

    @SuppressWarnings("unchecked")
    void renderView() {
        String status = str(reservation.get("status"));
        statusBadge.setText(status != null ? status.toUpperCase() : "");

        if ("Pending".equalsIgnoreCase(status)) {
            editBtn.setVisibility(View.VISIBLE);
        }

        requesterText.setText(str(reservation.get("facultyName")));

        String purpose = str(reservation.get("purpose"));
        Object attendeesObj = reservation.get("attendees");
        Map<String, Object> attendees = attendeesObj instanceof Map ? (Map<String, Object>) attendeesObj : new HashMap<>();

        StringBuilder coursePurpose = new StringBuilder("Course Title: " + str(reservation.get("courseTitle"))
                + "\nPurpose: " + purpose);
        String customPurpose = str(attendees.get("customPurpose"));
        if (!customPurpose.isEmpty()) {
            coursePurpose.append("\nSpecified Activity: ").append(customPurpose);
        }
        coursePurposeText.setText(coursePurpose.toString());

        roomScheduleText.setText("Room: " + str(reservation.get("roomName"))
                + "\nDate: " + str(reservation.get("date"))
                + "\nTime: " + str(reservation.get("startTime")) + " – " + str(reservation.get("endTime")));

        StringBuilder audience = new StringBuilder("Type: " + audienceType);
        if ("Class".equalsIgnoreCase(audienceType)) {
            audience.append("\nCourse: ").append(str(attendees.get("course")));
            audience.append("\nYear/Section: ").append(str(attendees.get("yearSectionGroup")));
        } else if ("Organization".equalsIgnoreCase(audienceType)) {
            audience.append("\nOrganization: ").append(str(attendees.get("organization")));
        }
        audienceText.setText(audience.toString());

        List<String> equipmentRaw = (List<String>) reservation.get("requiredEquipment");
        String equipmentStr = "None";
        if (equipmentRaw != null && !equipmentRaw.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (String eq : equipmentRaw) {
                labels.add(EQUIPMENT_LABELS.getOrDefault(eq, eq));
            }
            equipmentStr = android.text.TextUtils.join(", ", labels);
        }
        equipmentCapacityText.setText("Equipment: " + equipmentStr
                + "\nEstimated Attendees: " + str(reservation.get("studentRange")));

        if ("Rejected".equalsIgnoreCase(status)) {
            denialReasonLabel.setVisibility(View.VISIBLE);
            denialReasonText.setVisibility(View.VISIBLE);
            String reason = str(reservation.get("denialReason"));
            denialReasonText.setText(reason.isEmpty() ? "No reason provided." : reason);
        }

        Timestamp createdAt = (Timestamp) reservation.get("createdAt");
        if (createdAt != null) {
            SimpleDateFormat fmt = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US);
            requestedOnText.setText(fmt.format(createdAt.toDate()));
        }
    }

    String str(Object o) {
        return o != null ? o.toString() : "";
    }

    void enterEditMode() {
        editing = true;
        viewLayout.setVisibility(View.GONE);
        editLayout.setVisibility(View.VISIBLE);
        editBtn.setVisibility(View.GONE);

        editDateText.setText(str(reservation.get("date")));
        editStartText.setText(str(reservation.get("startTime")));
        editEndText.setText(str(reservation.get("endTime")));

        boolean isClass = "Class".equalsIgnoreCase(audienceType);
        String[] purposes = isClass ? CLASS_PURPOSES : ORG_PURPOSES;
        String[] ranges = isClass ? CLASS_STUDENT_RANGES : ORG_STUDENT_RANGES;

        ArrayAdapter<String> purposeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, purposes);
        editPurposeSpinner.setAdapter(purposeAdapter);
        String currentPurpose = str(reservation.get("purpose"));
        int purposeIndex = 0;
        for (int i = 0; i < purposes.length; i++) if (purposes[i].equals(currentPurpose)) purposeIndex = i;
        editPurposeSpinner.setSelection(purposeIndex);

        editPurposeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = parent.getItemAtPosition(pos).toString();
                editCustomPurposeLayout.setVisibility("Other Activity".equals(selected) ? View.VISIBLE : View.GONE);
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        editCustomPurposeLayout.setVisibility("Other Activity".equals(currentPurpose) ? View.VISIBLE : View.GONE);

        Object attendeesObj = reservation.get("attendees");
        if (attendeesObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> attendees = (Map<String, Object>) attendeesObj;
            editCustomPurposeInput.setText(str(attendees.get("customPurpose")));
        }

        ArrayAdapter<String> rangeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, ranges);
        editStudentRangeSpinner.setAdapter(rangeAdapter);
        String currentRange = str(reservation.get("studentRange"));
        int rangeIndex = 0;
        for (int i = 0; i < ranges.length; i++) if (ranges[i].equals(currentRange)) rangeIndex = i;
        editStudentRangeSpinner.setSelection(rangeIndex);

        loadRoomsForEdit();
    }

    void exitEditMode() {
        editing = false;
        viewLayout.setVisibility(View.VISIBLE);
        editLayout.setVisibility(View.GONE);
        editBtn.setVisibility(View.VISIBLE);
    }

    void loadRoomsForEdit() {
        FirebaseFirestore.getInstance().collection("rooms").get().addOnSuccessListener(snap -> {
            allRooms = snap.getDocuments();
            List<String> names = new ArrayList<>();
            String currentRoom = str(reservation.get("roomName"));
            int selectedIndex = 0;
            for (int i = 0; i < allRooms.size(); i++) {
                String name = allRooms.get(i).getString("roomName");
                names.add(name);
                if (name != null && name.equals(currentRoom)) selectedIndex = i;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            editRoomSpinner.setAdapter(adapter);
            editRoomSpinner.setSelection(selectedIndex);
        });
    }

    void pickDate() {
        Calendar c = Calendar.getInstance();
        DatePickerDialog dp = new DatePickerDialog(this,
                (view, year, month, day) -> editDateText.setText(String.format("%04d-%02d-%02d", year, month + 1, day)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        dp.getDatePicker().setMinDate(System.currentTimeMillis());
        dp.show();
    }

    void pickTime(TextView target) {
        new TimePickerDialog(this,
                (view, hour, minute) -> target.setText(String.format("%02d:%02d", hour, minute)),
                12, 0, true).show();
    }

    void saveChanges() {
        int roomIndex = editRoomSpinner.getSelectedItemPosition();
        if (roomIndex < 0 || roomIndex >= allRooms.size()) {
            Toast.makeText(this, "Select a room", Toast.LENGTH_SHORT).show();
            return;
        }
        DocumentSnapshot roomDoc = allRooms.get(roomIndex);
        String roomId = roomDoc.getId();
        String roomName = roomDoc.getString("roomName");
        String date = editDateText.getText().toString();
        String startTime = editStartText.getText().toString();
        String endTime = editEndText.getText().toString();
        String purpose = (String) editPurposeSpinner.getSelectedItem();
        String studentRange = (String) editStudentRangeSpinner.getSelectedItem();
        String customPurpose = editCustomPurposeInput.getText().toString().trim();

        if ("Other Activity".equals(purpose) && customPurpose.isEmpty()) {
            Toast.makeText(this, "Please specify the activity", Toast.LENGTH_SHORT).show();
            return;
        }

        saveEditBtn.setEnabled(false);
        roomAvailabilityHint.setText("");

        RoomAvailability.checkAvailability(roomId, date, startTime, endTime, reservationId, (available, reason) -> {
            runOnUiThread(() -> {
                saveEditBtn.setEnabled(true);
                if (!available) {
                    roomAvailabilityHint.setText(reason);
                    return;
                }
                commitChanges(roomId, roomName, date, startTime, endTime, purpose, studentRange, customPurpose);
            });
        });
    }

    @SuppressWarnings("unchecked")
    void commitChanges(String roomId, String roomName, String date, String startTime,
                       String endTime, String purpose, String studentRange, String customPurpose) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("roomId", roomId);
        updates.put("roomName", roomName);
        updates.put("date", date);
        updates.put("startTime", startTime);
        updates.put("endTime", endTime);
        updates.put("purpose", purpose);
        updates.put("studentRange", studentRange);
        updates.put("updatedAt", Timestamp.now());

        Object attendeesObj = reservation.get("attendees");
        Map<String, Object> attendees = attendeesObj instanceof Map
                ? new HashMap<>((Map<String, Object>) attendeesObj) : new HashMap<>();
        attendees.put("customPurpose", "Other Activity".equals(purpose) ? customPurpose : "");
        updates.put("attendees", attendees);

        FirebaseFirestore.getInstance().collection("reservationRequests").document(reservationId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    String uid = FirebaseAuth.getInstance().getUid();
                    String courseTitle = str(reservation.get("courseTitle"));

                    NotificationHelper.send(uid, "faculty", "Reservation Updated",
                            "Your reservation for " + roomName + " on " + date + " ("
                                    + startTime + " - " + endTime + ") has been updated.",
                            "reservation-updated", "INFO");

                    Map<String, Object> details = new HashMap<>();
                    details.put("reservationId", reservationId);
                    ActivityLogger.log("Updated Pending Reservation", "edit",
                            roomName + " - " + courseTitle, "SUCCESS", details, null);

                    Toast.makeText(this, "Reservation updated", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}