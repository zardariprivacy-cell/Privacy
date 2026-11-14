package com.nawazishali.guftagu;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Firebase over REST only (no SDK): Identity Toolkit for accounts,
 * Realtime Database for data. All calls run on a background thread;
 * callbacks arrive on the main thread.
 */
public class Api {

    public interface Cb<T> {
        void done(T result, String error);
    }

    private static final ExecutorService EX = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static class Resp {
        int code;
        String body;
    }

    private static Resp raw(String method, String urlStr, String jsonBody) {
        Resp r = new Resp();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setRequestMethod(method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            if (jsonBody != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                OutputStream os = c.getOutputStream();
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.close();
            }
            r.code = c.getResponseCode();
            InputStream is = (r.code >= 200 && r.code < 300) ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (is != null) {
                BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
            }
            r.body = sb.toString();
        } catch (Exception e) {
            r.code = -1;
            r.body = "";
        } finally {
            if (c != null) c.disconnect();
        }
        return r;
    }

    private static String friendlyError(String body) {
        try {
            JSONObject o = new JSONObject(body);
            String msg = o.getJSONObject("error").getString("message");
            if (msg.contains("EMAIL_EXISTS")) return "Ye username pehle se kisi ke paas hai — koi aur choose karo";
            if (msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("INVALID_PASSWORD") || msg.contains("EMAIL_NOT_FOUND"))
                return "Username ya password ghalat hai";
            if (msg.contains("USER_DISABLED")) return "Ye account band (ban) hai — owner se rabta karo";
            if (msg.contains("WEAK_PASSWORD")) return "Password kamzor hai — lamba password rakho";
            if (msg.contains("TOO_MANY_ATTEMPTS")) return "Bohot koshishain ho gayein — thodi dair baad try karo";
            return msg;
        } catch (Exception e) {
            return "Network masla — internet check karke dobara try karo";
        }
    }

    // ---------- Auth ----------

