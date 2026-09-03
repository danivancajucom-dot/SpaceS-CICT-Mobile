package com.example.spacescict;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomePage {

    private Button reserveButton;

    private TextView greetingName;
    private TextView scheduleStatusBadge;
    private TextView scheduleRoomText;
    private TextView scheduleSubjectText;
    private TextView scheduleTimeText;
    private TextView noUpcomingText;

    // IMPORTANT:
    // noScheduleText is a LinearLayout in layout_home.xml
    private LinearLayout noScheduleText;

    private ImageView homeProfileImage;

    private View scheduleCard;

    private RecyclerView upcomingRecycler;

    private Context context;

    public HomePage(Context context, View view) {

        this.context = context;

        // =========================
        // FIND VIEWS
        // =========================

        reserveButton = view.findViewById(R.id.reserveButton);

        greetingName = view.findViewById(R.id.greetingName);

        homeProfileImage = view.findViewById(R.id.homeProfileImage);

        scheduleCard = view.findViewById(R.id.scheduleCard);

        scheduleStatusBadge =
                view.findViewById(R.id.scheduleStatusBadge);

        scheduleRoomText =
                view.findViewById(R.id.scheduleRoomText);

        scheduleSubjectText =
                view.findViewById(R.id.scheduleSubjectText);

        scheduleTimeText =
                view.findViewById(R.id.scheduleTimeText);

        // FIXED:
        // This is a LinearLayout, NOT a TextView
        noScheduleText =
                view.findViewById(R.id.noScheduleText);

        noUpcomingText =
                view.findViewById(R.id.noUpcomingText);

        upcomingRecycler =
                view.findViewById(R.id.upcomingRecycler);


        // =========================
        // RESERVE BUTTON
        // =========================

        if (reserveButton != null) {
            reserveButton.setOnClickListener(v -> {

                context.startActivity(
                        new Intent(
                                context,
                                ReservationActivity.class
                        )
                );
            });
        }

        // =========================
        // PROFILE IMAGE
        // =========================

        if (homeProfileImage != null) {

            homeProfileImage.setOnClickListener(v -> {

                if (context instanceof DashboardActivity) {

                    ((DashboardActivity) context)
                            .openProfile();
                }
            });
        }
        View seeAllText = view.findViewById(R.id.seeAllText);
        if (seeAllText != null) {
            seeAllText.setOnClickListener(v -> {
                if (context instanceof DashboardActivity) {
                    ((DashboardActivity) context).goToWeeklySchedule();
                }
            });
        }

        // =========================
        // LOAD DATA
        // =========================

        loadFacultyName();
        loadSchedule();
    }

    // ============================================================
    // LOAD FACULTY NAME
    // ============================================================

    private void loadFacultyName() {

        String uid =
                FirebaseAuth.getInstance().getUid();

        if (uid == null || greetingName == null) {
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .get()

                .addOnSuccessListener(doc -> {

                    if (!doc.exists()) {

                        greetingName.setText("Faculty");
                        return;
                    }

                    String first =
                            doc.getString("firstName");

                    String last =
                            doc.getString("lastName");

                    String fullName =
                            (
                                    (first != null ? first : "")
                                            + " "
                                            + (last != null ? last : "")
                            ).trim();

                    if (fullName.isEmpty()) {

                        greetingName.setText("Faculty");

                    } else {

                        greetingName.setText(
                                "Prof. " + fullName
                        );
                    }

                    String photoUrl =
                            doc.getString("photoUrl");

                    if (homeProfileImage != null
                            && photoUrl != null
                            && !photoUrl.isEmpty()) {

                        Glide.with(context)
                                .load(photoUrl)
                                .circleCrop()
                                .placeholder(
                                        R.drawable.ic_user
                                )
                                .into(homeProfileImage);
                    }
                })

                .addOnFailureListener(e -> {

                    if (greetingName != null) {
                        greetingName.setText("Faculty");
                    }
                });
    }

    // ============================================================
    // LOAD SCHEDULE
    // ============================================================


    private void loadSchedule() {

        ScheduleLoader.load(
                (todaysItems,
                 upcomingItems,
                 termLabel,
                 meetingsPerWeek,
                 roomsUsed) -> {

                    // --------------------------------------------
                    // TODAY'S SCHEDULE
                    // --------------------------------------------

                    if (todaysItems == null
                            || todaysItems.isEmpty()) {

                        if (scheduleCard != null) {
                            scheduleCard.setVisibility(
                                    View.GONE
                            );
                        }

                        if (noScheduleText != null) {
                            noScheduleText.setVisibility(
                                    View.VISIBLE
                            );
                        }

                    } else {

                        if (scheduleCard != null) {
                            scheduleCard.setVisibility(
                                    View.VISIBLE
                            );
                        }

                        if (noScheduleText != null) {
                            noScheduleText.setVisibility(
                                    View.GONE
                            );
                        }

                        ScheduleLoader.ScheduleItem active =
                                todaysItems.get(0);

                        for (
                                ScheduleLoader.ScheduleItem item
                                : todaysItems
                        ) {

                            if (
                                    item != null
                                            && "ONGOING".equals(
                                            item.status
                                    )
                            ) {

                                active = item;
                                break;
                            }
                        }

                        if (active == null) {
                            return;
                        }

                        // STATUS

                        if (scheduleStatusBadge != null) {

                            scheduleStatusBadge.setText(
                                    active.status != null
                                            ? active.status
                                            : ""
                            );
                        }

                        // ROOM

                        if (scheduleRoomText != null) {

                            scheduleRoomText.setText(
                                    active.roomName != null
                                            ? active.roomName
                                            : ""
                            );
                        }

                        // TYPE TAG

                        String tag = "";

                        if ("reassignment".equals(
                                active.kind)) {

                            tag = " (Moved)";

                        } else if ("reservation".equals(
                                active.kind)) {

                            tag = " (Reservation)";

                        } else if ("faculty-online".equals(
                                active.kind)) {

                            tag = " (Online)";
                        }

                        // SUBJECT

                        if (scheduleSubjectText != null) {

                            scheduleSubjectText.setText(
                                    (
                                            active.subject != null
                                                    ? active.subject
                                                    : ""
                                    ) + tag
                            );
                        }

                        // TIME

                        if (scheduleTimeText != null) {

                            scheduleTimeText.setText(
                                    formatTime(
                                            active.startTime
                                    )
                                            + " - "
                                            + formatTime(
                                            active.endTime
                                    )
                            );
                        }
                    }

                    // --------------------------------------------
                    // UPCOMING CLASSES
                    // --------------------------------------------

                    List<ScheduleLoader.ScheduleItem> upcoming =
                            upcomingItems != null
                                    ? upcomingItems
                                    : new ArrayList<>();

                    if (upcoming.isEmpty()) {

                        if (upcomingRecycler != null) {

                            upcomingRecycler.setVisibility(
                                    View.GONE
                            );
                        }

                        if (noUpcomingText != null) {

                            noUpcomingText.setVisibility(
                                    View.VISIBLE
                            );
                        }

                    } else {

                        if (upcomingRecycler != null) {

                            upcomingRecycler.setVisibility(
                                    View.VISIBLE
                            );

                            upcomingRecycler.setLayoutManager(
                                    new LinearLayoutManager(
                                            upcomingRecycler.getContext()
                                    )
                            );

                            upcomingRecycler.setAdapter(
                                    new UpcomingAdapter(upcoming)
                            );
                        }

                        if (noUpcomingText != null) {

                            noUpcomingText.setVisibility(
                                    View.GONE
                            );
                        }
                    }
                }
        );
    }

    // ============================================================
    // FORMAT TIME
    // ============================================================

    private String formatTime(String time) {

        if (time == null || time.isEmpty()) {
            return "";
        }

        int[] p =
                ScheduleLoader.parseTimeParts(time);

        String suffix =
                p[0] >= 12 ? "PM" : "AM";

        int hh =
                p[0] % 12 == 0
                        ? 12
                        : p[0] % 12;

        return String.format(
                Locale.US,
                "%02d:%02d %s",
                hh,
                p[1],
                suffix
        );
    }
}