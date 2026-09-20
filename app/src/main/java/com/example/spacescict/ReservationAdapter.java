package com.example.spacescict;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ReservationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_ITEM = 0;
    private static final int TYPE_FOOTER = 1;

    ArrayList<ReservationModel> list;
    OnItemClickListener listener;

    /** Set when more results exist beyond what's currently shown; draws a "Load more" row. */
    private boolean hasMore = false;
    private Runnable onLoadMore;

    public interface OnItemClickListener {
        void onClick(ReservationModel model);
    }

    public void setOnItemClick(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setOnLoadMore(Runnable onLoadMore) {
        this.onLoadMore = onLoadMore;
    }

    public void setHasMore(boolean hasMore) {
        if (this.hasMore != hasMore) {
            this.hasMore = hasMore;
        }
    }

    public ReservationAdapter(ArrayList<ReservationModel> list) {
        this.list = list;
    }

    @Override
    public int getItemViewType(int position) {
        return position == list.size() ? TYPE_FOOTER : TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return list.size() + (hasMore ? 1 : 0);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_FOOTER) {
            return new FooterViewHolder(buildFooterView(parent));
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_reservation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int i) {
        if (holder instanceof FooterViewHolder) return; // footer is fully built at creation

        ViewHolder h = (ViewHolder) holder;
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

    private View buildFooterView(ViewGroup parent) {
        TextView footer = new TextView(parent.getContext());
        footer.setText("Load more reservations");
        footer.setGravity(Gravity.CENTER);
        footer.setTextColor(Color.parseColor("#F97316"));
        footer.setTypeface(null, android.graphics.Typeface.BOLD);
        footer.setTextSize(13);
        int padV = (int) (16 * parent.getResources().getDisplayMetrics().density);
        footer.setPadding(0, padV, 0, padV);
        footer.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
        footer.setOnClickListener(v -> {
            if (onLoadMore != null) onLoadMore.run();
        });
        return footer;
    }

    static class FooterViewHolder extends RecyclerView.ViewHolder {
        FooterViewHolder(@NonNull View itemView) {
            super(itemView);
        }
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