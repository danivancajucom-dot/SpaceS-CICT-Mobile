// NotificationsActivity.java
package com.example.spacescict;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

        backButton.setOnClickListener(v -> {
            finish();
        });

        list = new ArrayList<>();

        list.add(new NotificationModel(
                R.drawable.notification_gray_bg,
                "Schedule Changed:\nGender and Society",
                "Room changed from CT8 to CT6 for today’s 12:00 PM lecture due to an event.",
                "2m ago",
                "NEW",
                R.drawable.notification_orange_bg
        ));

        list.add(new NotificationModel(
                R.drawable.ic_warning,
                "Override Alert: Lab 10",
                "Dean Digna has requested an administrative override for your 4:00 PM slot.",
                "15m ago",
                "URGENT",
                R.drawable.notification_red_bg
        ));

        list.add(new NotificationModel(
                R.drawable.status_green,
                "Reservation Approved",
                "Your request for SDL1 on Saturday, Oct 26th has been approved.",
                "15m ago",
                "",
                R.drawable.notification_green_bg
        ));

        recyclerView.setLayoutManager(
                new LinearLayoutManager(this)
        );

        recyclerView.setAdapter(
                new NotificationAdapter(list)
        );
    }
}