package com.nawazishali.muetpractice;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
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
            img.setImageBitmap(makeCircular(bmp));
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

    /**
     * Crops the photo into a circle with a premium gold ring border.
     * Any failure returns the original bitmap - it can never break the UI.
     */
    public static Bitmap makeCircular(Bitmap src) {
        try {
            if (src == null) return null;
            int size = Math.min(src.getWidth(), src.getHeight());
            if (size <= 0) return src;
            int x0 = (src.getWidth() - size) / 2;
            int y0 = (src.getHeight() - size) / 2;
            Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            BitmapShader shader = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            Matrix m = new Matrix();
            m.setTranslate(-x0, -y0);
            shader.setLocalMatrix(m);
            paint.setShader(shader);
            float r = size / 2f;
            canvas.drawCircle(r, r, r, paint);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            float sw = Math.max(3f, size * 0.05f);
            ring.setStrokeWidth(sw);
            ring.setColor(0xFFF5B81D);
            canvas.drawCircle(r, r, r - sw / 2f, ring);
            Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
            inner.setStyle(Paint.Style.STROKE);
            inner.setStrokeWidth(Math.max(1.5f, size * 0.014f));
            inner.setColor(0xFFFFE9A8);
            canvas.drawCircle(r, r, r - sw - size * 0.014f, inner);
            return out;
        } catch (Throwable t) {
            return src;
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
