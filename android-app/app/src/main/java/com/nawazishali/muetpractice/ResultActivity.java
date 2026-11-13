package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        int score = getIntent().getIntExtra("score", 0);
        int total = getIntent().getIntExtra("total", 0);
        String review = getIntent().getStringExtra("review");
        int pct = total == 0 ? 0 : Math.round(100f * score / total);

        ((TextView) findViewById(R.id.tvPercent)).setText(pct + "%");
        TextView grade = findViewById(R.id.tvGrade);
        if (pct >= 80) grade.setText("Excellent!");
        else if (pct >= 50) grade.setText("Good Job!");
        else if (pct >= 35) grade.setText("Keep Practicing!");
        else grade.setText("Don't Give Up!");

        ((TextView) findViewById(R.id.tvCorrect)).setText("✔ " + score);
        ((TextView) findViewById(R.id.tvWrong)).setText("✗ " + (total - score));
        ((TextView) findViewById(R.id.tvScoreText)).setText(score + "/" + total);

        LinearLayout box = findViewById(R.id.layoutReview);
        if (review != null && !review.trim().isEmpty()) {
            for (String item : review.split("\n\n")) {
                TextView tv = new TextView(this);
                tv.setText(item);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(13);
                tv.setBackgroundResource(R.drawable.bg_card);
                tv.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(10);
                tv.setLayoutParams(lp);
                box.addView(tv);
            }
        }

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            Intent i = new Intent(this, HomeActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            finish();
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
