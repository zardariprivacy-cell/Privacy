package com.nawazishali.guftagu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        String name = Session.name(this);
        ((TextView) findViewById(R.id.tvProfName)).setText(name == null ? "" : name);
        TextView avatar = findViewById(R.id.avatarInit);
        avatar.setText(name == null || name.trim().isEmpty() ? "?" : name.trim().substring(0, 1).toUpperCase());
        findViewById(R.id.profileCard).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        LinearLayout menu = findViewById(R.id.menuList);
        PrefRows.textRow(this, menu, "🔒 Privacy", "Read receipts, last seen, block list",
                v -> startActivity(new Intent(this, PrivacyActivity.class)));
        PrefRows.textRow(this, menu, "💬 Chats", "Font size, wallpaper, Enter se send",
                v -> startActivity(new Intent(this, ChatsSettingsActivity.class)));
        PrefRows.textRow(this, menu, "🎨 Theme", AppPrefs.darkMode(this) ? "Dark mode — ON hai" : "Light mode — ON hai",
                v -> new AlertDialog.Builder(this)
                        .setTitle("Theme chuno")
                        .setSingleChoiceItems(new String[]{"☀️ Light", "🌙 Dark"}, AppPrefs.darkMode(this) ? 1 : 0, (d, which) -> {
                            AppPrefs.setDarkMode(this, which == 1);
                            d.dismiss();
                            recreate();
                        })
                        .show());
        PrefRows.textRow(this, menu, "🔔 Notifications", "Awaz aur vibration (chat khuli ho to)",
                v -> startActivity(new Intent(this, NotificationsActivity.class)));
        PrefRows.textRow(this, menu, "💾 Storage", "Cache, local history",
                v -> startActivity(new Intent(this, StorageActivity.class)));
        PrefRows.textRow(this, menu, "👤 Account", "Password change, account delete",
                v -> startActivity(new Intent(this, AccountActivity.class)));
        PrefRows.textRow(this, menu, "🔐 App Lock (PIN)",
                AppPrefs.pinHash(this) != null ? "On hai — badalne/hatane ke liye tap karo" : "Off hai — PIN lagane ke liye tap karo",
                v -> pinMenu());
        PrefRows.textRow(this, menu, "❓ Help / About", "Guftagu 2.6 — encryption aur haddain",
                v -> startActivity(new Intent(this, HelpActivity.class)));

        ((TextView) findViewById(R.id.tvVersion)).setText(
                "Guftagu — Version 2.6 • End-to-end encrypted • Developed by Nawazish Ali");

        refreshOwnerSection();
        ((Button) findViewById(R.id.btnOwner)).setOnClickListener(v -> {
            if (OwnerMode.isUnlocked(this)) {
                OwnerMode.lock(this);
                refreshOwnerSection();
                Toast.makeText(this, "Admin Panel band ho gaya", Toast.LENGTH_SHORT).show();
            } else {
                EditText etCode = new EditText(this);
                etCode.setHint("Owner Code");
                etCode.setSingleLine(true);
                etCode.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                new AlertDialog.Builder(this)
                        .setTitle("Owner Mode")
                        .setMessage("Admin Panel kholne ke liye Owner Code daalo. Ye code sirf app ke owner ke paas hota hai.")
                        .setView(etCode)
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Unlock", (d, w) -> {
                            if (OwnerMode.tryUnlock(this, etCode.getText().toString())) {
                                refreshOwnerSection();
                                Toast.makeText(this, "Owner Mode ON — Home par Admin button nazar ayega", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, "Code ghalat hai", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .show();
            }
        });

        ((Button) findViewById(R.id.btnLogout)).setOnClickListener(v -> {
            Session.clear(this);
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        String name = Session.name(this);
        TextView tv = findViewById(R.id.tvProfName);
        if (tv != null && name != null) tv.setText(name);
        // refresh About line from server profile if present
        Api.dbGet(this, "/users/" + Session.uid(this), (prof, err) -> {
            if (prof != null) {
                String about = prof.optString("about", "");
                TextView ta = findViewById(R.id.tvProfAbout);
                if (ta != null) ta.setText(about.isEmpty() ? "Profile edit karne ke liye tap karo" : about);
            }
        });
    }

    private void pinMenu() {
        boolean has = AppPrefs.pinHash(this) != null;
        if (!has) {
            askPin("Naya PIN lagao (4-6 digits)", pin1 ->
                    askPin("PIN dobara daalo (confirm)", pin2 -> {
                        if (pin1.equals(pin2)) {
                            AppPrefs.setPin(this, pin1);
                            Toast.makeText(this, "App Lock ON ho gaya 🔐", Toast.LENGTH_SHORT).show();
                            recreate();
                        } else {
                            Toast.makeText(this, "Dono PIN ek jaisay nahi — dobara try karo", Toast.LENGTH_SHORT).show();
                        }
                    }));
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("App Lock")
                    .setItems(new String[]{"PIN badlo", "PIN hatao"}, (d, w) -> {
                        if (w == 1) {
                            askPin("Maujooda PIN daalo", pin -> {
                                if (AppPrefs.checkPin(this, pin)) {
                                    AppPrefs.setPin(this, null);
                                    Toast.makeText(this, "App Lock OFF ho gaya", Toast.LENGTH_SHORT).show();
                                    recreate();
                                } else Toast.makeText(this, "PIN ghalat hai", Toast.LENGTH_SHORT).show();
                            });
                        } else {
                            askPin("Maujooda PIN daalo", old -> {
                                if (!AppPrefs.checkPin(this, old)) {
                                    Toast.makeText(this, "PIN ghalat hai", Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                askPin("Naya PIN (4-6 digits)", pin1 ->
                                        askPin("Naya PIN dobara (confirm)", pin2 -> {
                                            if (pin1.equals(pin2)) {
                                                AppPrefs.setPin(this, pin1);
                                                Toast.makeText(this, "PIN badal gaya ✅", Toast.LENGTH_SHORT).show();
                                                recreate();
                                            } else {
                                                Toast.makeText(this, "Dono PIN ek jaisay nahi", Toast.LENGTH_SHORT).show();
                                            }
                                        }));
                            });
                        }
                    })
                    .show();
        }
    }

    private interface PinCb { void got(String pin); }

    private void askPin(String title, final PinCb cb) {
        EditText et = new EditText(this);
        et.setHint("PIN");
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("OK", (d, w) -> {
                    String pin = et.getText().toString().trim();
                    if (pin.length() < 4 || pin.length() > 6) {
                        Toast.makeText(this, "PIN 4 se 6 digits ka ho", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    cb.got(pin);
                })
                .show();
    }

    private void refreshOwnerSection() {
        boolean on = OwnerMode.isUnlocked(this);
        boolean isOwnerAccount = G.OWNER_UID.equals(Session.uid(this));
        ((TextView) findViewById(R.id.tvOwnerStatus)).setText(on
                ? "Admin Panel: ON — Home par Admin button hai." + (isOwnerAccount
                    ? " Tum OWNER account se logged in ho, asal admin powers bhi server par tumhein hi mili hain."
                    : " NOTE: tum OWNER account se logged in NAHI ho — server admin powers sirf owner account ko deta hai.")
                : "Admin Panel: OFF — Admin Panel sirf owner ke liye hai.");
        ((Button) findViewById(R.id.btnOwner)).setText(on ? "Admin Panel Band Karo" : "Owner Code Daal Kar Unlock Karo");
    }
}
