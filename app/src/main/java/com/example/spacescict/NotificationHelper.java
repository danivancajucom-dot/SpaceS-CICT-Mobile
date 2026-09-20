package com.example.spacescict;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Central place for writing notification documents.
 *
 * IMPORTANT: the web app reads notifications with
 *   where("userId","==",uid) AND where("ownerType","==",<role>)
 * so ownerType MUST match the strings the web uses, otherwise the
 * recipient simply never sees the notification. The web uses:
 *   "faculty" | "clerk" | "admin" | "department-head"
 */
public class NotificationHelper {

    public static void send(String userId, String ownerType, String title,
                            String message, String type, String badge) {
        send(userId, ownerType, title, message, type, badge, null);
    }

    /** extras lets callers attach reservationId / assignmentId / roomId etc. */
    public static void send(String userId, String ownerType, String title, String message,
                            String type, String badge, Map<String, Object> extras) {
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
        if (extras != null) n.putAll(extras);

        FirebaseFirestore.getInstance().collection("notifications").add(n);
    }

    /**
     * Mirrors the web's notifyClerkAndAdmin(): notifies every user whose role is
     * Clerk or Admin. This is what the web faculty pages use for reservation
     * requests and room releases.
     */
    public static void notifyClerkAndAdmin(String title, String message,
                                           String type, String badge,
                                           Map<String, Object> extras) {
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String ownerType = ownerTypeFor(doc.getString("role"));
                        if (ownerType == null) continue;
                        if (!"clerk".equals(ownerType) && !"admin".equals(ownerType)) continue;
                        send(doc.getId(), ownerType, title, message, type, badge, extras);
                    }
                });
    }

    /**
     * Notifies Clerk, Admin AND Department Head. Use this when the mobile app
     * wants the widest staff reach (kept so existing deployments that rely on
     * department-head notifications keep working).
     */
    public static void notifyStaff(String title, String message,
                                   String type, String badge,
                                   Map<String, Object> extras) {
        FirebaseFirestore.getInstance().collection("users").get()
                .addOnSuccessListener(snapshot -> {
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String ownerType = ownerTypeFor(doc.getString("role"));
                        if (ownerType == null) continue;
                        send(doc.getId(), ownerType, title, message, type, badge, extras);
                    }
                });
    }

    /** Backwards-compatible wrapper used by the older reservation code. */
    public static void notifyClerkAndDepartmentHead(String title, String message,
                                                    String reservationId, String type) {
        Map<String, Object> extras = new HashMap<>();
        if (reservationId != null) extras.put("reservationId", reservationId);
        notifyStaff(title, message, type, "NEW", extras);
    }

    /** Maps a Firestore role string onto the ownerType value the web expects. */
    public static String ownerTypeFor(String role) {
        if (role == null) return null;
        String r = role.toLowerCase(Locale.US).trim();
        if (r.equals("clerk")) return "clerk";
        if (r.equals("admin")) return "admin";
        if (r.equals("faculty")) return null; // faculty are notified individually
        if (r.contains("department") && r.contains("head")) return "department-head";
        if (r.equals("department-head")) return "department-head";
        return null;
    }

    /** Updates the user's FCM token in Firestore so the backend can send push notifications. */
    public static void updateFCMToken(String userId) {
        if (userId == null) return;
        com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                .addOnSuccessListener(token -> {
                    FirebaseFirestore.getInstance().collection("users").document(userId)
                            .update("fcmToken", token);
                });
    }
}