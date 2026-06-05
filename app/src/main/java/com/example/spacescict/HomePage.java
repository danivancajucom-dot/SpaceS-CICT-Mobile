package com.example.spacescict;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

public class HomePage {

    Button reserveButton;


    public HomePage(
            Context context,
            View view
    ) {

        reserveButton =
                view.findViewById(R.id.reserveButton);

        reserveButton.setOnClickListener(v -> {

            context.startActivity(
                    new Intent(
                            context,
                            ReservationActivity.class
                    )
            );
        });
    }
}