package com.nawazishali.guftagu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Class<?> next;
        if (!Session.isLoggedIn(this)) next = LoginActivity.class;
        else if (AppPrefs.pinHash(this) != null) next = LockActivity.class;
        else next = HomeActivity.class;
        Intent i = new Intent(this, next);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
