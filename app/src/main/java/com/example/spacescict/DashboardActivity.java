package com.example.spacescict;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class DashboardActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private FrameLayout contentFrame;
    private LinearLayout header, navItemsContainer, profileExpandedOptions;
    private TextView drawerProfileName;
    private ImageView drawerProfileImage;
    private final ArrayList<View> navigationRows = new ArrayList<>();
    private ProfilePage currentProfilePage;
    private RoomIssuesPage currentRoomIssuesPage;
    private ActivityResultLauncher<String> photoPickerLauncher;
    private ActivityResultLauncher<String> issuePhotoPickerLauncher;
    private ActivityResultLauncher<String[]> scheduleImportLauncher;
    private int currentNavId = R.id.nav_home;
    private TextView notificationBadge;
    private ListenerRegistration notificationBadgeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        drawerLayout = findViewById(R.id.drawerLayout);
        contentFrame = findViewById(R.id.contentFrame);
        header = findViewById(R.id.header);
        navItemsContainer = findViewById(R.id.navItemsContainer);
        profileExpandedOptions = findViewById(R.id.profileExpandedOptions);
        drawerProfileName = findViewById(R.id.drawerProfileName);
        drawerProfileImage = findViewById(R.id.drawerProfileImage);

        ImageView menuButton = findViewById(R.id.menuBtn);
        ImageView closeButton = findViewById(R.id.closeDrawerBtn);
        ImageView notification = findViewById(R.id.notification);
        notificationBadge = findViewById(R.id.notificationBadge);
        LinearLayout profileMiniRow = findViewById(R.id.profileMiniRow);
        ImageView profileArrow = findViewById(R.id.profileExpandArrow);

        if (menuButton != null) menuButton.setOnClickListener(v -> drawerLayout.openDrawer(
                androidx.core.view.GravityCompat.START));
        if (closeButton != null) closeButton.setOnClickListener(v -> closeDrawer());
        if (notification != null) notification.setOnClickListener(v ->
                NavigationHelper.goTo(this, NotificationsActivity.class, "Loading notifications..."));

        listenForUnreadNotifications();
        NotificationHelper.updateFCMToken(FirebaseAuth.getInstance().getUid());

        photoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && currentProfilePage != null) {
                        uploadToCloudinary(uri, "spaces/profiles", secureUrl -> {
                            currentProfilePage.onPhotoUploaded(secureUrl);
                            loadDrawerProfile();
                        });
                    }
                });

        issuePhotoPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && currentRoomIssuesPage != null) {
                        currentRoomIssuesPage.onPhotosPicked(java.util.Collections.singletonList(uri));
                    }
                });

        scheduleImportLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) importSchedule(uri);
                });

        if (profileMiniRow != null) {
            profileMiniRow.setOnClickListener(v -> {
                boolean expanded = profileExpandedOptions.getVisibility() == View.VISIBLE;
                profileExpandedOptions.setVisibility(expanded ? View.GONE : View.VISIBLE);
                if (profileArrow != null) profileArrow.setRotation(expanded ? 90 : 270);
            });
        }

        View profileButton = findViewById(R.id.drawerProfileBtn);
        if (profileButton != null) profileButton.setOnClickListener(v -> {
            closeDrawer();
            openProfile();
        });

        View settingsButton = findViewById(R.id.drawerSettingsBtn);
        if (settingsButton != null) settingsButton.setOnClickListener(v -> {
            closeDrawer();
            loadSettings();
        });

        View logoutButton = findViewById(R.id.drawerLogoutBtn);
        if (logoutButton != null) logoutButton.setOnClickListener(v -> {
            closeDrawer();
            ConfirmDialog.show(this, "Logout", "Are you sure you want to log out?",
                    "Confirm", "Cancel", () -> {
                        LoadingOverlay.show(this, "Signing out...");
                        FirebaseAuth.getInstance().signOut();
                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }, 400);
                    });
        });

        buildNavItems();
        loadDrawerProfile();
        loadHome();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void closeDrawer() {
        if (drawerLayout != null) drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
    }

    private void buildNavItems() {
        if (navItemsContainer == null) return;
        navItemsContainer.removeAllViews();
        navigationRows.clear();

        int[][] items = {
                {R.id.nav_home, R.drawable.ic_home},
                {R.id.nav_schedule, R.drawable.ic_calendar},
                {R.id.nav_rooms, R.drawable.ic_laptop},
                {R.id.nav_issues, R.drawable.ic_warning},
                {R.id.nav_reservations, R.drawable.ic_check},
                {R.id.nav_announcement, R.drawable.ic_notification}
        };
        String[] labels = {
                "Dashboard", "Schedule", "Rooms", "Reported Issues",
                "Reservations", "Announcement Channel"
        };

        for (int i = 0; i < items.length; i++) {
            View row = LayoutInflater.from(this).inflate(
                    R.layout.drawer_nav_item, navItemsContainer, false);
            ImageView icon = row.findViewById(R.id.itemIcon);
            TextView label = row.findViewById(R.id.itemLabel);
            icon.setImageResource(items[i][1]);
            label.setText(labels[i]);
            int navId = items[i][0];
            row.setTag(navId);
            row.setOnClickListener(v -> {
                closeDrawer();
                onNavItemClicked(navId);
            });
            navigationRows.add(row);
            navItemsContainer.addView(row);
        }
        refreshNavHighlight();
    }

    private void refreshNavHighlight() {
        for (View row : navigationRows) {
            boolean active = row.getTag() instanceof Integer
                    && ((Integer) row.getTag()) == currentNavId;
            GradientDrawable background = new GradientDrawable();
            background.setColor(active ? Color.parseColor("#FFF1E6") : Color.TRANSPARENT);
            background.setCornerRadius(dp(12));
            row.setBackground(background);
            ImageView icon = row.findViewById(R.id.itemIcon);
            TextView label = row.findViewById(R.id.itemLabel);
            icon.setColorFilter(Color.parseColor(active ? "#F97316" : "#78716C"));
            label.setTextColor(Color.parseColor(active ? "#EA580C" : "#44403C"));
            row.setSelected(active);
        }
    }

    private void onNavItemClicked(int navId) {
        currentNavId = navId;
        refreshNavHighlight();
        if (navId == R.id.nav_home) loadHome();
        else if (navId == R.id.nav_schedule) loadWeeklySchedule();
        else if (navId == R.id.nav_rooms) loadRooms();
        else if (navId == R.id.nav_issues) loadRoomIssues();
        else if (navId == R.id.nav_reservations) loadReservation();
        else if (navId == R.id.nav_announcement) loadAnnouncements();
    }

    private void loadDrawerProfile() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    String first = doc.getString("firstName") == null ? "" : doc.getString("firstName");
                    String last = doc.getString("lastName") == null ? "" : doc.getString("lastName");
                    String name = (first + " " + last).trim();
                    drawerProfileName.setText(name.isEmpty() ? "Faculty" : name);

                    String photoUrl = doc.getString("photoUrl");
                    if (drawerProfileImage != null) {
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(this).load(photoUrl).circleCrop()
                                    .placeholder(R.drawable.ic_user).into(drawerProfileImage);
                        } else {
                            String initial = "";
                            if (!first.isEmpty()) initial += first.substring(0, 1).toUpperCase();
                            if (!last.isEmpty()) initial += last.substring(0, 1).toUpperCase();
                            if (initial.isEmpty()) initial = "U";

                            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(120, 120, android.graphics.Bitmap.Config.ARGB_8888);
                            android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                            android.graphics.Paint paint = new android.graphics.Paint();
                            paint.setColor(android.graphics.Color.parseColor("#FFEDD5"));
                            paint.setAntiAlias(true);
                            canvas.drawCircle(60, 60, 60, paint);

                            paint.setColor(android.graphics.Color.parseColor("#EA580C"));
                            paint.setTextSize(44);
                            paint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
                            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                            float yPos = (canvas.getHeight() / 2L) - ((paint.descent() + paint.ascent()) / 2L);
                            canvas.drawText(initial, 60, yPos, paint);
                            drawerProfileImage.setImageBitmap(bitmap);
                        }
                    }
                })
                .addOnFailureListener(e -> drawerProfileName.setText("Faculty"));
    }

    private void clearContent() {
        header.setVisibility(View.VISIBLE);
        contentFrame.removeAllViews();
        refreshNavHighlight();
    }

    private void loadHome() {
        currentNavId = R.id.nav_home;
        clearContent();
        View view = LayoutInflater.from(this).inflate(R.layout.layout_home, contentFrame, true);
        new HomePage(this, view);
    }

    private void loadRooms() {
        currentNavId = R.id.nav_rooms;
        clearContent();
        View view = LayoutInflater.from(this).inflate(R.layout.layout_rooms, contentFrame, true);
        new RoomsPage(this, view);
    }

    private void loadRoomIssues() {
        currentNavId = R.id.nav_issues;
        clearContent();
        View view = LayoutInflater.from(this).inflate(R.layout.layout_room_issues, contentFrame, true);
        currentRoomIssuesPage = new RoomIssuesPage(this, view,
                () -> issuePhotoPickerLauncher.launch("image/*"));
    }

    private void loadReservation() {
        currentNavId = R.id.nav_reservations;
        clearContent();
        View view = LayoutInflater.from(this).inflate(
                R.layout.activity_reservation_page, contentFrame, true);
        new ReservationPage(this, view);
    }

    public void loadWeeklySchedule() {
        currentNavId = R.id.nav_schedule;
        clearContent();
        View view = LayoutInflater.from(this).inflate(
                R.layout.activity_weekly_schedule, contentFrame, true);
        new WeeklySchedulePage(this, view);
    }

    public void openScheduleImport() {
        if (scheduleImportLauncher != null) {
            scheduleImportLauncher.launch(new String[]{
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-excel"
            });
        }
    }

    private void importSchedule(Uri uri) {
        LoadingOverlay.show(this, "Importing schedule...");
        ScheduleImportHelper.importSchedule(this, uri, new ScheduleImportHelper.Callback() {
            @Override public void onSuccess(int importedRows) {
                LoadingOverlay.hide();
                Toast.makeText(DashboardActivity.this,
                        importedRows + " schedule entries imported.", Toast.LENGTH_LONG).show();
                loadWeeklySchedule();
            }

            @Override public void onSuccess(int roomSchedules, int onlineClasses, int skipped) {
                LoadingOverlay.hide();
                StringBuilder message = new StringBuilder();
                message.append(roomSchedules).append(" room schedule")
                        .append(roomSchedules == 1 ? "" : "s");
                message.append(", ").append(onlineClasses).append(" online class")
                        .append(onlineClasses == 1 ? "" : "es");
                if (skipped > 0) message.append(", ").append(skipped).append(" duplicate")
                        .append(skipped == 1 ? "" : "s").append(" skipped");
                message.append(".");
                Toast.makeText(DashboardActivity.this, message.toString(), Toast.LENGTH_LONG).show();
                loadWeeklySchedule();
            }

            @Override public void onError(String message) {
                LoadingOverlay.hide();
                Toast.makeText(DashboardActivity.this,
                        "Schedule import failed: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void listenForUnreadNotifications() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null || notificationBadge == null) return;

        notificationBadgeListener = FirebaseFirestore.getInstance()
                .collection("notifications")
                .whereEqualTo("userId", uid)
                .whereEqualTo("ownerType", "faculty")
                .whereEqualTo("unread", true)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null) return;
                    int count = 0;
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshots.getDocuments()) {
                        Boolean archived = doc.getBoolean("archived");
                        if (archived == null || !archived) count++;
                    }
                    if (count <= 0) {
                        notificationBadge.setVisibility(View.GONE);
                    } else {
                        notificationBadge.setVisibility(View.VISIBLE);
                        notificationBadge.setText(count > 9 ? "9+" : String.valueOf(count));
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationBadgeListener != null) notificationBadgeListener.remove();
    }

    public void loadAnnouncements() {
        currentNavId = R.id.nav_announcement;
        clearContent();
        View view = LayoutInflater.from(this).inflate(
                R.layout.activity_broadcast, contentFrame, true);
        new BroadcastActivity(this, view);
    }

    public void openProfile() {
        clearContent();
        View view = LayoutInflater.from(this).inflate(R.layout.activity_profile, contentFrame, true);
        currentProfilePage = new ProfilePage(this, view, this::loadHome,
                () -> photoPickerLauncher.launch("image/*"));
    }

    public void loadSettings() {
        clearContent();
        View view = LayoutInflater.from(this).inflate(R.layout.activity_settings, contentFrame, true);
        new SettingsPage(this, view);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * Generic Cloudinary upload, shared by the profile photo picker and the
     * "Attach Photo" picker on Report Room Issue. `folder` keeps the two kinds
     * of uploads organized on Cloudinary's side; `onSuccess` runs on the UI
     * thread with the resulting secure_url.
     */
    private void uploadToCloudinary(Uri uri, String folder, java.util.function.Consumer<String> onSuccess) {
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("Could not read selected photo");
                byte[] bytes = readAllBytesCompat(input);
                String boundary = "Boundary-" + System.currentTimeMillis();
                HttpURLConnection connection = (HttpURLConnection) new URL(
                        "https://api.cloudinary.com/v1_1/dqn1s5ujs/image/upload").openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = connection.getOutputStream()) {
                    writePart(out, boundary, "upload_preset", null, "SpaceSCICT".getBytes(StandardCharsets.UTF_8));
                    writePart(out, boundary, "folder", null, folder.getBytes(StandardCharsets.UTF_8));
                    writePart(out, boundary, "file", "photo.jpg", bytes);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream responseStream = code == 200
                        ? connection.getInputStream() : connection.getErrorStream();
                String response = responseStream == null ? "" :
                        new String(readAllBytesCompat(responseStream), StandardCharsets.UTF_8);
                if (code != 200) throw new java.io.IOException("Upload failed");
                String secureUrl = new JSONObject(response).getString("secure_url");
                runOnUiThread(() -> onSuccess.accept(secureUrl));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "Upload error: " + error.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    public void uploadIssuePhotoToCloudinary(Uri uri, java.util.function.BiConsumer<String, String> callback) {
        new Thread(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new java.io.IOException("Could not read selected photo");
                byte[] bytes = readAllBytesCompat(input);
                String boundary = "Boundary-" + System.currentTimeMillis();
                HttpURLConnection connection = (HttpURLConnection) new URL(
                        "https://api.cloudinary.com/v1_1/dqn1s5ujs/image/upload").openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream out = connection.getOutputStream()) {
                    writePart(out, boundary, "upload_preset", null, "SpaceSCICT".getBytes(StandardCharsets.UTF_8));
                    writePart(out, boundary, "folder", null, "spaces/room-issues".getBytes(StandardCharsets.UTF_8));
                    writePart(out, boundary, "file", "photo.jpg", bytes);
                    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                }
                int code = connection.getResponseCode();
                InputStream responseStream = code == 200
                        ? connection.getInputStream() : connection.getErrorStream();
                String response = responseStream == null ? "" :
                        new String(readAllBytesCompat(responseStream), StandardCharsets.UTF_8);
                if (code != 200) throw new java.io.IOException("Upload failed");
                String secureUrl = new JSONObject(response).getString("secure_url");
                runOnUiThread(() -> callback.accept(secureUrl, null));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "Upload error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    callback.accept(null, error.getMessage());
                });
            }
        }).start();
    }

    private void writePart(OutputStream out, String boundary, String name,
                           String filename, byte[] value) throws java.io.IOException {
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) disposition += "; filename=\"" + filename + "\"";
        out.write(("--" + boundary + "\r\n" + disposition + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] readAllBytesCompat(InputStream input) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = input.read(chunk)) != -1) buffer.write(chunk, 0, count);
        return buffer.toByteArray();
    }
}