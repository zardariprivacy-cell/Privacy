package com.nawazishali.muetpractice;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Online leaderboard backed by a Firebase Realtime Database over its REST
 * API (no SDK needed). Each phone keeps ONE entry under its own random
 * device id: the student's display name + best Mock Test score. Only name
 * and score ever leave the phone — no password, no email.
 */
public class OnlineBoard {

    private static final String DB_URL = "https://unitest-prep-default-rtdb.firebaseio.com";
    private static final String PREFS = "online_board";
    private static final String KEY_ID = "device_id";

    public static class Row {
        public String name;
        public int score;
        public int total;
        public int pct;
        public long ts;
    }

    public interface Callback {
        void onDone(List<Row> rows); // rows == null means the fetch failed (offline etc.)
    }

    private static String deviceId(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = p.getString(KEY_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString().replace("-", "");
            p.edit().putString(KEY_ID, id).apply();
        }
        return id;
    }

    /** Upload this phone's best mock score for the signed-in user, if any. Fire and forget. */
    public static void syncMyBest(Context ctx, Db db) {
        long uid = Session.currentUserId(ctx);
        if (uid <= 0) return;
        User me = db.getUser(uid);
        ResultRow best = db.bestMock(uid);
        if (me == null || best == null) return;
        pushBest(ctx, me.name, best.score, best.total, best.percent());
    }

    public static void pushBest(final Context ctx, final String name, final int score,
                                final int total, final int pct) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("score", score);
                body.put("total", total);
                body.put("pct", pct);
                body.put("ts", System.currentTimeMillis());
                URL url = new URL(DB_URL + "/leaderboard/" + deviceId(ctx) + ".json");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("PUT");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                OutputStream os = c.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();
                c.getResponseCode();
                c.disconnect();
            } catch (Exception ignored) {
                // Offline ya server busy — local board phir bhi chalta hai.
            }
        }).start();
    }

    public static void fetch(final Callback cb) {
        new Thread(() -> {
            List<Row> rows = null;
            try {
                URL url = new URL(DB_URL + "/leaderboard.json");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    rows = new ArrayList<>();
                    String text = sb.toString().trim();
                    if (!text.equals("null") && !text.isEmpty()) {
                        JSONObject root = new JSONObject(text);
                        Iterator<String> keys = root.keys();
                        while (keys.hasNext()) {
                            JSONObject o = root.optJSONObject(keys.next());
                            if (o == null) continue;
                            Row r = new Row();
                            r.name = o.optString("name", "Student");
                            r.score = o.optInt("score", 0);
                            r.total = o.optInt("total", 0);
                            r.pct = o.optInt("pct", 0);
                            r.ts = o.optLong("ts", 0);
                            rows.add(r);
                        }
                        rows.sort((a, b) -> {
                            if (b.pct != a.pct) return Integer.compare(b.pct, a.pct);
                            return Integer.compare(b.score, a.score);
                        });
                    }
                }
                c.disconnect();
            } catch (Exception e) {
                rows = null;
            }
            cb.onDone(rows);
        }).start();
    }
}
