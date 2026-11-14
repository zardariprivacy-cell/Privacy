package com.nawazishali.guftagu;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ChatActivity extends Activity {

    private static final int REQ_PHOTO = 41;
    private static final int REQ_FILE = 42;
    private static final int REQ_MIC = 43;

    private String myUid, peerUid, peerName, peerPub, roomId;
    private Ratchet ratchet;
    private LocalDb db;
    private String replyMid, replyWho, replySnip;
    private android.view.View replyBar;
    private TextView tvReply;
    private long lastTypingMs = 0;
    private LinearLayout msgList;
    private ScrollView scroll;
    private EditText etMessage;
    private Button btnMicSend;
    private android.view.View btnAttach, btnCamera;
    private android.net.Uri pendingCamUri;
    private static final int REQ_CAMERA = 61;
    private static final int REQ_PERM_CAM = 62;
    private LinearLayout inputBar, recordBar;
    private TextView tvRecTime, tvSub;
    private final Handler handler = new Handler();
    private boolean alive = true;
    private boolean blocked = false;

    private long peerSeenIdx = -1;
    private long lastSeenSent = -1;
    private long maxPeerIdx = -1;
    private boolean firstPoll = true;

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
        Intent in = getIntent();
        peerUid = in.getStringExtra("peerUid");
        peerName = in.getStringExtra("peerName");
        peerPub = in.getStringExtra("peerPub");
        myUid = Session.uid(this);
        if (myUid == null || peerUid == null) { finish(); return; }
        roomId = myUid.compareTo(peerUid) < 0 ? myUid + "_" + peerUid : peerUid + "_" + myUid;
        blocked = AppPrefs.isBlocked(this, peerUid);

        ((TextView) findViewById(R.id.tvChatTitle)).setText(peerName == null || peerName.isEmpty() ? "Chat" : peerName);
        tvSub = findViewById(R.id.tvChatSub);
        msgList = findViewById(R.id.msgList);
        scroll = findViewById(R.id.chatScroll);
        AppPrefs.applyWallpaper(this, findViewById(R.id.wallpaperRoot));
        etMessage = findViewById(R.id.etMessage);
        btnMicSend = findViewById(R.id.btnMicSend);
        btnAttach = findViewById(R.id.btnAttach);
        btnCamera = findViewById(R.id.btnCamera);
        final android.widget.GridLayout emojiPanel = findViewById(R.id.emojiPanel);
        EmojiPanel.build(this, emojiPanel, etMessage);
        etMessage.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence cs, int a, int b, int c) { }
            public void onTextChanged(CharSequence cs, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable ed) {
                btnMicSend.setText(ed.toString().trim().isEmpty() ? "\uD83C\uDFA4" : "\u27A4");
                if (!ed.toString().trim().isEmpty()
                        && System.currentTimeMillis() - lastTypingMs > 4000) {
                    lastTypingMs = System.currentTimeMillis();
                    Presence.typing(ChatActivity.this, roomId);
                }
            }
        });
        inputBar = findViewById(R.id.inputBar);
        recordBar = findViewById(R.id.recordBar);
        tvRecTime = findViewById(R.id.tvRecTime);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSafety).setOnClickListener(v -> showSafety());

        db = new LocalDb(this);
        Presence.beat(this);

        if (blocked) {
            tvSub.setText("🚫 Tum ne is user ko block kiya hai");
            setInputEnabled(false);
        }

        if (peerPub == null || peerPub.isEmpty()) {
            tvSub.setText("⚠️ Is user ne abhi app nahi khola — unki encryption key nahi hai");
            setInputEnabled(false);
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
            setInputEnabled(false);
            return;
        }

        btnMicSend.setOnClickListener(v -> {
            if (!etMessage.getText().toString().trim().isEmpty()) sendText();
            else onMicTap();
        });
        btnAttach.setOnClickListener(v -> showAttachMenu());
        btnCamera.setOnClickListener(v -> openCamera());
        findViewById(R.id.btnEmoji).setOnClickListener(v ->
                emojiPanel.setVisibility(emojiPanel.getVisibility() == android.view.View.VISIBLE
                        ? android.view.View.GONE : android.view.View.VISIBLE));
        replyBar = findViewById(R.id.replyBar);
        tvReply = findViewById(R.id.tvReply);
        findViewById(R.id.btnReplyX).setOnClickListener(v -> clearReply());
        findViewById(R.id.btnRecCancel).setOnClickListener(v -> cancelRecording());
        findViewById(R.id.btnRecSend).setOnClickListener(v -> sendRecording());

        etMessage.setOnKeyListener((v, keyCode, event) -> {
            if (AppPrefs.enterSends(this) && keyCode == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN) {
                sendText();
                return true;
            }
            return false;
        });

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
        stopPlayback();
        if (recording) cancelRecording();
        super.onDestroy();
    }

    private void setInputEnabled(boolean on) {
        etMessage.setEnabled(on);
        btnMicSend.setEnabled(on);
        btnAttach.setEnabled(on);
        btnCamera.setEnabled(on);
    }

    // ---------------- sending ----------------

    private void sendText() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        String body = text;
        if (replyMid != null) {
            try {
                JSONObject p = new JSONObject();
                p.put("t", "text");
                p.put("x", text);
                JSONObject q = new JSONObject();
                q.put("mid", replyMid);
                q.put("who", replyWho == null ? "" : replyWho);
                q.put("snip", replySnip == null ? "" : replySnip);
                p.put("q", q);
                body = p.toString();
            } catch (Exception ignored) {
            }
        }
        sendPayload(body, () -> {
            etMessage.setText("");
            clearReply();
        });
    }

    private void setReply(LocalDb.Msg m) {
        replyMid = m.msgid;
        replyWho = m.sender.equals(myUid) ? "Tum" : peerName;
        replySnip = Meta.snippetOf(m.body);
        tvReply.setText("\u21A9\uFE0F " + replyWho + ": " + replySnip);
        replyBar.setVisibility(android.view.View.VISIBLE);
        etMessage.requestFocus();
    }

    private void clearReply() {
        replyMid = null;
        replyWho = null;
        replySnip = null;
        if (replyBar != null) replyBar.setVisibility(android.view.View.GONE);
    }

    private void sendMeta(String body) {
        if (ratchet == null) return;
        try {
            Ratchet.Encrypted e = ratchet.encrypt(body);
            JSONObject m = new JSONObject();
            m.put("from", myUid);
            m.put("idx", e.idx);
            m.put("body", e.bodyB64);
            m.put("ts", System.currentTimeMillis());
            Api.dbPost(this, "/rooms/" + roomId + "/messages", m, (pid, err) -> { });
        } catch (Exception ignored) {
        }
    }

    /** Encrypts the body (text or a media payload JSON) and posts it as one chat message. */
    private void sendPayload(final String body, final Runnable onOk) {
        if (ratchet == null) return;
        try {
            Ratchet.Encrypted e = ratchet.encrypt(body);
            JSONObject m = new JSONObject();
            m.put("from", myUid);
            m.put("idx", e.idx);
            m.put("body", e.bodyB64);
            long ts = System.currentTimeMillis();
            m.put("ts", ts);
            final long myIdx = e.idx;
            Api.dbPost(this, "/rooms/" + roomId + "/messages", m, (pushId, err) -> {
                if (err != null) {
                    Toast.makeText(this, "Message nahi gaya — internet check karo", Toast.LENGTH_SHORT).show();
                    return;
                }
                db.addMessage(roomId, pushId, myUid, body, ts, myIdx);
                if (onOk != null) onOk.run();
                renderAll();
                scrollToBottom();
            });
        } catch (Exception ex) {
            Toast.makeText(this, "Encryption masla — message nahi gaya", Toast.LENGTH_SHORT).show();
        }
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
        if (requestCode == REQ_CAMERA) {
            if (resultCode == RESULT_OK && pendingCamUri != null) {
                final Uri camUri = pendingCamUri;
                Toast.makeText(this, "Photo tayyar ho raha hai...", Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    try {
                        byte[] bytes = MediaHelper.prepPhoto(this, camUri);
                        runOnUiThread(() -> MediaHelper.uploadBlob(this, bytes, (blobId, keyB64, err) -> {
                            if (err != null) {
                                Toast.makeText(this, "Photo nahi gaya", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            sendPayload(MediaHelper.photoPayload(blobId, keyB64), null);
                        }));
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "Photo parhi nahi gayi", Toast.LENGTH_SHORT).show());
                    }
                }).start();
            }
            pendingCamUri = null;
            return;
        }
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
                        sendPayload(MediaHelper.photoPayload(blobId, keyB64), null);
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
                                "File bohat bari hai — sirf 1 MB tak ja sakti hai (video ke liye alag plan chahiye)",
                                Toast.LENGTH_LONG).show());
                        return;
                    }
                    runOnUiThread(() -> MediaHelper.uploadBlob(this, bytes, (blobId, keyB64, err) -> {
                        if (err != null) {
                            Toast.makeText(this, "File nahi gayi", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        sendPayload(MediaHelper.filePayload(blobId, keyB64, name, bytes.length), null);
                    }));
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "File parhi nahi gayi", Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    // ---------------- voice recording ----------------

    private void openCamera() {
        if (android.os.Build.VERSION.SDK_INT < 29
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM_CAM);
            return;
        }
        try {
            android.content.ContentValues vals = new android.content.ContentValues();
            vals.put(android.provider.MediaStore.Images.Media.TITLE, "guftagu_" + System.currentTimeMillis());
            pendingCamUri = getContentResolver().insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, vals);
            if (pendingCamUri == null) {
                Toast.makeText(this, "Camera nahi khula", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent cam = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCamUri);
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(cam, REQ_CAMERA);
        } catch (Exception e) {
            Toast.makeText(this, "Camera nahi khula", Toast.LENGTH_SHORT).show();
        }
    }

    private void onMicTap() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_PERM_CAM) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCamera();
            return;
        }
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
                    // keep a local copy so it plays instantly on this phone
                    try {
                        File dst = MediaHelper.cacheFile(this, blobId);
                        java.nio.file.Files.copy(recorded.toPath(), dst.toPath(),
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }
                    recorded.delete();
                    sendPayload(MediaHelper.voicePayload(blobId, keyB64, durSec), null);
                }));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Voice message parha nahi gaya", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ---------------- receiving ----------------

    private void poll() {
        if (ratchet == null) return;
        Api.dbGet(this, "/users/" + peerUid, (profile, err) -> {
            if (profile != null && !blocked) {
                String sub = null;
                JSONObject typing = profile.optJSONObject("typing");
                if (typing != null && roomId.equals(typing.optString("room", ""))
                        && System.currentTimeMillis() - typing.optLong("ts", 0) < 6000) {
                    sub = "✍️ likh raha hai...";
                }
                if (sub == null) sub = Presence.format(profile);
                tvSub.setText(sub != null ? sub : "🔒 end-to-end encrypted");
            }
        });
        Api.dbGetQ(this, "/rooms/" + roomId + "/messages", "orderBy=%22ts%22&limitToLast=100", (msgs, err) -> {
            if (msgs == null || err != null) return;
            List<String> ids = new ArrayList<>();
            Iterator<String> it = msgs.keys();
            while (it.hasNext()) ids.add(it.next());
            Collections.sort(ids, (a, b) -> Long.compare(
                    msgs.optJSONObject(a).optLong("ts", 0), msgs.optJSONObject(b).optLong("ts", 0)));
            boolean added = false;
            boolean ticksChanged = false;
            boolean gotPeerMsg = false;
            boolean metaChanged = false;
            for (String id : ids) {
                JSONObject m = msgs.optJSONObject(id);
                if (m == null) continue;
                String from = m.optString("from", "");
                if (m.has("seen")) {
                    if (from.equals(peerUid)) {
                        long s = m.optLong("seen", -1);
                        if (s > peerSeenIdx) {
                            peerSeenIdx = s;
                            ticksChanged = true;
                        }
                    }
                    continue;
                }
                if (db.hasMessage(roomId, id)) continue;
                long ts = m.optLong("ts", System.currentTimeMillis());
                if (from.equals(myUid)) {
                    db.addMessage(roomId, id, myUid, "📱 (ye message tumhare dusre phone se gaya tha)", ts, m.optLong("idx", -1));
                    added = true;
                } else if (from.equals(peerUid)) {
                    if (blocked) continue;
                    long midx = m.optLong("idx", -1);
                    String plain = ratchet.decrypt(midx, m.optString("body", ""), from);
                    if (plain != null && Meta.isMeta(plain)) {
                        Meta.apply(db, roomId, from, plain);
                        metaChanged = true;
                    } else {
                        db.addMessage(roomId, id, from,
                                plain != null ? plain : "🔒 Encrypted message — khul nahi saka", ts, midx);
                        added = true;
                        gotPeerMsg = true;
                    }
                    if (midx > maxPeerIdx) maxPeerIdx = midx;
                }
            }
            if (added) {
                renderAll();
                scrollToBottom();
                if (gotPeerMsg && !firstPoll) notifyCue();
            }
            if ((ticksChanged || metaChanged) && !added) renderAll();
            firstPoll = false;
            // publish my read receipt
            if (AppPrefs.readReceipts(this) && maxPeerIdx > lastSeenSent) {
                lastSeenSent = maxPeerIdx;
                try {
                    JSONObject seen = new JSONObject();
                    seen.put("from", myUid);
                    seen.put("seen", maxPeerIdx);
                    seen.put("ts", System.currentTimeMillis());
                    Api.dbPost(this, "/rooms/" + roomId + "/messages", seen, (pid, e2) -> { });
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void notifyCue() {
        if (AppPrefs.soundOn(this)) {
            try {
                ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60);
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
                tg.release();
            } catch (Exception ignored) {
            }
        }
        if (AppPrefs.vibrateOn(this)) {
            try {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) v.vibrate(60);
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------- safety + block ----------------

    private void showSafety() {
        if (peerPub == null || peerPub.isEmpty()) {
            Toast.makeText(this, "Saathi ki key abhi nahi hai", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String num = Crypto.safetyNumber(Crypto.myPubBytes(this), Crypto.unb64(peerPub));
            AlertDialog.Builder b = new AlertDialog.Builder(this)
                    .setTitle("🔑 Safety Number")
                    .setMessage(num + "\n\nYe number tumhare aur " + peerName + " ke phone par ek jaisa hona chahiye. " +
                            "Phone call par mila kar confirm karo — number ek jaisa hai to beech mein koi tumhari baat nahi sun sakta, server bhi nahi.")
                    .setPositiveButton("Theek hai", null);
            b.setNegativeButton(blocked ? "✅ Unblock karo" : "🚫 Block karo", (d, w) -> {
                if (blocked) AppPrefs.unblock(this, peerUid);
                else AppPrefs.block(this, peerUid, peerName);
                recreate();
            });
            b.show();
        } catch (Exception e) {
            Toast.makeText(this, "Safety number nahi ban saka", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------- rendering ----------------

    private void renderAll() {
        msgList.removeAllViews();
        for (LocalDb.Msg m : db.messages(roomId)) msgList.addView(bubble(m));
    }

    private String timeStr(long ts) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
    }

    private CharSequence footer(LocalDb.Msg m) {
        String t = timeStr(m.ts);
        if (!m.sender.equals(myUid) || !AppPrefs.readReceipts(this) || m.idx < 0) return t;
        if (m.idx <= peerSeenIdx) {
            SpannableString ss = new SpannableString(t + " ✓✓");
            ss.setSpan(new ForegroundColorSpan(0xFF53BDEB), t.length(), ss.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return ss;
        }
        return t + " ✓";
    }

    private LinearLayout bubble(final LocalDb.Msg m) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = dp(6);
        wrap.setLayoutParams(wlp);

        if ("sys".equals(m.sender)) {
            TextView tv = new TextView(this);
            tv.setText(m.body);
            tv.setTextColor(0xFF5A4E1F);
            tv.setTextSize(12);
            tv.setGravity(Gravity.CENTER);
            tv.setBackgroundResource(R.drawable.bg_sys);
            tv.setPadding(dp(14), dp(6), dp(14), dp(6));
            wrap.addView(tv);
            return wrap;
        }

        boolean mine = m.sender.equals(myUid);
        JSONObject payload = MediaHelper.parsePayload(m.body);
        View content;

        if (Meta.isDeleted(m.body)) {
            TextView tv = new TextView(this);
            tv.setText("🚫 Ye message delete ho gaya");
            tv.setTextColor(0xFF667781);
            tv.setTextSize(AppPrefs.bubbleTextSize(this));
            tv.setTypeface(null, android.graphics.Typeface.ITALIC);
            content = tv;
        } else if (payload == null || "text".equals(payload.optString("t"))) {
            content = richTextView(m, payload);
        } else if ("voice".equals(payload.optString("t"))) {
            content = voiceView(m, payload);
        } else if ("photo".equals(payload.optString("t"))) {
            content = photoView(m, payload);
        } else {
            content = fileView(m, payload);
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(mine ? R.drawable.bubble_out : R.drawable.bubble_in);
        card.setPadding(dp(12), dp(8), dp(12), dp(6));
        card.addView(content);
        TextView foot = new TextView(this);
        boolean edited = payload != null && payload.optBoolean("ed");
        foot.setText(edited ? "✎ edited • " + footer(m) : footer(m));
        foot.setTextColor(0xFF667781);
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
        View chips = reactionChips(m);
        if (chips != null) wrap.addView(chips);
        if (!Meta.isDeleted(m.body)) {
            card.setOnLongClickListener(v -> {
                showMsgMenu(m);
                return true;
            });
        }
        return wrap;
    }

    private View richTextView(final LocalDb.Msg m, final JSONObject payload) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        String text = m.body;
        if (payload != null) {
            text = payload.optString("x", "");
            if (payload.optBoolean("fwd")) {
                TextView fwd = new TextView(this);
                fwd.setText("↪ Forwarded");
                fwd.setTextColor(0xFF667781);
                fwd.setTextSize(11);
                fwd.setTypeface(null, android.graphics.Typeface.ITALIC);
                col.addView(fwd);
            }
            JSONObject q = payload.optJSONObject("q");
            if (q != null) {
                LinearLayout quote = new LinearLayout(this);
                quote.setOrientation(LinearLayout.HORIZONTAL);
                quote.setBackgroundResource(R.drawable.bg_chip);
                quote.setPadding(dp(8), dp(5), dp(8), dp(5));
                View bar = new View(this);
                bar.setBackgroundColor(0xFF00A884);
                bar.setLayoutParams(new LinearLayout.LayoutParams(dp(3), LinearLayout.LayoutParams.MATCH_PARENT));
                quote.addView(bar);
                LinearLayout qtexts = new LinearLayout(this);
                qtexts.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                qlp.leftMargin = dp(8);
                qtexts.setLayoutParams(qlp);
                TextView who = new TextView(this);
                who.setText(q.optString("who", ""));
                who.setTextColor(0xFF00A884);
                who.setTextSize(12);
                who.setTypeface(null, android.graphics.Typeface.BOLD);
                qtexts.addView(who);
                TextView snip = new TextView(this);
                snip.setText(q.optString("snip", ""));
                snip.setTextColor(0xFF667781);
                snip.setTextSize(12);
                snip.setMaxLines(2);
                qtexts.addView(snip);
                quote.addView(qtexts);
                LinearLayout.LayoutParams quoteLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                quoteLp.bottomMargin = dp(5);
                quote.setLayoutParams(quoteLp);
                col.addView(quote);
            }
        }
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF111111);
        tv.setTextSize(AppPrefs.bubbleTextSize(this));
        col.addView(tv);
        return col;
    }

    private String myReaction(LocalDb.Msg m) {
        if (m.react == null || m.react.isEmpty()) return "";
        try {
            return new JSONObject(m.react).optString(myUid, "");
        } catch (Exception e) {
            return "";
        }
    }

    private View reactionChips(final LocalDb.Msg m) {
        if (m.react == null || m.react.isEmpty()) return null;
        try {
            JSONObject r = new JSONObject(m.react);
            java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
            Iterator<String> it = r.keys();
            while (it.hasNext()) {
                String e = r.optString(it.next(), "");
                if (!e.isEmpty()) counts.put(e, counts.containsKey(e) ? counts.get(e) + 1 : 1);
            }
            if (counts.isEmpty()) return null;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.gravity = m.sender.equals(myUid) ? Gravity.END : Gravity.START;
            rlp.topMargin = dp(2);
            row.setLayoutParams(rlp);
            for (final java.util.Map.Entry<String, Integer> en : counts.entrySet()) {
                TextView chip = new TextView(this);
                chip.setText(en.getKey() + " " + en.getValue());
                chip.setTextSize(12);
                chip.setBackgroundResource(R.drawable.bg_chip);
                chip.setPadding(dp(8), dp(2), dp(8), dp(2));
                LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                clp.leftMargin = dp(4);
                chip.setLayoutParams(clp);
                chip.setOnClickListener(v -> {
                    String send = en.getKey().equals(myReaction(m)) ? "" : en.getKey();
                    sendMeta(Meta.react(m.msgid, send));
                    db.setReact(roomId, m.msgid, myUid, send);
                    renderAll();
                });
                row.addView(chip);
            }
            return row;
        } catch (Exception e) {
            return null;
        }
    }

    private void showMsgMenu(final LocalDb.Msg m) {
        final boolean mine = m.sender.equals(myUid);
        final JSONObject payload = MediaHelper.parsePayload(m.body);
        final boolean textish = payload == null || "text".equals(payload.optString("t"));
        final java.util.List<String> items = new java.util.ArrayList<>();
        items.add("😍 React karo");
        items.add("↩️ Reply");
        items.add("➡️ Forward");
        if (textish) items.add("📋 Copy");
        if (mine && textish) items.add("✏️ Edit");
        if (mine) items.add("🗑 Delete — sab ke liye");
        new AlertDialog.Builder(this)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String sel = items.get(which);
                    if (sel.startsWith("😍")) reactPicker(m);
                    else if (sel.startsWith("↩")) setReply(m);
                    else if (sel.startsWith("➡")) forwardDialog(m);
                    else if (sel.startsWith("📋")) {
                        android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(android.content.ClipData.newPlainText(
                                "message", Meta.displayText(m.body)));
                        Toast.makeText(this, "Copy ho gaya", Toast.LENGTH_SHORT).show();
                    } else if (sel.startsWith("✏")) editDialog(m);
                    else if (sel.startsWith("🗑")) deleteConfirm(m);
                })
                .show();
    }

    private void reactPicker(final LocalDb.Msg m) {
        final String[] emojis = {"❤️", "👍", "😂", "😮", "😢", "🙏"};
        new AlertDialog.Builder(this)
                .setTitle("Reaction chuno")
                .setItems(emojis, (d, which) -> {
                    String send = emojis[which].equals(myReaction(m)) ? "" : emojis[which];
                    sendMeta(Meta.react(m.msgid, send));
                    db.setReact(roomId, m.msgid, myUid, send);
                    renderAll();
                })
                .show();
    }

    private void editDialog(final LocalDb.Msg m) {
        final EditText et = new EditText(this);
        et.setText(Meta.displayText(m.body));
        et.setSelection(et.getText().length());
        new AlertDialog.Builder(this)
                .setTitle("Message edit karo")
                .setView(et)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, w) -> {
                    String nt = et.getText().toString().trim();
                    if (nt.isEmpty()) return;
                    sendMeta(Meta.edit(m.msgid, nt));
                    db.applyEdit(roomId, m.msgid, nt);
                    renderAll();
                })
                .show();
    }

    private void deleteConfirm(final LocalDb.Msg m) {
        new AlertDialog.Builder(this)
                .setTitle("Message delete karun?")
                .setMessage("Ye message sab ke liye delete ho jayega.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) -> {
                    sendMeta(Meta.del(m.msgid));
                    db.applyDelete(roomId, m.msgid);
                    renderAll();
                })
                .show();
    }

    private void forwardDialog(final LocalDb.Msg m) {
        Api.dbGet(this, "/users", (users, err) -> {
            if (users == null) {
                Toast.makeText(this, "Users load nahi hue", Toast.LENGTH_SHORT).show();
                return;
            }
            final java.util.List<String> labels = new java.util.ArrayList<>();
            final java.util.List<String[]> targets = new java.util.ArrayList<>();
            labels.add("👥 Sab ka Group");
            targets.add(new String[]{"group", "", ""});
            Iterator<String> it = users.keys();
            while (it.hasNext()) {
                String uid = it.next();
                if (uid.equals(myUid) || uid.equals(peerUid)) continue;
                JSONObject u = users.optJSONObject(uid);
                if (u == null) continue;
                labels.add(u.optString("name", "User"));
                targets.add(new String[]{uid, u.optString("name", ""), u.optString("pub", "")});
            }
            new AlertDialog.Builder(this)
                    .setTitle("Kisko forward karun?")
                    .setItems(labels.toArray(new String[0]), (d, which) -> {
                        String[] t = targets.get(which);
                        final String fwdBody = Meta.forwardBody(m.body);
                        if ("group".equals(t[0])) {
                            Intent i = new Intent(this, GroupActivity.class);
                            i.putExtra("forwardBody", fwdBody);
                            startActivity(i);
                        } else {
                            if (t[2].isEmpty()) {
                                Toast.makeText(this, "Is user ne abhi app nahi khola", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            Forwarder.forwardToUser(this, t[0], t[2], fwdBody, ok ->
                                    Toast.makeText(this, ok ? "Forward ho gaya ✅" : "Forward nahi hua",
                                            Toast.LENGTH_SHORT).show());
                        }
                    })
                    .show();
        });
    }

    private View voiceView(final LocalDb.Msg m, final JSONObject payload) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView play = new TextView(this);
        boolean isPlaying = m.msgid.equals(playingId);
        play.setText(isPlaying ? "⏸" : "▶");
        play.setTextColor(0xFF00A884);
        play.setTextSize(20);
        play.setPadding(dp(2), dp(2), dp(10), dp(2));
        row.addView(play);
        TextView label = new TextView(this);
        int dur = payload.optInt("dur", 0);
        label.setText("🎤 Voice message • " + MediaHelper.fmtDur(dur));
        label.setTextColor(0xFF111111);
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
        // auto-download small voice notes as they arrive
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
        name.setTextColor(0xFF111111);
        name.setTextSize(AppPrefs.bubbleTextSize(this));
        col.addView(name);
        final TextView sub = new TextView(this);
        long size = payload.optLong("size", 0);
        final String blobId = payload.optString("b", "");
        final String key = payload.optString("k", "");
        File saved = savedFileTarget(payload.optString("name", "file"));
        sub.setText(MediaHelper.fmtSize(size) + (saved.exists() ? " • Save hai — tap karke dekho" : " • Download ke liye tap karo"));
        sub.setTextColor(0xFF667781);
        sub.setTextSize(12);
        col.addView(sub);
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
                renderAll();
            } catch (Exception e) {
                Toast.makeText(this, "Save nahi hui", Toast.LENGTH_SHORT).show();
            }
        }));
        return col;
    }

    private File savedFileTarget(String name) {
        File dir = new File(getExternalFilesDir(null), "GuftaguDownloads");
        return new File(dir, name);
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
