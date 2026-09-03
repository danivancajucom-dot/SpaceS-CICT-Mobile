package com.example.spacescict;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class LoadingOverlay {

    static FrameLayout currentOverlay;
    static TextView currentMessageView;

    public static void show(Activity activity, String message) {
        if (currentOverlay != null) {
            if (currentMessageView != null) currentMessageView.setText(message);
            return;
        }

        FrameLayout root = activity.findViewById(android.R.id.content);

        FrameLayout scrim = new FrameLayout(activity);
        scrim.setBackgroundColor(Color.parseColor("#F5F3F0"));
        scrim.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scrim.setClickable(true);

        LinearLayout inner = new LinearLayout(activity);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams innerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        innerParams.gravity = Gravity.CENTER;
        inner.setLayoutParams(innerParams);

        // Branded icon chip
        androidx.cardview.widget.CardView iconCard = new androidx.cardview.widget.CardView(activity);
        int chipSize = (int) dp(activity, 72);
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(chipSize, chipSize);
        iconCard.setLayoutParams(chipParams);
        iconCard.setRadius(dp(activity, 20));
        iconCard.setCardElevation(0);
        iconCard.setCardBackgroundColor(Color.parseColor("#FFF1E6"));

        FrameLayout iconWrap = new FrameLayout(activity);
        iconWrap.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ProgressBar spinner = new ProgressBar(activity);
        spinner.getIndeterminateDrawable().setColorFilter(Color.parseColor("#F97316"), PorterDuff.Mode.SRC_IN);
        int spinnerSize = (int) dp(activity, 34);
        FrameLayout.LayoutParams spinnerParams = new FrameLayout.LayoutParams(spinnerSize, spinnerSize);
        spinnerParams.gravity = Gravity.CENTER;
        spinner.setLayoutParams(spinnerParams);
        iconWrap.addView(spinner);
        iconCard.addView(iconWrap);
        inner.addView(iconCard);

        TextView messageView = new TextView(activity);
        messageView.setText(message != null ? message : "Loading...");
        messageView.setTextColor(Color.parseColor("#1C1917"));
        messageView.setTextSize(16);
        messageView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams msgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        msgParams.topMargin = (int) dp(activity, 20);
        messageView.setLayoutParams(msgParams);
        setBoldFont(activity, messageView);
        inner.addView(messageView);

        TextView subMessage = new TextView(activity);
        subMessage.setText("Please wait a moment");
        subMessage.setTextColor(Color.parseColor("#78716C"));
        subMessage.setTextSize(13);
        subMessage.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = (int) dp(activity, 6);
        subMessage.setLayoutParams(subParams);
        inner.addView(subMessage);

        scrim.addView(inner);
        root.addView(scrim);

        currentOverlay = scrim;
        currentMessageView = messageView;
    }

    public static void hide() {
        if (currentOverlay != null && currentOverlay.getParent() != null) {
            ((ViewGroup) currentOverlay.getParent()).removeView(currentOverlay);
        }
        currentOverlay = null;
        currentMessageView = null;
    }

    static void setBoldFont(Activity activity, TextView view) {
        try {
            android.graphics.Typeface tf = androidx.core.content.res.ResourcesCompat.getFont(activity, R.font.lexend_bold);
            view.setTypeface(tf);
        } catch (Exception ignored) {}
    }

    static float dp(Activity activity, int value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }
}