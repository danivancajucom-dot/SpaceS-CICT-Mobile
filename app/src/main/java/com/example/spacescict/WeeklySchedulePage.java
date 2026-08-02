package com.example.spacescict;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WeeklySchedulePage {

    Context context;
    LinearLayout weekContainer;
    TextView weekLabelText, termLabelText;
    View prevBtn, nextBtn;
    int weekOffset = 0;

    public WeeklySchedulePage(Context context, View view) {
        this.context = context;
        weekContainer = view.findViewById(R.id.weekContainer);
        weekLabelText = view.findViewById(R.id.weekLabelText);
        termLabelText = view.findViewById(R.id.termLabelText);
        prevBtn = view.findViewById(R.id.weekPrevBtn);
        nextBtn = view.findViewById(R.id.weekNextBtn);

        prevBtn.setOnClickListener(v -> {
            weekOffset--;
            load();
        });
        nextBtn.setOnClickListener(v -> {
            weekOffset++;
            load();
        });

        load();
    }

    void load() {
        weekContainer.removeAllViews();
        TextView loadingText = new TextView(context);
        loadingText.setText("Loading schedule...");
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(0, 40, 0, 40);
        weekContainer.addView(loadingText);

        ScheduleLoader.loadWeek(weekOffset, new ScheduleLoader.WeekCallback() {
            @Override
            public void onResult(Map<String, List<ScheduleLoader.ScheduleItem>> byDay, String weekLabel, String termLabel) {
                weekLabelText.setText(weekLabel);
                termLabelText.setText(termLabel != null ? termLabel : "");
                renderWeek(byDay);
            }

            @Override
            public void onError(String message) {
                weekContainer.removeAllViews();
                TextView error = new TextView(context);
                error.setText("Failed to load schedule:\n" + message);
                error.setTextColor(Color.parseColor("#DC2626"));
                error.setGravity(Gravity.CENTER);
                error.setPadding(20, 60, 20, 60);
                weekContainer.addView(error);
            }
        });
    }

    void renderWeek(Map<String, List<ScheduleLoader.ScheduleItem>> byDay) {
        weekContainer.removeAllViews();

        boolean anyItems = false;
        for (String day : ScheduleLoader.MON_FIRST) {
            List<ScheduleLoader.ScheduleItem> items = byDay.get(day);
            if (items == null || items.isEmpty()) continue;
            anyItems = true;

            TextView dayHeader = new TextView(context);
            String dateLabel = items.get(0).date;
            dayHeader.setText(fullDayName(day) + (dateLabel != null ? " • " + dateLabel : ""));
            dayHeader.setTypeface(null, Typeface.BOLD);
            dayHeader.setTextColor(Color.parseColor("#111827"));
            dayHeader.setTextSize(16);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            headerParams.topMargin = 20;
            headerParams.bottomMargin = 8;
            dayHeader.setLayoutParams(headerParams);
            weekContainer.addView(dayHeader);

            for (ScheduleLoader.ScheduleItem item : items) {
                weekContainer.addView(buildItemCard(item));
            }
        }

        if (!anyItems) {
            TextView empty = new TextView(context);
            empty.setText("No events found for this week.");
            empty.setGravity(Gravity.CENTER);
            empty.setTextColor(Color.parseColor("#6B7280"));
            empty.setPadding(0, 60, 0, 60);
            weekContainer.addView(empty);
        }
    }

    View buildItemCard(ScheduleLoader.ScheduleItem item) {
        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = 10;
        wrapper.setLayoutParams(params);

        View stripe = new View(context);
        stripe.setLayoutParams(new LinearLayout.LayoutParams(6, LinearLayout.LayoutParams.MATCH_PARENT));
        stripe.setBackgroundColor(colorFor(item.kind).border);
        wrapper.addView(stripe);

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(16, 14, 16, 14);
        inner.setBackgroundColor(colorFor(item.kind).bg);
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setText(item.subject != null ? item.subject : "");
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(colorFor(item.kind).text);
        title.setTextSize(15);
        inner.addView(title);

        TextView subtitle = new TextView(context);
        String room = item.roomName != null ? item.roomName : "-";
        subtitle.setText(room + "  •  " + formatTime(item.startTime) + " - " + formatTime(item.endTime));
        subtitle.setTextColor(colorFor(item.kind).text);
        subtitle.setTextSize(13);
        inner.addView(subtitle);

        if (item.kind.equals("reassignment") && item.originalRoom != null) {
            TextView moved = new TextView(context);
            moved.setText("Moved from " + item.originalRoom);
            moved.setTextColor(colorFor(item.kind).text);
            moved.setTextSize(12);
            inner.addView(moved);
        }

        wrapper.addView(inner);
        return wrapper;
    }

    static class Colors { int bg, border, text; Colors(int bg, int border, int text) { this.bg = bg; this.border = border; this.text = text; } }

    Colors colorFor(String kind) {
        switch (kind) {
            case "schedule": return new Colors(Color.parseColor("#EEF2FF"), Color.parseColor("#4F6EF7"), Color.parseColor("#3651D4"));
            case "event": return new Colors(Color.parseColor("#ECFDF5"), Color.parseColor("#34C77B"), Color.parseColor("#1A9E5C"));
            case "reservation": return new Colors(Color.parseColor("#FFF7ED"), Color.parseColor("#F97316"), Color.parseColor("#C2621A"));
            case "reassignment": return new Colors(Color.parseColor("#F5F3FF"), Color.parseColor("#8B5CF6"), Color.parseColor("#6D28D9"));
            default: return new Colors(Color.WHITE, Color.GRAY, Color.BLACK);
        }
    }

    String formatTime(String time) {
        if (time == null) return "";
        int[] p = ScheduleLoader.parseTimeParts(time);
        String suffix = p[0] >= 12 ? "PM" : "AM";
        int hh = p[0] % 12 == 0 ? 12 : p[0] % 12;
        return String.format(Locale.US, "%02d:%02d %s", hh, p[1], suffix);
    }

    String fullDayName(String abbrev) {
        switch (abbrev) {
            case "MON": return "Monday"; case "TUE": return "Tuesday"; case "WED": return "Wednesday";
            case "THU": return "Thursday"; case "FRI": return "Friday"; case "SAT": return "Saturday";
            default: return "Sunday";
        }
    }
}