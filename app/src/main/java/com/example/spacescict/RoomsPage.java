package com.example.spacescict;

import android.content.Context;
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class RoomsPage {

    public RoomsPage(Context context, View view) {

        RecyclerView recyclerView =
                view.findViewById(R.id.roomsRecycler);

        if (recyclerView == null) return;

        ArrayList<RoomModel> list = new ArrayList<>();

        list.add(new RoomModel(
                "SDL2",
                "AVAILABLE NOW",
                "30 Capacity",
                "Until 3:30 PM",
                R.drawable.room1
        ));

        list.add(new RoomModel(
                "Prog Lab 1",
                "OCCUPIED",
                "40 Capacity",
                "Occupied",
                R.drawable.room2
        ));

        recyclerView.setLayoutManager(
                new LinearLayoutManager(context)
        );

        recyclerView.setAdapter(
                new RoomAdapter(list)
        );
    }
}