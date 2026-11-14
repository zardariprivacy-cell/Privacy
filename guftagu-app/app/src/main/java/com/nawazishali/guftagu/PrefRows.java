package com.nawazishali.guftagu;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Small builders for WhatsApp-style settings rows. */
public class PrefRows {

    public interface OnToggle {
        void changed(boolean on);
    }

    private static int dp(Activity a, int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    public static void header(Activity a, LinearLayout parent, String text) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(0xFF00A884);
        tv.setTextSize(13);
        tv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 18);
        lp.bottomMargin = dp(a, 4);
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    public static void note(Activity a, LinearLayout parent, String text) {
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextColor(0xFF8696A0);
        tv.setTextSize(13);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(a, 6);
        tv.setLayoutParams(lp);
        parent.addView(tv);
    }

    public static void switchRow(Activity a, LinearLayout parent, String title, String sub,
                                 boolean checked, final OnToggle cb) {
        LinearLayout row = rowBase(a, parent);
        row.addView(texts(a, title, sub));
        Switch sw = new Switch(a);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((CompoundButton b, boolean on) -> cb.changed(on));
        row.addView(sw);
    }

    public static void textRow(Activity a, LinearLayout parent, String title, String sub,
                               View.OnClickListener click) {
        LinearLayout row = rowBase(a, parent);
        row.addView(texts(a, title, sub));
        TextView arrow = new TextView(a);
        arrow.setText("›");
        arrow.setTextColor(0xFF8696A0);
        arrow.setTextSize(22);
        row.addView(arrow);
        if (click != null) row.setOnClickListener(click);
    }

    private static LinearLayout rowBase(Activity a, LinearLayout parent) {
        LinearLayout row = new LinearLayout(a);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(a, 4), dp(a, 10), dp(a, 4), dp(a, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(lp);
        parent.addView(row);
        return row;
    }

    private static LinearLayout texts(Activity a, String title, String sub) {
        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView tvT = new TextView(a);
        tvT.setText(title);
        tvT.setTextColor(0xFFFFFFFF);
        tvT.setTextSize(16);
        texts.addView(tvT);
        if (sub != null && !sub.isEmpty()) {
            TextView tvS = new TextView(a);
            tvS.setText(sub);
            tvS.setTextColor(0xFF8696A0);
            tvS.setTextSize(13);
            texts.addView(tvS);
        }
        return texts;
    }
}
