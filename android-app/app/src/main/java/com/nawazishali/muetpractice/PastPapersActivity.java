package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * ECAT Past Papers: real UET Lahore entrance-test papers (PDF) hosted on
 * Google Drive. The list is fetched from a small JSON file on the app's
 * public page, so new papers can be added without an app update; if the
 * fetch fails, a built-in copy of the list is used. Buttons open the
 * paper in Google Drive, where it can be viewed and downloaded.
 */
public class PastPapersActivity extends Activity {

    private static final String LIST_URL =
            "https://zardariprivacy-cell.github.io/Privacy/ecat-papers.json";

    // Built-in copy of the list (same content as ecat-papers.json) for offline use.
    private static final String FALLBACK_JSON = "[{\"year\": \"2020\", \"title\": \"ECAT Past Paper 2020\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/1mFKd6rdzkoHlglDf8iOUtNEzBb81g2Je/view?usp=drivesdk\"}, {\"year\": \"2017\", \"title\": \"ECAT Past Paper 2017\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/1m2ojwuML1DXhrXBa6zMI5FkD-gArHjF5/view?usp=drivesdk\"}, {\"year\": \"2016\", \"title\": \"ECAT Past Paper 2016\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/1ZMz_67C3ECNniSVjkVBHX7_OOl8lWaIr/view?usp=drivesdk\"}, {\"year\": \"2015\", \"title\": \"ECAT Past Paper 2015\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/1XZGaC_PcaS4IW8R2L36udSkgXykW-DWY/view?usp=drivesdk\"}, {\"year\": \"2014\", \"title\": \"ECAT Past Paper 2014\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/1WnVzrdAA_cWxsiezjlZ9Y1bbKRDI1lRw/view?usp=drivesdk\"}, {\"year\": \"2012\", \"title\": \"ECAT Past Paper 2012\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/1cQKHzvMwqjTdik-djVM_O8y8_X3UwL3f/view?usp=drivesdk\"}, {\"year\": \"2011\", \"title\": \"ECAT Past Paper 2011\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/10SQZcaHOy4A6_n92OGc93r8NCow71dHF/view?usp=drivesdk\"}, {\"year\": \"2010\", \"title\": \"ECAT Past Paper 2010\", \"meta\": \"UET Lahore • Entrance Test • 100 MCQs • 100 Minutes\", \"url\": \"https://drive.google.com/file/d/1xp-fNoBoQ2ywTVa8yv8_4xT-e50Nos5c/view?usp=drivesdk\"}]";

    private LinearLayout container;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_papers);
        container = findViewById(R.id.papersContainer);
        tvStatus = findViewById(R.id.tvPapersStatus);

        // Show the built-in list immediately, then refresh from the server.
        render(FALLBACK_JSON);
        new Thread(() -> {
            String json = fetch(LIST_URL);
            if (json != null) {
                runOnUiThread(() -> render(json));
            }
        }).start();
    }

    private String fetch(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "UniTestPrep-App");
            if (conn.getResponseCode() != 200) return null;
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            String out = sb.toString().trim();
            return out.startsWith("[") ? out : null;
        } catch (Throwable t) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void render(String json) {
        try {
            JSONArray arr = new JSONArray(json);
            container.removeAllViews();
            if (arr.length() == 0) {
                tvStatus.setText("Papers jald arahe hain — thoda intezaar karo.");
                return;
            }
            tvStatus.setText("UET ECAT ke asal past papers. Paper kholo, download karo " +
                    "aur asal exam pattern ki practice karo — har paper mein 100 MCQs hain.");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.getJSONObject(i);
                container.addView(paperCard(
                        p.optString("title"),
                        p.optString("meta"),
                        p.optString("url")));
            }
        } catch (Throwable t) {
            tvStatus.setText("Papers list load nahi ho saki — internet check karke dobara kholo.");
        }
    }

    private LinearLayout paperCard(String title, String meta, final String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(34, 28, 34, 28);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = 22;
        card.setLayoutParams(cp);

        TextView t = new TextView(this);
        t.setText("📄  " + title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView s = new TextView(this);
        s.setText(meta + "  •  Free PDF");
        s.setTextColor(0xFFA9BADF);
        s.setTextSize(12.5f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = 6;
        card.addView(s, sp);

        Button btn = new Button(this);
        btn.setText("📄  Open / Download Paper");
        btn.setTextColor(0xFF231A00);
        btn.setTextSize(15);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setBackgroundResource(R.drawable.bg_btn_gold);
        btn.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 118);
        bp.topMargin = 18;
        card.addView(btn, bp);
        btn.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Throwable ignored) {
            }
        });
        return card;
    }
}
