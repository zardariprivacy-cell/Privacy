package com.nawazishali.muetpractice;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * AI Study Assistant powered by the Gemini API (free tier).
 * Students can ask study questions in English or Roman Urdu and get
 * simple, teacher-style answers. The API key is kept in two parts so
 * it does not sit in the APK as one plain string.
 */
public class StudyAssistantActivity extends Activity {

    private static final String KEY_P1 = "AQ.Ab8RN6Ihu9GP0LZmNpZHXS";
    private static final String KEY_P2 = "QotmfnxEzpkRXzsh596H0Z20xWjw";
    private static final String MODEL = "gemini-3.5-flash";
    private static final String MODEL_FALLBACK = "gemini-3.5-flash-lite";

    private static String endpointFor(String model) {
        return "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
    }

    private static final String SYSTEM_PROMPT =
            "You are the Ilmora Study Assistant for Pakistani students preparing for entry tests " +
            "(MUET, ECAT), job tests (NTS, PTS, OTS) and English learning. Rules: " +
            "1) Answer in simple, clear English. " +
            "2) Keep answers short and focused, under 200 words, unless a full step-by-step solution is needed. " +
            "3) For maths or science problems, show clear step-by-step working. " +
            "4) For MCQs, give the correct answer first, then a one-line reason. " +
            "5) Use simple examples a student can relate to. " +
            "6) If the student writes in Roman Urdu, reply in Roman Urdu mixed with English. " +
            "7) Be friendly and encouraging, like a good teacher.";

    private ScrollView scrollChat;
    private LinearLayout chatBox;
    private EditText etQuestion;
    private Button btnSend;
    private final List<String[]> history = new ArrayList<>(); // {role, text}
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_assistant);
        if (Session.currentUserId(this) <= 0) {
            finish();
            return;
        }
        scrollChat = findViewById(R.id.scrollChat);
        chatBox = findViewById(R.id.chatBox);
        etQuestion = findViewById(R.id.etQuestion);
        btnSend = findViewById(R.id.btnSend);
        btnSend.setOnClickListener(v -> send());
        if (apiKey().isEmpty()) {
            addBubble("The Study Assistant is not activated yet. Please ask the app owner to switch it on.", false);
            etQuestion.setEnabled(false);
            btnSend.setEnabled(false);
        } else {
            addBubble("Assalam-o-Alaikum! I am your AI Study Assistant. Ask me anything — Math, Physics, " +
                    "Chemistry, English, GK and more.\n\nTry: \"What is Newton's second law?\" or \"Solve: 2x + 5 = 15\"", false);
        }
    }

    private String apiKey() {
        return KEY_P1 + KEY_P2;
    }

    private void send() {
        if (busy) return;
        String q = etQuestion.getText().toString().trim();
        if (q.isEmpty()) return;
        etQuestion.setText("");
        addBubble(q, true);
        history.add(new String[]{"user", q});
        busy = true;
        btnSend.setEnabled(false);
        final TextView thinking = addBubble("Thinking...", false);
        new Thread(() -> {
            String reply;
            try {
                reply = callGeminiModel(MODEL);
                if (reply == null || "__BUSY__".equals(reply)) {
                    String fb = callGeminiModel(MODEL_FALLBACK);
                    if (fb != null) reply = fb;
                }
            } catch (Exception e) {
                reply = null;
            }
            final String answer = reply;
            runOnUiThread(() -> {
                busy = false;
                btnSend.setEnabled(true);
                if ("__BUSY__".equals(answer)) {
                    thinking.setText("I am a little busy right now (the free daily limit). Please try again in a short while.");
                    removeLastUserTurn();
                } else if (answer != null && !answer.isEmpty()) {
                    thinking.setText(clean(answer));
                    history.add(new String[]{"model", answer});
                    trimHistory();
                } else {
                    thinking.setText("Sorry, I could not answer right now. Please check your internet connection and try again.");
                    removeLastUserTurn();
                }
                scrollDown();
            });
        }).start();
    }

    private void removeLastUserTurn() {
        if (!history.isEmpty() && "user".equals(history.get(history.size() - 1)[0])) {
            history.remove(history.size() - 1);
        }
    }

    private void trimHistory() {
        while (history.size() > 20) history.remove(0);
        while (!history.isEmpty() && "model".equals(history.get(0)[0])) history.remove(0);
    }

    private String callGeminiModel(String model) throws Exception {
        JSONObject body = new JSONObject();
        JSONObject sys = new JSONObject();
        JSONArray sysParts = new JSONArray();
        sysParts.put(new JSONObject().put("text", SYSTEM_PROMPT));
        sys.put("parts", sysParts);
        body.put("system_instruction", sys);

        JSONArray contents = new JSONArray();
        for (String[] m : history) {
            JSONObject c = new JSONObject();
            c.put("role", m[0]);
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", m[1]));
            c.put("parts", parts);
            contents.put(c);
        }
        body.put("contents", contents);

        JSONObject gen = new JSONObject();
        gen.put("maxOutputTokens", 1024);
        gen.put("temperature", 0.4);
        body.put("generationConfig", gen);

        HttpURLConnection conn = (HttpURLConnection) new URL(endpointFor(model)).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("x-goog-api-key", apiKey());
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
        }
        if (code == 429) return "__BUSY__";
        if (code < 200 || code >= 300) return null;

        JSONObject resp = new JSONObject(sb.toString());
        JSONArray cands = resp.optJSONArray("candidates");
        if (cands == null || cands.length() == 0) return null;
        JSONObject content = cands.getJSONObject(0).optJSONObject("content");
        if (content == null) return null;
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) return null;
        return parts.getJSONObject(0).optString("text", "");
    }

    private TextView addBubble(String text, boolean user) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(user ? Color.WHITE : Color.rgb(18, 38, 74));
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        tv.setBackgroundResource(user ? R.drawable.bg_chat_user : R.drawable.bg_chat_ai);
        tv.setMaxWidth(Math.round(getResources().getDisplayMetrics().widthPixels * 0.82f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.gravity = user ? Gravity.END : Gravity.START;
        tv.setLayoutParams(lp);
        chatBox.addView(tv);
        scrollDown();
        return tv;
    }

    private void scrollDown() {
        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }

    /** Gemini replies in markdown; plain TextView cannot render it, so tidy it up. */
    private String clean(String t) {
        String out = t.replace("**", "").replace("__", "").replace("$$", "").replace("$", "");
        out = out.replaceAll("(?m)^---+\\s*$", "");
        out = out.replaceAll("(?m)^#{1,6}\\s*", "");
        out = out.replaceAll("(?m)^\\*\\s+", "• ");
        out = out.replaceAll("(?m)^-\\s+", "• ");
        out = out.replaceAll("\\n{3,}", "\n\n");
        return out.trim();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
