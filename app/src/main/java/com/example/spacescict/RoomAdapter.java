package com.example.spacescict;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.ViewHolder> {

    ArrayList<RoomModel> list;
    OnReserveClickListener listener;

    public interface OnReserveClickListener {
        void onReserve(RoomModel room);
    }

    public RoomAdapter(ArrayList<RoomModel> list, OnReserveClickListener listener) {
        this.list = list;
        this.listener = listener;
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

        h.roomName.setText(room.roomName);
        h.image.setImageResource(room.image);

        // Capacity — always shown, this is the fact you need before reserving regardless of status
        h.capacity.setText(room.capacity > 0 ? room.capacity + " Capacity" : "Capacity N/A");

        // Floor — always shown too, another pre-reservation fact
        h.floor.setText(room.floor != null && !room.floor.isEmpty() ? room.floor : "Floor N/A");

        // Room type, e.g. "Conference Room", "Computer Lab"
        h.roomType.setText(room.roomType != null && !room.roomType.isEmpty() ? room.roomType : "");

        String status = room.status != null ? room.status : "";
        h.status.setText(status.toUpperCase());

        if (status.equalsIgnoreCase("Available")) {
            h.status.setTextColor(Color.parseColor("#22C55E"));
            h.time.setText("Available now");
        } else if (status.equalsIgnoreCase("Occupied")) {
            h.status.setTextColor(Color.parseColor("#DC2626"));
            h.time.setText(room.occupiedUntil != null && !room.occupiedUntil.isEmpty()
                    ? "Occupied until " + room.occupiedUntil : "Currently occupied");
        } else if (status.equalsIgnoreCase("Maintenance")) {
            h.status.setTextColor(Color.parseColor("#F59E0B"));
            h.time.setText("Under maintenance");
        } else {
            h.status.setTextColor(Color.parseColor("#64748B"));
            h.time.setText("Status unknown");
        }

        h.reserveBtn.setOnClickListener(v -> {
            if (listener != null) listener.onReserve(room);
        });
        h.reserveBtn.setEnabled(!status.equalsIgnoreCase("Maintenance"));
        h.reserveBtn.setAlpha(status.equalsIgnoreCase("Maintenance") ? 0.5f : 1f);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView roomName, status, capacity, floor, roomType, time;
        ImageView image;
        Button reserveBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            roomName = itemView.findViewById(R.id.roomName);
            status = itemView.findViewById(R.id.statusText);
            capacity = itemView.findViewById(R.id.capacityText);
            floor = itemView.findViewById(R.id.floorText);
            roomType = itemView.findViewById(R.id.roomTypeText);
            time = itemView.findViewById(R.id.timeText);
            image = itemView.findViewById(R.id.roomImage);
            reserveBtn = itemView.findViewById(R.id.reserveBtn);
        }
    }
}