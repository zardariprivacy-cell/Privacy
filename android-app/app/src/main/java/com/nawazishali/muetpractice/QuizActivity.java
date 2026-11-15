package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class QuizActivity extends Activity {

    private static final String[] LETTERS = {"A", "B", "C", "D"};

    private Db db;
    private boolean mock;
    private String subjectLabel;
    private String displayTitle;
    private List<Question> questions = new ArrayList<>();
    private int index = 0;
    private int score = 0;
    private int selected = -1;
    private boolean answered = false;
    private final List<String> reviewItems = new ArrayList<>();
    private CountDownTimer timer;
    private boolean fullMock;
    private long timerLeftMs = -1;
    private int savedSelected = -1;
    private boolean savedAnswered = false;
    private final List<Integer> perms = new ArrayList<>();
    private static final List<List<Integer>> PERMS = buildPerms();

    private static List<List<Integer>> buildPerms() {
        List<List<Integer>> all = new ArrayList<>();
        for (int a = 0; a < 4; a++) for (int b = 0; b < 4; b++) {
            if (b == a) continue;
            for (int c = 0; c < 4; c++) {
                if (c == a || c == b) continue;
                List<Integer> p = new ArrayList<>();
                p.add(a); p.add(b); p.add(c); p.add(6 - a - b - c);
                all.add(p);
            }
        }
        return all;
    }
    private boolean prefVibrate = true;
    private boolean prefExplain = true;

    private TextView tvTitle, tvTimer, tvQBadge, tvSubject, tvQuestion, tvExplanation, tvProgressBottom;
    private ProgressBar pbQuiz;
    private LinearLayout layoutOptions;
    private Button btnNext;
    private final List<Button> optionButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);
        android.content.SharedPreferences sp = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        prefVibrate = sp.getBoolean(SettingsActivity.KEY_VIBRATE, true);
        prefExplain = sp.getBoolean(SettingsActivity.KEY_EXPLAIN, true);
        db = new Db(this);
        if (Session.currentUserId(this) <= 0) {
            finish();
            return;
        }

        tvTitle = findViewById(R.id.tvQuizTitle);
        tvTimer = findViewById(R.id.tvTimer);
        tvQBadge = findViewById(R.id.tvQBadge);
        tvSubject = findViewById(R.id.tvSubject);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvExplanation = findViewById(R.id.tvExplanation);
        tvProgressBottom = findViewById(R.id.tvProgressBottom);
        pbQuiz = findViewById(R.id.pbQuiz);
        layoutOptions = findViewById(R.id.layoutOptions);
        btnNext = findViewById(R.id.btnNext);

        boolean restored = savedInstanceState != null && savedInstanceState.containsKey("q_ids")
                && restoreState(savedInstanceState);
        List<Question> raw = new ArrayList<>();
        if (!restored) {
            mock = "mock".equals(getIntent().getStringExtra("mode"));
            fullMock = getIntent().getBooleanExtra("full", false);
        }
        if (!restored && mock) {
            subjectLabel = fullMock ? "Full Mock Test" : "Mock Test";
            int perSubject = fullMock ? 25 : 5;
            for (String s : Db.SUBJECTS) {
                List<Question> pool = new ArrayList<>(db.questionsFor(s));
                Collections.shuffle(pool);
                raw.addAll(pool.subList(0, Math.min(perSubject, pool.size())));
            }
            Collections.shuffle(raw);
            tvTitle.setText(subjectLabel);
        } else if (!restored) {
            subjectLabel = getIntent().getStringExtra("subject");
            if (subjectLabel == null) subjectLabel = "Mathematics";
            String chapter = getIntent().getStringExtra("chapter");
            String topic = getIntent().getStringExtra("topic");
            if (topic != null) {
                raw = db.questionsForTopic(subjectLabel, chapter, topic);
                displayTitle = topic;
            } else {
                raw = db.questionsFor(subjectLabel);
                displayTitle = subjectLabel + " Practice";
            }
            tvTitle.setText(displayTitle);
        }
        // Shuffle the options of every question once, keeping track of the answer.
        if (!restored) {
            for (Question q : raw) {
                questions.add(shuffled(q));
            }
        }
        if (questions.isEmpty()) {
            Toast.makeText(this, "No questions are available in this subject yet", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnNext.setOnClickListener(v -> next());
        if (mock) {
            tvTimer.setVisibility(View.VISIBLE);
            btnNext.setVisibility(View.VISIBLE);
            startTimer(timerLeftMs > 0 ? timerLeftMs : (fullMock ? 100L : 20L) * 60L * 1000L);
        }
        showQuestion();
        if (restored) repaintRestored();
    }

    // ---- State save/restore: rotation or process death never loses an attempt ----
    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (questions.isEmpty()) return;
        out.putBoolean("mock", mock);
        out.putBoolean("full", fullMock);
        out.putString("subjectLabel", subjectLabel);
        out.putString("displayTitle", displayTitle);
        long[] ids = new long[questions.size()];
        int[] pm = new int[questions.size()];
        for (int i = 0; i < questions.size(); i++) {
            ids[i] = questions.get(i).id;
            pm[i] = perms.get(i);
        }
        out.putLongArray("q_ids", ids);
        out.putIntArray("q_perms", pm);
        out.putInt("index", index);
        out.putInt("score", score);
        out.putStringArrayList("review", new ArrayList<>(reviewItems));
        out.putInt("curSelected", selected);
        out.putBoolean("curAnswered", answered);
        out.putLong("timerLeft", timerLeftMs);
    }

    private boolean restoreState(Bundle b) {
        long[] ids = b.getLongArray("q_ids");
        int[] pm = b.getIntArray("q_perms");
        if (ids == null || pm == null || ids.length == 0 || ids.length != pm.length) return false;
        mock = b.getBoolean("mock");
        fullMock = b.getBoolean("full");
        subjectLabel = b.getString("subjectLabel");
        displayTitle = b.getString("displayTitle");
        for (int i = 0; i < ids.length; i++) {
            Question base = db.questionById(ids[i]);
            if (base == null) return false;
            questions.add(withOrder(base, PERMS.get(pm[i])));
            perms.add(pm[i]);
        }
        index = b.getInt("index");
        score = b.getInt("score");
        ArrayList<String> rv = b.getStringArrayList("review");
        if (rv != null) reviewItems.addAll(rv);
        savedSelected = b.getInt("curSelected", -1);
        savedAnswered = b.getBoolean("curAnswered", false);
        timerLeftMs = b.getLong("timerLeft", -1);
        tvTitle.setText(mock ? subjectLabel : displayTitle);
        return true;
    }

    private void repaintRestored() {
        if (mock) {
            if (savedSelected >= 0) {
                selected = savedSelected;
                for (int i = 0; i < optionButtons.size(); i++) {
                    optionButtons.get(i).setBackgroundResource(
                            i == selected ? R.drawable.bg_opt_selected : R.drawable.bg_opt);
                }
            }
        } else if (savedAnswered && savedSelected >= 0) {
            selected = savedSelected;
            answered = true;
            Question q = questions.get(index);
            for (int i = 0; i < optionButtons.size(); i++) {
                Button btn = optionButtons.get(i);
                btn.setEnabled(false);
                if (i == q.correctIndex) {
                    btn.setBackgroundResource(R.drawable.bg_correct);
                    btn.setTextColor(Color.WHITE);
                    btn.setText(optionText(i, q.options[i], "   \u2713"));
                } else if (i == savedSelected) {
                    btn.setBackgroundResource(R.drawable.bg_wrong);
                    btn.setTextColor(Color.WHITE);
                    btn.setText(optionText(i, q.options[i], "   \u2717"));
                }
            }
            if (prefExplain && q.explanation != null && !q.explanation.trim().isEmpty()) {
                tvExplanation.setText("\uD83D\uDCA1 " + q.explanation);
                tvExplanation.setVisibility(View.VISIBLE);
            }
            btnNext.setVisibility(View.VISIBLE);
        }
    }

    private Question shuffled(Question q) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < q.options.length; i++) order.add(i);
        Collections.shuffle(order);
        perms.add(PERMS.indexOf(order));
        return withOrder(q, order);
    }

    private Question withOrder(Question q, List<Integer> order) {
        Question c = new Question();
        c.id = q.id;
        c.subject = q.subject;
        c.question = q.question;
        c.explanation = q.explanation;
        c.options = new String[q.options.length];
        for (int i = 0; i < order.size(); i++) {
            c.options[i] = q.options[order.get(i)];
            if (order.get(i) == q.correctIndex) c.correctIndex = i;
        }
        return c;
    }

    private void startTimer(long durationMs) {
        timer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long ms) {
                timerLeftMs = ms;
                long s = ms / 1000;
                tvTimer.setText(String.format(Locale.US, "⏱ %02d:%02d", s / 60, s % 60));
            }

            @Override
            public void onFinish() {
                Toast.makeText(QuizActivity.this, "Time is up!", Toast.LENGTH_LONG).show();
                recordCurrent(false);
                finishQuiz();
            }
        }.start();
    }

    private SpannableString optionText(int i, String text) {
        return optionText(i, text, "");
    }

    private SpannableString optionText(int i, String text, String suffix) {
        String full = LETTERS[i] + "    " + text + suffix;
        SpannableString ss = new SpannableString(full);
        ss.setSpan(new ForegroundColorSpan(Color.rgb(21, 101, 192)), 0, 1, 0);
        ss.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), 0, 1, 0);
        return ss;
    }

    private void showQuestion() {
        Question q = questions.get(index);
        selected = -1;
        answered = false;
        tvQBadge.setText("Q." + (index + 1));
        tvSubject.setText(q.subject);
        tvQuestion.setText(q.question);
        tvExplanation.setVisibility(View.GONE);
        tvProgressBottom.setText((index + 1) + " / " + questions.size());
        pbQuiz.setProgress(Math.round(100f * index / questions.size()));
        btnNext.setText(index == questions.size() - 1 ? (mock ? "Finish" : "See Result  ›") : "Next  ›");
        if (!mock) btnNext.setVisibility(View.GONE);

        layoutOptions.removeAllViews();
        optionButtons.clear();
        for (int i = 0; i < q.options.length; i++) {
            final int oi = i;
            Button b = new Button(this);
            b.setText(optionText(i, q.options[i]));
            b.setTextColor(Color.rgb(18, 38, 74));
            b.setTextSize(15);
            b.setAllCaps(false);
            b.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
            b.setPadding(dp(16), dp(14), dp(16), dp(14));
            b.setBackgroundResource(R.drawable.bg_opt);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> onOptionPicked(oi));
            layoutOptions.addView(b);
            optionButtons.add(b);
        }
    }

    private void onOptionPicked(int oi) {
        Question q = questions.get(index);
        if (mock) {
            selected = oi;
            for (int i = 0; i < optionButtons.size(); i++) {
                optionButtons.get(i).setBackgroundResource(
                        i == oi ? R.drawable.bg_opt_selected : R.drawable.bg_opt);
            }
            return;
        }
        if (answered) return;
        answered = true;
        if (prefVibrate) {
            try {
                android.os.Vibrator vib = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (vib != null) vib.vibrate(35);
            } catch (Throwable ignored) { }
        }
        selected = oi;
        boolean right = oi == q.correctIndex;
        if (right) score++;
        for (int i = 0; i < optionButtons.size(); i++) {
            Button b = optionButtons.get(i);
            b.setEnabled(false);
            if (i == q.correctIndex) {
                b.setBackgroundResource(R.drawable.bg_correct);
                b.setTextColor(Color.WHITE);
                b.setText(optionText(i, q.options[i], "   ✓"));
            } else if (i == oi) {
                b.setBackgroundResource(R.drawable.bg_wrong);
                b.setTextColor(Color.WHITE);
                b.setText(optionText(i, q.options[i], "   ✗"));
            }
        }
        if (prefExplain && q.explanation != null && !q.explanation.trim().isEmpty()) {
            tvExplanation.setText("💡 " + q.explanation);
            tvExplanation.setVisibility(View.VISIBLE);
        }
        reviewItems.add(reviewText(q, oi));
        btnNext.setVisibility(View.VISIBLE);
    }

    private String reviewText(Question q, int picked) {
        String your = picked >= 0 ? q.options[picked] : "—";
        boolean right = picked == q.correctIndex;
        String s = "Q. " + q.question + "\nYour answer: " + your + (right ? "  ✓ Correct" : "  ✗ Wrong");
        if (!right) s += "\nCorrect answer: " + q.options[q.correctIndex];
        return s;
    }

    private void recordCurrent(boolean countUnanswered) {
        if (index >= questions.size()) return;
        Question q = questions.get(index);
        if (mock) {
            if (selected == q.correctIndex) score++;
            reviewItems.add(reviewText(q, selected));
        }
    }

    private void next() {
        if (mock) {
            recordCurrent(true);
        } else if (!answered) {
            return; // practice: must answer first
        }
        if (index >= questions.size() - 1) {
            finishQuiz();
        } else {
            index++;
            showQuestion();
        }
    }

    private void finishQuiz() {
        if (timer != null) timer.cancel();
        long uid = Session.currentUserId(this);
        db.addResult(uid, mock ? "mock" : "practice", subjectLabel, score, questions.size());
        if (mock) {
            User me = db.getUser(uid);
            ResultRow best = db.bestMock(uid);
            if (me != null && best != null) {
                OnlineBoard.pushBest(this, me.name, best.score, best.total, best.percent());
            }
        }
        Intent i = new Intent(this, ResultActivity.class);
        i.putExtra("score", score);
        i.putExtra("total", questions.size());
        i.putExtra("title", (mock ? subjectLabel : displayTitle) + " Result");
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < reviewItems.size(); k++) {
            if (k > 0) sb.append("\n\n");
            sb.append(reviewItems.get(k));
        }
        i.putExtra("review", sb.toString());
        startActivity(i);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (timer != null) timer.cancel();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
