package com.example.spacescict;

import android.os.Bundle;
import android.widget.ImageView;

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
    ArrayList<NotificationModel> list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        recyclerView = findViewById(R.id.notificationRecycler);
        ImageView backButton = findViewById(R.id.backBtn);

        backButton.setOnClickListener(v -> finish());

        list = new ArrayList<>();
        adapter = new NotificationAdapter(list);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        String uid = FirebaseAuth.getInstance().getUid();

        FirebaseFirestore.getInstance()
                .collection("notifications") // 🔥 flat collection, matches your web rules
                .whereEqualTo("userId", uid) // ⚠️ confirm this field name — see note above
                .orderBy("time", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    list.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        list.add(new NotificationModel(
                                R.drawable.notification_gray_bg,
                                doc.getString("title"),
                                doc.getString("message"),
                                doc.getString("time"),
                                doc.getString("tag"),
                                R.drawable.notification_orange_bg
                        ));
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}