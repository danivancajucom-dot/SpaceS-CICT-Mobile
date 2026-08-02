package com.example.spacescict;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class ActivityLogger {

    public interface LogCallback {
        void onDone();
    }

    public static void log(String action, String actionType, String target, String status) {
        log(action, actionType, target, status, null, null);
    }

    public static void log(String action, String actionType, String target, String status,
                           Map<String, Object> details, LogCallback callback) {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            if (callback != null) callback.onDone();
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String first = doc.getString("firstName");
                    String last = doc.getString("lastName");
                    String role = doc.getString("role");
                    String fullName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();

                    Map<String, Object> log = new HashMap<>();
                    log.put("timestamp", Timestamp.now());
                    log.put("action", action);
                    log.put("actionType", actionType);
                    log.put("user", fullName.isEmpty() ? "Faculty" : fullName);
                    log.put("role", role != null ? role : "Faculty");
                    log.put("target", target);
                    log.put("status", status);
                    log.put("userId", uid);
                    if (details != null) log.put("details", details);

                    FirebaseFirestore.getInstance().collection("activityLogs")
                            .add(log)
                            .addOnCompleteListener(t -> {
                                if (callback != null) callback.onDone();
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onDone();
                });
    }
}