package com.nawazishali.guftagu;

import android.content.Context;
import android.content.SharedPreferences;

/** Login session, stored on this phone only. */
public class Session {
    private static final String PREFS = "guftagu_session";

    private static SharedPreferences p(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void saveLogin(Context ctx, String uid, String username, String name,
                                 String idToken, String refreshToken, long expiresInSec) {
        p(ctx).edit()
                .putString("uid", uid)
                .putString("username", username)
                .putString("name", name)
                .putString("idToken", idToken)
                .putString("refreshToken", refreshToken)
                .putLong("exp", System.currentTimeMillis() + expiresInSec * 1000L - 60000L)
                .apply();
    }

    public static void saveTokens(Context ctx, String idToken, String refreshToken, long expiresInSec) {
        p(ctx).edit()
                .putString("idToken", idToken)
                .putString("refreshToken", refreshToken)
                .putLong("exp", System.currentTimeMillis() + expiresInSec * 1000L - 60000L)
                .apply();
    }

    public static void saveName(Context ctx, String name) {
        p(ctx).edit().putString("name", name).apply();
    }

    public static boolean isLoggedIn(Context ctx) {
        return uid(ctx) != null && refreshToken(ctx) != null;
    }

    public static String uid(Context ctx) { return p(ctx).getString("uid", null); }
    public static String username(Context ctx) { return p(ctx).getString("username", null); }
    public static String name(Context ctx) { return p(ctx).getString("name", null); }
    public static String idToken(Context ctx) { return p(ctx).getString("idToken", null); }
    public static String refreshToken(Context ctx) { return p(ctx).getString("refreshToken", null); }
    public static long exp(Context ctx) { return p(ctx).getLong("exp", 0); }

    public static void clear(Context ctx) {
        p(ctx).edit().clear().apply();
    }
}
