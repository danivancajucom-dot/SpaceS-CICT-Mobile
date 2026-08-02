package com.example.spacescict;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    EditText name, email;

    ImageView editBtn, backBtn;
    Button resetPasswordBtn;

    boolean isEditing = false;

    FirebaseFirestore db;
    FirebaseAuth auth;
    String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        name = findViewById(R.id.nameInput);
        email = findViewById(R.id.emailInput);

        editBtn = findViewById(R.id.editBtn);
        backBtn = findViewById(R.id.backBtn);

        resetPasswordBtn = findViewById(R.id.resetPasswordBtn);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            uid = auth.getCurrentUser().getUid();
        }

        loadProfile();

        setFieldsEnabled(false);

        editBtn.setOnClickListener(v -> toggleEdit());

        backBtn.setOnClickListener(v -> handleBack());

        resetPasswordBtn.setOnClickListener(v -> sendResetPasswordEmail());
    }

    // ================= LOAD PROFILE =================

    private void loadProfile() {

        if (uid == null) return;

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (doc.exists()) {

                        String userName = doc.getString("name");
                        String userEmail = doc.getString("email");

                        name.setText(userName != null ? userName : "");
                        email.setText(userEmail != null ? userEmail : "");
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Failed to load profile: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    // ================= EDIT MODE =================

    private void toggleEdit() {

        if (!isEditing) {

            isEditing = true;

            setFieldsEnabled(true);

            editBtn.setImageResource(R.drawable.ic_check);

            Toast.makeText(this,
                    "Edit mode enabled",
                    Toast.LENGTH_SHORT).show();

        } else {

            ConfirmDialog.show(
                    this,
                    "Save Changes?",
                    "Do you want to save your changes?",
                    "Confirm",
                    "Cancel",
                    this::saveProfile
            );
        }
    }

    // ================= SAVE =================

    private void saveProfile() {

        if (uid == null) return;

        String newName = name.getText().toString().trim();
        String newEmail = email.getText().toString().trim();

        Map<String, Object> updates = new HashMap<>();
        updates.put("name", newName);
        updates.put("email", newEmail);

        db.collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    isEditing = false;

                    setFieldsEnabled(false);

                    editBtn.setImageResource(R.drawable.ic_edit);

                    Toast.makeText(
                            this,
                            "Profile updated successfully",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Save failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    // ================= RESET PASSWORD =================

    private void sendResetPasswordEmail() {

        String userEmail = email.getText().toString().trim();

        if (userEmail.isEmpty()) {

            Toast.makeText(
                    this,
                    "Email not found",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        auth.sendPasswordResetEmail(userEmail)
                .addOnSuccessListener(unused ->
                        Toast.makeText(
                                this,
                                "Password reset email sent",
                                Toast.LENGTH_LONG
                        ).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    // ================= BACK =================

    private void handleBack() {

        if (isEditing) {

            new AlertDialog.Builder(this)
                    .setTitle("Discard Changes?")
                    .setMessage("You have unsaved changes.")
                    .setPositiveButton("Discard",
                            (dialog, which) -> finish())
                    .setNegativeButton("Stay", null)
                    .show();

        } else {
            finish();
        }
    }

    // ================= ENABLE/DISABLE =================

    private void setFieldsEnabled(boolean enabled) {

        name.setEnabled(enabled);
        email.setEnabled(enabled);

        name.setFocusableInTouchMode(enabled);
        email.setFocusableInTouchMode(enabled);

        name.setClickable(enabled);
        email.setClickable(enabled);
    }
}