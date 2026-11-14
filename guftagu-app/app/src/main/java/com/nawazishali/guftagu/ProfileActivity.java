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

    private static final int REQ_AVATAR = 71;
    private EditText etName, etAbout;
    private String uid;
    private android.widget.ImageView ivAvatar;
    private TextView tvAvatarInit;

    @Override
    protected String title() { return "Profile"; }

    @Override
    protected void build() {
        uid = Session.uid(this);
        android.widget.FrameLayout avatarWrap = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams awp = new LinearLayout.LayoutParams(dp(84), dp(84));
        awp.gravity = Gravity.CENTER_HORIZONTAL;
        avatarWrap.setLayoutParams(awp);
        tvAvatarInit = new TextView(this);
        String n = Session.name(this);
        tvAvatarInit.setText(n == null || n.trim().isEmpty() ? "?" : n.trim().substring(0, 1).toUpperCase());
        tvAvatarInit.setGravity(Gravity.CENTER);
        tvAvatarInit.setTextColor(0xFFFFFFFF);
        tvAvatarInit.setTextSize(30);
        tvAvatarInit.setTypeface(null, Typeface.BOLD);
        tvAvatarInit.setBackgroundResource(AppPrefs.avatarBgFor(uid));
        avatarWrap.addView(tvAvatarInit, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        ivAvatar = new android.widget.ImageView(this);
        ivAvatar.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        ivAvatar.setVisibility(android.view.View.GONE);
        avatarWrap.addView(ivAvatar, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        list.addView(avatarWrap);

        Button btnPhoto = new Button(this);
        btnPhoto.setText("📷 Profile Photo Lagao");
        btnPhoto.setTextColor(0xFFFFFFFF);
        btnPhoto.setBackgroundResource(R.drawable.bg_btn_teal);
        LinearLayout.LayoutParams bpp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(46));
        bpp.topMargin = dp(12);
        btnPhoto.setLayoutParams(bpp);
        btnPhoto.setOnClickListener(v -> {
            android.content.Intent pick = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
            pick.setType("image/*");
            startActivityForResult(pick, REQ_AVATAR);
        });
        list.addView(btnPhoto);

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
                if (prof.has("pic")) showAvatar(prof.optJSONObject("pic"));
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
        et.setTextColor(AppPrefs.ink(this));
        et.setHintTextColor(AppPrefs.sub(this));
        et.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        et.setLayoutParams(lp);
        return et;
    }

    private void showAvatar(JSONObject pic) {
        if (pic == null) return;
        MediaHelper.fetchBlob(this, pic.optString("b", ""), pic.optString("k", ""), (file, err) -> {
            if (file == null) return;
            try {
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
                if (bmp == null) return;
                ivAvatar.setImageBitmap(MediaHelper.circular(bmp));
                ivAvatar.setVisibility(android.view.View.VISIBLE);
                tvAvatarInit.setVisibility(android.view.View.GONE);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_AVATAR || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        Toast.makeText(this, "Photo tayyar ho raha hai...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] bytes = MediaHelper.prepAvatar(this, uri);
                runOnUiThread(() -> MediaHelper.uploadBlob(this, bytes, (blobId, keyB64, err) -> {
                    if (err != null) {
                        Toast.makeText(this, "Photo nahi lagi", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Api.dbGet(this, "/users/" + uid, (prof, e2) -> {
                        try {
                            JSONObject p = prof != null ? prof : new JSONObject();
                            JSONObject pic = new JSONObject();
                            pic.put("b", blobId);
                            pic.put("k", keyB64);
                            p.put("pic", pic);
                            if (!p.has("pub")) p.put("pub", Crypto.myPubB64(this));
                            if (!p.has("name")) p.put("name", Session.name(this));
                            Api.dbPut(this, "/users/" + uid, p, (ok, e3) -> {
                                if (e3 != null) Toast.makeText(this, "Photo save nahi hui", Toast.LENGTH_SHORT).show();
                                else {
                                    Toast.makeText(this, "Profile photo lag gayi ✅", Toast.LENGTH_SHORT).show();
                                    showAvatar(pic);
                                }
                            });
                        } catch (Exception ignored) {
                        }
                    });
                }));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Photo parhi nahi gayi", Toast.LENGTH_SHORT).show());
            }
        }).start();
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
