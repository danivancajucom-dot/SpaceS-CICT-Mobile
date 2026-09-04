package com.example.spacescict;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class NavigationHelper {

    public static void goTo(Activity from, Class<?> target, String loadingMessage) {
        LoadingOverlay.show(from, loadingMessage);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            from.startActivity(new Intent(from, target));
            LoadingOverlay.hide();
        }, 400);
    }

    public static void goTo(Activity from, Intent intent, String loadingMessage) {
        LoadingOverlay.show(from, loadingMessage);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            from.startActivity(intent);
            LoadingOverlay.hide();
        }, 400);
    }

    public interface InlineSwapAction {
        void run();
    }

    // For DashboardActivity's contentFrame swaps (Profile, Home, Rooms, etc. — not new Activities)
    public static void inlineSwap(Activity activity, String loadingMessage, InlineSwapAction action) {
        LoadingOverlay.show(activity, loadingMessage);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            action.run();
            LoadingOverlay.hide();
        }, 350);
    }
}