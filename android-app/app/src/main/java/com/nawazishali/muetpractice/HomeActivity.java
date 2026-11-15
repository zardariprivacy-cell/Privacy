package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;

public class HomeActivity extends Activity {

    private static final String[] QUOTES = {
            "Dream Big, Prepare Better!",
            "Small steps every day beat big plans someday.",
            "Your future is created by what you do today.",
            "Success loves preparation.",
            "Hard work beats talent when talent sleeps.",
            "Every expert was once a beginner.",
            "Discipline today, pride tomorrow.",
            "Study now, shine later.",
            "Believe you can, and you are halfway there.",
            "One MCQ at a time - keep going!",
            "Winners practice until they cannot get it wrong.",
            "Your only limit is your effort.",
            "Make your parents proud.",
            "Knowledge is the best investment.",
            "Slow progress is still progress.",
            "Focus on the goal, not the fear.",
            "Today's practice is tomorrow's rank.",
            "Great marks start with a single page.",
            "Stay curious, keep learning.",
            "Effort never goes to waste.",
            "You are stronger than your excuses.",
            "Dream it. Learn it. Achieve it.",
            "Consistency is the secret of toppers.",
            "Every day is a new chance to improve.",
            "Push yourself - no one else will.",
            "Learning never exhausts the mind.",
            "Success is a journey of daily practice.",
            "Be patient: results take time.",
            "Your dreams need your hard work.",
            "Start where you are, use what you have."
    };

    private Db db;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Ads.attachBanner(this, findViewById(R.id.adContainer));
        final android.widget.TextView tvAdStatus = findViewById(R.id.tvAdStatus);
        // Ad diagnostics are owner-only: students never see technical status text.
        if (OwnerMode.isUnlocked(this)) {
            tvAdStatus.setVisibility(View.VISIBLE);
            final android.os.Handler adHandler = new android.os.Handler();
            final int[] adTicks = {0};
            adHandler.post(new Runnable() {
                @Override
                public void run() {
                    tvAdStatus.setText("Ad status: " + Ads.sdkStatus + "  •  Banner: " + Ads.bannerStatus);
                    if (++adTicks[0] < 20) adHandler.postDelayed(this, 1000);
                }
            });
        } else {
            tvAdStatus.setVisibility(View.GONE);
        }
        db = new Db(this);
        user = db.getUser(Session.currentUserId(this));
        if (user == null) {
            goLogin();
            return;
        }

