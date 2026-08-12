package com.example.spacescict;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class NotificationHelper {

    public static void send(String userId, String ownerType, String title,
                            String message, String type, String badge) {
        if (userId == null) return;

        Map<String, Object> n = new HashMap<>();
        n.put("userId", userId);
        n.put("ownerType", ownerType);
        n.put("title", title);
        n.put("message", message);
        n.put("type", type);
        n.put("unread", true);
        n.put("archived", false);
        n.put("badge", badge);
        n.put("createdAt", Timestamp.now());

        FirebaseFirestore.getInstance().collection("notifications").add(n);
    }

    public static void notifyClerkAndDepartmentHead(String title, String message,
                                                    String reservationId, String type) {
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        String role = doc.getString("role");
                        if (role == null) continue;
                        String r = role.toLowerCase().trim();

                        String ownerType = null;
                        if (r.equals("clerk")) ownerType = "clerk";
                        else if (r.contains("department") && r.contains("head")) ownerType = "department-head";
                        if (ownerType == null) continue;

                        Map<String, Object> n = new HashMap<>();
                        n.put("userId", doc.getId());
                        n.put("ownerType", ownerType);
                        n.put("reservationId", reservationId);
                        n.put("title", title);
                        n.put("message", message);
                        n.put("type", type);
                        n.put("unread", true);
                        n.put("archived", false);
                        n.put("badge", "NEW");
                        n.put("createdAt", Timestamp.now());

                        FirebaseFirestore.getInstance().collection("notifications").add(n);
                    }
                });
    }
}