    public static void signUp(final Context ctx, final String username, final String password,
                              final String displayName, final Cb<String> cb) {
        EX.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", username + G.EMAIL_DOMAIN);
                body.put("password", password);
                body.put("returnSecureToken", true);
                Resp r = raw("POST", G.AUTH + "/accounts:signUp?key=" + G.API_KEY, body.toString());
                if (r.code == 200) {
                    JSONObject o = new JSONObject(r.body);
                    Session.saveLogin(ctx, o.getString("localId"), username, displayName,
                            o.getString("idToken"), o.getString("refreshToken"),
                            Long.parseLong(o.optString("expiresIn", "3600")));
                    final String newUid = o.getString("localId");
                    MAIN.post(() -> cb.done(newUid, null));
                } else {
                    String err = friendlyError(r.body);
                    MAIN.post(() -> cb.done(null, err));
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, "Network masla — dobara try karo"));
            }
        });
    }

    public static void signIn(final Context ctx, final String username, final String password,
                              final Cb<String> cb) {
        EX.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", username + G.EMAIL_DOMAIN);
                body.put("password", password);
                body.put("returnSecureToken", true);
                Resp r = raw("POST", G.AUTH + "/accounts:signInWithPassword?key=" + G.API_KEY, body.toString());
                if (r.code == 200) {
                    JSONObject o = new JSONObject(r.body);
                    Session.saveLogin(ctx, o.getString("localId"), username,
                            Session.name(ctx), o.getString("idToken"), o.getString("refreshToken"),
                            Long.parseLong(o.optString("expiresIn", "3600")));
                    final String inUid = o.getString("localId");
                    MAIN.post(() -> cb.done(inUid, null));
                } else {
                    String err = friendlyError(r.body);
                    MAIN.post(() -> cb.done(null, err));
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, "Network masla — dobara try karo"));
            }
        });
    }

    /** Blocking refresh; call from a background thread. Returns true on success. */
    private static boolean refreshBlocking(Context ctx) {
        try {
            String rt = Session.refreshToken(ctx);
            if (rt == null) return false;
            JSONObject body = new JSONObject();
            body.put("grant_type", "refresh_token");
            body.put("refresh_token", rt);
            Resp r = raw("POST", G.TOKEN + "?key=" + G.API_KEY, body.toString());
            if (r.code == 200) {
                JSONObject o = new JSONObject(r.body);
                Session.saveTokens(ctx, o.getString("id_token"), o.getString("refresh_token"),
                        Long.parseLong(o.optString("expires_in", "3600")));
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String tokenBlocking(Context ctx) {
        if (System.currentTimeMillis() > Session.exp(ctx)) refreshBlocking(ctx);
        return Session.idToken(ctx);
    }

    public static void changePassword(final Context ctx, final String newPassword, final Cb<Boolean> cb) {
        EX.execute(() -> {
            try {
                String tok = tokenBlocking(ctx);
                JSONObject body = new JSONObject();
                body.put("idToken", tok);
                body.put("password", newPassword);
                body.put("returnSecureToken", true);
                Resp r = raw("POST", G.AUTH + "/accounts:update?key=" + G.API_KEY, body.toString());
                if (r.code == 200) {
                    JSONObject o = new JSONObject(r.body);
                    Session.saveTokens(ctx, o.getString("idToken"), o.getString("refreshToken"),
                            Long.parseLong(o.optString("expiresIn", "3600")));
                    MAIN.post(() -> cb.done(true, null));
                } else {
                    String err = friendlyError(r.body);
                    MAIN.post(() -> cb.done(null, err));
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, "Network masla — dobara try karo"));
            }
        });
    }

    // ---------- Database ----------

    private interface DbWork<T> {
        T run(String token) throws Exception;
    }

    private static <T> void db(final Context ctx, final DbWork<T> work, final Cb<T> cb) {
        EX.execute(() -> {
            try {
                String tok = tokenBlocking(ctx);
                try {
                    T out = work.run(tok);
                    MAIN.post(() -> cb.done(out, null));
                } catch (DbHttp e) {
                    if (e.code == 401 && refreshBlocking(ctx)) {
                        T out = work.run(Session.idToken(ctx));
                        MAIN.post(() -> cb.done(out, null));
                    } else {
                        MAIN.post(() -> cb.done(null, "Server ne mana kar diya (" + e.code + ")"));
                    }
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.done(null, "Network masla — dobara try karo"));
            }
        });
    }

    private static class DbHttp extends Exception {
        final int code;
        DbHttp(int code) { this.code = code; }
    }

    private static JSONObject getJson(String url) throws Exception {
        Resp r = raw("GET", url, null);
        if (r.code == 401 || r.code == 403) throw new DbHttp(r.code);
        if (r.code != 200) throw new DbHttp(r.code);
        if (r.body == null || r.body.trim().isEmpty() || r.body.trim().equals("null")) return null;
        return new JSONObject(r.body);
    }

    private static void putJson(String url, String body) throws Exception {
        Resp r = raw("PUT", url, body);
        if (r.code != 200) throw new DbHttp(r.code);
    }

    private static String postJson(String url, String body) throws Exception {
        Resp r = raw("POST", url, body);
        if (r.code != 200) throw new DbHttp(r.code);
        return new JSONObject(r.body).getString("name");
    }

    private static void deleteUrl(String url) throws Exception {
        Resp r = raw("DELETE", url, null);
        if (r.code != 200) throw new DbHttp(r.code);
    }

    private static String u(String path, String tok) {
        return G.DB + path + ".json?auth=" + tok;
    }

    public static void dbGet(Context ctx, final String path, final Cb<JSONObject> cb) {
        db(ctx, tok -> getJson(u(path, tok)), cb);
    }

    /** GET with extra RTDB query params, e.g. orderBy="ts"&limitToLast=100 (URL-encoded). */
    public static void dbGetQ(Context ctx, final String path, final String query, final Cb<JSONObject> cb) {
        db(ctx, tok -> getJson(u(path, tok) + "&" + query), cb);
    }

    public static void dbPut(Context ctx, final String path, final JSONObject body, final Cb<Boolean> cb) {
        db(ctx, tok -> {
            putJson(u(path, tok), body.toString());
            return true;
        }, cb);
    }

    public static void dbPutBool(Context ctx, final String path, final boolean value, final Cb<Boolean> cb) {
        db(ctx, tok -> {
            putJson(u(path, tok), value ? "true" : "false");
            return true;
        }, cb);
    }

    public static void dbPost(Context ctx, final String path, final JSONObject body, final Cb<String> cb) {
        db(ctx, tok -> postJson(u(path, tok), body.toString()), cb);
    }

    public static void dbDelete(Context ctx, final String path, final Cb<Boolean> cb) {
        db(ctx, tok -> {
            deleteUrl(u(path, tok));
            return true;
        }, cb);
    }
}
