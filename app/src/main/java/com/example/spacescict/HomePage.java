package com.example.spacescict;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomePage {

    Button reserveButton;
    TextView greetingName, scheduleStatusBadge, scheduleRoomText, scheduleSubjectText,
            scheduleTimeText, noScheduleText, noUpcomingText;
    View scheduleCard;
    RecyclerView upcomingRecycler;

    public HomePage(Context context, View view) {

        reserveButton = view.findViewById(R.id.reserveButton);
        greetingName = view.findViewById(R.id.greetingName);
        scheduleCard = view.findViewById(R.id.scheduleCard);
        scheduleStatusBadge = view.findViewById(R.id.scheduleStatusBadge);
        scheduleRoomText = view.findViewById(R.id.scheduleRoomText);
        scheduleSubjectText = view.findViewById(R.id.scheduleSubjectText);
        scheduleTimeText = view.findViewById(R.id.scheduleTimeText);
        noScheduleText = view.findViewById(R.id.noScheduleText);
        noUpcomingText = view.findViewById(R.id.noUpcomingText);
        upcomingRecycler = view.findViewById(R.id.upcomingRecycler);

        reserveButton.setOnClickListener(v ->
                context.startActivity(new Intent(context, ReservationActivity.class)));

        loadFacultyName();
        loadSchedule();
    }

    void loadFacultyName() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || greetingName == null) return;

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String first = doc.getString("firstName");
                    String last = doc.getString("lastName");
                    String fullName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                    greetingName.setText(fullName.isEmpty() ? "Faculty" : "Prof. " + fullName);
                })
                .addOnFailureListener(e -> greetingName.setText("Faculty"));
    }

    void loadSchedule() {
        ScheduleLoader.load((todaysItems, upcomingItems, termLabel, meetingsPerWeek, roomsUsed) -> {

            if (todaysItems.isEmpty()) {
                scheduleCard.setVisibility(View.GONE);
                noScheduleText.setVisibility(View.VISIBLE);
            } else {
                scheduleCard.setVisibility(View.VISIBLE);
                noScheduleText.setVisibility(View.GONE);

                ScheduleLoader.ScheduleItem active = todaysItems.get(0);
                for (ScheduleLoader.ScheduleItem item : todaysItems) {
                    if ("ONGOING".equals(item.status)) { active = item; break; }
                }

                scheduleStatusBadge.setText(active.status);
                scheduleRoomText.setText(active.roomName != null ? active.roomName : "");
                String tag = active.kind.equals("reassignment") ? " (Moved)"
                        : active.kind.equals("reservation") ? " (Reservation)" : "";
                scheduleSubjectText.setText((active.subject != null ? active.subject : "") + tag);
                scheduleTimeText.setText(formatTime(active.startTime) + " - " + formatTime(active.endTime));
            }

            List<ScheduleLoader.ScheduleItem> upcoming = upcomingItems != null ? upcomingItems : new ArrayList<>();
            if (upcoming.isEmpty()) {
                upcomingRecycler.setVisibility(View.GONE);
                noUpcomingText.setVisibility(View.VISIBLE);
            } else {
                upcomingRecycler.setVisibility(View.VISIBLE);
                noUpcomingText.setVisibility(View.GONE);
                upcomingRecycler.setLayoutManager(new LinearLayoutManager(upcomingRecycler.getContext()));
                upcomingRecycler.setAdapter(new UpcomingAdapter(upcoming));
            }
        });
    }

    String formatTime(String time) {
        if (time == null) return "";
        int[] p = ScheduleLoader.parseTimeParts(time);
        String suffix = p[0] >= 12 ? "PM" : "AM";
        int hh = p[0] % 12 == 0 ? 12 : p[0] % 12;
        return String.format(Locale.US, "%02d:%02d %s", hh, p[1], suffix);
    }
}