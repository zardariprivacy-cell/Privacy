package com.nawazishali.guftagu;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Groups / Communities: the shared group, my custom groups, and group creation. */
public class GroupsActivity extends BaseActivity {

    private LinearLayout list;
    private String myUid, myName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myUid = Session.uid(this);
        myName = Session.name(this);

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
        title.setText("  👥 Groups");
        title.setTextColor(AppPrefs.ink(this));
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        head.addView(title);
        root.addView(head);

        Button create = new Button(this);
        create.setText("➕ Naya Group Banao");
        create.setBackgroundResource(R.drawable.bg_btn_teal);
        create.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        clp.topMargin = dp(12);
        create.setLayoutParams(clp);
        create.setOnClickListener(v -> createNameDialog());
        root.addView(create);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
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
        list.addView(row("Sab ka Group 👥", "Sab members ka shared group",
                v -> startActivity(new Intent(this, GroupActivity.class)), null));
        Api.dbGet(this, "/users/" + myUid + "/mygroups", (mine, err) -> {
            if (mine == null) return;
            Iterator<String> it = mine.keys();
            while (it.hasNext()) {
                final String gid = it.next();
                Api.dbGet(this, "/groups/" + gid, (g, e2) -> {
                    if (g == null) return; // not a member (or deleted) — rules deny the read
                    final String gname = g.optString("name", "Group");
                    final String gowner = g.optString("owner", "");
                    JSONObject members = g.optJSONObject("members");
                    final int count = members != null ? members.length() : 0;
                    list.addView(row(gname, "👥 " + count + " members — kholne ke liye tap",
                            v -> {
                                Intent i = new Intent(this, GroupActivity.class);
                                i.putExtra("gid", gid);
                                i.putExtra("gname", gname);
                                i.putExtra("gowner", gowner);
                                startActivity(i);
                            },
                            v -> {
                                groupLongMenu(gid, gname, gowner);
                                return true;
                            }));
                });
            }
        });
    }

    private void groupLongMenu(final String gid, final String gname, final String gowner) {
        boolean mine = myUid.equals(gowner);
        new AlertDialog.Builder(this)
                .setTitle(gname)
                .setItems(new String[]{mine ? "🗑 Group delete karo (sab ke liye)" : "🚪 Group chhoro"}, (d, which) -> {
                    if (mine) {
                        Api.dbDelete(this, "/groups/" + gid, (ok, e) -> { });
                        Api.dbDelete(this, "/users/" + myUid + "/mygroups/" + gid, (ok, e) -> { });
                        Toast.makeText(this, "Group delete ho gaya", Toast.LENGTH_SHORT).show();
                    } else {
                        Api.dbDelete(this, "/groups/" + gid + "/members/" + myUid, (ok, e) -> { });
                        Api.dbDelete(this, "/users/" + myUid + "/mygroups/" + gid, (ok, e) -> { });
                        Toast.makeText(this, "Group chhor diya", Toast.LENGTH_SHORT).show();
                    }
                    reload();
                })
                .show();
    }

    private void createNameDialog() {
        final EditText et = new EditText(this);
        et.setHint("Group ka naam, masalan: Family, Doston ki Mehfil");
        new AlertDialog.Builder(this)
                .setTitle("➕ Naya Group")
                .setView(et)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Aagay", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Naam likho pehle", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    pickMembersDialog(name);
                })
                .show();
    }

    private void pickMembersDialog(final String gname) {
        Api.dbGet(this, "/users", (users, err) -> {
            if (users == null) {
                Toast.makeText(this, "Users load nahi hue", Toast.LENGTH_SHORT).show();
                return;
            }
            final List<String> labels = new ArrayList<>();
            final List<String> uids = new ArrayList<>();
            Iterator<String> it = users.keys();
            while (it.hasNext()) {
                String uid = it.next();
                if (uid.equals(myUid)) continue;
                JSONObject u = users.optJSONObject(uid);
                if (u == null) continue;
                labels.add(u.optString("name", "User"));
                uids.add(uid);
            }
            if (labels.isEmpty()) {
                Toast.makeText(this, "Add karne ke liye koi user nahi", Toast.LENGTH_SHORT).show();
                return;
            }
            final boolean[] checked = new boolean[labels.size()];
            new AlertDialog.Builder(this)
                    .setTitle("Members chuno — " + gname)
                    .setMultiChoiceItems(labels.toArray(new String[0]), checked,
                            (d, which, isChecked) -> checked[which] = isChecked)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Group Banao", (d, w) -> {
                        try {
                            JSONObject members = new JSONObject();
                            JSONObject me = new JSONObject();
                            me.put("name", myName == null ? "" : myName);
                            members.put(myUid, me);
                            List<String> chosen = new ArrayList<>();
                            for (int i = 0; i < uids.size(); i++) {
                                if (!checked[i]) continue;
                                JSONObject mo = new JSONObject();
                                mo.put("name", labels.get(i));
                                members.put(uids.get(i), mo);
                                chosen.add(uids.get(i));
                            }
                            JSONObject g = new JSONObject();
                            g.put("name", gname);
                            g.put("owner", myUid);
                            g.put("ts", System.currentTimeMillis());
                            g.put("members", members);
                            Api.dbPost(this, "/groups", g, (gid, e3) -> {
                                if (e3 != null || gid == null) {
                                    Toast.makeText(this, "Group nahi bana — internet check karo", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                try {
                                    JSONObject idx = new JSONObject();
                                    idx.put("name", gname);
                                    Api.dbPut(this, "/users/" + myUid + "/mygroups/" + gid, idx, (ok, e4) -> { });
                                    for (String uid : chosen) {
                                        Api.dbPut(this, "/users/" + uid + "/mygroups/" + gid, idx, (ok, e4) -> { });
                                    }
                                } catch (Exception ignored) {
                                }
                                Intent i = new Intent(this, GroupActivity.class);
                                i.putExtra("gid", gid);
                                i.putExtra("gname", gname);
                                i.putExtra("gowner", myUid);
                                startActivity(i);
                            });
                        } catch (Exception e) {
                            Toast.makeText(this, "Group nahi bana", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        });
    }

    private LinearLayout row(String title, String sub, android.view.View.OnClickListener click,
                             android.view.View.OnLongClickListener longClick) {
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
        if (longClick != null) row.setOnLongClickListener(longClick);
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
