package com.nawazishali.muetpractice;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PerformanceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        Db db = new Db(this);
        long uid = Session.currentUserId(this);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("My Performance");
        TextView tvSummary = findViewById(R.id.tvSummary);
        LinearLayout container = findViewById(R.id.listContainer);

        List<ResultRow> results = db.resultsForUser(uid);
        if (results.isEmpty()) {
            tvSummary.setText("Abhi tak koi test nahi diya. Pehle ek practice ya mock test do!");
            return;
        }

        int totalScore = 0, totalQs = 0;
        for (ResultRow r : results) {
            totalScore += r.score;
            totalQs += r.total;
        }
        int accuracy = totalQs == 0 ? 0 : Math.round(totalScore * 100f / totalQs);
        int bestMock = db.bestScore(uid, "mock", null);
        StringBuilder sb = new StringBuilder();
        sb.append("Total attempts: ").append(results.size()).append("\n");
        sb.append("Overall accuracy: ").append(accuracy).append("%\n");
        if (bestMock >= 0) sb.append("Best Mock Test score: ").append(bestMock).append("/20\n");
        sb.append("\nBest score per subject (Practice):");
        for (String s : Db.SUBJECTS) {
            int b = db.bestScore(uid, "practice", s);
            sb.append("\n• ").append(s).append(": ").append(b < 0 ? "—" : b + "/" + db.questionsFor(s).size());
        }
        tvSummary.setText(sb.toString());

        SimpleDateFormat fmt = new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US);
        for (ResultRow r : results) {
            TextView row = new TextView(this);
            String what = r.mode.equals("mock") ? "Mock Test" : "Practice — " + r.subject;
            row.setText(what + "\n" + r.score + "/" + r.total + " (" + r.percent() + "%)  •  "
                    + fmt.format(new Date(r.dateMillis)));
            row.setTextSize(15);
            row.setTextColor(0xFFFFFFFF);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(8);
            row.setLayoutParams(lp);
            container.addView(row);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
