package com.nawazishali.muetpractice;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Lists the labelled Biology diagrams bundled in assets/diagrams. */
public class DiagramsActivity extends Activity {

    static class Item {
        String file, title, group, explanation;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(238, 245, 255));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(box);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Biology Diagrams");
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(18, 38, 74));
        box.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Tap any diagram to see it big, with a simple explanation.");
        sub.setTextSize(13);
        sub.setTextColor(Color.rgb(90, 107, 140));
        box.addView(sub);

        String lastGroup = null;
        for (Item it : loadIndex()) {
            if (lastGroup == null || !lastGroup.equals(it.group)) {
                lastGroup = it.group;
                TextView h = new TextView(this);
                h.setText(it.group);
                h.setTextSize(17);
                h.setTypeface(Typeface.DEFAULT_BOLD);
                h.setTextColor(Color.rgb(21, 101, 192));
                LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                hlp.topMargin = dp(20);
                h.setLayoutParams(hlp);
                box.addView(h);
            }
            box.addView(row(it));
        }

        TextView credit = new TextView(this);
        credit.setText("Diagram images: Wikimedia Commons (free educational licences).");
        credit.setTextSize(11);
        credit.setTextColor(Color.rgb(120, 135, 160));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(24);
        credit.setLayoutParams(clp);
        box.addView(credit);
    }

    private LinearLayout row(final Item it) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        card.setLayoutParams(lp);

        ImageView thumb = new ImageView(this);
        thumb.setLayoutParams(new LinearLayout.LayoutParams(dp(64), dp(64)));
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setImageBitmap(loadThumb(it.file));
        card.addView(thumb);

        TextView name = new TextView(this);
        name.setText(it.title);
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Color.rgb(18, 38, 74));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nlp.leftMargin = dp(14);
        name.setLayoutParams(nlp);
        card.addView(name);

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(24);
        arrow.setTextColor(Color.rgb(18, 38, 74));
        card.addView(arrow);

        card.setOnClickListener(v -> {
            Intent i = new Intent(this, DiagramDetailActivity.class);
            i.putExtra("file", it.file);
            i.putExtra("title", it.title);
            i.putExtra("explanation", it.explanation);
            startActivity(i);
        });
        return card;
    }

    private Bitmap loadThumb(String file) {
        try {
            InputStream in = getAssets().open("diagrams/" + file);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private List<Item> loadIndex() {
        List<Item> out = new ArrayList<>();
        try {
            InputStream in = getAssets().open("diagrams/index.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] f = line.split("\\|", 4);
                if (f.length != 4) continue;
                Item it = new Item();
                it.file = f[0].trim();
                it.title = f[1].trim();
                it.group = f[2].trim();
                it.explanation = f[3].trim();
                out.add(it);
            }
            br.close();
        } catch (Exception e) {
            // No diagrams bundled; the screen just stays empty.
        }
        return out;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
