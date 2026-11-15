package com.nawazishali.muetpractice;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;

/** Shows one diagram big, on a white card, with its explanation. */
public class DiagramDetailActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String file = getIntent().getStringExtra("file");
        String titleText = getIntent().getStringExtra("title");
        String explanation = getIntent().getStringExtra("explanation");

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(238, 245, 255));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(box);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Color.rgb(18, 38, 74));
        box.addView(title);

        ImageView img = new ImageView(this);
        img.setBackgroundColor(Color.WHITE);
        img.setPadding(dp(10), dp(10), dp(10), dp(10));
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(14);
        img.setLayoutParams(ilp);
        try {
            InputStream in = getAssets().open("diagrams/" + file);
            Bitmap b = BitmapFactory.decodeStream(in);
            in.close();
            img.setImageBitmap(b);
        } catch (Exception e) {
            // Image missing; the explanation still shows.
        }
        box.addView(img);

        TextView body = new TextView(this);
        body.setText(explanation);
        body.setTextSize(16);
        body.setLineSpacing(0, 1.25f);
        body.setTextColor(Color.rgb(40, 55, 85));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(16);
        body.setLayoutParams(blp);
        box.addView(body);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
