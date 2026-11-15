package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

public class CrashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);
        TextView tvCrash = findViewById(R.id.tvCrash);
        // Students get a friendly message; the raw technical error is owner-only.
        if (OwnerMode.isUnlocked(this)) {
            ((TextView) findViewById(R.id.tvCrashTitle)).setText("The app stopped — technical details (Owner)");
            ((TextView) findViewById(R.id.tvCrashSub)).setText("Owner Mode: error details are shown below for debugging.");
            tvCrash.setText(CrashSaver.read(this));
        } else {
            tvCrash.setVisibility(android.view.View.GONE);
        }
        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            CrashSaver.clear(this);
            long uid = Session.currentUserId(this);
            Intent i = new Intent(this, uid > 0 ? HomeActivity.class : LoginActivity.class);
            startActivity(i);
            finish();
        });
    }
}
