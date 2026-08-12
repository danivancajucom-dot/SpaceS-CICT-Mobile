package com.example.spacescict;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeeklySchedulePage {

    Context context;
    LinearLayout dayHeaderRow, timeColumn;
    FrameLayout gridCanvas;
    TextView weekLabelText, termLabelText;
    View prevBtn, nextBtn;
    HorizontalScrollView headerScroll, contentHScroll;
    ScrollView timeColumnScroll, contentVScroll;
    int weekOffset = 0;

    static final int START_HOUR = 7, END_HOUR = 21;
    int hourHeightPx, colWidthPx;

    public WeeklySchedulePage(Context context, View view) {
        this.context = context;
        dayHeaderRow = view.findViewById(R.id.dayHeaderRow);
        timeColumn = view.findViewById(R.id.timeColumn);
        gridCanvas = view.findViewById(R.id.gridCanvas);
        weekLabelText = view.findViewById(R.id.weekLabelText);
        termLabelText = view.findViewById(R.id.termLabelText);
        prevBtn = view.findViewById(R.id.weekPrevBtn);
        nextBtn = view.findViewById(R.id.weekNextBtn);
        headerScroll = view.findViewById(R.id.headerScroll);
        contentHScroll = view.findViewById(R.id.contentHScroll);
        timeColumnScroll = view.findViewById(R.id.timeColumnScroll);
        contentVScroll = view.findViewById(R.id.contentVScroll);

        hourHeightPx = dp(56);
        colWidthPx = dp(110);

        // Header row is display-only — block direct touch so it can't desync from the main scroll
        headerScroll.setOnTouchListener((v, event) -> true);
        timeColumnScroll.setOnTouchListener((v, event) -> true);

        // Sync header horizontal scroll with the main grid's horizontal scroll
        contentHScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            headerScroll.scrollTo(scrollX, 0);
        });

        // Sync time column vertical scroll with the main grid's vertical scroll
        contentVScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            timeColumnScroll.scrollTo(0, scrollY);
        });

        prevBtn.setOnClickListener(v -> { weekOffset--; load(); });
        nextBtn.setOnClickListener(v -> { weekOffset++; load(); });

        buildTimeColumn();
        load();
    }

    int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.getResources().getDisplayMetrics());
    }

    void buildTimeColumn() {
        timeColumn.removeAllViews();
        for (int h = START_HOUR; h < END_HOUR; h++) {
            TextView label = new TextView(context);
            label.setText(fmtHour(h));
            label.setTextSize(10);
            label.setTextColor(Color.parseColor("#6B7280"));
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, hourHeightPx);
            label.setLayoutParams(lp);
            timeColumn.addView(label);
        }
    }

    String fmtHour(int h) {
        if (h < 12) return h + " AM";
        if (h == 12) return "12 PM";
        return (h - 12) + " PM";
    }

    void load() {
        // Reset scroll to top-left on every week change, so the new week always starts fully visible
        contentHScroll.post(() -> contentHScroll.scrollTo(0, 0));
        headerScroll.post(() -> headerScroll.scrollTo(0, 0));

        ScheduleLoader.loadWeek(weekOffset, new ScheduleLoader.WeekCallback() {
            @Override
            public void onResult(Map<String, List<ScheduleLoader.ScheduleItem>> byDay, String weekLabel, String termLabel) {
                weekLabelText.setText(weekLabel);
                termLabelText.setText(termLabel != null ? termLabel : "");
                renderHeaders();
                renderGrid(byDay);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(context, "Failed to load schedule: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    void renderHeaders() {
        dayHeaderRow.removeAllViews();
        String[] labels = ScheduleLoader.MON_FIRST;
        java.util.Calendar monday = ScheduleLoader.getStartOfWeek(java.util.Calendar.getInstance());
        monday.add(java.util.Calendar.DAY_OF_MONTH, weekOffset * 7);

        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(colWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
            col.setLayoutParams(lp);

            TextView dayName = new TextView(context);
            dayName.setText(labels[i]);
            dayName.setTextSize(11);
            dayName.setTextColor(Color.parseColor("#6B7280"));
            dayName.setGravity(Gravity.CENTER);
            col.addView(dayName);

            TextView dayNum = new TextView(context);
            dayNum.setText(String.valueOf(monday.get(java.util.Calendar.DAY_OF_MONTH)));
            dayNum.setTypeface(null, Typeface.BOLD);
            dayNum.setTextColor(Color.parseColor("#111827"));
            dayNum.setGravity(Gravity.CENTER);
            col.addView(dayNum);

            dayHeaderRow.addView(col);
            monday.add(java.util.Calendar.DAY_OF_MONTH, 1);
        }
    }

    void renderGrid(Map<String, List<ScheduleLoader.ScheduleItem>> byDay) {
        gridCanvas.removeAllViews();
        int totalHeight = (END_HOUR - START_HOUR) * hourHeightPx;
        int totalWidth = colWidthPx * 7;

        // gridCanvas's direct parent is a ScrollView — must use ScrollView.LayoutParams (extends FrameLayout.LayoutParams)
        ScrollView.LayoutParams canvasParams = new ScrollView.LayoutParams(totalWidth, totalHeight);
        gridCanvas.setLayoutParams(canvasParams);

        for (int h = 0; h <= (END_HOUR - START_HOUR); h++) {
            View line = new View(context);
            line.setBackgroundColor(Color.parseColor("#E5E7EB"));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(totalWidth, dp(1));
            lp.topMargin = h * hourHeightPx;
            gridCanvas.addView(line, lp);
        }
        for (int d = 0; d <= 7; d++) {
            View line = new View(context);
            line.setBackgroundColor(Color.parseColor("#E5E7EB"));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(1), totalHeight);
            lp.leftMargin = d * colWidthPx;
            gridCanvas.addView(line, lp);
        }

        String[] dayOrder = ScheduleLoader.MON_FIRST;
        for (int i = 0; i < dayOrder.length; i++) {
            List<ScheduleLoader.ScheduleItem> items = byDay.get(dayOrder[i]);
            if (items == null) continue;
            for (ScheduleLoader.ScheduleItem item : items) {
                gridCanvas.addView(buildEventBlock(item, i));
            }
        }
    }

    View buildEventBlock(ScheduleLoader.ScheduleItem item, int dayIndex) {
        int[] start = ScheduleLoader.parseTimeParts(item.startTime);
        int[] end = ScheduleLoader.parseTimeParts(item.endTime);

        float startOffsetHrs = (start[0] - START_HOUR) + start[1] / 60f;
        float durationHrs = (end[0] - start[0]) + (end[1] - start[1]) / 60f;

        int top = Math.round(startOffsetHrs * hourHeightPx);
        int height = Math.max(Math.round(durationHrs * hourHeightPx) - dp(2), dp(24));

        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(6), dp(4), dp(6), dp(4));
        Colors c = colorFor(item.kind);
        block.setBackgroundColor(c.bg);

        TextView title = new TextView(context);
        title.setText(item.subject != null ? item.subject : "");
        title.setTextColor(c.text);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(11);
        title.setMaxLines(2);
        block.addView(title);

        TextView room = new TextView(context);
        room.setText(item.roomName != null ? item.roomName : "");
        room.setTextColor(c.text);
        room.setTextSize(10);
        block.addView(room);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(colWidthPx - dp(4), height);
        lp.leftMargin = dayIndex * colWidthPx + dp(2);
        lp.topMargin = top + dp(1);
        block.setLayoutParams(lp);

        block.setOnClickListener(v -> showDetailsDialog(item));

        return block;
    }

    static class Colors { int bg, text; Colors(int bg, int text) { this.bg = bg; this.text = text; } }

    Colors colorFor(String kind) {
        switch (kind) {
            case "schedule": return new Colors(Color.parseColor("#EEF2FF"), Color.parseColor("#3651D4"));
            case "event": return new Colors(Color.parseColor("#ECFDF5"), Color.parseColor("#1A9E5C"));
            case "reservation": return new Colors(Color.parseColor("#FFF7ED"), Color.parseColor("#C2621A"));
            case "reassignment": return new Colors(Color.parseColor("#F5F3FF"), Color.parseColor("#6D28D9"));
            default: return new Colors(Color.WHITE, Color.BLACK);
        }
    }

    void showDetailsDialog(ScheduleLoader.ScheduleItem item) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(16), dp(24), dp(16));

        addDetailRow(body, "Subject", item.subject);
        addDetailRow(body, "Room", item.roomName);
        addDetailRow(body, "Date", item.date);
        addDetailRow(body, "Time", formatTime(item.startTime) + " - " + formatTime(item.endTime));
        if (item.section != null && !item.section.isEmpty()) addDetailRow(body, "Section", item.section);
        if (item.faculty != null && !item.faculty.isEmpty()) addDetailRow(body, "Faculty", item.faculty);
        if (item.kind.equals("reassignment") && item.originalRoom != null) {
            addDetailRow(body, "Moved from", item.originalRoom);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle(titleFor(item.kind))
                .setView(body)
                .setNegativeButton("Close", null);

        String status = computeStatus(item);
        if (item.kind.equals("schedule") && !status.equals("COMPLETED")) {
            builder.setPositiveButton("Release Room", (d, w) -> showReleaseDialog(item));
        }

        builder.show();
    }

    void addDetailRow(LinearLayout parent, String label, String value) {
        TextView tv = new TextView(context);
        tv.setText(label + ": " + (value != null ? value : "N/A"));
        tv.setTextColor(Color.parseColor("#374151"));
        tv.setPadding(0, dp(4), 0, dp(4));
        parent.addView(tv);
    }

    String titleFor(String kind) {
        switch (kind) {
            case "schedule": return "Class Schedule";
            case "event": return "Room Activity";
            case "reservation": return "Reservation";
            case "reassignment": return "Reassigned Class";
            default: return "Details";
        }
    }

    String computeStatus(ScheduleLoader.ScheduleItem item) {
        String today = ScheduleLoader.toDateStr(java.util.Calendar.getInstance());
        if (item.date == null) return "SCHEDULED";
        if (item.date.compareTo(today) < 0) return "COMPLETED";
        if (item.date.compareTo(today) > 0) return "UPCOMING";

        java.util.Calendar now = java.util.Calendar.getInstance();
        int nowMin = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        int[] s = ScheduleLoader.parseTimeParts(item.startTime);
        int[] e = ScheduleLoader.parseTimeParts(item.endTime);
        int startMin = s[0] * 60 + s[1];
        int endMin = e[0] * 60 + e[1];
        if (nowMin >= startMin && nowMin < endMin) return "ONGOING";
        if (nowMin < startMin) return "UPCOMING";
        return "COMPLETED";
    }

    String formatTime(String time) {
        if (time == null) return "";
        int[] p = ScheduleLoader.parseTimeParts(time);
        String suffix = p[0] >= 12 ? "PM" : "AM";
        int hh = p[0] % 12 == 0 ? 12 : p[0] % 12;
        return String.format(Locale.US, "%02d:%02d %s", hh, p[1], suffix);
    }

    void showReleaseDialog(ScheduleLoader.ScheduleItem item) {
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(16), dp(24), dp(16));

        TextView label = new TextView(context);
        label.setText("Reason for releasing this room");
        label.setTextColor(Color.parseColor("#111827"));
        body.addView(label);

        EditText reasonInput = new EditText(context);
        reasonInput.setHint("e.g. Class cancelled, moved online, etc.");
        body.addView(reasonInput);

        new AlertDialog.Builder(context)
                .setTitle("Release Room")
                .setView(body)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Confirm Release", (d, w) -> {
                    String reason = reasonInput.getText().toString().trim();
                    if (reason.isEmpty()) {
                        Toast.makeText(context, "Please provide a reason", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitRelease(item, reason);
                })
                .show();
    }

    void submitRelease(ScheduleLoader.ScheduleItem item, String reason) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    String first = userDoc.getString("firstName");
                    String last = userDoc.getString("lastName");
                    String fullName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();

                    Map<String, Object> release = new HashMap<>();
                    release.put("scheduleId", item.id);
                    release.put("roomName", item.roomName);
                    release.put("date", item.date);
                    release.put("day", ScheduleLoader.dayAbbrevForDate(item.date));
                    release.put("subject", item.subject != null ? item.subject : "");
                    release.put("section", item.section != null ? item.section : "");
                    release.put("startTime", item.startTime);
                    release.put("endTime", item.endTime);
                    release.put("faculty", fullName);
                    release.put("releasedBy", uid);
                    release.put("releasedByName", fullName);
                    release.put("reason", reason);
                    release.put("status", "released");
                    release.put("releasedAt", Timestamp.now());

                    FirebaseFirestore.getInstance().collection("roomReleases").add(release)
                            .addOnSuccessListener(ref -> {
                                notifyRelease(uid, fullName, item.roomName, item.subject, item.date, item.startTime, item.endTime);

                                Map<String, Object> details = new HashMap<>();
                                details.put("reason", reason);
                                ActivityLogger.log("Released Room", "UPDATE",
                                        item.roomName + " | " + item.subject, "SUCCESS", details, () -> {
                                            Toast.makeText(context, "Room released successfully!", Toast.LENGTH_SHORT).show();
                                            load();
                                        });
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(context, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                });
    }

    void notifyRelease(String facultyId, String facultyName, String roomName, String subject,
                       String date, String startTime, String endTime) {
        FirebaseFirestore.getInstance().collection("users").get().addOnSuccessListener(snap -> {
            for (com.google.firebase.firestore.DocumentSnapshot userDoc : snap.getDocuments()) {
                String role = userDoc.getString("role");
                if (role == null) continue;
                String r = role.toLowerCase(Locale.US);
                String ownerType = r.equals("clerk") ? "clerk"
                        : (r.contains("department") && r.contains("head")) ? "department-head" : null;
                if (ownerType == null) continue;

                NotificationHelper.send(userDoc.getId(), ownerType, "Room Released",
                        facultyName + " released " + roomName + " for " + subject + " on " + date
                                + " (" + startTime + " - " + endTime + ").",
                        "room-release", "NEW");
            }
        });

        NotificationHelper.send(facultyId, "faculty", "Room Released",
                "You successfully released " + roomName + " (" + subject + ") on " + date
                        + " (" + startTime + " - " + endTime + ").",
                "room-release", "SUCCESS");
    }
}