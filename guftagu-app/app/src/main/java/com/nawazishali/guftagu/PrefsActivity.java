package com.nawazishali.guftagu;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Base class for the WhatsApp-style settings sub-screens. */
public abstract class PrefsActivity extends Activity {

    protected LinearLayout list;

    protected abstract String title();
    protected abstract void build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prefs);
        ((TextView) findViewById(R.id.tvTitle)).setText(title());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        list = findViewById(R.id.prefsList);
        build();
    }

    protected int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
