package com.nawazishali.guftagu;

import android.content.Context;

import java.security.MessageDigest;

/**
 * The Admin Panel UI is locked behind a secret Owner Code that only the
 * app's owner knows (only its SHA-256 hash lives here). Real admin powers
 * are ALSO enforced on the server: Firebase rules accept admin writes
 * (bans, broadcasts) only from the owner's account uid.
 */
public class OwnerMode {

    private static final String PREFS = "guftagu_owner";
    private static final String KEY = "unlocked";
    private static final String CODE_HASH = "5b09d9d9a69248ffdff5fc7a49ddaa88cbfb96771e7c6eb13d80cab5299b5879";

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
