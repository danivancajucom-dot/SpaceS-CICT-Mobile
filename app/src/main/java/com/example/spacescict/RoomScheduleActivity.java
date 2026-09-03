package com.example.spacescict;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoomScheduleActivity extends AppCompatActivity {

    public static final String EXTRA_ROOM_ID = "roomId";
    public static final String EXTRA_ROOM_NAME = "roomName";

    String roomId, roomName;
    int weekOffset = 0;

    LinearLayout dayHeaderRow, timeColumn;
    FrameLayout gridCanvas;
    TextView weekLabelText, roomNameHeader;

    static final int START_HOUR = 7, END_HOUR = 21;
    int hourHeightPx, colWidthPx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_schedule);

        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);
        if (roomId == null) {
            finish();
            return;
        }

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        roomNameHeader = findViewById(R.id.roomNameHeader);
        roomNameHeader.setText(roomName != null ? roomName : "Room Schedule");

        dayHeaderRow = findViewById(R.id.dayHeaderRow);
        timeColumn = findViewById(R.id.timeColumn);
        gridCanvas = findViewById(R.id.gridCanvas);
        weekLabelText = findViewById(R.id.weekLabelText);

        View prevBtn = findViewById(R.id.weekPrevBtn);
        View nextBtn = findViewById(R.id.weekNextBtn);

        hourHeightPx = dp(56);
        colWidthPx = dp(110);

        prevBtn.setOnClickListener(v -> { weekOffset--; load(); });
        nextBtn.setOnClickListener(v -> { weekOffset++; load(); });

        buildTimeColumn();
        load();
    }

    int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    void buildTimeColumn() {
        timeColumn.removeAllViews();
        for (int h = START_HOUR; h < END_HOUR; h++) {
            TextView label = new TextView(this);
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
        Calendar monday = ScheduleLoader.getStartOfWeek(Calendar.getInstance());
        monday.add(Calendar.DAY_OF_MONTH, weekOffset * 7);
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_MONTH, 6);

        String[] weekDates = new String[7];
        Calendar cursor = (Calendar) monday.clone();
        for (int i = 0; i < 7; i++) {
            weekDates[i] = ScheduleLoader.toDateStr(cursor);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
        weekLabelText.setText(ScheduleLoader.formatWeekLabel(monday, sunday));
        renderHeaders(monday);

        RoomScheduleLoader.loadWeek(roomId, weekDates, new RoomScheduleLoader.Callback() {
            @Override
            public void onResult(Map<String, List<RoomScheduleLoader.RoomItem>> byDay) {
                renderGrid(byDay);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RoomScheduleActivity.this, "Failed to load room schedule: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    void renderHeaders(Calendar monday) {
        dayHeaderRow.removeAllViews();
        String[] labels = ScheduleLoader.MON_FIRST;
        Calendar cursor = (Calendar) monday.clone();

        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(colWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
            col.setLayoutParams(lp);

            TextView dayName = new TextView(this);
            dayName.setText(labels[i]);
            dayName.setTextSize(11);
            dayName.setTextColor(Color.parseColor("#6B7280"));
            dayName.setGravity(Gravity.CENTER);
            col.addView(dayName);

            TextView dayNum = new TextView(this);
            dayNum.setText(String.valueOf(cursor.get(Calendar.DAY_OF_MONTH)));
            dayNum.setTypeface(null, Typeface.BOLD);
            dayNum.setTextColor(Color.parseColor("#111827"));
            dayNum.setGravity(Gravity.CENTER);
            col.addView(dayNum);

            dayHeaderRow.addView(col);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }
    }

    void renderGrid(Map<String, List<RoomScheduleLoader.RoomItem>> byDay) {
        gridCanvas.removeAllViews();
        int totalHeight = (END_HOUR - START_HOUR) * hourHeightPx;
        int totalWidth = colWidthPx * 7;

        // FIX: gridCanvas's actual parent is a LinearLayout, not a ScrollView
        LinearLayout.LayoutParams canvasParams = new LinearLayout.LayoutParams(totalWidth, totalHeight);
        gridCanvas.setLayoutParams(canvasParams);

        for (int h = 0; h <= (END_HOUR - START_HOUR); h++) {
            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#E5E7EB"));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(totalWidth, dp(1));
            lp.topMargin = h * hourHeightPx;
            gridCanvas.addView(line, lp);
        }
        for (int d = 0; d <= 7; d++) {
            View line = new View(this);
            line.setBackgroundColor(Color.parseColor("#E5E7EB"));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(1), totalHeight);
            lp.leftMargin = d * colWidthPx;
            gridCanvas.addView(line, lp);
        }

        String[] dayOrder = ScheduleLoader.MON_FIRST;
        for (int i = 0; i < dayOrder.length; i++) {
            List<RoomScheduleLoader.RoomItem> items = byDay.get(dayOrder[i]);
            if (items == null) continue;
            for (RoomScheduleLoader.RoomItem item : items) {
                gridCanvas.addView(buildEventBlock(item, i));
            }
        }
    }

    View buildEventBlock(RoomScheduleLoader.RoomItem item, int dayIndex) {
        int[] start = ScheduleLoader.parseTimeParts(item.startTime);
        int[] end = ScheduleLoader.parseTimeParts(item.endTime);

        float startOffsetHrs = (start[0] - START_HOUR) + start[1] / 60f;
        float durationHrs = (end[0] - start[0]) + (end[1] - start[1]) / 60f;

        int top = Math.round(startOffsetHrs * hourHeightPx);
        int height = Math.max(Math.round(durationHrs * hourHeightPx) - dp(2), dp(24));

        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(6), dp(4), dp(6), dp(4));
        Colors c = colorFor(item.kind);
        block.setBackgroundColor(c.bg);

        TextView title = new TextView(this);
        title.setText(item.subject != null ? item.subject : "");
        title.setTextColor(c.text);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(11);
        title.setMaxLines(2);
        block.addView(title);

        TextView faculty = new TextView(this);
        faculty.setText(item.faculty != null ? item.faculty : "");
        faculty.setTextColor(c.text);
        faculty.setTextSize(10);
        block.addView(faculty);

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

    void showDetailsDialog(RoomScheduleLoader.RoomItem item) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        body.setPadding(pad, dp(16), pad, dp(16));

        addRow(body, "Subject", item.subject);
        addRow(body, "Faculty", item.faculty);
        addRow(body, "Time", formatTime(item.startTime) + " - " + formatTime(item.endTime));
        if (item.kind.equals("reassignment") && item.originalRoom != null) {
            addRow(body, "Moved from", item.originalRoom);
        }

        new AlertDialog.Builder(this)
                .setTitle(titleFor(item.kind))
                .setView(body)
                .setNegativeButton("Close", null)
                .show();
    }

    void addRow(LinearLayout parent, String label, String value) {
        TextView tv = new TextView(this);
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

    String formatTime(String time) {
        if (time == null) return "";
        int[] p = ScheduleLoader.parseTimeParts(time);
        String suffix = p[0] >= 12 ? "PM" : "AM";
        int hh = p[0] % 12 == 0 ? 12 : p[0] % 12;
        return String.format(Locale.US, "%02d:%02d %s", hh, p[1], suffix);
    }
}