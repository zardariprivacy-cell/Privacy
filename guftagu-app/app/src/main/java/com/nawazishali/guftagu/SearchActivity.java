package com.nawazishali.guftagu;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Global search across all locally stored (decrypted) messages. */
public class SearchActivity extends BaseActivity {

    private EditText et;
    private LinearLayout results;
    private LocalDb db;
    private String myUid;
    private final Map<String, JSONObject> users = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new LocalDb(this);
        myUid = Session.uid(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_app);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        setContentView(root);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(android.view.Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("←");
        back.setBackgroundResource(R.drawable.bg_btn_ghost);
        back.setTextColor(AppPrefs.ink(this));
        back.setOnClickListener(v -> finish());
        head.addView(back);
        TextView title = new TextView(this);
        title.setText("  🔍 Message Search");
        title.setTextColor(AppPrefs.ink(this));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(title);
        root.addView(head);

        et = new EditText(this);
        et.setHint("Lafz likho jo dhundna hai...");
        et.setBackgroundResource(R.drawable.bg_input);
        et.setTextColor(AppPrefs.ink(this));
        et.setHintTextColor(AppPrefs.sub(this));
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        elp.topMargin = dp(12);
        et.setLayoutParams(elp);
        String q0 = getIntent().getStringExtra("q");
        if (q0 != null) et.setText(q0);
        root.addView(et);

        Button go = new Button(this);
        go.setText("Dhundo");
        go.setBackgroundResource(R.drawable.bg_btn_teal);
        go.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        glp.topMargin = dp(10);
        go.setLayoutParams(glp);
        go.setOnClickListener(v -> runSearch());
        root.addView(go);

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        slp.topMargin = dp(10);
        sv.setLayoutParams(slp);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        sv.addView(results);
        root.addView(sv);

        Api.dbGet(this, "/users", (u, err) -> {
            if (u != null) {
                Iterator<String> it = u.keys();
                while (it.hasNext()) {
                    String uid = it.next();
                    JSONObject prof = u.optJSONObject(uid);
                    if (prof != null) users.put(uid, prof);
                }
            }
            if (q0 != null && !q0.trim().isEmpty()) runSearch();
        });
    }

    private void runSearch() {
        results.removeAllViews();
        final String q = et.getText().toString().trim();
        if (q.isEmpty()) return;
        List<LocalDb.Hit> hits = db.search(q);
        int shown = 0;
        for (final LocalDb.Hit h : hits) {
            String disp = Meta.displayText(h.msg.body);
            if (!disp.toLowerCase(Locale.getDefault()).contains(q.toLowerCase(Locale.getDefault()))) continue;
            final boolean isGroup = "group".equals(h.room);
            String peerUid = null;
            String name = "Sab ka Group";
            if (!isGroup) {
                String[] parts = h.room.split("_");
                if (parts.length == 2) peerUid = parts[0].equals(myUid) ? parts[1] : parts[0];
                JSONObject prof = peerUid != null ? users.get(peerUid) : null;
                name = prof != null && !prof.optString("name", "").isEmpty()
                        ? prof.optString("name") : "User";
            }
            final String fPeer = peerUid;
            final String fName = name;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = dp(8);
            row.setLayoutParams(rlp);
            TextView t1 = new TextView(this);
            t1.setText(name + "  •  " + new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(new Date(h.msg.ts)));
            t1.setTextColor(AppPrefs.ink(this));
            t1.setTextSize(13);
            t1.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(t1);
            TextView t2 = new TextView(this);
            t2.setText(disp);
            t2.setTextColor(AppPrefs.sub(this));
            t2.setTextSize(14);
            t2.setMaxLines(2);
            row.addView(t2);
            row.setOnClickListener(v -> {
                if (isGroup) {
                    startActivity(new Intent(this, GroupActivity.class));
                } else if (fPeer != null) {
                    JSONObject prof = users.get(fPeer);
                    Intent i = new Intent(this, ChatActivity.class);
                    i.putExtra("peerUid", fPeer);
                    i.putExtra("peerName", fName);
                    i.putExtra("peerPub", prof != null ? prof.optString("pub", "") : "");
                    startActivity(i);
                }
            });
            results.addView(row);
            shown++;
            if (shown >= 50) break;
        }
        if (shown == 0) {
            TextView none = new TextView(this);
            none.setText("Koi message nahi mila is lafz ke saath.");
            none.setTextColor(AppPrefs.sub(this));
            none.setTextSize(14);
            none.setPadding(dp(4), dp(14), dp(4), 0);
            results.addView(none);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
