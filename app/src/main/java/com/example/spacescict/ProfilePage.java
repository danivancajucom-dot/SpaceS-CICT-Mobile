package com.example.spacescict;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.List;

public class ProfilePage {

    Context context;
    EditText name, email;
    ImageView editBtn, backBtn, profilePhoto;
    View cameraBtn, uploadPictureBtn;

    boolean isEditing = false;
    FirebaseFirestore db;
    String uid;
    Runnable onBack;
    PhotoPickerHandler photoPickerHandler;


    View tabDetails, tabActivityLog, detailsTabContent, activityTabContent;
    RecyclerView activityRecycler;
    View noActivityContainer;
    List<ActivityLogModel> activityList = new ArrayList<>();

    public interface PhotoPickerHandler {
        void launchPicker();
    }

    public ProfilePage(Context context, View view, Runnable onBack, PhotoPickerHandler photoPickerHandler) {
        this.context = context;
        this.onBack = onBack;
        this.photoPickerHandler = photoPickerHandler;

        name = view.findViewById(R.id.nameInput);
        email = view.findViewById(R.id.emailInput);
        editBtn = view.findViewById(R.id.editBtn);
        backBtn = view.findViewById(R.id.backBtn);
        profilePhoto = view.findViewById(R.id.profilePhoto);
        cameraBtn = view.findViewById(R.id.cameraBtn);
        uploadPictureBtn = view.findViewById(R.id.uploadPictureBtn);

        tabDetails = view.findViewById(R.id.tabDetails);
        tabActivityLog = view.findViewById(R.id.tabActivityLog);
        detailsTabContent = view.findViewById(R.id.detailsTabContent);
        activityTabContent = view.findViewById(R.id.activityTabContent);
        activityRecycler = view.findViewById(R.id.activityRecycler);
        noActivityContainer = view.findViewById(R.id.noActivityContainer);

        if (tabDetails != null && tabActivityLog != null) {
            tabDetails.setOnClickListener(v -> switchTab(true));
            tabActivityLog.setOnClickListener(v -> switchTab(false));
        }

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        loadProfile();
        setFieldsEnabled(false);
        cameraBtn.setVisibility(View.GONE);
        if (uploadPictureBtn != null) uploadPictureBtn.setVisibility(View.GONE);

        editBtn.setOnClickListener(v -> {
            if (!isEditing) Toast.makeText(context, "Edit mode enabled", Toast.LENGTH_SHORT).show();
            toggleEdit();
        });

        if (backBtn != null) backBtn.setOnClickListener(v -> handleBack());

        cameraBtn.setOnClickListener(v -> {
            if (photoPickerHandler != null) photoPickerHandler.launchPicker();
        });

        if (uploadPictureBtn != null) {
            uploadPictureBtn.setOnClickListener(v -> {
                if (photoPickerHandler != null) photoPickerHandler.launchPicker();
            });
        }
    }

