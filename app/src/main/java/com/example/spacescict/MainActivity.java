package com.example.spacescict;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        mAuth = FirebaseAuth.getInstance();

        Button loginButton = findViewById(R.id.loginButton);
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
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
                return;
            }

            loginButton.setEnabled(false);
            LoadingOverlay.show(this, "Signing in...");

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(result -> {
                        String uid = mAuth.getUid();
                        FirebaseFirestore.getInstance()
                                .collection("users").document(uid).get()
                                .addOnSuccessListener(doc -> {
                                    loginButton.setEnabled(true);

                                    if (!doc.exists()) {
                                        LoadingOverlay.hide();
                                        mAuth.signOut();
                                        Toast.makeText(this, "Account not found", Toast.LENGTH_LONG).show();
                                        return;
                                    }

                                    String role = doc.getString("role");
                                    String status = doc.getString("status");

                                    boolean isFaculty = "Faculty".equalsIgnoreCase(role);
                                    boolean isActive = status == null || "Active".equalsIgnoreCase(status);

                                    if (isFaculty && isActive) {
                                        LoadingOverlay.show(this, "Getting things ready...");
                                        startActivity(new Intent(MainActivity.this, DashboardActivity.class));
                                        finish();
                                    } else if (!isFaculty) {
                                        LoadingOverlay.hide();
                                        mAuth.signOut();
                                        Toast.makeText(this, "This app is for faculty accounts only", Toast.LENGTH_LONG).show();
                                    } else {
                                        LoadingOverlay.hide();
                                        mAuth.signOut();
                                        Toast.makeText(this, "Your account is inactive. Contact admin.", Toast.LENGTH_LONG).show();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    loginButton.setEnabled(true);
                                    LoadingOverlay.hide();
                                    mAuth.signOut();
                                    Toast.makeText(this, "Could not verify account: " + e.getMessage(), Toast.LENGTH_LONG).show();
                                });
                    })
                    .addOnFailureListener(e -> {
                        loginButton.setEnabled(true);
                        LoadingOverlay.hide();
                        Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    void showForgotPasswordDialog(String prefillEmail) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);

        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(dialogView);
        dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        EditText resetEmailInput = dialogView.findViewById(R.id.resetEmailInput);
        if (!prefillEmail.isEmpty()) resetEmailInput.setText(prefillEmail);

        dialogView.findViewById(R.id.resetCancelBtn).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.resetSendBtn).setOnClickListener(v -> {
            String email = resetEmailInput.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show();
                return;
            }

            LoadingOverlay.show(this, "Sending reset link...");

            mAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused -> {
                        LoadingOverlay.hide();
                        dialog.dismiss();
                        Toast.makeText(this, "Reset email sent. Check your inbox.", Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        LoadingOverlay.hide();
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();

        // Fixed sizing: force the dialog to 88% of screen width, not WRAP_CONTENT
        // This is what was rendering as a thin strip before.
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int dialogWidth = (int) (screenWidth * 0.88);
        dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }
    }
