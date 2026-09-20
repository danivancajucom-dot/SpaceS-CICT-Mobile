package com.example.spacescict;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Availability engine. Rewritten to match FacultyRoom.jsx / FacultySubmitReservation.jsx:
 *
 *  - room reassignments are honoured (a class moved AWAY frees the old room,
 *    a class moved INTO a room occupies it)
 *  - cancelled room activities no longer block a room
 *  - "occupiedUntil" is reported so cards can say "Occupied until 15:30"
 *  - optional equipment / capacity filtering for the reservation form
 *  - one collectionGroup read instead of one read per room (much faster)
 */
public final class RoomAvailability {

    public interface ResultCallback { void onResult(List<RoomStatus> rooms); }
    public interface AvailabilityCallback { void onResult(boolean available, String reason); }
    public interface SlotResultCallback { void onResult(List<RoomSlotStatus> rooms); }

    public static class RoomStatus {
        public String id, roomName, building, floor, roomType, status, occupiedUntil;
        public int capacity;
        public boolean maintenance;
        public Map<String, Boolean> equipment;
    }

    public static class RoomSlotStatus {
        public String id, roomName, building, floor, roomType, status, occupiedUntil;
        public int capacity;
        public Map<String, Boolean> equipment;
    }

    /** Optional extra filtering, mirroring the web reservation form. */
    public static class Filter {
        public String floorContains;              // e.g. "1st"
        public List<String> requiredEquipment;    // e.g. ["projector","ac"]
        public int minimumCapacity;               // 0 = no constraint
    }

    private RoomAvailability() {}

