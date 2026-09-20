package com.example.spacescict;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Rooms browsing screen.
 *
 * UI note: this used to have TWO overlapping ways to change date/time/status -
 * a row of buttons always on screen, plus a "Filters" FAB opening a dialog with
 * its own (differently laid out) date/time controls and a status chip row that
 * was built in XML but never populated in code. Both edited the same state, so
 * it was easy to lose track of which one you'd actually used.
 *
 * Now there's one place to change date, time and status: the filters dialog.
 * The row under Building/Floor is a summary - a small chip for each active
 * value - and tapping any chip reopens that same dialog. Building and Floor
 * stay directly on screen since faculty flip through those constantly while
 * date/time/status are occasional adjustments.
 */
public class RoomsPage {
    private final Context context;
    private final ArrayList<RoomModel> allRooms = new ArrayList<>();
    private final ArrayList<RoomModel> filteredRooms = new ArrayList<>();
    private final ArrayList<Watch> watches = new ArrayList<>();
    private final Set<String> notifyingWatchIds = new HashSet<>();
    private final Handler refreshHandler = new Handler();
    private final Runnable refreshRunnable = this::loadRooms;

    private static final String[] STATUS_OPTIONS =
            {"All Status", "Available", "Occupied", "Under Maintenance"};

    private RoomAdapter adapter;
    private Spinner buildingSpinner;
    private LinearLayout floorContainer;
    private TextView dateButton, timeButton, statusChip, watchCount, emptyState;
    private View clearFiltersButton;

    private String selectedDate, startTime, endTime, selectedFloor = "All Floors";
    private String filterStatus = "All Status";

    private ListenerRegistration watchRegistration;

    private static class Watch {
        String id, roomId, roomName, date, startTime, endTime;
    }