        findViewById(R.id.btnMock).setOnClickListener(v -> {
            Intent i = new Intent(this, QuizActivity.class);
            i.putExtra("mode", "mock");
            startActivity(i);
        });
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnBooks).setOnClickListener(v -> startActivity(new Intent(this, BooksActivity.class)));
        findViewById(R.id.btnPapers).setOnClickListener(v -> startActivity(new Intent(this, PastPapersActivity.class)));
        findViewById(R.id.btnAssistant).setOnClickListener(v -> startActivity(new Intent(this, StudyAssistantActivity.class)));
        View.OnClickListener toPerformance = v -> startActivity(new Intent(this, PerformanceActivity.class));
        findViewById(R.id.btnPerformance).setOnClickListener(toPerformance);
        findViewById(R.id.navPerformance).setOnClickListener(toPerformance);
        View.OnClickListener toLeaderboard = v -> startActivity(new Intent(this, LeaderboardActivity.class));
        findViewById(R.id.btnLeaderboard).setOnClickListener(toLeaderboard);
        findViewById(R.id.navLeaderboard).setOnClickListener(toLeaderboard);
        View.OnClickListener toProfile = v -> startActivity(new Intent(this, ProfileActivity.class));
        findViewById(R.id.btnProfile).setOnClickListener(toProfile);
        findViewById(R.id.navProfile).setOnClickListener(toProfile);

        refreshManageButton();
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Session.clear(this);
            goLogin();
        });

        refreshHeader();
        buildSections();

        // Quote of the day: a fresh motivational quote every day, never the same two days in a row.
        TextView tvQuote = findViewById(R.id.tvQuote);
        int day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
        tvQuote.setText("\u201C" + QUOTES[day % QUOTES.length] + "\u201D");
    }

    private void refreshManageButton() {
        TextView btnManage = findViewById(R.id.btnManage);
        if (OwnerMode.isUnlocked(this)) {
            btnManage.setVisibility(View.VISIBLE);
            btnManage.setOnClickListener(v -> startActivity(new Intent(this, ManageActivity.class)));
        } else {
            btnManage.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (user != null) {
            User fresh = db.getUser(user.id);
            if (fresh != null) user = fresh;
            refreshHeader();
            refreshManageButton();
        }
    }

    private void refreshHeader() {
        ((TextView) findViewById(R.id.tvUserName)).setText(user.name);
        AvatarHelper.load((ImageView) findViewById(R.id.imgAvatar),
                (TextView) findViewById(R.id.tvInitials), user);
        List<ResultRow> results = db.resultsForUser(user.id);
        int totalQs = 0, totalScore = 0;
        for (ResultRow r : results) {
            totalQs += r.total;
            totalScore += r.score;
        }
        int pct = totalQs == 0 ? 0 : Math.round(100f * totalScore / totalQs);
        ((ProgressBar) findViewById(R.id.pbProgress)).setProgress(pct);
        ((TextView) findViewById(R.id.tvProgressPct)).setText(pct + "%");
        if (totalQs > 0) {
            ((TextView) findViewById(R.id.tvProgressNote)).setText(
                    "Practice accuracy — " + totalScore + " correct out of " + totalQs + " answered");
        }
    }

    private void buildSections() {
        LinearLayout box = findViewById(R.id.layoutSubjects);
        box.removeAllViews();
        addSectionHeader(box, "Entry Test Preparation", "University entry tests — MUET • ECAT • MDCAT style MCQs", true);
        for (String subject : Db.SUBJECTS) addSubjectCard(box, subject);
        addSubjectCard(box, "Biology");
        addFullMockCard(box);
        addSectionHeader(box, "Job Preparation", "NTS • PTS • OTS & government job test preparation", false);
        for (String subject : Db.JOB_SUBJECTS) addSubjectCard(box, subject);
        addSectionHeader(box, "English Language Learning", "Grammar & vocabulary — easy lessons and practice", false);
        for (String subject : Db.ENG_SUBJECTS) addSubjectCard(box, subject);
        addLessonsCard(box);
        addSectionHeader(box, "Matric \u2014 Class 9 & 10", "Sindh Board \u2022 chapter-wise MCQs, topic by topic", false);
        addSubHeader(box, "Class 9");
        for (String subject : Db.MATRIC9_SUBJECTS) addSubjectCard(box, subject);
        addSubHeader(box, "Class 10");
        for (String subject : Db.MATRIC10_SUBJECTS) addSubjectCard(box, subject);
    }

    private void addSubHeader(LinearLayout box, String title) {
        TextView h = new TextView(this);
        h.setText(title);
        h.setTextColor(Color.rgb(21, 101, 192));
        h.setTextSize(17);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(18);
        lp.leftMargin = dp(4);
        h.setLayoutParams(lp);
        box.addView(h);
    }

    private void addSectionHeader(LinearLayout box, String title, String sub, boolean first) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(first ? 4 : 22);
        wrap.setLayoutParams(lp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.rgb(18, 38, 74));
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        wrap.addView(t);
        TextView st = new TextView(this);
        st.setText(sub);
        st.setTextColor(Color.rgb(90, 107, 140));
        st.setTextSize(12);
        wrap.addView(st);
        box.addView(wrap);
    }

    private int subjectBg(String subject) {
        switch (subject) {
            case "Physics": return R.drawable.bg_sub_physics;
            case "Chemistry": return R.drawable.bg_sub_chemistry;
            case "English": return R.drawable.bg_sub_english;
            case "GK & Pakistan Studies": return R.drawable.bg_sub_gk;
            case "Islamic Studies": return R.drawable.bg_sub_islam;
            case "Everyday Science": return R.drawable.bg_sub_science;
            case "Job Mathematics": return R.drawable.bg_sub_math;
            case "Computer Basics": return R.drawable.bg_sub_comp;
            case "Job English": return R.drawable.bg_sub_english;
            case "English Grammar": return R.drawable.bg_sub_grammar;
            case "English Vocabulary": return R.drawable.bg_sub_vocab;
            case "Biology": return R.drawable.bg_sub_biology;
            default:
                if (subject.contains("Physics")) return R.drawable.bg_sub_physics;
                if (subject.contains("Chemistry")) return R.drawable.bg_sub_chemistry;
                if (subject.contains("Biology")) return R.drawable.bg_sub_biology;
                if (subject.contains("English")) return R.drawable.bg_sub_english;
                return R.drawable.bg_sub_math;
        }
    }

    private int iconFor(String subject) {
        switch (subject) {
            case "Mathematics": return R.drawable.ic_math;
            case "Physics": return R.drawable.ic_physics;
            case "Chemistry": return R.drawable.ic_chemistry;
            case "English": return R.drawable.ic_english;
            case "GK & Pakistan Studies": return R.drawable.ic_gk;
            case "Islamic Studies": return R.drawable.ic_islamic;
            case "Everyday Science": return R.drawable.ic_science;
            case "Job Mathematics": return R.drawable.ic_jobmath;
            case "Job English": return R.drawable.ic_jobeng;
            case "Computer Basics": return R.drawable.ic_computer;
            case "English Grammar": return R.drawable.ic_grammar;
            case "English Vocabulary": return R.drawable.ic_vocab;
            case "Biology": return R.drawable.ic_biology;
            default:
                if (subject.contains("Physics")) return R.drawable.ic_physics;
                if (subject.contains("Chemistry")) return R.drawable.ic_chemistry;
                if (subject.contains("Mathematics")) return R.drawable.ic_math;
                if (subject.contains("Biology")) return R.drawable.ic_biology;
                if (subject.contains("English")) return R.drawable.ic_english;
                return R.drawable.ic_books;
        }
    }

    private void addIconChip(LinearLayout card, int iconRes) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setBackgroundResource(R.drawable.bg_icon_chip);
        icon.setPadding(dp(11), dp(11), dp(11), dp(11));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(52), dp(52));
        lp.rightMargin = dp(14);
        icon.setLayoutParams(lp);
        card.addView(icon);
    }

    private void addSubjectCard(LinearLayout box, String subject) {
        int count = db.questionsFor(subject).size();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(14), dp(16));
        card.setBackgroundResource(subjectBg(subject));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        addIconChip(card, iconFor(subject));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(subject);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(name);

        TextView sub = new TextView(this);
        sub.setText("Concepts • MCQs • Practice — " + count + " questions");
        sub.setTextColor(Color.rgb(215, 227, 255));
        sub.setTextSize(12);
        texts.addView(sub);
        card.addView(texts);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(28);
        card.addView(arrow);

        card.setOnClickListener(v -> {
            Intent i = new Intent(this, TopicListActivity.class);
            i.putExtra("subject", subject);
            startActivity(i);
        });
        box.addView(card);
    }

    private void addFullMockCard(LinearLayout box) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(14), dp(16));
        card.setBackgroundResource(R.drawable.bg_sub_physics);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);
        addIconChip(card, R.drawable.ic_mock);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText("Full Mock Test");
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(name);
        TextView sub = new TextView(this);
        sub.setText("100 MCQs \u2022 100 Minutes \u2022 Real Exam Style");
        sub.setTextColor(Color.rgb(215, 227, 255));
        sub.setTextSize(12);
        texts.addView(sub);
        card.addView(texts);
        TextView arrow = new TextView(this);
        arrow.setText("\u203A");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(28);
        card.addView(arrow);
        card.setOnClickListener(v -> {
            Intent i = new Intent(this, QuizActivity.class);
            i.putExtra("mode", "mock");
            i.putExtra("full", true);
            startActivity(i);
        });
        box.addView(card);
    }

    private void addLessonsCard(LinearLayout box) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(14), dp(16));
        card.setBackgroundResource(R.drawable.bg_sub_grammar);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);
        addIconChip(card, R.drawable.ic_lessons);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView name = new TextView(this);
        name.setText("English Lessons");
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(name);
        TextView sub = new TextView(this);
        sub.setText("Tenses • Articles • Prepositions • 50 Common Words");
        sub.setTextColor(Color.rgb(215, 227, 255));
        sub.setTextSize(12);
        texts.addView(sub);
        card.addView(texts);
        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(28);
        card.addView(arrow);
        card.setOnClickListener(v -> startActivity(new Intent(this, LessonsActivity.class)));
        box.addView(card);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void goLogin() {
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
