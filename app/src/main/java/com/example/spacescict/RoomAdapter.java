package com.example.spacescict;

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

    public RoomAdapter(ArrayList<RoomModel> list) {
        this.list = list;
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

        h.roomName.setText(room.name);
        h.status.setText(room.status);
        h.capacity.setText(room.capacity);
        h.time.setText(room.time);
        h.image.setImageResource(room.image);
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