package com.example.spacescict;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScheduleLoader {

    public static class ScheduleItem {
        public String id, kind, subject, roomName, section, startTime, endTime, date, faculty, originalRoom;
        public long occurrenceMillis;
        public boolean isToday;
        public String status;
    }

    public interface Callback {
        void onResult(List<ScheduleItem> todaysItems, List<ScheduleItem> upcomingItems,
                      String termLabel, int meetingsPerWeek, int roomsUsed);
    }

    public interface WeekCallback {
        void onResult(Map<String, List<ScheduleItem>> byDay, String weekLabel, String termLabel);
        default void onError(String message) {}
    }

    static final String[] DAY_LABELS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};
    static final String[] MON_FIRST = {"MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"};

    static String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase(Locale.US).replace(".", "").replace(",", "")
                .replaceAll("\\s+", " ").trim();
    }

    static int semesterRank(String sem) {
        if (sem == null) return 0;
        String s = sem.toLowerCase(Locale.US);
        if (s.contains("2nd")) return 2;
        if (s.contains("1st")) return 1;
        return 0;
    }

    static int schoolYearStart(String sy) {
        if (sy == null) return 0;
        Matcher m = Pattern.compile("\\d{4}").matcher(sy);
        return m.find() ? Integer.parseInt(m.group()) : 0;
    }

    static int[] parseTimeParts(String time) {
        if (time == null || !time.contains(":")) return new int[]{0, 0};
        String[] p = time.split(":");
        try {
            return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    static long getNextOccurrenceMillis(String dayAbbrev, String startTime, Calendar now) {
        int targetDow = -1;
        for (int i = 0; i < DAY_LABELS.length; i++) if (DAY_LABELS[i].equals(dayAbbrev)) targetDow = i + 1;
        if (targetDow == -1) return -1;

        int[] hm = parseTimeParts(startTime);
        for (int i = 0; i < 14; i++) {
            Calendar candidate = (Calendar) now.clone();
            candidate.add(Calendar.DAY_OF_MONTH, i);
            candidate.set(Calendar.HOUR_OF_DAY, hm[0]);
            candidate.set(Calendar.MINUTE, hm[1]);
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);
            if (candidate.get(Calendar.DAY_OF_WEEK) == targetDow && !candidate.before(now)) {
                return candidate.getTimeInMillis();
            }
        }
        return -1;
    }

    static String toDateStr(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static boolean safeEq(String a, String b) {
        return (a == null ? "" : a).equals(b == null ? "" : b);
    }

    static long dateTimeToMillis(String date, String time) {
        if (date == null || time == null) return Long.MAX_VALUE;
        try {
            String[] dp = date.split("-");
            int[] tp = parseTimeParts(time);
            Calendar c = Calendar.getInstance();
            c.set(Integer.parseInt(dp[0]), Integer.parseInt(dp[1]) - 1, Integer.parseInt(dp[2]), tp[0], tp[1], 0);
            c.set(Calendar.MILLISECOND, 0);
            return c.getTimeInMillis();
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    static String dayAbbrevForDate(String dateStr) {
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar c = Calendar.getInstance();
            c.setTime(fmt.parse(dateStr));
            return DAY_LABELS[c.get(Calendar.DAY_OF_WEEK) - 1];
        } catch (Exception e) {
            return "MON";
        }
    }

    static Calendar getStartOfWeek(Calendar date) {
        Calendar d = (Calendar) date.clone();
        int day = d.get(Calendar.DAY_OF_WEEK);
        int diff = day == Calendar.SUNDAY ? -6 : Calendar.MONDAY - day;
        d.add(Calendar.DAY_OF_MONTH, diff);
        d.set(Calendar.HOUR_OF_DAY, 0);
        d.set(Calendar.MINUTE, 0);
        d.set(Calendar.SECOND, 0);
        d.set(Calendar.MILLISECOND, 0);
        return d;
    }

    static String formatWeekLabel(Calendar start, Calendar end) {
        String[] months = {"January","February","March","April","May","June","July","August",
                "September","October","November","December"};
        String startMonth = months[start.get(Calendar.MONTH)];
        String endMonth = months[end.get(Calendar.MONTH)];
        if (start.get(Calendar.MONTH) == end.get(Calendar.MONTH)) {
            return startMonth + " " + start.get(Calendar.DAY_OF_MONTH) + " - " + end.get(Calendar.DAY_OF_MONTH)
                    + ", " + end.get(Calendar.YEAR);
        }
        return startMonth + " " + start.get(Calendar.DAY_OF_MONTH) + " - " + endMonth + " "
                + end.get(Calendar.DAY_OF_MONTH) + ", " + end.get(Calendar.YEAR);
    }

    public static void load(Callback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            callback.onResult(new ArrayList<>(), new ArrayList<>(), null, 0, 0);
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).get().addOnSuccessListener(userDoc -> {
            if (!userDoc.exists()) {
                callback.onResult(new ArrayList<>(), new ArrayList<>(), null, 0, 0);
                return;
            }

            String nameToMatch = fullNameKey(userDoc);

            db.collection("rooms").get().addOnSuccessListener(roomsSnap -> {
                Map<String, String> roomNames = new HashMap<>();
                for (DocumentSnapshot r : roomsSnap.getDocuments()) roomNames.put(r.getId(), r.getString("roomName"));

                db.collectionGroup("schedules").get().addOnSuccessListener(schedSnap -> {
                    List<DocumentSnapshot> matched = matchSchedules(schedSnap.getDocuments(), nameToMatch);
                    List<DocumentSnapshot> latestSchedules = latestTermSchedules(matched);
                    String termLabel = termLabelFor(latestSchedules);

                    db.collection("facultySchedules").whereEqualTo("userId", uid).get()
                            .addOnSuccessListener(onlineSnap -> {
                                List<DocumentSnapshot> onlineSchedules = onlineSnap.getDocuments();

                                // NEW: fetch events for conflict-override, matching WeeklySchedule's logic
                                db.collection("events").get().addOnSuccessListener(eventsSnap -> {

                                    db.collection("roomReleases").get().addOnSuccessListener(releaseSnap -> {
                                        Set<String> releasedKeys = new HashSet<>();
                                        for (DocumentSnapshot d : releaseSnap.getDocuments()) {
                                            if (uid.equals(d.getString("releasedBy"))) {
                                                releasedKeys.add(d.getString("scheduleId") + "_" + d.getString("date"));
                                            }
                                        }

                                        db.collection("roomReassignments").get().addOnSuccessListener(reassignSnap -> {
                                            Set<String> awayKeys = new HashSet<>();
                                            List<DocumentSnapshot> reassignedInto = new ArrayList<>();
                                            for (DocumentSnapshot d : reassignSnap.getDocuments()) {
                                                String status = d.getString("status");
                                                String facultyId = d.getString("facultyId");
                                                if (status == null || !status.equalsIgnoreCase("approved")) continue;
                                                if (!uid.equals(facultyId)) continue;
                                                if (d.getString("oldRoomId") != null) {
                                                    awayKeys.add(d.getString("scheduleId") + "_" + d.getString("date"));
                                                }
                                                reassignedInto.add(d);
                                            }

                                            db.collection("reservationRequests").get().addOnSuccessListener(resSnap -> {
                                                List<DocumentSnapshot> approvedReservations = new ArrayList<>();
                                                for (DocumentSnapshot r : resSnap.getDocuments()) {
                                                    String status = r.getString("status");
                                                    if (status == null || !status.equalsIgnoreCase("approved")) continue;
                                                    boolean isOwnerById = uid.equals(r.getString("userId")) || uid.equals(r.getString("createdBy"));
                                                    boolean isOwnerByName = normalizeName(r.getString("facultyName")).equals(nameToMatch);
                                                    if (isOwnerById || isOwnerByName) approvedReservations.add(r);
                                                }

                                                buildTodayItems(latestSchedules, onlineSchedules, roomNames, releasedKeys, awayKeys,
                                                        reassignedInto, approvedReservations, eventsSnap.getDocuments(), termLabel, callback);
                                            });
                                        });
                                    });
                                });
                            });
                });
            });
        });
    }

    static void buildTodayItems(List<DocumentSnapshot> latestSchedules, List<DocumentSnapshot> onlineSchedules,
                                Map<String, String> roomNames, Set<String> releasedKeys, Set<String> awayKeys,
                                List<DocumentSnapshot> reassignedInto, List<DocumentSnapshot> approvedReservations,
                                List<DocumentSnapshot> events, String termLabel, Callback callback) {

        Calendar now = Calendar.getInstance();
        String todayStr = toDateStr(now);
        int currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);

        List<ScheduleItem> scheduleCandidates = new ArrayList<>();

        for (DocumentSnapshot s : latestSchedules) {
            String day = s.getString("day");
            String startTime = s.getString("startTime");
            String endTime = s.getString("endTime");
            long occurrence = getNextOccurrenceMillis(day, startTime, now);
            if (occurrence == -1) continue;

            Calendar occCal = Calendar.getInstance();
            occCal.setTimeInMillis(occurrence);
            String dateStr = toDateStr(occCal);
            String scheduleId = s.getId();
            String key = scheduleId + "_" + dateStr;
            if (releasedKeys.contains(key)) continue;
            if (awayKeys.contains(key)) continue;

            String roomId = s.getReference().getParent().getParent() != null
                    ? s.getReference().getParent().getParent().getId() : null;

            ScheduleItem item = new ScheduleItem();
            item.id = scheduleId;
            item.kind = "schedule";
            item.subject = s.getString("subject");
            item.roomName = roomId != null ? roomNames.get(roomId) : null;
            item.section = s.getString("section");
            item.startTime = startTime;
            item.endTime = endTime;
            item.date = dateStr;
            item.occurrenceMillis = occurrence;
            item.isToday = dateStr.equals(todayStr);
            item.faculty = s.getString("faculty");
            scheduleCandidates.add(item);
        }

        // NEW: conflict-override — a room activity overlapping a schedule (same date) hides that schedule item
        List<ScheduleItem> allItems = new ArrayList<>();
        for (ScheduleItem sched : scheduleCandidates) {
            boolean overridden = false;
            for (DocumentSnapshot e : events) {
                String eDate = e.getString("date");
                if (eDate == null || !eDate.equals(sched.date)) continue;
                if (overlap(e.getString("startTime"), e.getString("endTime"), sched.startTime, sched.endTime)) {
                    overridden = true;
                    break;
                }
            }
            if (!overridden) allItems.add(sched);
        }

        // Add room activities that are today and overlap the faculty's own rooms/time — matching web's activityItems
        for (DocumentSnapshot e : events) {
            String eDate = e.getString("date");
            if (eDate == null || !eDate.equals(todayStr)) continue;
            boolean relevant = false;
            for (ScheduleItem sched : scheduleCandidates) {
                if (sched.date.equals(eDate) && overlap(e.getString("startTime"), e.getString("endTime"), sched.startTime, sched.endTime)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant) continue;

            ScheduleItem item = new ScheduleItem();
            item.id = e.getId();
            item.kind = "event";
            String title = e.getString("title");
            String purpose = e.getString("purpose");
            item.subject = title != null ? title : (purpose != null ? purpose : "Room Activity");
            item.roomName = e.getString("roomName");
            item.startTime = e.getString("startTime");
            item.endTime = e.getString("endTime");
            item.date = eDate;
            item.occurrenceMillis = dateTimeToMillis(eDate, e.getString("startTime"));
            item.isToday = true;
            allItems.add(item);
        }

        for (DocumentSnapshot r : onlineSchedules) {
            // (online schedules unaffected by room conflicts, keep as-is)
        }

        for (DocumentSnapshot s : onlineSchedules) {
            String day = s.getString("day");
            String startTime = s.getString("startTime");
            String endTime = s.getString("endTime");
            long occurrence = getNextOccurrenceMillis(day, startTime, now);
            if (occurrence == -1) continue;

            Calendar occCal = Calendar.getInstance();
            occCal.setTimeInMillis(occurrence);
            String dateStr = toDateStr(occCal);

            ScheduleItem item = new ScheduleItem();
            item.id = s.getId();
            item.kind = "faculty-online";
            item.subject = s.getString("subject");
            item.roomName = "Online";
            item.section = s.getString("section");
            item.startTime = startTime;
            item.endTime = endTime;
            item.date = dateStr;
            item.occurrenceMillis = occurrence;
            item.isToday = dateStr.equals(todayStr);
            item.faculty = s.getString("facultyName");
            allItems.add(item);
        }

        for (DocumentSnapshot r : reassignedInto) {
            String date = r.getString("date");
            String startTime = r.getString("startTime");
            String endTime = r.getString("endTime");
            long occurrence = dateTimeToMillis(date, startTime);
            if (occurrence < now.getTimeInMillis()) continue;

            ScheduleItem item = new ScheduleItem();
            item.id = r.getId();
            item.kind = "reassignment";
            item.subject = (r.getString("courseTitle") != null ? r.getString("courseTitle") : "Class") + " (Moved)";
            item.roomName = r.getString("newRoomName");
            item.startTime = startTime;
            item.endTime = endTime;
            item.date = date;
            item.occurrenceMillis = occurrence;
            item.isToday = todayStr.equals(date);
            item.faculty = r.getString("facultyName");
            item.originalRoom = r.getString("oldRoomName");
            allItems.add(item);
        }

        for (DocumentSnapshot r : approvedReservations) {
            String date = r.getString("date");
            String startTime = r.getString("startTime");
            String endTime = r.getString("endTime");
            long occurrence = dateTimeToMillis(date, startTime);
            if (occurrence < now.getTimeInMillis()) continue;

            ScheduleItem item = new ScheduleItem();
            item.id = r.getId();
            item.kind = "reservation";
            String courseTitle = r.getString("courseTitle");
            String purpose = r.getString("purpose");
            item.subject = courseTitle != null ? courseTitle : (purpose != null ? purpose : "Reservation");
            item.roomName = r.getString("roomName");
            item.startTime = startTime;
            item.endTime = endTime;
            item.date = date;
            item.occurrenceMillis = occurrence;
            item.isToday = todayStr.equals(date);
            item.faculty = r.getString("facultyName");
            allItems.add(item);
        }

        Collections.sort(allItems, Comparator.comparingLong(a -> a.occurrenceMillis));

        List<ScheduleItem> todaysItems = new ArrayList<>();
        for (ScheduleItem item : allItems) {
            if (!item.isToday) continue;
            int[] sh = parseTimeParts(item.startTime);
            int[] eh = parseTimeParts(item.endTime);
            int startMin = sh[0] * 60 + sh[1];
            int endMin = eh[0] * 60 + eh[1];
            item.status = currentMinutes >= startMin && currentMinutes < endMin ? "ONGOING"
                    : currentMinutes < startMin ? "UPCOMING" : "COMPLETED";
            todaysItems.add(item);
        }
        Collections.sort(todaysItems, Comparator.comparing(a -> parseTimeParts(a.startTime)[0] * 60 + parseTimeParts(a.startTime)[1]));

        List<ScheduleItem> upcomingItems = new ArrayList<>();
        for (ScheduleItem item : allItems) {
            if (item.occurrenceMillis <= now.getTimeInMillis()) continue;

            // Defensive: skip anything dated today whose end time has already passed
            if (item.date != null && item.date.equals(todayStr)) {
                int[] eh = parseTimeParts(item.endTime);
                int endMin = eh[0] * 60 + eh[1];
                if (currentMinutes >= endMin) continue;
            }

            upcomingItems.add(item);
            if (upcomingItems.size() >= 5) break;
        }

        Set<String> roomsUsedSet = new HashSet<>();
        for (ScheduleItem item : allItems) if (item.roomName != null) roomsUsedSet.add(item.roomName);

        callback.onResult(todaysItems, upcomingItems, termLabel, latestSchedules.size() + onlineSchedules.size(), roomsUsedSet.size());
    }

    public static void loadWeek(int weekOffset, WeekCallback callback) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            callback.onResult(new HashMap<>(), "", null);
            return;
        }

        Calendar monday = getStartOfWeek(Calendar.getInstance());
        monday.add(Calendar.DAY_OF_MONTH, weekOffset * 7);
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_MONTH, 6);

        String[] weekDates = new String[7];
        Calendar cursor = (Calendar) monday.clone();
        for (int i = 0; i < 7; i++) {
            weekDates[i] = toDateStr(cursor);
            cursor.add(Calendar.DAY_OF_MONTH, 1);
        }

        String weekLabel = formatWeekLabel(monday, sunday);
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        callback.onResult(new HashMap<>(), weekLabel, null);
                        return;
                    }
                    String nameToMatch = fullNameKey(userDoc);

                    db.collectionGroup("schedules").get()
                            .addOnSuccessListener(schedSnap -> {
                                List<DocumentSnapshot> matched = matchSchedules(schedSnap.getDocuments(), nameToMatch);
                                List<DocumentSnapshot> latestSchedules = latestTermSchedules(matched);
                                String termLabel = termLabelFor(latestSchedules);

                                db.collection("facultySchedules").whereEqualTo("userId", uid).get()
                                        .addOnSuccessListener(onlineSnap -> {
                                            List<DocumentSnapshot> onlineSchedules = onlineSnap.getDocuments();

                                            db.collection("rooms").get()
                                                    .addOnSuccessListener(roomsSnap -> {
                                                        Map<String, String> roomNames = new HashMap<>();
                                                        for (DocumentSnapshot r : roomsSnap.getDocuments()) roomNames.put(r.getId(), r.getString("roomName"));

                                                        db.collection("events").get()
                                                                .addOnSuccessListener(eventSnap ->
                                                                        db.collection("reservationRequests").get()
                                                                                .addOnSuccessListener(resSnap ->
                                                                                        db.collection("roomReassignments").get()
                                                                                                .addOnSuccessListener(reassignSnap ->
                                                                                                        db.collection("roomReleases").get()
                                                                                                                .addOnSuccessListener(releaseSnap ->
                                                                                                                        buildWeekItems(latestSchedules, onlineSchedules, roomNames, eventSnap.getDocuments(),
                                                                                                                                resSnap.getDocuments(), reassignSnap.getDocuments(), releaseSnap.getDocuments(),
                                                                                                                                weekDates, uid, nameToMatch, weekLabel, termLabel, callback))
                                                                                                                .addOnFailureListener(e -> callback.onError("roomReleases: " + e.getMessage())))
                                                                                                .addOnFailureListener(e -> callback.onError("roomReassignments: " + e.getMessage())))
                                                                                .addOnFailureListener(e -> callback.onError("reservationRequests: " + e.getMessage())))
                                                                .addOnFailureListener(e -> callback.onError("events: " + e.getMessage()));
                                                    })
                                                    .addOnFailureListener(e -> callback.onError("rooms: " + e.getMessage()));
                                        })
                                        .addOnFailureListener(e -> callback.onError("facultySchedules: " + e.getMessage()));
                            })
                            .addOnFailureListener(e -> callback.onError("schedules collectionGroup: " + e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onError("users: " + e.getMessage()));
    }

    static void buildWeekItems(List<DocumentSnapshot> schedules, List<DocumentSnapshot> onlineSchedules,
                               Map<String, String> roomNames, List<DocumentSnapshot> events, List<DocumentSnapshot> reservations,
                               List<DocumentSnapshot> reassignments, List<DocumentSnapshot> releases, String[] weekDates,
                               String uid, String nameToMatch, String weekLabel, String termLabel, WeekCallback callback) {

        Map<String, List<ScheduleItem>> byDay = new HashMap<>();
        for (String d : MON_FIRST) byDay.put(d, new ArrayList<>());

        Set<String> weekDateSet = new HashSet<>();
        Collections.addAll(weekDateSet, weekDates);

        Set<String> releasedKeys = new HashSet<>();
        for (DocumentSnapshot r : releases) {
            String scheduleId = r.getString("scheduleId");
            String date = r.getString("date");
            if (scheduleId != null && date != null) releasedKeys.add(scheduleId + "_" + date);
        }

        // NEW: build reassigned-away keys (schedule occurrence moved elsewhere this date)
        Set<String> reassignedKeys = new HashSet<>();
        for (DocumentSnapshot r : reassignments) {
            String status = r.getString("status");
            if (status == null || !status.equalsIgnoreCase("approved")) continue;
            String scheduleId = r.getString("scheduleId");
            String date = r.getString("date");
            if (scheduleId != null && date != null) reassignedKeys.add(scheduleId + "_" + date);
        }

        // Build schedule items first, but hold them so we can check conflicts before adding to byDay
        List<ScheduleItem> scheduleItems = new ArrayList<>();
        for (DocumentSnapshot s : schedules) {
            String day = s.getString("day");
            if (day == null || !byDay.containsKey(day)) continue;

            int dayIdx = -1;
            for (int i = 0; i < MON_FIRST.length; i++) if (MON_FIRST[i].equals(day)) dayIdx = i;
            if (dayIdx == -1) continue;

            String dateStr = weekDates[dayIdx];
            String scheduleId = s.getId();
            String occurrenceKey = scheduleId + "_" + dateStr;
            if (releasedKeys.contains(occurrenceKey)) continue;
            if (reassignedKeys.contains(occurrenceKey)) continue; // NEW: skip reassigned-away occurrences

            String roomId = s.getReference().getParent().getParent() != null
                    ? s.getReference().getParent().getParent().getId() : null;

            ScheduleItem item = new ScheduleItem();
            item.id = scheduleId;
            item.kind = "schedule";
            item.subject = s.getString("subject");
            item.roomName = roomId != null ? roomNames.get(roomId) : null;
            item.section = s.getString("section");
            item.startTime = s.getString("startTime");
            item.endTime = s.getString("endTime");
            item.date = dateStr;
            item.faculty = s.getString("faculty");
            scheduleItems.add(item);
        }

        // NEW: build activity items separately, checking overlap against scheduleItems
        List<ScheduleItem> activityItems = new ArrayList<>();
        Set<String> overriddenScheduleIds = new HashSet<>();
        for (DocumentSnapshot e : events) {
            String date = e.getString("date");
            if (date == null || !weekDateSet.contains(date)) continue;
            String dayAbbrev = dayAbbrevForDate(date);

            ScheduleItem item = new ScheduleItem();
            item.id = e.getId();
            item.kind = "event";
            String title = e.getString("title");
            String purpose = e.getString("purpose");
            item.subject = title != null ? title : (purpose != null ? purpose : "Room Activity");
            item.roomName = e.getString("roomName");
            item.startTime = e.getString("startTime");
            item.endTime = e.getString("endTime");
            item.date = date;

            for (ScheduleItem sched : scheduleItems) {
                if (!dayAbbrev.equals(sched.date != null ? dayAbbrevForDate(sched.date) : "")) continue;
                if (!sched.date.equals(date)) continue;
                if (overlap(item.startTime, item.endTime, sched.startTime, sched.endTime)) {
                    overriddenScheduleIds.add(sched.id + "_" + sched.date);
                    break;
                }
            }

            activityItems.add(item);
            byDay.getOrDefault(dayAbbrev, new ArrayList<>()).add(item);
        }

        // Add schedule items EXCEPT the ones overridden by a conflicting activity
        for (ScheduleItem sched : scheduleItems) {
            String key = sched.id + "_" + sched.date;
            if (overriddenScheduleIds.contains(key)) continue; // NEW: hide, activity takes precedence
            byDay.get(dayAbbrevForDate(sched.date)).add(sched);
        }

        for (DocumentSnapshot s : onlineSchedules) {
            String day = s.getString("day");
            if (day == null || !byDay.containsKey(day)) continue;

            int dayIdx = -1;
            for (int i = 0; i < MON_FIRST.length; i++) if (MON_FIRST[i].equals(day)) dayIdx = i;
            if (dayIdx == -1) continue;

            String dateStr = weekDates[dayIdx];

            ScheduleItem item = new ScheduleItem();
            item.id = s.getId();
            item.kind = "faculty-online";
            item.subject = s.getString("subject");
            item.roomName = "Online";
            item.section = s.getString("section");
            item.startTime = s.getString("startTime");
            item.endTime = s.getString("endTime");
            item.date = dateStr;
            item.faculty = s.getString("facultyName");
            byDay.get(day).add(item);
        }

        for (DocumentSnapshot r : reservations) {
            String status = r.getString("status");
            if (status == null || !status.equalsIgnoreCase("approved")) continue;
            String date = r.getString("date");
            if (date == null || !weekDateSet.contains(date)) continue;

            boolean isOwnerById = uid.equals(r.getString("userId")) || uid.equals(r.getString("createdBy"));
            boolean isOwnerByName = normalizeName(r.getString("facultyName")).equals(nameToMatch);
            if (!isOwnerById && !isOwnerByName) continue;

            String dayAbbrev = dayAbbrevForDate(date);
            ScheduleItem item = new ScheduleItem();
            item.id = r.getId();
            item.kind = "reservation";
            String courseTitle = r.getString("courseTitle");
            String purpose = r.getString("purpose");
            item.subject = courseTitle != null ? courseTitle : (purpose != null ? purpose : "Reservation");
            item.roomName = r.getString("roomName");
            item.startTime = r.getString("startTime");
            item.endTime = r.getString("endTime");
            item.date = date;
            byDay.getOrDefault(dayAbbrev, new ArrayList<>()).add(item);
        }

        for (DocumentSnapshot r : reassignments) {
            String status = r.getString("status");
            String facultyId = r.getString("facultyId");
            if (status == null || !status.equalsIgnoreCase("approved") || !uid.equals(facultyId)) continue;
            String date = r.getString("date");
            if (date == null || !weekDateSet.contains(date)) continue;

            String dayAbbrev = dayAbbrevForDate(date);
            ScheduleItem item = new ScheduleItem();
            item.id = r.getId();
            item.kind = "reassignment";
            String courseTitle = r.getString("courseTitle");
            item.subject = (courseTitle != null ? courseTitle : "Class") + " (Moved)";
            item.roomName = r.getString("newRoomName");
            item.originalRoom = r.getString("oldRoomName");
            item.startTime = r.getString("startTime");
            item.endTime = r.getString("endTime");
            item.date = date;
            byDay.getOrDefault(dayAbbrev, new ArrayList<>()).add(item);
        }

        for (String d : MON_FIRST) {
            Collections.sort(byDay.get(d), Comparator.comparingInt(i ->
                    parseTimeParts(i.startTime)[0] * 60 + parseTimeParts(i.startTime)[1]));
        }

        callback.onResult(byDay, weekLabel, termLabel);
    }

    static String fullNameKey(DocumentSnapshot userDoc) {
        String first = userDoc.getString("firstName");
        String last = userDoc.getString("lastName");
        String mi = userDoc.getString("middleInitial");
        return normalizeName((last != null ? last : "") + ", " + (first != null ? first : "")
                + (mi != null && !mi.isEmpty() ? " " + mi : ""));
    }

    static List<DocumentSnapshot> matchSchedules(List<DocumentSnapshot> allSchedules, String nameToMatch) {
        List<DocumentSnapshot> matched = new ArrayList<>();
        for (DocumentSnapshot s : allSchedules) {
            Boolean initialized = s.getBoolean("initialized");
            if (Boolean.TRUE.equals(initialized)) continue;
            String faculty = s.getString("faculty");
            if (faculty == null || !normalizeName(faculty).equals(nameToMatch)) continue;
            matched.add(s);
        }
        return matched;
    }

    static boolean overlap(String aStart, String aEnd, String bStart, String bEnd) {
        int[] as = parseTimeParts(aStart), ae = parseTimeParts(aEnd);
        int[] bs = parseTimeParts(bStart), be = parseTimeParts(bEnd);
        int aStartMin = as[0]*60+as[1], aEndMin = ae[0]*60+ae[1];
        int bStartMin = bs[0]*60+bs[1], bEndMin = be[0]*60+be[1];
        return aStartMin < bEndMin && aEndMin > bStartMin;
    }
    static List<DocumentSnapshot> latestTermSchedules(List<DocumentSnapshot> matched) {
        DocumentSnapshot latest = null;
        int bestY = -1, bestS = -1;
        for (DocumentSnapshot s : matched) {
            int y = schoolYearStart(s.getString("schoolYear"));
            int sr = semesterRank(s.getString("semester"));
            if (y > bestY || (y == bestY && sr > bestS)) { bestY = y; bestS = sr; latest = s; }
        }
        List<DocumentSnapshot> result = new ArrayList<>();
        if (latest == null) return result;
        String latestSY = latest.getString("schoolYear");
        String latestSem = latest.getString("semester");
        for (DocumentSnapshot s : matched) {
            if (safeEq(s.getString("schoolYear"), latestSY) && safeEq(s.getString("semester"), latestSem)) {
                result.add(s);
            }
        }
        return result;
    }

    static String termLabelFor(List<DocumentSnapshot> latestSchedules) {
        if (latestSchedules.isEmpty()) return null;
        DocumentSnapshot s = latestSchedules.get(0);
        String sem = s.getString("semester");
        String sy = s.getString("schoolYear");
        return (sem != null ? sem : "") + " Semester, " + (sy != null ? sy : "");
    }
}