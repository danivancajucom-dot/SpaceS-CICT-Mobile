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
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.ViewHolder> {

    ArrayList<RoomModel> list;
    OnReserveClickListener listener;
    OnViewScheduleListener scheduleListener;

    public interface OnReserveClickListener {
        void onReserve(RoomModel room);
    }

    public interface OnViewScheduleListener {
        void onViewSchedule(RoomModel room);
    }

    public RoomAdapter(ArrayList<RoomModel> list, OnReserveClickListener listener, OnViewScheduleListener scheduleListener) {
        this.list = list;
        this.listener = listener;
        this.scheduleListener = scheduleListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_room, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int i) {
        RoomModel room = list.get(i);
        String status = room.status != null ? room.status : "";

        h.roomName.setText(room.roomName != null ? room.roomName : "");
        h.capacity.setText(room.capacity + " Capacity");
        h.floor.setText(room.floor != null ? room.floor : "");
        h.roomType.setText(room.roomType != null ? room.roomType : "");
        h.status.setText(status.toUpperCase());

        CardView statusCard = (CardView) h.status.getParent();

        if (status.equalsIgnoreCase("Available")) {
            statusCard.setCardBackgroundColor(Color.parseColor("#22C55E"));
            h.time.setText("Available Now");
        } else if (status.equalsIgnoreCase("Occupied")) {
            statusCard.setCardBackgroundColor(Color.parseColor("#EF4444"));
            h.time.setText(room.occupiedUntil != null && !room.occupiedUntil.isEmpty()
                    ? "Occupied until " + room.occupiedUntil : "Currently occupied");
        } else if (status.equalsIgnoreCase("Maintenance")) {
            statusCard.setCardBackgroundColor(Color.parseColor("#F97316"));
            h.time.setText("Under maintenance");
        } else {
            statusCard.setCardBackgroundColor(Color.parseColor("#64748B"));
            h.time.setText("Status unknown");
        }

        h.reserveBtn.setOnClickListener(v -> {
            if (listener != null) listener.onReserve(room);
        });
        h.reserveBtn.setEnabled(!status.equalsIgnoreCase("Maintenance"));

        h.viewScheduleBtn.setOnClickListener(v -> {
            if (scheduleListener != null) scheduleListener.onViewSchedule(room);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView roomName, status, capacity, floor, roomType, time;
        ImageView statusIcon;
        Button reserveBtn, viewScheduleBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            roomName = itemView.findViewById(R.id.roomName);
            status = itemView.findViewById(R.id.statusText);
            capacity = itemView.findViewById(R.id.capacityText);
            floor = itemView.findViewById(R.id.floorText);
            roomType = itemView.findViewById(R.id.roomTypeText);
            time = itemView.findViewById(R.id.timeText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            reserveBtn = itemView.findViewById(R.id.reserveBtn);
            viewScheduleBtn = itemView.findViewById(R.id.viewScheduleBtn);
        }
    }
}