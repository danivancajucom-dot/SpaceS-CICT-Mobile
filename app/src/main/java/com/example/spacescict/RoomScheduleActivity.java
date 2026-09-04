package com.example.spacescict;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    HorizontalScrollView headerScroll, contentHScroll;
    ScrollView timeColumnScroll, contentVScroll;

    static final int START_HOUR = 7, END_HOUR = 21;
    int hourHeightPx, colWidthPx;

    static final int[][] FACULTY_PALETTE = {
            {0xFFEEF2FF, 0xFF3651D4},
            {0xFFECFDF5, 0xFF1A9E5C},
            {0xFFFFF7ED, 0xFFC2621A},
            {0xFFF5F3FF, 0xFF6D28D9},
            {0xFFFDF2F8, 0xFFBE185D},
            {0xFFF0FDFA, 0xFF0F766E},
            {0xFFFEFCE8, 0xFF854D0E},
    };

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
        headerScroll = findViewById(R.id.headerScroll);
        contentHScroll = findViewById(R.id.contentHScroll);
        timeColumnScroll = findViewById(R.id.timeColumnScroll);
        contentVScroll = findViewById(R.id.contentVScroll);

        View prevBtn = findViewById(R.id.weekPrevBtn);
        View nextBtn = findViewById(R.id.weekNextBtn);

        hourHeightPx = dp(56);
        colWidthPx = dp(110);

        headerScroll.setOnTouchListener((v, event) -> true);
        timeColumnScroll.setOnTouchListener((v, event) -> true);

        contentHScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                headerScroll.scrollTo(scrollX, 0));

        contentVScroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                timeColumnScroll.scrollTo(0, scrollY));

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
        contentHScroll.post(() -> contentHScroll.scrollTo(0, 0));
        headerScroll.post(() -> headerScroll.scrollTo(0, 0));

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

        ScrollView.LayoutParams canvasParams = new ScrollView.LayoutParams(totalWidth, totalHeight);
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
        Colors c = colorFor(item.kind, item.faculty);
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

    Colors colorFor(String kind, String faculty) {
        if ("reassignment".equals(kind)) {
            return new Colors(0xFFF5F3FF, 0xFF6D28D9);
        }
        if (faculty == null || faculty.trim().isEmpty()) {
            return new Colors(Color.WHITE, Color.BLACK);
        }
        int hash = Math.abs(faculty.trim().toLowerCase(Locale.US).hashCode());
        int[] pair = FACULTY_PALETTE[hash % FACULTY_PALETTE.length];
        return new Colors(pair[0], pair[1]);
    }

    void showDetailsDialog(RoomScheduleLoader.RoomItem item) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(20));

        TextView title = new TextView(this);
        title.setText(titleFor(item.kind));
        title.setTextColor(Color.parseColor("#1C1917"));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = dp(16);
        title.setLayoutParams(titleParams);
        root.addView(title);

        addRow(root, "Subject", item.subject);
        addRow(root, "Faculty", item.faculty);
        addRow(root, "Time", formatTime(item.startTime) + " - " + formatTime(item.endTime));
        if (item.kind.equals("reassignment") && item.originalRoom != null) {
            addRow(root, "Moved from", item.originalRoom);
        }

        androidx.cardview.widget.CardView closeCard = new androidx.cardview.widget.CardView(this);
        closeCard.setRadius(dp(14));
        closeCard.setCardElevation(0);
        closeCard.setCardBackgroundColor(Color.parseColor("#F97316"));
        LinearLayout.LayoutParams closeCardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        closeCardParams.topMargin = dp(20);
        closeCard.setLayoutParams(closeCardParams);

        TextView closeBtn = new TextView(this);
        closeBtn.setText("Close");
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTypeface(null, Typeface.BOLD);
        closeBtn.setGravity(Gravity.CENTER);
        closeBtn.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        closeCard.addView(closeBtn);
        root.addView(closeCard);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout((int) (screenWidth * 0.88), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    void addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(12);
        row.setLayoutParams(rowParams);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#78716C"));
        labelView.setTextSize(11);
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value != null && !value.isEmpty() ? value : "N/A");
        valueView.setTextColor(Color.parseColor("#1C1917"));
        valueView.setTextSize(14);
        valueView.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = dp(2);
        valueView.setLayoutParams(valueParams);
        row.addView(valueView);

        parent.addView(row);
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