    public RoomsPage(Context context, View view) {
        this.context = context;
        resetDateTime();

        RecyclerView recycler = view.findViewById(R.id.roomsRecycler);
        buildingSpinner = view.findViewById(R.id.buildingSpinner);
        floorContainer = view.findViewById(R.id.floorContainer);
        dateButton = view.findViewById(R.id.roomDateButton);
        timeButton = view.findViewById(R.id.roomTimeButton);
        statusChip = view.findViewById(R.id.roomStatusChip);
        watchCount = view.findViewById(R.id.roomWatchCount);
        emptyState = view.findViewById(R.id.roomsEmptyState);
        clearFiltersButton = view.findViewById(R.id.clearRoomFiltersButton);

        View filtersFab = view.findViewById(R.id.filtersFab);
        if (filtersFab != null) {
            filtersFab.setOnClickListener(v -> showFiltersDialog());
        }
        adapter = new RoomAdapter(filteredRooms, this::reserveRoom, this::viewSchedule, this::toggleWatch);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);
        buildingSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, new String[]{"All Buildings"}));

        buildingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View item, int position, long id) {
                selectedFloor = "All Floors";
                updateFloorButtons();
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        if (dateButton != null) dateButton.setOnClickListener(v -> showFiltersDialog());
        if (timeButton != null) timeButton.setOnClickListener(v -> showFiltersDialog());
        if (statusChip != null) statusChip.setOnClickListener(v -> showFiltersDialog());
        if (clearFiltersButton != null) clearFiltersButton.setOnClickListener(v -> clearFilters());

        updateFilterLabels();
        subscribeToWatches();
        loadRooms();
    }

    // ════════════════════════════════════════════════════════════════════
    // FILTERS DIALOG - the one place date, time and status are edited
    // ════════════════════════════════════════════════════════════════════

    void showFiltersDialog() {
        View dv = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_rooms_filters, null);
        android.app.Dialog dialog = new android.app.Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(dv);
        dialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        TextView dateText = dv.findViewById(R.id.filterDateText);
        TextView startText = dv.findViewById(R.id.filterStartTimeText);
        TextView endText = dv.findViewById(R.id.filterEndTimeText);
        com.google.android.flexbox.FlexboxLayout statusChips = dv.findViewById(R.id.filterStatusChips);
        dv.findViewById(R.id.filtersCloseBtn).setOnClickListener(v -> dialog.dismiss());

        dateText.setText(selectedDate);
        startText.setText(startTime);
        endText.setText(endTime);

        dateText.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            try {
                String[] parts = dateText.getText().toString().split("-");
                c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            } catch (Exception ignored) { /* keep today */ }
            new DatePickerDialog(context, (view1, year, month, day) ->
                    dateText.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)),
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });
        startText.setOnClickListener(v -> {
            int[] hm = parseHm(startText.getText().toString());
            new TimePickerDialog(context, (view1, hour, minute) ->
                    startText.setText(String.format(Locale.US, "%02d:%02d", hour, minute)),
                    hm[0], hm[1], true).show();
        });
        endText.setOnClickListener(v -> {
            int[] hm = parseHm(endText.getText().toString());
            new TimePickerDialog(context, (view1, hour, minute) ->
                    endText.setText(String.format(Locale.US, "%02d:%02d", hour, minute)),
                    hm[0], hm[1], true).show();
        });

        // Status chips - this row existed in the layout since the original build
        // but nothing ever populated it, so the "Status" filter was dead. Build
        // it the same way the Floor row already does.
        final String[] chosenStatus = {filterStatus};
        if (statusChips != null) {
            statusChips.removeAllViews();
            for (String option : STATUS_OPTIONS) {
                TextView chip = buildFilterChip(option, option.equals(filterStatus));
                chip.setOnClickListener(v -> {
                    chosenStatus[0] = option;
                    for (int i = 0; i < statusChips.getChildCount(); i++) {
                        View child = statusChips.getChildAt(i);
                        if (child instanceof TextView) {
                            styleFilterChip((TextView) child,
                                    ((TextView) child).getText().toString().equals(option));
                        }
                    }
                });
                statusChips.addView(chip);
            }
        }

        dv.findViewById(R.id.filterClearBtn).setOnClickListener(v -> {
            dialog.dismiss();
            clearFilters();
        });

        dv.findViewById(R.id.filterApplyBtn).setOnClickListener(v -> {
            selectedDate = dateText.getText().toString();
            startTime = startText.getText().toString();
            endTime = endText.getText().toString();
            filterStatus = chosenStatus[0];

            loadRooms();
            dialog.dismiss();
        });

        dialog.show();
        int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
        dialog.getWindow().setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView buildFilterChip(String label, boolean selected) {
        TextView chip = new TextView(context);
        chip.setText(label);
        styleFilterChip(chip, selected);
        chip.setTextSize(12);
        chip.setPadding(dp(14), dp(9), dp(14), dp(9));
        com.google.android.flexbox.FlexboxLayout.LayoutParams params =
                new com.google.android.flexbox.FlexboxLayout.LayoutParams(
                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT,
                        com.google.android.flexbox.FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(8), dp(8));
        chip.setLayoutParams(params);
        return chip;
    }

    private void styleFilterChip(TextView chip, boolean selected) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setCornerRadius(dp(20));
        if (selected) {
            chip.setTextColor(Color.WHITE);
            gd.setColor(Color.parseColor("#F97316"));
        } else {
            chip.setTextColor(Color.parseColor("#F97316"));
            gd.setColor(Color.WHITE);
            gd.setStroke(dp(1), Color.parseColor("#FDDCBE"));
        }
        chip.setBackground(gd);
    }

    private int[] parseHm(String value) {
        try {
            String[] parts = value.split(":");
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (Exception e) {
            Calendar now = Calendar.getInstance();
            return new int[]{now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)};
        }
    }

    private void resetDateTime() {
        selectedDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        Calendar now = Calendar.getInstance();
        startTime = String.format(Locale.US, "%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
        now.add(Calendar.HOUR_OF_DAY, 1);
        endTime = String.format(Locale.US, "%02d:%02d",
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE));
    }

    private void loadRooms() {
        if (context instanceof Activity) LoadingOverlay.show((Activity) context, "Checking room availability...");
        RoomAvailability.loadAvailabilityForSlot(selectedDate, startTime, endTime,
                FirebaseAuth.getInstance().getUid(), results -> {
                    allRooms.clear();
                    if (results != null) {
                        for (RoomAvailability.RoomSlotStatus result : results) {
                            RoomModel room = new RoomModel(result.id, result.roomName, result.building,
                                    result.floor, result.roomType, displayStatus(result.status),
                                    result.occupiedUntil == null ? "" : result.occupiedUntil,
                                    result.capacity, R.drawable.room1);
                            room.watched = isWatched(room.roomId);
                            allRooms.add(room);
                        }
                    }
                    buildBuildingOptions();
                    updateFloorButtons();
                    applyFilters();
                    notifyAvailableWatches();
                    LoadingOverlay.hide();
                    refreshHandler.removeCallbacks(refreshRunnable);
                    refreshHandler.postDelayed(refreshRunnable, 30_000);
                });
    }

    private static String displayStatus(String value) {
        return "Maintenance".equalsIgnoreCase(value) ? "Under Maintenance" : value;
    }

    private void buildBuildingOptions() {
        String selected = buildingSpinner.getSelectedItem() == null
                ? "All Buildings" : buildingSpinner.getSelectedItem().toString();
        ArrayList<String> options = new ArrayList<>();
        options.add("All Buildings");
        for (RoomModel room : allRooms) {
            if (room.building != null && !room.building.isEmpty() && !options.contains(room.building)) {
                options.add(room.building);
            }
        }
        Collections.sort(options.subList(1, options.size()));
        buildingSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, options));
        int index = options.indexOf(selected);
        buildingSpinner.setSelection(index < 0 ? 0 : index);
    }

    private void updateFloorButtons() {
        floorContainer.removeAllViews();
        String selectedBuilding = buildingSpinner.getSelectedItem() == null
                ? "All Buildings" : buildingSpinner.getSelectedItem().toString();
        ArrayList<String> floors = new ArrayList<>();
        floors.add("All Floors");
        for (RoomModel room : allRooms) {
            if (("All Buildings".equals(selectedBuilding) || selectedBuilding.equals(room.building))
                    && room.floor != null && !room.floor.isEmpty() && !floors.contains(room.floor)) {
                floors.add(room.floor);
            }
        }
        if (!floors.contains(selectedFloor)) selectedFloor = "All Floors";
        for (String floor : floors) {
            TextView chip = buildFilterChip(floor, floor.equals(selectedFloor));
            chip.setGravity(Gravity.CENTER);
            chip.setOnClickListener(v -> {
                selectedFloor = floor;
                updateFloorButtons();
                applyFilters();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(38));
            params.setMargins(0, 0, dp(8), 0);
            floorContainer.addView(chip, params);
        }
    }

    private void applyFilters() {
        String building = buildingSpinner.getSelectedItem() == null
                ? "All Buildings" : buildingSpinner.getSelectedItem().toString();
        filteredRooms.clear();
        for (RoomModel room : allRooms) {
            if (!"All Buildings".equals(building) && !building.equals(room.building)) continue;
            if (!"All Floors".equals(selectedFloor) && !selectedFloor.equals(room.floor)) continue;
            if (!"All Status".equals(filterStatus) && !filterStatus.equals(room.status)) continue;
            filteredRooms.add(room);
        }
        adapter.notifyDataSetChanged();
        emptyState.setVisibility(filteredRooms.isEmpty() ? View.VISIBLE : View.GONE);
        updateFilterLabels();
    }

    private void updateFilterLabels() {
        if (dateButton != null) dateButton.setText("\uD83D\uDCC5  " + selectedDate);
        if (timeButton != null) {
            timeButton.setText("\uD83D\uDD52  " + formatTime(startTime) + " \u2013 " + formatTime(endTime));
        }
        if (statusChip != null) {
            statusChip.setText("All Status".equals(filterStatus) ? "Any Status" : filterStatus);
        }
        if (watchCount != null) watchCount.setText(watches.isEmpty() ? "" : watches.size() + " watching");
        if (clearFiltersButton != null) {
            clearFiltersButton.setVisibility(isAnyFilterActive() ? View.VISIBLE : View.GONE);
        }
    }

    private boolean isAnyFilterActive() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
        String building = buildingSpinner != null && buildingSpinner.getSelectedItem() != null
                ? buildingSpinner.getSelectedItem().toString() : "All Buildings";
        return !"All Buildings".equals(building)
                || !"All Floors".equals(selectedFloor)
                || !"All Status".equals(filterStatus)
                || !today.equals(selectedDate);
    }

    private void clearFilters() {
        selectedFloor = "All Floors";
        filterStatus = "All Status";
        resetDateTime();
        if (buildingSpinner != null) buildingSpinner.setSelection(0);
        updateFloorButtons();
        loadRooms();
    }

    private void reserveRoom(RoomModel room) {
        if ("Under Maintenance".equalsIgnoreCase(room.status)) return;
        Intent intent = new Intent(context, ReservationActivity.class);
        intent.putExtra("roomId", room.roomId);
        intent.putExtra("roomName", room.roomName);
        intent.putExtra("date", selectedDate);
        intent.putExtra("startTime", startTime);
        intent.putExtra("endTime", endTime);
        NavigationHelper.goTo((Activity) context, intent, "Opening reservation form...");
    }

    private void viewSchedule(RoomModel room) {
        Intent intent = new Intent(context, RoomScheduleActivity.class);
        intent.putExtra(RoomScheduleActivity.EXTRA_ROOM_ID, room.roomId);
        intent.putExtra(RoomScheduleActivity.EXTRA_ROOM_NAME, room.roomName);
        NavigationHelper.goTo((Activity) context, intent, "Loading room schedule...");
    }

    private void subscribeToWatches() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        watchRegistration = FirebaseFirestore.getInstance().collection("roomAvailabilityWatches")
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) return;
                    watches.clear();
                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Watch watch = new Watch();
                            watch.id = doc.getId();
                            watch.roomId = doc.getString("roomId");
                            watch.roomName = doc.getString("roomName");
                            watch.date = doc.getString("date");
                            watch.startTime = doc.getString("startTime");
                            watch.endTime = doc.getString("endTime");
                            watches.add(watch);
                        }
                    }
                    for (RoomModel room : allRooms) room.watched = isWatched(room.roomId);
                    adapter.notifyDataSetChanged();
                    updateFilterLabels();
                    notifyAvailableWatches();
                });
    }

    private boolean isWatched(String roomId) {
        for (Watch watch : watches) {
            if (roomId.equals(watch.roomId) && selectedDate.equals(watch.date)) return true;
        }
        return false;
    }

    private void toggleWatch(RoomModel room) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            Toast.makeText(context, "Please sign in again.", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("Available".equalsIgnoreCase(room.status)) {
            Toast.makeText(context, "This room is already available.", Toast.LENGTH_SHORT).show();
            return;
        }
        Watch existing = null;
        for (Watch watch : watches) {
            if (room.roomId.equals(watch.roomId) && selectedDate.equals(watch.date)) {
                existing = watch;
                break;
            }
        }
        if (existing != null) {
            FirebaseFirestore.getInstance().collection("roomAvailabilityWatches")
                    .document(existing.id).delete()
                    .addOnSuccessListener(ignored -> Toast.makeText(context,
                            "Availability watch removed.", Toast.LENGTH_SHORT).show());
            return;
        }
        java.util.Map<String, Object> watch = new java.util.HashMap<>();
        watch.put("userId", uid);
        watch.put("roomId", room.roomId);
        watch.put("roomName", room.roomName);
        watch.put("building", room.building == null ? "" : room.building);
        watch.put("floor", room.floor == null ? "" : room.floor);
        watch.put("date", selectedDate);
        watch.put("startTime", startTime);
        watch.put("endTime", endTime);
        watch.put("roomStatusAtWatch", room.status);
        watch.put("notified", false);
        watch.put("createdAt", Timestamp.now());
        FirebaseFirestore.getInstance().collection("roomAvailabilityWatches").add(watch)
                .addOnSuccessListener(ignored -> Toast.makeText(context,
                        "You'll be notified when this room is available.", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(error -> Toast.makeText(context,
                        "Could not add watch: " + error.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void notifyAvailableWatches() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        for (RoomModel room : allRooms) {
            if ("Available".equalsIgnoreCase(room.status)) notifyAvailable(room, uid);
        }
    }

    private void notifyAvailable(RoomModel room, String uid) {
        for (Watch watch : new ArrayList<>(watches)) {
            if (!room.roomId.equals(watch.roomId) || !selectedDate.equals(watch.date)
                    || notifyingWatchIds.contains(watch.id)) continue;
            notifyingWatchIds.add(watch.id);
            java.util.Map<String, Object> notification = new java.util.HashMap<>();
            notification.put("userId", uid);
            notification.put("ownerType", "faculty");
            notification.put("title", "Room Now Available");
            notification.put("message", room.roomName + " is now available for your watched slot on "
                    + watch.date + " (" + watch.startTime + " \u2013 " + watch.endTime + ").");
            notification.put("type", "room-available");
            notification.put("roomId", room.roomId);
            notification.put("roomName", room.roomName);
            notification.put("date", watch.date);
            notification.put("startTime", watch.startTime);
            notification.put("endTime", watch.endTime);
            notification.put("unread", true);
            notification.put("archived", false);
            notification.put("badge", "NEW");
            notification.put("createdAt", FieldValue.serverTimestamp());
            FirebaseFirestore.getInstance().collection("notifications").add(notification)
                    .addOnCompleteListener(ignored -> FirebaseFirestore.getInstance()
                            .collection("roomAvailabilityWatches").document(watch.id).delete());
        }
    }

    private String formatTime(String value) {
        try {
            String[] parts = value.split(":");
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, Integer.parseInt(parts[0]));
            calendar.set(Calendar.MINUTE, Integer.parseInt(parts[1]));
            return new SimpleDateFormat("h:mm a", Locale.US).format(calendar.getTime());
        } catch (Exception ignored) {
            return value;
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}