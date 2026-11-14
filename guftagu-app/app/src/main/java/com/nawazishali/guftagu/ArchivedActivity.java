package com.nawazishali.guftagu;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Iterator;

/** Archived chats: tap to open, long-press to unarchive. */
public class ArchivedActivity extends BaseActivity {

    private LinearLayout list;
    private String myUid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myUid = Session.uid(this);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundResource(R.drawable.bg_app);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(14));
        sv.addView(root);
        setContentView(sv);

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
        title.setText("  📥 Archived Chats");
        title.setTextColor(AppPrefs.ink(this));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(title);
        root.addView(head);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(10);
        list.setLayoutParams(llp);
        root.addView(list);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        list.removeAllViews();
        Api.dbGet(this, "/users", (users, err) -> {
            list.removeAllViews();
            boolean any = false;
            if (AppPrefs.isArchived(this, "group")) {
                any = true;
                list.addView(row("Sab ka Group", "👥 Group chat — kholne ke liye tap, unarchive ke liye long-press",
                        v -> startActivity(new Intent(this, GroupActivity.class)),
                        v -> {
                            unarchive("group", "Sab ka Group");
                            return true;
                        }));
            }
            if (users != null) {
                Iterator<String> it = users.keys();
                while (it.hasNext()) {
                    final String uid = it.next();
                    if (uid.equals(myUid) || !AppPrefs.isArchived(this, uid)) continue;
                    JSONObject u = users.optJSONObject(uid);
                    if (u == null) continue;
                    final String name = u.optString("name", "User");
                    final String pub = u.optString("pub", "");
                    any = true;
                    list.addView(row(name, "Chat kholne ke liye tap, unarchive ke liye long-press",
                            v -> {
                                Intent i = new Intent(this, ChatActivity.class);
                                i.putExtra("peerUid", uid);
                                i.putExtra("peerName", name);
                                i.putExtra("peerPub", pub);
                                startActivity(i);
                            },
                            v -> {
                                unarchive(uid, name);
                                return true;
                            }));
                }
            }
            if (!any) {
                TextView none = new TextView(this);
                none.setText("Koi archived chat nahi hai.");
                none.setTextColor(AppPrefs.sub(this));
                none.setTextSize(14);
                none.setPadding(dp(4), dp(14), dp(4), 0);
                list.addView(none);
            }
        });
    }

    private void unarchive(final String key, final String label) {
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setItems(new String[]{"📤 Archive se hatao"}, (d, which) -> {
                    AppPrefs.setArchived(this, key, false);
                    Toast.makeText(this, "Archive se wapas aa gaya", Toast.LENGTH_SHORT).show();
                    reload();
                })
                .show();
    }

    private LinearLayout row(String title, String sub, View.OnClickListener click,
                             View.OnLongClickListener longClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        row.setLayoutParams(rlp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(AppPrefs.ink(this));
        t.setTextSize(16);
        row.addView(t);
        TextView s2 = new TextView(this);
        s2.setText(sub);
        s2.setTextColor(AppPrefs.sub(this));
        s2.setTextSize(13);
        row.addView(s2);
        row.setOnClickListener(click);
        row.setOnLongClickListener(longClick);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
