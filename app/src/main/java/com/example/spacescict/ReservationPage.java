// ReservationPage.java
package com.example.spacescict;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class ReservationPage {

    public ReservationPage(Context context, View view) {

        RecyclerView recycler =
                view.findViewById(R.id.reservationRecycler);

        if (recycler == null) return;

        ArrayList<ReservationModel> list =
                new ArrayList<>();

        list.add(new ReservationModel(
                "PENDING",
                "SDL3",
                "Game Development",
                R.drawable.room1
        ));

        list.add(new ReservationModel(
                "APPROVED",
                "IT13",
                "Social and Professional Issues",
                R.drawable.room2
        ));

        list.add(new ReservationModel(
                "DENIED",
                "SDL1",
                "Computer Programming",
                R.drawable.room1
        ));

        recycler.setLayoutManager(
                new LinearLayoutManager(context)
        );

        recycler.setAdapter(
                new ReservationAdapter(list)
        );
    }
}