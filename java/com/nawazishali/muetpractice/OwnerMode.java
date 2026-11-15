package com.nawazishali.muetpractice;

import android.content.Context;

import java.security.MessageDigest;

/**
 * MCQ management is locked behind a secret Owner Code that only the app's
 * owner knows. Regular accounts are always students. The code itself is
 * never stored — only its SHA-256 hash is compared.
 */
public class OwnerMode {

    private static final String PREFS = "owner_mode";
    private static final String KEY = "unlocked";
    private static final String CODE_HASH = "12c32a21899a34787cff64a67351f1b24de5762e4b680f900ce0373cfc0d1eea";

    public static boolean isUnlocked(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    public static boolean tryUnlock(Context ctx, String code) {
        if (code != null && sha256(code.trim()).equals(CODE_HASH)) {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, true).apply();
            return true;
        }
        return false;
    }

    public static void lock(Context ctx) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, false).apply();
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
