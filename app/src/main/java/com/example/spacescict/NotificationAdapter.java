package com.example.spacescict;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    List<NotificationModel> list;
    SimpleDateFormat fmt = new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault());
    OnActionListener listener;

    public interface OnActionListener {
        void onOpenDetail(NotificationModel n, int position);
        void onArchive(NotificationModel n, int position);
        void onViewAction(NotificationModel n);
    }

    public NotificationAdapter(List<NotificationModel> list, OnActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationModel item = list.get(position);

        holder.title.setText(item.title != null ? item.title : "");
        holder.desc.setText(item.desc != null ? item.desc : "");
        holder.status.setText(item.badge != null ? item.badge : "");
        holder.time.setText(item.createdAt != null ? fmt.format(item.createdAt.toDate()) : "");

        holder.itemView.setAlpha(item.unread ? 1f : 0.6f);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpenDetail(item, holder.getAdapterPosition());
        });

        holder.archiveBtn.setOnClickListener(v -> {
            if (listener != null) listener.onArchive(item, holder.getAdapterPosition());
        });

        boolean isReassignment = "room-reassignment".equals(item.type) && item.assignmentId != null;
        holder.viewActionBtn.setVisibility(isReassignment ? View.VISIBLE : View.GONE);
        holder.viewActionBtn.setOnClickListener(v -> {
            if (listener != null) listener.onViewAction(item);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, desc, time, status;
        ImageView icon, archiveBtn;
        Button viewActionBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.titleText);
            desc = itemView.findViewById(R.id.descText);
            time = itemView.findViewById(R.id.timeText);
            status = itemView.findViewById(R.id.statusText);
            icon = itemView.findViewById(R.id.iconImage);
            archiveBtn = itemView.findViewById(R.id.archiveBtn);
            viewActionBtn = itemView.findViewById(R.id.viewActionBtn);
        }
    }
}