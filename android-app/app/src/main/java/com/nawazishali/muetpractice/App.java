package com.nawazishali.muetpractice;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            CrashSaver.save(this, error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            }
        });
    }
}
