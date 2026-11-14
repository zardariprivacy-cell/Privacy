package com.nawazishali.guftagu;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StatusActivity extends Activity {

    private String authorUid, myUid;
    private TextView tvBody;
    private ImageView iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_status);
        authorUid = getIntent().getStringExtra("authorUid");
        String authorName = getIntent().getStringExtra("authorName");
        myUid = Session.uid(this);
        ((TextView) findViewById(R.id.tvStatusName)).setText(authorName == null ? "Status" : authorName);
        tvBody = findViewById(R.id.tvStatusBody);
        iv = findViewById(R.id.ivStatus);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        if (authorUid == null || myUid == null) {
            tvBody.setText("Status nahi mila");
            return;
        }
        load();
    }

    private void load() {
        Api.dbGet(this, "/status/" + authorUid, (st, err) -> {
            if (st == null) {
                tvBody.setText("Status khatam ho gaya hai");
                return;
            }
            long ts = st.optLong("ts", 0);
            if (ts <= 0 || System.currentTimeMillis() - ts >= 24L * 3600 * 1000) {
                tvBody.setText("Status khatam ho gaya hai (24 ghante guzar gaye)");
                return;
            }
            ((TextView) findViewById(R.id.tvStatusTime)).setText(
                    "Status • " + new SimpleDateFormat("HH:mm, dd MMM", Locale.getDefault()).format(new Date(ts)));
            Api.dbGet(this, "/users/" + authorUid, (prof, e2) -> {
                String pub = prof != null ? prof.optString("pub", "") : "";
                String payload = decryptStatus(st, pub);
                if (payload == null) {
                    tvBody.setText("🔒 Ye status tumhare liye nahi khulta\n(status lagne ke baad tum ne account banaya hoga)");
                    return;
                }
                showPayload(payload);
            });
        });
    }

    private String decryptStatus(JSONObject st, String authorPub) {
        try {
            JSONObject wraps = st.optJSONObject("wraps");
            if (wraps == null || !wraps.has(myUid)) return null;
            if (authorPub == null || authorPub.isEmpty()) {
                if (authorUid.equals(myUid)) authorPub = Crypto.myPubB64(this);
                else return null;
            }
            byte[] wrapKey = Crypto.groupWrapKey(Crypto.myPriv(this), Crypto.unb64(authorPub));
            JSONObject w = wraps.getJSONObject(myUid);
            byte[] statusKey = Crypto.aesGcmDecrypt(wrapKey, Crypto.unb64(w.getString("n")),
                    Crypto.unb64(w.getString("w")), "statuswrap".getBytes(StandardCharsets.UTF_8));
            byte[] pt = Crypto.aesGcmDecrypt(statusKey, Crypto.unb64(st.getString("n")),
                    Crypto.unb64(st.getString("c")), "guftagu-status".getBytes(StandardCharsets.UTF_8));
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private void showPayload(String payloadJson) {
        try {
            JSONObject p = new JSONObject(payloadJson);
            String t = p.optString("t", "");
            if (t.equals("text")) {
                tvBody.setText(p.optString("text", ""));
            } else if (t.equals("photo")) {
                tvBody.setText("📷 Photo load ho raha hai...");
                MediaHelper.fetchBlob(this, p.optString("b", ""), p.optString("k", ""), (file, err) -> {
                    if (file == null) {
                        tvBody.setText("Photo download nahi hua");
                        return;
                    }
                    Bitmap bmp = decodeSampled(file, 1400);
                    if (bmp == null) {
                        tvBody.setText("Photo khul nahi saka");
                        return;
                    }
                    tvBody.setVisibility(android.view.View.GONE);
                    iv.setVisibility(android.view.View.VISIBLE);
                    iv.setImageBitmap(bmp);
                });
            } else {
                tvBody.setText("Status ki samajh nahi ayi");
            }
        } catch (Exception e) {
            tvBody.setText("Status khul nahi saka");
        }
    }

    private Bitmap decodeSampled(File f, int targetW) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth / sample > targetW) sample *= 2;
            o.inSampleSize = sample;
            o.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Exception e) {
            return null;
        }
    }
}
