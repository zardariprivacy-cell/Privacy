package com.nawazishali.guftagu;

import android.app.AlertDialog;

public class ChatsSettingsActivity extends PrefsActivity {

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 91 || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        new Thread(() -> {
            try {
                java.io.InputStream in = getContentResolver().openInputStream(uri);
                android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                if (in != null) in.close();
                if (bmp == null) throw new Exception("decode");
                if (bmp.getWidth() > 1080) {
                    int h = Math.round(bmp.getHeight() * (1080f / bmp.getWidth()));
                    bmp = android.graphics.Bitmap.createScaledBitmap(bmp, 1080, h, true);
                }
                java.io.FileOutputStream out = new java.io.FileOutputStream(AppPrefs.customWallpaperFile(this));
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out);
                out.close();
                runOnUiThread(() -> {
                    AppPrefs.setWallpaper(this, AppPrefs.WALLPAPER_NAMES.length - 1);
                    android.widget.Toast.makeText(this, "Tumhari photo wallpaper lag gayi ✅", android.widget.Toast.LENGTH_SHORT).show();
                    recreate();
                });
            } catch (Exception e) {
                runOnUiThread(() -> android.widget.Toast.makeText(this, "Photo nahi lagi", android.widget.Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    @Override
    protected String title() { return "Chats"; }

    @Override
    protected void build() {
        PrefRows.header(this, list, "DISPLAY");
        PrefRows.textRow(this, list, "Font size", AppPrefs.FONT_NAMES[AppPrefs.fontSize(this)],
                v -> new AlertDialog.Builder(this)
                        .setTitle("Font size")
                        .setSingleChoiceItems(AppPrefs.FONT_NAMES, AppPrefs.fontSize(this), (d, which) -> {
                            AppPrefs.setFontSize(this, which);
                            d.dismiss();
                            recreate();
                        })
                        .show());
        PrefRows.textRow(this, list, "Chat wallpaper", AppPrefs.WALLPAPER_NAMES[AppPrefs.wallpaper(this)],
                v -> new AlertDialog.Builder(this)
                        .setTitle("Chat wallpaper")
                        .setSingleChoiceItems(AppPrefs.WALLPAPER_NAMES, AppPrefs.wallpaper(this), (d, which) -> {
                            d.dismiss();
                            if (which == AppPrefs.WALLPAPER_NAMES.length - 1) {
                                android.content.Intent pick = new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT);
                                pick.setType("image/*");
                                startActivityForResult(pick, 91);
                            } else {
                                AppPrefs.setWallpaper(this, which);
                                recreate();
                            }
                        })
                        .show());

        PrefRows.header(this, list, "CHAT SETTINGS");
        PrefRows.switchRow(this, list, "Enter dabane se message jaye",
                "Keyboard ka Enter dabao to message send ho jaye",
                AppPrefs.enterSends(this), on -> AppPrefs.setEnterSends(this, on));

        PrefRows.header(this, list, "MEDIA AUTO-DOWNLOAD");
        PrefRows.switchRow(this, list, "Photos khud download hon",
                "Band ho to photo par tap karke download karna hoga",
                AppPrefs.autoDlPhoto(this), on -> AppPrefs.setAutoDlPhoto(this, on));
        PrefRows.switchRow(this, list, "Voice messages khud download hon",
                "Band ho to play dabane par download honge",
                AppPrefs.autoDlVoice(this), on -> AppPrefs.setAutoDlVoice(this, on));
    }
}
