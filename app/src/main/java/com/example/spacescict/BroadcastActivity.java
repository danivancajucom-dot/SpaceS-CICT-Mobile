package com.example.spacescict;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class BroadcastActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    BroadcastAdapter adapter;
    List<BroadcastModel> fullList = new ArrayList<>();
    List<BroadcastModel> visibleList = new ArrayList<>();
    TextView noBroadcastText;
    String myRole = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_broadcast);

        ImageView backBtn = findViewById(R.id.backBtn);
        backBtn.setOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.broadcastRecycler);
        noBroadcastText = findViewById(R.id.noBroadcastText);

        adapter = new BroadcastAdapter(visibleList, new BroadcastAdapter.OnActionListener() {
            @Override
            public void onToggleLike(BroadcastModel msg) { toggleReaction(msg, "like"); }

            @Override
            public void onToggleLove(BroadcastModel msg) { toggleReaction(msg, "love"); }

            @Override
            public void onOpenFile(String url) { openUrl(url); }

            @Override
            public void onOpenLink(String url) { openUrl(url); }

            @Override
            public void onOpenImage(String url) { openUrl(url); }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    myRole = userDoc.getString("role") != null ? userDoc.getString("role") : "";
                    loadMessages(uid);
                });
    }

    void openUrl(String url) {
        if (url == null || url.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open link", Toast.LENGTH_SHORT).show();
        }
    }

    void loadMessages(String uid) {
        FirebaseFirestore.getInstance().collection("broadcastChannels")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    fullList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        BroadcastModel m = new BroadcastModel();
                        m.id = doc.getId();
                        m.content = doc.getString("content");
                        m.senderId = doc.getString("senderId");
                        m.senderName = doc.getString("senderName");
                        m.senderRole = doc.getString("senderRole");
                        m.recipient = doc.getString("recipient");
                        m.imageUrl = doc.getString("imageUrl");
                        m.fileUrl = doc.getString("fileUrl");
                        m.fileName = doc.getString("fileName");
                        m.createdAt = doc.getTimestamp("createdAt");

                        Object linkPreview = doc.get("linkPreview");
                        if (linkPreview instanceof java.util.Map) {
                            java.util.Map<?, ?> lp = (java.util.Map<?, ?>) linkPreview;
                            Object title = lp.get("title");
                            Object url = lp.get("url");
                            Object image = lp.get("image");
                            m.linkTitle = title != null ? title.toString() : null;
                            m.linkUrl = url != null ? url.toString() : null;
                            m.linkImage = image != null ? image.toString() : null;
                        }

                        Object reactions = doc.get("reactions");
                        if (reactions instanceof java.util.Map) {
                            java.util.Map<?, ?> r = (java.util.Map<?, ?>) reactions;
                            Object like = r.get("like");
                            Object love = r.get("love");
                            if (like instanceof List) {
                                for (Object o : (List<?>) like) m.likeUids.add(o.toString());
                            }
                            if (love instanceof List) {
                                for (Object o : (List<?>) love) m.loveUids.add(o.toString());
                            }
                        }

                        boolean visible = "All Staffs".equals(m.recipient)
                                || uid.equals(m.senderId)
                                || (m.recipient != null && m.recipient.equalsIgnoreCase(myRole));

                        if (visible) fullList.add(m);
                    }

                    visibleList.clear();
                    visibleList.addAll(fullList);
                    adapter.notifyDataSetChanged();

                    noBroadcastText.setVisibility(visibleList.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(visibleList.isEmpty() ? View.GONE : View.VISIBLE);

                    if (!visibleList.isEmpty()) {
                        recyclerView.scrollToPosition(visibleList.size() - 1);
                    }
                });
    }

    void toggleReaction(BroadcastModel msg, String type) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        List<String> currentList = type.equals("like") ? msg.likeUids : msg.loveUids;
        boolean hasReacted = currentList.contains(uid);

        FirebaseFirestore.getInstance().collection("broadcastChannels").document(msg.id)
                .update("reactions." + type,
                        hasReacted ? FieldValue.arrayRemove(uid) : FieldValue.arrayUnion(uid))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}