package com.nawazishali.guftagu;

import android.app.Activity;

import org.json.JSONObject;

/** Sends an existing message body into another 1-to-1 chat (end-to-end encrypted). */
public class Forwarder {

    public interface Done {
        void onDone(boolean ok);
    }

    public static void forwardToUser(final Activity a, final String targetUid, final String targetPub,
                                     final String body, final Done done) {
        final String myUid = Session.uid(a);
        if (myUid == null || targetPub == null || targetPub.isEmpty()) {
            done.onDone(false);
            return;
        }
        final String room = myUid.compareTo(targetUid) < 0
                ? myUid + "_" + targetUid : targetUid + "_" + myUid;
        Api.dbPutBool(a, "/rooms/" + room + "/members/" + myUid, true, (ok, e0) -> { });
        try {
            Ratchet r = Ratchet.open(a, myUid, room, targetUid, targetPub, Crypto.myPriv(a));
            Ratchet.Encrypted enc = r.encrypt(body);
            JSONObject m = new JSONObject();
            m.put("from", myUid);
            m.put("idx", enc.idx);
            m.put("body", enc.bodyB64);
            final long ts = System.currentTimeMillis();
            m.put("ts", ts);
            final long idx = enc.idx;
            Api.dbPost(a, "/rooms/" + room + "/messages", m, (pushId, err) -> {
                if (err != null) {
                    done.onDone(false);
                    return;
                }
                new LocalDb(a).addMessage(room, pushId, myUid, body, ts, idx);
                done.onDone(true);
            });
        } catch (Exception e) {
            done.onDone(false);
        }
    }
}
