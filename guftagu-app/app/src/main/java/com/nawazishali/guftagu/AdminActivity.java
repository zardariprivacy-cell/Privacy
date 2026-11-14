package com.nawazishali.guftagu;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class AdminActivity extends Activity {

    private LinearLayout userList;
    private TextView tvStatus;
    private EditText etBroadcast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!OwnerMode.isUnlocked(this)) {
            Toast.makeText(this, "Admin Panel band hai — pehle Settings se Owner Code daal kar unlock karo", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_admin);
        userList = findViewById(R.id.userList);
        tvStatus = findViewById(R.id.tvAdminStatus);
        etBroadcast = findViewById(R.id.etBroadcast);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnBroadcast).setOnClickListener(v -> {
            String text = etBroadcast.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, "Pehle announcement likho", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject b = new JSONObject();
                b.put("text", text);
                b.put("ts", System.currentTimeMillis());
                Api.dbPut(this, "/admin/broadcast", b, (ok, err) -> {
                    if (err != null) Toast.makeText(this, "Nahi gaya — ye sirf OWNER account (nawazish) se hota hai", Toast.LENGTH_LONG).show();
                    else Toast.makeText(this, "Announcement sab ko bhej diya 📢", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception ignored) {
            }
        });
        findViewById(R.id.btnClearBroadcast).setOnClickListener(v ->
                Api.dbDelete(this, "/admin/broadcast", (ok, err) -> {
                    if (err != null) Toast.makeText(this, "Nahi hata — ye sirf OWNER account se hota hai", Toast.LENGTH_LONG).show();
                    else {
                        etBroadcast.setText("");
                        Toast.makeText(this, "Announcement hata diya", Toast.LENGTH_SHORT).show();
                    }
                }));

        Api.dbGet(this, "/admin/broadcast", (b, err) -> {
            if (b != null) etBroadcast.setText(b.optString("text", ""));
        });
        loadUsers();
    }

    private void loadUsers() {
        Api.dbGet(this, "/bans", (bans, e1) ->
                Api.dbGet(this, "/users", (users, e2) -> {
                    userList.removeAllViews();
                    if (users == null) {
                        tvStatus.setText("Users load nahi hue — internet check karo");
                        return;
                    }
                    List<String[]> rows = new ArrayList<>();
                    Iterator<String> it = users.keys();
                    while (it.hasNext()) {
                        String uid = it.next();
                        JSONObject u = users.optJSONObject(uid);
                        if (u == null) continue;
                        rows.add(new String[]{uid, u.optString("name", "")});
                    }
                    Collections.sort(rows, (a, b) -> a[1].compareToIgnoreCase(b[1]));
                    tvStatus.setText("Total users: " + rows.size() +
                            "\n🔒 Note: Admin private chats NAHI parh sakta — messages end-to-end encrypted hain. Admin sirf users, ban aur announcements sambhal sakta hai.");
                    for (String[] r : rows) {
                        boolean banned = bans != null && bans.has(r[0]);
                        userList.addView(userRow(r[0], r[1], banned));
                    }
                }));
    }

    private View userRow(final String uid, String name, final boolean banned) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        row.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        texts.setLayoutParams(tlp);
        TextView tvN = new TextView(this);
        boolean isOwner = uid.equals(G.OWNER_UID);
        tvN.setText(name.isEmpty() ? "(bina naam)" : name);
        tvN.setTextColor(0xFFFFFFFF);
        tvN.setTextSize(15);
        tvN.setTypeface(null, Typeface.BOLD);
        texts.addView(tvN);
        TextView tvU = new TextView(this);
        tvU.setText(isOwner ? "OWNER — tumhara account" : (banned ? "⛔ BANNED" : uid.substring(0, Math.min(10, uid.length())) + "…"));
        tvU.setTextColor(banned ? 0xFFF44336 : 0xFF8696A0);
        tvU.setTextSize(12);
        texts.addView(tvU);
        row.addView(texts);

        if (!isOwner) {
            Button b = new Button(this);
            b.setText(banned ? "Unban" : "Ban");
            b.setTextColor(0xFFFFFFFF);
            b.setBackgroundResource(banned ? R.drawable.bg_btn_teal : R.drawable.bg_btn_danger);
            b.setOnClickListener(v -> {
                if (banned) {
                    Api.dbDelete(this, "/bans/" + uid, (ok, err) -> {
                        if (err != null) Toast.makeText(this, "Nahi hua — ye sirf OWNER account se hota hai", Toast.LENGTH_LONG).show();
                        else { Toast.makeText(this, "Unban ho gaya", Toast.LENGTH_SHORT).show(); loadUsers(); }
                    });
                } else {
                    Api.dbPutBool(this, "/bans/" + uid, true, (ok, err) -> {
                        if (err != null) Toast.makeText(this, "Nahi hua — ye sirf OWNER account se hota hai", Toast.LENGTH_LONG).show();
                        else { Toast.makeText(this, "User ban ho gaya ⛔", Toast.LENGTH_SHORT).show(); loadUsers(); }
                    });
                }
            });
            row.addView(b);
        }
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
