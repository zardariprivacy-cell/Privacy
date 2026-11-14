package com.nawazishali.guftagu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends Activity {

    private String myUid, peerUid, peerName, peerPub, roomId;
    private Ratchet ratchet;
    private LocalDb db;
    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText etMessage;
    private Button btnSend;
    private final Handler handler = new Handler();
    private boolean alive = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        Intent in = getIntent();
        peerUid = in.getStringExtra("peerUid");
        peerName = in.getStringExtra("peerName");
        peerPub = in.getStringExtra("peerPub");
        myUid = Session.uid(this);
        if (myUid == null || peerUid == null) { finish(); return; }
        roomId = myUid.compareTo(peerUid) < 0 ? myUid + "_" + peerUid : peerUid + "_" + myUid;

        ((TextView) findViewById(R.id.tvChatTitle)).setText(peerName == null || peerName.isEmpty() ? "Chat" : peerName);
        TextView tvSub = findViewById(R.id.tvChatSub);
        msgList = findViewById(R.id.msgList);
        scroll = findViewById(R.id.chatScroll);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSafety).setOnClickListener(v -> showSafety());

        db = new LocalDb(this);

        if (peerPub == null || peerPub.isEmpty()) {
            tvSub.setText("⚠️ Is user ne abhi app nahi khola — unki encryption key nahi hai");
            etMessage.setEnabled(false);
            btnSend.setEnabled(false);
            renderAll();
            return;
        }

        Api.dbPutBool(this, "/rooms/" + roomId + "/members/" + myUid, true, (ok, err) -> { });

        try {
            String stored = Ratchet.storedPeerPub(this, myUid, roomId);
            ratchet = Ratchet.open(this, myUid, roomId, peerUid, peerPub, Crypto.myPriv(this));
            if (stored != null && !stored.equals(peerPub)) {
                db.addMessage(roomId, "sys" + System.currentTimeMillis(), "sys",
                        "🔒 Security code badal gaya — saathi ka naya phone ya nayi install hai. 🔑 button se safety number mila kar check kar lo.",
                        System.currentTimeMillis());
            }
        } catch (Exception e) {
            tvSub.setText("⚠️ Encryption shuru nahi ho saka");
            etMessage.setEnabled(false);
            btnSend.setEnabled(false);
            return;
        }

        btnSend.setOnClickListener(v -> sendMessage());
        renderAll();
        poll();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!alive) return;
                poll();
                handler.postDelayed(this, 3000);
            }
        }, 3000);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    private void showSafety() {
        if (peerPub == null || peerPub.isEmpty()) {
            Toast.makeText(this, "Saathi ki key abhi nahi hai", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String num = Crypto.safetyNumber(Crypto.myPubBytes(this), Crypto.unb64(peerPub));
            new AlertDialog.Builder(this)
                    .setTitle("🔑 Safety Number")
                    .setMessage(num + "\n\nYe number tumhare aur " + peerName + " ke phone par ek jaisa hona chahiye. " +
                            "Phone call par mila kar confirm karo — number ek jaisa hai to beech mein koi tumhari baat nahi sun sakta, server bhi nahi.")
                    .setPositiveButton("Theek hai", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Safety number nahi ban saka", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || ratchet == null) return;
        btnSend.setEnabled(false);
        try {
            Ratchet.Encrypted e = ratchet.encrypt(text);
            JSONObject m = new JSONObject();
            m.put("from", myUid);
            m.put("idx", e.idx);
            m.put("body", e.bodyB64);
            long ts = System.currentTimeMillis();
            m.put("ts", ts);
            Api.dbPost(this, "/rooms/" + roomId + "/messages", m, (pushId, err) -> {
                btnSend.setEnabled(true);
                if (err != null) {
                    Toast.makeText(this, "Message nahi gaya — internet check karo", Toast.LENGTH_SHORT).show();
                    return;
                }
                db.addMessage(roomId, pushId, myUid, text, ts);
                etMessage.setText("");
                renderAll();
                scrollToBottom();
            });
        } catch (Exception ex) {
            btnSend.setEnabled(true);
            Toast.makeText(this, "Encryption masla — message nahi gaya", Toast.LENGTH_SHORT).show();
        }
    }

    private void poll() {
        if (ratchet == null) return;
        Api.dbGetQ(this, "/rooms/" + roomId + "/messages", "orderBy=%22ts%22&limitToLast=100", (msgs, err) -> {
            if (msgs == null || err != null) return;
            List<String> ids = new ArrayList<>();
            Iterator<String> it = msgs.keys();
            while (it.hasNext()) ids.add(it.next());
            Collections.sort(ids, (a, b) -> Long.compare(
                    msgs.optJSONObject(a).optLong("ts", 0), msgs.optJSONObject(b).optLong("ts", 0)));
            boolean added = false;
            for (String id : ids) {
                if (db.hasMessage(roomId, id)) continue;
                JSONObject m = msgs.optJSONObject(id);
                if (m == null) continue;
                String from = m.optString("from", "");
                long ts = m.optLong("ts", System.currentTimeMillis());
                if (from.equals(myUid)) {
                    db.addMessage(roomId, id, myUid, "📱 (ye message tumhare dusre phone se gaya tha)", ts);
                    added = true;
                } else if (from.equals(peerUid)) {
                    String plain = ratchet.decrypt(m.optLong("idx", -1), m.optString("body", ""), from);
                    db.addMessage(roomId, id, from,
                            plain != null ? plain : "🔒 Encrypted message — khul nahi saka", ts);
                    added = true;
                }
            }
            if (added) {
                renderAll();
                scrollToBottom();
            }
        });
    }

    private void renderAll() {
        msgList.removeAllViews();
        for (LocalDb.Msg m : db.messages(roomId)) msgList.addView(bubble(m));
    }

    private LinearLayout bubble(LocalDb.Msg m) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(6);
        wrap.setLayoutParams(wlp);

        if ("sys".equals(m.sender)) {
            TextView tv = new TextView(this);
            tv.setText(m.body);
            tv.setTextColor(0xFFF5B81D);
            tv.setTextSize(12);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(dp(16), dp(4), dp(16), dp(4));
            wrap.addView(tv);
            return wrap;
        }

        boolean mine = m.sender.equals(myUid);
        TextView tv = new TextView(this);
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.ts));
        tv.setText(m.body + "\n" + time);
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
