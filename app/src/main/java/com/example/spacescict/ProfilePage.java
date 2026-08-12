package com.example.spacescict;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ProfilePage {

    Context context;
    EditText name, email;
    ImageView editBtn, backBtn, profilePhoto, cameraBtn;
    Button resetPasswordBtn;

    boolean isEditing = false;
    FirebaseFirestore db;
    String uid;
    Runnable onBack;
    PhotoPickerHandler photoPickerHandler;

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
        resetPasswordBtn = view.findViewById(R.id.resetPasswordBtn);

        db = FirebaseFirestore.getInstance();
        uid = FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        loadProfile();
        setFieldsEnabled(false);
        cameraBtn.setVisibility(View.GONE);

        editBtn.setOnClickListener(v -> {
            if (!isEditing) Toast.makeText(context, "Edit mode enabled", Toast.LENGTH_SHORT).show();
            toggleEdit();
        });

        if (backBtn != null) backBtn.setOnClickListener(v -> handleBack());

        cameraBtn.setOnClickListener(v -> {
            if (photoPickerHandler != null) photoPickerHandler.launchPicker();
        });

        resetPasswordBtn.setOnClickListener(v -> sendPasswordReset());
    }

    void loadProfile() {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String first = doc.getString("firstName");
                        String last = doc.getString("lastName");
                        name.setText(((first != null ? first : "") + " " + (last != null ? last : "")).trim());
                        email.setText(doc.getString("email"));

                        String photoUrl = doc.getString("photoUrl");
                        if (photoUrl != null && !photoUrl.isEmpty()) {
                            Glide.with(context)
                                    .load(photoUrl)
                                    .circleCrop()
                                    .placeholder(R.drawable.ic_user)
                                    .into(profilePhoto);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Failed to load profile: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    void sendPasswordReset() {
        String userEmail = email.getText().toString().trim();
        if (userEmail.isEmpty()) return;

        FirebaseAuth.getInstance().sendPasswordResetEmail(userEmail)
                .addOnSuccessListener(unused ->
                        Toast.makeText(context, "Reset email sent. Check your inbox.", Toast.LENGTH_LONG).show())
                .addOnFailureListener(e ->
                        Toast.makeText(context, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    void toggleEdit() {
        if (!isEditing) {
            isEditing = true;
            setFieldsEnabled(true);
            cameraBtn.setVisibility(View.VISIBLE);
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