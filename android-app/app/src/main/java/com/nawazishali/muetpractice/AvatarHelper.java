package com.nawazishali.muetpractice;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;

public class AvatarHelper {

    /**
     * Shows the user's photo when one is set, otherwise their initials.
     * The photo is always decoded DOWN-SAMPLED to a small size and every
     * failure is swallowed — a huge camera photo can never crash the app
     * or block it from opening again.
     */
    public static void load(ImageView img, TextView initials, User user) {
        Bitmap bmp = null;
        try {
            if (user != null && user.photoPath != null) {
                File f = new File(user.photoPath);
                if (f.exists() && f.length() > 0) {
                    bmp = decodeSampled(user.photoPath, 256);
                }
            }
        } catch (Throwable ignored) {
            bmp = null;
        }
        if (bmp != null) {
            img.setImageBitmap(bmp);
            img.setVisibility(View.VISIBLE);
            if (initials != null) initials.setVisibility(View.GONE);
        } else {
            img.setVisibility(View.GONE);
            if (initials != null) {
                initials.setText(user == null ? "?" : user.initials());
                initials.setVisibility(View.VISIBLE);
            }
        }
    }

    /** Decodes an image file scaled down so neither dimension is much larger than targetPx. */
    public static Bitmap decodeSampled(String path, int targetPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / sample > targetPx * 2) {
            sample *= 2;
        }
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        opts.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(path, opts);
    }
}
