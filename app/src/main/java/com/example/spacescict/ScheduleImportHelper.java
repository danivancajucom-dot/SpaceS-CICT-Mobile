package com.example.spacescict;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Converts the common schedule spreadsheet/PDF layout into the same Firestore
 * schedule documents used by ScheduleLoader. It intentionally keeps parsing
 * local so imported files are not uploaded to a third-party service.
 */
public final class ScheduleImportHelper {

    public interface Callback {
        void onSuccess(int importedRows);
        /** Room schedules, online classes, and duplicates skipped. */
        default void onSuccess(int roomSchedules, int onlineClasses, int skipped) {
            onSuccess(roomSchedules + onlineClasses);
        }
        void onError(String message);
    }

    private static class ScheduleRow {
        String subject, room, day, startTime, endTime, section, faculty;
    }

    private ScheduleImportHelper() {}

    public static void importSchedule(Context context, Uri uri, Callback callback) {
        new Thread(() -> {
            try {
                String name = String.valueOf(uri).toLowerCase(Locale.US);
                String mime = context.getContentResolver().getType(uri);
                List<String[]> rows;
                if (name.endsWith(".pdf") || (mime != null && mime.toLowerCase(Locale.US).contains("pdf"))) {
                    rows = parsePdf(context, uri);
                } else {
                    rows = parseExcel(context, uri);
                }
                int[] counts = writeRowsDetailed(context, rows);
                main(context, () -> callback.onSuccess(counts[0], counts[1], counts[2]));
            } catch (Exception error) {
                main(context, () -> callback.onError(error.getMessage() == null
                        ? "The file could not be read." : error.getMessage()));
            }
        }).start();
    }

    private static List<String[]> parseExcel(Context context, Uri uri) throws Exception {
        Map<String, byte[]> entries = readZipEntries(context, uri);
        byte[] sheet = entries.get("xl/worksheets/sheet1.xml");
        if (sheet == null) throw new IllegalArgumentException("The Excel file has no first worksheet.");

        List<String> sharedStrings = new ArrayList<>();
        byte[] shared = entries.get("xl/sharedStrings.xml");
        if (shared != null) {
            Matcher items = Pattern.compile("<si\\b[^>]*>(.*?)</si>", Pattern.DOTALL)
                    .matcher(new String(shared, StandardCharsets.UTF_8));
            while (items.find()) sharedStrings.add(xmlText(items.group(1)));
        }

        List<String[]> rows = new ArrayList<>();
        Matcher rowMatcher = Pattern.compile("<row\\b[^>]*>(.*?)</row>", Pattern.DOTALL)
                .matcher(new String(sheet, StandardCharsets.UTF_8));
        while (rowMatcher.find()) {
            String body = rowMatcher.group(1);
            Map<Integer, String> values = new HashMap<>();
            Matcher cellMatcher = Pattern.compile(
                    "<c\\b[^>]*\\br=\"([A-Z]+)\\d+\"([^>]*)>(.*?)</c>",
                    Pattern.DOTALL).matcher(body);
            int maxColumn = -1;
            while (cellMatcher.find()) {
                int column = columnIndex(cellMatcher.group(1));
                String attributes = cellMatcher.group(2);
                String cellBody = cellMatcher.group(3);
                Matcher valueMatcher = Pattern.compile("<v\\b[^>]*>(.*?)</v>", Pattern.DOTALL)
                        .matcher(cellBody);
                String value = valueMatcher.find() ? xmlText(valueMatcher.group(1)) : xmlText(cellBody);
                if (attributes.contains("t=\"s\"")) {
                    try {
                        int stringIndex = Integer.parseInt(value);
                        value = stringIndex < sharedStrings.size() ? sharedStrings.get(stringIndex) : "";
                    } catch (NumberFormatException ignored) {
                        value = "";
                    }
                } else if (attributes.contains("t=\"inlineStr\"")) {
                    value = xmlText(cellBody);
                }
                values.put(column, value);
                maxColumn = Math.max(maxColumn, column);
            }
            if (maxColumn >= 0) {
                String[] row = new String[maxColumn + 1];
                for (int i = 0; i <= maxColumn; i++) row[i] = values.containsKey(i) ? values.get(i) : "";
                rows.add(row);
            }
        }
        return rows;
    }