    // ─── time helpers ────────────────────────────────────────────────────
    static int toMinutes(String time) {
        if (time == null || !time.contains(":")) return 0;
        try {
            String[] parts = time.trim().split(":");
            return Integer.parseInt(parts[0].trim()) * 60
                    + Integer.parseInt(parts[1].replaceAll("[^0-9].*", "").trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    static boolean overlap(String aStart, String aEnd, String bStart, String bEnd) {
        return toMinutes(aStart) < toMinutes(bEnd) && toMinutes(aEnd) > toMinutes(bStart);
    }

    /**
     * The web treats a zero-length window (start == end, i.e. "right now") as
     * overlapping anything that contains that instant, so mirror that here.
     */
    static boolean overlapInclusive(String windowStart, String windowEnd,
                                    String otherStart, String otherEnd) {
        int ws = toMinutes(windowStart), we = toMinutes(windowEnd);
        if (ws > we) { int swap = ws; ws = we; we = swap; }
        int os = toMinutes(otherStart), oe = toMinutes(otherEnd);
        return os <= we && oe >= ws;
    }

    static String todayStr() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date());
    }

    static String currentTimeStr() {
        Calendar calendar = Calendar.getInstance();
        return String.format(Locale.US, "%02d:%02d",
                calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    static String currentDayAbbrev() {
        String[] days = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
        return days[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1];
    }

    static String dayAbbrevForDate(String date) {
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date));
            return new String[]{"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"}[
                    calendar.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception ignored) {
            return "MON";
        }
    }

    static boolean isUnderMaintenance(DocumentSnapshot room, String date,
                                      String startTime, String endTime) {
        String status = room.getString("roomStatus");
        if (status == null || !status.trim().equalsIgnoreCase("maintenance")) return false;
        String startDate = room.getString("maintenanceStartDate");
        String endDate = room.getString("maintenanceEndDate");
        if (startDate == null || endDate == null) return true;
        return date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0;
    }

    static boolean isCancelled(DocumentSnapshot doc) {
        String status = doc.getString("status");
        return status != null && status.equalsIgnoreCase("cancelled");
    }

    /** The web writes "approved" from the clerk side and "accepted" from faculty. */
    static boolean isApprovedReassignment(DocumentSnapshot doc) {
        String status = doc.getString("status");
        if (status == null) return false;
        return status.equalsIgnoreCase("approved") || status.equalsIgnoreCase("accepted");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Boolean> equipmentOf(DocumentSnapshot room) {
        Object raw = room.get("equipment");
        Map<String, Boolean> result = new HashMap<>();
        if (raw instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
                if (entry.getKey() == null) continue;
                Object value = entry.getValue();
                result.put(entry.getKey().toString().toLowerCase(Locale.US),
                        Boolean.TRUE.equals(value));
            }
        }
        return result;
    }

    // ─── public API ──────────────────────────────────────────────────────

    public static void loadCurrentStatus(ResultCallback callback) {
        String time = currentTimeStr();
        loadAvailabilityForSlot(todayStr(), time, time, null, results -> {
            List<RoomStatus> converted = new ArrayList<>();
            for (RoomSlotStatus item : results) {
                RoomStatus room = new RoomStatus();
                room.id = item.id;
                room.roomName = item.roomName;
                room.building = item.building;
                room.floor = item.floor;
                room.roomType = item.roomType;
                room.capacity = item.capacity;
                room.status = item.status;
                room.occupiedUntil = item.occupiedUntil;
                room.equipment = item.equipment;
                room.maintenance = "Maintenance".equalsIgnoreCase(item.status);
                converted.add(room);
            }
            callback.onResult(converted);
        });
    }

    public static void loadAvailabilityForSlot(String date, String startTime, String endTime,
                                               String currentUid, SlotResultCallback callback) {
        loadAvailabilityForSlot(date, startTime, endTime, currentUid, null, callback);
    }

    public static void loadAvailabilityForSlot(String date, String startTime, String endTime,
                                               String currentUid, Filter filter,
                                               SlotResultCallback callback) {
        final FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("rooms").get()
                .addOnSuccessListener(rooms -> db.collectionGroup("schedules").get()
                        .addOnSuccessListener(schedules -> db.collection("events").whereEqualTo("date", date).get()
                                .addOnSuccessListener(events -> db.collection("reservationRequests").whereEqualTo("date", date).get()
                                        .addOnSuccessListener(reservations -> db.collection("roomReleases").whereEqualTo("date", date).get()
                                                .addOnSuccessListener(releases -> db.collection("roomReassignments").whereEqualTo("date", date).get()
                                                        .addOnSuccessListener(reassignments -> callback.onResult(build(
                                                                rooms.getDocuments(), schedules.getDocuments(), events.getDocuments(),
                                                                reservations.getDocuments(), releases.getDocuments(),
                                                                reassignments.getDocuments(), date, startTime, endTime,
                                                                currentUid, filter)))
                                                        .addOnFailureListener(e -> callback.onResult(new ArrayList<>())))
                                                .addOnFailureListener(e -> callback.onResult(new ArrayList<>())))
                                        .addOnFailureListener(e -> callback.onResult(new ArrayList<>())))
                                .addOnFailureListener(e -> callback.onResult(new ArrayList<>())))
                        .addOnFailureListener(e -> callback.onResult(new ArrayList<>())))
                .addOnFailureListener(e -> callback.onResult(new ArrayList<>()));
    }

    private static List<RoomSlotStatus> build(List<DocumentSnapshot> roomDocs,
                                              List<DocumentSnapshot> scheduleDocs,
                                              List<DocumentSnapshot> events,
                                              List<DocumentSnapshot> reservations,
                                              List<DocumentSnapshot> releases,
                                              List<DocumentSnapshot> reassignments,
                                              String date, String startTime, String endTime,
                                              String currentUid, Filter filter) {

        String day = dayAbbrevForDate(date);

        // schedules grouped by their parent room
        Map<String, List<DocumentSnapshot>> schedulesByRoom = new HashMap<>();
        for (DocumentSnapshot schedule : scheduleDocs) {
            String roomId = parentRoomId(schedule);
            if (roomId == null) continue;
            List<DocumentSnapshot> list = schedulesByRoom.get(roomId);
            if (list == null) {
                list = new ArrayList<>();
                schedulesByRoom.put(roomId, list);
            }
            list.add(schedule);
        }

        // scheduleId_date keys that were released for this date
        Set<String> releasedKeys = new HashSet<>();
        for (DocumentSnapshot release : releases) {
            String scheduleId = release.getString("scheduleId");
            if (scheduleId != null) releasedKeys.add(scheduleId + "_" + date);
        }

        // reassignments: away keys free the old room, "into" entries occupy the new one
        Set<String> awayKeys = new HashSet<>();
        Map<String, List<DocumentSnapshot>> reassignedInto = new HashMap<>();
        for (DocumentSnapshot reassignment : reassignments) {
            if (!isApprovedReassignment(reassignment)) continue;
            String scheduleId = reassignment.getString("scheduleId");
            String oldRoomId = reassignment.getString("oldRoomId");
            String newRoomId = reassignment.getString("newRoomId");
            if (scheduleId != null && oldRoomId != null) {
                awayKeys.add(oldRoomId + "|" + scheduleId + "_" + date);
            }
            if (newRoomId != null) {
                List<DocumentSnapshot> list = reassignedInto.get(newRoomId);
                if (list == null) {
                    list = new ArrayList<>();
                    reassignedInto.put(newRoomId, list);
                }
                list.add(reassignment);
            }
        }

        List<RoomSlotStatus> results = new ArrayList<>();

        for (DocumentSnapshot roomDoc : roomDocs) {
            RoomSlotStatus room = new RoomSlotStatus();
            room.id = roomDoc.getId();
            room.roomName = roomDoc.getString("roomName");
            room.building = roomDoc.getString("building");
            room.floor = roomDoc.getString("floor");
            room.roomType = roomDoc.getString("roomType");
            Long capacity = roomDoc.getLong("capacity");
            room.capacity = capacity == null ? 0 : capacity.intValue();
            room.equipment = equipmentOf(roomDoc);
            room.occupiedUntil = "";

            if (!passesFilter(room, filter)) continue;

            if (isUnderMaintenance(roomDoc, date, startTime, endTime)) {
                room.status = "Maintenance";
                results.add(room);
                continue;
            }

            boolean occupied = false;
            boolean reservedByUser = false;
            String occupiedUntil = "";

            List<DocumentSnapshot> schedules = schedulesByRoom.get(room.id);
            if (schedules != null) {
                for (DocumentSnapshot schedule : schedules) {
                    if (Boolean.TRUE.equals(schedule.getBoolean("initialized"))) continue;
                    String scheduleDay = schedule.getString("day");
                    if (scheduleDay == null || !day.equalsIgnoreCase(scheduleDay.trim())) continue;
                    String key = schedule.getId() + "_" + date;
                    if (releasedKeys.contains(key)) continue;
                    if (awayKeys.contains(room.id + "|" + key)) continue;
                    if (overlapInclusive(startTime, endTime,
                            schedule.getString("startTime"), schedule.getString("endTime"))) {
                        occupied = true;
                        occupiedUntil = schedule.getString("endTime");
                        break;
                    }
                }
            }

            if (!occupied) {
                for (DocumentSnapshot event : events) {
                    if (!room.id.equals(event.getString("roomId"))) continue;
                    if (isCancelled(event)) continue;
                    if (overlapInclusive(startTime, endTime,
                            event.getString("startTime"), event.getString("endTime"))) {
                        occupied = true;
                        occupiedUntil = event.getString("endTime");
                        break;
                    }
                }
            }

            if (!occupied) {
                for (DocumentSnapshot reservation : reservations) {
                    if (!room.id.equals(reservation.getString("roomId"))) continue;
                    String status = reservation.getString("status");
                    if (status == null || status.equalsIgnoreCase("rejected")
                            || status.equalsIgnoreCase("cancelled")) continue;
                    if (!overlapInclusive(startTime, endTime,
                            reservation.getString("startTime"), reservation.getString("endTime"))) continue;
                    if (currentUid != null && currentUid.equals(reservation.getString("userId"))) {
                        reservedByUser = true;
                    } else if (status.equalsIgnoreCase("approved")) {
                        occupied = true;
                        occupiedUntil = reservation.getString("endTime");
                        break;
                    }
                }
            }

            if (!occupied) {
                List<DocumentSnapshot> moved = reassignedInto.get(room.id);
                if (moved != null) {
                    for (DocumentSnapshot reassignment : moved) {
                        if (overlapInclusive(startTime, endTime,
                                reassignment.getString("startTime"), reassignment.getString("endTime"))) {
                            occupied = true;
                            occupiedUntil = reassignment.getString("endTime");
                            break;
                        }
                    }
                }
            }

            room.status = occupied ? "Occupied" : reservedByUser ? "Reserved" : "Available";
            room.occupiedUntil = occupiedUntil == null ? "" : occupiedUntil;
            results.add(room);
        }

        return results;
    }

    private static boolean passesFilter(RoomSlotStatus room, Filter filter) {
        if (filter == null) return true;

        if (filter.floorContains != null && !filter.floorContains.isEmpty()) {
            String floor = room.floor == null ? "" : room.floor.toLowerCase(Locale.US);
            if (!floor.contains(filter.floorContains.toLowerCase(Locale.US))) return false;
        }

        if (filter.requiredEquipment != null && !filter.requiredEquipment.isEmpty()) {
            for (String needed : filter.requiredEquipment) {
                if (needed == null) continue;
                if (!Boolean.TRUE.equals(room.equipment.get(needed.toLowerCase(Locale.US)))) {
                    return false;
                }
            }
        }

        if (filter.minimumCapacity > 0 && room.capacity < filter.minimumCapacity) return false;

        return true;
    }

    static String parentRoomId(DocumentSnapshot schedule) {
        try {
            if (schedule.getReference().getParent().getParent() == null) return null;
            return schedule.getReference().getParent().getParent().getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Single-room conflict check used by the reservation edit screen.
     * excludeReservationId lets a reservation ignore itself while editing.
     */
    public static void checkAvailability(String roomId, String date, String startTime,
                                         String endTime, String excludeReservationId,
                                         AvailabilityCallback callback) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("rooms").document(roomId).get().addOnSuccessListener(room -> {
            if (isUnderMaintenance(room, date, startTime, endTime)) {
                callback.onResult(false, "This room is under maintenance during the selected time.");
                return;
            }
            db.collection("roomReleases").whereEqualTo("date", date).get()
                    .addOnSuccessListener(releaseSnap -> {
                        Set<String> released = new HashSet<>();
                        for (DocumentSnapshot release : releaseSnap.getDocuments()) {
                            String scheduleId = release.getString("scheduleId");
                            if (scheduleId != null) released.add(scheduleId);
                        }
                        db.collection("roomReassignments").whereEqualTo("date", date).get()
                                .addOnSuccessListener(reassignSnap -> {
                                    Set<String> movedAway = new HashSet<>();
                                    List<DocumentSnapshot> movedIn = new ArrayList<>();
                                    for (DocumentSnapshot r : reassignSnap.getDocuments()) {
                                        if (!isApprovedReassignment(r)) continue;
                                        if (roomId.equals(r.getString("oldRoomId"))
                                                && r.getString("scheduleId") != null) {
                                            movedAway.add(r.getString("scheduleId"));
                                        }
                                        if (roomId.equals(r.getString("newRoomId"))) movedIn.add(r);
                                    }
                                    for (DocumentSnapshot r : movedIn) {
                                        if (overlap(startTime, endTime,
                                                r.getString("startTime"), r.getString("endTime"))) {
                                            callback.onResult(false,
                                                    "Another class has been moved into this room at that time.");
                                            return;
                                        }
                                    }
                                    checkSchedules(db, roomId, date, startTime, endTime,
                                            released, movedAway, excludeReservationId, callback);
                                })
                                .addOnFailureListener(e -> checkSchedules(db, roomId, date, startTime,
                                        endTime, released, new HashSet<>(), excludeReservationId, callback));
                    })
                    .addOnFailureListener(e -> checkSchedules(db, roomId, date, startTime, endTime,
                            new HashSet<>(), new HashSet<>(), excludeReservationId, callback));
        }).addOnFailureListener(e -> callback.onResult(false, "Could not verify this room right now."));
    }

    private static void checkSchedules(FirebaseFirestore db, String roomId, String date,
                                       String startTime, String endTime, Set<String> released,
                                       Set<String> movedAway, String excludeReservationId,
                                       AvailabilityCallback callback) {
        db.collection("rooms").document(roomId).collection("schedules").get()
                .addOnSuccessListener(schedules -> {
                    String day = dayAbbrevForDate(date);
                    for (DocumentSnapshot schedule : schedules.getDocuments()) {
                        if (Boolean.TRUE.equals(schedule.getBoolean("initialized"))) continue;
                        String scheduleDay = schedule.getString("day");
                        if (scheduleDay == null || !day.equalsIgnoreCase(scheduleDay.trim())) continue;
                        if (released.contains(schedule.getId())) continue;
                        if (movedAway.contains(schedule.getId())) continue;
                        if (overlap(startTime, endTime, schedule.getString("startTime"),
                                schedule.getString("endTime"))) {
                            callback.onResult(false, "This room has a regular class schedule at that time.");
                            return;
                        }
                    }
                    db.collection("events").whereEqualTo("roomId", roomId)
                            .whereEqualTo("date", date).get().addOnSuccessListener(events -> {
                                for (DocumentSnapshot event : events.getDocuments()) {
                                    if (isCancelled(event)) continue;
                                    if (overlap(startTime, endTime, event.getString("startTime"),
                                            event.getString("endTime"))) {
                                        callback.onResult(false,
                                                "This room has an activity scheduled at that time.");
                                        return;
                                    }
                                }
                                db.collection("reservationRequests").whereEqualTo("roomId", roomId)
                                        .whereEqualTo("date", date).get().addOnSuccessListener(reservations -> {
                                            for (DocumentSnapshot reservation : reservations.getDocuments()) {
                                                if (reservation.getId().equals(excludeReservationId)) continue;
                                                String status = reservation.getString("status");
                                                if (status == null || status.equalsIgnoreCase("rejected")
                                                        || status.equalsIgnoreCase("cancelled")) continue;
                                                if (overlap(startTime, endTime,
                                                        reservation.getString("startTime"),
                                                        reservation.getString("endTime"))) {
                                                    callback.onResult(false,
                                                            "This room is already reserved at that time.");
                                                    return;
                                                }
                                            }
                                            callback.onResult(true, null);
                                        })
                                        .addOnFailureListener(e -> callback.onResult(false,
                                                "Could not verify existing reservations."));
                            })
                            .addOnFailureListener(e -> callback.onResult(false,
                                    "Could not verify room activities."));
                })
                .addOnFailureListener(e -> callback.onResult(false, "Could not verify class schedules."));
    }
}