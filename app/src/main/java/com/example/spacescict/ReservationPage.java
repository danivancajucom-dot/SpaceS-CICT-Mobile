package com.example.spacescict;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;

public class ReservationPage {

    ArrayList<ReservationModel> fullList = new ArrayList<>();
    ArrayList<ReservationModel> filteredList = new ArrayList<>();
    ReservationAdapter adapter;
    String currentFilter = "all";
    TextView tabAll, tabPending, tabApproved, tabDenied;

    public ReservationPage(Context context, View view) {
        RecyclerView recycler = view.findViewById(R.id.reservationRecycler);
        if (recycler == null) return;

        tabAll = view.findViewById(R.id.tabAll);
        tabPending = view.findViewById(R.id.tabPending);
        tabApproved = view.findViewById(R.id.tabApproved);
        tabDenied = view.findViewById(R.id.tabDenied);

        adapter = new ReservationAdapter(filteredList);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        adapter.setOnItemClick(reservation -> {
            Intent intent = new Intent(context, ReservationDetailActivity.class);
            intent.putExtra(ReservationDetailActivity.EXTRA_RESERVATION_ID, reservation.id);
            context.startActivity(intent);
        });

        if (tabAll != null) tabAll.setOnClickListener(v -> setFilter("all"));
        if (tabPending != null) tabPending.setOnClickListener(v -> setFilter("pending"));
        if (tabApproved != null) tabApproved.setOnClickListener(v -> setFilter("approved"));
        if (tabDenied != null) tabDenied.setOnClickListener(v -> setFilter("denied"));

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        FirebaseFirestore.getInstance()
                .collection("reservationRequests")
                .whereEqualTo("userId", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    fullList.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        ReservationModel m = new ReservationModel(
                                doc.getString("status"),
                                doc.getString("roomName"),
                                doc.getString("courseTitle"),
                                R.drawable.room1
                        );
                        m.id = doc.getId();
                        fullList.add(m);
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
        for (ReservationModel m : fullList) {
            String status = m.status != null ? m.status : "";
            switch (currentFilter) {
                case "pending": if (status.equalsIgnoreCase("Pending")) filteredList.add(m); break;
                case "approved": if (status.equalsIgnoreCase("Approved")) filteredList.add(m); break;
                case "denied": if (status.equalsIgnoreCase("Rejected")) filteredList.add(m); break;
                default: filteredList.add(m);
            }
        }
        adapter.notifyDataSetChanged();
    }

    void updateTabStyles() {
        int active = Color.parseColor("#F97316");
        int inactive = Color.parseColor("#64748B");
        if (tabAll != null) tabAll.setTextColor(currentFilter.equals("all") ? active : inactive);
        if (tabPending != null) tabPending.setTextColor(currentFilter.equals("pending") ? active : inactive);
        if (tabApproved != null) tabApproved.setTextColor(currentFilter.equals("approved") ? active : inactive);
        if (tabDenied != null) tabDenied.setTextColor(currentFilter.equals("denied") ? active : inactive);
    }
}