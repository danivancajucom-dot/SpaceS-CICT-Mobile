package com.example.spacescict;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Mirrors the web's utils/verification.js: generate a 6-digit code, store it
 * in the shared "verificationCodes" Firestore collection (single-use + TTL,
 * doc id "{email}__{purpose}") and email it via EmailJS, so a code requested
 * from the phone can be verified the exact same way the web verifies one
 * (and vice versa - the two platforms share the same Firestore doc).
 *
 * EmailJS credentials match the web's (same service/template/account), so
 * this only works once "Allow API calls from non-browser applications" is
 * enabled for that EmailJS account (Account -> Security). If that box isn't
 * checked, the send will fail with a 403 and createAndSendCode reports it
 * through the error callback like any other failure - it does not pretend to
 * silently succeed.
 */
public final class VerificationHelper {

    private static final String EMAILJS_SERVICE_ID = "service_qogg8xj";
    private static final String EMAILJS_TEMPLATE_ID = "template_asj2rbj";
    private static final String EMAILJS_PUBLIC_KEY = "bNsod6OOQzMmRo0Cs";

    public static final int CODE_LENGTH = 6;
    public static final int CODE_TTL_MIN = 10;

    private static final Map<String, String> PURPOSE_LABELS = new HashMap<>();
    static {
        PURPOSE_LABELS.put("password-reset", "reset your password");
        PURPOSE_LABELS.put("password-change", "change your password");
        PURPOSE_LABELS.put("email-change", "change your email address");
    }

    public interface ResultCallback {
        void onSuccess();
        void onError(String message);
    }

    private VerificationHelper() {}

    public static String generateCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    /** Creates a code, saves it in Firestore, and emails it via EmailJS. Runs the callback on the UI thread. */
    public static void createAndSendCode(String email, String purpose, ResultCallback callback) {
        String code = generateCode();
        long expiresAt = System.currentTimeMillis() + CODE_TTL_MIN * 60_000L;
        String id = email.toLowerCase() + "__" + purpose;

        Map<String, Object> doc = new HashMap<>();
        doc.put("email", email.toLowerCase());
        doc.put("code", code);
        doc.put("purpose", purpose);
        doc.put("expiresAt", expiresAt);
        doc.put("createdAt", System.currentTimeMillis());
        doc.put("used", false);

        FirebaseFirestore.getInstance().collection("verificationCodes").document(id).set(doc)
                .addOnSuccessListener(ignored -> sendEmail(email, code, purpose, callback))
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not create verification code."));
    }

    private static void sendEmail(String email, String code, String purpose, ResultCallback callback) {
        new Thread(() -> {
            try {
                JSONObject templateParams = new JSONObject();
                templateParams.put("to_email", email);
                templateParams.put("to_name", email.contains("@") ? email.split("@")[0] : email);
                templateParams.put("code", code);
                templateParams.put("ttl_minutes", CODE_TTL_MIN);
                templateParams.put("purpose_label",
                        PURPOSE_LABELS.containsKey(purpose) ? PURPOSE_LABELS.get(purpose) : "verify your identity");

                JSONObject body = new JSONObject();
                body.put("service_id", EMAILJS_SERVICE_ID);
                body.put("template_id", EMAILJS_TEMPLATE_ID);
                body.put("user_id", EMAILJS_PUBLIC_KEY);
                body.put("accessToken", "1rkqtN5xCSK08kVI7FF0D");
                body.put("template_params", templateParams);

                HttpURLConnection connection = (HttpURLConnection)
                        new URL("https://api.emailjs.com/api/v1.0/email/send").openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();
                if (status == 200) {
                    postSuccess(callback);
                } else {
                    java.io.InputStream errStream = connection.getErrorStream();
                    String errResponse = "";
                    if (errStream != null) {
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = errStream.read(buf)) != -1) bos.write(buf, 0, len);
                        errResponse = bos.toString("UTF-8");
                    }
                    String msg = "EmailJS error " + status;
                    if (status == 403) {
                        msg += " (Forbidden): Ensure 'Allow API calls from non-browser applications' is enabled AND 'Require Access Token' is DISABLED in EmailJS Account > Security.";
                    }
                    if (!errResponse.isEmpty()) {
                        msg += " - Details: " + errResponse;
                    }
                    postError(callback, msg);
                }
            } catch (Exception e) {
                postError(callback, e.getMessage() != null ? e.getMessage() : "Could not send the verification email.");
            }
        }).start();
    }

    /** Checks the code the person typed. Marks it used on success. Runs the callback on the UI thread. */
    public static void verifyCode(String email, String purpose, String entered, ResultCallback callback) {
        String id = email.toLowerCase() + "__" + purpose;
        FirebaseFirestore.getInstance().collection("verificationCodes").document(id).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        callback.onError("No code found. Please request a new one.");
                        return;
                    }
                    Boolean used = snap.getBoolean("used");
                    Long expiresAt = snap.getLong("expiresAt");
                    String storedCode = snap.getString("code");

                    if (Boolean.TRUE.equals(used)) {
                        callback.onError("This code has already been used.");
                        return;
                    }
                    if (expiresAt == null || System.currentTimeMillis() > expiresAt) {
                        callback.onError("This code has expired. Please request a new one.");
                        return;
                    }
                    if (storedCode == null || !storedCode.equals(String.valueOf(entered).trim())) {
                        callback.onError("Incorrect code. Please try again.");
                        return;
                    }

                    FirebaseFirestore.getInstance().collection("verificationCodes").document(id)
                            .update("used", true)
                            .addOnSuccessListener(ignored -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onError(
                                    e.getMessage() != null ? e.getMessage() : "Could not verify the code."));
                })
                .addOnFailureListener(e -> callback.onError(
                        e.getMessage() != null ? e.getMessage() : "Could not verify the code."));
    }

    private static void postSuccess(ResultCallback callback) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(callback::onSuccess);
    }

    private static void postError(ResultCallback callback, String message) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onError(message));
    }
}
