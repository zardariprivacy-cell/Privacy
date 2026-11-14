package com.nawazishali.guftagu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class SignupActivity extends Activity {

    private EditText etName, etUsername, etPassword;
    private TextView tvStatus;
    private Button btnSignup;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        etName = findViewById(R.id.etName);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvStatus = findViewById(R.id.tvSignupStatus);
        btnSignup = findViewById(R.id.btnSignup);

        findViewById(R.id.tvGoLogin).setOnClickListener(v -> finish());

        btnSignup.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String username = etUsername.getText().toString().trim().toLowerCase();
            String password = etPassword.getText().toString();
            if (name.isEmpty()) { tvStatus.setText("Apna naam likho"); return; }
            if (!username.matches("[a-z0-9_]{3,20}")) {
                tvStatus.setText("Username 3-20 characters — sirf chhoti letters, numbers ya _");
                return;
            }
            if (password.length() < 6) { tvStatus.setText("Password kam az kam 6 characters ka ho"); return; }
            btnSignup.setEnabled(false);
            tvStatus.setText("Account ban raha hai...");
            Api.signUp(this, username, password, name, (uid, err) -> {
                if (err != null) {
                    btnSignup.setEnabled(true);
                    tvStatus.setText(err);
                    return;
                }
                try {
                    JSONObject p = new JSONObject();
                    p.put("name", name);
                    p.put("pub", Crypto.myPubB64(this));
                    Api.dbPut(this, "/users/" + uid, p, (ok, e2) -> {
                        Toast.makeText(this, "Account ban gaya — khush aamdeed!", Toast.LENGTH_LONG).show();
                        Intent i = new Intent(this, HomeActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    });
                } catch (Exception e) {
                    btnSignup.setEnabled(true);
                    tvStatus.setText("Keys banane mein masla — dobara try karo");
                }
            });
        });
    }
}
