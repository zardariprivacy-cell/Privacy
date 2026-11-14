package com.nawazishali.guftagu;

import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class ProfileActivity extends PrefsActivity {

    private EditText etName, etAbout;
    private String uid;

    @Override
    protected String title() { return "Profile"; }

    @Override
    protected void build() {
        uid = Session.uid(this);
        TextView avatar = new TextView(this);
        String n = Session.name(this);
        avatar.setText(n == null || n.trim().isEmpty() ? "?" : n.trim().substring(0, 1).toUpperCase());
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setTextSize(30);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setBackgroundResource(R.drawable.bg_circle);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(84), dp(84));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        avatar.setLayoutParams(alp);
        list.addView(avatar);

        PrefRows.header(this, list, "NAAM");
        etName = input(n == null ? "" : n, "Poora naam");
        list.addView(etName);

        PrefRows.header(this, list, "ABOUT");
        etAbout = input("", "Jaise: Assalamualaikum! Main Guftagu par hoon");
        list.addView(etAbout);

        Button save = new Button(this);
        save.setText("Save Karo");
        save.setTextColor(0xFFFFFFFF);
        save.setTextSize(15);
        save.setBackgroundResource(R.drawable.bg_btn_teal);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        slp.topMargin = dp(18);
        save.setLayoutParams(slp);
        save.setOnClickListener(v -> save());
        list.addView(save);

        PrefRows.note(this, list, "Username: @" + Session.username(this) + " (username badal nahi sakta)");
        PrefRows.note(this, list, "🔒 Tumhari private key sirf is phone mein hai — server par kabhi nahi jati. Phone kho jaye to account wapas nahi aa sakta (E2EE ki qeemat yahi hai).");

        Api.dbGet(this, "/users/" + uid, (prof, err) -> {
            if (prof != null) {
                String about = prof.optString("about", "");
                if (!about.isEmpty()) etAbout.setText(about);
                String name = prof.optString("name", "");
                if (!name.isEmpty()) etName.setText(name);
            }
        });
    }

    private EditText input(String value, String hint) {
        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF8696A0);
        et.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        et.setLayoutParams(lp);
        return et;
    }

    private void save() {
        final String name = etName.getText().toString().trim();
        final String about = etAbout.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Naam khaali nahi ho sakta", Toast.LENGTH_SHORT).show();
            return;
        }
        Api.dbGet(this, "/users/" + uid, (prof, err) -> {
            try {
                JSONObject p = prof != null ? prof : new JSONObject();
                p.put("name", name);
                p.put("about", about);
                if (!p.has("pub")) p.put("pub", Crypto.myPubB64(this));
                Api.dbPut(this, "/users/" + uid, p, (ok, e2) -> {
                    if (e2 != null) Toast.makeText(this, "Save nahi hua", Toast.LENGTH_SHORT).show();
                    else {
                        Session.saveName(this, name);
                        Toast.makeText(this, "Profile save ho gaya ✅", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            } catch (Exception e) {
                Toast.makeText(this, "Masla ho gaya", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
