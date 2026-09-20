package com.example.spacescict;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SettingsPage {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^\\S+@\\S+\\.\\S+$");

    private final Context context;
    private final View view;

    private TextView emailText;
    private LinearLayout changeEmailBtn, privacyBtn, termsBtn, helpBtn, deleteAccountBtn;
    private SwitchMaterial pushSwitch;
    private EditText currentPasswordInput;
    private TextView changePasswordBtn;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseUser currentUser;
    private String uid;
    private boolean busy = false;

    public SettingsPage(Context context, View view) {
        this.context = context;
        this.view = view;

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        currentUser = auth.getCurrentUser();

        initializeViews();

        if (currentUser != null) {
            uid = currentUser.getUid();
            loadUserData();
        }

        setupListeners();
    }

    private void initializeViews() {
        emailText = view.findViewById(R.id.emailText);
        changeEmailBtn = view.findViewById(R.id.changeEmailBtn);
        currentPasswordInput = view.findViewById(R.id.currentPasswordInput);
        changePasswordBtn = view.findViewById(R.id.changePasswordBtn);
        privacyBtn = view.findViewById(R.id.privacyBtn);
        termsBtn = view.findViewById(R.id.termsBtn);
        helpBtn = view.findViewById(R.id.helpBtn);
        deleteAccountBtn = view.findViewById(R.id.deleteAccountBtn);
        pushSwitch = view.findViewById(R.id.pushSwitch);
    }

    private void loadUserData() {
        if (emailText != null) emailText.setText(currentUser.getEmail());

        firestore.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    Boolean pushEnabled = documentSnapshot.getBoolean("pushNotifications");
                    if (pushEnabled != null && pushSwitch != null) pushSwitch.setChecked(pushEnabled);
                });
    }

    private void setupListeners() {
        if (changeEmailBtn != null) changeEmailBtn.setOnClickListener(v -> showChangeEmailDialog());
        if (changePasswordBtn != null) changePasswordBtn.setOnClickListener(v -> beginPasswordChange());

        if (pushSwitch != null) {
            pushSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                    saveNotificationPreference(isChecked));
        }

        if (privacyBtn != null) privacyBtn.setOnClickListener(v -> showPrivacyPolicyDialog());
        if (termsBtn != null) termsBtn.setOnClickListener(v -> showTermsDialog());
        if (helpBtn != null) helpBtn.setOnClickListener(v -> showHelpDialog());
        if (deleteAccountBtn != null) deleteAccountBtn.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void saveNotificationPreference(boolean enabled) {
        if (uid == null) return;
        firestore.collection("users").document(uid).update("pushNotifications", enabled);
    }

    // ════════════════════════════════════════════════════════════════════
    // CHANGE PASSWORD
    // ════════════════════════════════════════════════════════════════════

    private void beginPasswordChange() {
        if (busy) return;
        if (currentUser == null || currentPasswordInput == null) return;

        String currentPassword = currentPasswordInput.getText().toString().trim();
        if (currentPassword.isEmpty()) {
            Toast.makeText(context, "Enter your current password first", Toast.LENGTH_SHORT).show();
            return;
        }

        String email = currentUser.getEmail();
        if (email == null) return;

        busy = true;
        LoadingOverlay.show((android.app.Activity) context, "Verifying identity...");

        AuthCredential credential = EmailAuthProvider.getCredential(email, currentPassword);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> {
                    LoadingOverlay.show((android.app.Activity) context, "Sending verification code...");
                    VerificationHelper.createAndSendCode(email, "password-change", new VerificationHelper.ResultCallback() {
                        @Override
                        public void onSuccess() {
                            busy = false;
                            LoadingOverlay.hide();
                            Toast.makeText(context, "A 6-digit code was sent to " + email + ".", Toast.LENGTH_LONG).show();
                            showVerificationCodeDialog(currentPassword, email);
                        }

                        @Override
                        public void onError(String message) {
                            busy = false;
                            LoadingOverlay.hide();
                            new AlertDialog.Builder(context)
                                    .setTitle("Verification Failed")
                                    .setMessage(message)
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide();
                    busy = false;
                    Toast.makeText(context, describeAuthError(e, "Your current password is incorrect."),
                            Toast.LENGTH_LONG).show();
                });
    }

    private void showVerificationCodeDialog(String currentPassword, String email) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(22));

        TextView title = new TextView(context);
        title.setText("Enter Verification Code");
        title.setTextColor(Color.parseColor("#1C1917"));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(18);
        root.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("We sent a 6-digit code to " + email + ". It expires in "
                + VerificationHelper.CODE_TTL_MIN + " minutes.");
        subtitle.setTextColor(Color.parseColor("#78716C"));
        subtitle.setTextSize(12.5f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(6);
        subtitleParams.bottomMargin = dp(16);
        subtitle.setLayoutParams(subtitleParams);
        root.addView(subtitle);

        EditText codeInput = new EditText(context);
        codeInput.setHint("6-digit code");
        codeInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        codeInput.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(VerificationHelper.CODE_LENGTH)});
        codeInput.setGravity(Gravity.CENTER);
        codeInput.setTextSize(20);
        codeInput.setBackgroundResource(R.drawable.input_field_bg);
        codeInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        codeInput.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        root.addView(codeInput);

        TextView resendText = new TextView(context);
        resendText.setTextColor(Color.parseColor("#E86A12"));
        resendText.setTextSize(12.5f);
        resendText.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams resendParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        resendParams.topMargin = dp(14);
        resendText.setLayoutParams(resendParams);
        root.addView(resendText);

        CardView verifyCard = new CardView(context);
        verifyCard.setRadius(dp(14));
        verifyCard.setCardElevation(0);
        verifyCard.setCardBackgroundColor(Color.parseColor("#F97316"));
        LinearLayout.LayoutParams verifyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        verifyParams.topMargin = dp(18);
        verifyCard.setLayoutParams(verifyParams);
        TextView verifyText = new TextView(context);
        verifyText.setText("Verify Code");
        verifyText.setTextColor(Color.WHITE);
        verifyText.setTypeface(null, Typeface.BOLD);
        verifyText.setGravity(Gravity.CENTER);
        verifyText.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        verifyCard.addView(verifyText);
        root.addView(verifyCard);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        final android.os.CountDownTimer[] timerHolder = new android.os.CountDownTimer[1];
        Runnable startCooldown = () -> {
            resendText.setEnabled(false);
            timerHolder[0] = new android.os.CountDownTimer(30_000, 1000) {
                @Override public void onTick(long millisUntilFinished) {
                    resendText.setText("Resend code in " + (millisUntilFinished / 1000 + 1) + "s");
                }
                @Override public void onFinish() {
                    resendText.setText("Didn't get the code? Resend");
                    resendText.setEnabled(true);
                }
            }.start();
        };
        startCooldown.run();

        resendText.setOnClickListener(v -> {
            if (!resendText.isEnabled()) return;
            resendText.setEnabled(false);
            VerificationHelper.createAndSendCode(email, "password-change", new VerificationHelper.ResultCallback() {
                @Override public void onSuccess() {
                    Toast.makeText(context, "A new code was sent.", Toast.LENGTH_SHORT).show();
                    startCooldown.run();
                }
                @Override public void onError(String message) {
                    resendText.setEnabled(true);
                    Toast.makeText(context, "Resend failed: " + message, Toast.LENGTH_LONG).show();
                }
            });
        });

        verifyCard.setOnClickListener(v -> {
            String entered = codeInput.getText().toString().trim();
            if (entered.length() != VerificationHelper.CODE_LENGTH) {
                Toast.makeText(context, "Enter the " + VerificationHelper.CODE_LENGTH + "-digit code.", Toast.LENGTH_SHORT).show();
                return;
            }
            VerificationHelper.verifyCode(email, "password-change", entered,
                    new VerificationHelper.ResultCallback() {
                        @Override public void onSuccess() {
                            if (timerHolder[0] != null) timerHolder[0].cancel();
                            dialog.dismiss();
                            showNewPasswordDialog(currentPassword);
                        }
                        @Override public void onError(String message) {
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });

        dialog.setOnDismissListener(d -> { if (timerHolder[0] != null) timerHolder[0].cancel(); });
        dialog.show();
        setDialogWidth(dialog);
    }

    private void showNewPasswordDialog(String currentPassword) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(22));

        TextView title = new TextView(context);
        title.setText("Set a New Password");
        title.setTextColor(Color.parseColor("#1C1917"));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(18);
        root.addView(title);

        EditText newPasswordInput = new EditText(context);
        newPasswordInput.setHint("New password");
        newPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPasswordInput.setBackgroundResource(R.drawable.input_field_bg);
        newPasswordInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams npParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        npParams.topMargin = dp(16);
        newPasswordInput.setLayoutParams(npParams);
        root.addView(newPasswordInput);

        EditText confirmPasswordInput = new EditText(context);
        confirmPasswordInput.setHint("Confirm new password");
        confirmPasswordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirmPasswordInput.setBackgroundResource(R.drawable.input_field_bg);
        confirmPasswordInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams cpParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        cpParams.topMargin = dp(10);
        confirmPasswordInput.setLayoutParams(cpParams);
        root.addView(confirmPasswordInput);

        LinearLayout checklist = new LinearLayout(context);
        checklist.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams checklistParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        checklistParams.topMargin = dp(14);
        checklist.setLayoutParams(checklistParams);
        root.addView(checklist);

        String[] ruleKeys = {"length", "uppercase", "lowercase", "number", "special"};
        Map<String, TextView> ruleViews = new HashMap<>();
        for (String key : ruleKeys) {
            TextView row = new TextView(context);
            row.setText("\u25CB  " + PasswordRules.label(key));
            row.setTextSize(12);
            row.setTextColor(Color.parseColor("#78716C"));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.topMargin = dp(4);
            row.setLayoutParams(rowParams);
            checklist.addView(row);
            ruleViews.put(key, row);
        }

        newPasswordInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                Map<String, Boolean> checks = PasswordRules.check(s.toString());
                for (Map.Entry<String, Boolean> entry : checks.entrySet()) {
                    TextView row = ruleViews.get(entry.getKey());
                    if (row == null) continue;
                    boolean pass = Boolean.TRUE.equals(entry.getValue());
                    row.setText((pass ? "\u2713  " : "\u25CB  ") + PasswordRules.label(entry.getKey()));
                    row.setTextColor(Color.parseColor(pass ? "#16A34A" : "#78716C"));
                }
            }
        });

        CardView updateCard = new CardView(context);
        updateCard.setRadius(dp(14));
        updateCard.setCardElevation(0);
        updateCard.setCardBackgroundColor(Color.parseColor("#F97316"));
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        updateParams.topMargin = dp(20);
        updateCard.setLayoutParams(updateParams);
        TextView updateText = new TextView(context);
        updateText.setText("Update Password");
        updateText.setTextColor(Color.WHITE);
        updateText.setTypeface(null, Typeface.BOLD);
        updateText.setGravity(Gravity.CENTER);
        updateText.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateCard.addView(updateText);
        root.addView(updateCard);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        updateCard.setOnClickListener(v -> {
            String newPassword = newPasswordInput.getText().toString();
            String confirmPassword = confirmPasswordInput.getText().toString();
            if (!PasswordRules.isStrong(newPassword)) {
                Toast.makeText(context, "New password does not meet requirements.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                Toast.makeText(context, "Passwords must match.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (newPassword.equals(currentPassword)) {
                Toast.makeText(context, "New password must differ from current one.", Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            finishPasswordChange(currentPassword, newPassword);
        });

        dialog.show();
        setDialogWidth(dialog);
    }

    private void finishPasswordChange(String currentPassword, String newPassword) {
        if (busy) return;
        busy = true;
        LoadingOverlay.show((android.app.Activity) context, "Updating password...");
        String email = currentUser.getEmail();
        if (email == null) { busy = false; LoadingOverlay.hide(); return; }

        AuthCredential credential = EmailAuthProvider.getCredential(email, currentPassword);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> currentUser.updatePassword(newPassword)
                        .addOnSuccessListener(unused2 -> {
                            LoadingOverlay.hide();
                            busy = false;
                            if (currentPasswordInput != null) currentPasswordInput.setText("");
                            Toast.makeText(context, "Password updated successfully.", Toast.LENGTH_LONG).show();
                        })
                        .addOnFailureListener(e -> {
                            LoadingOverlay.hide();
                            busy = false;
                            Toast.makeText(context, describeAuthError(e, "Could not update password."), Toast.LENGTH_LONG).show();
                        }))
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide();
                    busy = false;
                    Toast.makeText(context, describeAuthError(e, "Your current password is incorrect."), Toast.LENGTH_LONG).show();
                });
    }

    // ════════════════════════════════════════════════════════════════════
    // CHANGE EMAIL
    // ════════════════════════════════════════════════════════════════════

    private void showChangeEmailDialog() {
        if (currentUser == null) return;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(22), dp(24), dp(22));

        TextView title = new TextView(context);
        title.setText("Change Email");
        title.setTextColor(Color.parseColor("#1C1917"));
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(18);
        root.addView(title);

        TextView subtitle = new TextView(context);
        subtitle.setText("Confirm your password and enter your new email address.");
        subtitle.setTextColor(Color.parseColor("#78716C"));
        subtitle.setTextSize(12.5f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(4);
        subtitle.setLayoutParams(subtitleParams);
        root.addView(subtitle);

        EditText passwordInput = new EditText(context);
        passwordInput.setHint("Current password");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setBackgroundResource(R.drawable.input_field_bg);
        passwordInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams pwParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        pwParams.topMargin = dp(16);
        passwordInput.setLayoutParams(pwParams);
        root.addView(passwordInput);

        EditText emailInput = new EditText(context);
        emailInput.setHint("New email address");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        emailInput.setBackgroundResource(R.drawable.input_field_bg);
        emailInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams emParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        emParams.topMargin = dp(10);
        emailInput.setLayoutParams(emParams);
        root.addView(emailInput);

        CardView updateCard = new CardView(context);
        updateCard.setRadius(dp(14));
        updateCard.setCardElevation(0);
        updateCard.setCardBackgroundColor(Color.parseColor("#F97316"));
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50));
        updateParams.topMargin = dp(20);
        updateCard.setLayoutParams(updateParams);
        TextView updateText = new TextView(context);
        updateText.setText("Update Email");
        updateText.setTextColor(Color.WHITE);
        updateText.setTypeface(null, Typeface.BOLD);
        updateText.setGravity(Gravity.CENTER);
        updateText.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        updateCard.addView(updateText);
        root.addView(updateCard);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(20));
        root.setBackground(bg);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(root);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        updateCard.setOnClickListener(v -> {
            String currentPassword = passwordInput.getText().toString();
            String newEmail = emailInput.getText().toString().trim();
            String existingEmail = currentUser.getEmail() == null ? "" : currentUser.getEmail();
            if (currentPassword.isEmpty()) { Toast.makeText(context, "Enter your password.", Toast.LENGTH_SHORT).show(); return; }
            if (newEmail.isEmpty() || !EMAIL_PATTERN.matcher(newEmail).matches()) { Toast.makeText(context, "Enter valid email.", Toast.LENGTH_SHORT).show(); return; }
            if (newEmail.equalsIgnoreCase(existingEmail)) { Toast.makeText(context, "Same as current email.", Toast.LENGTH_SHORT).show(); return; }
            dialog.dismiss();
            submitEmailChange(currentPassword, newEmail);
        });

        dialog.show();
        setDialogWidth(dialog);
    }

    private void submitEmailChange(String currentPassword, String newEmail) {
        if (busy) return;
        busy = true;
        LoadingOverlay.show((android.app.Activity) context, "Updating email...");
        String existingEmail = currentUser.getEmail();
        if (existingEmail == null) { busy = false; LoadingOverlay.hide(); return; }

        AuthCredential credential = EmailAuthProvider.getCredential(existingEmail, currentPassword);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> currentUser.updateEmail(newEmail)
                        .addOnSuccessListener(unused2 -> firestore.collection("users").document(uid).update("email", newEmail)
                                .addOnSuccessListener(unused3 -> {
                                    LoadingOverlay.hide(); busy = false;
                                    if (emailText != null) emailText.setText(newEmail);
                                    Toast.makeText(context, "Email changed successfully.", Toast.LENGTH_LONG).show();
                                })
                                .addOnFailureListener(e -> {
                                    LoadingOverlay.hide(); busy = false;
                                    if (emailText != null) emailText.setText(newEmail);
                                    Toast.makeText(context, "Email updated in Auth, but Firestore refresh failed.", Toast.LENGTH_LONG).show();
                                }))
                        .addOnFailureListener(e -> {
                            LoadingOverlay.hide(); busy = false;
                            Toast.makeText(context, describeAuthError(e, "Could not update email."), Toast.LENGTH_LONG).show();
                        }))
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide(); busy = false;
                    Toast.makeText(context, describeAuthError(e, "Your current password is incorrect."), Toast.LENGTH_LONG).show();
                });
    }

    // ════════════════════════════════════════════════════════════════════
    // INFO MODALS
    // ════════════════════════════════════════════════════════════════════

    private Dialog buildInfoModal(int iconRes, String title, String subtitle) {
        View modalView = LayoutInflater.from(context).inflate(R.layout.dialog_info_modal, null);

        ImageView icon = modalView.findViewById(R.id.modalIcon);
        TextView titleView = modalView.findViewById(R.id.modalTitle);
        TextView subtitleView = modalView.findViewById(R.id.modalSubtitle);
        ImageView closeBtn = modalView.findViewById(R.id.modalCloseBtn);

        icon.setImageResource(iconRes);
        titleView.setText(title);
        subtitleView.setText(subtitle);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(modalView);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Refined close button matching web version - small gray button top right
        closeBtn.setImageResource(R.drawable.ic_close);
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        return dialog;
    }

    private void showPrivacyPolicyDialog() {
        Dialog dialog = buildInfoModal(R.drawable.ic_security, "Privacy Policy",
                "How SpaceS CICT collects, uses, and protects your information.");
        LinearLayout container = dialog.findViewById(R.id.modalBodyContainer);

        TextView intro = new TextView(context);
        intro.setText("SpaceS CICT is a classroom allocation and scheduling platform built for CICT at Bulacan State University.");
        intro.setTextColor(Color.parseColor("#44403C"));
        intro.setTextSize(13.5f);
        container.addView(intro);

        addSectionHeading(container, "Information We Collect");
        addBulletList(container, "Account details", "Login activity", "Schedules and reservations");

        addSectionHeading(container, "How We Use Your Information");
        addBulletList(container, "To authenticate accounts", "To manage scheduling", "To send notifications");

        dialog.show();
        setDialogWidth(dialog);
    }

    private void showTermsDialog() {
        Dialog dialog = buildInfoModal(R.drawable.ic_subject, "Terms of Use",
                "Please read these terms before using SpaceS CICT.");
        LinearLayout container = dialog.findViewById(R.id.modalBodyContainer);
        TextView intro = new TextView(context);
        intro.setText("By logging in, you agree to use the platform responsibly.");
        intro.setTextColor(Color.parseColor("#44403C"));
        intro.setTextSize(13.5f);
        container.addView(intro);
        addSectionHeading(container, "Account Responsibility");
        addBulletList(container, "Accounts must not be shared.", "Keep credentials confidential.");
        dialog.show();
        setDialogWidth(dialog);
    }

    private void showHelpDialog() {
        Dialog dialog = buildInfoModal(R.drawable.ic_info, "Help Center",
                "Need help? Reach out or check the FAQs.");
        LinearLayout container = dialog.findViewById(R.id.modalBodyContainer);
        container.addView(buildContactRow("OUTLOOK", "spaces-bulsu@outlook.com"));
        container.addView(buildContactRow("GMAIL", "spacescict@gmail.com"));
        TextView faqLink = new TextView(context);
        faqLink.setText("Check the FAQs first  \u2192");
        faqLink.setTextColor(Color.parseColor("#F97316"));
        faqLink.setTypeface(null, Typeface.BOLD);
        faqLink.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 24;
        faqLink.setLayoutParams(lp);
        faqLink.setOnClickListener(v -> { dialog.dismiss(); showFaqDialog(); });
        container.addView(faqLink);
        dialog.show();
        setDialogWidth(dialog);
    }

    private void showFaqDialog() {
        Dialog dialog = buildInfoModal(R.drawable.ic_notification, "Frequently Asked Questions",
                "Mabilisang sagot sa mga karaniwang tanong.");
        LinearLayout container = dialog.findViewById(R.id.modalBodyContainer);
        String[][] faqs = {
                {"Ano ang SpaceS CICT?", "Classroom scheduling platform para sa CICT."},
                {"Bakit naka-block ang account ko?", "Maaaring hindi active. Makipag-ugnayan sa Admin."}
        };
        for (String[] faq : faqs) container.addView(buildFaqItem(faq[0], faq[1]));
        dialog.show();
        setDialogWidth(dialog);
    }

    private View buildContactRow(String label, String email) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.input_field_bg);
        int pad = dp(14);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = 10;
        row.setLayoutParams(rowParams);
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#94A3B8"));
        labelView.setTextSize(10);
        col.addView(labelView);
        TextView emailView = new TextView(context);
        emailView.setText(email);
        emailView.setTypeface(null, Typeface.BOLD);
        emailView.setTextColor(Color.parseColor("#1C1917"));
        emailView.setTextSize(14);
        col.addView(emailView);
        row.addView(col);
        return row;
    }

    private View buildFaqItem(String question, String answer) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 16, 0, 0);
        LinearLayout qRow = new LinearLayout(context);
        qRow.setOrientation(LinearLayout.HORIZONTAL);
        qRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView q = new TextView(context);
        q.setText(question);
        q.setTypeface(null, Typeface.BOLD);
        q.setTextColor(Color.parseColor("#1C1917"));
        q.setTextSize(14);
        LinearLayout.LayoutParams qParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        q.setLayoutParams(qParams);
        qRow.addView(q);
        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(Color.parseColor("#94A3B8"));
        LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        chevron.setLayoutParams(cParams);
        qRow.addView(chevron);
        container.addView(qRow);
        TextView a = new TextView(context);
        a.setText(answer);
        a.setTextColor(Color.parseColor("#78716C"));
        a.setTextSize(13);
        a.setVisibility(View.GONE);
        a.setPadding(0, 8, dp(24), 0);
        container.addView(a);
        qRow.setOnClickListener(v -> {
            boolean visible = a.getVisibility() == View.VISIBLE;
            a.setVisibility(visible ? View.GONE : View.VISIBLE);
            chevron.setRotation(visible ? 0 : 90);
        });
        return container;
    }

    private void addSectionHeading(LinearLayout container, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextColor(Color.parseColor("#1C1917"));
        tv.setTextSize(15);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = 20;
        tv.setLayoutParams(lp);
        container.addView(tv);
    }

    private void addBulletList(LinearLayout container, String... bullets) {
        for (String bullet : bullets) {
            TextView tv = new TextView(context);
            tv.setText("\u2022  " + bullet);
            tv.setTextColor(Color.parseColor("#44403C"));
            tv.setTextSize(13.5f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = 10;
            tv.setLayoutParams(lp);
            container.addView(tv);
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DELETE ACCOUNT
    // ════════════════════════════════════════════════════════════════════

    private void showDeleteAccountDialog() {
        View dv = LayoutInflater.from(context).inflate(R.layout.dialog_delete_step1, null);
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dv);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        CheckBox checkbox = dv.findViewById(R.id.deleteUnderstandCheckbox);
        Button continueBtn = dv.findViewById(R.id.deleteContinueBtn);
        Button cancelBtn = dv.findViewById(R.id.deleteCancelBtn);
        checkbox.setOnCheckedChangeListener((bv, isChecked) -> continueBtn.setEnabled(isChecked));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        continueBtn.setOnClickListener(v -> { dialog.dismiss(); showDeleteConfirmDialog(); });
        dialog.show();
        setDialogWidth(dialog);
    }

    private void showDeleteConfirmDialog() {
        View dv = LayoutInflater.from(context).inflate(R.layout.dialog_delete_step2, null);
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(dv);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        EditText passwordInput = dv.findViewById(R.id.deletePasswordInput);
        EditText confirmTextInput = dv.findViewById(R.id.deleteConfirmTextInput);
        dv.findViewById(R.id.deleteStep2CancelBtn).setOnClickListener(v -> dialog.dismiss());
        dv.findViewById(R.id.deleteFinalBtn).setOnClickListener(v -> {
            String password = passwordInput.getText().toString().trim();
            String confirmText = confirmTextInput == null ? "" : confirmTextInput.getText().toString().trim();
            if (password.isEmpty()) { Toast.makeText(context, "Enter your password", Toast.LENGTH_SHORT).show(); return; }
            if (!"DELETE MY ACCOUNT".equals(confirmText)) { Toast.makeText(context, "Type exactly to confirm.", Toast.LENGTH_LONG).show(); return; }
            dialog.dismiss();
            reauthenticateAndDelete(password);
        });
        dialog.show();
        setDialogWidth(dialog);
    }

    private void reauthenticateAndDelete(String password) {
        if (currentUser == null || currentUser.getEmail() == null) return;
        LoadingOverlay.show((android.app.Activity) context, "Verifying identity...");
        AuthCredential credential = EmailAuthProvider.getCredential(currentUser.getEmail(), password);
        currentUser.reauthenticate(credential)
                .addOnSuccessListener(unused -> {
                    LoadingOverlay.show((android.app.Activity) context, "Deleting account...");
                    deleteViaCloudFunctionThenFallback();
                })
                .addOnFailureListener(e -> {
                    LoadingOverlay.hide();
                    Toast.makeText(context, "Incorrect password: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void deleteViaCloudFunctionThenFallback() {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        FirebaseFunctions.getInstance().getHttpsCallable("deleteUser").call(data)
                .addOnSuccessListener(result -> {
                    LoadingOverlay.hide();
                    Toast.makeText(context, "Account deleted.", Toast.LENGTH_LONG).show();
                    ((android.app.Activity) context).finishAffinity();
                })
                .addOnFailureListener(e -> {
                    boolean missing = e instanceof FirebaseFunctionsException
                            && (((FirebaseFunctionsException) e).getCode() == FirebaseFunctionsException.Code.NOT_FOUND
                            || ((FirebaseFunctionsException) e).getCode() == FirebaseFunctionsException.Code.UNAVAILABLE);
                    if (missing) deleteClientSideFallback();
                    else { LoadingOverlay.hide(); Toast.makeText(context, "Deletion failed: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
                });
    }

    private void deleteClientSideFallback() {
        firestore.collection("users").document(uid).delete()
                .addOnCompleteListener(ignored -> currentUser.delete()
                        .addOnSuccessListener(unused3 -> { LoadingOverlay.hide(); ((android.app.Activity) context).finishAffinity(); })
                        .addOnFailureListener(e -> { LoadingOverlay.hide(); Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show(); }));
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════

    private String describeAuthError(Exception e, String fallback) {
        String message = e.getMessage() == null ? fallback : e.getMessage().toLowerCase(java.util.Locale.US);
        if (message.contains("password is invalid") || message.contains("wrong-password")) return "Your current password is incorrect.";
        if (message.contains("email-already-in-use")) return "That email is already in use.";
        if (message.contains("requires-recent-login")) return "Please log out and log back in, then try again.";
        return message;
    }

    private void setDialogWidth(Dialog dialog) {
        int width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
        dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}