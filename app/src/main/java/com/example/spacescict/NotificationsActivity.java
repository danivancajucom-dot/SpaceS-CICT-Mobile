package com.example.spacescict;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class NotificationsActivity extends AppCompatActivity {

    RecyclerView recyclerView;
    NotificationAdapter adapter;
    ArrayList<NotificationModel> fullList = new ArrayList<>();
    ArrayList<NotificationModel> filteredList = new ArrayList<>();

    TextView tabAllText, tabUnreadText, tabArchivedText;

    enum Filter { ALL, UNREAD, ARCHIVED }
    Filter currentFilter = Filter.ALL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        recyclerView = findViewById(R.id.notificationRecycler);
        ImageView backButton = findViewById(R.id.backBtn);
        backButton.setOnClickListener(v -> finish());

        tabAllText = findViewById(R.id.tabAllText);
        tabUnreadText = findViewById(R.id.tabUnreadText);
        tabArchivedText = findViewById(R.id.tabArchivedText);

        findViewById(R.id.tabAll).setOnClickListener(v -> setFilter(Filter.ALL));
        findViewById(R.id.tabUnread).setOnClickListener(v -> setFilter(Filter.UNREAD));
        tabArchivedText.setOnClickListener(v -> setFilter(Filter.ARCHIVED));

        adapter = new NotificationAdapter(filteredList, new NotificationAdapter.OnActionListener() {
            @Override
            public void onTap(NotificationModel n, int position) {
                // Navigate to reassignment screen if applicable, without changing read state prematurely
                boolean isReassignment = "room-reassignment".equals(n.type) && n.assignmentId != null;

                if (n.unread) {
                    n.unread = false;
                    markNotificationRead(n);
                    // Re-filter immediately — item must disappear right away if we're on the Unread tab
                    applyFilter();
                }

                if (isReassignment) {
                    Intent intent = new Intent(NotificationsActivity.this, RoomReassignmentActivity.class);
                    intent.putExtra(RoomReassignmentActivity.EXTRA_ASSIGNMENT_ID, n.assignmentId);
                    startActivity(intent);
                }
            }

            @Override
            public void onArchive(NotificationModel n, int position) {
                // Optimistic local update — don't wait for Firestore round-trip to reflect it in the UI
                n.archived = true;
                applyFilter();
                archiveNotification(n);
            }

            @Override
            public void onViewAction(NotificationModel n) {
                Intent intent = new Intent(NotificationsActivity.this, RoomReassignmentActivity.class);
                intent.putExtra(RoomReassignmentActivity.EXTRA_ASSIGNMENT_ID, n.assignmentId);
                startActivity(intent);
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        updateTabStyles();

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", uid)
                .whereEqualTo("ownerType", "faculty")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    fullList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Boolean unread = doc.getBoolean("unread");
                        Boolean archived = doc.getBoolean("archived");
                        NotificationModel n = new NotificationModel(
                                R.drawable.notification_gray_bg,
                                doc.getString("title"),
                                doc.getString("message"),
                                doc.getString("badge"),
                                doc.getString("type"),
                                doc.getTimestamp("createdAt"),
                                unread != null && unread,
                                archived != null && archived,
                                R.drawable.notification_orange_bg
                        );
                        n.id = doc.getId();
                        n.assignmentId = doc.getString("assignmentId");
                        fullList.add(n);
                    }
                    applyFilter();
                });
    }

    void setFilter(Filter filter) {
        currentFilter = filter;
        updateTabStyles();
        applyFilter();
    }

    void applyFilter() {
        filteredList.clear();
        for (NotificationModel n : fullList) {
            switch (currentFilter) {
                case ALL:
                    if (!n.archived) filteredList.add(n);
                    break;
                case UNREAD:
                    if (n.unread && !n.archived) filteredList.add(n);
                    break;
                case ARCHIVED:
                    if (n.archived) filteredList.add(n);
                    break;
            }
        }
        adapter.notifyDataSetChanged();
    }

    void updateTabStyles() {
        int active = Color.parseColor("#F97316");
        int inactive = Color.parseColor("#667085");

        tabAllText.setTextColor(currentFilter == Filter.ALL ? active : inactive);
        tabUnreadText.setTextColor(currentFilter == Filter.UNREAD ? active : inactive);
        tabArchivedText.setTextColor(currentFilter == Filter.ARCHIVED ? active : inactive);
    }

    void markNotificationRead(NotificationModel n) {
        if (n.id == null) return;
        FirebaseFirestore.getInstance().collection("notifications").document(n.id)
                .update("unread", false);
    }

    void archiveNotification(NotificationModel n) {
        if (n.id == null) return;
        FirebaseFirestore.getInstance().collection("notifications").document(n.id)
                .update("archived", true)
                .addOnFailureListener(e -> {
                    // Revert the optimistic update if the write actually failed
                    n.archived = false;
                    applyFilter();
                });
    }
}