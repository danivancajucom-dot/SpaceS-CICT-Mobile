package com.example.spacescict;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RoomScheduleLoader {

    public static class RoomItem {
        public String id, kind, subject, section, faculty, startTime, endTime, originalRoom;
    }

    public interface Callback {
        void onResult(java.util.Map<String, List<RoomItem>> byDay);
        default void onError(String message) {}
    }

    public static void loadWeek(String roomId, String[] weekDates, Callback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("rooms").document(roomId).collection("schedules").get()
                .addOnSuccessListener(schedSnap -> {
                    List<DocumentSnapshot> schedules = new ArrayList<>();
                    for (DocumentSnapshot d : schedSnap.getDocuments()) {
                        Boolean initialized = d.getBoolean("initialized");
                        if (!Boolean.TRUE.equals(initialized)) schedules.add(d);
                    }

                    db.collection("events").whereEqualTo("roomId", roomId).get()
                            .addOnSuccessListener(eventSnap ->
                                    db.collection("reservationRequests").whereEqualTo("roomId", roomId).get()
                                            .addOnSuccessListener(resSnap ->
                                                    db.collection("roomReleases").whereEqualTo("roomId", roomId).get()
                                                            .addOnSuccessListener(releaseSnap ->
                                                                    db.collection("roomReassignments").get()
                                                                            .addOnSuccessListener(reassignSnap ->
                                                                                    build(schedules, eventSnap.getDocuments(), resSnap.getDocuments(),
                                                                                            releaseSnap.getDocuments(), reassignSnap.getDocuments(),
                                                                                            roomId, weekDates, callback))
                                                                            .addOnFailureListener(e -> callback.onError("roomReassignments: " + e.getMessage())))
                                                            .addOnFailureListener(e -> callback.onError("roomReleases: " + e.getMessage())))
                                            .addOnFailureListener(e -> callback.onError("reservationRequests: " + e.getMessage())))
                            .addOnFailureListener(e -> callback.onError("events: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("schedules: " + e.getMessage()));
    }

    static void build(List<DocumentSnapshot> schedules, List<DocumentSnapshot> events,
                      List<DocumentSnapshot> reservations, List<DocumentSnapshot> releases,
                      List<DocumentSnapshot> reassignments, String roomId, String[] weekDates,
                      Callback callback) {

        java.util.Map<String, List<RoomItem>> byDay = new java.util.HashMap<>();
        for (String d : ScheduleLoader.MON_FIRST) byDay.put(d, new ArrayList<>());

        Set<String> weekDateSet = new HashSet<>();
        java.util.Collections.addAll(weekDateSet, weekDates);

        Set<String> releasedKeys = new HashSet<>();
        for (DocumentSnapshot r : releases) {
            String scheduleId = r.getString("scheduleId");
            String date = r.getString("date");
            if (scheduleId != null && date != null) releasedKeys.add(scheduleId + "_" + date);
        }

        Set<String> awayKeys = new HashSet<>();
        List<DocumentSnapshot> reassignedInto = new ArrayList<>();
        for (DocumentSnapshot r : reassignments) {
            String status = r.getString("status");
            if (status == null || !status.equalsIgnoreCase("approved")) continue;
            String oldRoomId = r.getString("oldRoomId");
            String newRoomId = r.getString("newRoomId");
            if (roomId.equals(oldRoomId)) {
                awayKeys.add(r.getString("scheduleId") + "_" + r.getString("date"));
            }
            if (roomId.equals(newRoomId)) {
                reassignedInto.add(r);
            }
        }

        // Regular schedules — recur every matching weekday within this week
        for (DocumentSnapshot s : schedules) {
            String day = s.getString("day");
            if (day == null || !byDay.containsKey(day)) continue;

            int dayIdx = -1;
            for (int i = 0; i < ScheduleLoader.MON_FIRST.length; i++)
                if (ScheduleLoader.MON_FIRST[i].equals(day)) dayIdx = i;
            if (dayIdx == -1) continue;

            String dateStr = weekDates[dayIdx];
            String scheduleId = s.getId();
            if (releasedKeys.contains(scheduleId + "_" + dateStr)) continue;
            if (awayKeys.contains(scheduleId + "_" + dateStr)) continue;

            RoomItem item = new RoomItem();
            item.id = scheduleId;
            item.kind = "schedule";
            item.subject = s.getString("subject");
            item.section = s.getString("section");
            item.faculty = s.getString("faculty");
            item.startTime = s.getString("startTime");
            item.endTime = s.getString("endTime");
            byDay.get(day).add(item);
        }

        // Room activities (events)
        for (DocumentSnapshot e : events) {
            String date = e.getString("date");
            if (date == null || !weekDateSet.contains(date)) continue;
            String dayAbbrev = ScheduleLoader.dayAbbrevForDate(date);

            RoomItem item = new RoomItem();
            item.id = e.getId();
            item.kind = "event";
            String title = e.getString("title");
            String purpose = e.getString("purpose");
            item.subject = title != null ? title : (purpose != null ? purpose : "Room Activity");
            item.faculty = e.getString("faculty") != null ? e.getString("faculty") : "Room Activity";
            item.startTime = e.getString("startTime");
            item.endTime = e.getString("endTime");
            byDay.getOrDefault(dayAbbrev, new ArrayList<>()).add(item);
        }

        // Approved reservations
        for (DocumentSnapshot r : reservations) {
            String status = r.getString("status");
            if (status == null || !status.equalsIgnoreCase("approved")) continue;
            String date = r.getString("date");
            if (date == null || !weekDateSet.contains(date)) continue;
            String dayAbbrev = ScheduleLoader.dayAbbrevForDate(date);

            RoomItem item = new RoomItem();
            item.id = r.getId();
            item.kind = "reservation";
            String courseTitle = r.getString("courseTitle");
            String purpose = r.getString("purpose");
            item.subject = courseTitle != null ? courseTitle : (purpose != null ? purpose : "Reservation");
            item.faculty = r.getString("facultyName") != null ? r.getString("facultyName") : "Walk-in";
            item.startTime = r.getString("startTime");
            item.endTime = r.getString("endTime");
            byDay.getOrDefault(dayAbbrev, new ArrayList<>()).add(item);
        }

        // Reassigned-in classes
        for (DocumentSnapshot r : reassignedInto) {
            String date = r.getString("date");
            if (date == null || !weekDateSet.contains(date)) continue;
            String dayAbbrev = ScheduleLoader.dayAbbrevForDate(date);

            RoomItem item = new RoomItem();
            item.id = r.getId();
            item.kind = "reassignment";
            String courseTitle = r.getString("courseTitle");
            item.subject = (courseTitle != null ? courseTitle : "Class") + " (Moved)";
            item.faculty = r.getString("facultyName");
            item.originalRoom = r.getString("oldRoomName");
            item.startTime = r.getString("startTime");
            item.endTime = r.getString("endTime");
            byDay.getOrDefault(dayAbbrev, new ArrayList<>()).add(item);
        }

        for (String d : ScheduleLoader.MON_FIRST) {
            java.util.Collections.sort(byDay.get(d), java.util.Comparator.comparingInt(i ->
                    ScheduleLoader.parseTimeParts(i.startTime)[0] * 60 + ScheduleLoader.parseTimeParts(i.startTime)[1]));
        }

        callback.onResult(byDay);
    }
}