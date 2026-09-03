package com.example.spacescict;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
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
        String status = model.status != null ? model.status.toLowerCase().trim() : "";

        h.roomName.setText(model.roomName != null ? model.roomName : "");
        h.subject.setText(model.courseTitle != null ? model.courseTitle : "");
        h.dateTime.setText((model.date != null ? model.date : "") + " | "
                + (model.startTime != null ? model.startTime : "") + " - " + (model.endTime != null ? model.endTime : ""));
        h.status.setText(model.status != null ? model.status.toUpperCase() : "");

        CardView statusCard = (CardView) h.status.getParent();
        if (status.equals("pending")) {
            statusCard.setCardBackgroundColor(Color.parseColor("#F97316"));
        } else if (status.equals("approved")) {
            statusCard.setCardBackgroundColor(Color.parseColor("#22C55E"));
        } else if (status.equals("cancelled")) {
            statusCard.setCardBackgroundColor(Color.parseColor("#6B7280"));
        } else {
            statusCard.setCardBackgroundColor(Color.parseColor("#EF4444"));
        }

        boolean isDenied = status.equals("rejected") || status.equals("denied");
        h.denialReasonCard.setVisibility(isDenied ? View.VISIBLE : View.GONE);
        if (isDenied) {
            String reason = model.denialReason != null && !model.denialReason.isEmpty()
                    ? model.denialReason : "No reason provided.";
            h.denialReasonText.setText(reason);
        }

        h.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(model);
        });
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView status, roomName, subject, dateTime, denialReasonText, detailsBtn;
        CardView denialReasonCard;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            status = itemView.findViewById(R.id.statusText);
            roomName = itemView.findViewById(R.id.roomName);
            subject = itemView.findViewById(R.id.subjectText);
            dateTime = itemView.findViewById(R.id.dateTimeText);
            denialReasonCard = itemView.findViewById(R.id.denialReasonCard);
            denialReasonText = itemView.findViewById(R.id.denialReasonText);
            detailsBtn = itemView.findViewById(R.id.detailsBtn);
        }
    }
}