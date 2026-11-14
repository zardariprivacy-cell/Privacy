package com.nawazishali.guftagu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Local copy of decrypted messages (plaintext lives only on this phone).
 * The server only ever stores ciphertext.
 */
public class LocalDb extends SQLiteOpenHelper {

    public static class Msg {
        public String msgid;
        public String sender;
        public String body;
        public long ts;
    }

    public LocalDb(Context ctx) {
        super(ctx, "guftagu.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages(room TEXT NOT NULL, msgid TEXT NOT NULL, " +
                "sender TEXT NOT NULL, body TEXT NOT NULL, ts INTEGER NOT NULL, " +
                "PRIMARY KEY(room, msgid))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
    }

    public void addMessage(String room, String msgid, String sender, String body, long ts) {
        ContentValues v = new ContentValues();
        v.put("room", room);
        v.put("msgid", msgid);
        v.put("sender", sender);
        v.put("body", body);
        v.put("ts", ts);
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

    public List<Msg> messages(String room) {
        List<Msg> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("messages", null, "room=?",
                new String[]{room}, null, null, "ts ASC");
        while (c.moveToNext()) {
            Msg m = new Msg();
            m.msgid = c.getString(0 + 1);
            m.sender = c.getString(2);
            m.body = c.getString(3);
            m.ts = c.getLong(4);
            out.add(m);
        }
        c.close();
        return out;
    }
}
