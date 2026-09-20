package com.example.spacescict;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.ViewHolder> {
    private final ArrayList<RoomModel> list;
    private final OnReserveClickListener reserveListener;
    private final OnViewScheduleListener scheduleListener;
    private final OnWatchClickListener watchListener;

    public interface OnReserveClickListener { void onReserve(RoomModel room); }
    public interface OnViewScheduleListener { void onViewSchedule(RoomModel room); }
    public interface OnWatchClickListener { void onToggleWatch(RoomModel room); }

    public RoomAdapter(ArrayList<RoomModel> list, OnReserveClickListener reserveListener,
                       OnViewScheduleListener scheduleListener,
                       OnWatchClickListener watchListener) {
        this.list = list;
        this.reserveListener = reserveListener;
        this.scheduleListener = scheduleListener;
        this.watchListener = watchListener;
    }

    public RoomAdapter(ArrayList<RoomModel> list, OnReserveClickListener reserveListener,
                       OnViewScheduleListener scheduleListener) {
        this(list, reserveListener, scheduleListener, null);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_room, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RoomModel room = list.get(position);
        String status = room.status == null ? "" : room.status;
        boolean maintenance = isMaintenance(status);

        holder.roomName.setText(room.roomName == null ? "" : room.roomName);
        holder.capacity.setText(room.capacity + " Capacity");
        String location = room.building == null ? "" : room.building;
        if (room.floor != null && !room.floor.isEmpty()) {
            if (!location.isEmpty()) location += " · ";
            location += room.floor;
        }
        holder.floor.setText(location);
        holder.roomType.setText(room.roomType == null ? "" : room.roomType);
        holder.status.setText(status.toUpperCase());

        if (status.equalsIgnoreCase("Available")) {
            setStatus(holder, ContextCompat.getColor(holder.itemView.getContext(), R.color.success), "Available Now");
        } else if (status.equalsIgnoreCase("Occupied")) {
            setStatus(holder, ContextCompat.getColor(holder.itemView.getContext(), R.color.danger), room.occupiedUntil == null || room.occupiedUntil.isEmpty()
                    ? "Currently occupied" : "Occupied until " + room.occupiedUntil);
        } else if (maintenance) {
            setStatus(holder, ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_orange), "Under maintenance");
        } else {
            setStatus(holder, ContextCompat.getColor(holder.itemView.getContext(), R.color.ink_400), "Status unknown");
        }

        holder.reserveButton.setOnClickListener(v -> {
            if (!maintenance && reserveListener != null) reserveListener.onReserve(room);
        });
        holder.reserveButton.setEnabled(!maintenance);
        holder.viewScheduleButton.setOnClickListener(v -> {
            if (scheduleListener != null) scheduleListener.onViewSchedule(room);
        });
        if (holder.watchButton != null) {
            // Stays enabled even when the room is Available (matches web): tapping
            // it then just surfaces the "this room is already available" toast from
            // RoomsPage.toggleWatch() instead of being a dead, unpressable button.
            holder.watchButton.setText(room.watched ? "Watching" : "Notify me");
            holder.watchButton.setEnabled(!maintenance);
            holder.watchButton.setOnClickListener(v -> {
                if (watchListener != null) watchListener.onToggleWatch(room);
            });
        }
    }

    private static void setStatus(ViewHolder holder, int color, String time) {
        ((CardView) holder.status.getParent()).setCardBackgroundColor(color);
        holder.time.setText(time);
    }

    private static boolean isMaintenance(String status) {
        return status.equalsIgnoreCase("Maintenance")
                || status.equalsIgnoreCase("Under Maintenance");
    }

    @Override public int getItemCount() { return list.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView roomName, status, capacity, floor, roomType, time;
        ImageView statusIcon;
        View reserveButton, viewScheduleButton;
        TextView watchButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            roomName = itemView.findViewById(R.id.roomName);
            status = itemView.findViewById(R.id.statusText);
            capacity = itemView.findViewById(R.id.capacityText);
            floor = itemView.findViewById(R.id.floorText);
            roomType = itemView.findViewById(R.id.roomTypeText);
            time = itemView.findViewById(R.id.timeText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            reserveButton = itemView.findViewById(R.id.reserveButton);
            viewScheduleButton = itemView.findViewById(R.id.viewScheduleButton);
            watchButton = itemView.findViewById(R.id.watchButton);
        }
    }
}