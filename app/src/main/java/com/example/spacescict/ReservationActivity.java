package com.example.spacescict;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReservationActivity extends AppCompatActivity {

    TextView datePicker, startTime, endTime;
    Spinner purposeSpinner, attendeesSpinner, floorSpinner, audienceTypeSpinner;
    LinearLayout attendeesLayout, classFieldsLayout, orgFieldLayout, customPurposeLayout;
    GridLayout roomGrid;
    EditText courseInput, programInput, yearSectionInput, orgInput, customPurposeInput;
    CheckBox eqAc, eqComputer, eqProjector, eqSmartBoard, eqTvDisplay;
    LinearLayout equipmentLayout;

    String selectedRoomId = null;
    String selectedRoomName = null;
    String facultyName = "";

    static final String[] CLASS_PURPOSES = {"Lecture", "Hands-on", "Examination"};
    static final String[] ORG_PURPOSES = {"Workshop", "Training", "Meeting", "Other Activity"};
    static final String[] CLASS_STUDENT_RANGES = {"30-50", "50-60", "60-80", "80-100"};
    static final String[] ORG_STUDENT_RANGES = {"1-30", "31-50", "51-80", "81-100", "101+"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reservation);

        try {
            ImageView backButton = findViewById(R.id.backBtn);

            datePicker = findViewById(R.id.dateText);
            startTime = findViewById(R.id.startText);
            endTime = findViewById(R.id.endText);

            purposeSpinner = findViewById(R.id.purposeSpinner);
            attendeesSpinner = findViewById(R.id.attendeesSpinner);
            floorSpinner = findViewById(R.id.floorSpinner);
            audienceTypeSpinner = findViewById(R.id.audienceTypeSpinner);

            attendeesLayout = findViewById(R.id.attendeesLayout);
            classFieldsLayout = findViewById(R.id.classFieldsLayout);
            orgFieldLayout = findViewById(R.id.orgFieldLayout);
            customPurposeLayout = findViewById(R.id.customPurposeLayout);
            equipmentLayout = findViewById(R.id.equipmentLayout);

            roomGrid = findViewById(R.id.roomGrid);

            courseInput = findViewById(R.id.courseInput);
            programInput = findViewById(R.id.programInput);
            yearSectionInput = findViewById(R.id.yearSectionInput);
            orgInput = findViewById(R.id.orgInput);
            customPurposeInput = findViewById(R.id.customPurposeInput);

            eqAc = findViewById(R.id.eqAc);
            eqComputer = findViewById(R.id.eqComputer);
            eqProjector = findViewById(R.id.eqProjector);
            eqSmartBoard = findViewById(R.id.eqSmartBoard);
            eqTvDisplay = findViewById(R.id.eqTvDisplay);

            Button submit = findViewById(R.id.submitBtn);

            loadFacultyName();
            setupSpinners();
            setupPickers();

            backButton.setOnClickListener(v -> {
                startActivity(new Intent(ReservationActivity.this, DashboardActivity.class));
                finish();
            });

            submit.setOnClickListener(v -> {
                LoadingOverlay.show(this, "Checking availability...");
                checkUserConflict((conflict, message) -> {
                    LoadingOverlay.hide();

                    if (conflict) {
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                        return;
                    }
                    String error = validate();
                    if (error != null) {
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ConfirmDialog.show(this, "Confirmation", " Are you sure you want to reserve?",
                            "Confirm", "Cancel", this::submitReservation);
                });
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void loadFacultyName() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String first = doc.getString("firstName");
                    String last = doc.getString("lastName");
                    facultyName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                });
    }

    String validate() {
        String audienceType = (String) audienceTypeSpinner.getSelectedItem();
        String purpose = (String) purposeSpinner.getSelectedItem();

        if (courseInput.getText().toString().trim().isEmpty()) return "Course title is required.";
        if (datePicker.getText().toString().equals("Select Date")) return "Select a reservation date.";
        if (audienceType == null) return "Select audience type.";

        if (audienceType.equals("Class")) {
            if (programInput.getText().toString().trim().isEmpty()) return "Enter course/program.";
            if (yearSectionInput.getText().toString().trim().isEmpty()) return "Enter Year / Section Group.";
            if (purpose.equals("Hands-on") && !anyEquipmentChecked()) {
                return "Select at least one required equipment.";
            }
            if ((purpose.equals("Lecture") || purpose.equals("Examination"))
                    && attendeesSpinner.getSelectedItem() == null) {
                return "Select the estimated number of students.";
            }
        }

        if (audienceType.equals("Organization")) {
            if (orgInput.getText().toString().trim().isEmpty()) return "Enter organization name.";
            if (purpose.equals("Other Activity") && customPurposeInput.getText().toString().trim().isEmpty()) {
                return "Please specify the activity.";
            }
            if (attendeesSpinner.getSelectedItem() == null) {
                return "Select the estimated number of attendees.";
            }
        }

        if (startTime.getText().toString().equals("Select Start Time")) return "Select a start time.";
        if (endTime.getText().toString().equals("Select End Time")) return "Select an end time.";
        if (selectedRoomId == null) return "Select an available room.";

        return null;
    }

    boolean anyEquipmentChecked() {
        return eqAc.isChecked() || eqComputer.isChecked() || eqProjector.isChecked()
                || eqSmartBoard.isChecked() || eqTvDisplay.isChecked();
    }

    void submitReservation() {
        LoadingOverlay.show(this, "Submitting reservation...");
        String uid = FirebaseAuth.getInstance().getUid();
        String audienceType = (String) audienceTypeSpinner.getSelectedItem();
        String purpose = (String) purposeSpinner.getSelectedItem();

        List<String> equipment = new ArrayList<>();
        if (eqProjector.isChecked()) equipment.add("projector");
        if (eqTvDisplay.isChecked()) equipment.add("tvDisplay");
        if (eqAc.isChecked()) equipment.add("ac");
        if (eqComputer.isChecked()) equipment.add("computer");
        if (eqSmartBoard.isChecked()) equipment.add("smartBoard");

        String finalPurpose = purpose;
        String customPurpose = "";
        if ("Other Activity".equals(purpose)) {
            customPurpose = customPurposeInput.getText().toString().trim();
            finalPurpose = customPurpose.isEmpty() ? "Other Activity" : customPurpose;
        }

        Map<String, Object> attendees = new HashMap<>();
        attendees.put("course", audienceType.equals("Class") ? programInput.getText().toString().trim() : "");
        attendees.put("yearSectionGroup", audienceType.equals("Class") ? yearSectionInput.getText().toString().trim() : "");
        attendees.put("organization", audienceType.equals("Organization") ? orgInput.getText().toString().trim() : "");
        attendees.put("customPurpose", customPurpose);

        Map<String, Object> reservation = new HashMap<>();
        reservation.put("userId", uid);
        reservation.put("facultyName", facultyName);
        reservation.put("roomId", selectedRoomId);
        reservation.put("roomName", selectedRoomName);
        reservation.put("audienceType", audienceType);
        reservation.put("attendees", attendees);
        reservation.put("courseTitle", courseInput.getText().toString().trim());
        reservation.put("purpose", finalPurpose);
        reservation.put("requiredEquipment", equipment);
        reservation.put("studentRange", attendeesSpinner.getSelectedItem() != null
                ? attendeesSpinner.getSelectedItem().toString() : "");
        reservation.put("date", datePicker.getText().toString());
        reservation.put("startTime", startTime.getText().toString());
        reservation.put("endTime", endTime.getText().toString());
        reservation.put("status", "Pending");
        reservation.put("createdAt", Timestamp.now());

        String finalFinalPurpose = finalPurpose;
        FirebaseFirestore.getInstance()
                .collection("reservationRequests")
                .add(reservation)
                .addOnSuccessListener(ref -> {
                    LoadingOverlay.hide();
                    NotificationHelper.notifyClerkAndDepartmentHead(
                            "New Reservation Request",
                            facultyName + " submitted a reservation request for \"" + courseInput.getText().toString().trim()
                                    + "\" in " + selectedRoomName + " on " + datePicker.getText() + ".",
                            ref.getId(), "reservation-request");

                    NotificationHelper.send(uid, "faculty", "Reservation Submitted",
                            "Your reservation request for " + selectedRoomName + " on " + datePicker.getText()
                                    + " (" + startTime.getText() + " - " + endTime.getText() + ") has been submitted successfully and is waiting for approval.",
                            "reservation-submitted", "INFO");

                    Map<String, Object> details = new HashMap<>();
                    details.put("courseTitle", courseInput.getText().toString().trim());
                    details.put("room", selectedRoomName);
                    details.put("date", datePicker.getText().toString());
                    details.put("startTime", startTime.getText().toString());
                    details.put("endTime", endTime.getText().toString());
                    details.put("purpose", finalFinalPurpose);
                    details.put("audienceType", audienceType);

                    ActivityLogger.log("Submitted Reservation Request", "success",
                            selectedRoomName + " | " + courseInput.getText().toString().trim(), "SUCCESS",
                            details, this::finish);

                })
                .addOnFailureListener(e ->{
                        LoadingOverlay.hide();
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

    }

    void setupPickers() {
        datePicker.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            DatePickerDialog dp = new DatePickerDialog(this,
                    (view, year, month, day) -> {
                        datePicker.setText(String.format("%04d-%02d-%02d", year, month + 1, day));
                        refreshRooms();
                    },
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
            dp.getDatePicker().setMinDate(System.currentTimeMillis());
            dp.show();
        });

        startTime.setOnClickListener(v -> showTime(startTime, true));
        endTime.setOnClickListener(v -> showTime(endTime, false));
    }

    void showTime(TextView target, boolean isStart) {
        TimePickerDialog tp = new TimePickerDialog(this,
                (view, hour, minute) -> {
                    target.setText(String.format("%02d:%02d", hour, minute));
                    refreshRooms();
                },
                12, 0, true);
        tp.show();
    }
    void showTime(TextView target) {
        TimePickerDialog tp = new TimePickerDialog(this,
                (view, hour, minute) -> target.setText(String.format("%02d:%02d", hour, minute)),
                12, 0, true);
        tp.show();
    }

    void setupSpinners() {

        audienceTypeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Class", "Organization"}));

        audienceTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = parent.getItemAtPosition(pos).toString();
                boolean isClass = selected.equals("Class");

                classFieldsLayout.setVisibility(isClass ? View.VISIBLE : View.GONE);
                orgFieldLayout.setVisibility(isClass ? View.GONE : View.VISIBLE);

                purposeSpinner.setAdapter(new ArrayAdapter<>(ReservationActivity.this,
                        android.R.layout.simple_spinner_dropdown_item,
                        isClass ? CLASS_PURPOSES : ORG_PURPOSES));

                customPurposeLayout.setVisibility(View.GONE);
                equipmentLayout.setVisibility(View.GONE);
                attendeesLayout.setVisibility(View.GONE);
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        purposeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String purpose = parent.getItemAtPosition(pos).toString();
                String audienceType = (String) audienceTypeSpinner.getSelectedItem();
                boolean isClass = "Class".equals(audienceType);

                customPurposeLayout.setVisibility(purpose.equals("Other Activity") ? View.VISIBLE : View.GONE);
                equipmentLayout.setVisibility(isClass && purpose.equals("Hands-on") ? View.VISIBLE : View.GONE);

                boolean needsAttendees = isClass
                        ? (purpose.equals("Lecture") || purpose.equals("Examination"))
                        : true;

                attendeesLayout.setVisibility(needsAttendees ? View.VISIBLE : View.GONE);
                if (needsAttendees) {
                    attendeesSpinner.setAdapter(new ArrayAdapter<>(ReservationActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            isClass ? CLASS_STUDENT_RANGES : ORG_STUDENT_RANGES));
                }
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        floorSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"1st", "3rd", "4th"}));

        floorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                refreshRooms();
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });


    }

    interface ConflictCallback {
        void onResult(boolean conflict, String message);
    }

    void checkUserConflict(ConflictCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        String date = datePicker.getText().toString();

        FirebaseFirestore.getInstance().collection("reservationRequests")
                .whereEqualTo("userId", uid)
                .whereEqualTo("date", date)
                .get()
                .addOnSuccessListener(snap -> {

                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        String status = doc.getString("status");
                        if (status != null && status.equalsIgnoreCase("Rejected")) continue;

                        String docRoomId = doc.getString("roomId");
                        if (selectedRoomId != null && selectedRoomId.equals(docRoomId)) continue;

                        if (RoomAvailability.overlap(startTime.getText().toString(), endTime.getText().toString(),
                                doc.getString("startTime"), doc.getString("endTime"))) {
                            String msg = "You already have a " + (status != null ? status.toLowerCase() : "")
                                    + " reservation for \"" + doc.getString("roomName") + "\" on " + doc.getString("date")
                                    + " from " + doc.getString("startTime") + " to " + doc.getString("endTime")
                                    + ". Please choose a different time.";
                            callback.onResult(true, msg);
                            return;
                        }
                    }
                    callback.onResult(false, null);
                })
                .addOnFailureListener(e -> callback.onResult(false, null));
    }
    String currentDate = null, currentStart = null, currentEnd = null;

    void refreshRooms() {
        String floor = (String) floorSpinner.getSelectedItem();
        if (floor == null) return;
        currentDate = datePicker.getText().toString();
        currentStart = startTime.getText().toString();
        currentEnd = endTime.getText().toString();

        if (currentDate.equals("Select Date") || currentStart.equals("Select Start Time") || currentEnd.equals("Select End Time")) {
            roomGrid.removeAllViews();
            TextView hint = new TextView(this);
            hint.setText("Select a date and time first to see room availability.");
            hint.setPadding(20, 20, 20, 20);
            hint.setTextColor(Color.parseColor("#6B7280"));
            roomGrid.addView(hint);
            return;
        }

        loadRoomsForFloor(floor);
    }

    void loadRoomsForFloor(String floorFilter) {
        roomGrid.removeAllViews();
        selectedRoomId = null;
        selectedRoomName = null;

        String uid = FirebaseAuth.getInstance().getUid();

        RoomAvailability.loadAvailabilityForSlot(currentDate, currentStart, currentEnd, uid, results -> {
            for (RoomAvailability.RoomSlotStatus rs : results) {
                if (rs.floor == null || !rs.floor.toLowerCase().contains(floorFilter.toLowerCase())) continue;

                boolean available = rs.status.equals("Available");
                boolean reserved = rs.status.equals("Reserved");

                TextView btn = new TextView(this);
                String label = rs.roomName + (reserved ? " (Reserved)" : rs.status.equals("Occupied") ? " (Occupied)" : rs.status.equals("Maintenance") ? " (Maintenance)" : "");
                btn.setText(label);
                btn.setPadding(20, 20, 20, 20);
                btn.setGravity(Gravity.CENTER);
                btn.setEnabled(available);

                if (available) {
                    btn.setBackgroundResource(R.drawable.room_available);
                } else if (reserved) {
                    btn.setBackgroundColor(Color.parseColor("#FEF3C7"));
                    btn.setTextColor(Color.parseColor("#92400E"));
                } else {
                    btn.setBackgroundResource(R.drawable.status_denied);
                }

                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.setMargins(10, 10, 10, 10);
                params.width = 0;
                params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
                btn.setLayoutParams(params);

                if (available) {
                    btn.setOnClickListener(v -> {
                        selectedRoomId = rs.id;
                        selectedRoomName = rs.roomName;
                        for (int i = 0; i < roomGrid.getChildCount(); i++) {
                            View child = roomGrid.getChildAt(i);
                            if (child instanceof TextView && ((TextView) child).isEnabled()) {
                                child.setBackgroundResource(R.drawable.room_available);
                            }
                        }
                        btn.setBackgroundResource(R.drawable.room_selected);
                    });
                }

                roomGrid.addView(btn);
            }
        });
    }
}