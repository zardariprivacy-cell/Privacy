package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ProfileActivity extends Activity {

    private static final int REQ_PICK_PHOTO = 42;
    private Db db;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        db = new Db(this);

        findViewById(R.id.btnChangePhoto).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("image/*");
            startActivityForResult(Intent.createChooser(i, "Choose profile picture"), REQ_PICK_PHOTO);
        });
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Session.clear(this);
            Intent i = new Intent(this, LoginActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        user = db.getUser(Session.currentUserId(this));
        if (user == null) {
            finish();
            return;
        }
        AvatarHelper.load(findViewById(R.id.imgPhoto), findViewById(R.id.tvAvatarInitials), user);
        ((TextView) findViewById(R.id.tvName)).setText(user.name);
        ((TextView) findViewById(R.id.tvUsername)).setText("Username: " + user.username);
        ((TextView) findViewById(R.id.tvRole)).setText(user.admin
                ? "Role: Admin — tum MCQs change kar sakte ho"
                : "Role: Student");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_PHOTO || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            // 1) Copy the picked image into a temp file.
            File tmp = new File(getFilesDir(), "profile_tmp_" + user.id);
            try (InputStream in = getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(tmp)) {
                if (in == null) throw new Exception("no stream");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            // 2) Shrink it to a small JPEG (max 512px) so it can never be too
            //    heavy to load again. If shrinking fails, keep the raw copy —
            //    avatars are decoded down-sampled at load time anyway.
            File out = new File(getFilesDir(), "profile_" + user.id + ".jpg");
            boolean shrunk = false;
            try {
                android.graphics.Bitmap bmp = AvatarHelper.decodeSampled(tmp.getAbsolutePath(), 512);
                if (bmp != null) {
                    try (OutputStream os = new FileOutputStream(out)) {
                        shrunk = bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, os);
                    }
                    bmp.recycle();
                }
            } catch (Throwable ignored) {
                shrunk = false;
            }
            if (!shrunk) {
                try (InputStream in = new java.io.FileInputStream(tmp);
                     OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                }
            }
            tmp.delete();
            db.updatePhoto(user.id, out.getAbsolutePath());
            Toast.makeText(this, "Profile picture lag gayi!", Toast.LENGTH_SHORT).show();
            onResume();
        } catch (Exception e) {
            Toast.makeText(this, "Photo nahi lagi — dobara try karo", Toast.LENGTH_LONG).show();
        }
    }
}
