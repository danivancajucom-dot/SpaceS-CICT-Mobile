package com.example.spacescict;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.ViewHolder> {
    private final List<ActivityLogModel> list;

    public ActivityLogAdapter(List<ActivityLogModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_activity_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityLogModel log = list.get(position);
        holder.action.setText(log.action == null ? "Activity" : log.action);
        holder.target.setText(log.target == null ? "" : log.target);
        holder.time.setText(formatTime(log.timestamp));
        String status = log.status == null ? "" : log.status.toLowerCase(Locale.US);
        boolean failed = status.contains("fail") || status.contains("denied")
                || status.contains("reject");
        holder.status.setText(log.status == null ? "" : log.status.toUpperCase(Locale.US));
        holder.status.setTextColor(Color.parseColor(failed ? "#EF4444" : "#22C55E"));
        holder.icon.setImageResource(iconFor(log.actionType));
        ((CardView) holder.icon.getParent()).setCardBackgroundColor(
                Color.parseColor("#FFF1E6"));
    }

    private int iconFor(String actionType) {
        if (actionType == null) return R.drawable.ic_edit;
        switch (actionType.toLowerCase(Locale.US)) {
            case "edit":
            case "update": return R.drawable.ic_edit;
            case "cancel":
            case "denied": return R.drawable.ic_back;
            case "success": return R.drawable.ic_check;
            default: return R.drawable.ic_edit;
        }
    }

    private String formatTime(com.google.firebase.Timestamp timestamp) {
        if (timestamp == null) return "";
        long seconds = (System.currentTimeMillis() - timestamp.toDate().getTime()) / 1000;
        if (seconds < 0) return "just now";
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 7) return days + "d ago";
        return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(timestamp.toDate());
    }

    @Override public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView action, target, time, status;

        ViewHolder(@NonNull View view) {
            super(view);
            icon = view.findViewById(R.id.activityIcon);
            action = view.findViewById(R.id.activityAction);
            target = view.findViewById(R.id.activityTarget);
            time = view.findViewById(R.id.activityTime);
            status = view.findViewById(R.id.activityStatus);
        }
    }
}