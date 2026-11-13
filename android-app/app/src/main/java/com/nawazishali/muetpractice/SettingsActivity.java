package com.nawazishali.muetpractice;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    public static final String PREFS = "app_settings";
    public static final String KEY_VIBRATE = "vibrate_on_answer";
    public static final String KEY_EXPLAIN = "show_explanation";

    private Db db;
    private long userId;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        db = new Db(this);
        userId = Session.currentUserId(this);
        if (userId <= 0) {
            finish();
            return;
        }
        user = db.getUser(userId);
        if (user == null) {
            finish();
            return;
        }

        ((TextView) findViewById(R.id.setName)).setText(user.name);
        ((TextView) findViewById(R.id.setUsername)).setText("@" + user.username);
        ((TextView) findViewById(R.id.setRole)).setText(user.admin ? "Admin account" : "Student account");

        EditText etEmail = findViewById(R.id.etEmail);
        if (user.email != null) etEmail.setText(user.email);
        ((Button) findViewById(R.id.btnSaveEmail)).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (!email.isEmpty() && !email.contains("@")) {
                Toast.makeText(this, "Email sahi likho (isme @ hona chahiye)", Toast.LENGTH_SHORT).show();
                return;
            }
            db.updateEmail(userId, email.isEmpty() ? null : email);
            Toast.makeText(this, "Email save ho gaya", Toast.LENGTH_SHORT).show();
        });

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        Switch swVibrate = findViewById(R.id.swVibrate);
        Switch swExplain = findViewById(R.id.swExplain);
        swVibrate.setChecked(prefs.getBoolean(KEY_VIBRATE, true));
        swExplain.setChecked(prefs.getBoolean(KEY_EXPLAIN, true));
        swVibrate.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(KEY_VIBRATE, on).apply());
        swExplain.setOnCheckedChangeListener((b, on) ->
                prefs.edit().putBoolean(KEY_EXPLAIN, on).apply());

        EditText etOld = findViewById(R.id.etOldPass);
        EditText etNew = findViewById(R.id.etNewPass);
        ((Button) findViewById(R.id.btnChangePass)).setOnClickListener(v -> {
            String oldP = etOld.getText().toString();
            String newP = etNew.getText().toString();
            if (newP.length() < 4) {
                Toast.makeText(this, "Naya password kam az kam 4 characters ka ho", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!db.checkPassword(userId, oldP)) {
                Toast.makeText(this, "Purana password ghalat hai", Toast.LENGTH_SHORT).show();
                return;
            }
            db.updatePassword(userId, newP);
            etOld.setText("");
            etNew.setText("");
            Toast.makeText(this, "Password change ho gaya", Toast.LENGTH_SHORT).show();
        });

        ((Button) findViewById(R.id.btnResetProgress)).setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Reset My Progress")
                        .setMessage("Tumhare saare test results aur performance delete ho jayenge. Ye wapas nahi aayega. Pakka?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            db.deleteResultsForUser(userId);
                            Toast.makeText(this, "Progress reset ho gaya", Toast.LENGTH_SHORT).show();
                        })
                        .show());

        ((TextView) findViewById(R.id.setAbout)).setText(
                "UniTest Prep — Version 3.6\n" +
                "Total MCQs in this app: " + db.totalQuestions() + "\n" +
                "Subjects: Mathematics • Physics • Chemistry • English\n" +
                "For entry tests of all universities\n" +
                "Developed by Nawazish Ali\n" +
                "Ad status: " + Ads.bannerStatus + " (" + Ads.sdkStatus + ")");

        ((Button) findViewById(R.id.btnShare)).setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                    "UniTest Prep — 4,480 Entry Test MCQs! Har topic mein 50 sawal, " +
                    "Mathematics, Physics, Chemistry aur English. Saari universities ke entry test ki tayyari ke liye best app!\n" +
                    "Download: https://drive.google.com/uc?id=1kF0AWDSx5WfLgStzgMFshvBc70C6l7I1&export=download");
            startActivity(Intent.createChooser(share, "Share UniTest Prep"));
        });
    }
}
