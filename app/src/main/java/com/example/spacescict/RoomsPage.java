package com.example.spacescict;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RoomsPage {

    ArrayList<RoomModel> fullList = new ArrayList<>();
    ArrayList<RoomModel> filteredList = new ArrayList<>();
    RoomAdapter adapter;
    String currentFilter = "All";
    TextView tabAll, tabAvailable, tabOccupied, tabMaintenance;

    public RoomsPage(Context context, View view) {
        RecyclerView recyclerView = view.findViewById(R.id.roomsRecycler);
        if (recyclerView == null) return;

        tabAll = view.findViewById(R.id.tabAll);
        tabAvailable = view.findViewById(R.id.tabAvailable);
        tabOccupied = view.findViewById(R.id.tabOccupied);
        tabMaintenance = view.findViewById(R.id.tabMaintenance);

        adapter = new RoomAdapter(filteredList, room ->
                context.startActivity(new Intent(context, ReservationActivity.class)));

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        if (tabAll != null) tabAll.setOnClickListener(v -> setFilter("All"));
        if (tabAvailable != null) tabAvailable.setOnClickListener(v -> setFilter("Available"));
        if (tabOccupied != null) tabOccupied.setOnClickListener(v -> setFilter("Occupied"));
        if (tabMaintenance != null) tabMaintenance.setOnClickListener(v -> setFilter("Maintenance"));

        loadRooms();
    }

    void loadRooms() {
        RoomAvailability.loadCurrentStatus(results -> {
            fullList.clear();
            for (RoomAvailability.RoomStatus rs : results) {
                fullList.add(new RoomModel(rs.id, rs.roomName, rs.floor, rs.roomType,
                        rs.status, rs.occupiedUntil, rs.capacity, R.drawable.room1));
            }
            applyFilter();
        });
    }

    void setFilter(String filter) {
        currentFilter = filter;
        updateTabStyles();
        applyFilter();
    }

    void applyFilter() {
        filteredList.clear();
        for (RoomModel r : fullList) {
            if (currentFilter.equals("All") || currentFilter.equalsIgnoreCase(r.status)) {
                filteredList.add(r);
            }
        }
        adapter.notifyDataSetChanged();
    }

    void updateTabStyles() {
        int active = Color.parseColor("#F97316");
        int inactive = Color.parseColor("#64748B");
        if (tabAll != null) tabAll.setTextColor(currentFilter.equals("All") ? active : inactive);
        if (tabAvailable != null) tabAvailable.setTextColor(currentFilter.equals("Available") ? active : inactive);
        if (tabOccupied != null) tabOccupied.setTextColor(currentFilter.equals("Occupied") ? active : inactive);
        if (tabMaintenance != null) tabMaintenance.setTextColor(currentFilter.equals("Maintenance") ? active : inactive);
    }
}