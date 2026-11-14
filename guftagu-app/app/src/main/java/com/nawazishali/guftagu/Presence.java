package com.nawazishali.guftagu;

import android.content.Context;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** "online / last seen" — a heartbeat timestamp inside the user's own profile node. */
public class Presence {

    public static void beat(final Context ctx) {
        try {
            String uid = Session.uid(ctx);
            if (uid == null || !AppPrefs.showLastSeen(ctx)) return;
            JSONObject pres = new JSONObject();
            pres.put("ts", System.currentTimeMillis());
            Api.dbPut(ctx, "/users/" + uid + "/pres", pres, (ok, err) -> { });
        } catch (Exception ignored) {
        }
    }

    public static void clear(final Context ctx) {
        try {
            String uid = Session.uid(ctx);
            if (uid == null) return;
            JSONObject pres = new JSONObject();
            pres.put("ts", 0);
            Api.dbPut(ctx, "/users/" + uid + "/pres", pres, (ok, err) -> { });
        } catch (Exception ignored) {
        }
    }

    /** Returns "online" / "last seen ..." / null when nothing should be shown. */
    public static String format(JSONObject userProfile) {
        if (userProfile == null) return null;
        JSONObject pres = userProfile.optJSONObject("pres");
        if (pres == null) return null;
        long ts = pres.optLong("ts", 0);
        if (ts <= 0) return null;
        long now = System.currentTimeMillis();
        if (now - ts < 45000) return "online";
        Date d = new Date(ts);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(d);
        String day = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(d);
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(now));
        String yest = new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                .format(new Date(now - 86400000L));
        if (day.equals(today)) return "last seen aaj " + time;
        if (day.equals(yest)) return "last seen kal " + time;
        return "last seen " + new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(d);
    }

    public static boolean isOnline(JSONObject userProfile) {
        if (userProfile == null) return false;
        JSONObject pres = userProfile.optJSONObject("pres");
        if (pres == null) return false;
        long ts = pres.optLong("ts", 0);
        return ts > 0 && System.currentTimeMillis() - ts < 45000;
    }
}
