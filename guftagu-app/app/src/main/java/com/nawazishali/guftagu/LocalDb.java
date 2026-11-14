package com.nawazishali.guftagu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Local copy of decrypted messages (plaintext lives only on this phone).
 * The server only ever stores ciphertext.
 * v3 adds per-message reactions (JSON: uid -> emoji).
 */
public class LocalDb extends SQLiteOpenHelper {

    public static class Msg {
        public String msgid;
        public String sender;
        public String body;
        public long ts;
        public long idx = -1;
        public String react = "";
    }

    public LocalDb(Context ctx) {
        super(ctx, "guftagu.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages(room TEXT NOT NULL, msgid TEXT NOT NULL, " +
                "sender TEXT NOT NULL, body TEXT NOT NULL, ts INTEGER NOT NULL, " +
                "idx INTEGER DEFAULT -1, react TEXT DEFAULT '', PRIMARY KEY(room, msgid))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        if (oldV < 2) db.execSQL("ALTER TABLE messages ADD COLUMN idx INTEGER DEFAULT -1");
        if (oldV < 3) db.execSQL("ALTER TABLE messages ADD COLUMN react TEXT DEFAULT ''");
    }

    public void addMessage(String room, String msgid, String sender, String body, long ts) {
        addMessage(room, msgid, sender, body, ts, -1);
    }

    public void addMessage(String room, String msgid, String sender, String body, long ts, long idx) {
        ContentValues v = new ContentValues();
        v.put("room", room);
        v.put("msgid", msgid);
        v.put("sender", sender);
        v.put("body", body);
        v.put("ts", ts);
        v.put("idx", idx);
        getWritableDatabase().insertWithOnConflict("messages", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public boolean hasMessage(String room, String msgid) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT 1 FROM messages WHERE room=? AND msgid=? LIMIT 1",
                new String[]{room, msgid});
        boolean has = c.moveToFirst();
        c.close();
        return has;
    }

    private Msg readRow(Cursor c) {
        Msg m = new Msg();
        m.msgid = c.getString(1);
        m.sender = c.getString(2);
        m.body = c.getString(3);
        m.ts = c.getLong(4);
        m.idx = c.getLong(5);
        String r = c.getString(6);
        m.react = r == null ? "" : r;
        return m;
    }

    public Msg getMsg(String room, String msgid) {
        Cursor c = getReadableDatabase().query("messages", null, "room=? AND msgid=?",
                new String[]{room, msgid}, null, null, null, "1");
        Msg m = null;
        if (c.moveToFirst()) m = readRow(c);
        c.close();
        return m;
    }

    public List<Msg> messages(String room) {
        List<Msg> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null, "room=?",
                new String[]{room}, null, null, "ts ASC");
        while (c.moveToNext()) out.add(readRow(c));
        c.close();
        return out;
    }

    public Msg lastMessage(String room) {
        Cursor c = getReadableDatabase().query("messages", null, "room=?",
                new String[]{room}, null, null, "ts DESC", "1");
        Msg m = null;
        if (c.moveToFirst()) m = readRow(c);
        c.close();
        return m;
    }

    public void updateBody(String room, String msgid, String body) {
        ContentValues v = new ContentValues();
        v.put("body", body);
        getWritableDatabase().update("messages", v, "room=? AND msgid=?",
                new String[]{room, msgid});
    }

    /** Sets one user's reaction on a message (empty emoji = remove). Idempotent. */
    public void setReact(String room, String msgid, String uid, String emoji) {
        Msg m = getMsg(room, msgid);
        if (m == null) return;
        try {
            JSONObject r = m.react.isEmpty() ? new JSONObject() : new JSONObject(m.react);
            if (emoji == null || emoji.isEmpty()) r.remove(uid);
            else r.put(uid, emoji);
            ContentValues v = new ContentValues();
            v.put("react", r.length() == 0 ? "" : r.toString());
            getWritableDatabase().update("messages", v, "room=? AND msgid=?",
                    new String[]{room, msgid});
        } catch (Exception ignored) {
        }
    }

    /** Replaces a text message's content with an edited version (keeps any quote). */
    public void applyEdit(String room, String msgid, String newText) {
        Msg m = getMsg(room, msgid);
        if (m == null) return;
        JSONObject p = MediaHelper.parsePayload(m.body);
        if (p != null && !"text".equals(p.optString("t"))) return; // media can't be edited
        try {
            JSONObject np = new JSONObject();
            np.put("t", "text");
            np.put("x", newText);
            np.put("ed", true);
            if (p != null) {
                if (p.has("q")) np.put("q", p.getJSONObject("q"));
                if (p.optBoolean("fwd")) np.put("fwd", true);
            }
            updateBody(room, msgid, np.toString());
        } catch (Exception ignored) {
        }
    }

    public void applyDelete(String room, String msgid) {
        Msg m = getMsg(room, msgid);
        if (m == null) return;
        ContentValues v = new ContentValues();
        v.put("body", "{\"t\":\"deleted\"}");
        v.put("react", "");
        getWritableDatabase().update("messages", v, "room=? AND msgid=?",
                new String[]{room, msgid});
    }

    public void deleteMessage(String room, String msgid) {
        getWritableDatabase().delete("messages", "room=? AND msgid=?",
                new String[]{room, msgid});
    }

    /** Messages in a room older than the cutoff (for disappearing-message pruning). */
    public List<Msg> expired(String room, long cutoff) {
        List<Msg> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null, "room=? AND ts<?",
                new String[]{room, String.valueOf(cutoff)}, null, null, "ts ASC");
        while (c.moveToNext()) out.add(readRow(c));
        c.close();
        return out;
    }

    public static class Hit {
        public String room;
        public Msg msg;
    }

    /** Raw candidate matches for global search (callers filter on display text). */
    public List<Hit> search(String q) {
        List<Hit> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT * FROM messages WHERE body LIKE ? ORDER BY ts DESC LIMIT 300",
                new String[]{"%" + q + "%"});
        while (c.moveToNext()) {
            Hit h = new Hit();
            h.room = c.getString(0);
            h.msg = readRow(c);
            out.add(h);
        }
        c.close();
        return out;
    }

    public int countAll() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM messages", null);
        int n = 0;
        if (c.moveToFirst()) n = c.getInt(0);
        c.close();
        return n;
    }

    public void clearAll() {
        getWritableDatabase().delete("messages", null, null);
    }
}
