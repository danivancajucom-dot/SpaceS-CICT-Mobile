package com.example.spacescict;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class UpcomingAdapter extends RecyclerView.Adapter<UpcomingAdapter.ViewHolder> {

    List<ScheduleLoader.ScheduleItem> list;

    public UpcomingAdapter(List<ScheduleLoader.ScheduleItem> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_upcoming_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int i) {
        ScheduleLoader.ScheduleItem item = list.get(i);
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(item.occurrenceMillis);

        String[] dayAbbrev = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        h.dayLabel.setText(dayAbbrev[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]);
        h.dateLabel.setText(String.valueOf(c.get(java.util.Calendar.DAY_OF_MONTH)));

        String tag = "";
        if (item.kind.equals("reassignment")) tag = " (Moved)";
        else if (item.kind.equals("reservation")) tag = " (Reservation)";
        h.subjectLabel.setText(item.subject + tag);

        h.roomTimeLabel.setText(item.roomName + " • " + formatTime(item.startTime));
    }

    String formatTime(String time) {
        int[] p = ScheduleLoader.parseTimeParts(time);
        String suffix = p[0] >= 12 ? "PM" : "AM";
        int hh = p[0] % 12 == 0 ? 12 : p[0] % 12;
        return String.format(Locale.US, "%02d:%02d %s", hh, p[1], suffix);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView dayLabel, dateLabel, subjectLabel, roomTimeLabel;
        ViewHolder(@NonNull View v) {
            super(v);
            dayLabel = v.findViewById(R.id.dayLabel);
            dateLabel = v.findViewById(R.id.dateLabel);
            subjectLabel = v.findViewById(R.id.subjectLabel);
            roomTimeLabel = v.findViewById(R.id.roomTimeLabel);
        }
    }
}