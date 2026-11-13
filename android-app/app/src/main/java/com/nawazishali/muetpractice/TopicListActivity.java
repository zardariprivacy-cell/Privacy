package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Shows the chapters and topics of one subject; tapping a topic starts a
 *  practice quiz with only that topic's questions. */
public class TopicListActivity extends Activity {

    private Db db;
    private String subject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_topics);
        db = new Db(this);
        subject = getIntent().getStringExtra("subject");
        if (subject == null) subject = "Mathematics";

        ((TextView) findViewById(R.id.tvSubjectTitle)).setText(subject);
        ((TextView) findViewById(R.id.tvSubjectSub)).setText("Chapter chuno, phir topic — sirf usi topic ke MCQs aayenge");

        LinearLayout box = findViewById(R.id.layoutTopics);

        int total = db.questionsFor(subject).size();
        box.addView(rowCard("Poora Subject — Mixed Practice",
                total + " sawal • saare chapters mile hue", true,
                v -> startQuiz(null, null)));

        String lastChapter = null;
        List<TopicInfo> topics = db.topicsFor(subject);
        for (TopicInfo t : topics) {
            if (lastChapter == null || !lastChapter.equals(t.chapter)) {
                lastChapter = t.chapter;
                TextView header = new TextView(this);
                header.setText(t.chapter);
                header.setTextColor(Color.rgb(245, 184, 29));
                header.setTextSize(16);
                header.setTypeface(Typeface.DEFAULT_BOLD);
                LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hlp.topMargin = dp(20);
                header.setLayoutParams(hlp);
                box.addView(header);
            }
            final String chapter = t.chapter;
            final String topic = t.topic;
            box.addView(rowCard(topic, t.count + " MCQs", false,
                    v -> startQuiz(chapter, topic)));
        }
    }

    private LinearLayout rowCard(String title, String sub, boolean highlight,
                                 android.view.View.OnClickListener click) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(12), dp(14));
        card.setBackgroundResource(highlight ? R.drawable.bg_tile_blue : R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView name = new TextView(this);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(name);

        TextView s = new TextView(this);
        s.setText(sub);
        s.setTextColor(Color.rgb(169, 186, 223));
        s.setTextSize(12);
        texts.addView(s);
        card.addView(texts);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(24);
        card.addView(arrow);

        card.setOnClickListener(click);
        return card;
    }

    private void startQuiz(String chapter, String topic) {
        Intent i = new Intent(this, QuizActivity.class);
        i.putExtra("mode", "practice");
        i.putExtra("subject", subject);
        if (chapter != null) i.putExtra("chapter", chapter);
        if (topic != null) i.putExtra("topic", topic);
        startActivity(i);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
