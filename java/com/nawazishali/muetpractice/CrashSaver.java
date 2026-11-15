package com.nawazishali.muetpractice;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Saves the last crash's stack trace so it can be shown on screen next launch. */
public class CrashSaver {

    private static final String PREF = "crash";
    private static final String KEY = "pending";
    private static final String FILE = "last_crash.txt";

    public static void save(Context ctx, Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            File f = new File(ctx.getFilesDir(), FILE);
            try (FileWriter w = new FileWriter(f)) {
                w.write(sw.toString());
            }
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY, true).apply();
        } catch (Exception ignored) {
        }
    }

    public static boolean hasPending(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    public static String read(Context ctx) {
        try {
            File f = new File(ctx.getFilesDir(), FILE);
            if (!f.exists()) return "(no details saved)";
            byte[] bytes = Files.readAllBytes(f.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "(could not read details)";
        }
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY, false).apply();
        try {
            new File(ctx.getFilesDir(), FILE).delete();
        } catch (Exception ignored) {
        }
    }
}
