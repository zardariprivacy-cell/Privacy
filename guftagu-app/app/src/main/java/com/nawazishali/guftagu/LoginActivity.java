package com.nawazishali.guftagu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends Activity {

    private EditText etUsername, etPassword;
    private TextView tvStatus;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvStatus = findViewById(R.id.tvLoginStatus);
        btnLogin = findViewById(R.id.btnLogin);

        findViewById(R.id.tvGoSignup).setOnClickListener(v ->
                startActivity(new Intent(this, SignupActivity.class)));

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim().toLowerCase();
            String password = etPassword.getText().toString();
            if (username.isEmpty() || password.isEmpty()) {
                tvStatus.setText("Username aur password dono likho");
                return;
            }
            btnLogin.setEnabled(false);
            tvStatus.setText("Login ho raha hai...");
            Api.signIn(this, username, password, (uid, err) -> {
                btnLogin.setEnabled(true);
                if (err != null) {
                    tvStatus.setText(err);
                    return;
                }
                checkBanAndEnter(uid);
            });
        });
    }

    private void checkBanAndEnter(String uid) {
        Api.dbGet(this, "/bans", (bans, err) -> {
            if (bans != null && bans.has(uid)) {
                Session.clear(this);
                tvStatus.setText("⛔ Ye account owner ne ban kiya hai");
                return;
            }
            syncProfileAndEnter(uid);
        });
    }

    private void syncProfileAndEnter(String uid) {
        Api.dbGet(this, "/users/" + uid, (profile, err) -> {
            try {
                String myPub = Crypto.myPubB64(this);
                String name = profile != null ? profile.optString("name", "") : "";
                if (name.isEmpty()) name = Session.username(this);
                Session.saveName(this, name);
                String pub = profile != null ? profile.optString("pub", "") : "";
                if (profile == null || !myPub.equals(pub)) {
                    JSONObject p = new JSONObject();
                    p.put("name", name);
                    p.put("pub", myPub);
                    Api.dbPut(this, "/users/" + uid, p, (ok, e2) -> goHome());
                } else {
                    goHome();
                }
            } catch (Exception e) {
                goHome();
            }
        });
    }

    private void goHome() {
        Toast.makeText(this, "Khush aamdeed!", Toast.LENGTH_SHORT).show();
        Intent i = new Intent(this, HomeActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
