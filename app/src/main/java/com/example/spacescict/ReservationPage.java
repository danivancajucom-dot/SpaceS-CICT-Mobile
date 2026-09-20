package com.example.spacescict;

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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * "My Reservations" list.
 *
 * Added in this pass: search (by course, room or purpose), a newest/oldest
 * sort toggle, per-tab counts, an empty state, and a "Load more" footer so a
 * long history doesn't all render at once. Also fixed a real bug: the tabs'
 * active color was only ever applied after the FIRST tap - on initial load
 * every tab looked identical regardless of which one ("All") was actually
 * selected.
 */
public class ReservationPage {

    private static final int PAGE_SIZE = 10;

    ArrayList<ReservationModel> fullList = new ArrayList<>();
    ArrayList<ReservationModel> filteredList = new ArrayList<>();
    ReservationAdapter adapter;
    String currentFilter = "all";
    TextView tabAll, tabPending, tabApproved, tabDenied, tabCancelled;

    private EditText searchInput;
    private TextView sortBtn;
    private View emptyState;

    private String searchQuery = "";
    private boolean sortNewestFirst = true;
    private int visibleCount = PAGE_SIZE;

    public ReservationPage(Context context, View view) {
        RecyclerView recycler = view.findViewById(R.id.reservationRecycler);
        if (recycler == null) return;

        tabAll = view.findViewById(R.id.tabAll);
        tabPending = view.findViewById(R.id.tabPending);
        tabApproved = view.findViewById(R.id.tabApproved);
        tabDenied = view.findViewById(R.id.tabDenied);
        tabCancelled = view.findViewById(R.id.tabCancelled);

        searchInput = view.findViewById(R.id.reservationSearchInput);
        sortBtn = view.findViewById(R.id.reservationSortBtn);
        emptyState = view.findViewById(R.id.reservationsEmptyState);

        View fab = view.findViewById(R.id.fabAddReservation);

        adapter = new ReservationAdapter(filteredList);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        adapter.setOnItemClick(reservation -> {
            Intent intent = new Intent(context, ReservationDetailActivity.class);
            intent.putExtra(ReservationDetailActivity.EXTRA_RESERVATION_ID, reservation.id);
            context.startActivity(intent);
        });
        adapter.setOnLoadMore(() -> {
            visibleCount += PAGE_SIZE;
            applyFilter();
        });

        if (tabAll != null) tabAll.setOnClickListener(v -> setFilter("all"));
        if (tabPending != null) tabPending.setOnClickListener(v -> setFilter("pending"));
        if (tabApproved != null) tabApproved.setOnClickListener(v -> setFilter("approved"));
        if (tabDenied != null) tabDenied.setOnClickListener(v -> setFilter("denied"));
        if (tabCancelled != null) tabCancelled.setOnClickListener(v -> setFilter("cancelled"));
        if (fab != null) {
            fab.setOnClickListener(v -> context.startActivity(new Intent(context, ReservationActivity.class)));
        }
        View emptyStateCreateBtn = view.findViewById(R.id.emptyStateCreateBtn);
        if (emptyStateCreateBtn != null) {
            emptyStateCreateBtn.setOnClickListener(v -> context.startActivity(new Intent(context, ReservationActivity.class)));
        }

        if (searchInput != null) {
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    searchQuery = s.toString().trim().toLowerCase(Locale.US);
                    visibleCount = PAGE_SIZE;
                    applyFilter();
                }
            });
        }

        if (sortBtn != null) {
            updateSortLabel();
            sortBtn.setOnClickListener(v -> {
                sortNewestFirst = !sortNewestFirst;
                updateSortLabel();
                visibleCount = PAGE_SIZE;
                applyFilter();
            });
        }

        // Show the correct active tab immediately, instead of only after a tap.
        updateTabStyles(0, 0, 0, 0, 0);

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
                        m.facultyName = doc.getString("facultyName");
                        m.date = doc.getString("date");
                        m.startTime = doc.getString("startTime");
                        m.endTime = doc.getString("endTime");
                        m.purpose = doc.getString("purpose");
                        m.denialReason = doc.getString("denialReason");
                        fullList.add(m);
                    }
                    applyFilter();
                });
    }

    void setFilter(String filter) {
        currentFilter = filter;
        visibleCount = PAGE_SIZE;
        applyFilter();
    }

    private void updateSortLabel() {
        if (sortBtn != null) sortBtn.setText(sortNewestFirst ? "Newest" : "Oldest");
    }

    /** Reservations matching the search box, regardless of the active status tab. */
    private List<ReservationModel> bySearch() {
        if (searchQuery.isEmpty()) return new ArrayList<>(fullList);
        List<ReservationModel> result = new ArrayList<>();
        for (ReservationModel m : fullList) {
            if (matchesSearch(m)) result.add(m);
        }
        return result;
    }

    private boolean matchesSearch(ReservationModel m) {
        return containsQuery(m.courseTitle) || containsQuery(m.roomName) || containsQuery(m.purpose);
    }

    private boolean containsQuery(String value) {
        return value != null && value.toLowerCase(Locale.US).contains(searchQuery);
    }

    void applyFilter() {
        List<ReservationModel> searched = bySearch();

        int countAll = searched.size();
        int countPending = 0, countApproved = 0, countDenied = 0, countCancelled = 0;
        List<ReservationModel> matched = new ArrayList<>();

        for (ReservationModel m : searched) {
            String status = m.status != null ? m.status.toLowerCase(Locale.US).trim() : "";
            switch (status) {
                case "pending": countPending++; break;
                case "approved": countApproved++; break;
                case "rejected": countDenied++; break;
                case "cancelled": countCancelled++; break;
                default: break;
            }

            boolean include;
            switch (currentFilter) {
                case "pending": include = status.equals("pending"); break;
                case "approved": include = status.equals("approved"); break;
                case "denied": include = status.equals("rejected"); break;
                case "cancelled": include = status.equals("cancelled"); break;
                default: include = true;
            }
            if (include) matched.add(m);
        }

        // The Firestore query already orders by createdAt DESCENDING ("Newest"),
        // so "Oldest" is simply that same list reversed.
        if (!sortNewestFirst) Collections.reverse(matched);

        int limit = Math.min(visibleCount, matched.size());
        filteredList.clear();
        filteredList.addAll(matched.subList(0, limit));
        adapter.setHasMore(matched.size() > limit);
        adapter.notifyDataSetChanged();

        if (emptyState != null) {
            emptyState.setVisibility(matched.isEmpty() ? View.VISIBLE : View.GONE);
        }

        updateTabStyles(countAll, countPending, countApproved, countDenied, countCancelled);
    }

    void updateTabStyles(int all, int pending, int approved, int denied, int cancelled) {
        int active = Color.parseColor("#F97316");
        int inactive = Color.parseColor("#64748B");

        if (tabAll != null) {
            tabAll.setTextColor(currentFilter.equals("all") ? active : inactive);
            tabAll.setText("All" + countSuffix(all));
        }
        if (tabPending != null) {
            tabPending.setTextColor(currentFilter.equals("pending") ? active : inactive);
            tabPending.setText("Pending" + countSuffix(pending));
        }
        if (tabApproved != null) {
            tabApproved.setTextColor(currentFilter.equals("approved") ? active : inactive);
            tabApproved.setText("Approved" + countSuffix(approved));
        }
        if (tabDenied != null) {
            tabDenied.setTextColor(currentFilter.equals("denied") ? active : inactive);
            tabDenied.setText("Denied" + countSuffix(denied));
        }
        if (tabCancelled != null) {
            tabCancelled.setTextColor(currentFilter.equals("cancelled") ? active : inactive);
            tabCancelled.setText("Cancelled" + countSuffix(cancelled));
        }
    }

    private String countSuffix(int count) {
        return count > 0 ? " (" + count + ")" : "";
    }
}