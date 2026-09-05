package com.example.spacescict;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

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
            public void onOpenDetail(NotificationModel n, int position) {
                if (n.unread) {
                    n.unread = false;
                    markNotificationRead(n);
                    applyFilter();
                }
                showDetailDialog(n);
            }

            @Override
            public void onArchive(NotificationModel n, int position) {
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
                    n.archived = false;
                    applyFilter();
                });
    }

    void showDetailDialog(NotificationModel n) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, dp(22), pad, dp(22));

        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        androidx.cardview.widget.CardView iconCard = new androidx.cardview.widget.CardView(this);
        iconCard.setRadius(dp(14));
        iconCard.setCardElevation(0);
        iconCard.setCardBackgroundColor(Color.parseColor("#FFF1E6"));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconCard.setLayoutParams(iconParams);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_notification);
        icon.setColorFilter(Color.parseColor("#F97316"));
        icon.setPadding(dp(12), dp(12), dp(12), dp(12));
        iconCard.addView(icon);
        headerRow.addView(iconCard);

        TextView badge = new TextView(this);
        badge.setText(n.badge != null ? n.badge : "");
        badge.setTextColor(Color.parseColor("#F97316"));
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextSize(11);
        badge.setBackgroundColor(Color.parseColor("#FFF1E6"));
        badge.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = dp(12);
        badge.setLayoutParams(badgeParams);
        headerRow.addView(badge);

        root.addView(headerRow);

        TextView title = new TextView(this);
        title.setText(n.title != null ? n.title : "");
        title.setTextColor(Color.parseColor("#1C1917"));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(18);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(16);
        title.setLayoutParams(titleParams);
        root.addView(title);

        TextView message = new TextView(this);
        message.setText(n.desc != null ? n.desc : "");
        message.setTextColor(Color.parseColor("#44403C"));
        message.setTextSize(14);
        message.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = dp(10);
        message.setLayoutParams(msgParams);
        root.addView(message);

        TextView time = new TextView(this);
        time.setText(n.createdAt != null
                ? new SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.US).format(n.createdAt.toDate()) : "");
        time.setTextColor(Color.parseColor("#78716C"));
        time.setTextSize(12);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = dp(14);
        time.setLayoutParams(timeParams);
        root.addView(time);

        boolean isReassignment = "room-reassignment".equals(n.type) && n.assignmentId != null;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        if (isReassignment) {
            androidx.cardview.widget.CardView viewCard = new androidx.cardview.widget.CardView(this);
            viewCard.setRadius(dp(14));
            viewCard.setCardElevation(0);
            viewCard.setCardBackgroundColor(Color.parseColor("#F97316"));
            LinearLayout.LayoutParams viewParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            viewParams.topMargin = dp(20);
            viewCard.setLayoutParams(viewParams);
            TextView viewText = new TextView(this);
            viewText.setText("View Reassignment");
            viewText.setTextColor(Color.WHITE);
            viewText.setTypeface(null, Typeface.BOLD);
            viewText.setGravity(Gravity.CENTER);
            viewText.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            viewCard.addView(viewText);
            viewCard.setOnClickListener(v -> {
                dialog.dismiss();
                Intent intent = new Intent(this, RoomReassignmentActivity.class);
                intent.putExtra(RoomReassignmentActivity.EXTRA_ASSIGNMENT_ID, n.assignmentId);
                startActivity(intent);
            });
            root.addView(viewCard);
        }

        androidx.cardview.widget.CardView closeCard = new androidx.cardview.widget.CardView(this);
        closeCard.setRadius(dp(14));
        closeCard.setCardElevation(0);
        closeCard.setCardBackgroundColor(Color.parseColor("#EEF1F5"));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        closeParams.topMargin = dp(10);
        closeCard.setLayoutParams(closeParams);
        TextView closeText = new TextView(this);
        closeText.setText("Close");
        closeText.setTextColor(Color.parseColor("#44403C"));
        closeText.setTypeface(null, Typeface.BOLD);
        closeText.setGravity(Gravity.CENTER);
        closeText.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        closeCard.addView(closeText);
        closeCard.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeCard);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.show();

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        dialog.getWindow().setLayout((int) (screenWidth * 0.88), android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}