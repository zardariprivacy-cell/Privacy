package com.nawazishali.guftagu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.setName)).setText(
                Session.name(this) == null ? "" : Session.name(this));
        ((TextView) findViewById(R.id.setUsername)).setText("@" +
                (Session.username(this) == null ? "" : Session.username(this)));
        ((TextView) findViewById(R.id.setAbout)).setText(
                "Guftagu — Version 1.0\n" +
                "End-to-end encrypted chat (ECDH P-256 + AES-256-GCM, hash-chain ratchet)\n" +
                "Messages server par sirf encrypted jate hain — server/admin parh nahi sakta\n" +
                "Developed by Nawazish Ali");

        EditText etNewPass = findViewById(R.id.etNewPass);
        ((Button) findViewById(R.id.btnChangePass)).setOnClickListener(v -> {
            String np = etNewPass.getText().toString();
            if (np.length() < 6) {
                Toast.makeText(this, "Naya password kam az kam 6 characters ka ho", Toast.LENGTH_SHORT).show();
                return;
            }
            Api.changePassword(this, np, (ok, err) -> {
                if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                else {
                    etNewPass.setText("");
                    Toast.makeText(this, "Password change ho gaya ✅", Toast.LENGTH_SHORT).show();
                }
            });
        });

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
                etCode.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
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
