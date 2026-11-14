package com.nawazishali.guftagu;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Encrypted media blobs. Firebase Storage is not available on the free
 * Spark plan, so media travels as AES-256-GCM ciphertext inside the
 * Realtime Database (/blobs). Every blob gets a fresh random key; the key
 * itself only ever travels inside the end-to-end encrypted chat message.
 */
public class MediaHelper {

    public static final int MAX_FILE_BYTES = 1000000;

    public interface BlobCb {
        void done(String blobId, String keyB64, String error);
    }

    public interface FileCb {
        void done(File file, String error);
    }

    public static File cacheFile(Context ctx, String blobId) {
        File dir = new File(ctx.getCacheDir(), "media");
        if (!dir.exists()) dir.mkdirs();
        return new File(dir, blobId);
    }

    public static void uploadBlob(final Context ctx, byte[] plain, final BlobCb cb) {
        try {
            final byte[] key = Crypto.randomBytes(32);
            final byte[] nonce = Crypto.randomBytes(12);
            final byte[] ct = Crypto.aesGcmEncrypt(key, nonce, plain,
                    "guftagu-blob".getBytes(StandardCharsets.UTF_8));
            JSONObject blob = new JSONObject();
            blob.put("from", Session.uid(ctx));
            blob.put("n", Crypto.b64(nonce));
            blob.put("d", Crypto.b64(ct));
            Api.dbPost(ctx, "/blobs", blob, (pushId, err) -> {
                if (err != null) cb.done(null, null, err);
                else cb.done(pushId, Crypto.b64(key), null);
            });
        } catch (Exception e) {
            cb.done(null, null, "Encryption masla");
        }
    }

    public static void fetchBlob(final Context ctx, final String blobId, final String keyB64, final FileCb cb) {
        final File out = cacheFile(ctx, blobId);
        if (out.exists() && out.length() > 0) {
            cb.done(out, null);
            return;
        }
        Api.dbGet(ctx, "/blobs/" + blobId, (blob, err) -> {
            if (err != null || blob == null) {
                cb.done(null, "Media download nahi hua");
                return;
            }
            try {
                byte[] pt = Crypto.aesGcmDecrypt(Crypto.unb64(keyB64),
                        Crypto.unb64(blob.getString("n")), Crypto.unb64(blob.getString("d")),
                        "guftagu-blob".getBytes(StandardCharsets.UTF_8));
                FileOutputStream fos = new FileOutputStream(out);
                fos.write(pt);
                fos.close();
                cb.done(out, null);
            } catch (Exception e) {
                cb.done(null, "Media khul nahi saka");
            }
        });
    }

    /** Center-crops a bitmap into a circle (for avatars). */
    public static android.graphics.Bitmap circular(android.graphics.Bitmap bmp) {
        int size = Math.min(bmp.getWidth(), bmp.getHeight());
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(
                size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out);
        android.graphics.Path path = new android.graphics.Path();
        path.addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CCW);
        c.clipPath(path);
        int left = (bmp.getWidth() - size) / 2, top = (bmp.getHeight() - size) / 2;
        c.drawBitmap(bmp, -left, -top, null);
        return out;
    }

    /** Small square JPEG for profile photos. */
    public static byte[] prepAvatar(android.content.Context ctx, Uri uri) throws Exception {
        byte[] full = prepPhoto(ctx, uri);
        android.graphics.Bitmap bmp = BitmapFactory.decodeByteArray(full, 0, full.length);
        int size = Math.min(bmp.getWidth(), bmp.getHeight());
        android.graphics.Bitmap sq = android.graphics.Bitmap.createBitmap(
                bmp, (bmp.getWidth() - size) / 2, (bmp.getHeight() - size) / 2, size, size);
        android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(sq, 256, 256, true);
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        small.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos);
        return bos.toByteArray();
    }

    // ---- message payloads (these JSON strings are what actually get encrypted into a chat message) ----

    public static String voicePayload(String blobId, String keyB64, int durSec) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", "voice");
            o.put("b", blobId);
            o.put("k", keyB64);
            o.put("dur", durSec);
            return o.toString();
        } catch (Exception e) { return ""; }
    }

    public static String photoPayload(String blobId, String keyB64) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", "photo");
            o.put("b", blobId);
            o.put("k", keyB64);
            return o.toString();
        } catch (Exception e) { return ""; }
    }

    public static String filePayload(String blobId, String keyB64, String name, long size) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", "file");
            o.put("b", blobId);
            o.put("k", keyB64);
            o.put("name", name);
            o.put("size", size);
            return o.toString();
        } catch (Exception e) { return ""; }
    }

    /** Returns the parsed media payload, or null for a plain-text message. */
    public static JSONObject parsePayload(String body) {
        if (body == null || !body.startsWith("{")) return null;
        try {
            JSONObject o = new JSONObject(body);
            String t = o.optString("t", "");
            if (t.equals("voice") || t.equals("photo") || t.equals("file")) return o;
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String previewText(String body) {
        JSONObject p = parsePayload(body);
        if (p == null) return body;
        switch (p.optString("t")) {
            case "voice": return "🎤 Voice message";
            case "photo": return "📷 Photo";
            case "file": return "📎 " + p.optString("name", "File");
            case "text": return (p.optBoolean("fwd") ? "↪ " : "") + p.optString("x", "");
            case "deleted": return "🚫 Message delete ho gaya";
            default: return body;
        }
    }

    // ---- media preparation ----

    /** Reads a picked image, scales it down and returns JPEG bytes (keeps blobs small). */
    public static byte[] prepPhoto(Context ctx, Uri uri) throws Exception {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        Bitmap bmp = BitmapFactory.decodeStream(is);
        if (is != null) is.close();
        if (bmp == null) throw new Exception("Photo parhi nahi gayi");
        int w = bmp.getWidth(), h = bmp.getHeight();
        int max = 1024;
        if (w > max || h > max) {
            float scale = Math.min((float) max / w, (float) max / h);
            bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * scale), Math.round(h * scale), true);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.JPEG, 75, bos);
        return bos.toByteArray();
    }

    public static byte[] readAll(Context ctx, Uri uri) throws Exception {
        InputStream is = ctx.getContentResolver().openInputStream(uri);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        if (is != null) is.close();
        return bos.toByteArray();
    }

    public static String fileName(Context ctx, Uri uri) {
        String name = null;
        try {
            android.database.Cursor c = ctx.getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                c.close();
            }
        } catch (Exception ignored) {
        }
        if (name == null || name.isEmpty()) name = "file";
        return name;
    }

    public static String fmtDur(int sec) {
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    public static String fmtSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / 1048576.0);
    }

    public static long dirSize(File dir) {
        long total = 0;
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) total += f.isDirectory() ? dirSize(f) : f.length();
        return total;
    }
}
