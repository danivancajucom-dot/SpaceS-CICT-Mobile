package com.example.spacescict;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;

public class RoomsPage {

    private Context context;

    private final ArrayList<RoomModel> fullList = new ArrayList<>();
    private final ArrayList<RoomModel> filteredList = new ArrayList<>();

    private RoomAdapter adapter;

    private String currentFilter = "All";
    private String currentSearchQuery = "";

    private TextView tabAll;
    private TextView tabAvailable;
    private TextView tabOccupied;
    private TextView tabMaintenance;

    public RoomsPage(Context context, View view) {

        this.context = context;

        RecyclerView recyclerView =
                view.findViewById(R.id.roomsRecycler);

        if (recyclerView == null) {
            return;
        }

        tabAll = view.findViewById(R.id.tabAll);
        tabAvailable = view.findViewById(R.id.tabAvailable);
        tabOccupied = view.findViewById(R.id.tabOccupied);
        tabMaintenance = view.findViewById(R.id.tabMaintenance);

        EditText searchInput =
                view.findViewById(R.id.searchInput);

        // =========================
        // ADAPTER
        // =========================

        adapter = new RoomAdapter(
                filteredList,

                room -> {

                    if (!(context instanceof Activity)) {
                        return;
                    }

                    NavigationHelper.goTo(
                            (Activity) context,
                            ReservationActivity.class,
                            "Opening reservation form..."
                    );
                },

                room -> {

                    if (!(context instanceof Activity)) {
                        return;
                    }

                    Intent intent =
                            new Intent(
                                    context,
                                    RoomScheduleActivity.class
                            );

                    intent.putExtra(
                            RoomScheduleActivity.EXTRA_ROOM_ID,
                            room.roomId
                    );

                    intent.putExtra(
                            RoomScheduleActivity.EXTRA_ROOM_NAME,
                            room.roomName
                    );

                    NavigationHelper.goTo(
                            (Activity) context,
                            intent,
                            "Loading room schedule..."
                    );
                }
        );

        recyclerView.setLayoutManager(
                new LinearLayoutManager(context)
        );

        recyclerView.setAdapter(adapter);

        // =========================
        // FILTERS
        // =========================

        if (tabAll != null) {
            tabAll.setOnClickListener(
                    v -> setFilter("All")
            );
        }

        if (tabAvailable != null) {
            tabAvailable.setOnClickListener(
                    v -> setFilter("Available")
            );
        }

        if (tabOccupied != null) {
            tabOccupied.setOnClickListener(
                    v -> setFilter("Occupied")
            );
        }

        if (tabMaintenance != null) {
            tabMaintenance.setOnClickListener(
                    v -> setFilter("Maintenance")
            );
        }

        // =========================
        // SEARCH
        // =========================

        if (searchInput != null) {

            searchInput.addTextChangedListener(
                    new TextWatcher() {

                        @Override
                        public void beforeTextChanged(
                                CharSequence s,
                                int start,
                                int count,
                                int after) {
                        }

                        @Override
                        public void onTextChanged(
                                CharSequence s,
                                int start,
                                int before,
                                int count) {

                            currentSearchQuery =
                                    s.toString()
                                            .toLowerCase(Locale.US)
                                            .trim();

                            applyFilter();
                        }

                        @Override
                        public void afterTextChanged(
                                Editable s) {
                        }
                    }
            );
        }

        updateTabStyles();
        loadRooms();
    }

    // =========================
    // LOAD ROOMS
    // =========================

    private void loadRooms() {

        if (context instanceof Activity) {
            LoadingOverlay.show(
                    (Activity) context,
                    "Loading rooms..."
            );
        }

        RoomAvailability.loadCurrentStatus(
                results -> {

                    fullList.clear();

                    if (results != null) {

                        for (
                                RoomAvailability.RoomStatus rs
                                : results
                        ) {

                            if (rs == null) {
                                continue;
                            }

                            fullList.add(
                                    new RoomModel(
                                            rs.id,
                                            rs.roomName,
                                            rs.floor,
                                            rs.roomType,
                                            rs.status,
                                            rs.occupiedUntil,
                                            rs.capacity,
                                            R.drawable.room1
                                    )
                            );
                        }
                    }

                    applyFilter();

                    if (context instanceof Activity) {
                        LoadingOverlay.hide();
                    }
                }
        );
    }

    // =========================
    // FILTER
    // =========================

    private void setFilter(String filter) {

        currentFilter =
                filter != null
                        ? filter
                        : "All";

        updateTabStyles();
        applyFilter();
    }

    // =========================
    // APPLY FILTER
    // =========================

    private void applyFilter() {

        filteredList.clear();

        for (RoomModel r : fullList) {

            if (r == null) {
                continue;
            }

            String roomStatus =
                    r.status != null
                            ? r.status
                            : "";

            String roomName =
                    r.roomName != null
                            ? r.roomName
                            : "";

            String roomType =
                    r.roomType != null
                            ? r.roomType
                            : "";

            boolean matchesTab =
                    currentFilter.equalsIgnoreCase("All")
                            || currentFilter.equalsIgnoreCase(
                            roomStatus
                    );

            boolean matchesSearch =
                    currentSearchQuery.isEmpty()
                            || roomName
                            .toLowerCase(Locale.US)
                            .contains(currentSearchQuery)
                            || roomType
                            .toLowerCase(Locale.US)
                            .contains(currentSearchQuery);

            if (matchesTab && matchesSearch) {
                filteredList.add(r);
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // =========================
    // TAB STYLES
    // =========================

    private void updateTabStyles() {

        int active =
                Color.parseColor("#F97316");

        int inactive =
                Color.parseColor("#64748B");

        if (tabAll != null) {

            tabAll.setTextColor(
                    currentFilter.equalsIgnoreCase("All")
                            ? active
                            : inactive
            );
        }

        if (tabAvailable != null) {

            tabAvailable.setTextColor(
                    currentFilter.equalsIgnoreCase("Available")
                            ? active
                            : inactive
            );
        }

        if (tabOccupied != null) {

            tabOccupied.setTextColor(
                    currentFilter.equalsIgnoreCase("Occupied")
                            ? active
                            : inactive
            );
        }

        if (tabMaintenance != null) {

            tabMaintenance.setTextColor(
                    currentFilter.equalsIgnoreCase("Maintenance")
                            ? active
                            : inactive
            );
        }
    }
}