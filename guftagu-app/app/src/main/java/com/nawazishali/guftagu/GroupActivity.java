package com.nawazishali.guftagu;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GroupActivity extends Activity {

    private static final String ROOM = "group";
    private String myUid, myName;
    private LocalDb db;
    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText etMessage;
    private Button btnSend;
    private final Map<String, JSONObject> directory = new HashMap<>();
    private final Handler handler = new Handler();
    private boolean alive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        myUid = Session.uid(this);
        myName = Session.name(this);
        if (myUid == null) { finish(); return; }

        ((TextView) findViewById(R.id.tvChatTitle)).setText("Sab ka Group 👥");
        ((TextView) findViewById(R.id.tvChatSub)).setText("🔒 Group encryption — naye members puranay messages nahi parh sakte");
        msgList = findViewById(R.id.msgList);
        scroll = findViewById(R.id.chatScroll);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSafety).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("🔒 Group Encryption")
                        .setMessage("Har message ek nayi random key se lock hota hai, aur wo key har member ke liye alag se uski public key se band kar bheji jati hai. Server par sirf band (encrypted) text jata hai — server, admin, koi bhi message nahi parh sakta. Jo member baad mein join kare, wo puranay messages nahi parh sakta.")
                        .setPositiveButton("Theek hai", null)
                        .show());

        db = new LocalDb(this);

        try {
            JSONObject me = new JSONObject();
            me.put("name", myName == null ? "" : myName);
            Api.dbPut(this, "/group/members/" + myUid, me, (ok, err) -> { });
        } catch (Exception ignored) {
        }

        btnSend.setOnClickListener(v -> sendMessage());
        pollCycle();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!alive) return;
                pollCycle();
                handler.postDelayed(this, 3500);
            }
        }, 3500);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    private void pollCycle() {
        Api.dbGet(this, "/users", (users, err) -> {
            if (users != null) {
                directory.clear();
                Iterator<String> it = users.keys();
                while (it.hasNext()) {
                    String uid = it.next();
                    JSONObject u = users.optJSONObject(uid);
                    if (u != null) directory.put(uid, u);
                }
            }
            fetchMessages();
        });
    }

    private void fetchMessages() {
        Api.dbGetQ(this, "/group/messages", "orderBy=%22ts%22&limitToLast=100", (msgs, err) -> {
            if (msgs == null || err != null) return;
            List<String> ids = new ArrayList<>();
            Iterator<String> it = msgs.keys();
            while (it.hasNext()) ids.add(it.next());
            Collections.sort(ids, (a, b) -> Long.compare(
                    msgs.optJSONObject(a).optLong("ts", 0), msgs.optJSONObject(b).optLong("ts", 0)));
            boolean added = false;
            for (String id : ids) {
                if (db.hasMessage(ROOM, id)) continue;
                JSONObject m = msgs.optJSONObject(id);
                if (m == null) continue;
                String from = m.optString("from", "");
                long ts = m.optLong("ts", System.currentTimeMillis());
                String plain = decryptGroupMessage(m);
                db.addMessage(ROOM, id, from,
                        plain != null ? plain : "🔒 Ye message tumhare group join karne se pehle ka hai — nahi khul sakta",
                        ts);
                added = true;
            }
            if (added || msgList.getChildCount() == 0) {
                renderAll();
                if (added) scrollToBottom();
            }
        });
    }

    /** Returns the plaintext, or null when this phone has no wrapped key for the message. */
    private String decryptGroupMessage(JSONObject m) {
        try {
            JSONObject wraps = m.optJSONObject("wraps");
            if (wraps == null || !wraps.has(myUid)) return null;
            String from = m.optString("from", "");
            JSONObject senderProfile = directory.get(from);
            String senderPub = senderProfile != null ? senderProfile.optString("pub", "") : "";
            if (senderPub.isEmpty() && from.equals(myUid)) senderPub = Crypto.myPubB64(this);
            if (senderPub.isEmpty()) return null;
            byte[] wrapKey = Crypto.groupWrapKey(Crypto.myPriv(this), Crypto.unb64(senderPub));
            JSONObject w = wraps.getJSONObject(myUid);
            byte[] msgKey = Crypto.aesGcmDecrypt(wrapKey, Crypto.unb64(w.getString("n")),
                    Crypto.unb64(w.getString("w")), "groupwrap".getBytes(StandardCharsets.UTF_8));
            byte[] pt = Crypto.aesGcmDecrypt(msgKey, Crypto.unb64(m.getString("n")),
                    Crypto.unb64(m.getString("c")), "guftagu-group".getBytes(StandardCharsets.UTF_8));
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private void sendMessage() {
        final String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        btnSend.setEnabled(false);
        Api.dbGet(this, "/group/members", (members, err) -> {
            if (members == null) {
                btnSend.setEnabled(true);
                Toast.makeText(this, "Members load nahi hue — internet check karo", Toast.LENGTH_SHORT).show();
                return;
            }
            Api.dbGet(this, "/users", (users, err2) -> {
                try {
                    if (users != null) {
                        directory.clear();
                        Iterator<String> it = users.keys();
                        while (it.hasNext()) {
                            String uid = it.next();
                            JSONObject u = users.optJSONObject(uid);
                            if (u != null) directory.put(uid, u);
                        }
                    }
                    byte[] msgKey = Crypto.randomBytes(32);
                    byte[] nonce = Crypto.randomBytes(12);
                    byte[] ct = Crypto.aesGcmEncrypt(msgKey, nonce,
                            text.getBytes(StandardCharsets.UTF_8),
                            "guftagu-group".getBytes(StandardCharsets.UTF_8));
                    JSONObject wraps = new JSONObject();
                    Iterator<String> mit = members.keys();
                    while (mit.hasNext()) {
                        String memberUid = mit.next();
                        JSONObject prof = directory.get(memberUid);
                        String pub = prof != null ? prof.optString("pub", "") : "";
                        if (pub.isEmpty()) continue;
                        try {
                            byte[] wk = Crypto.groupWrapKey(Crypto.myPriv(this), Crypto.unb64(pub));
                            byte[] wn = Crypto.randomBytes(12);
                            byte[] wrapped = Crypto.aesGcmEncrypt(wk, wn, msgKey,
                                    "groupwrap".getBytes(StandardCharsets.UTF_8));
                            JSONObject w = new JSONObject();
                            w.put("n", Crypto.b64(wn));
                            w.put("w", Crypto.b64(wrapped));
                            wraps.put(memberUid, w);
                        } catch (Exception ignored) {
                        }
                    }
                    JSONObject m = new JSONObject();
                    m.put("from", myUid);
                    m.put("name", myName == null ? "" : myName);
                    m.put("n", Crypto.b64(nonce));
                    m.put("c", Crypto.b64(ct));
                    m.put("wraps", wraps);
                    long ts = System.currentTimeMillis();
                    m.put("ts", ts);
                    Api.dbPost(this, "/group/messages", m, (pushId, e3) -> {
                        btnSend.setEnabled(true);
                        if (e3 != null) {
                            Toast.makeText(this, "Message nahi gaya — internet check karo", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        db.addMessage(ROOM, pushId, myUid, text, ts);
                        etMessage.setText("");
                        renderAll();
                        scrollToBottom();
                    });
                } catch (Exception ex) {
                    btnSend.setEnabled(true);
                    Toast.makeText(this, "Encryption masla — message nahi gaya", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void renderAll() {
        msgList.removeAllViews();
        for (LocalDb.Msg m : db.messages(ROOM)) msgList.addView(bubble(m));
    }

    private LinearLayout bubble(LocalDb.Msg m) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(6);
        wrap.setLayoutParams(wlp);

        boolean mine = m.sender.equals(myUid);
        TextView tv = new TextView(this);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.ts));
        String label = "";
        if (!mine) {
            JSONObject prof = directory.get(m.sender);
            String n = prof != null ? prof.optString("name", "") : "";
            if (n.isEmpty() && m.body != null) n = "";
            label = n.isEmpty() ? "" : n + "\n";
        }
        tv.setText(label + m.body + "\n" + time);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(15);
        tv.setBackgroundResource(mine ? R.drawable.bubble_out : R.drawable.bubble_in);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.leftMargin = mine ? dp(56) : 0;
        lp.rightMargin = mine ? 0 : dp(56);
        tv.setLayoutParams(lp);
        wrap.addView(tv);
        return wrap;
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
