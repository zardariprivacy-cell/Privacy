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
 * Books Library: free official Sindh Textbook Board (STBB) textbooks for
 * Class XI and XII. The book list is fetched from a small JSON file hosted
 * on the app's public page, so new books can be added without an app
 * update; if the fetch fails, a built-in copy of the list is used.
 * Download buttons open the official STBB PDF link.
 */
public class BooksActivity extends Activity {

    private static final String LIST_URL =
            "https://zardariprivacy-cell.github.io/Privacy/books.json";

    // Built-in copy of the list (same content as books.json) for offline use.
    private static final String FALLBACK_JSON = "[{\"class\": \"Class XI — 1st Year\", \"subject\": \"Physics\", \"title\": \"Physics — Class XI\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=221&download=1\"}, {\"class\": \"Class XI — 1st Year\", \"subject\": \"Chemistry\", \"title\": \"Chemistry — Class XI\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=206&download=1\"}, {\"class\": \"Class XI — 1st Year\", \"subject\": \"Mathematics\", \"title\": \"Mathematics — Class XI\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=207&download=1\"}, {\"class\": \"Class XI — 1st Year\", \"subject\": \"Biology\", \"title\": \"Biology — Class XI\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=219&download=1\"}, {\"class\": \"Class XI — 1st Year\", \"subject\": \"English\", \"title\": \"English — Class XI\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=203&download=1\"}, {\"class\": \"Class XI — 1st Year\", \"subject\": \"Computer Science\", \"title\": \"Computer Science — Class XI\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=324&download=1\"}, {\"class\": \"Class XII — 2nd Year\", \"subject\": \"Physics\", \"title\": \"Physics — Class XII\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=215&download=1\"}, {\"class\": \"Class XII — 2nd Year\", \"subject\": \"Chemistry\", \"title\": \"Chemistry — Class XII\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=218&download=1\"}, {\"class\": \"Class XII — 2nd Year\", \"subject\": \"Mathematics\", \"title\": \"Mathematics — Class XII\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=225&download=1\"}, {\"class\": \"Class XII — 2nd Year\", \"subject\": \"Biology\", \"title\": \"Biology — Class XII\", \"url\": \"https://portal.stbb.edu.pk/ebooks/pdf_proxy.php?id=228&download=1\"}]";

    private LinearLayout container;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_books);
        container = findViewById(R.id.booksContainer);
        tvStatus = findViewById(R.id.tvBooksStatus);

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
                tvStatus.setText("Books jald arahi hain — thoda intezaar karo.");
                return;
            }
            tvStatus.setText("Official Sindh Textbook Board (STBB) ki free kitabein. " +
                    "Download karke offline parho. Note: ye kitabein bari files hain " +
                    "(kuch 200 MB se bhi zyada) — WiFi par download karna behtar hai.");
            String lastClass = "";
            for (int i = 0; i < arr.length(); i++) {
                JSONObject b = arr.getJSONObject(i);
                String cls = b.optString("class");
                String title = b.optString("title");
                String subject = b.optString("subject");
                String url = b.optString("url");
                if (!cls.equals(lastClass)) {
                    lastClass = cls;
                    TextView h = new TextView(this);
                    h.setText(cls);
                    h.setTextColor(0xFFF5B81D);
                    h.setTextSize(17);
                    h.setTypeface(Typeface.DEFAULT_BOLD);
                    LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    hp.topMargin = i == 0 ? 0 : 42;
                    hp.bottomMargin = 14;
                    container.addView(h, hp);
                }
                container.addView(bookCard(title, subject, url));
            }
        } catch (Throwable t) {
            tvStatus.setText("Books list load nahi ho saki — internet check karke dobara kholo.");
        }
    }

    private LinearLayout bookCard(String title, String subject, final String url) {
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
        t.setText("📖  " + title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(t);

        TextView s = new TextView(this);
        s.setText(subject + "  •  STBB  •  English Medium  •  Free PDF");
        s.setTextColor(0xFFA9BADF);
        s.setTextSize(12.5f);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = 6;
        card.addView(s, sp);

        Button btn = new Button(this);
        btn.setText("⬇  Download PDF");
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
