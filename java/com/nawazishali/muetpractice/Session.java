package com.nawazishali.muetpractice;

import android.content.Context;
import android.content.SharedPreferences;

public class Session {
    private static final String PREF = "session";
    private static final String KEY = "user_id";

    public static long currentUserId(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return sp.getLong(KEY, -1);
    }

    public static void set(Context ctx, long userId) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putLong(KEY, userId).apply();
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }
}
