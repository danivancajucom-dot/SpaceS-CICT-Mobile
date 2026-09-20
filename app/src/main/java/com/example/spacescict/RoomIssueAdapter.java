package com.example.spacescict;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class RoomIssueAdapter extends RecyclerView.Adapter<RoomIssueAdapter.ViewHolder> {
    private final List<RoomIssueModel> issues;

    public RoomIssueAdapter(List<RoomIssueModel> issues) {
        this.issues = issues;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_room_issue, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RoomIssueModel issue = issues.get(position);

        holder.roomName.setText(valueOr(issue.roomName, "Unknown Room"));
        holder.category.setText(valueOr(issue.category, "General"));
        holder.description.setText(valueOr(issue.description, "No description provided."));
        holder.reporterName.setText(valueOr(issue.reporterName, "Faculty"));
        holder.createdAt.setText(formatTimestamp(issue.createdAt));

        // Category Icon
        holder.icon.setImageResource(getCategoryIcon(issue.category));

        // Severity Badge
        String sev = valueOr(issue.severity, "Medium");
        holder.severityBadge.setText(sev.toUpperCase());
        int sColor = severityColor(sev);
        holder.severityBadge.setTextColor(sColor);
        GradientDrawable sevBg = new GradientDrawable();
        sevBg.setCornerRadius(dp(holder.itemView.getContext(), 4));
        sevBg.setStroke(dp(holder.itemView.getContext(), 1), sColor);
        holder.severityBadge.setBackground(sevBg);

        // Status Badge
        String stat = valueOr(issue.status, "Pending");
        holder.statusText.setText(stat.toUpperCase());
        int stColor = statusColor(stat);
        int stSoft = statusSoftColor(stat);
        holder.statusCard.setCardBackgroundColor(stSoft);
        holder.statusText.setTextColor(stColor);

        // Photo
        if (issue.imageUrl != null && !issue.imageUrl.isEmpty()) {
            holder.photo.setVisibility(View.VISIBLE);
            holder.photoPlaceholder.setVisibility(View.GONE);
            Glide.with(holder.itemView.getContext()).load(issue.imageUrl).into(holder.photo);
        } else {
            holder.photo.setVisibility(View.GONE);
            holder.photoPlaceholder.setVisibility(View.VISIBLE);
        }

        // Activity Log / Clerk Notes
        if (issue.clerkNotes != null && !issue.clerkNotes.trim().isEmpty()) {
            holder.activityLog.setVisibility(View.VISIBLE);
            holder.activityLog.setText(issue.clerkNotes);
        } else {
            holder.activityLog.setVisibility(View.GONE);
        }
    }

    private int getCategoryIcon(String category) {
        if (category == null) return R.drawable.ic_other;
        switch (category) {
            case "Electrical": return R.drawable.ic_electrical;
            case "Plumbing / Leak": return R.drawable.ic_plumbing;
            case "Network / Internet": return R.drawable.ic_network;
            case "Equipment": return R.drawable.ic_video_label;
            case "Air Conditioning": return R.drawable.ic_ac_unit;
            case "Furniture": return R.drawable.ic_weekend;
            case "Cleanliness": return R.drawable.ic_cleaning_services;
            case "Security": return R.drawable.ic_security;
            default: return R.drawable.ic_other;
        }
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) return "Just now";
        return new SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US).format(timestamp.toDate());
    }

    private static int statusColor(String status) {
        if ("Resolved".equalsIgnoreCase(status)) return Color.parseColor("#16A34A");
        if ("In Progress".equalsIgnoreCase(status) || "Acknowledged".equalsIgnoreCase(status)) return Color.parseColor("#2563EB");
        return Color.parseColor("#EA580C"); // Pending
    }

    private static int statusSoftColor(String status) {
        if ("Resolved".equalsIgnoreCase(status)) return Color.parseColor("#F0FDF4");
        if ("In Progress".equalsIgnoreCase(status) || "Acknowledged".equalsIgnoreCase(status)) return Color.parseColor("#EFF6FF");
        return Color.parseColor("#FFF7ED"); // Pending
    }

    private static int severityColor(String severity) {
        if ("Urgent".equalsIgnoreCase(severity)) return Color.parseColor("#DC2626");
        if ("High".equalsIgnoreCase(severity)) return Color.parseColor("#EA580C");
        if ("Low".equalsIgnoreCase(severity)) return Color.parseColor("#16A34A");
        return Color.parseColor("#D97706"); // Medium
    }

    private int dp(android.content.Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public int getItemCount() { return issues.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView roomName, category, description, reporterName, createdAt, severityBadge, statusText, activityLog;
        ImageView icon, photo;
        CardView statusCard;
        View photoPlaceholder;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            roomName = itemView.findViewById(R.id.issueRoomText);
            category = itemView.findViewById(R.id.issueCategoryText);
            description = itemView.findViewById(R.id.issueDescriptionText);
            reporterName = itemView.findViewById(R.id.issueReporterName);
            createdAt = itemView.findViewById(R.id.issueTimeText);
            severityBadge = itemView.findViewById(R.id.issueSeverityBadge);
            statusText = itemView.findViewById(R.id.issueStatusText);
            statusCard = itemView.findViewById(R.id.issueStatusCard);
            activityLog = itemView.findViewById(R.id.issueActivityLog);
            icon = itemView.findViewById(R.id.issueIcon);
            photo = itemView.findViewById(R.id.issuePhoto);
            photoPlaceholder = itemView.findViewById(R.id.issuePhotoPlaceholder);
        }
    }
}
