package com.example.spacescict;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class ReservationActivity extends AppCompatActivity {

    TextView datePicker, startTime, endTime;
    Spinner purposeSpinner, attendeesSpinner, floorSpinner;
    LinearLayout attendeesLayout;
    GridLayout roomGrid;

    Map<String, String[]> rooms = new HashMap<>();
    String selectedRoom = null; // 🔥 tracks tapped room

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

            attendeesLayout = findViewById(R.id.attendeesLayout);
            roomGrid = findViewById(R.id.roomGrid);

            Button submit = findViewById(R.id.submitBtn);

            if (datePicker == null || startTime == null || endTime == null) {
                throw new RuntimeException("Missing ID in XML layout");
            }

            setupSpinners();
            setupPickers();
            setupRooms();

            backButton.setOnClickListener(v -> {
                Intent intent = new Intent(ReservationActivity.this, DashboardActivity.class);
                startActivity(intent);
                finish();
            });

            submit.setOnClickListener(v -> {
                if (selectedRoom == null) {
                    Toast.makeText(this, "Please select a room", Toast.LENGTH_SHORT).show();
                    return;
                }

                ConfirmDialog.show(
                        this,
                        "Confirmation",
                        " Are you sure you want to reserve?",
                        "Confirm",
                        "Cancel",
                        () -> submitReservation()
                );
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================= SUBMIT =================
    void submitReservation() {
        String uid = FirebaseAuth.getInstance().getUid();

        Map<String, Object> reservation = new HashMap<>();
        reservation.put("userId", uid);
        reservation.put("room", selectedRoom);
        reservation.put("subject", purposeSpinner.getSelectedItem().toString()); // ⚠️ swap for a real subject field if you add one
        reservation.put("status", "PENDING");
        reservation.put("date", datePicker.getText().toString());
        reservation.put("startTime", startTime.getText().toString());
        reservation.put("endTime", endTime.getText().toString());
        reservation.put("purpose", purposeSpinner.getSelectedItem().toString());

        FirebaseFirestore.getInstance()
                .collection("reservationRequests") // 🔥 matches your web rules
                .add(reservation)
                .addOnSuccessListener(ref -> finish())
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ================= DATE + TIME =================
    void setupPickers() {

        datePicker.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();

            DatePickerDialog dp = new DatePickerDialog(this,
                    (view, year, month, day) -> {
                        datePicker.setText(day + "/" + (month + 1) + "/" + year);
                    },
                    c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)
            );

            dp.getDatePicker().setMinDate(System.currentTimeMillis());
            dp.show();
        });

        startTime.setOnClickListener(v -> showTime(startTime));
        endTime.setOnClickListener(v -> showTime(endTime));
    }

    void showTime(TextView target) {
        TimePickerDialog tp = new TimePickerDialog(this,
                (view, hour, minute) -> {
                    target.setText(String.format("%02d:%02d", hour, minute));
                }, 12, 0, false);
        tp.show();
    }

    // ================= SPINNERS =================
    void setupSpinners() {

        ArrayAdapter<String> purposeAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Exam", "Hands On", "Lecture"}
        );
        purposeSpinner.setAdapter(purposeAdapter);

        purposeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = parent.getItemAtPosition(pos).toString();
                attendeesLayout.setVisibility(selected.equals("Lecture") ? View.VISIBLE : View.GONE);
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        attendeesSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"10-30", "30-50", "50-70"}
        ));

        floorSpinner.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"1st Floor", "3rd Floor", "4th Floor"}
        ));

        floorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                loadRooms(parent.getItemAtPosition(pos).toString());
            }
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ================= ROOMS =================
    void setupRooms() {
        rooms.put("1st Floor", new String[]{"A1","A2","A3","A4","IT13","IT14"});
        rooms.put("3rd Floor", new String[]{"IT1","IT2","SDL1","SDL2","SDL3","SDL4","Smart Prog Lab 1","Smart Prog Lab 2","Smart Prog Lab 3"});
        rooms.put("4th Floor", new String[]{"CT6","CT7","CT8","ACAD1","AVR","CISCO LAB1","CISCO LAB2"});
    }

    void loadRooms(String floor) {

        roomGrid.removeAllViews();
        selectedRoom = null; // reset when floor changes

        for (String room : rooms.get(floor)) {

            TextView btn = new TextView(this);
            btn.setText(room);
            btn.setPadding(20,20,20,20);
            btn.setBackgroundResource(R.drawable.room_available);
            btn.setGravity(Gravity.CENTER);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.setMargins(10,10,10,10);
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);

            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                selectedRoom = room;
                for (int i = 0; i < roomGrid.getChildCount(); i++) {
                    roomGrid.getChildAt(i).setBackgroundResource(R.drawable.room_available);
                }
                btn.setBackgroundResource(R.drawable.room_selected);
            });

            roomGrid.addView(btn);
        }
    }
}