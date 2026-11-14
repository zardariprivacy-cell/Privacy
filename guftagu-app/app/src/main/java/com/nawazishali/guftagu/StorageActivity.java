package com.nawazishali.guftagu;

import android.app.AlertDialog;
import android.widget.Toast;

import java.io.File;

public class StorageActivity extends PrefsActivity {

    @Override
    protected String title() { return "Storage"; }

    @Override
    protected void build() {
        LocalDb db = new LocalDb(this);
        PrefRows.header(this, list, "IS PHONE PAR");
        PrefRows.textRow(this, list, "Saved messages", db.countAll() + " messages (sirf is phone par, decrypted)", null);
        long cache = MediaHelper.dirSize(new File(getCacheDir(), "media"));
        PrefRows.textRow(this, list, "Media cache", MediaHelper.fmtSize(cache) + " (photos/voice jo khule hain)", null);
        PrefRows.note(this, list, "Server par tumhare messages sirf encrypted (band) paray hain — un ka size wahan alag hai.");

        PrefRows.header(this, list, "SAAF KARO");
        PrefRows.textRow(this, list, "Media cache saaf karo",
                "Photos/voice dobara zaroorat parne par download honge",
                v -> {
                    deleteRec(new File(getCacheDir(), "media"));
                    Toast.makeText(this, "Media cache saaf ho gaya", Toast.LENGTH_SHORT).show();
                    recreate();
                });
        PrefRows.textRow(this, list, "Local chat history delete karo",
                "Is phone ki saari chat history khatam (server se aakhri messages dobara aa sakte hain)",
                v -> new AlertDialog.Builder(this)
                        .setTitle("Pakka delete karein?")
                        .setMessage("Is phone par mojood saari chat history delete ho jayegi.")
                        .setNegativeButton("Nahi", null)
                        .setPositiveButton("Delete", (d, w) -> {
                            new LocalDb(this).clearAll();
                            Toast.makeText(this, "History delete ho gayi", Toast.LENGTH_SHORT).show();
                            recreate();
                        })
                        .show());
    }

    private void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }
}
