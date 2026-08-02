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

public class ReservationAdapter extends RecyclerView.Adapter<ReservationAdapter.ViewHolder> {

    ArrayList<ReservationModel> list;
    OnItemClickListener listener;

    public interface OnItemClickListener {
        void onClick(ReservationModel model);
    }

    public void setOnItemClick(OnItemClickListener listener) {
        this.listener = listener;
    }

    public ReservationAdapter(ArrayList<ReservationModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_reservation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int i) {
        ReservationModel model = list.get(i);

        h.status.setText(model.status);
        h.room.setText(model.roomName);
        h.subject.setText(model.courseTitle);
        h.image.setImageResource(model.image);

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(model);
        });

        if ("Pending".equalsIgnoreCase(model.status)) {
            h.status.setBackgroundResource(R.drawable.status_pending);
            h.status.setTextColor(Color.parseColor("#D97706"));
        } else if ("Approved".equalsIgnoreCase(model.status)) {
            h.status.setBackgroundResource(R.drawable.status_approved);
            h.status.setTextColor(Color.parseColor("#16A34A"));
        } else {
            h.status.setBackgroundResource(R.drawable.status_denied);
            h.status.setTextColor(Color.parseColor("#DC2626"));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView status, room, subject;
        ImageView image;
        Button detailsBtn;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            status = itemView.findViewById(R.id.statusText);
            room = itemView.findViewById(R.id.roomName);
            subject = itemView.findViewById(R.id.subjectText);
            image = itemView.findViewById(R.id.roomImage);
            detailsBtn = itemView.findViewById(R.id.detailsBtn);
        }
    }
}