package com.nawazishali.guftagu;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** All WhatsApp-style settings, stored on this phone. */
public class AppPrefs {

    private static final String P = "guftagu_settings";
    private static final String B = "guftagu_blocks";

    // Wallpapers: indices 0-2 are doodle patterns (drawables), 3-8 are solid colors
    public static final String[] WALLPAPER_NAMES = {
            "Doodle — Classic (halka)", "Safed (plain)", "Doodle — Dark",
            "Doodle — Neela", "Doodle — Hara",
            "Gehra Neela", "Gehra Hara", "Maroon", "Purple", "Brown"
    };
    private static final int[] WALLPAPER_COLORS = {
            0xFF0A1931, 0xFF06281D, 0xFF2B0B12, 0xFF170B2E, 0xFF1D130B
    };
    private static final int[] AVATAR_BGS = {
            R.drawable.bg_circle, R.drawable.bg_circle_1, R.drawable.bg_circle_2,
            R.drawable.bg_circle_3, R.drawable.bg_circle_4, R.drawable.bg_circle_5
    };

    /** A stable gradient avatar color for a user (same person = same color everywhere). */
    public static int avatarBgFor(String seed) {
        if (seed == null) return AVATAR_BGS[0];
        return AVATAR_BGS[Math.abs(seed.hashCode()) % AVATAR_BGS.length];
    }

    public static void applyWallpaper(android.app.Activity a, android.view.View target) {
        int i = wallpaper(a);
        if (i == 0) target.setBackgroundResource(R.drawable.bg_wp0);
        else if (i == 1) target.setBackgroundColor(0xFFFFFFFF);
        else if (i == 2) target.setBackgroundResource(R.drawable.bg_wp1);
        else if (i == 3) target.setBackgroundResource(R.drawable.bg_wp2);
        else if (i == 4) target.setBackgroundResource(R.drawable.bg_wp3);
        else {
            int ci = i - 5;
            if (ci < 0 || ci >= WALLPAPER_COLORS.length) {
                target.setBackgroundResource(R.drawable.bg_wp0);
                return;
            }
            target.setBackgroundColor(WALLPAPER_COLORS[ci]);
        }
    }
    public static final String[] FONT_NAMES = {"Chhota", "Darmiyana", "Bara"};

    private static SharedPreferences p(Context c) { return c.getSharedPreferences(P, Context.MODE_PRIVATE); }

    public static boolean readReceipts(Context c) { return p(c).getBoolean("readReceipts", true); }
    public static void setReadReceipts(Context c, boolean v) { p(c).edit().putBoolean("readReceipts", v).apply(); }

    public static boolean showLastSeen(Context c) { return p(c).getBoolean("showLastSeen", true); }
    public static void setShowLastSeen(Context c, boolean v) { p(c).edit().putBoolean("showLastSeen", v).apply(); }

    public static boolean hideProfile(Context c) { return p(c).getBoolean("hideProfile", false); }
    public static void setHideProfile(Context c, boolean v) { p(c).edit().putBoolean("hideProfile", v).apply(); }

    public static boolean enterSends(Context c) { return p(c).getBoolean("enterSends", true); }
    public static void setEnterSends(Context c, boolean v) { p(c).edit().putBoolean("enterSends", v).apply(); }

    public static int fontSize(Context c) { return p(c).getInt("fontSize", 1); }
    public static void setFontSize(Context c, int v) { p(c).edit().putInt("fontSize", v).apply(); }
    public static float bubbleTextSize(Context c) {
        int f = fontSize(c);
        return f == 0 ? 14f : (f == 2 ? 18f : 16f);
    }

    public static int wallpaper(Context c) { return p(c).getInt("wallpaper", 0); }
    public static void setWallpaper(Context c, int v) { p(c).edit().putInt("wallpaper", v).apply(); }

    public static boolean autoDlPhoto(Context c) { return p(c).getBoolean("autoDlPhoto", true); }
    public static void setAutoDlPhoto(Context c, boolean v) { p(c).edit().putBoolean("autoDlPhoto", v).apply(); }
    public static boolean autoDlVoice(Context c) { return p(c).getBoolean("autoDlVoice", true); }
    public static void setAutoDlVoice(Context c, boolean v) { p(c).edit().putBoolean("autoDlVoice", v).apply(); }

    public static boolean soundOn(Context c) { return p(c).getBoolean("soundOn", true); }
    public static void setSoundOn(Context c, boolean v) { p(c).edit().putBoolean("soundOn", v).apply(); }
    public static boolean vibrateOn(Context c) { return p(c).getBoolean("vibrateOn", true); }
    public static void setVibrateOn(Context c, boolean v) { p(c).edit().putBoolean("vibrateOn", v).apply(); }

    // ---- App lock (PIN) ----
    public static String pinHash(Context c) { return p(c).getString("pinHash", null); }
    public static void setPin(Context c, String pin) {
        p(c).edit().putString("pinHash", pin == null ? null : sha256(pin)).apply();
    }
    public static boolean checkPin(Context c, String pin) {
        String h = pinHash(c);
        return h != null && h.equals(sha256(pin == null ? "" : pin));
    }

    public static String sha256(String s) {
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

    // ---- Blocked contacts (per phone) ----
    private static SharedPreferences pb(Context c) { return c.getSharedPreferences(B, Context.MODE_PRIVATE); }

    public static boolean isBlocked(Context c, String uid) {
        return pb(c).getStringSet("uids", new HashSet<>()).contains(uid);
    }

    public static void block(Context c, String uid, String name) {
        Set<String> s = new HashSet<>(pb(c).getStringSet("uids", new HashSet<>()));
        s.add(uid);
        pb(c).edit().putStringSet("uids", s).putString("name_" + uid, name).apply();
    }

    public static void unblock(Context c, String uid) {
        Set<String> s = new HashSet<>(pb(c).getStringSet("uids", new HashSet<>()));
        s.remove(uid);
        pb(c).edit().putStringSet("uids", s).remove("name_" + uid).apply();
    }

    public static List<String[]> blockedList(Context c) {
        List<String[]> out = new ArrayList<>();
        for (String uid : pb(c).getStringSet("uids", new HashSet<>())) {
            out.add(new String[]{uid, pb(c).getString("name_" + uid, "")});
        }
        return out;
    }
}
