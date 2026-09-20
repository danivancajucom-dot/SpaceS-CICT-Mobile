package com.example.spacescict;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomePage {

    private final Context context;

    private View reserveButton;

    private TextView greetingName;
    private TextView scheduleStatusBadge;
    private TextView scheduleRoomText;
    private TextView scheduleSubjectText;
    private TextView scheduleTimeText;
    private TextView noUpcomingText;

    private LinearLayout noScheduleText;

    private ImageView homeProfileImage;

    private View scheduleCard;
    private View todayScheduleCardContainer;
    private View upcomingCardContainer;

    private RecyclerView upcomingRecycler;

    // Announcement card
    private View announcementCard;
    private TextView announcementSenderText;
    private TextView announcementContentText;
    private TextView announcementTimeText;
    private TextView announcementViewBtn;

    // Stats
    private LinearLayout statsRow1, statsRow2;

    public HomePage(Context context, View view) {

        this.context = context;

        // ============================================================
        // ANNOUNCEMENT
        // ============================================================

        announcementCard =
                view.findViewById(R.id.announcementCard);

        announcementSenderText =
                view.findViewById(R.id.announcementSenderText);

        announcementContentText =
                view.findViewById(R.id.announcementContentText);

        announcementTimeText =
                view.findViewById(R.id.announcementTimeText);

        announcementViewBtn =
                view.findViewById(R.id.announcementViewBtn);

        // ============================================================
        // STATS
        // ============================================================

        statsRow1 = view.findViewById(R.id.statsRow1);
        statsRow2 = view.findViewById(R.id.statsRow2);

        // ============================================================
        // HOME
        // ============================================================

        reserveButton =
                view.findViewById(R.id.reserveButton);

        greetingName =
                view.findViewById(R.id.greetingName);

        homeProfileImage =
                view.findViewById(R.id.homeProfileImage);

        scheduleCard =
                view.findViewById(R.id.scheduleCard);

        todayScheduleCardContainer =
                view.findViewById(R.id.todayScheduleCardContainer);

        upcomingCardContainer =
                view.findViewById(R.id.upcomingCardContainer);

        scheduleStatusBadge =
                view.findViewById(R.id.scheduleStatusBadge);

        scheduleRoomText =
                view.findViewById(R.id.scheduleRoomText);

        scheduleSubjectText =
                view.findViewById(R.id.scheduleSubjectText);

        scheduleTimeText =
                view.findViewById(R.id.scheduleTimeText);

        noScheduleText =
                view.findViewById(R.id.noScheduleText);

        noUpcomingText =
                view.findViewById(R.id.noUpcomingText);

        upcomingRecycler =
                view.findViewById(R.id.upcomingRecycler);

        // ============================================================
        // RESERVE ROOM
        // ============================================================

        if (reserveButton != null) {
            reserveButton.setOnClickListener(v -> {

                if (context instanceof Activity) {

                    NavigationHelper.goTo(
                            (Activity) context,
                            ReservationActivity.class,
                            "Opening reservation form..."
                    );
                }
            });
        }

        // ============================================================
        // PROFILE IMAGE
        // ============================================================

        if (homeProfileImage != null) {

            homeProfileImage.setOnClickListener(v -> {

                if (context instanceof DashboardActivity) {

                    DashboardActivity dash =
                            (DashboardActivity) context;

                    NavigationHelper.inlineSwap(
                            dash,
                            "Loading profile...",
                            dash::openProfile
                    );
                }
            });
        }

        // ============================================================
        // SEE ALL SCHEDULE
        // ============================================================

        View seeAllText =
                view.findViewById(R.id.seeAllText);

        if (seeAllText != null) {

            seeAllText.setOnClickListener(v -> {

                if (context instanceof DashboardActivity) {

                    DashboardActivity dash =
                            (DashboardActivity) context;

                    NavigationHelper.inlineSwap(
                            dash,
                            "Loading schedule...",
                            dash::loadWeeklySchedule
                    );
                }
            });
        }

        View allUpcomingText =
                view.findViewById(R.id.allUpcomingText);

        if (allUpcomingText != null) {

            allUpcomingText.setOnClickListener(v -> {

                if (context instanceof DashboardActivity) {

                    DashboardActivity dash =
                            (DashboardActivity) context;

                    NavigationHelper.inlineSwap(
                            dash,
                            "Loading schedule...",
                            dash::loadWeeklySchedule
                    );
                }
            });
        }

        // ============================================================
        // LOAD DATA
        // ============================================================

        loadFacultyName();
        loadSchedule();
        loadLatestAnnouncement();
    }

    // ============================================================
    // LOAD LATEST ANNOUNCEMENT
    // ============================================================

    private void loadLatestAnnouncement() {

        FirebaseFirestore.getInstance()
                .collection("broadcastChannels")
                .orderBy(
                        "createdAt",
                        Query.Direction.DESCENDING
                )
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (snapshot == null || snapshot.isEmpty()) {

                        if (announcementCard != null) {
                            announcementCard.setVisibility(
                                    View.GONE
                            );
                        }

                        return;
                    }

                    com.google.firebase.firestore.DocumentSnapshot doc =
                            snapshot.getDocuments().get(0);

                    if (announcementCard != null) {
                        announcementCard.setVisibility(
                                View.VISIBLE
                        );
                    }

                    String sender =
                            doc.getString("senderName");

                    String content =
                            doc.getString("content");

                    if (announcementSenderText != null) {
                        announcementSenderText.setText(
                                sender != null
                                        ? sender
                                        : "Announcement"
                        );
                    }

                    if (announcementContentText != null) {
                        announcementContentText.setText(
                                content != null
                                        ? content
                                        : ""
                        );
                    }

                    Timestamp timestamp =
                            doc.getTimestamp("createdAt");

                    if (announcementTimeText != null) {

                        if (timestamp != null) {

                            long difference =
                                    System.currentTimeMillis()
                                            - timestamp.toDate().getTime();

                            long minutes =
                                    difference / 60000L;

                            if (minutes < 1) {

                                announcementTimeText.setText(
                                        "Just now"
                                );

                            } else if (minutes < 60) {

                                announcementTimeText.setText(
                                        minutes + "m ago"
                                );

                            } else {

                                long hours =
                                        minutes / 60L;

                                if (hours < 24) {

                                    announcementTimeText.setText(
                                            hours + "h ago"
                                    );

                                } else {

                                    long days =
                                            hours / 24L;

                                    announcementTimeText.setText(
                                            days + "d ago"
                                    );
                                }
                            }

                        } else {

                            announcementTimeText.setText("");
                        }
                    }

                    // Open announcements inside DashboardActivity
                    View.OnClickListener openAnnouncement =
                            v -> {

                                if (context instanceof DashboardActivity) {

                                    DashboardActivity dash =
                                            (DashboardActivity) context;

                                    NavigationHelper.inlineSwap(
                                            dash,
                                            "Loading announcements...",
                                            dash::loadAnnouncements
                                    );
                                }
                            };

                    if (announcementCard != null) {
                        announcementCard.setOnClickListener(
                                openAnnouncement
                        );
                    }

                    if (announcementViewBtn != null) {
                        announcementViewBtn.setOnClickListener(
                                openAnnouncement
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    if (announcementCard != null) {
                        announcementCard.setVisibility(
                                View.GONE
                        );
                    }
                });
    }


    // ============================================================
    // LOAD FACULTY NAME
    // ============================================================

    private void loadFacultyName() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || greetingName == null) return;

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc == null || !doc.exists()) {
                        setGreeting("Faculty");
                        return;
                    }

                    String first = doc.getString("firstName");
                    String last = doc.getString("lastName");
                    if (first == null) first = "";
                    if (last == null) last = "";

                    String greetingTarget = first.trim().isEmpty() ? "Faculty" : first.trim();
                    setGreeting(greetingTarget);

                    String photoUrl = doc.getString("photoUrl");
                    if (homeProfileImage != null) {
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(context)
                                    .load(photoUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_user)
                                    .into(homeProfileImage);
                        } else {
                            // Convert initials into a clean circle drawable background with initials text
                            String initial = "";
                            if (!first.isEmpty()) initial += first.substring(0, 1).toUpperCase();
                            if (!last.isEmpty()) initial += last.substring(0, 1).toUpperCase();
                            if (initial.isEmpty()) initial = "U";

                            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                            android.graphics.Paint paint = new android.graphics.Paint();
                            paint.setColor(android.graphics.Color.parseColor("#FFEDD5")); // soft orange tint
                            paint.setAntiAlias(true);
                            canvas.drawCircle(60, 60, 60, paint);

                            paint.setColor(android.graphics.Color.parseColor("#EA580C")); // vivid brand orange
                            paint.setTextSize(44);
                            paint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
                            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                            float yPos = (canvas.getHeight() / 2L) - ((paint.descent() + paint.ascent()) / 2L);
                            canvas.drawText(initial, 60, yPos, paint);

                            homeProfileImage.setImageBitmap(bitmap);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (greetingName != null) greetingName.setText("Faculty");
                });
    }

    private void setGreeting(String name) {
        if (greetingName == null) return;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String salutation;
        if (hour >= 5 && hour < 12) {
            salutation = "Good Morning";
        } else if (hour >= 12 && hour < 17) {
            salutation = "Good Afternoon";
        } else {
            salutation = "Good Evening";
        }
        greetingName.setText(salutation + ", " + name + "!");
    }

    // ============================================================
    // LOAD SCHEDULE
    // ============================================================

    private void loadSchedule() {

        ScheduleLoader.load(
                (
                        todaysItems,
                        upcomingItems,
                        termLabel,
                        meetingsPerWeek,
                        roomsUsed
                ) -> {

                    // =================================================
                    // HIDE/SHOW ENTIRE DASHBOARD WIDGETS IF NO SCHEDULE IMPORTED
                    // =================================================
                    boolean hasImportedSchedule = termLabel != null && !termLabel.trim().isEmpty();

                    if (statsRow1 != null && statsRow1.getParent() instanceof View) {
                        ((View) statsRow1.getParent()).setVisibility(hasImportedSchedule ? View.VISIBLE : View.GONE);
                    }
                    if (todayScheduleCardContainer != null) {
                        todayScheduleCardContainer.setVisibility(View.VISIBLE);
                    }
                    if (upcomingCardContainer != null) {
                        upcomingCardContainer.setVisibility(View.VISIBLE);
                    }

                    // =================================================
                    // TODAY
                    // =================================================

                    if (
                            todaysItems == null
                                    || todaysItems.isEmpty()
                    ) {

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

                        if (active != null) {

                            if (scheduleStatusBadge != null) {

                                scheduleStatusBadge.setText(
                                        active.status != null
                                                ? active.status
                                                : ""
                                );
                            }

                            if (scheduleRoomText != null) {

                                scheduleRoomText.setText(
                                        active.roomName != null
                                                ? active.roomName
                                                : ""
                                );
                            }

                            String tag = "";

                            if (
                                    "reassignment".equals(
                                            active.kind
                                    )
                            ) {

                                tag = " (Moved)";

                            } else if (
                                    "reservation".equals(
                                            active.kind
                                    )
                            ) {

                                tag = " (Reservation)";

                            } else if (
                                    "faculty-online".equals(
                                            active.kind
                                    )
                            ) {

                                tag = " (Online)";
                            }

                            if (scheduleSubjectText != null) {

                                scheduleSubjectText.setText(
                                        (
                                                active.subject != null
                                                        ? active.subject
                                                        : ""
                                        ) + tag
                                );
                            }

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
                    }

                    // =================================================
                    // UPCOMING
                    // =================================================

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
                                    new UpcomingAdapter(
                                            upcoming
                                    )
                            );
                        }

                        if (noUpcomingText != null) {
                            noUpcomingText.setVisibility(
                                    View.GONE
                            );
                        }
                    }

                    // =================================================
                    // STATS
                    // =================================================

                    buildStatsGrid(
                            todaysItems != null
                                    ? todaysItems.size()
                                    : 0,
                            meetingsPerWeek,
                            roomsUsed,
                            upcoming
                    );
                }
        );
    }

    // ============================================================
    // BUILD STATS
    // ============================================================

    private void buildStatsGrid(int classesToday, int meetingsPerWeek, int roomsUsed,
                                List<ScheduleLoader.ScheduleItem> upcoming) {
        if (statsRow1 == null || statsRow2 == null) return;

        statsRow1.removeAllViews();
        statsRow2.removeAllViews();

        String nextLabel = "None";
        String nextSub = "No upcoming classes";

        if (upcoming != null && !upcoming.isEmpty()) {
            ScheduleLoader.ScheduleItem next = upcoming.get(0);
            long difference = next.occurrenceMillis - System.currentTimeMillis();
            if (difference < 0) difference = 0;
            long totalHours = difference / (1000L * 60L * 60L);
            long days = totalHours / 24L;
            long hours = totalHours % 24L;
            nextLabel = days > 0 ? "in " + days + "d " + hours + "h" : "in " + hours + "h";
            nextSub = "Next: " + (next.subject != null ? next.subject : "Class");
        }

        addStatCard(statsRow1, R.drawable.ic_check, String.valueOf(classesToday), "Classes Today");
        addStatCard(statsRow1, R.drawable.ic_calendar, String.valueOf(meetingsPerWeek), "Meetings / Week");
        addStatCard(statsRow2, R.drawable.ic_laptop, String.valueOf(roomsUsed), "Room" + (roomsUsed == 1 ? "" : "s") + " Used");
        addStatCard(statsRow2, R.drawable.ic_clock, nextLabel, nextSub);
    }

    private void addStatCard(LinearLayout row, int iconRes, String value, String label) {
        if (row == null) return;

        View card = LayoutInflater.from(context).inflate(R.layout.recycler_stat_card, row, false);
        ImageView icon = card.findViewById(R.id.statIcon);
        TextView valueView = card.findViewById(R.id.statValue);
        TextView labelView = card.findViewById(R.id.statLabel);

        if (icon != null) icon.setImageResource(iconRes);
        if (valueView != null) valueView.setText(value != null ? value : "");
        if (labelView != null) labelView.setText(label != null ? label : "");

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMarginEnd(row == statsRow1 ? 6 : 6);
        if (row.getChildCount() > 0) params.setMarginStart(6);
        card.setLayoutParams(params);
        row.addView(card);
    }

    // ============================================================
    // ADD STAT CARD
    // ============================================================


    // ============================================================
    // FORMAT TIME
    // ============================================================

    private String formatTime(String time) {

        if (time == null || time.isEmpty()) {
            return "";
        }

        int[] parts =
                ScheduleLoader.parseTimeParts(time);

        if (
                parts == null
                        || parts.length < 2
        ) {
            return time;
        }

        String suffix =
                parts[0] >= 12
                        ? "PM"
                        : "AM";

        int hour =
                parts[0] % 12 == 0
                        ? 12
                        : parts[0] % 12;

        return String.format(
                Locale.US,
                "%02d:%02d %s",
                hour,
                parts[1],
                suffix
        );
    }
}