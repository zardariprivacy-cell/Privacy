package com.nawazishali.muetpractice;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Simple English lessons for the English Language Learning section. */
public class LessonsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.bg_app);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("📚 English Lessons");
        title.setTextColor(Color.rgb(18, 38, 74));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Short, simple lessons — read them, then practice in the Grammar and Vocabulary topics.");
        sub.setTextColor(Color.rgb(90, 107, 140));
        sub.setTextSize(13);
        sub.setPadding(0, dp(4), 0, dp(8));
        root.addView(sub);

        addLesson(root, "Lesson 1 — The 12 Tenses (Easy Chart)",
                "PRESENT\n" +
                "1. Present Simple: I play. (roz ka kaam / sach)\n" +
                "2. Present Continuous: I am playing. (abhi ho raha hai)\n" +
                "3. Present Perfect: I have played. (ho chuka hai)\n" +
                "4. Present Perfect Continuous: I have been playing since morning.\n\n" +
                "PAST\n" +
                "5. Past Simple: I played yesterday.\n" +
                "6. Past Continuous: I was playing at 5 pm.\n" +
                "7. Past Perfect: I had played before he came.\n" +
                "8. Past Perfect Continuous: I had been playing for two hours.\n\n" +
                "FUTURE\n" +
                "9. Future Simple: I will play tomorrow.\n" +
                "10. Future Continuous: I will be playing at 5 pm.\n" +
                "11. Future Perfect: I will have played by Sunday.\n" +
                "12. Future Perfect Continuous: I will have been playing for a week.\n\n" +
                "Yaad rakho: has/have ke saath hamesha verb ki 3rd form aati hai (played, gone, written).");

        addLesson(root, "Lesson 2 — Articles: A, An, The",
                "A / AN — kisi ek aam cheez ke liye:\n" +
                "• a book, a car, a university (university 'yoo' se shuru hota hai, is liye A)\n" +
                "• an apple, an hour (hour mein h silent hai), an honest man\n\n" +
                "Rule: agar word vowel SOUND (a, e, i, o, u ki awaz) se shuru ho to AN, warna A.\n\n" +
                "THE — khaas / mashhoor cheez ke liye:\n" +
                "• the sun, the moon, the Earth (ye ek hi hain)\n" +
                "• the best, the first, the largest (superlative words ke saath hamesha THE)\n" +
                "• the Quran, the Indus (khaas kitabein aur darya)\n\n" +
                "NO ARTICLE: languages (English, Urdu), games (cricket), subjects (Mathematics) ke pehle kuch nahi aata.");

        addLesson(root, "Lesson 3 — Prepositions (20 Common Uses)",
                "• good AT English — bad AT maths (skill ke saath AT)\n" +
                "• interested IN music\n" +
                "• afraid OF dogs\n" +
                "• depend ON me\n" +
                "• senior TO me / junior TO me (than nahi aata)\n" +
                "• married TO someone\n" +
                "• famous FOR his poetry\n" +
                "• proud OF my country\n" +
                "• full OF water\n" +
                "• kind TO the poor\n" +
                "• angry WITH a person, angry AT a thing\n" +
                "• listen TO the teacher\n" +
                "• wait FOR the bus\n" +
                "• suffer FROM fever\n" +
                "• belong TO me\n" +
                "• agree WITH a person, agree TO a plan\n" +
                "• fond OF tea\n" +
                "• tired OF waiting\n" +
                "• made OF wood\n" +
                "• in the morning, at night, on Monday (waqt ke saath)");

        addLesson(root, "Lesson 4 — 50 Common English Words",
                "Rapid — very fast • Ancient — very old • Honest — truthful\n" +
                "Brave — not afraid • Silent — quiet • Difficult — hard\n" +
                "Necessary — needed • Famous — well known • Journey — trip\n" +
                "Purchase — buy • Assist — help • Discover — find out\n" +
                "Enough — sufficient • Victory — win • Defeat — loss\n" +
                "Begin — start • Wealthy — rich • Generous — kind in giving\n" +
                "Exhausted — very tired • Vanish — disappear • Polite — well-mannered\n" +
                "Cure — treat successfully • Arrive — reach • Careful — cautious\n" +
                "Medal — award • Knowledge — information & understanding\n" +
                "Danger — risk • Difficult — not easy • Simple — easy\n" +
                "Strong — powerful • Weak — not strong • Happy — glad\n" +
                "Angry — annoyed • Proud — feeling pride • Afraid — frightened\n" +
                "Busy — occupied • Free — not busy • Ready — prepared\n" +
                "True — correct • False — wrong • Important — necessary & valuable\n" +
                "Different — not the same • Similar — alike • Common — usual\n" +
                "Special — particular • Whole — complete • Empty — with nothing inside");

        addLesson(root, "Lesson 5 — Synonyms & Antonyms (20 Pairs)",
                "SYNONYMS (same meaning):\n" +
                "Happy = Glad • Rapid = Fast • Brave = Courageous\n" +
                "Rich = Wealthy • Begin = Start • Difficult = Hard\n" +
                "Ancient = Old • Assist = Help • Purchase = Buy\n" +
                "Famous = Well known\n\n" +
                "ANTONYMS (opposite meaning):\n" +
                "Victory x Defeat • Brave x Cowardly • Ancient x Modern\n" +
                "Expand x Contract • Arrive x Depart • Hot x Cold\n" +
                "Rich x Poor • Easy x Difficult • Begin x End\n" +
                "Love x Hate\n\n" +
                "Tip: Job tests (NTS, PTS) mein har saal synonyms/antonyms ke 5-10 sawal aate hain — ye list ratta kar lo!");

        TextView back = new TextView(this);
        back.setText("‹  Back");
        back.setTextColor(Color.rgb(21, 101, 192));
        back.setTextSize(16);
        back.setPadding(0, dp(14), 0, 0);
        back.setOnClickListener(v -> finish());
        root.addView(back);
    }

    private void addLesson(LinearLayout root, String heading, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        card.setLayoutParams(lp);

        TextView h = new TextView(this);
        h.setText(heading);
        h.setTextColor(Color.rgb(21, 101, 192));
        h.setTextSize(16);
        h.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(h);

        TextView b = new TextView(this);
        b.setText(body);
        b.setTextColor(Color.rgb(51, 65, 92));
        b.setTextSize(13.5f);
        b.setPadding(0, dp(6), 0, 0);
        b.setLineSpacing(0, 1.25f);
        card.addView(b);
        root.addView(card);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
