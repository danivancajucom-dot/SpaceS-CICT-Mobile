package com.example.spacescict;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class ReservationPage {

    public ReservationPage(Context context, View view) {

        RecyclerView recycler = view.findViewById(R.id.reservationRecycler);
        if (recycler == null) return;

        ArrayList<ReservationModel> list = new ArrayList<>();
        ReservationAdapter adapter = new ReservationAdapter(list);

        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        String uid = FirebaseAuth.getInstance().getUid();

        FirebaseFirestore.getInstance()
                .collection("reservationRequests") // 🔥 matches your web rules
                .whereEqualTo("userId", uid)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    list.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        list.add(new ReservationModel(
                                doc.getString("status"),
                                doc.getString("room"),
                                doc.getString("subject"),
                                R.drawable.room1
                        ));
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}