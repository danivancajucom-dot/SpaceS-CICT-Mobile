package com.example.spacescict;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Mirrors the web's passwordChecks()/isStrong() in FacultySettings.jsx exactly,
 * so a password accepted on one platform is never rejected on the other.
 */
public final class PasswordRules {

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern NUMBER = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL =
            Pattern.compile("[!@#$%^&*()_\\-+=\\[\\]{};:'\",.<>/?\\\\|`~]");

    private PasswordRules() {}

    public static Map<String, Boolean> check(String password) {
        String pw = password == null ? "" : password;
        Map<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("length", pw.length() >= 8);
        checks.put("uppercase", UPPER.matcher(pw).find());
        checks.put("lowercase", LOWER.matcher(pw).find());
        checks.put("number", NUMBER.matcher(pw).find());
        checks.put("special", SPECIAL.matcher(pw).find());
        return checks;
    }

    public static boolean isStrong(String password) {
        for (boolean pass : check(password).values()) if (!pass) return false;
        return true;
    }

    public static String label(String key) {
        switch (key) {
            case "length": return "At least 8 characters";
            case "uppercase": return "One uppercase letter";
            case "lowercase": return "One lowercase letter";
            case "number": return "One number";
            case "special": return "One special character";
            default: return key;
        }
    }
}