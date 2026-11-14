package com.nawazishali.guftagu;

import android.app.AlertDialog;
import android.widget.Toast;

import org.json.JSONObject;

public class PrivacyActivity extends PrefsActivity {

    @Override
    protected String title() { return "Privacy"; }

    @Override
    protected void build() {
        PrefRows.header(this, list, "PRIVACY");
        PrefRows.switchRow(this, list, "Read receipts (blue ticks ✓✓)",
                "Band karne se tumhare messages par ticks nahi ayenge, aur tumhein bhi dusron ke nahi dikhenge",
                AppPrefs.readReceipts(this), on -> AppPrefs.setReadReceipts(this, on));
        PrefRows.switchRow(this, list, "Last seen dikhana",
                "On ho to users dekhenge tum kab online the",
                AppPrefs.showLastSeen(this), on -> {
                    AppPrefs.setShowLastSeen(this, on);
                    if (on) Presence.beat(this);
                    else Presence.clear(this);
                });
        PrefRows.switchRow(this, list, "Apna profile chhupao",
                "On ho to naye users ko tum list mein nahi dikhoge",
                AppPrefs.hideProfile(this), on -> {
                    AppPrefs.setHideProfile(this, on);
                    saveHiddenFlag(on);
                });

        PrefRows.header(this, list, "BLOCKED USERS");
        java.util.List<String[]> blocked = AppPrefs.blockedList(this);
        if (blocked.isEmpty()) {
            PrefRows.note(this, list, "Koi blocked nahi hai. Kisi ko block karna ho to us ki chat khol kar 🔑 button se block karo.");
        }
        for (String[] b : blocked) {
            final String uid = b[0];
            PrefRows.textRow(this, list, b[1].isEmpty() ? "User" : b[1], "Unblock karne ke liye tap karo",
                    v -> new AlertDialog.Builder(this)
                            .setTitle("Unblock karo?")
                            .setMessage("Is user ko unblock kar dein?")
                            .setNegativeButton("Nahi", null)
                            .setPositiveButton("Unblock", (d, w) -> {
                                AppPrefs.unblock(this, uid);
                                Toast.makeText(this, "Unblock ho gaya", Toast.LENGTH_SHORT).show();
                                recreate();
                            })
                            .show());
        }

        PrefRows.header(this, list, "ENCRYPTION");
        PrefRows.note(this, list, "Har chat end-to-end encrypted hai. Har chat ke 🔑 button se apna Safety Number mila kar confirm kar sakte ho ke beech mein koi nahi hai. Admin bhi tumhari chats nahi parh sakta.");
    }

    private void saveHiddenFlag(final boolean hidden) {
        final String uid = Session.uid(this);
        Api.dbGet(this, "/users/" + uid, (prof, err) -> {
            if (prof == null) return;
            try {
                prof.put("hidden", hidden);
                Api.dbPut(this, "/users/" + uid, prof, (ok, e2) -> { });
            } catch (Exception ignored) {
            }
        });
    }
}
