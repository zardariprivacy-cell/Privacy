package com.nawazishali.guftagu;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

/**
 * Applies the in-app Light/Dark choice by forcing the night configuration,
 * so -night resources (drawables, colors, theme) follow the user's setting
 * rather than the phone's system theme.
 */
public class BaseActivity extends Activity {

    @Override
    protected void attachBaseContext(Context base) {
        if (AppPrefs.darkMode(base)) {
            Configuration cfg = new Configuration(base.getResources().getConfiguration());
            cfg.uiMode = (cfg.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
            base = base.createConfigurationContext(cfg);
        }
        super.attachBaseContext(base);
    }
}
