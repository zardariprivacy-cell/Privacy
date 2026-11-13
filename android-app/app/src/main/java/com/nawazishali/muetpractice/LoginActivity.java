package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends Activity {

    private Db db;
    private EditText etUsername;
    private EditText etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = new Db(this);
        try {
            setContentView(R.layout.activity_login);
            etUsername = findViewById(R.id.etUsername);
            etPassword = findViewById(R.id.etPassword);
            Button btnLogin = findViewById(R.id.btnLogin);
            TextView tvGoSignup = findViewById(R.id.tvGoSignup);
            btnLogin.setOnClickListener(v -> doLogin());
            tvGoSignup.setOnClickListener(v ->
                    startActivity(new Intent(this, SignupActivity.class)));
        } catch (Throwable t) {
            // The designed screen failed on this phone — record why and fall
            // back to a plain built-in login screen so the app still works.
            CrashSaver.save(this, t);
            buildFallback();
        }
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Username and password likho", Toast.LENGTH_SHORT).show();
            return;
        }
        User u = db.login(username, password);
        if (u == null) {
            Toast.makeText(this, "Wrong username or password", Toast.LENGTH_LONG).show();
            return;
        }
        Session.set(this, u.id);
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    private void buildFallback() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("UniTest Prep — Login");
        title.setTextSize(20);
        title.setTextColor(0xFF0D1B3E);
        root.addView(title);

        etUsername = new EditText(this);
        etUsername.setHint("Username");
        root.addView(etUsername);

        etPassword = new EditText(this);
        etPassword.setHint("Password");
        etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(etPassword);

        Button btn = new Button(this);
        btn.setText("Login");
        btn.setOnClickListener(v -> doLogin());
        root.addView(btn);

        TextView link = new TextView(this);
        link.setText("No account? Create one — Signup");
        link.setTextColor(0xFF0D1B3E);
        link.setPadding(0, pad / 2, 0, 0);
        link.setOnClickListener(v -> startActivity(new Intent(this, SignupActivity.class)));
        root.addView(link);

        TextView note = new TextView(this);
        note.setText("(Simple screen — main design is being fixed)");
        note.setTextColor(Color.GRAY);
        root.addView(note);

        setContentView(root);
    }
}
