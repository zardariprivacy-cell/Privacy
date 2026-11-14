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

public class GroupActivity extends BaseActivity {

    private String ROOM = "group";
    private String gid;           // null = legacy "Sab ka Group"
    private String msgPath = "/group/messages";
    private String membersPath = "/group/members";
    private String groupTitle = "Sab ka Group";
    private String ownerUid;
    private static final int REQ_PHOTO = 51;
    private static final int REQ_FILE = 52;
    private static final int REQ_MIC = 53;

    private String myUid, myName;
    private LocalDb db;
    private String replyMid, replyWho, replySnip;
    private android.view.View replyBar;
    private TextView tvReply;
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

        gid = getIntent().getStringExtra("gid");
        if (gid != null) {
            ROOM = "grp_" + gid;
            msgPath = "/groups/" + gid + "/messages";
            membersPath = "/groups/" + gid + "/members";
            String gn = getIntent().getStringExtra("gname");
            if (gn != null && !gn.isEmpty()) groupTitle = gn;
            ownerUid = getIntent().getStringExtra("gowner");
        }

        ((TextView) findViewById(R.id.tvChatTitle)).setText(gid == null ? "Sab ka Group 👥" : groupTitle);
        findViewById(R.id.btnCall).setVisibility(android.view.View.GONE);
        findViewById(R.id.btnVideo).setVisibility(android.view.View.GONE);
        tvSub = findViewById(R.id.tvChatSub);
        tvSub.setText("🔒 Group encryption — naye members puranay messages nahi parh sakte");
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
            }
        });
        inputBar = findViewById(R.id.inputBar);
        recordBar = findViewById(R.id.recordBar);
        tvRecTime = findViewById(R.id.tvRecTime);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSafety).setOnClickListener(v -> {
            if (gid == null) showGroupInfo(true);
            else groupOptions();
        });

        db = new LocalDb(this);
        Presence.beat(this);

        if (gid == null) {
            try {
                JSONObject me = new JSONObject();
                me.put("name", myName == null ? "" : myName);
                Api.dbPut(this, membersPath + "/" + myUid, me, (ok, err) -> { });
            } catch (Exception ignored) {
            }
        } else {
            Api.dbGet(this, "/groups/" + gid, (g, err) -> {
                if (g == null) {
                    Toast.makeText(this, "Group load nahi hua", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                ownerUid = g.optString("owner", "");
                String gn = g.optString("name", "");
                if (!gn.isEmpty()) {
                    groupTitle = gn;
                    ((TextView) findViewById(R.id.tvChatTitle)).setText(gn);
                }
                JSONObject members = g.optJSONObject("members");
                if (members == null || !members.has(myUid)) {
                    Toast.makeText(this, "Tum is group ke member nahi ho", Toast.LENGTH_LONG).show();
                    finish();
                }
            });
        }

        btnMicSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) sendGroupText(text);
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

        String fwdBody = getIntent().getStringExtra("forwardBody");
        if (fwdBody != null && !fwdBody.isEmpty()) {
            sendGroupPayload(fwdBody, () -> Toast.makeText(this, "Forward ho gaya ✅", Toast.LENGTH_SHORT).show());
        }
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

    private void showGroupInfo(boolean withExp) {
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("🔒 Group Encryption")
                .setMessage("Har message ek nayi random key se lock hota hai, aur wo key har member ke liye alag se uski public key se band kar bheji jati hai. Server par sirf band (encrypted) text jata hai — server, admin, koi bhi message nahi parh sakta. Jo member baad mein join kare, wo puranay messages nahi parh sakta.")
                .setPositiveButton("Theek hai", null);
        if (withExp) b.setNeutralButton("⏳ Gayab Msgs", (d, w) -> expDialog());
        b.show();
    }

    private void groupOptions() {
        new AlertDialog.Builder(this)
                .setTitle(groupTitle)
                .setItems(new String[]{"🔒 Encryption info", "👥 Members", "⏳ Gayab Msgs (Disappearing)"}, (d, w) -> {
                    if (w == 0) showGroupInfo(false);
                    else if (w == 1) membersDialog();
                    else expDialog();
                })
                .show();
    }

    private void membersDialog() {
        Api.dbGet(this, membersPath, (members, err) -> {
            if (members == null) {
                Toast.makeText(this, "Members load nahi hue", Toast.LENGTH_SHORT).show();
                return;
            }
            final boolean iAmOwner = myUid.equals(ownerUid);
            final java.util.List<String> labels = new java.util.ArrayList<>();
            final java.util.List<String> uids = new java.util.ArrayList<>();
            Iterator<String> it = members.keys();
            while (it.hasNext()) {
                String uid = it.next();
                JSONObject mo = members.optJSONObject(uid);
                String n = mo != null ? mo.optString("name", "") : "";
                String tag = uid.equals(ownerUid) ? " 👑 Owner" : uid.equals(myUid) ? " (Tum)" : "";
                labels.add((n.isEmpty() ? "User" : n) + tag);
                uids.add(uid);
            }
            labels.add(iAmOwner ? "➕ Member add karo" : "🚪 Group chhoro (Leave)");
            new AlertDialog.Builder(this)
                    .setTitle("👥 Members (" + uids.size() + ")")
                    .setItems(labels.toArray(new String[0]), (d, which) -> {
                        if (which == labels.size() - 1) {
                            if (iAmOwner) addMemberDialog(members);
                            else leaveGroup();
                        } else if (iAmOwner) {
                            final String target = uids.get(which);
                            if (!target.equals(myUid) && !target.equals(ownerUid)) {
                                removeMember(target, labels.get(which));
                            }
                        }
                    })
                    .show();
        });
    }

    private void addMemberDialog(final JSONObject currentMembers) {
        Api.dbGet(this, "/users", (users, err) -> {
            if (users == null) {
                Toast.makeText(this, "Users load nahi hue", Toast.LENGTH_SHORT).show();
                return;
            }
            final java.util.List<String> labels = new java.util.ArrayList<>();
            final java.util.List<String> uids = new java.util.ArrayList<>();
            Iterator<String> it = users.keys();
            while (it.hasNext()) {
                String uid = it.next();
                if (uid.equals(myUid) || currentMembers.has(uid)) continue;
                JSONObject u = users.optJSONObject(uid);
                if (u == null) continue;
                labels.add(u.optString("name", "User"));
                uids.add(uid);
            }
            if (labels.isEmpty()) {
                Toast.makeText(this, "Add karne ke liye koi nahi bacha", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setTitle("Kisko add karun?")
                    .setItems(labels.toArray(new String[0]), (d, which) -> {
                        final String uid = uids.get(which);
                        try {
                            JSONObject mo = new JSONObject();
                            mo.put("name", labels.get(which));
                            Api.dbPut(this, membersPath + "/" + uid, mo, (ok, e2) -> { });
                            JSONObject idx = new JSONObject();
                            idx.put("name", groupTitle);
                            Api.dbPut(this, "/users/" + uid + "/mygroups/" + gid, idx, (ok, e2) -> { });
                            Toast.makeText(this, labels.get(which) + " add ho gaya ✅", Toast.LENGTH_SHORT).show();
                            pollCycle();
                        } catch (Exception ignored) {
                        }
                    })
                    .show();
        });
    }

    private void removeMember(final String uid, final String label) {
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(new String[]{"🚫 Group se nikalo"}, (d, which) -> {
                    Api.dbDelete(this, membersPath + "/" + uid, (ok, e) -> { });
                    Api.dbDelete(this, "/users/" + uid + "/mygroups/" + gid, (ok, e) -> { });
                    Toast.makeText(this, "Member nikal gaya", Toast.LENGTH_SHORT).show();
                    pollCycle();
                })
                .show();
    }

    private void leaveGroup() {
        new AlertDialog.Builder(this)
                .setTitle("Group chhoro?")
                .setMessage("Tum is group se nikal jaoge. Purani chat is phone par reh sakti hai, naye messages nahi ayenge.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Chhoro", (d, w) -> {
                    Api.dbDelete(this, membersPath + "/" + myUid, (ok, e) -> { });
                    Api.dbDelete(this, "/users/" + myUid + "/mygroups/" + gid, (ok, e) -> { });
                    finish();
                })
                .show();
    }

    private void expDialog() {
        final String[] opts = {"Off", "24 ghante", "7 din"};
        final int[] hours = {0, 24, 168};
        int cur = AppPrefs.disappearHours(this, ROOM);
        int sel = cur == 24 ? 1 : cur == 168 ? 2 : 0;
        new AlertDialog.Builder(this)
                .setTitle("⏳ Disappearing messages (poora group)")
                .setSingleChoiceItems(opts, sel, (d, w) -> {
                    int h = hours[w];
                    AppPrefs.setDisappearHours(this, ROOM, h);
                    sendGroupMeta(Meta.exp(h));
                    db.addMessage(ROOM, "sys" + System.currentTimeMillis(), myUid,
                            h == 0 ? "⏳ Disappearing messages OFF ho gaye"
                                    : "⏳ Disappearing messages ON: " + opts[w] + " se puranay messages gayab ho jayenge",
                            System.currentTimeMillis());
                    d.dismiss();
                    pruneExpired();
                    renderAll();
                })
                .show();
    }

    private void pruneExpired() {
        int h = AppPrefs.disappearHours(this, ROOM);
        if (h <= 0) return;
        long cutoff = System.currentTimeMillis() - h * 3600000L;
        for (LocalDb.Msg m : db.expired(ROOM, cutoff)) {
            if (m.sender.equals(myUid) && !m.msgid.startsWith("sys")) {
                Api.dbDelete(this, msgPath + "/" + m.msgid, (ok, e) -> { });
            }
            db.deleteMessage(ROOM, m.msgid);
        }
    }

    private void sendGroupText(String text) {
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
        sendGroupPayload(body, () -> {
            etMessage.setText("");
            clearReply();
        });
    }

    private void setReply(LocalDb.Msg m) {
        replyMid = m.msgid;
        if (m.sender.equals(myUid)) {
            replyWho = "Tum";
        } else {
            JSONObject prof = directory.get(m.sender);
            String n = prof != null ? prof.optString("name", "") : "";
            replyWho = n.isEmpty() ? "User" : n;
        }
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

    private void sendGroupPayload(final String plainBody, final Runnable onOk) {
        postGroup(plainBody, onOk, true);
    }

    private void sendGroupMeta(final String plainBody) {
        postGroup(plainBody, null, false);
    }

    private void postGroup(final String plainBody, final Runnable onOk, final boolean storeLocal) {
        Api.dbGet(this, membersPath, (members, err) -> {
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
                    Api.dbPost(this, msgPath, m, (pushId, e3) -> {
                        if (e3 != null) {
                            if (storeLocal) Toast.makeText(this, "Message nahi gaya — internet check karo", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (storeLocal) {
                            db.addMessage(ROOM, pushId, myUid, plainBody, ts);
                            renderAll();
                            scrollToBottom();
                        }
                        if (onOk != null) onOk.run();
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
                            sendGroupPayload(MediaHelper.photoPayload(blobId, keyB64), null);
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
            Api.dbGet(this, membersPath, (members, e2) -> {
                if (members != null) {
                    tvSub.setText(members.length() + " members • 🔒 group encryption");
                }
                fetchMessages();
            });
        });
    }

    private void fetchMessages() {
        Api.dbGetQ(this, msgPath, "orderBy=%22ts%22&limitToLast=100", (msgs, err) -> {
            if (msgs == null || err != null) return;
            List<String> ids = new ArrayList<>();
            Iterator<String> it = msgs.keys();
            while (it.hasNext()) ids.add(it.next());
            Collections.sort(ids, (a, b) -> Long.compare(
                    msgs.optJSONObject(a).optLong("ts", 0), msgs.optJSONObject(b).optLong("ts", 0)));
            boolean added = false;
            boolean metaChanged = false;
            for (String id : ids) {
                if (db.hasMessage(ROOM, id)) continue;
                JSONObject m = msgs.optJSONObject(id);
                if (m == null) continue;
                String from = m.optString("from", "");
                if (AppPrefs.isBlocked(this, from)) continue;
                long ts = m.optLong("ts", System.currentTimeMillis());
                String plain = decryptGroupMessage(m);
                if (plain != null && Meta.isExp(plain)) {
                    AppPrefs.setDisappearHours(this, ROOM, Meta.expHours(plain));
                    metaChanged = true;
                } else if (plain != null && Meta.isMeta(plain)) {
                    Meta.apply(db, ROOM, from, plain);
                    metaChanged = true;
                } else {
                    db.addMessage(ROOM, id, from,
                            plain != null ? plain : "🔒 Ye message tumhare group join karne se pehle ka hai — nahi khul sakta",
                            ts);
                    added = true;
                }
            }
            pruneExpired();
            if (added || metaChanged || msgList.getChildCount() == 0) {
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
                nameTv.setTextColor(AppPrefs.nameInk(this));
                nameTv.setTextSize(12);
                card.addView(nameTv);
            }
        }

        JSONObject payload = MediaHelper.parsePayload(m.body);
        View content;
        if (Meta.isDeleted(m.body)) {
            TextView tv = new TextView(this);
            tv.setText("🚫 Ye message delete ho gaya");
            tv.setTextColor(AppPrefs.sub(this));
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
        card.addView(content);

        TextView foot = new TextView(this);
        boolean edited = payload != null && payload.optBoolean("ed");
        foot.setText((edited ? "✎ edited • " : "") + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(m.ts)));
        foot.setTextColor(AppPrefs.sub(this));
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
                fwd.setTextColor(AppPrefs.sub(this));
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
                snip.setTextColor(AppPrefs.sub(this));
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
        tv.setTextColor(AppPrefs.ink(this));
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
                    sendGroupMeta(Meta.react(m.msgid, send));
                    db.setReact(ROOM, m.msgid, myUid, send);
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
                    sendGroupMeta(Meta.react(m.msgid, send));
                    db.setReact(ROOM, m.msgid, myUid, send);
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
                    sendGroupMeta(Meta.edit(m.msgid, nt));
                    db.applyEdit(ROOM, m.msgid, nt);
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
                    sendGroupMeta(Meta.del(m.msgid));
                    db.applyDelete(ROOM, m.msgid);
                    renderAll();
                })
                .show();
    }

    private void forwardDialog(final LocalDb.Msg m) {
        final java.util.List<String> labels = new java.util.ArrayList<>();
        final java.util.List<String[]> targets = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, JSONObject> en : directory.entrySet()) {
            String uid = en.getKey();
            if (uid.equals(myUid)) continue;
            labels.add(en.getValue().optString("name", "User"));
            targets.add(new String[]{uid, en.getValue().optString("pub", "")});
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "Forward ke liye koi user nahi", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Kisko forward karun?")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String[] t = targets.get(which);
                    if (t[1].isEmpty()) {
                        Toast.makeText(this, "Is user ne abhi app nahi khola", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Forwarder.forwardToUser(this, t[0], t[1], Meta.forwardBody(m.body), ok ->
                            Toast.makeText(this, ok ? "Forward ho gaya ✅" : "Forward nahi hua",
                                    Toast.LENGTH_SHORT).show());
                })
                .show();
    }

    private View voiceView(final LocalDb.Msg m, final JSONObject payload) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView play = new TextView(this);
        play.setText(m.msgid.equals(playingId) ? "⏸" : "▶");
        play.setTextColor(0xFF00A884);
        play.setTextSize(20);
        play.setPadding(dp(2), dp(2), dp(10), dp(2));
        row.addView(play);
        TextView label = new TextView(this);
        label.setText("🎤 Voice message • " + MediaHelper.fmtDur(payload.optInt("dur", 0)));
        label.setTextColor(AppPrefs.ink(this));
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
        name.setTextColor(AppPrefs.ink(this));
        name.setTextSize(AppPrefs.bubbleTextSize(this));
        col.addView(name);
        TextView sub = new TextView(this);
        sub.setText(MediaHelper.fmtSize(payload.optLong("size", 0)) + " • Download ke liye tap karo");
        sub.setTextColor(AppPrefs.sub(this));
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
