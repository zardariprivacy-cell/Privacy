package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SignupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        EditText etName = findViewById(R.id.etName);
        EditText etUsername = findViewById(R.id.etUsername);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignup = findViewById(R.id.btnSignup);
        Db db = new Db(this);

        btnSignup.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString();
            if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Saari fields bharo", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 4) {
                Toast.makeText(this, "Password kam az kam 4 characters ka ho", Toast.LENGTH_SHORT).show();
                return;
            }
            long id = db.createUser(name, username, password);
            if (id < 0) {
                Toast.makeText(this, "Ye username pehle se hai — koi aur choose karo", Toast.LENGTH_LONG).show();
                return;
            }
            Session.set(this, id);
            User u = db.getUser(id);
            if (u != null && u.admin) {
                Toast.makeText(this, "Account ban gaya — tum is app ke Admin ho!", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Account ban gaya!", Toast.LENGTH_SHORT).show();
            }
            Intent i = new Intent(this, HomeActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });
    }
}
