package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // If the previous run crashed, show the saved error first.
        if (CrashSaver.hasPending(this)) {
            startActivity(new Intent(this, CrashActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_splash);

        // Warm up the database (creates it and loads the starter questions
        // on first run). Guarded so a storage hiccup can never kill launch —
        // the database also opens lazily on first real use.
        try {
            new Db(this).getReadableDatabase();
        } catch (Exception ignored) {
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            long uid = Session.currentUserId(this);
            Intent i = new Intent(this, uid > 0 ? HomeActivity.class : LoginActivity.class);
            startActivity(i);
            finish();
        }, 1100);
    }
}
