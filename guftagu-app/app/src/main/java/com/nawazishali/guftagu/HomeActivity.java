package com.nawazishali.guftagu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends Activity {

    private static final int REQ_STATUS_PHOTO = 61;

    private LinearLayout chatList, statusList;
    private TextView tvStatus, tvBroadcast;
    private EditText etSearch;
    private String myUid, myName;
    private LocalDb db;
    private final Handler handler = new Handler();
    private boolean alive = true;

    private int filterMode = 0;
    private JSONObject cachedUsers;
    private JSONObject cachedStatus;
    private boolean banned = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        myUid = Session.uid(this);
        if (myUid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        myName = Session.name(this);
        db = new LocalDb(this);
        chatList = findViewById(R.id.chatList);
        statusList = findViewById(R.id.statusList);
        tvStatus = findViewById(R.id.tvHomeStatus);
        tvBroadcast = findViewById(R.id.tvBroadcast);
        etSearch = findViewById(R.id.etSearch);

        Button btnAdmin = findViewById(R.id.btnAdmin);
        btnAdmin.setVisibility(OwnerMode.isUnlocked(this) ? View.VISIBLE : View.GONE);
        btnAdmin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { renderChats(); }
            @Override public void afterTextChanged(Editable s) { }
        });

        Button chipAll = findViewById(R.id.chipAll);
        Button chipOnline = findViewById(R.id.chipOnline);
        Button chipGroups = findViewById(R.id.chipGroups);
        chipAll.setOnClickListener(v -> setFilter(0));
        chipOnline.setOnClickListener(v -> setFilter(1));
        chipGroups.setOnClickListener(v -> setFilter(2));
        findViewById(R.id.navStatus).setOnClickListener(v -> {
            if (!banned) postStatusMenu();
        });
        findViewById(R.id.navGroup).setOnClickListener(v ->
                startActivity(new Intent(this, GroupActivity.class)));
        findViewById(R.id.navSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.fabNew).setOnClickListener(v -> newChatDialog());

        Presence.beat(this);
        loadAll();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!alive) return;
                Presence.beat(HomeActivity.this);
                loadAll();
                handler.postDelayed(this, 20000);
            }
        }, 20000);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Button btnAdmin = findViewById(R.id.btnAdmin);
        if (btnAdmin != null) btnAdmin.setVisibility(OwnerMode.isUnlocked(this) ? View.VISIBLE : View.GONE);
        if (cachedUsers != null) {
            renderChats();
            renderStatuses();
        }
    }

    private void loadAll() {
        Api.dbGet(this, "/admin/broadcast", (b, err) -> {
            if (b != null && b.optString("text", "").length() > 0) {
                tvBroadcast.setText("📢 " + b.optString("text"));
                tvBroadcast.setVisibility(View.VISIBLE);
            } else {
                tvBroadcast.setVisibility(View.GONE);
            }
        });
        Api.dbGet(this, "/bans", (bans, err) -> {
            banned = bans != null && bans.has(myUid);
            if (banned) {
                tvStatus.setText("⛔ Tumhara account owner ne ban kiya hai — tum chat nahi kar sakte.");
                chatList.removeAllViews();
                statusList.removeAllViews();
                return;
            }
            Api.dbGet(this, "/users", (users, e2) -> {
                cachedUsers = users;
                renderChats();
                Api.dbGet(this, "/status", (st, e3) -> {
                    cachedStatus = st;
                    renderStatuses();
                });
            });
        });
    }

    private void setFilter(int mode) {
        filterMode = mode;
        ((Button) findViewById(R.id.chipAll)).setBackgroundResource(
                mode == 0 ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
        ((Button) findViewById(R.id.chipOnline)).setBackgroundResource(
                mode == 1 ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
        ((Button) findViewById(R.id.chipGroups)).setBackgroundResource(
                mode == 2 ? R.drawable.bg_chip_sel : R.drawable.bg_chip);
        renderChats();
    }

    private void newChatDialog() {
        if (cachedUsers == null) {
            Toast.makeText(this, "Users load ho rahe hain...", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> names = new ArrayList<>();
        List<String[]> refs = new ArrayList<>();
        Iterator<String> it = cachedUsers.keys();
        while (it.hasNext()) {
            String uid = it.next();
            if (uid.equals(myUid)) continue;
            JSONObject u = cachedUsers.optJSONObject(uid);
            if (u == null) continue;
            names.add(u.optString("name", "User"));
            refs.add(new String[]{uid, u.optString("name", ""), u.optString("pub", "")});
        }
        if (names.isEmpty()) {
            Toast.makeText(this, "Abhi koi aur user nahi hai", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Nayi chat — kisse?")
                .setItems(names.toArray(new String[0]), (d, w) -> {
                    String[] r = refs.get(w);
                    Intent i = new Intent(this, ChatActivity.class);
                    i.putExtra("peerUid", r[0]);
                    i.putExtra("peerName", r[1]);
                    i.putExtra("peerPub", r[2]);
                    startActivity(i);
                })
                .show();
    }

    // ---------------- chats ----------------

    private void renderChats() {
        if (cachedUsers == null || banned) return;
        String q = etSearch.getText().toString().trim().toLowerCase();
        chatList.removeAllViews();
        if (filterMode != 1) chatList.addView(groupRow());
        List<String[]> rows = new ArrayList<>();
        Iterator<String> it = cachedUsers.keys();
        while (it.hasNext()) {
            String uid = it.next();
            if (uid.equals(myUid)) continue;
            JSONObject u = cachedUsers.optJSONObject(uid);
            if (u == null) continue;
            String name = u.optString("name", "");
            if (!q.isEmpty() && !name.toLowerCase().contains(q)) continue;
            if (filterMode == 2) continue;
            if (filterMode == 1 && !Presence.isOnline(u)) continue;
            rows.add(new String[]{uid, name, u.optString("pub", "")});
        }
        Collections.sort(rows, (a, b) -> a[1].compareToIgnoreCase(b[1]));

        // respect "hide my profile": hidden users appear only if we already chat with them
        List<String[]> visible = new ArrayList<>();
        for (String[] r : rows) {
            JSONObject u = cachedUsers.optJSONObject(r[0]);
            boolean hidden = u != null && u.optBoolean("hidden", false);
            String room = roomId(r[0]);
            if (hidden && db.lastMessage(room) == null) continue;
            visible.add(r);
        }

        if (visible.isEmpty()) {
            tvStatus.setText(q.isEmpty()
                    ? "Abhi aur koi user nahi hai — doston ko Guftagu par bulao!"
                    : "Is naam ka koi user nahi mila");
        } else {
            tvStatus.setText("");
        }
        for (String[] r : visible) chatList.addView(userRow(r[0], r[1], r[2]));
    }

    private String roomId(String peerUid) {
        return myUid.compareTo(peerUid) < 0 ? myUid + "_" + peerUid : peerUid + "_" + myUid;
    }

    private String timeLabel(long ts) {
        String day = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(ts));
        String today = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
        String yest = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date(System.currentTimeMillis() - 86400000L));
        if (day.equals(today)) return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(ts));
        if (day.equals(yest)) return "Kal";
        return new SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(new Date(ts));
    }

    private LinearLayout rowBase(String title, String sub, String time, boolean online) {
        return rowBase(title, sub, time, online, title, false, null);
    }

    private LinearLayout rowBase(String title, String sub, String time, boolean online, String avatarSeed, boolean ring) {
        return rowBase(title, sub, time, online, avatarSeed, ring, null);
    }

    private LinearLayout rowBase(String title, String sub, String time, boolean online, String avatarSeed, boolean ring, JSONObject prof) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);

        FrameLayout avatar = new FrameLayout(this);
        avatar.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        if (ring) {
            avatar.setBackgroundResource(R.drawable.bg_circle_ring);
            avatar.setPadding(dp(3), dp(3), dp(3), dp(3));
        }
        TextView init = new TextView(this);
        init.setGravity(Gravity.CENTER);
        init.setTextSize(16);
        init.setTextColor(0xFFFFFFFF);
        init.setTypeface(null, Typeface.BOLD);
        init.setBackgroundResource(AppPrefs.avatarBgFor(avatarSeed));
        String t = title.trim();
        init.setText(t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase());
        avatar.addView(init, new FrameLayout.LayoutParams(dp(46), dp(46)));
        if (prof != null && prof.has("pic")) bindAvatarPhoto(avatar, init, prof.optJSONObject("pic"));
        if (online) {
            View dot = new View(this);
            dot.setBackgroundResource(R.drawable.bg_circle);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(12), dp(12));
            dlp.gravity = Gravity.BOTTOM | Gravity.END;
            avatar.addView(dot, dlp);
        }
        row.addView(avatar);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(12);
        texts.setLayoutParams(tlp);
        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(0xFF111111);
        tvT.setTextSize(16);
        tvT.setTypeface(null, Typeface.BOLD);
        texts.addView(tvT);
        TextView tvS = new TextView(this);
        tvS.setText(sub);
        tvS.setTextColor(0xFF667781);
        tvS.setTextSize(13);
        tvS.setSingleLine(true);
        texts.addView(tvS);
        row.addView(texts);

        if (time != null) {
            TextView tvTime = new TextView(this);
            tvTime.setText(time);
            tvTime.setTextColor(0xFF667781);
            tvTime.setTextSize(12);
            row.addView(tvTime);
        }
        return row;
    }

    private View groupRow() {
        LocalDb.Msg last = db.lastMessage("group");
        String sub = "👥 Sab members • 🔒 group encryption";
        String time = null;
        if (last != null) {
            sub = (last.sender.equals(myUid) ? "Tum: " : "") + MediaHelper.previewText(last.body);
            time = timeLabel(last.ts);
        }
        LinearLayout row = rowBase("Sab ka Group", sub, time, false, "group", false);
        row.setOnClickListener(v -> startActivity(new Intent(this, GroupActivity.class)));
        return row;
    }

    private View userRow(final String uid, final String name, final String pub) {
        JSONObject prof = cachedUsers.optJSONObject(uid);
        boolean online = Presence.isOnline(prof);
        LocalDb.Msg last = db.lastMessage(roomId(uid));
        String sub;
        String time = null;
        if (last != null) {
            sub = (last.sender.equals(myUid) ? "Tum: " : "") + MediaHelper.previewText(last.body);
            time = timeLabel(last.ts);
        } else {
            sub = (pub == null || pub.isEmpty())
                    ? "Abhi app nahi khola — key nahi bani"
                    : "🔒 End-to-end encrypted";
        }
        LinearLayout row = rowBase(name.isEmpty() ? "(bina naam)" : name, sub, time, online, uid, false, prof);
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("peerUid", uid);
            i.putExtra("peerName", name);
            i.putExtra("peerPub", pub == null ? "" : pub);
            startActivity(i);
        });
        return row;
    }

    // ---------------- status ----------------

    private boolean fresh(JSONObject st) {
        if (st == null) return false;
        long ts = st.optLong("ts", 0);
        return ts > 0 && System.currentTimeMillis() - ts < 24L * 3600 * 1000;
    }

    private void renderStatuses() {
        if (banned) return;
        statusList.removeAllViews();
        JSONObject mine = cachedStatus != null ? cachedStatus.optJSONObject(myUid) : null;
        boolean myFresh = fresh(mine);
        LinearLayout myRow = rowBase("Mera Status",
                myFresh ? "Laga hua hai • " + timeLabel(mine.optLong("ts")) + " — manage ke liye tap karo"
                        : "Photo ya text status lagao — 24 ghante rahega",
                null, false, myUid, false, cachedUsers != null ? cachedUsers.optJSONObject(myUid) : null);
        myRow.setOnClickListener(v -> {
            if (myFresh) {
                new AlertDialog.Builder(this)
                        .setTitle("Mera Status")
                        .setItems(new String[]{"👀 Apna status dekho", "➕ Naya status lagao", "🗑 Status hatao"}, (d, w) -> {
                            if (w == 0) openStatus(myUid, myName == null ? "Mein" : myName);
                            else if (w == 1) postStatusMenu();
                            else Api.dbDelete(this, "/status/" + myUid, (ok, err) -> {
                                Toast.makeText(this, "Status hat gaya", Toast.LENGTH_SHORT).show();
                                loadAll();
                            });
                        })
                        .show();
            } else {
                postStatusMenu();
            }
        });
        statusList.addView(myRow);

        if (cachedStatus != null) {
            Iterator<String> it = cachedStatus.keys();
            List<String> uids = new ArrayList<>();
            while (it.hasNext()) {
                String uid = it.next();
                if (uid.equals(myUid)) continue;
                if (!fresh(cachedStatus.optJSONObject(uid))) continue;
                if (AppPrefs.isBlocked(this, uid)) continue;
                uids.add(uid);
            }
            Collections.sort(uids, (a, b) -> Long.compare(
                    cachedStatus.optJSONObject(b).optLong("ts"), cachedStatus.optJSONObject(a).optLong("ts")));
            for (String uid : uids) {
                JSONObject st = cachedStatus.optJSONObject(uid);
                String name = st.optString("name", "");
                if (cachedUsers != null && cachedUsers.optJSONObject(uid) != null) {
                    String dn = cachedUsers.optJSONObject(uid).optString("name", "");
                    if (!dn.isEmpty()) name = dn;
                }
                final String fUid = uid;
                final String fName = name;
                LinearLayout row = rowBase(name.isEmpty() ? "Status" : name,
                        "Status • " + timeLabel(st.optLong("ts")), null, false, uid, true, cachedUsers != null ? cachedUsers.optJSONObject(uid) : null);
                row.setOnClickListener(v -> openStatus(fUid, fName));
                statusList.addView(row);
            }
        }
    }

    private void openStatus(String authorUid, String authorName) {
        Intent i = new Intent(this, StatusActivity.class);
        i.putExtra("authorUid", authorUid);
        i.putExtra("authorName", authorName);
        startActivity(i);
    }

    private void postStatusMenu() {
        new AlertDialog.Builder(this)
                .setTitle("Status lagao")
                .setItems(new String[]{"📷 Photo status", "✏️ Text status"}, (d, w) -> {
                    if (w == 0) {
                        Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                        pick.setType("image/*");
                        startActivityForResult(pick, REQ_STATUS_PHOTO);
                    } else {
                        final EditText et = new EditText(this);
                        et.setHint("Status likho...");
                        new AlertDialog.Builder(this)
                                .setTitle("Text Status")
                                .setView(et)
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton("Lagao", (d2, w2) -> {
                                    String text = et.getText().toString().trim();
                                    if (text.isEmpty()) return;
                                    try {
                                        JSONObject p = new JSONObject();
                                        p.put("t", "text");
                                        p.put("text", text);
                                        publishStatus(p.toString());
                                    } catch (Exception ignored) {
                                    }
                                })
                                .show();
                    }
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_STATUS_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        Toast.makeText(this, "Status tayyar ho raha hai...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] bytes = MediaHelper.prepPhoto(this, uri);
                runOnUiThread(() -> MediaHelper.uploadBlob(this, bytes, (blobId, keyB64, err) -> {
                    if (err != null) {
                        Toast.makeText(this, "Status nahi laga", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    publishStatus(MediaHelper.photoPayload(blobId, keyB64));
                }));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Photo parhi nahi gayi", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** Encrypts the payload once and wraps the key for every user (the status audience). */
    private void publishStatus(final String payloadJson) {
        Api.dbGet(this, "/users", (users, err) -> {
            if (users == null) {
                Toast.makeText(this, "Users load nahi hue — status nahi laga", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                byte[] statusKey = Crypto.randomBytes(32);
                byte[] nonce = Crypto.randomBytes(12);
                byte[] ct = Crypto.aesGcmEncrypt(statusKey, nonce,
                        payloadJson.getBytes(StandardCharsets.UTF_8),
                        "guftagu-status".getBytes(StandardCharsets.UTF_8));
                JSONObject wraps = new JSONObject();
                Iterator<String> it = users.keys();
                while (it.hasNext()) {
                    String uid = it.next();
                    JSONObject prof = users.optJSONObject(uid);
                    String pub = prof != null ? prof.optString("pub", "") : "";
                    if (pub.isEmpty()) continue;
                    try {
                        byte[] wk = Crypto.groupWrapKey(Crypto.myPriv(this), Crypto.unb64(pub));
                        byte[] wn = Crypto.randomBytes(12);
                        byte[] wrapped = Crypto.aesGcmEncrypt(wk, wn, statusKey,
                                "statuswrap".getBytes(StandardCharsets.UTF_8));
                        JSONObject w = new JSONObject();
                        w.put("n", Crypto.b64(wn));
                        w.put("w", Crypto.b64(wrapped));
                        wraps.put(uid, w);
                    } catch (Exception ignored) {
                    }
                }
                JSONObject st = new JSONObject();
                st.put("name", myName == null ? "" : myName);
                st.put("ts", System.currentTimeMillis());
                st.put("n", Crypto.b64(nonce));
                st.put("c", Crypto.b64(ct));
                st.put("wraps", wraps);
                Api.dbPut(this, "/status/" + myUid, st, (ok, e2) -> {
                    if (e2 != null) Toast.makeText(this, "Status nahi laga", Toast.LENGTH_SHORT).show();
                    else {
                        Toast.makeText(this, "Status lag gaya — 24 ghante rahega ⏰", Toast.LENGTH_SHORT).show();
                        loadAll();
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "Status encryption masla", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindAvatarPhoto(FrameLayout frame, final TextView init, JSONObject pic) {
        if (pic == null) return;
        String b = pic.optString("b", "");
        String k = pic.optString("k", "");
        if (b.isEmpty() || k.isEmpty()) return;
        final android.widget.ImageView iv = new android.widget.ImageView(this);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        frame.addView(iv, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        MediaHelper.fetchBlob(this, b, k, (file, err) -> {
            if (file == null || !alive) return;
            android.graphics.Bitmap bmp = decodeSampled(file, 200);
            if (bmp == null) return;
            iv.setImageBitmap(MediaHelper.circular(bmp));
            init.setVisibility(View.GONE);
        });
    }

    private android.graphics.Bitmap decodeSampled(java.io.File f, int targetW) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / sample > targetW) sample *= 2;
            o.inSampleSize = sample;
            o.inJustDecodeBounds = false;
            return android.graphics.BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) {
            return null;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
