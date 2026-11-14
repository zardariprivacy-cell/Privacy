package com.nawazishali.guftagu;

import android.app.AlertDialog;
import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class AccountActivity extends PrefsActivity {

    @Override
    protected String title() { return "Account"; }

    @Override
    protected void build() {
        PrefRows.header(this, list, "PASSWORD");
        final EditText et = new EditText(this);
        et.setHint("Naya password (kam az kam 6)");
        et.setSingleLine(true);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setPadding(dp(14), dp(12), dp(14), dp(12));
        et.setTextColor(0xFFFFFFFF);
        et.setHintTextColor(0xFF8696A0);
        list.addView(et);
        Button btn = new Button(this);
        btn.setText("Password Change Karo");
        btn.setTextColor(0xFFFFFFFF);
        btn.setBackgroundResource(R.drawable.bg_btn_teal);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        blp.topMargin = dp(10);
        btn.setLayoutParams(blp);
        btn.setOnClickListener(v -> {
            String np = et.getText().toString();
            if (np.length() < 6) {
                Toast.makeText(this, "Password kam az kam 6 characters ka ho", Toast.LENGTH_SHORT).show();
                return;
            }
            Api.changePassword(this, np, (ok, err) -> {
                if (err != null) Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                else {
                    et.setText("");
                    Toast.makeText(this, "Password change ho gaya ✅", Toast.LENGTH_SHORT).show();
                }
            });
        });
        list.addView(btn);

        PrefRows.header(this, list, "DANGER ZONE");
        PrefRows.note(this, list, "Account delete karne se tumhara account aur profile hamesha ke liye khatam ho jayega. Dusron ke phones par tumhari bheji hui chats un ke paas reh sakti hain — E2EE mein wapas bulana mumkin nahi hota.");
        PrefRows.textRow(this, list, "⛔ Account DELETE karo", "Hamesha ke liye",
                v -> new AlertDialog.Builder(this)
                        .setTitle("Account delete karein?")
                        .setMessage("Ye wapas nahi ho sakta. Pakka?")
                        .setNegativeButton("Nahi", null)
                        .setPositiveButton("DELETE", (d, w) -> confirmAgain())
                        .show());
    }

    private void confirmAgain() {
        new AlertDialog.Builder(this)
                .setTitle("Aakhri dafa pooch raha hoon")
                .setMessage("Tumhara Guftagu account abhi delete ho jayega.")
                .setNegativeButton("Ruk jao", null)
                .setPositiveButton("DELETE KARO", (d, w) -> doDelete())
                .show();
    }

    private void doDelete() {
        final String uid = Session.uid(this);
        Api.dbDelete(this, "/status/" + uid, (o1, e1) ->
                Api.dbDelete(this, "/users/" + uid, (o2, e2) ->
                        Api.deleteAccount(this, (ok, err) -> {
                            if (err != null) {
                                Toast.makeText(this, err, Toast.LENGTH_LONG).show();
                                return;
                            }
                            Session.clear(this);
                            Toast.makeText(this, "Account delete ho gaya", Toast.LENGTH_LONG).show();
                            Intent i = new Intent(this, MainActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        })));
    }
}
