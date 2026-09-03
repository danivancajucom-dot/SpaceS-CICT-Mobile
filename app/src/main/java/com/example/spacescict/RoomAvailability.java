package com.example.spacescict;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RoomAvailability {

    public interface ResultCallback {
        void onResult(List<RoomStatus> rooms);
    }

    public interface AvailabilityCallback {
        void onResult(boolean available, String reason);
    }

    public static class RoomStatus {
        public String id, roomName, floor, roomType, status, occupiedUntil;
        public int capacity;
        public boolean maintenance;
        public Map<String, Boolean> equipment;
    }

    static int toMinutes(String time) {
        if (time == null || !time.contains(":")) return 0;
        String[] p = time.split(":");
        try {
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean overlap(String aStart, String aEnd, String bStart, String bEnd) {
        return toMinutes(aStart) < toMinutes(bEnd) && toMinutes(aEnd) > toMinutes(bStart);
    }

    static String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
    }

    static String currentDayAbbrev() {
        String[] days = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        Calendar c = Calendar.getInstance();
        return days[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    static String currentTimeStr() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    static boolean isUnderMaintenance(DocumentSnapshot room, String date, String startTime, String endTime) {
        String status = room.getString("roomStatus");
        if (status == null || !status.equalsIgnoreCase("maintenance")) return false;
        String mStartDate = room.getString("maintenanceStartDate");
        String mEndDate = room.getString("maintenanceEndDate");
        if (mStartDate == null || mEndDate == null) return true;
        return date.compareTo(mStartDate) >= 0 && date.compareTo(mEndDate) <= 0;
    }

    public static void loadCurrentStatus(ResultCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String today = todayStr();
        String currentTime = currentTimeStr();
        String currentDay = currentDayAbbrev();
        int currentMinutes = toMinutes(currentTime);

        db.collection("rooms").get().addOnSuccessListener(roomsSnap -> {
            List<DocumentSnapshot> roomDocs = roomsSnap.getDocuments();

            db.collection("events").get().addOnSuccessListener(eventsSnap -> {
                db.collection("reservationRequests").get().addOnSuccessListener(reservationsSnap -> {
                    db.collection("roomReleases").get().addOnSuccessListener(releasesSnap -> {
                        db.collection("roomReassignments").get().addOnSuccessListener(reassignSnap -> {

                            Map<String, Set<String>> releaseMap = new HashMap<>();
                            for (DocumentSnapshot d : releasesSnap.getDocuments()) {
                                String date = d.getString("date");
                                if (!today.equals(date)) continue;
                                String roomId = d.getString("roomId");
                                String key = d.getString("scheduleId") + "_" + date;
                                releaseMap.computeIfAbsent(roomId, k -> new HashSet<>()).add(key);
                            }

                            Map<String, Set<String>> reassignAway = new HashMap<>();
                            Map<String, List<DocumentSnapshot>> reassignInto = new HashMap<>();
                            for (DocumentSnapshot d : reassignSnap.getDocuments()) {
                                String status = d.getString("status");
                                String date = d.getString("date");
                                if (status == null || !status.equalsIgnoreCase("approved")) continue;
                                if (!today.equals(date)) continue;

                                String key = d.getString("scheduleId") + "_" + date;
                                String oldRoomId = d.getString("oldRoomId");
                                String newRoomId = d.getString("newRoomId");
                                if (oldRoomId != null) reassignAway.computeIfAbsent(oldRoomId, k -> new HashSet<>()).add(key);
                                if (newRoomId != null) reassignInto.computeIfAbsent(newRoomId, k -> new ArrayList<>()).add(d);
                            }

                            List<RoomStatus> results = new ArrayList<>();
                            processRoomsSequentially(db, roomDocs, 0, results, eventsSnap, reservationsSnap,
                                    releaseMap, reassignAway, reassignInto, today, currentDay, currentMinutes, callback);
                        });
                    });
                });
            });
        });
    }

    private static void processRoomsSequentially(
            FirebaseFirestore db, List<DocumentSnapshot> roomDocs, int index, List<RoomStatus> results,
            QuerySnapshot eventsSnap, QuerySnapshot reservationsSnap,
            Map<String, Set<String>> releaseMap, Map<String, Set<String>> reassignAway,
            Map<String, List<DocumentSnapshot>> reassignInto,
            String today, String currentDay, int currentMinutes, ResultCallback callback) {

        if (index >= roomDocs.size()) {
            callback.onResult(results);
            return;
        }

        DocumentSnapshot roomDoc = roomDocs.get(index);
        RoomStatus rs = new RoomStatus();
        rs.id = roomDoc.getId();
        rs.roomName = roomDoc.getString("roomName");
        rs.floor = roomDoc.getString("floor");
        rs.roomType = roomDoc.getString("roomType");
        Long cap = roomDoc.getLong("capacity");
        rs.capacity = cap != null ? cap.intValue() : 0;

        if (isUnderMaintenance(roomDoc, today, currentTimeStr(), currentTimeStr())) {
            rs.status = "Maintenance";
            rs.maintenance = true;
            results.add(rs);
            processRoomsSequentially(db, roomDocs, index + 1, results, eventsSnap, reservationsSnap,
                    releaseMap, reassignAway, reassignInto, today, currentDay, currentMinutes, callback);
            return;
        }

        db.collection("rooms").document(rs.id).collection("schedules").get()
                .addOnSuccessListener(schedSnap -> {
                    boolean occupied = false;
                    String occupiedUntil = "";

                    Set<String> releasesForRoom = releaseMap.getOrDefault(rs.id, new HashSet<>());
                    Set<String> awayForRoom = reassignAway.getOrDefault(rs.id, new HashSet<>());

                    for (DocumentSnapshot sched : schedSnap.getDocuments()) {
                        Boolean initialized = sched.getBoolean("initialized");
                        if (Boolean.TRUE.equals(initialized)) continue;
                        String day = sched.getString("day");
                        if (!currentDay.equals(day)) continue;

                        String key = sched.getId() + "_" + today;
                        if (releasesForRoom.contains(key)) continue;
                        if (awayForRoom.contains(key)) continue;

                        int start = toMinutes(sched.getString("startTime"));
                        int end = toMinutes(sched.getString("endTime"));
                        if (currentMinutes >= start && currentMinutes < end) {
                            occupied = true;
                            occupiedUntil = sched.getString("endTime");
                        }
                    }

                    if (!occupied) {
                        for (DocumentSnapshot e : eventsSnap.getDocuments()) {
                            if (!rs.id.equals(e.getString("roomId"))) continue;
                            if (!today.equals(e.getString("date"))) continue;
                            int start = toMinutes(e.getString("startTime"));
                            int end = toMinutes(e.getString("endTime"));
                            if (currentMinutes >= start && currentMinutes < end) {
                                occupied = true;
                                occupiedUntil = e.getString("endTime");
                            }
                        }
                    }

                    if (!occupied) {
                        for (DocumentSnapshot r : reservationsSnap.getDocuments()) {
                            if (!rs.id.equals(r.getString("roomId"))) continue;
                            String status = r.getString("status");
                            if (status == null || !status.equalsIgnoreCase("approved")) continue;
                            if (!today.equals(r.getString("date"))) continue;
                            int start = toMinutes(r.getString("startTime"));
                            int end = toMinutes(r.getString("endTime"));
                            if (currentMinutes >= start && currentMinutes < end) {
                                occupied = true;
                                occupiedUntil = r.getString("endTime");
                            }
                        }
                    }

                    if (!occupied) {
                        List<DocumentSnapshot> into = reassignInto.getOrDefault(rs.id, new ArrayList<>());
                        for (DocumentSnapshot r : into) {
                            int start = toMinutes(r.getString("startTime"));
                            int end = toMinutes(r.getString("endTime"));
                            if (currentMinutes >= start && currentMinutes < end) {
                                occupied = true;
                                occupiedUntil = r.getString("endTime");
                            }
                        }
                    }

                    rs.status = occupied ? "Occupied" : "Available";
                    rs.occupiedUntil = occupiedUntil;
                    rs.maintenance = false;
                    results.add(rs);

                    processRoomsSequentially(db, roomDocs, index + 1, results, eventsSnap, reservationsSnap,
                            releaseMap, reassignAway, reassignInto, today, currentDay, currentMinutes, callback);
                });
    }

    public static void checkAvailability(String roomId, String date, String startTime, String endTime,
                                         String excludeReservationId, AvailabilityCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String dayAbbrev = dayAbbrevForDate(date);

        db.collection("rooms").document(roomId).get().addOnSuccessListener(roomDoc -> {
            if (isUnderMaintenance(roomDoc, date, startTime, endTime)) {
                callback.onResult(false, "This room is under maintenance during the selected time.");
                return;
            }

            db.collection("rooms").document(roomId).collection("schedules").get()
                    .addOnSuccessListener(schedSnap -> {
                        for (DocumentSnapshot sched : schedSnap.getDocuments()) {
                            Boolean initialized = sched.getBoolean("initialized");
                            if (Boolean.TRUE.equals(initialized)) continue;
                            if (!dayAbbrev.equals(sched.getString("day"))) continue;
                            if (overlap(startTime, endTime, sched.getString("startTime"), sched.getString("endTime"))) {
                                callback.onResult(false, "This room has a regular class schedule at that time.");
                                return;
                            }
                        }

                        db.collection("events")
                                .whereEqualTo("roomId", roomId)
                                .whereEqualTo("date", date)
                                .get().addOnSuccessListener(eventSnap -> {
                                    for (DocumentSnapshot e : eventSnap.getDocuments()) {
                                        if (overlap(startTime, endTime, e.getString("startTime"), e.getString("endTime"))) {
                                            callback.onResult(false, "This room has an activity scheduled at that time.");
                                            return;
                                        }
                                    }

                                    db.collection("reservationRequests")
                                            .whereEqualTo("roomId", roomId)
                                            .whereEqualTo("date", date)
                                            .get().addOnSuccessListener(resSnap -> {
                                                for (DocumentSnapshot r : resSnap.getDocuments()) {
                                                    if (r.getId().equals(excludeReservationId)) continue;
                                                    String status = r.getString("status");
                                                    if (status == null || status.equalsIgnoreCase("Rejected")) continue;
                                                    if (overlap(startTime, endTime, r.getString("startTime"), r.getString("endTime"))) {
                                                        callback.onResult(false, "This room is already reserved at that time.");
                                                        return;
                                                    }
                                                }
                                                callback.onResult(true, null);
                                            });
                                });
                    });
        });
    }

    public static class RoomSlotStatus {
        public String id, roomName, floor, roomType, status; // Available / Occupied / Reserved / Maintenance
        public int capacity;
        public Map<String, Boolean> equipment;
    }

    public interface SlotResultCallback {
        void onResult(List<RoomSlotStatus> rooms);
    }

    public static void loadAvailabilityForSlot(String date, String startTime, String endTime,
                                               String currentUid, SlotResultCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("rooms").get().addOnSuccessListener(roomsSnap -> {
            List<DocumentSnapshot> roomDocs = roomsSnap.getDocuments();

            db.collection("events").whereEqualTo("date", date).get().addOnSuccessListener(eventsSnap -> {
                db.collection("reservationRequests").whereEqualTo("date", date).get().addOnSuccessListener(resSnap -> {
                    db.collection("roomReleases").get().addOnSuccessListener(releaseSnap -> {

                        Map<String, Set<String>> releaseMap = new HashMap<>();
                        for (DocumentSnapshot d : releaseSnap.getDocuments()) {
                            String relDate = d.getString("date");
                            if (!date.equals(relDate)) continue;
                            String roomId = d.getString("roomId");
                            String key = d.getString("scheduleId") + "_" + relDate;
                            releaseMap.computeIfAbsent(roomId, k -> new HashSet<>()).add(key);
                        }

                        List<RoomSlotStatus> results = new ArrayList<>();
                        processSlotRoomsSequentially(db, roomDocs, 0, results, eventsSnap.getDocuments(),
                                resSnap.getDocuments(), releaseMap, date, startTime, endTime, currentUid, callback);
                    });
                });
            });
        });
    }

    private static void processSlotRoomsSequentially(
            FirebaseFirestore db, List<DocumentSnapshot> roomDocs, int index, List<RoomSlotStatus> results,
            List<DocumentSnapshot> events, List<DocumentSnapshot> reservations, Map<String, Set<String>> releaseMap,
            String date, String startTime, String endTime, String currentUid, SlotResultCallback callback) {

        if (index >= roomDocs.size()) {
            callback.onResult(results);
            return;
        }

        DocumentSnapshot roomDoc = roomDocs.get(index);
        RoomSlotStatus rs = new RoomSlotStatus();
        rs.id = roomDoc.getId();
        rs.roomName = roomDoc.getString("roomName");
        rs.floor = roomDoc.getString("floor");
        rs.roomType = roomDoc.getString("roomType");
        Long cap = roomDoc.getLong("capacity");
        rs.capacity = cap != null ? cap.intValue() : 0;

        if (isUnderMaintenance(roomDoc, date, startTime, endTime)) {
            rs.status = "Maintenance";
            results.add(rs);
            processSlotRoomsSequentially(db, roomDocs, index + 1, results, events, reservations, releaseMap,
                    date, startTime, endTime, currentUid, callback);
            return;
        }

        db.collection("rooms").document(rs.id).collection("schedules").get()
                .addOnSuccessListener(schedSnap -> {
                    boolean occupied = false;
                    String dayAbbrev = dayAbbrevForDate(date);
                    Set<String> releasesForRoom = releaseMap.getOrDefault(rs.id, new HashSet<>());

                    for (DocumentSnapshot sched : schedSnap.getDocuments()) {
                        Boolean initialized = sched.getBoolean("initialized");
                        if (Boolean.TRUE.equals(initialized)) continue;
                        if (!dayAbbrev.equals(sched.getString("day"))) continue;
                        String key = sched.getId() + "_" + date;
                        if (releasesForRoom.contains(key)) continue;
                        if (overlap(startTime, endTime, sched.getString("startTime"), sched.getString("endTime"))) {
                            occupied = true;
                            break;
                        }
                    }

                    if (!occupied) {
                        for (DocumentSnapshot e : events) {
                            if (!rs.id.equals(e.getString("roomId"))) continue;
                            if (overlap(startTime, endTime, e.getString("startTime"), e.getString("endTime"))) {
                                occupied = true;
                                break;
                            }
                        }
                    }

                    boolean reservedByUser = false;
                    if (!occupied) {
                        for (DocumentSnapshot r : reservations) {
                            if (!rs.id.equals(r.getString("roomId"))) continue;
                            String status = r.getString("status");
                            if (status == null || status.equalsIgnoreCase("Rejected") || status.equalsIgnoreCase("cancelled")) continue;
                            if (!overlap(startTime, endTime, r.getString("startTime"), r.getString("endTime"))) continue;

                            if (currentUid != null && currentUid.equals(r.getString("userId"))) {
                                reservedByUser = true;
                                break;
                            } else if (status.equalsIgnoreCase("Approved")) {
                                occupied = true;
                                break;
                            }
                            // other user's pending -> ignored, matches web
                        }
                    }

                    rs.status = occupied ? "Occupied" : reservedByUser ? "Reserved" : "Available";
                    results.add(rs);

                    processSlotRoomsSequentially(db, roomDocs, index + 1, results, events, reservations, releaseMap,
                            date, startTime, endTime, currentUid, callback);
                });
    }


    static String dayAbbrevForDate(String dateStr) {
        String[] days = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar c = Calendar.getInstance();
            c.setTime(fmt.parse(dateStr));
            return days[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return "MON";
        }
    }
}