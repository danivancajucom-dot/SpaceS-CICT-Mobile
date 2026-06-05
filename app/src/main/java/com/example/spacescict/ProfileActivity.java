package com.example.spacescict;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class ProfileActivity extends AppCompatActivity {

    EditText name, email, password;
    ImageView editBtn, backBtn, eyeBtn;

    boolean isEditing = false;
    boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Bind views
        name = findViewById(R.id.nameInput);
        email = findViewById(R.id.emailInput);
        password = findViewById(R.id.passwordInput);

        editBtn = findViewById(R.id.editBtn);
        backBtn = findViewById(R.id.backBtn);
        eyeBtn = findViewById(R.id.eyeBtn);

        // Initially disabled
        setFieldsEnabled(false);
        eyeBtn.setEnabled(false);
        eyeBtn.setAlpha(0.4f);

        // Click listeners
        editBtn.setOnClickListener(v -> {
            if (!isEditing) {
                Toast.makeText(this, "Edit mode enabled", Toast.LENGTH_SHORT).show();
            }
            toggleEdit();
        });
        backBtn.setOnClickListener(v -> handleBack());
        eyeBtn.setOnClickListener(v -> togglePassword());
    }

    // ================= EDIT TOGGLE =================
    void toggleEdit() {

        if (!isEditing) {
            isEditing = true;

            setFieldsEnabled(true);

            eyeBtn.setEnabled(true);
            eyeBtn.setAlpha(1f);

            editBtn.setImageResource(R.drawable.ic_check);
        } else {
            // SAVE CONFIRM
            ConfirmDialog.show(
                    this,
                    "Save Changes?",
                    "Do you want to save your changes?",
                    "Confirm",
                    "Cancel",
                    () -> {

                        isEditing = false;

                        setFieldsEnabled(false);

                        eyeBtn.setEnabled(false);
                        eyeBtn.setAlpha(0.4f);

                        editBtn.setImageResource(R.drawable.ic_edit);
                    }
            );
        }
    }
    // ================= BACK BUTTON =================
    void handleBack() {

        if (isEditing) {
            new AlertDialog.Builder(this)
                    .setTitle("Discard Changes?")
                    .setMessage("You have unsaved changes.")
                    .setPositiveButton("Discard", (d, w) -> finish())
                    .setNegativeButton("Stay", null)
                    .show();
        } else {
            finish();
        }
    }

    // ================= ENABLE/DISABLE FIELDS =================
    void setFieldsEnabled(boolean enabled) {

        name.setEnabled(enabled);
        email.setEnabled(enabled);
        password.setEnabled(enabled);

        name.setFocusableInTouchMode(enabled);
        email.setFocusableInTouchMode(enabled);
        password.setFocusableInTouchMode(enabled);

        name.setClickable(enabled);
        email.setClickable(enabled);
        password.setClickable(enabled);
    }

    // ================= PASSWORD TOGGLE =================
    void togglePassword() {
        if (!isPasswordVisible) {
            password.setInputType(
                    InputType.TYPE_CLASS_TEXT |
                            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            );
            eyeBtn.setImageResource(R.drawable.ic_eye_off);
            isPasswordVisible = true;
        } else {
            password.setInputType(
                    InputType.TYPE_CLASS_TEXT |
                            InputType.TYPE_TEXT_VARIATION_PASSWORD
            );
            eyeBtn.setImageResource(R.drawable.ic_eye);
            isPasswordVisible = false;
        }
        password.setSelection(password.getText().length());
    }
}