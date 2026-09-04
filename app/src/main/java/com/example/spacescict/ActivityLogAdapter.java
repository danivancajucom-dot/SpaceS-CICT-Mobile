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

import java.util.List;
import java.util.Locale;

public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.ViewHolder> {

    List<ActivityLogModel> list;

    public ActivityLogAdapter(List<ActivityLogModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_activity_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int i) {
        ActivityLogModel log = list.get(i);

        h.action.setText(log.action != null ? log.action : "Activity");
        h.target.setText(log.target != null ? log.target : "");
        h.time.setText(formatTime(log.timestamp));

        String status = log.status != null ? log.status.toLowerCase(Locale.US) : "";
        boolean failed = status.contains("fail") || status.contains("denied") || status.contains("reject");
        h.status.setText(log.status != null ? log.status.toUpperCase() : "");
        h.status.setTextColor(failed ? Color.parseColor("#EF4444") : Color.parseColor("#22C55E"));

        h.icon.setImageResource(iconFor(log.actionType));

        CardView iconCard = (CardView) h.icon.getParent();
        iconCard.setCardBackgroundColor(Color.parseColor("#FFF1E6"));
    }

    int iconFor(String actionType) {
        if (actionType == null) return R.drawable.ic_edit;
        switch (actionType.toLowerCase(Locale.US)) {
            case "edit":
            case "update":
                return R.drawable.ic_edit;
            case "cancel":
                return R.drawable.ic_back;
            case "success":
                return R.drawable.ic_check;
            case "denied":
                return R.drawable.ic_back;
            default:
                return R.drawable.ic_edit;
        }
    }

    String formatTime(com.google.firebase.Timestamp ts) {
        if (ts == null) return "";
        long diffMs = System.currentTimeMillis() - ts.toDate().getTime();
        long diffSec = diffMs / 1000;
        if (diffSec < 0) return "just now";
        if (diffSec < 60) return diffSec + "s ago";
        long diffMin = diffSec / 60;
        if (diffMin < 60) return diffMin + "m ago";
        long diffHr = diffMin / 60;
        if (diffHr < 24) return diffHr + "h ago";
        long diffDay = diffHr / 24;
        if (diffDay < 7) return diffDay + "d ago";
        return new java.text.SimpleDateFormat("MMM d, yyyy", Locale.US).format(ts.toDate());
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView icon;
        TextView action, target, time, status;

        ViewHolder(@NonNull View v) {
            super(v);
            icon = v.findViewById(R.id.activityIcon);
            action = v.findViewById(R.id.activityAction);
            target = v.findViewById(R.id.activityTarget);
            time = v.findViewById(R.id.activityTime);
            status = v.findViewById(R.id.activityStatus);
        }
    }
}