    void switchTab(boolean showDetails) {
        if (detailsTabContent != null) detailsTabContent.setVisibility(showDetails ? View.VISIBLE : View.GONE);
        if (activityTabContent != null) activityTabContent.setVisibility(showDetails ? View.GONE : View.VISIBLE);

        androidx.cardview.widget.CardView detailsCard = (androidx.cardview.widget.CardView) tabDetails;
        androidx.cardview.widget.CardView activityCard = (androidx.cardview.widget.CardView) tabActivityLog;

        detailsCard.setCardBackgroundColor(showDetails
                ? android.graphics.Color.parseColor("#F97316") : android.graphics.Color.parseColor("#FFFFFF"));
        activityCard.setCardBackgroundColor(!showDetails
                ? android.graphics.Color.parseColor("#F97316") : android.graphics.Color.parseColor("#FFFFFF"));

        TextView detailsText = (TextView) detailsCard.getChildAt(0);
        TextView activityText = (TextView) activityCard.getChildAt(0);
        detailsText.setTextColor(showDetails ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#44403C"));
        activityText.setTextColor(!showDetails ? android.graphics.Color.WHITE : android.graphics.Color.parseColor("#44403C"));

        if (!showDetails && activityRecycler != null && activityRecycler.getAdapter() == null) {
            loadActivityLog();
        }
    }
    void loadActivityLog() {
        if (activityRecycler == null || uid == null) return;

        activityRecycler.setVisibility(View.GONE);
        noActivityContainer.setVisibility(View.GONE);

        db.collection("activityLogs")
                .whereEqualTo("userId", uid)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snap -> {
                    activityList.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        activityList.add(new ActivityLogModel(
                                doc.getId(),
                                doc.getString("action"),
                                doc.getString("actionType"),
                                doc.getString("target"),
                                doc.getString("status"),
                                doc.getTimestamp("timestamp")
                        ));
                    }
                    activityRecycler.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(context));
                    activityRecycler.setAdapter(new ActivityLogAdapter(activityList));

                    boolean empty = activityList.isEmpty();
                    activityRecycler.setVisibility(empty ? View.GONE : View.VISIBLE);
                    noActivityContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
                })
                .addOnFailureListener(e -> {
                    noActivityContainer.setVisibility(View.VISIBLE);
                });
    }
    void loadProfile() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String first = doc.getString("firstName");
                        String last = doc.getString("lastName");
                        if (first == null) first = "";
                        if (last == null) last = "";
                        name.setText((first + " " + last).trim());
                        email.setText(doc.getString("email"));

                        String photoUrl = doc.getString("photoUrl");
                        if (profilePhoto != null) {
                            if (photoUrl != null && !photoUrl.isEmpty()) {
                                Glide.with(context)
                                        .load(photoUrl)
                                        .circleCrop()
                                        .placeholder(R.drawable.ic_user)
                                        .into(profilePhoto);
                            } else {
                                String initial = "";
                                if (!first.isEmpty()) initial += first.substring(0, 1).toUpperCase();
                                if (!last.isEmpty()) initial += last.substring(0, 1).toUpperCase();
                                if (initial.isEmpty()) initial = "U";

                                android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(240, 240, android.graphics.Bitmap.Config.ARGB_8888);
                                android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                                android.graphics.Paint paint = new android.graphics.Paint();
                                paint.setColor(android.graphics.Color.parseColor("#FFEDD5"));
                                paint.setAntiAlias(true);
                                canvas.drawCircle(120, 120, 120, paint);

                                paint.setColor(android.graphics.Color.parseColor("#EA580C"));
                                paint.setTextSize(88);
                                paint.setTypeface(android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD));
                                paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                                float yPos = (canvas.getHeight() / 2L) - ((paint.descent() + paint.ascent()) / 2L);
                                canvas.drawText(initial, 120, yPos, paint);
                                profilePhoto.setImageBitmap(bitmap);
                            }
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }


    void toggleEdit() {
        if (!isEditing) {
            isEditing = true;
            setFieldsEnabled(true);
            cameraBtn.setVisibility(View.VISIBLE);
            if (uploadPictureBtn != null) uploadPictureBtn.setVisibility(View.VISIBLE);
            editBtn.setImageResource(R.drawable.ic_check);
        } else {
            ConfirmDialog.show(context, "Save Changes?", "Do you want to save your changes?",
                    "Confirm", "Cancel", this::saveProfile);
        }
    }

    void saveProfile() {
        String fullName = name.getText().toString().trim();
        String firstName = fullName;
        String lastName = "";
        int spaceIdx = fullName.indexOf(' ');
        if (spaceIdx > 0) {
            firstName = fullName.substring(0, spaceIdx);
            lastName = fullName.substring(spaceIdx + 1).trim();
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("firstName", firstName);
        updates.put("lastName", lastName);

        db.collection("users").document(uid).set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    isEditing = false;
                    setFieldsEnabled(false);
                    cameraBtn.setVisibility(View.GONE);
                    if (uploadPictureBtn != null) uploadPictureBtn.setVisibility(View.GONE);
                    editBtn.setImageResource(R.drawable.ic_edit);

                    ActivityLogger.log("Updated profile", "edit", "Faculty Profile", "Success", new HashMap<>(), null);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    public void onPhotoUploaded(String photoUrl) {
        db.collection("users").document(uid).update("photoUrl", photoUrl)
                .addOnSuccessListener(unused -> {
                    Glide.with(context).load(photoUrl).circleCrop().into(profilePhoto);
                    Toast.makeText(context, "Photo updated", Toast.LENGTH_SHORT).show();
                });
    }

    void handleBack() {
        if (isEditing) {
            new AlertDialog.Builder(context)
                    .setTitle("Discard Changes?")
                    .setMessage("You have unsaved changes.")
                    .setPositiveButton("Discard", (d, w) -> { if (onBack != null) onBack.run(); })
                    .setNegativeButton("Stay", null)
                    .show();
        } else {
            if (onBack != null) onBack.run();
        }
    }

    void setFieldsEnabled(boolean enabled) {
        name.setEnabled(enabled);
        name.setFocusableInTouchMode(enabled);
        name.setClickable(enabled);
    }
}