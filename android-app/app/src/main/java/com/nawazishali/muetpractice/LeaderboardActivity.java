package com.nawazishali.muetpractice;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardActivity extends Activity {

    private static class Entry {
        User user;
        ResultRow best;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);
        Db db = new Db(this);

        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText("Leaderboard");
        TextView tvSummary = findViewById(R.id.tvSummary);
        tvSummary.setText("Ranked by best Mock Test score on this phone.");
        LinearLayout container = findViewById(R.id.listContainer);

        List<Entry> entries = new ArrayList<>();
        for (User u : db.allUsers()) {
            ResultRow best = db.bestMock(u.id);
            if (best != null) {
                Entry e = new Entry();
                e.user = u;
                e.best = best;
                entries.add(e);
            }
        }
        entries.sort((a, b) -> {
            int pa = a.best.percent(), pb = b.best.percent();
            if (pb != pa) return Integer.compare(pb, pa);
            return Integer.compare(b.best.score, a.best.score);
        });

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Abhi tak kisi ne Mock Test nahi diya. Pehle tum top par aao!");
            empty.setTextColor(0xFFA9BADF);
            empty.setTextSize(15);
            container.addView(empty);
            return;
        }

        int rank = 1;
        for (Entry e : entries) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER_VERTICAL);
            rowLayout.setBackgroundResource(R.drawable.bg_card);
            rowLayout.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.topMargin = dp(8);
            rowLayout.setLayoutParams(rowLp);

            TextView tvRank = new TextView(this);
            tvRank.setText("#" + rank);
            tvRank.setTextColor(0xFFF5B81D);
            tvRank.setTextSize(17);
            tvRank.setTypeface(null, android.graphics.Typeface.BOLD);
            tvRank.setMinWidth(dp(44));

            TextView tvInit = new TextView(this);
            tvInit.setGravity(Gravity.CENTER);
            tvInit.setTextSize(14);
            tvInit.setTextColor(0xFF231A00);
            tvInit.setBackgroundResource(R.drawable.bg_circle);
            LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(42), dp(42));
            avLp.leftMargin = dp(4);
            tvInit.setLayoutParams(avLp);

            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
            AvatarHelper.load(img, tvInit, e.user);
            // AvatarHelper toggles between the two views inside its own parent;
            // here add only the one that ended up visible.
            rowLayout.addView(tvRank);
            if (img.getVisibility() == android.view.View.VISIBLE) {
                LinearLayout.LayoutParams ilp = (LinearLayout.LayoutParams) img.getLayoutParams();
                ilp.leftMargin = dp(4);
                rowLayout.addView(img);
            } else {
                rowLayout.addView(tvInit);
            }

            TextView tvText = new TextView(this);
            tvText.setText(e.user.name
                    + "\nBest Mock: " + e.best.score + "/" + e.best.total + " (" + e.best.percent() + "%)");
            tvText.setTextSize(15);
            tvText.setTextColor(0xFFFFFFFF);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = dp(10);
            tvText.setLayoutParams(tlp);
            rowLayout.addView(tvText);

            container.addView(rowLayout);
            rank++;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
