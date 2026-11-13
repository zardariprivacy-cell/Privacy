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
        tvCrash.setText(CrashSaver.read(this));
        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            CrashSaver.clear(this);
            long uid = Session.currentUserId(this);
            Intent i = new Intent(this, uid > 0 ? HomeActivity.class : LoginActivity.class);
            startActivity(i);
            finish();
        });
    }
}
