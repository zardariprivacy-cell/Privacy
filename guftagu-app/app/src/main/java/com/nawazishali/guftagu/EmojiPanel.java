package com.nawazishali.guftagu;

import android.app.Activity;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;

/** Simple built-in emoji panel for the chat input bar (no library needed). */
public class EmojiPanel {

    private static final String[] EMOJIS = {
            "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎",
            "🤩", "😜", "😇", "🙂", "😉", "😌", "😴", "😱",
            "🥳", "😢", "😭", "😡", "👍", "👎", "👏", "🙏",
            "💪", "🔥", "❤️", "💔", "💯", "✨", "🎉", "🌹",
            "🇵🇰", "🤲", "😅", "🙄", "🤔", "🥺", "😋", "🤗"
    };

    public static void build(Activity a, GridLayout grid, final EditText target) {
        float dens = a.getResources().getDisplayMetrics().density;
        int pad = Math.round(6 * dens);
        for (final String e : EMOJIS) {
            TextView tv = new TextView(a);
            tv.setText(e);
            tv.setTextSize(24);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(pad, pad, pad, pad);
            tv.setOnClickListener(v -> {
                int pos = target.getSelectionStart();
                if (pos < 0) pos = target.getText().length();
                target.getText().insert(pos, e);
            });
            grid.addView(tv);
        }
    }
}
