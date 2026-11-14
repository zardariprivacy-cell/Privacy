package com.nawazishali.guftagu;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class HomeActivity extends Activity {

    private LinearLayout chatList;
    private TextView tvStatus, tvBroadcast;
    private String myUid;
    private final Handler handler = new Handler();
    private boolean alive = true;

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
        chatList = findViewById(R.id.chatList);
        tvStatus = findViewById(R.id.tvHomeStatus);
        tvBroadcast = findViewById(R.id.tvBroadcast);

        Button btnAdmin = findViewById(R.id.btnAdmin);
        btnAdmin.setVisibility(OwnerMode.isUnlocked(this) ? View.VISIBLE : View.GONE);
        btnAdmin.setOnClickListener(v -> startActivity(new Intent(this, AdminActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        loadAll();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!alive) return;
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
            if (bans != null && bans.has(myUid)) {
                tvStatus.setText("⛔ Tumhara account owner ne ban kiya hai — tum chat nahi kar sakte.");
                chatList.removeAllViews();
                return;
            }
            loadDirectory();
        });
    }

    private void loadDirectory() {
        Api.dbGet(this, "/users", (users, err) -> {
            if (err != null) {
                tvStatus.setText("Users load nahi hue — internet check karo");
                return;
            }
            chatList.removeAllViews();
            chatList.addView(groupRow());
            List<String[]> rows = new ArrayList<>();
            if (users != null) {
                Iterator<String> it = users.keys();
                while (it.hasNext()) {
                    String uid = it.next();
                    if (uid.equals(myUid)) continue;
                    JSONObject u = users.optJSONObject(uid);
                    if (u == null) continue;
                    rows.add(new String[]{uid, u.optString("name", ""), u.optString("pub", "")});
                }
            }
            Collections.sort(rows, (a, b) -> a[1].compareToIgnoreCase(b[1]));
            if (rows.isEmpty()) {
                tvStatus.setText("Abhi aur koi user nahi hai — doston ko Guftagu par bulao!");
            } else {
                tvStatus.setText(rows.size() + " users — kisi par tap karke private chat shuru karo");
            }
            for (String[] r : rows) chatList.addView(userRow(r[0], r[1], r[2]));
        });
    }

    private LinearLayout rowBase(String title, String sub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);

        TextView init = new TextView(this);
        init.setGravity(Gravity.CENTER);
        init.setTextSize(16);
        init.setTextColor(0xFFFFFFFF);
        init.setTypeface(null, Typeface.BOLD);
        init.setBackgroundResource(R.drawable.bg_circle);
        init.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        String t = title.trim();
        init.setText(t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase());
        row.addView(init);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(12);
        texts.setLayoutParams(tlp);
        TextView tvT = new TextView(this);
        tvT.setText(title);
        tvT.setTextColor(0xFFFFFFFF);
        tvT.setTextSize(16);
        tvT.setTypeface(null, Typeface.BOLD);
        texts.addView(tvT);
        TextView tvS = new TextView(this);
        tvS.setText(sub);
        tvS.setTextColor(0xFF8696A0);
        tvS.setTextSize(12);
        texts.addView(tvS);
        row.addView(texts);
        return row;
    }

    private View groupRow() {
        LinearLayout row = rowBase("Sab ka Group", "👥 Sab members • 🔒 group encryption");
        row.setOnClickListener(v -> startActivity(new Intent(this, GroupActivity.class)));
        return row;
    }

    private View userRow(final String uid, final String name, final String pub) {
        String sub = (pub == null || pub.isEmpty())
                ? "Abhi app nahi khola — key nahi bani"
                : "🔒 End-to-end encrypted";
        LinearLayout row = rowBase(name.isEmpty() ? "(bina naam)" : name, sub);
        row.setOnClickListener(v -> {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra("peerUid", uid);
            i.putExtra("peerName", name);
            i.putExtra("peerPub", pub == null ? "" : pub);
            startActivity(i);
        });
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
