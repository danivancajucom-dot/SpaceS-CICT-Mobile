package com.example.spacescict;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        View loginButton = findViewById(R.id.loginButton);
        EditText passwordInput = findViewById(R.id.passwordInput);
        EditText emailInput = findViewById(R.id.emailInput);
        ImageView togglePassword = findViewById(R.id.togglePassword);
        TextView forgotPasswordText = findViewById(R.id.forgotPasswordText);

        togglePassword.setOnClickListener(v -> {
            if (passwordInput.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePassword.setImageResource(R.drawable.ic_eye_off);
            } else {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePassword.setImageResource(R.drawable.ic_eye);
            }
            passwordInput.setSelection(passwordInput.length());
        });

        if (forgotPasswordText != null) {
            forgotPasswordText.setOnClickListener(v -> showForgotPasswordDialog(emailInput.getText().toString().trim()));
        }

        loginButton.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(MainActivity.this, "Fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            LoadingOverlay.show(this, "Signing in...");
            loginButton.setEnabled(false);

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            checkRoleAndProceed();
                        } else {
                            LoadingOverlay.hide();
                            loginButton.setEnabled(true);
                            Toast.makeText(MainActivity.this, "Authentication failed.", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    private void checkRoleAndProceed() {
        String uid = mAuth.getUid();
        if (uid == null) {
            LoadingOverlay.hide();
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    LoadingOverlay.hide();
                    if (!doc.exists()) {
                        Toast.makeText(this, "Profile not found.", Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                        return;
                    }

                    String role = doc.getString("role");
                    if (role != null && role.equalsIgnoreCase("faculty")) {
                        startActivity(new Intent(MainActivity.this, DashboardActivity.class));
                        finish();
                    } else {
                        Toast.makeText(this, "Only faculty can access the mobile app.", Toast.LENGTH_LONG).show();
                        mAuth.signOut();
                    }
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide();
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void showForgotPasswordDialog(String initialEmail) {
        View dv = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dv).create();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        EditText emailIn = dv.findViewById(R.id.forgotEmailInput);
        if (initialEmail != null) emailIn.setText(initialEmail);

        dv.findViewById(R.id.forgotCancelBtn).setOnClickListener(v -> dialog.dismiss());
        dv.findViewById(R.id.forgotSubmitBtn).setOnClickListener(v -> {
            String email = emailIn.getText().toString().trim();
            if (email.isEmpty()) return;

            FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                    .addOnSuccessListener(u -> {
                        Toast.makeText(this, "Reset email sent.", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e -> Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        dialog.show();
    }
}
