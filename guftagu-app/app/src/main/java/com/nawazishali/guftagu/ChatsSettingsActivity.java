package com.nawazishali.guftagu;

import android.app.AlertDialog;

public class ChatsSettingsActivity extends PrefsActivity {

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
                            AppPrefs.setWallpaper(this, which);
                            d.dismiss();
                            recreate();
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
