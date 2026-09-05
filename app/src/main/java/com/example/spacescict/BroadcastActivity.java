package com.example.spacescict;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class BroadcastActivity {

    private final Context context;

    private RecyclerView recyclerView;
    private BroadcastAdapter adapter;

    private final List<BroadcastModel> fullList = new ArrayList<>();
    private final List<BroadcastModel> visibleList = new ArrayList<>();

    private TextView noBroadcastText;

    private String myRole = "";
    private boolean firstLoadDone = false;

    public BroadcastActivity(Context context, View view) {

        this.context = context;

        recyclerView = view.findViewById(R.id.broadcastRecycler);
        noBroadcastText = view.findViewById(R.id.noBroadcastText);

        if (recyclerView == null) {
            Toast.makeText(
                    context,
                    "Announcement list could not be loaded.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        adapter = new BroadcastAdapter(
                visibleList,
                new BroadcastAdapter.OnActionListener() {

                    @Override
                    public void onToggleLike(BroadcastModel msg) {
                        toggleReaction(msg, "like");
                    }

                    @Override
                    public void onToggleLove(BroadcastModel msg) {
                        toggleReaction(msg, "love");
                    }

                    @Override
                    public void onOpenFile(String url) {
                        openUrl(url);
                    }

                    @Override
                    public void onOpenLink(String url) {
                        openUrl(url);
                    }

                    @Override
                    public void onOpenImage(String url) {
                        openUrl(url);
                    }
                }
        );

        recyclerView.setLayoutManager(
                new LinearLayoutManager(context)
        );

        recyclerView.setAdapter(adapter);

        loadAnnouncements();
    }

    // ============================================================
    // LOAD ANNOUNCEMENTS
    // ============================================================

    private void loadAnnouncements() {


        String uid = FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            showEmptyState();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()

                .addOnSuccessListener(userDoc -> {

                    myRole = userDoc.getString("role");

                    if (myRole == null) {
                        myRole = "";
                    }

                    loadMessages(uid);
                })

                .addOnFailureListener(e -> {


                    Toast.makeText(
                            context,
                            "Could not load account information.",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    // ============================================================
    // LOAD MESSAGES
    // ============================================================

    private void loadMessages(String uid) {

        FirebaseFirestore.getInstance()
                .collection("broadcastChannels")
                .orderBy(
                        "createdAt",
                        Query.Direction.ASCENDING
                )
                .addSnapshotListener((snapshots, error) -> {

                    if (!firstLoadDone) {
                        firstLoadDone = true;
                    }

                    if (error != null) {

                        Toast.makeText(
                                context,
                                "Could not load announcements.",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    if (snapshots == null) {
                        showEmptyState();
                        return;
                    }

                    fullList.clear();

                    for (
                            DocumentSnapshot doc
                            : snapshots.getDocuments()
                    ) {

                        BroadcastModel m =
                                new BroadcastModel();

                        m.id = doc.getId();

                        m.content =
                                doc.getString("content");

                        m.senderId =
                                doc.getString("senderId");

                        m.senderName =
                                doc.getString("senderName");

                        m.senderRole =
                                doc.getString("senderRole");

                        m.recipient =
                                doc.getString("recipient");

                        m.imageUrl =
                                doc.getString("imageUrl");

                        m.fileUrl =
                                doc.getString("fileUrl");

                        m.fileName =
                                doc.getString("fileName");

                        m.createdAt =
                                doc.getTimestamp("createdAt");

                        // ==================================================
                        // LINK PREVIEW
                        // ==================================================

                        Object linkPreview =
                                doc.get("linkPreview");

                        if (
                                linkPreview
                                        instanceof java.util.Map
                        ) {

                            java.util.Map<?, ?> lp =
                                    (java.util.Map<?, ?>)
                                            linkPreview;

                            Object title =
                                    lp.get("title");

                            Object url =
                                    lp.get("url");

                            Object image =
                                    lp.get("image");

                            m.linkTitle =
                                    title != null
                                            ? title.toString()
                                            : null;

                            m.linkUrl =
                                    url != null
                                            ? url.toString()
                                            : null;

                            m.linkImage =
                                    image != null
                                            ? image.toString()
                                            : null;
                        }

                        // ==================================================
                        // REACTIONS
                        // ==================================================

                        Object reactions =
                                doc.get("reactions");

                        if (
                                reactions
                                        instanceof java.util.Map
                        ) {

                            java.util.Map<?, ?> r =
                                    (java.util.Map<?, ?>)
                                            reactions;

                            Object like =
                                    r.get("like");

                            Object love =
                                    r.get("love");

                            if (like instanceof List) {

                                for (
                                        Object o
                                        : (List<?>) like
                                ) {

                                    if (o != null) {
                                        m.likeUids.add(
                                                o.toString()
                                        );
                                    }
                                }
                            }

                            if (love instanceof List) {

                                for (
                                        Object o
                                        : (List<?>) love
                                ) {

                                    if (o != null) {
                                        m.loveUids.add(
                                                o.toString()
                                        );
                                    }
                                }
                            }
                        }

                        // ==================================================
                        // VISIBILITY
                        // ==================================================

                        boolean visible =
                                "All Staffs".equalsIgnoreCase(
                                        m.recipient
                                )
                                        || uid.equals(
                                        m.senderId
                                )
                                        || (
                                        m.recipient != null
                                                && m.recipient
                                                .equalsIgnoreCase(
                                                        myRole
                                                )
                                );

                        if (visible) {
                            fullList.add(m);
                        }
                    }

                    visibleList.clear();
                    visibleList.addAll(fullList);

                    adapter.notifyDataSetChanged();

                    updateEmptyState();
                });
    }

    // ============================================================
    // EMPTY STATE
    // ============================================================

    private void updateEmptyState() {

        if (visibleList.isEmpty()) {
            showEmptyState();
            return;
        }

        if (noBroadcastText != null) {
            noBroadcastText.setVisibility(
                    View.GONE
            );
        }

        recyclerView.setVisibility(
                View.VISIBLE
        );
    }

    private void showEmptyState() {

        if (noBroadcastText != null) {
            noBroadcastText.setVisibility(
                    View.VISIBLE
            );
        }

        if (recyclerView != null) {
            recyclerView.setVisibility(
                    View.GONE
            );
        }
    }

    // ============================================================
    // OPEN URL
    // ============================================================

    private void openUrl(String url) {

        if (url == null || url.isEmpty()) {
            return;
        }

        try {

            Intent intent =
                    new Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                    );

            context.startActivity(intent);

        } catch (Exception e) {

            Toast.makeText(
                    context,
                    "Could not open link",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ============================================================
    // LIKE / LOVE
    // ============================================================

    private void toggleReaction(
            BroadcastModel msg,
            String type
    ) {

        if (msg == null || msg.id == null) {
            return;
        }

        String uid =
                FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            return;
        }

        List<String> currentList =
                type.equals("like")
                        ? msg.likeUids
                        : msg.loveUids;

        boolean hasReacted =
                currentList.contains(uid);

        FirebaseFirestore.getInstance()
                .collection("broadcastChannels")
                .document(msg.id)
                .update(
                        "reactions." + type,
                        hasReacted
                                ? FieldValue.arrayRemove(uid)
                                : FieldValue.arrayUnion(uid)
                )
                .addOnFailureListener(e ->
                        Toast.makeText(
                                context,
                                "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }
}