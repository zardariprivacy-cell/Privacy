package com.nawazishali.guftagu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LockActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
        EditText etPin = findViewById(R.id.etPin);
        findViewById(R.id.btnUnlock).setOnClickListener(v -> {
            if (AppPrefs.checkPin(this, etPin.getText().toString().trim())) {
                Intent i = new Intent(this, HomeActivity.class);
                i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                finish();
            } else {
                etPin.setText("");
                Toast.makeText(this, "PIN ghalat hai", Toast.LENGTH_SHORT).show();
            }
        });
        ((TextView) findViewById(R.id.tvForgot)).setOnClickListener(v -> {
            Session.clear(this);
            Intent i = new Intent(this, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }
}
