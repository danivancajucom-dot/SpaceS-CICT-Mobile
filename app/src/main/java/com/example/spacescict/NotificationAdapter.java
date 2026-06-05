// NotificationAdapter.java
package com.example.spacescict;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotificationAdapter
        extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    List<NotificationModel> list;

    public NotificationAdapter(List<NotificationModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {

        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull ViewHolder holder,
            int position
    ) {

        NotificationModel item = list.get(position);

        holder.title.setText(item.title);
        holder.desc.setText(item.desc);
        holder.time.setText(item.time);
        holder.status.setText(item.status);

        holder.icon.setImageResource(item.icon);
        holder.iconBg.setBackgroundResource(item.bg);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        TextView title, desc, time, status;
        ImageView icon;
        LinearLayout iconBg;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);

            title = itemView.findViewById(R.id.titleText);
            desc = itemView.findViewById(R.id.descText);
            time = itemView.findViewById(R.id.timeText);
            status = itemView.findViewById(R.id.statusText);

            icon = itemView.findViewById(R.id.iconImage);
            iconBg = itemView.findViewById(R.id.iconContainer);
        }
    }
}