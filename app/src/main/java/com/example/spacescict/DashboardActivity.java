package com.example.spacescict;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DashboardActivity extends AppCompatActivity {

    FrameLayout contentFrame;
    BottomNavigationView bottomNav;

    LinearLayout header;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        header = findViewById(R.id.header);
        contentFrame = findViewById(R.id.contentFrame);
        bottomNav = findViewById(R.id.bottomNav);
        ImageView logout = findViewById(R.id.logout);
        ImageView notification = findViewById(R.id.notification);

        logout.setOnClickListener(v -> {
            ConfirmDialog.show(
                    this,
                    "Logout",
                    " Are you sure you want to log out?",
                    "Confirm",
                    "Cancel",
                    () -> {
                        finish();
                    }
            );
        });

        notification.setOnClickListener(v -> {
                        Intent intent =
                                new Intent(
                                        DashboardActivity.this,
                                        NotificationsActivity.class
                                );
                        startActivity(intent);
        });

        // default page
        loadHome();

        bottomNav.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_home) {

                loadHome();
                return true;

            } else if (id == R.id.nav_schedule) {

                loadHome();
                return true;

            } else if (id == R.id.nav_reservations) {
                loadReservation();
                return true;
            } else if (id == R.id.nav_rooms) {

                loadRooms();
                return true;

            } else if (id == R.id.nav_profile) {

                loadProfile();
                return true;
            }

            return false;
        });
    }

    // ================= HOME =================

    private void loadHome() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();

        View view = LayoutInflater.from(this)
                .inflate(
                        R.layout.layout_home,
                        contentFrame,
                        true
                );

        new HomePage(this, view);
    }

    // ================= ROOMS =================

    private void loadRooms() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();

        View view = LayoutInflater.from(this)
                .inflate(R.layout.layout_rooms,
                        contentFrame,
                        true);

        new RoomsPage(this, view);
    }

    // ================= RESERVATION =================

    private void loadReservation() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();

        View view = LayoutInflater.from(this)
                .inflate(R.layout.activity_reservation_page,
                        contentFrame,
                        true);

        new ReservationPage(this, view);
    }

    // ================= PROFILE =================

    private void loadProfile() {

        header.setVisibility(View.GONE);   // 🔥 hide top bar

        contentFrame.removeAllViews();

        View view = LayoutInflater.from(this)
                .inflate(R.layout.activity_profile,
                        contentFrame,
                        true);
    }
}