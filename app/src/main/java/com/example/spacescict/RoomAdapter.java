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
        h.capacity.setText(room.capacity + " Capacity");
        h.image.setImageResource(room.image);

        String status = room.status != null ? room.status : "";
        h.status.setText(status.toUpperCase());

        if (status.equalsIgnoreCase("Available")) {
            h.status.setTextColor(Color.parseColor("#22C55E"));
            h.time.setText(room.floor != null ? room.floor + " Floor" : "");
        } else if (status.equalsIgnoreCase("Occupied")) {
            h.status.setTextColor(Color.parseColor("#DC2626"));
            h.time.setText(room.occupiedUntil != null && !room.occupiedUntil.isEmpty()
                    ? "Until " + room.occupiedUntil : "Occupied");
        } else if (status.equalsIgnoreCase("Maintenance")) {
            h.status.setTextColor(Color.parseColor("#F59E0B"));
            h.time.setText("Under Maintenance");
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
        TextView roomName, status, capacity, time;
        ImageView image;
        Button reserveBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            roomName = itemView.findViewById(R.id.roomName);
            status = itemView.findViewById(R.id.statusText);
            capacity = itemView.findViewById(R.id.capacityText);
            time = itemView.findViewById(R.id.timeText);
            image = itemView.findViewById(R.id.roomImage);
            reserveBtn = itemView.findViewById(R.id.reserveBtn);
        }
    }
}