    private static List<String[]> parsePdf(Context context, Uri uri) throws Exception {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             PDDocument document = PDDocument.load(input)) {
            String text = new PDFTextStripper().getText(document);
            List<String[]> rows = new ArrayList<>();
            for (String line : text.split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                String[] columns = trimmed.contains("\t")
                        ? trimmed.split("\\t+")
                        : trimmed.split("\\s{2,}");
                if (columns.length >= 3) rows.add(trimmedColumns(columns));
            }
            return rows;
        }
    }

    private static String[] trimmedColumns(String[] values) {
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = values[i].trim();
        return result;
    }

    private static int writeRows(Context context, List<String[]> rawRows) throws Exception {
        int[] counts = writeRowsDetailed(context, rawRows);
        return counts[0] + counts[1];
    }

    /**
     * Writes the parsed rows the same way FacultySchedule's ImportScheduleModal does.
     *
     * Rows WITH a room  -> rooms/{roomId}/schedules, stamped with the faculty's
     *                      current term and isActive=false, so the entry stays
     *                      dormant until the registrar activates that term.
     * Rows WITHOUT a room -> facultySchedules as an online class owned by this user.
     *
     * The old version wrote everything into rooms/{id}/schedules with no term
     * fields at all, so imported classes never matched the active-term filter and
     * online classes were lost completely.
     *
     * @return {roomSchedulesAdded, onlineClassesAdded, duplicatesSkipped}
     */
    static int[] writeRowsDetailed(Context context, List<String[]> rawRows) throws Exception {
        if (rawRows.size() < 2) throw new IllegalArgumentException(
                "No schedule rows were found. Include a header row and schedule data.");

        Map<String, Integer> headers = headerIndexes(rawRows.get(0));
        List<ScheduleRow> schedules = new ArrayList<>();
        for (int i = 1; i < rawRows.size(); i++) {
            ScheduleRow row = toScheduleRow(rawRows.get(i), headers);
            if (row != null) schedules.add(row);
        }
        if (schedules.isEmpty()) throw new IllegalArgumentException(
                "No valid schedule entries were found. Check the subject, day, and time columns.");

        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) throw new IllegalStateException("You are signed out. Please log in again.");

        // The import always belongs to the signed-in faculty, whatever the sheet says.
        DocumentSnapshot userDoc = Tasks.await(firestore.collection("users").document(uid).get());
        String firstName = userDoc.getString("firstName");
        String lastName = userDoc.getString("lastName");
        String facultyName = ((firstName != null ? firstName : "") + " "
                + (lastName != null ? lastName : "")).trim();
        if (facultyName.isEmpty()) facultyName = "Faculty";

        Map<String, String> roomIds = new HashMap<>();
        List<DocumentSnapshot> roomDocs =
                Tasks.await(firestore.collection("rooms").get()).getDocuments();
        for (DocumentSnapshot room : roomDocs) {
            String roomName = room.getString("roomName");
            if (roomName != null) roomIds.put(ScheduleLoader.normalizeName(roomName), room.getId());
        }

        String[] term = resolveTerm(firestore, roomDocs, facultyName);
        String semester = term[0];
        String schoolYear = term[1];

        // Existing entries, so re-importing the same file does not duplicate rows.
        Set<String> existingRoomKeys = new HashSet<>();
        for (DocumentSnapshot schedule :
                Tasks.await(firestore.collectionGroup("schedules").get()).getDocuments()) {
            String parentRoom = parentRoomId(schedule);
            if (parentRoom == null) continue;
            existingRoomKeys.add(duplicateKey(parentRoom, schedule.getString("semester"),
                    schedule.getString("schoolYear"), schedule.getString("subject"),
                    schedule.getString("day"), schedule.getString("startTime"),
                    schedule.getString("endTime")));
        }

        Set<String> existingOnlineKeys = new HashSet<>();
        for (DocumentSnapshot online : Tasks.await(firestore.collection("facultySchedules")
                .whereEqualTo("userId", uid).get()).getDocuments()) {
            existingOnlineKeys.add(duplicateKey("online", online.getString("semester"),
                    online.getString("schoolYear"), online.getString("subject"),
                    online.getString("day"), online.getString("startTime"),
                    online.getString("endTime")));
        }

        int roomAdded = 0, onlineAdded = 0, skipped = 0;
        List<String> unknownRooms = new ArrayList<>();
        WriteBatch batch = firestore.batch();
        int batchCount = 0;

        for (ScheduleRow row : schedules) {
            boolean isOnline = row.room == null || row.room.trim().isEmpty();

            if (isOnline) {
                String key = duplicateKey("online", semester, schoolYear,
                        row.subject, row.day, row.startTime, row.endTime);
                if (!existingOnlineKeys.add(key)) { skipped++; continue; }

                Map<String, Object> data = new HashMap<>();
                data.put("userId", uid);
                data.put("facultyName", facultyName);
                data.put("subject", row.subject);
                data.put("section", row.section);
                data.put("day", row.day);
                data.put("startTime", row.startTime);
                data.put("endTime", row.endTime);
                data.put("semester", semester);
                data.put("schoolYear", schoolYear);
                data.put("isOnline", true);
                data.put("createdAt", Timestamp.now());
                batch.set(firestore.collection("facultySchedules").document(), data);
                onlineAdded++;
            } else {
                String roomId = roomIds.get(ScheduleLoader.normalizeName(row.room));
                if (roomId == null) {
                    if (!unknownRooms.contains(row.room)) unknownRooms.add(row.room);
                    continue;
                }

                String key = duplicateKey(roomId, semester, schoolYear,
                        row.subject, row.day, row.startTime, row.endTime);
                if (!existingRoomKeys.add(key)) { skipped++; continue; }

                Map<String, Object> data = new HashMap<>();
                data.put("subject", row.subject);
                data.put("roomName", row.room);
                data.put("section", row.section);
                data.put("faculty", facultyName);
                data.put("day", row.day);
                data.put("startTime", row.startTime);
                data.put("endTime", row.endTime);
                data.put("semester", semester);
                data.put("schoolYear", schoolYear);
                // Dormant until the registrar activates the term - matches the web.
                data.put("isActive", false);
                data.put("activeFrom", null);
                data.put("activeUntil", null);
                data.put("initialized", false);
                data.put("importedAt", Timestamp.now());
                data.put("importedBy", uid);
                batch.set(firestore.collection("rooms").document(roomId)
                        .collection("schedules").document(), data);
                roomAdded++;
            }

            batchCount++;
            if (batchCount == 400) {
                Tasks.await(batch.commit());
                batch = firestore.batch();
                batchCount = 0;
            }
        }

        if (batchCount > 0) Tasks.await(batch.commit());

        if (roomAdded == 0 && onlineAdded == 0) {
            if (skipped > 0) {
                throw new IllegalArgumentException(
                        "Every row in this file has already been imported.");
            }
            throw new IllegalArgumentException(unknownRooms.isEmpty()
                    ? "Nothing could be imported from this file."
                    : "None of these rooms exist in the system: "
                    + TextUtils.join(", ", unknownRooms));
        }

        return new int[]{roomAdded, onlineAdded, skipped};
    }

    /**
     * Finds the term this faculty is currently teaching, preferring a term the
     * registrar has activated, then the newest one. Falls back to the current
     * academic year when the faculty has no room schedules yet.
     */
    private static String[] resolveTerm(FirebaseFirestore firestore,
                                        List<DocumentSnapshot> roomDocs,
                                        String facultyName) throws Exception {
        String normalizedFaculty = ScheduleLoader.normalizeName(facultyName);
        String semester = "1st Semester";
        String schoolYear = "";
        int bestRank = -1;

        for (DocumentSnapshot schedule :
                Tasks.await(firestore.collectionGroup("schedules").get()).getDocuments()) {
            String faculty = schedule.getString("faculty");
            if (faculty == null) continue;
            if (!ScheduleLoader.normalizeName(faculty).equals(normalizedFaculty)) continue;
            String sem = schedule.getString("semester");
            String sy = schedule.getString("schoolYear");
            if (sem == null || sy == null) continue;

            int activeBonus = Boolean.TRUE.equals(schedule.getBoolean("isActive")) ? 1000 : 0;
            int rank = activeBonus + ScheduleLoader.schoolYearStart(sy) * 10
                    + ScheduleLoader.semesterRank(sem);
            if (rank > bestRank) {
                bestRank = rank;
                semester = sem;
                schoolYear = sy;
            }
        }

        if (schoolYear.isEmpty()) {
            int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            schoolYear = year + "-" + (year + 1);
            semester = "1st Semester";
        }
        return new String[]{semester, schoolYear};
    }

    private static String duplicateKey(String scope, String semester, String schoolYear,
                                       String subject, String day, String start, String end) {
        return (scope == null ? "" : scope) + "|"
                + (semester == null ? "" : semester) + "|"
                + (schoolYear == null ? "" : schoolYear) + "|"
                + (subject == null ? "" : subject.trim().toLowerCase(Locale.US)) + "|"
                + (day == null ? "" : day) + "|"
                + (start == null ? "" : start) + "|"
                + (end == null ? "" : end);
    }

    private static String parentRoomId(DocumentSnapshot schedule) {
        try {
            if (schedule.getReference().getParent().getParent() == null) return null;
            return schedule.getReference().getParent().getParent().getId();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Integer> headerIndexes(String[] row) {
        Map<String, Integer> result = new HashMap<>();
        for (int i = 0; i < row.length; i++) {
            String key = row[i].toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
            if (key.contains("subject") || key.contains("course")) result.put("subject", i);
            else if (key.contains("room")) result.put("room", i);
            else if (key.equals("day") || key.contains("weekday")) result.put("day", i);
            else if (key.contains("start")) result.put("start", i);
            else if (key.contains("end")) result.put("end", i);
            else if (key.contains("section")) result.put("section", i);
            else if (key.contains("faculty") || key.contains("instructor")) result.put("faculty", i);
        }
        return result;
    }

    private static ScheduleRow toScheduleRow(String[] values, Map<String, Integer> headers) {
        String subject = value(values, headers.get("subject"));
        String room = value(values, headers.get("room"));
        String day = normalizeDay(value(values, headers.get("day")));
        String start = normalizeTime(value(values, headers.get("start")));
        String end = normalizeTime(value(values, headers.get("end")));
        // A blank room is not an error - that is how the web marks an ONLINE class.
        if (subject.isEmpty() || day.isEmpty() || start.isEmpty() || end.isEmpty()) return null;
        ScheduleRow row = new ScheduleRow();
        row.subject = subject;
        row.room = room;
        row.day = day;
        row.startTime = start;
        row.endTime = end;
        row.section = value(values, headers.get("section"));
        row.faculty = value(values, headers.get("faculty"));
        return row;
    }

    private static String value(String[] values, Integer index) {
        return index == null || index < 0 || index >= values.length || values[index] == null
                ? "" : values[index].trim();
    }

    private static String normalizeDay(String value) {
        String day = value.toUpperCase(Locale.US).replaceAll("[^A-Z]", "");
        if (day.startsWith("MON")) return "MON";
        if (day.startsWith("TUE")) return "TUE";
        if (day.startsWith("WED")) return "WED";
        if (day.startsWith("THU")) return "THU";
        if (day.startsWith("FRI")) return "FRI";
        if (day.startsWith("SAT")) return "SAT";
        if (day.startsWith("SUN")) return "SUN";
        return "";
    }

    private static String normalizeTime(String value) {
        if (value.isEmpty()) return "";
        String time = value.trim().toUpperCase(Locale.US).replace(".", "");
        try {
            if (time.matches("\\d+(\\.\\d+)?")) {
                double excelFraction = Double.parseDouble(time);
                int minutes = (int) Math.round(excelFraction * 24 * 60) % (24 * 60);
                return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
            }
            String[] patterns = {"h:mm a", "hh:mm a", "H:mm", "HH:mm"};
            for (String pattern : patterns) {
                try {
                    java.util.Date parsed = new SimpleDateFormat(pattern, Locale.US).parse(time);
                    if (parsed != null) return new SimpleDateFormat("HH:mm", Locale.US).format(parsed);
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private static Map<String, byte[]> readZipEntries(Context context, Uri uri) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = zip.read(buffer)) != -1) output.write(buffer, 0, count);
                entries.put(entry.getName(), output.toByteArray());
            }
        }
        return entries;
    }

    private static int columnIndex(String letters) {
        int result = 0;
        for (int i = 0; i < letters.length(); i++) {
            result = result * 26 + (letters.charAt(i) - 'A' + 1);
        }
        return result - 1;
    }

    private static String xmlText(String input) {
        String value = input.replaceAll("<[^>]+>", "");
        return value.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    private static void main(Context context, Runnable runnable) {
        if (context instanceof Activity) ((Activity) context).runOnUiThread(runnable);
        else runnable.run();
    }
}