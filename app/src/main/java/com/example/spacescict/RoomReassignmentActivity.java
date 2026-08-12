package com.example.spacescict;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RoomReassignmentActivity extends AppCompatActivity {

    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";

    String assignmentId;
    Map<String, Object> assignment;

    TextView infoText, oldRoomText, newRoomText, expiredText;
    View actionsLayout, rejectBtn, approveBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_reassignment);

        infoText = findViewById(R.id.infoText);
        oldRoomText = findViewById(R.id.oldRoomText);
        newRoomText = findViewById(R.id.newRoomText);
        expiredText = findViewById(R.id.expiredText);
        actionsLayout = findViewById(R.id.actionsLayout);
        rejectBtn = findViewById(R.id.rejectBtn);
        approveBtn = findViewById(R.id.approveBtn);

        assignmentId = getIntent().getStringExtra(EXTRA_ASSIGNMENT_ID);
        if (assignmentId == null) {
            Toast.makeText(this, "Missing reassignment reference", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        load();

        rejectBtn.setOnClickListener(v -> respond("rejected"));
        approveBtn.setOnClickListener(v -> respond("approved"));
    }

    void load() {
        FirebaseFirestore.getInstance().collection("roomReassignments").document(assignmentId).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        Toast.makeText(this, "Reassignment not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    assignment = doc.getData();
                    render();
                });
    }

    void render() {
        String courseTitle = str(assignment.get("courseTitle"));
        String section = str(assignment.get("section"));
        String date = str(assignment.get("date"));
        String startTime = str(assignment.get("startTime"));
        String endTime = str(assignment.get("endTime"));

        infoText.setText("Course: " + courseTitle
                + "\nSection: " + section
                + "\nDate: " + date
                + "\nTime: " + startTime + " - " + endTime);

        oldRoomText.setText(str(assignment.get("oldRoomName")));
        newRoomText.setText(str(assignment.get("newRoomName")));

        if (isExpired()) {
            actionsLayout.setVisibility(View.GONE);
            expiredText.setVisibility(View.VISIBLE);
        }
    }

    boolean isExpired() {
        if (assignment == null) return false;
        String date = str(assignment.get("date"));
        String endTime = str(assignment.get("endTime"));
        if (date.isEmpty() || endTime.isEmpty()) return false;
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US);
            java.util.Date scheduleEnd = fmt.parse(date + "T" + endTime);
            return new java.util.Date().after(scheduleEnd);
        } catch (Exception e) {
            return false;
        }
    }

    String str(Object o) {
        return o != null ? o.toString() : "";
    }

    void respond(String decision) {
        if (isExpired()) {
            Toast.makeText(this, "This room reassignment has already expired.", Toast.LENGTH_SHORT).show();
            return;
        }

        String status = decision.equals("approved") ? "approved" : "rejected";
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put(status.equals("approved") ? "approvedAt" : "rejectedAt", Timestamp.now());

        FirebaseFirestore.getInstance().collection("roomReassignments").document(assignmentId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    String eventId = (String) assignment.get("eventId");
                    if (eventId != null) {
                        FirebaseFirestore.getInstance().collection("events").document(eventId)
                                .update("conflictResolved", true);
                    }
                    sendDecisionNotifications(decision.equals("approved") ? "accepted" : "rejected");
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    void sendDecisionNotifications(String decisionWord) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String first = userDoc.getString("firstName");
                    String last = userDoc.getString("lastName");
                    String facultyName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                    String courseTitle = str(assignment.get("courseTitle"));

                    NotificationHelper.send(uid, "faculty", "Room Reassignment",
                            "You " + decisionWord + " the room reassignment for " + courseTitle + ".",
                            "approved", decisionWord.toUpperCase());

                    FirebaseFirestore.getInstance().collection("users")
                            .whereEqualTo("role", "Department Head").get()
                            .addOnSuccessListener(heads -> {
                                for (com.google.firebase.firestore.DocumentSnapshot head : heads.getDocuments()) {
                                    NotificationHelper.send(head.getId(), "department-head", "Faculty Response",
                                            facultyName + " " + decisionWord + " the room reassignment request for " + courseTitle + ".",
                                            "room-reassignment", decisionWord.toUpperCase());
                                }
                            });

                    Map<String, Object> details = new HashMap<>();
                    ActivityLogger.log(
                            decisionWord.equals("accepted") ? "Accepted room reassignment" : "Rejected room reassignment",
                            decisionWord.equals("accepted") ? "success" : "denied",
                            courseTitle + " | " + str(assignment.get("oldRoomName")) + " → " + str(assignment.get("newRoomName")),
                            decisionWord.equals("accepted") ? "Success" : "Rejected",
                            details,
                            () -> {
                                Toast.makeText(this, "Room reassignment " + decisionWord + ".", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                });
    }
}