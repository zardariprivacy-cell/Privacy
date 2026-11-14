package com.nawazishali.guftagu;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
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
    private static final int REQ_PHOTO = 51;
    private static final int REQ_FILE = 52;
    private static final int REQ_MIC = 53;

    private String myUid, myName;
    private LocalDb db;
    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText etMessage;
    private Button btnSend, btnMic, btnAttach;
    private LinearLayout inputBar, recordBar;
    private TextView tvRecTime, tvSub;
    private final Map<String, JSONObject> directory = new HashMap<>();
    private final Handler handler = new Handler();
    private boolean alive = true;

    private MediaRecorder recorder;
    private File recFile;
    private long recStartMs;
    private boolean recording = false;
    private Runnable recTicker;
    private MediaPlayer player;
    private String playingId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        myUid = Session.uid(this);
        myName = Session.name(this);
        if (myUid == null) { finish(); return; }

        ((TextView) findViewById(R.id.tvChatTitle)).setText("Sab ka Group 👥");
        tvSub = findViewById(R.id.tvChatSub);
        tvSub.setText("🔒 Group encryption — naye members puranay messages nahi parh sakte");
        msgList = findViewById(R.id.msgList);
        scroll = findViewById(R.id.chatScroll);
        scroll.setBackgroundColor(AppPrefs.wallpaperColor(this));
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnMic = findViewById(R.id.btnMic);
        btnAttach = findViewById(R.id.btnAttach);
        inputBar = findViewById(R.id.inputBar);
        recordBar = findViewById(R.id.recordBar);
        tvRecTime = findViewById(R.id.tvRecTime);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSafety).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("🔒 Group Encryption")
                        .setMessage("Har message ek nayi random key se lock hota hai, aur wo key har member ke liye alag se uski public key se band kar bheji jati hai. Server par sirf band (encrypted) text jata hai — server, admin, koi bhi message nahi parh sakta. Jo member baad mein join kare, wo puranay messages nahi parh sakta.")
                        .setPositiveButton("Theek hai", null)
                        .show());

        db = new LocalDb(this);
        Presence.beat(this);

        try {
            JSONObject me = new JSONObject();
            me.put("name", myName == null ? "" : myName);
            Api.dbPut(this, "/group/members/" + myUid, me, (ok, err) -> { });
        } catch (Exception ignored) {
        }

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) sendGroupPayload(text, () -> etMessage.setText(""));
        });
        btnAttach.setOnClickListener(v -> showAttachMenu());
        btnMic.setOnClickListener(v -> onMicTap());
        findViewById(R.id.btnRecCancel).setOnClickListener(v -> cancelRecording());
        findViewById(R.id.btnRecSend).setOnClickListener(v -> sendRecording());

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
        stopPlayback();
        if (recording) cancelRecording();
        super.onDestroy();
    }

    // ---------------- group send (text or media payload) ----------------

    private void sendGroupPayload(final String plainBody, final Runnable onOk) {
        Api.dbGet(this, "/group/members", (members, err) -> {
            if (members == null) {
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
                            plainBody.getBytes(StandardCharsets.UTF_8),
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
                        if (e3 != null) {
                            Toast.makeText(this, "Message nahi gaya — internet check karo", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        db.addMessage(ROOM, pushId, myUid, plainBody, ts);
                        if (onOk != null) onOk.run();
                        renderAll();
                        scrollToBottom();
                    });
                } catch (Exception ex) {
                    Toast.makeText(this, "Encryption masla — message nahi gaya", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showAttachMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Kya bhejna hai?")
                .setItems(new String[]{"📷 Photo", "📎 File (1 MB tak)"}, (d, which) -> {
                    Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                    if (which == 0) {
                        pick.setType("image/*");
                        startActivityForResult(pick, REQ_PHOTO);
                    } else {
                        pick.setType("*/*");
                        startActivityForResult(pick, REQ_FILE);
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_PHOTO) {
            Toast.makeText(this, "Photo tayyar ho raha hai...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                try {
                    byte[] bytes = MediaHelper.prepPhoto(this, uri);
                    runOnUiThread(() -> MediaHelper.uploadBlob(this, bytes, (blobId, keyB64, err) -> {
                        if (err != null) {
                            Toast.makeText(this, "Photo nahi gaya", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        sendGroupPayload(MediaHelper.photoPayload(blobId, keyB64), null);
                    }));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Photo parhi nahi gayi", Toast.LENGTH_SHORT).show());
                }
            }).start();
        } else if (requestCode == REQ_FILE) {
            new Thread(() -> {
                try {
                    byte[] bytes = MediaHelper.readAll(this, uri);
                    final String name = MediaHelper.fileName(this, uri);
                    if (bytes.length > MediaHelper.MAX_FILE_BYTES) {
                        runOnUiThread(() -> Toast.makeText(this,
                                "File bohat bari hai — sirf 1 MB tak ja sakti hai",
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                    runOnUiThread(() -> MediaHelper.uploadBlob(this, bytes, (blobId, keyB64, err) -> {
                        if (err != null) {
                            Toast.makeText(this, "File nahi gayi", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        sendGroupPayload(MediaHelper.filePayload(blobId, keyB64, name, bytes.length), null);
                    }));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "File parhi nahi gayi", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    // ---------------- voice recording ----------------

    private void onMicTap() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_MIC && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else if (requestCode == REQ_MIC) {
            Toast.makeText(this, "Mic ki ijazat ke baghair voice message nahi ban sakta", Toast.LENGTH_LONG).show();
        }
    }

    private void startRecording() {
        try {
            recFile = new File(getCacheDir(), "rec_" + System.currentTimeMillis() + ".3gp");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
            recorder.setOutputFile(recFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            recStartMs = System.currentTimeMillis();
            inputBar.setVisibility(View.GONE);
            recordBar.setVisibility(View.VISIBLE);
            recTicker = new Runnable() {
                @Override
                public void run() {
                    if (!recording) return;
                    int sec = (int) ((System.currentTimeMillis() - recStartMs) / 1000);
                    tvRecTime.setText("● " + MediaHelper.fmtDur(sec) + "  — record ho raha hai");
                    if (sec >= 120) {
                        sendRecording();
                        return;
                    }
                    handler.postDelayed(this, 500);
                }
            };
            handler.post(recTicker);
        } catch (Exception e) {
            Toast.makeText(this, "Recording shuru nahi hui", Toast.LENGTH_SHORT).show();
            recording = false;
        }
    }

    private void stopRecorderQuiet() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        } catch (Exception ignored) {
        }
        recorder = null;
        recording = false;
        if (recTicker != null) handler.removeCallbacks(recTicker);
        inputBar.setVisibility(View.VISIBLE);
        recordBar.setVisibility(View.GONE);
    }

    private void cancelRecording() {
        stopRecorderQuiet();
        if (recFile != null) recFile.delete();
    }

    private void sendRecording() {
        final int durSec = Math.max(1, (int) ((System.currentTimeMillis() - recStartMs) / 1000));
        stopRecorderQuiet();
        if (recFile == null || !recFile.exists()) return;
        new Thread(() -> {
            try {
                byte[] bytes = MediaHelper.readAll(this, Uri.fromFile(recFile));
                final File recorded = recFile;
                runOnUiThread(() -> MediaHelper.uploadBlob(this, bytes, (blobId, keyB64, err) -> {
                    if (err != null) {
                        Toast.makeText(this, "Voice message nahi gaya", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        File dst = MediaHelper.cacheFile(this, blobId);
                        java.nio.file.Files.copy(recorded.toPath(), dst.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }
                    recorded.delete();
                    sendGroupPayload(MediaHelper.voicePayload(blobId, keyB64, durSec), null);
                }));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Voice message parha nahi gaya", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ---------------- receiving ----------------

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
            Api.dbGet(this, "/group/members", (members, e2) -> {
                if (members != null) {
                    tvSub.setText(members.length() + " members • 🔒 group encryption");
                }
                fetchMessages();
            });
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
                if (AppPrefs.isBlocked(this, from)) continue;
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

    // ---------------- rendering ----------------

    private void renderAll() {
        msgList.removeAllViews();
        for (LocalDb.Msg m : db.messages(ROOM)) msgList.addView(bubble(m));
    }

    private LinearLayout bubble(final LocalDb.Msg m) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(6);
        wrap.setLayoutParams(wlp);

        boolean mine = m.sender.equals(myUid);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(mine ? R.drawable.bubble_out : R.drawable.bubble_in);
        card.setPadding(dp(12), dp(8), dp(12), dp(6));

        if (!mine) {
            JSONObject prof = directory.get(m.sender);
            String n = prof != null ? prof.optString("name", "") : "";
            if (!n.isEmpty()) {
                TextView nameTv = new TextView(this);
                nameTv.setText(n);
                nameTv.setTextColor(0xFFF5B81D);
                nameTv.setTextSize(12);
                card.addView(nameTv);
            }
        }

        JSONObject payload = MediaHelper.parsePayload(m.body);
        View content;
        if (payload == null) {
            TextView tv = new TextView(this);
            tv.setText(m.body);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTextSize(AppPrefs.bubbleTextSize(this));
            content = tv;
        } else if ("voice".equals(payload.optString("t"))) {
            content = voiceView(m, payload);
        } else if ("photo".equals(payload.optString("t"))) {
            content = photoView(m, payload);
        } else {
            content = fileView(m, payload);
        }
        card.addView(content);

        TextView foot = new TextView(this);
        foot.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.ts)));
        foot.setTextColor(0xFFB9C6BC);
        foot.setTextSize(11);
        foot.setGravity(Gravity.END);
        card.addView(foot);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.leftMargin = mine ? dp(56) : 0;
        lp.rightMargin = mine ? 0 : dp(56);
        card.setLayoutParams(lp);
        wrap.addView(card);
        return wrap;
    }

    private View voiceView(final LocalDb.Msg m, final JSONObject payload) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView play = new TextView(this);
        play.setText(m.msgid.equals(playingId) ? "⏸" : "▶");
        play.setTextColor(0xFFFFFFFF);
        play.setTextSize(20);
        play.setPadding(dp(2), dp(2), dp(10), dp(2));
        row.addView(play);
        TextView label = new TextView(this);
        label.setText("🎤 Voice message • " + MediaHelper.fmtDur(payload.optInt("dur", 0)));
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(AppPrefs.bubbleTextSize(this));
        row.addView(label);
        final String blobId = payload.optString("b", "");
        final String key = payload.optString("k", "");
        row.setOnClickListener(v -> {
            if (m.msgid.equals(playingId)) {
                stopPlayback();
                renderAll();
                return;
            }
            MediaHelper.fetchBlob(this, blobId, key, (file, err) -> {
                if (file == null) {
                    Toast.makeText(this, "Voice download nahi hua", Toast.LENGTH_SHORT).show();
                    return;
                }
                playFile(m.msgid, file);
            });
        });
        if (!MediaHelper.cacheFile(this, blobId).exists() && (AppPrefs.autoDlVoice(this) || m.sender.equals(myUid))) {
            MediaHelper.fetchBlob(this, blobId, key, (f, e) -> { });
        }
        return row;
    }

    private View photoView(final LocalDb.Msg m, final JSONObject payload) {
        final String blobId = payload.optString("b", "");
        final String key = payload.optString("k", "");
        final ImageView iv = new ImageView(this);
        iv.setAdjustViewBounds(true);
        iv.setMaxWidth(dp(230));
        iv.setMinimumWidth(dp(160));
        iv.setMinimumHeight(dp(120));
        File cached = MediaHelper.cacheFile(this, blobId);
        if (cached.exists()) {
            Bitmap bmp = decodeSampled(cached, 460);
            if (bmp != null) iv.setImageBitmap(bmp);
            else iv.setImageResource(android.R.drawable.ic_menu_gallery);
        } else {
            iv.setImageResource(android.R.drawable.ic_menu_gallery);
            if (AppPrefs.autoDlPhoto(this) || m.sender.equals(myUid)) {
                MediaHelper.fetchBlob(this, blobId, key, (file, err) -> {
                    if (file != null && alive) renderAll();
                });
            }
        }
        iv.setOnClickListener(v -> MediaHelper.fetchBlob(this, blobId, key, (file, err) -> {
            if (file == null) {
                Toast.makeText(this, "Photo download nahi hua", Toast.LENGTH_SHORT).show();
                return;
            }
            Bitmap big = decodeSampled(file, 1400);
            ImageView full = new ImageView(this);
            if (big != null) full.setImageBitmap(big);
            new AlertDialog.Builder(this).setView(full).setPositiveButton("Band karo", null).show();
        }));
        return iv;
    }

    private View fileView(final LocalDb.Msg m, final JSONObject payload) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText("📎 " + payload.optString("name", "File"));
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(AppPrefs.bubbleTextSize(this));
        col.addView(name);
        TextView sub = new TextView(this);
        sub.setText(MediaHelper.fmtSize(payload.optLong("size", 0)) + " • Download ke liye tap karo");
        sub.setTextColor(0xFFB9C6BC);
        sub.setTextSize(12);
        col.addView(sub);
        final String blobId = payload.optString("b", "");
        final String key = payload.optString("k", "");
        col.setOnClickListener(v -> MediaHelper.fetchBlob(this, blobId, key, (file, err) -> {
            if (file == null) {
                Toast.makeText(this, "File download nahi hui", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                File dir = new File(getExternalFilesDir(null), "GuftaguDownloads");
                if (!dir.exists()) dir.mkdirs();
                File dst = new File(dir, payload.optString("name", "file"));
                java.nio.file.Files.copy(file.toPath(), dst.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Toast.makeText(this, "Save ho gayi: GuftaguDownloads folder mein (Files app se kholo)", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Save nahi hui", Toast.LENGTH_SHORT).show();
            }
        }));
        return col;
    }

    private Bitmap decodeSampled(File f, int targetW) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / sample > targetW) sample *= 2;
            o.inSampleSize = sample;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    private void playFile(String msgid, File file) {
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                playingId = null;
                if (alive) renderAll();
            });
            player.prepare();
            player.start();
            playingId = msgid;
            renderAll();
        } catch (Exception e) {
            Toast.makeText(this, "Voice chal nahi saka", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        try {
            if (player != null) {
                player.stop();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        playingId = null;
    }

    private void scrollToBottom() {
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
