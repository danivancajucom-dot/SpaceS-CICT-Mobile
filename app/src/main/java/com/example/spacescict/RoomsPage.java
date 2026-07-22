package com.example.spacescict;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class RoomsPage {

    public RoomsPage(Context context, View view) {

        RecyclerView recyclerView = view.findViewById(R.id.roomsRecycler);
        if (recyclerView == null) return;

        ArrayList<RoomModel> list = new ArrayList<>();
        RoomAdapter adapter = new RoomAdapter(list);

        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(adapter);

        FirebaseFirestore.getInstance()
                .collection("rooms")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;

                    list.clear();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        list.add(new RoomModel(
                                doc.getString("name"),
                                doc.getString("status"),
                                doc.getString("capacity"),
                                doc.getString("availableUntil"),
                                R.drawable.room1 // imageRes in Firestore is likely a URL/path, not a drawable int — see note below
                        ));
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}