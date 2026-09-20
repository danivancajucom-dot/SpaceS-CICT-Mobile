package com.example.spacescict;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.text.InputFilter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RoomIssuesPage {

    private static final int PAGE_SIZE = 6;

    private final Context context;

    private final List<RoomIssueModel> allIssues = new ArrayList<>();
    private final List<RoomIssueModel> visibleIssues = new ArrayList<>();

    private final RoomIssueAdapter adapter;

    private CardView allTab;
    private CardView openTab;
    private CardView resolvedTab;
    private CardView urgentTab;

    private TextView allCount;
    private TextView openCount;
    private TextView resolvedCount;
    private TextView urgentCount;

    private TextView resultCount;
    private TextView pageInfo;

    private LinearLayout emptyState;
    private TextView emptyMessage;

    private EditText searchInput;

    private Spinner roomSpinner;
    private Spinner sortSpinner;

    private View previousButton;
    private View nextButton;

    private String activeTab = "all";
    private int page = 1;

    /** All rooms from the `rooms` collection: {roomId, roomName, floor}. */
    private final List<String[]> roomCatalog = new ArrayList<>();
    private String reporterName = "";
    private String reporterRole = "";

    private static final int MAX_PHOTOS = 5;

    /** Web-parity issue categories (SubmitIssueModal.jsx CATEGORIES). */
    private static final String[][] CATEGORIES_WITH_ICONS = {
            {"Electrical", String.valueOf(R.drawable.ic_electrical)},
            {"Plumbing / Leak", String.valueOf(R.drawable.ic_plumbing)},
            {"Network / Internet", String.valueOf(R.drawable.ic_network)},
            {"Equipment", String.valueOf(R.drawable.ic_video_label)},
            {"Air Conditioning", String.valueOf(R.drawable.ic_ac_unit)},
            {"Furniture", String.valueOf(R.drawable.ic_weekend)},
            {"Cleanliness", String.valueOf(R.drawable.ic_cleaning_services)},
            {"Security", String.valueOf(R.drawable.ic_security)},
            {"Other", String.valueOf(R.drawable.ic_other)}
    };

    /** Callback into the host Activity to launch the multi-image picker. */
    private final Runnable onPickPhotosRequested;

    /** Photos picked for the report dialog currently on screen (cleared per dialog). */
    private final List<Uri> pendingPhotoUris = new ArrayList<>();
    private LinearLayout activePhotoPreviewRow;
    private HorizontalScrollView activePhotoPreviewScroll;
    private TextView activePhotosLabel;
    private CardView activeAttachPhotosCard;

    public RoomIssuesPage(Context context, View view) {
        this(context, view, null);
    }

    public RoomIssuesPage(Context context, View view, Runnable onPickPhotosRequested) {

        this.context = context;
        this.onPickPhotosRequested = onPickPhotosRequested;

        RecyclerView recycler = view.findViewById(R.id.issuesRecycler);

        adapter = new RoomIssueAdapter(visibleIssues);

        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(adapter);

        allTab = view.findViewById(R.id.issuesTabAll);
        openTab = view.findViewById(R.id.issuesTabOpen);
        resolvedTab = view.findViewById(R.id.issuesTabResolved);
        urgentTab = view.findViewById(R.id.issuesTabUrgent);

        allCount = view.findViewById(R.id.issuesTabAllCount);
        openCount = view.findViewById(R.id.issuesTabOpenCount);
        resolvedCount = view.findViewById(R.id.issuesTabResolvedCount);
        urgentCount = view.findViewById(R.id.issuesTabUrgentCount);

        emptyState = view.findViewById(R.id.issueEmptyState);
        emptyMessage = view.findViewById(R.id.issueEmptyMessage);

        resultCount = view.findViewById(R.id.issueResultCount);
        pageInfo = view.findViewById(R.id.issuePageInfo);

        searchInput = view.findViewById(R.id.issueSearchInput);

        roomSpinner = view.findViewById(R.id.issueRoomSpinner);
        sortSpinner = view.findViewById(R.id.issueSortSpinner);

        previousButton = view.findViewById(R.id.issuePreviousButton);
        nextButton = view.findViewById(R.id.issueNextButton);

        View reportButton = view.findViewById(R.id.reportIssueButton);

        sortSpinner.setAdapter(
                new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        new String[]{
                                "Newest First",
                                "Oldest First",
                                "Severity (High to Low)"
                        }
                )
        );

        allTab.setOnClickListener(v -> setTab("all"));
        openTab.setOnClickListener(v -> setTab("open"));
        resolvedTab.setOnClickListener(v -> setTab("resolved"));
        urgentTab.setOnClickListener(v -> setTab("urgent"));

        reportButton.setOnClickListener(v -> showReportDialog());

        previousButton.setOnClickListener(v -> {
            if (page > 1) {
                page--;
                applyFilters();
            }
        });

        nextButton.setOnClickListener(v -> {
            page++;
            applyFilters();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                page = 1;
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        AdapterViewHelper.listen(roomSpinner, () -> {
            page = 1;
            applyFilters();
        });

        AdapterViewHelper.listen(sortSpinner, () -> {
            page = 1;
            applyFilters();
        });

        updateTabColors();
        loadRoomCatalog();
        loadReporterName();
        subscribe();
    }

    /**
     * The report dialog used to build its room list from the issues the faculty
     * had ALREADY reported, so a brand-new account had an empty dropdown and
     * could never file a first report. Read the real rooms collection instead.
     */
    private void loadRoomCatalog() {
        FirebaseFirestore.getInstance().collection("rooms").get()
                .addOnSuccessListener(snapshot -> {
                    roomCatalog.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String roomName = doc.getString("roomName");
                        if (roomName == null || roomName.trim().isEmpty()) continue;
                        String floor = doc.getString("floor");
                        roomCatalog.add(new String[]{doc.getId(), roomName.trim(), floor == null ? "" : floor});
                    }
                    Collections.sort(roomCatalog, (a, b) -> a[1].compareToIgnoreCase(b[1]));
                });
    }

    private void loadReporterName() {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String first = doc.getString("firstName");
                    String last = doc.getString("lastName");
                    String full = ((first != null ? first : "") + " "
                            + (last != null ? last : "")).trim();
                    reporterName = full.isEmpty() ? "Faculty" : full;
                    String role = doc.getString("role");
                    reporterRole = role == null ? "" : role;
                });
    }

    /** Called by the host Activity once the person finishes picking photos. */
    public void onPhotosPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        int remaining = MAX_PHOTOS - pendingPhotoUris.size();
        if (remaining <= 0) {
            Toast.makeText(context, "You can only attach up to " + MAX_PHOTOS + " photos.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        for (Uri uri : uris) {
            if (pendingPhotoUris.size() >= MAX_PHOTOS) break;
            pendingPhotoUris.add(uri);
        }
        refreshPhotoPreview();
    }

    private void refreshPhotoPreview() {
        if (activePhotoPreviewRow == null) return;

        activePhotoPreviewRow.removeAllViews();

        if (pendingPhotoUris.isEmpty()) {
            if (activePhotoPreviewScroll != null) activePhotoPreviewScroll.setVisibility(View.GONE);
        } else {
            if (activePhotoPreviewScroll != null) activePhotoPreviewScroll.setVisibility(View.VISIBLE);

            for (int i = 0; i < pendingPhotoUris.size(); i++) {
                final int index = i;
                Uri uri = pendingPhotoUris.get(i);

                FrameLayout cell = new FrameLayout(context);
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(dp(72), dp(72));
                cellParams.setMargins(0, 0, dp(8), 0);
                cell.setLayoutParams(cellParams);

                ImageView thumb = new ImageView(context);
                thumb.setLayoutParams(new FrameLayout.LayoutParams(dp(72), dp(72)));
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                thumb.setBackgroundResource(R.drawable.image_radius);
                Glide.with(context).load(uri).centerCrop().into(thumb);
                cell.addView(thumb);

                TextView remove = new TextView(context);
                remove.setText("\u2715");
                remove.setTextColor(Color.WHITE);
                remove.setTextSize(10);
                remove.setGravity(android.view.Gravity.CENTER);
                remove.setBackgroundResource(R.drawable.circle_danger);
                FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(20), dp(20));
                removeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
                removeParams.topMargin = dp(2);
                removeParams.rightMargin = dp(2);
                remove.setLayoutParams(removeParams);
                remove.setOnClickListener(v -> {
                    pendingPhotoUris.remove(index);
                    refreshPhotoPreview();
                });
                cell.addView(remove);

                activePhotoPreviewRow.addView(cell);
            }
        }

        if (activePhotosLabel != null) {
            activePhotosLabel.setText("PHOTOS (optional \u00b7 up to " + MAX_PHOTOS + " \u00b7 "
                    + pendingPhotoUris.size() + "/" + MAX_PHOTOS + ")");
        }
        if (activeAttachPhotosCard != null) {
            TextView attachBtn = (TextView) ((CardView) activeAttachPhotosCard).getChildAt(0);
            attachBtn.setText(pendingPhotoUris.isEmpty() ? "Attach Photos" : "Add More Photos");
            activeAttachPhotosCard.setVisibility(
                    pendingPhotoUris.size() >= MAX_PHOTOS ? View.GONE : View.VISIBLE);
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private void subscribe() {

        String uid = FirebaseAuth.getInstance().getUid();

        if (uid == null) {
            emptyMessage.setText("Sign in to view your reported issues.");
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("roomIssues")
                .whereEqualTo("reporterId", uid)
                .addSnapshotListener((snapshots, error) -> {

                    if (error != null) {
                        emptyMessage.setText("Could not load reports.");
                        emptyState.setVisibility(View.VISIBLE);
                        return;
                    }

                    allIssues.clear();

                    if (snapshots != null) {
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {

                            RoomIssueModel issue = new RoomIssueModel();

                            issue.id = doc.getId();
                            issue.roomName = doc.getString("roomName");
                            issue.category = doc.getString("category");
                            issue.description = doc.getString("description");
                            issue.severity = doc.getString("severity");
                            issue.status = doc.getString("status");
                            issue.createdAt = doc.getTimestamp("createdAt");
                            issue.imageUrl = doc.getString("photoUrl");
                            issue.reporterName = doc.getString("reporterName");
                            issue.clerkNotes = doc.getString("clerkNotes");

                            allIssues.add(issue);
                        }
                    }

                    refreshRooms();
                    applyFilters();
                });
    }

    private void refreshRooms() {

        String selected =
                roomSpinner.getSelectedItem() == null
                        ? "All Rooms"
                        : roomSpinner.getSelectedItem().toString();

        ArrayList<String> rooms = new ArrayList<>();

        rooms.add("All Rooms");

        for (RoomIssueModel issue : allIssues) {

            if (issue.roomName != null &&
                    !issue.roomName.trim().isEmpty() &&
                    !rooms.contains(issue.roomName)) {

                rooms.add(issue.roomName);
            }
        }

        if (rooms.size() > 1) {
            Collections.sort(rooms.subList(1, rooms.size()));
        }

        roomSpinner.setAdapter(
                new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        rooms
                )
        );

        int index = rooms.indexOf(selected);
        roomSpinner.setSelection(index < 0 ? 0 : index);
    }

    private void setTab(String tab) {
        activeTab = tab;
        page = 1;
        updateTabColors();
        applyFilters();
    }

    private void updateTabColors() {
        int active = Color.parseColor("#F97316");
        int inactive = Color.parseColor("#78716C");

        allCount.setTextColor("all".equals(activeTab) ? active : inactive);
        openCount.setTextColor("open".equals(activeTab) ? active : inactive);
        resolvedCount.setTextColor("resolved".equals(activeTab) ? active : inactive);
        urgentCount.setTextColor("urgent".equals(activeTab) ? active : inactive);
    }

    private void applyFilters() {
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.US);
        String room = roomSpinner.getSelectedItem() == null
                ? "All Rooms" : roomSpinner.getSelectedItem().toString();
        ArrayList<RoomIssueModel> filtered = new ArrayList<>();
        for (RoomIssueModel issue : allIssues) {
            boolean statusMatch = "all".equals(activeTab)
                    || ("open".equals(activeTab) && !"Resolved".equalsIgnoreCase(issue.status))
                    || ("resolved".equals(activeTab) && "Resolved".equalsIgnoreCase(issue.status))
                    || ("urgent".equals(activeTab) && "Urgent".equalsIgnoreCase(issue.severity)
                    && !"Resolved".equalsIgnoreCase(issue.status));
            boolean roomMatch = "All Rooms".equals(room) || room.equals(issue.roomName);
            boolean searchMatch = query.isEmpty() || issue.searchableText().contains(query);
            if (statusMatch && roomMatch && searchMatch) filtered.add(issue);
        }

        int sort = sortSpinner.getSelectedItemPosition();
        Comparator<RoomIssueModel> comparator = (a, b) ->
                timestamp(b.createdAt).compareTo(timestamp(a.createdAt));
        if (sort == 1) comparator = (a, b) ->
                timestamp(a.createdAt).compareTo(timestamp(b.createdAt));
        if (sort == 2) comparator = (a, b) ->
                severity(b.severity) - severity(a.severity);
        filtered.sort(comparator);

        int pages = Math.max(1, (filtered.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(1, Math.min(page, pages));
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, filtered.size());
        visibleIssues.clear();
        if (start < end) visibleIssues.addAll(filtered.subList(start, end));
        adapter.notifyDataSetChanged();

        resultCount.setText(filtered.size() + (filtered.size() == 1 ? " result" : " results"));
        emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        pageInfo.setText(filtered.isEmpty() ? "No reports to show"
                : "Showing " + (start + 1) + "-" + end + " of " + filtered.size());
        previousButton.setEnabled(page > 1);
        nextButton.setEnabled(page < pages);
        updateTabCounts();
    }

    private void updateTabCounts() {
        int open = 0;
        int resolved = 0;
        int urgent = 0;

        for (RoomIssueModel issue : allIssues) {
            if ("Resolved".equalsIgnoreCase(issue.status)) {
                resolved++;
            } else {
                open++;

                if ("Urgent".equalsIgnoreCase(issue.severity)) {
                    urgent++;
                }
            }
        }

        allCount.setText(String.valueOf(allIssues.size()));
        openCount.setText(String.valueOf(open));
        resolvedCount.setText(String.valueOf(resolved));
        urgentCount.setText(String.valueOf(urgent));
    }

    private static Long timestamp(Timestamp timestamp) {
        return timestamp == null ? 0L : timestamp.toDate().getTime();
    }

    private static int severity(String value) {
        if ("Urgent".equalsIgnoreCase(value)) return 4;
        if ("High".equalsIgnoreCase(value)) return 3;
        if ("Low".equalsIgnoreCase(value)) return 1;
        return 2;
    }

    private void showReportDialog() {

        pendingPhotoUris.clear();

        View content = LayoutInflater.from(context)
                .inflate(R.layout.dialog_report_issue, null, false);

        Spinner roomSpinner = content.findViewById(R.id.issueRoomSpinner);
        GridLayout categoryGrid = content.findViewById(R.id.categoryGrid);
        LinearLayout severityRow = content.findViewById(R.id.severityRow);

        EditText descriptionInput =
                content.findViewById(R.id.issueDescriptionInput);

        TextView charCountText =
                content.findViewById(R.id.charCountText);

        View submitBtn =
                content.findViewById(R.id.reportSubmitBtn);

        View cancelBtn =
                content.findViewById(R.id.reportCancelBtn);

        ImageView closeBtn =
                content.findViewById(R.id.reportCloseBtn);

        activePhotoPreviewScroll = content.findViewById(R.id.photoPreviewScroll);
        activePhotoPreviewRow = content.findViewById(R.id.photoPreviewRow);
        activePhotosLabel = content.findViewById(R.id.photosLabel);
        activeAttachPhotosCard = content.findViewById(R.id.attachPhotosCard);
        View attachPhotosBtn = content.findViewById(R.id.attachPhotosBtn);
        TextView uploadProgressText = content.findViewById(R.id.uploadProgressText);

        refreshPhotoPreview();

        attachPhotosBtn.setOnClickListener(v -> {
            if (pendingPhotoUris.size() >= MAX_PHOTOS) {
                Toast.makeText(context, "You can only attach up to " + MAX_PHOTOS + " photos.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (onPickPhotosRequested != null) {
                onPickPhotosRequested.run();
            } else {
                Toast.makeText(context, "Photo attachment is unavailable here.", Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(content)
                .create();

        dialog.setCancelable(true);

        dialog.setOnDismissListener(d -> {
            activePhotoPreviewRow = null;
            activePhotoPreviewScroll = null;
            activePhotosLabel = null;
            activeAttachPhotosCard = null;
            pendingPhotoUris.clear();
        });

        /*
         * ROOMS - every room in the system, not just previously reported ones
         */
        ArrayList<String> rooms = new ArrayList<>();
        rooms.add("Select Room");
        for (String[] entry : roomCatalog) rooms.add(entry[1]);

        if (roomCatalog.isEmpty()) {
            Toast.makeText(context,
                    "Still loading rooms, please try again in a moment.",
                    Toast.LENGTH_SHORT).show();
            loadRoomCatalog();
        }

        roomSpinner.setAdapter(
                new ArrayAdapter<>(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        rooms
                )
        );

        /*
         * CATEGORIES — matches the web's SubmitIssueModal category list
         */
        final String[] selectedCategory = {CATEGORIES_WITH_ICONS[0][0]};

        for (String[] cat : CATEGORIES_WITH_ICONS) {
            String category = cat[0];
            int iconRes = Integer.parseInt(cat[1]);

            View chip = LayoutInflater.from(context).inflate(R.layout.item_category_chip, categoryGrid, false);
            ImageView icon = chip.findViewById(R.id.catIcon);
            TextView label = chip.findViewById(R.id.catLabel);
            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) chip;

            icon.setImageResource(iconRes);
            label.setText(category);

            Runnable updateStyle = () -> {
                boolean isSelected = category.equals(selectedCategory[0]);
                card.setCardBackgroundColor(Color.parseColor(isSelected ? "#FFF1E6" : "#FFFFFF"));
                card.setStrokeColor(android.content.res.ColorStateList.valueOf(
                        Color.parseColor(isSelected ? "#F97316" : "#E2E8F0")));
                card.setStrokeWidth(dp(isSelected ? 2 : 1));
                icon.setColorFilter(Color.parseColor(isSelected ? "#F97316" : "#64748B"));
                label.setTextColor(Color.parseColor(isSelected ? "#F97316" : "#64748B"));
                label.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            };

            updateStyle.run();

            chip.setOnClickListener(v -> {
                selectedCategory[0] = category;
                for (int i = 0; i < categoryGrid.getChildCount(); i++) {
                    View child = categoryGrid.getChildAt(i);
                    String childLabel = ((TextView)child.findViewById(R.id.catLabel)).getText().toString();
                    boolean isChildSelected = childLabel.equals(selectedCategory[0]);
                    com.google.android.material.card.MaterialCardView childCard = (com.google.android.material.card.MaterialCardView) child;
                    ImageView childIcon = child.findViewById(R.id.catIcon);
                    TextView childTv = child.findViewById(R.id.catLabel);

                    childCard.setCardBackgroundColor(Color.parseColor(isChildSelected ? "#FFF1E6" : "#FFFFFF"));
                    childCard.setStrokeColor(android.content.res.ColorStateList.valueOf(
                            Color.parseColor(isChildSelected ? "#F97316" : "#E2E8F0")));
                    childCard.setStrokeWidth(dp(isChildSelected ? 2 : 1));
                    childIcon.setColorFilter(Color.parseColor(isChildSelected ? "#F97316" : "#64748B"));
                    childTv.setTextColor(Color.parseColor(isChildSelected ? "#F97316" : "#64748B"));
                    childTv.setTypeface(null, isChildSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
            });

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(6), dp(6), dp(6), dp(6));
            chip.setLayoutParams(params);

            categoryGrid.addView(chip);
        }

        /*
         * SEVERITY
         */
        String[] severityOptions = {"Low", "Medium", "High", "Urgent"};
        final String[] selectedSeverity = {"Medium"};

        for (String severity : severityOptions) {
            View chip = LayoutInflater.from(context).inflate(R.layout.item_severity_chip, severityRow, false);
            TextView label = chip.findViewById(R.id.sevLabel);
            View dot = chip.findViewById(R.id.sevDot);
            com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) chip;

            label.setText(severity);
            int dotColor;
            switch(severity) {
                case "Low": dotColor = Color.parseColor("#16A34A"); break;
                case "High": dotColor = Color.parseColor("#EA580C"); break;
                case "Urgent": dotColor = Color.parseColor("#DC2626"); break;
                default: dotColor = Color.parseColor("#D97706"); break;
            }
            ((GradientDrawable)dot.getBackground()).setColor(dotColor);

            Runnable updateStyle = () -> {
                boolean isSelected = severity.equals(selectedSeverity[0]);
                card.setCardBackgroundColor(isSelected ? Color.parseColor("#FFFBEB") : Color.WHITE);
                card.setStrokeColor(android.content.res.ColorStateList.valueOf(
                        isSelected ? dotColor : Color.parseColor("#E2E8F0")));
                card.setStrokeWidth(dp(isSelected ? 2 : 1));
                label.setTextColor(isSelected ? dotColor : Color.parseColor("#64748B"));
                label.setTypeface(null, isSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            };

            updateStyle.run();

            chip.setOnClickListener(v -> {
                selectedSeverity[0] = severity;
                for (int i = 0; i < severityRow.getChildCount(); i++) {
                    View child = severityRow.getChildAt(i);
                    String childLabel = ((TextView)child.findViewById(R.id.sevLabel)).getText().toString();
                    boolean isChildSelected = childLabel.equals(selectedSeverity[0]);
                    com.google.android.material.card.MaterialCardView childCard = (com.google.android.material.card.MaterialCardView) child;
                    TextView childTv = child.findViewById(R.id.sevLabel);

                    int cDotColor;
                    switch(childLabel) {
                        case "Low": cDotColor = Color.parseColor("#16A34A"); break;
                        case "High": cDotColor = Color.parseColor("#EA580C"); break;
                        case "Urgent": cDotColor = Color.parseColor("#DC2626"); break;
                        default: cDotColor = Color.parseColor("#D97706"); break;
                    }

                    childCard.setCardBackgroundColor(isChildSelected ? Color.parseColor("#FFFBEB") : Color.WHITE);
                    childCard.setStrokeColor(android.content.res.ColorStateList.valueOf(
                            isChildSelected ? cDotColor : Color.parseColor("#E2E8F0")));
                    childCard.setStrokeWidth(dp(isChildSelected ? 2 : 1));
                    childTv.setTextColor(isChildSelected ? cDotColor : Color.parseColor("#64748B"));
                    childTv.setTypeface(null, isChildSelected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
            params.setMargins(dp(4), 0, dp(4), 0);
            chip.setLayoutParams(params);
            severityRow.addView(chip);
        }

        /*
         * CHARACTER COUNTER
         */
        descriptionInput.setFilters(
                new InputFilter[]{
                        new InputFilter.LengthFilter(500)
                });

        descriptionInput.addTextChangedListener(
                new TextWatcher() {
                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after) {
                    }

                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count) {

                        charCountText.setText(
                                s.length() + "/500");
                    }

                    @Override
                    public void afterTextChanged(
                            Editable s) {
                    }
                });

        /*
         * CLOSE
         */
        closeBtn.setOnClickListener(v -> dialog.dismiss());

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        /*
         * SUBMIT
         */
        submitBtn.setOnClickListener(v -> {

            String roomName =
                    roomSpinner.getSelectedItem() == null
                            ? ""
                            : roomSpinner.getSelectedItem().toString();

            String description =
                    descriptionInput.getText()
                            .toString()
                            .trim();

            if (roomName.equals("Select Room")) {

                Toast.makeText(
                        context,
                        "Please select a room.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            if (description.isEmpty()) {

                Toast.makeText(
                        context,
                        "Please enter a description.",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            String summary = "\u2022 Room: " + roomName + "\n" +
                             "\u2022 Category: " + selectedCategory[0] + "\n" +
                             "\u2022 Severity: " + selectedSeverity[0] + "\n" +
                             "\u2022 Description: " + description;

            ConfirmDialog.show(context, "Review Issue Report", summary, "Confirm & Submit", "Edit Details", () -> {
                submitBtn.setEnabled(false);
                cancelBtn.setEnabled(false);
                dialog.setCancelable(false);

                if (pendingPhotoUris.isEmpty()) {
                    submitReport(roomName, selectedCategory[0], description,
                            selectedSeverity[0], new ArrayList<>());
                    dialog.dismiss();
                    return;
                }

                uploadProgressText.setVisibility(View.VISIBLE);
                uploadPhotosSequentially(new ArrayList<>(pendingPhotoUris), 0, new ArrayList<>(),
                        uploadProgressText,
                        uploadedUrls -> {
                            submitReport(roomName, selectedCategory[0], description,
                                    selectedSeverity[0], uploadedUrls);
                            dialog.dismiss();
                        });
            });
        });

        dialog.show();
    }

    /** Recursively uploads each picked photo via the host Activity's Cloudinary helper,
     *  updating the progress label as each finishes, then hands back the full URL list. */
    private void uploadPhotosSequentially(List<Uri> remaining, int uploadedSoFarCount,
                                          List<String> uploadedUrls, TextView progressText,
                                          java.util.function.Consumer<List<String>> onDone) {
        int total = uploadedSoFarCount + remaining.size();

        if (remaining.isEmpty()) {
            progressText.setVisibility(View.GONE);
            onDone.accept(uploadedUrls);
            return;
        }

        progressText.setText("Uploading photo " + (uploadedSoFarCount + 1) + " of " + total + "\u2026");

        if (!(context instanceof DashboardActivity)) {
            // No host to perform the upload — submit without photos rather than block forever.
            progressText.setVisibility(View.GONE);
            onDone.accept(uploadedUrls);
            return;
        }

        Uri next = remaining.remove(0);
        ((DashboardActivity) context).uploadIssuePhotoToCloudinary(next, (secureUrl, error) -> {
            if (secureUrl != null) uploadedUrls.add(secureUrl);
            uploadPhotosSequentially(remaining, uploadedSoFarCount + 1, uploadedUrls, progressText, onDone);
        });
    }

    private void submitReport(String room, String category, String description, String severity,
                              List<String> photoUrls) {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) return;

        String roomId = null;
        String floor = "";
        for (String[] entry : roomCatalog) {
            if (entry[1].equalsIgnoreCase(room)) {
                roomId = entry[0];
                floor = entry.length > 2 && entry[2] != null ? entry[2] : "";
                break;
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("reporterId", uid);
        report.put("reporterName", reporterName.isEmpty() ? "Faculty" : reporterName);
        report.put("reporterRole", reporterRole);
        report.put("roomId", roomId);
        report.put("roomName", room);
        report.put("floor", floor);
        report.put("category", category);
        report.put("description", description);
        report.put("severity", severity);
        // "Pending" matches the web's SubmitIssueModal flow: Pending -> Admin acknowledges.
        report.put("status", "Pending");
        report.put("clerkNotes", "");
        report.put("acknowledgedBy", "");
        report.put("acknowledgedAt", null);
        report.put("resolvedBy", "");
        report.put("resolvedAt", null);
        report.put("photoUrls", photoUrls);
        report.put("photoUrl", photoUrls.isEmpty() ? "" : photoUrls.get(0));
        report.put("createdAt", Timestamp.now());
        report.put("updatedAt", Timestamp.now());

        FirebaseFirestore.getInstance().collection("roomIssues").add(report)
                .addOnSuccessListener(reference -> {
                    Map<String, Object> extras = new HashMap<>();
                    extras.put("issueId", reference.getId());
                    if (roomCatalogIdFor(room) != null) extras.put("roomId", roomCatalogIdFor(room));

                    NotificationHelper.notifyClerkAndAdmin(
                            "New Room Issue Reported",
                            (reporterName.isEmpty() ? "A faculty member" : reporterName)
                                    + " reported a " + severity.toLowerCase(Locale.US)
                                    + " " + category.toLowerCase(Locale.US)
                                    + " issue in " + room + ".",
                            "room-issue",
                            "Urgent".equalsIgnoreCase(severity) ? "URGENT" : "NEW",
                            extras);

                    NotificationHelper.send(uid, "faculty", "Issue Report Submitted",
                            "Your " + category.toLowerCase(Locale.US) + " issue for "
                                    + room + " has been submitted and is now open.",
                            "room-issue", "INFO", extras);

                    Map<String, Object> details = new HashMap<>();
                    details.put("room", room);
                    details.put("category", category);
                    details.put("severity", severity);
                    details.put("description", description);

                    ActivityLogger.log("Reported Room Issue", "CREATE",
                            room + " | " + category, "SUCCESS", details, null);

                    Toast.makeText(context, "Issue report submitted.", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(error -> Toast.makeText(context,
                        "Could not submit report: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show());
    }

    private String roomCatalogIdFor(String roomName) {
        for (String[] entry : roomCatalog) {
            if (entry[1].equalsIgnoreCase(roomName)) return entry[0];
        }
        return null;
    }

    private static final class AdapterViewHelper {
        static void listen(Spinner spinner, Runnable action) {
            spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                                                     View view, int position, long id) {
                    action.run();
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }
    }


}