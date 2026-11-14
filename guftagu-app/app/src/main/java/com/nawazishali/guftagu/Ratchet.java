package com.nawazishali.guftagu;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Iterator;
import java.util.TreeMap;

/**
 * Double-ratchet-lite for one 1-to-1 conversation: a symmetric hash chain
 * per direction (Signal-style chain keys without the DH ratchet).
 * Every message gets its own AES-GCM key, then the chain key is replaced
 * by its hash — so a leaked current key cannot decrypt PAST messages
 * (forward secrecy for the past). Chain + skipped-key state lives in the
 * app's private preferences.
 */
public class Ratchet {

    private final Context ctx;
    private final String stateKey;
    private final String roomId;
    private final String myUid;

    private byte[] sendCK;
    private byte[] recvCK;
    private long sendIdx;
    private long recvIdx;
    private final TreeMap<Long, byte[]> skipped = new TreeMap<>();
    private String peerPubB64;

    private Ratchet(Context ctx, String myUid, String roomId) {
        this.ctx = ctx;
        this.myUid = myUid;
        this.roomId = roomId;
        this.stateKey = "ratchet_" + myUid + "_" + roomId;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("guftagu_ratchet", Context.MODE_PRIVATE);
    }

    public static String storedPeerPub(Context ctx, String myUid, String roomId) {
        try {
            String s = prefs(ctx).getString("ratchet_" + myUid + "_" + roomId, null);
            if (s == null) return null;
            return new JSONObject(s).optString("peerPub", null);
        } catch (Exception e) {
            return null;
        }
    }

    public static Ratchet open(Context ctx, String myUid, String roomId, String peerUid,
                               String peerPubB64, PrivateKey myPriv) throws Exception {
        Ratchet r = new Ratchet(ctx, myUid, roomId);
        String saved = prefs(ctx).getString(r.stateKey, null);
        if (saved != null) {
            JSONObject o = new JSONObject(saved);
            if (peerPubB64.equals(o.optString("peerPub"))) {
                r.peerPubB64 = peerPubB64;
                r.sendCK = Crypto.unb64(o.getString("sendCK"));
                r.recvCK = Crypto.unb64(o.getString("recvCK"));
                r.sendIdx = o.getLong("sendIdx");
                r.recvIdx = o.getLong("recvIdx");
                JSONObject sk = o.optJSONObject("skipped");
                if (sk != null) {
                    Iterator<String> it = sk.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        r.skipped.put(Long.parseLong(k), Crypto.unb64(sk.getString(k)));
                    }
                }
                return r;
            }
        }
        // Fresh session (first chat, or the peer's key changed = new phone/reinstall)
        byte[] z = Crypto.ecdh(myPriv, Crypto.unb64(peerPubB64));
        byte[] root = Crypto.hkdf(z, "guftagu-v1".getBytes(StandardCharsets.UTF_8), "root", 32);
        byte[] ckLow = Crypto.hkdf(root, null, "chain-low-high", 32);
        byte[] ckHigh = Crypto.hkdf(root, null, "chain-high-low", 32);
        if (myUid.compareTo(peerUid) < 0) {
            r.sendCK = ckLow; r.recvCK = ckHigh;
        } else {
            r.sendCK = ckHigh; r.recvCK = ckLow;
        }
        r.peerPubB64 = peerPubB64;
        r.sendIdx = 0;
        r.recvIdx = 0;
        r.persist();
        return r;
    }

    private void persist() {
        try {
            JSONObject o = new JSONObject();
            o.put("peerPub", peerPubB64);
            o.put("sendCK", Crypto.b64(sendCK));
            o.put("recvCK", Crypto.b64(recvCK));
            o.put("sendIdx", sendIdx);
            o.put("recvIdx", recvIdx);
            JSONObject sk = new JSONObject();
            for (Long k : skipped.keySet()) sk.put(String.valueOf(k), Crypto.b64(skipped.get(k)));
            o.put("skipped", sk);
            prefs(ctx).edit().putString(stateKey, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    public static class Encrypted {
        public long idx;
        public String bodyB64;
    }

    public synchronized Encrypted encrypt(String plain) throws Exception {
        byte[] mk = Crypto.hmac(sendCK, new byte[]{0x01});
        sendCK = Crypto.hmac(sendCK, new byte[]{0x02});
        long idx = sendIdx++;
        byte[] nonceFull = Crypto.hmac(mk, "nonce".getBytes(StandardCharsets.UTF_8));
        byte[] nonce = new byte[12];
        System.arraycopy(nonceFull, 0, nonce, 0, 12);
        byte[] aad = (roomId + "|" + idx + "|" + myUid).getBytes(StandardCharsets.UTF_8);
        byte[] ct = Crypto.aesGcmEncrypt(mk, nonce, plain.getBytes(StandardCharsets.UTF_8), aad);
        persist();
        Encrypted e = new Encrypted();
        e.idx = idx;
        e.bodyB64 = Crypto.b64(ct);
        return e;
    }

    public synchronized String decrypt(long idx, String bodyB64, String fromUid) {
        try {
            byte[] mk;
            if (skipped.containsKey(idx)) {
                mk = skipped.remove(idx);
            } else {
                if (idx < recvIdx) return null; // already passed and not cached
                if (idx - recvIdx > 200) return null; // unreasonable gap
                byte[] use = null;
                while (recvIdx <= idx) {
                    byte[] m = Crypto.hmac(recvCK, new byte[]{0x01});
                    recvCK = Crypto.hmac(recvCK, new byte[]{0x02});
                    if (recvIdx == idx) use = m;
                    else skipped.put(recvIdx, m);
                    recvIdx++;
                }
                mk = use;
            }
            persist();
            byte[] nonceFull = Crypto.hmac(mk, "nonce".getBytes(StandardCharsets.UTF_8));
            byte[] nonce = new byte[12];
            System.arraycopy(nonceFull, 0, nonce, 0, 12);
            byte[] aad = (roomId + "|" + idx + "|" + fromUid).getBytes(StandardCharsets.UTF_8);
            byte[] pt = Crypto.aesGcmDecrypt(mk, nonce, Crypto.unb64(bodyB64), aad);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
