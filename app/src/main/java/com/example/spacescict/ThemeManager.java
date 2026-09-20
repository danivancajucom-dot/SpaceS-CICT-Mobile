package com.example.spacescict;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

/**
 * Mirrors the web's theme.js. Accent colours are stored PER ACCOUNT (keyed by
 * uid) so two faculty signing in on the same handset never inherit each other's
 * choice. Anyone without a saved choice gets the default orange.
 */
public final class ThemeManager {

    public static final class Theme {
        public final String id, name, description;
        public final int accent, accentHover, accentSoft, accentBorder;

        Theme(String id, String name, String description,
              String accent, String accentHover, String accentSoft, String accentBorder) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.accent = Color.parseColor(accent);
            this.accentHover = Color.parseColor(accentHover);
            this.accentSoft = Color.parseColor(accentSoft);
            this.accentBorder = Color.parseColor(accentBorder);
        }
    }

    public static final Theme[] THEMES = {
            new Theme("orange", "Sunset Orange", "The default SpaceS look - warm orange on white.",
                    "#F97316", "#EA580C", "#FFF1E6", "#FDDCBE"),
            new Theme("blue", "Ocean Blue", "Cool, professional blue on white.",
                    "#2563EB", "#1D4ED8", "#E8F0FE", "#B6CFFB"),
            new Theme("gray", "Slate Gray", "Neutral, minimal gray on white.",
                    "#475569", "#334155", "#EEF1F4", "#CBD5E1"),
            new Theme("navy", "Blue Slate", "Muted blue-gray combo for a calmer feel.",
                    "#3B5C7A", "#2C4560", "#EAF1F6", "#BCD2E0"),
    };

    public static final String DEFAULT_THEME_ID = "orange";

    private static final String PREFS = "spaces-theme";
    private static Theme cached;

    private ThemeManager() {}

    public static Theme byId(String id) {
        if (id != null) {
            for (Theme theme : THEMES) if (theme.id.equals(id)) return theme;
        }
        return THEMES[0];
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static String storedThemeId(Context context, String uid) {
        if (uid == null) return DEFAULT_THEME_ID;
        return prefs(context).getString(uid, DEFAULT_THEME_ID);
    }

    public static Theme apply(Context context, String themeId, String uid) {
        Theme theme = byId(themeId);
        cached = theme;
        if (uid != null) prefs(context).edit().putString(uid, theme.id).apply();
        return theme;
    }

    /** Loads the signed-in user's theme into the in-memory cache. Call on app start. */
    public static Theme load(Context context, String uid) {
        cached = byId(storedThemeId(context, uid));
        return cached;
    }

    public static Theme current() {
        return cached != null ? cached : THEMES[0];
    }

    public static int accent() { return current().accent; }
    public static int accentSoft() { return current().accentSoft; }
    public static int accentHover() { return current().accentHover; }
    public static int accentBorder() { return current().accentBorder; }
}