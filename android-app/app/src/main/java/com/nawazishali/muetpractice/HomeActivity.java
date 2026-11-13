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

    private Db db;
    private User user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Ads.attachBanner(this, findViewById(R.id.adContainer));
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
        View.OnClickListener toPerformance = v -> startActivity(new Intent(this, PerformanceActivity.class));
        findViewById(R.id.btnPerformance).setOnClickListener(toPerformance);
        findViewById(R.id.navPerformance).setOnClickListener(toPerformance);
        View.OnClickListener toLeaderboard = v -> startActivity(new Intent(this, LeaderboardActivity.class));
        findViewById(R.id.btnLeaderboard).setOnClickListener(toLeaderboard);
        findViewById(R.id.navLeaderboard).setOnClickListener(toLeaderboard);
        View.OnClickListener toProfile = v -> startActivity(new Intent(this, ProfileActivity.class));
        findViewById(R.id.btnProfile).setOnClickListener(toProfile);
        findViewById(R.id.navProfile).setOnClickListener(toProfile);

        TextView btnManage = findViewById(R.id.btnManage);
        if (user.admin) {
            btnManage.setVisibility(View.VISIBLE);
            btnManage.setOnClickListener(v -> startActivity(new Intent(this, ManageActivity.class)));
        }
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Session.clear(this);
            goLogin();
        });

        refreshHeader();
        buildSubjects();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (user != null) {
            User fresh = db.getUser(user.id);
            if (fresh != null) user = fresh;
            refreshHeader();
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

    private void buildSubjects() {
        LinearLayout box = findViewById(R.id.layoutSubjects);
        box.removeAllViews();
        for (String subject : Db.SUBJECTS) {
            int count = db.questionsFor(subject).size();
            int bg;
            switch (subject) {
                case "Physics": bg = R.drawable.bg_sub_physics; break;
                case "Chemistry": bg = R.drawable.bg_sub_chemistry; break;
                case "English": bg = R.drawable.bg_sub_english; break;
                default: bg = R.drawable.bg_sub_math; break;
            }

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(18), dp(16), dp(14), dp(16));
            card.setBackgroundResource(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            card.setLayoutParams(lp);

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
            sub.setText("Concepts • MCQs • Practice — " + count + " sawal");
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
