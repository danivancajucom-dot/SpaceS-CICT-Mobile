package com.example.spacescict;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DashboardActivity extends AppCompatActivity {

    FrameLayout contentFrame;
    BottomNavigationView bottomNav;
    LinearLayout header;

    ProfilePage currentProfilePage;
    ActivityResultLauncher<String> photoPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        header = findViewById(R.id.header);
        contentFrame = findViewById(R.id.contentFrame);
        bottomNav = findViewById(R.id.bottomNav);
        ImageView logout = findViewById(R.id.logout);
        ImageView notification = findViewById(R.id.notification);

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && currentProfilePage != null) {
                        uploadToCloudinary(uri);
                    }
                });

        logout.setOnClickListener(v -> {
            ConfirmDialog.show(
                    this, "Logout", " Are you sure you want to log out?",
                    "Confirm", "Cancel",
                    () -> {
                        FirebaseAuth.getInstance().signOut();
                        Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
            );
        });

        notification.setOnClickListener(v ->
                startActivity(new Intent(DashboardActivity.this, NotificationsActivity.class)));

        loadHome();

        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                loadHome();
                return true;
            } else if (id == R.id.nav_schedule) {
                loadWeeklySchedule();
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

    private void loadWeeklySchedule() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.activity_weekly_schedule, contentFrame, true);
        new WeeklySchedulePage(this, view);
    }
    private void loadHome() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.layout_home, contentFrame, true);
        new HomePage(this, view);
    }

    private void loadRooms() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.layout_rooms, contentFrame, true);
        new RoomsPage(this, view);
    }

    private void loadReservation() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.activity_reservation_page, contentFrame, true);
        new ReservationPage(this, view);
    }

    private void loadProfile() {
        header.setVisibility(View.GONE);
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.activity_profile, contentFrame, true);
        currentProfilePage = new ProfilePage(this, view, this::loadHome,
                () -> photoPickerLauncher.launch("image/*"));
    }

    void uploadToCloudinary(Uri uri) {
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                InputStream input = getContentResolver().openInputStream(uri);
                byte[] bytes = input.readAllBytes();
                input.close();

                String boundary = "Boundary-" + System.currentTimeMillis();
                URL url = new URL("https://api.cloudinary.com/v1_1/dqn1s5ujs/image/upload");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

                OutputStream out = conn.getOutputStream();
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\nSpaceSCICT\r\n").getBytes());
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"folder\"\r\n\r\nspaces/profiles\r\n").getBytes());
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes());
                out.write(bytes);
                out.write(("\r\n--" + boundary + "--\r\n").getBytes());
                out.close();

                int code = conn.getResponseCode();
                InputStream respStream = code == 200 ? conn.getInputStream() : conn.getErrorStream();
                String response = new String(respStream.readAllBytes());

                if (code == 200) {
                    JSONObject json = new JSONObject(response);
                    String secureUrl = json.getString("secure_url");
                    runOnUiThread(() -> currentProfilePage.onPhotoUploaded(secureUrl));
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Upload error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}