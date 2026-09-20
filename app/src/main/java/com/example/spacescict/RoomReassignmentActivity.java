package com.example.spacescict;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Faculty response to a room reassignment request.
 *
 * Kept deliberately in step with FacultyRoomReassignment.jsx:
 *  - writes status "accepted" / "declined" (NOT "approved"/"rejected" — the web
 *    reads those exact strings, and the old values made accepted reassignments
 *    disappear from the web schedule)
 *  - stamps acceptedAt / declinedAt / updatedAt / denialReason
 *  - writes the full resolution block back onto the linked event
 *  - notifies the faculty, every Admin, and the clerk who raised the request
 */
public class RoomReassignmentActivity extends AppCompatActivity {

    public static final String EXTRA_ASSIGNMENT_ID = "assignmentId";

    String assignmentId;
    Map<String, Object> assignment;

    TextView infoText, oldRoomText, newRoomText, expiredText;
    View actionsLayout, rejectBtn, approveBtn;

    private boolean submitting = false;

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

        if (rejectBtn != null) rejectBtn.setOnClickListener(v -> showDeclineDialog());
        if (approveBtn != null) approveBtn.setOnClickListener(v -> confirmAccept());
    }

    void load() {
        LoadingOverlay.show(this, "Loading reassignment...");
        FirebaseFirestore.getInstance().collection("roomReassignments").document(assignmentId).get()
                .addOnSuccessListener(doc -> {
                    LoadingOverlay.hide();
                    if (!doc.exists()) {
                        Toast.makeText(this, "Reassignment not found", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    assignment = doc.getData();
                    render();
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide();
                    Toast.makeText(this, "Could not load: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    void render() {
        String courseTitle = str(assignment.get("courseTitle"));
        String section = str(assignment.get("section"));
        String date = str(assignment.get("date"));
        String startTime = str(assignment.get("startTime"));
        String endTime = str(assignment.get("endTime"));

        infoText.setText("Course: " + orDash(courseTitle)
                + "\nSection: " + orDash(section)
                + "\nDate: " + orDash(date)
                + "\nTime: " + formatTime(startTime) + " - " + formatTime(endTime));

        oldRoomText.setText(orDash(str(assignment.get("oldRoomName"))));
        newRoomText.setText(orDash(str(assignment.get("newRoomName"))));

        String status = str(assignment.get("status")).toLowerCase(Locale.US);
        boolean alreadyAnswered = status.equals("accepted") || status.equals("declined")
                || status.equals("approved") || status.equals("rejected");

        if (isExpired() || alreadyAnswered) {
            if (actionsLayout != null) actionsLayout.setVisibility(View.GONE);
            if (expiredText != null) {
                expiredText.setVisibility(View.VISIBLE);
                if (alreadyAnswered && !isExpired()) {
                    boolean accepted = status.equals("accepted") || status.equals("approved");
                    expiredText.setText(accepted
                            ? "You already accepted this room reassignment."
                            : "You already declined this room reassignment.");
                }
            }
        }
    }

    boolean isExpired() {
        if (assignment == null) return false;
        String date = str(assignment.get("date"));
        String endTime = str(assignment.get("endTime"));
        if (date.isEmpty() || endTime.isEmpty()) return false;
        try {
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            java.util.Date scheduleEnd = fmt.parse(date + "T" + endTime);
            return scheduleEnd != null && new java.util.Date().after(scheduleEnd);
        } catch (Exception e) {
            return false;
        }
    }

    String str(Object o) {
        return o != null ? o.toString() : "";
    }

    String orDash(String value) {
        return value == null || value.trim().isEmpty() ? "-" : value;
    }

    String formatTime(String time) {
        if (time == null || time.isEmpty()) return "-";
        int[] p = ScheduleLoader.parseTimeParts(time);
        String suffix = p[0] >= 12 ? "PM" : "AM";
        int hh = p[0] % 12 == 0 ? 12 : p[0] % 12;
        return String.format(Locale.US, "%02d:%02d %s", hh, p[1], suffix);
    }

    int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ─── ACCEPT ──────────────────────────────────────────────────────────

    void confirmAccept() {
        if (submitting) return;
        if (isExpired()) {
            Toast.makeText(this, "This room reassignment has already expired.", Toast.LENGTH_SHORT).show();
            return;
        }
        ConfirmDialog.show(this, "Accept Room Change",
                "Accept the move to " + orDash(str(assignment.get("newRoomName"))) + "?",
                "Accept", "Cancel", () -> submit("accepted", null));
    }

    // ─── DECLINE (with reason, matching the web modal) ───────────────────

    void showDeclineDialog() {
        if (submitting) return;
        if (isExpired()) {
            Toast.makeText(this, "This room reassignment has already expired.", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(20));

        CardView iconCard = new CardView(this);
        iconCard.setRadius(dp(16));
        iconCard.setCardElevation(0);
        iconCard.setCardBackgroundColor(Color.parseColor("#FEF2F2"));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(56), dp(56));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconCard.setLayoutParams(iconParams);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_warning);
        icon.setColorFilter(Color.parseColor("#DC2626"));
        icon.setPadding(dp(14), dp(14), dp(14), dp(14));
        iconCard.addView(icon);
        root.addView(iconCard);

        TextView title = new TextView(this);
        title.setText("Decline Room Change");
        title.setTextColor(Color.parseColor("#1C1917"));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(14);
        title.setLayoutParams(titleParams);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Let the clerk know why you can't take "
                + orDash(str(assignment.get("newRoomName"))) + ".");
        subtitle.setTextColor(Color.parseColor("#78716C"));
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(6);
        subtitle.setLayoutParams(subParams);
        root.addView(subtitle);

        EditText reasonInput = new EditText(this);
        reasonInput.setHint("e.g. Room is too small for my section");
        reasonInput.setBackgroundResource(R.drawable.input_field_bg);
        reasonInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        reasonInput.setTextSize(14);
        reasonInput.setGravity(Gravity.TOP | Gravity.START);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(96));
        inputParams.topMargin = dp(18);
        reasonInput.setLayoutParams(inputParams);
        root.addView(reasonInput);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(20);
        buttonRow.setLayoutParams(rowParams);

        CardView cancelCard = new CardView(this);
        cancelCard.setRadius(dp(14));
        cancelCard.setCardElevation(0);
        cancelCard.setCardBackgroundColor(Color.parseColor("#EEF1F5"));
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        cancelParams.rightMargin = dp(8);
        cancelCard.setLayoutParams(cancelParams);
        TextView cancelText = new TextView(this);
        cancelText.setText("Go Back");
        cancelText.setTextColor(Color.parseColor("#44403C"));
        cancelText.setTypeface(null, Typeface.BOLD);
        cancelText.setGravity(Gravity.CENTER);
        cancelText.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        cancelCard.addView(cancelText);
        buttonRow.addView(cancelCard);

        CardView confirmCard = new CardView(this);
        confirmCard.setRadius(dp(14));
        confirmCard.setCardElevation(0);
        confirmCard.setCardBackgroundColor(Color.parseColor("#DC2626"));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        confirmParams.leftMargin = dp(8);
        confirmCard.setLayoutParams(confirmParams);
        TextView confirmText = new TextView(this);
        confirmText.setText("Decline");
        confirmText.setTextColor(Color.WHITE);
        confirmText.setTypeface(null, Typeface.BOLD);
        confirmText.setGravity(Gravity.CENTER);
        confirmText.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        confirmCard.addView(confirmText);
        buttonRow.addView(confirmCard);

        root.addView(buttonRow);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        cancelCard.setOnClickListener(v -> dialog.dismiss());
        confirmCard.setOnClickListener(v -> {
            String reason = reasonInput.getText().toString().trim();
            dialog.dismiss();
            submit("declined", reason.isEmpty() ? "No reason provided" : reason);
        });

        dialog.show();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout((int) (screenWidth * 0.88),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ─── WRITE ───────────────────────────────────────────────────────────

    void submit(String decision, String reason) {
        if (submitting) return;
        submitting = true;

        boolean accepted = "accepted".equals(decision);
        LoadingOverlay.show(this, accepted ? "Accepting room change..." : "Declining room change...");

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", decision);                 // "accepted" | "declined"
        updates.put("updatedAt", Timestamp.now());
        updates.put(accepted ? "acceptedAt" : "declinedAt", Timestamp.now());
        if (!accepted) updates.put("denialReason", reason);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("roomReassignments").document(assignmentId)
                .update(updates)
                .addOnSuccessListener(unused -> {
                    String eventId = (String) assignment.get("eventId");
                    if (eventId != null && !eventId.isEmpty()) {
                        Map<String, Object> eventUpdates = new HashMap<>();
                        eventUpdates.put("conflictResolved", true);
                        eventUpdates.put("resolution", accepted ? "approved" : "rejected");
                        eventUpdates.put("resolutionReason",
                                accepted ? "Accepted by faculty" : reason);
                        eventUpdates.put("resolvedAt", Timestamp.now());
                        db.collection("events").document(eventId).update(eventUpdates);
                    }
                    sendDecisionNotifications(decision, reason);
                })
                .addOnFailureListener(e -> {
                    submitting = false;
                    LoadingOverlay.hide();
                    Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    void sendDecisionNotifications(String decision, String reason) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            submitting = false;
            LoadingOverlay.hide();
            finish();
            return;
        }

        boolean accepted = "accepted".equals(decision);
        String verb = accepted ? "accepted" : "declined";
        String badge = accepted ? "ACCEPTED" : "DECLINED";
        String courseTitle = orDash(str(assignment.get("courseTitle")));
        String oldRoom = orDash(str(assignment.get("oldRoomName")));
        String newRoom = orDash(str(assignment.get("newRoomName")));

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            String first = userDoc.getString("firstName");
            String last = userDoc.getString("lastName");
            String facultyName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
            if (facultyName.isEmpty()) facultyName = "Faculty";
            final String finalFacultyName = facultyName;

            Map<String, Object> extras = new HashMap<>();
            extras.put("assignmentId", assignmentId);
            extras.put("reassignmentId", assignmentId);

            // 1. the faculty's own confirmation
            NotificationHelper.send(uid, "faculty", "Room Reassignment",
                    "You " + verb + " the room reassignment for " + courseTitle + ".",
                    "approved", badge, extras);

            // 2. every Admin (ownerType "admin" — this is what the web listens on)
            db.collection("users").whereEqualTo("role", "Admin").get()
                    .addOnSuccessListener(admins -> {
                        for (DocumentSnapshot admin : admins.getDocuments()) {
                            NotificationHelper.send(admin.getId(), "admin", "Faculty Response",
                                    finalFacultyName + " " + verb
                                            + " the room reassignment request for " + courseTitle + ".",
                                    "room-reassignment-status", badge, extras);
                        }
                    });

            // 3. the clerk who raised the request
            String requestedById = str(assignment.get("requestedById"));
            if (!requestedById.isEmpty()) {
                NotificationHelper.send(requestedById, "clerk",
                        accepted ? "Faculty Accepted Reassignment" : "Faculty Declined Reassignment",
                        finalFacultyName + " " + verb + " the reassignment for " + courseTitle
                                + " (" + oldRoom + " \u2192 " + newRoom + ").",
                        "room-reassignment-status", badge, extras);
            }

            Map<String, Object> details = new HashMap<>();
            details.put("decision", decision);
            if (!accepted && reason != null) details.put("reason", reason);

            ActivityLogger.log(
                    accepted ? "Accepted room reassignment" : "Declined room reassignment",
                    accepted ? "success" : "denied",
                    courseTitle + " | " + oldRoom + " \u2192 " + newRoom,
                    accepted ? "Success" : "Declined",
                    details,
                    () -> {
                        submitting = false;
                        LoadingOverlay.hide();
                        Toast.makeText(this,
                                accepted ? "Room reassignment accepted."
                                        : "Room reassignment declined.",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    });
        }).addOnFailureListener(e -> {
            submitting = false;
            LoadingOverlay.hide();
            finish();
        });
    }
}