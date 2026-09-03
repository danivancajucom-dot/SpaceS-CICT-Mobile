package com.example.spacescict;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class BroadcastAdapter extends RecyclerView.Adapter<BroadcastAdapter.ViewHolder> {

    List<BroadcastModel> list;
    OnActionListener listener;

    public interface OnActionListener {
        void onToggleLike(BroadcastModel msg);
        void onToggleLove(BroadcastModel msg);
        void onOpenFile(String url);
        void onOpenLink(String url);
        void onOpenImage(String url);
    }

    public BroadcastAdapter(List<BroadcastModel> list, OnActionListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.recycler_broadcast_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        BroadcastModel msg = list.get(position);
        String uid = FirebaseAuth.getInstance().getUid();
        boolean isMine = uid != null && uid.equals(msg.senderId);

        h.senderName.setText(isMine ? "You" : msg.senderName);
        h.senderRole.setText(msg.senderRole != null ? msg.senderRole : "");

        if (msg.imageUrl != null && !msg.imageUrl.isEmpty()) {
            h.image.setVisibility(View.VISIBLE);
            Glide.with(h.itemView.getContext()).load(msg.imageUrl).into(h.image);
            h.image.setOnClickListener(v -> { if (listener != null) listener.onOpenImage(msg.imageUrl); });
        } else {
            h.image.setVisibility(View.GONE);
        }

        if (msg.fileUrl != null && !msg.fileUrl.isEmpty()) {
            h.fileRow.setVisibility(View.VISIBLE);
            h.fileName.setText(msg.fileName != null ? msg.fileName : "File");
            h.fileRow.setOnClickListener(v -> { if (listener != null) listener.onOpenFile(msg.fileUrl); });
        } else {
            h.fileRow.setVisibility(View.GONE);
        }

        if (msg.linkUrl != null && !msg.linkUrl.isEmpty()) {
            h.linkRow.setVisibility(View.VISIBLE);
            h.linkTitle.setText(msg.linkTitle != null ? msg.linkTitle : msg.linkUrl);
            h.linkUrlText.setText(msg.linkUrl);
            h.linkRow.setOnClickListener(v -> { if (listener != null) listener.onOpenLink(msg.linkUrl); });
        } else {
            h.linkRow.setVisibility(View.GONE);
        }

        if (msg.content != null && !msg.content.trim().isEmpty()) {
            h.content.setVisibility(View.VISIBLE);
            h.content.setText(msg.content);
        } else {
            h.content.setVisibility(View.GONE);
        }

        h.time.setText(formatTimestamp(msg.createdAt));

        boolean iLiked = uid != null && msg.likeUids.contains(uid);
        boolean iLoved = uid != null && msg.loveUids.contains(uid);

        h.likeBtn.setText("\uD83D\uDC4D " + msg.likeUids.size());
        h.likeBtn.setTextColor(iLiked ? Color.parseColor("#F97316") : Color.parseColor("#4B5563"));
        h.likeBtn.setOnClickListener(v -> { if (listener != null) listener.onToggleLike(msg); });

        h.loveBtn.setText("\u2764\uFE0F " + msg.loveUids.size());
        h.loveBtn.setTextColor(iLoved ? Color.parseColor("#F97316") : Color.parseColor("#4B5563"));
        h.loveBtn.setOnClickListener(v -> { if (listener != null) listener.onToggleLove(msg); });
    }

    String formatTimestamp(com.google.firebase.Timestamp ts) {
        if (ts == null) return "";
        return new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US).format(ts.toDate());
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView senderName, senderRole, fileName, linkTitle, linkUrlText, content, time, likeBtn, loveBtn;
        ImageView image;
        LinearLayout fileRow, linkRow;

        ViewHolder(@NonNull View v) {
            super(v);
            senderName = v.findViewById(R.id.senderName);
            senderRole = v.findViewById(R.id.senderRole);
            image = v.findViewById(R.id.broadcastImage);
            fileRow = v.findViewById(R.id.fileAttachmentRow);
            fileName = v.findViewById(R.id.fileName);
            linkRow = v.findViewById(R.id.linkPreviewRow);
            linkTitle = v.findViewById(R.id.linkTitle);
            linkUrlText = v.findViewById(R.id.linkUrl);
            content = v.findViewById(R.id.messageContent);
            time = v.findViewById(R.id.messageTime);
            likeBtn = v.findViewById(R.id.likeBtn);
            loveBtn = v.findViewById(R.id.loveBtn);
        }
    }
}