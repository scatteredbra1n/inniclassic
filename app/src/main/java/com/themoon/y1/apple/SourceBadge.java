package com.themoon.y1.apple;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Small row icons: a disc for local files, a pink note for Apple Music, both when a group is mixed. */
public final class SourceBadge {
    private static Drawable local;
    private static Drawable apple;
    private static Drawable mixed;
    private static int cachedPx;

    private SourceBadge() {}

    public static Drawable get(Context context, int kind) {
        float density = context.getResources().getDisplayMetrics().density;
        int px = Math.max(12, Math.round(14 * density));
        if (cachedPx != px) {
            local = null;
            apple = null;
            mixed = null;
            cachedPx = px;
        }
        if (kind == AppleMusicSongs.APPLE) {
            if (apple == null) apple = draw(context, px, AppleMusicSongs.APPLE);
            return apple;
        }
        if (kind == AppleMusicSongs.MIXED) {
            if (mixed == null) mixed = draw(context, px, AppleMusicSongs.MIXED);
            return mixed;
        }
        if (local == null) local = draw(context, px, AppleMusicSongs.LOCAL);
        return local;
    }

    private static Drawable draw(Context context, int px, int kind) {
        int width = kind == AppleMusicSongs.MIXED ? px * 2 + Math.max(2, px / 5) : px;
        Bitmap bitmap = Bitmap.createBitmap(width, px, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        if (kind == AppleMusicSongs.LOCAL) drawLocal(canvas, px / 2f, px / 2f, px / 2f);
        else if (kind == AppleMusicSongs.APPLE) drawApple(canvas, px / 2f, px / 2f, px / 2f);
        else {
            drawLocal(canvas, px / 2f, px / 2f, px / 2f);
            drawApple(canvas, width - px / 2f, px / 2f, px / 2f);
        }
        BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
        drawable.setBounds(0, 0, width, px);
        return drawable;
    }

    private static void drawLocal(Canvas canvas, float cx, float cy, float radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, radius / 4f));
        paint.setColor(0xFF8E8E93);
        canvas.drawCircle(cx, cy, radius - paint.getStrokeWidth(), paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, radius / 3.2f, paint);
    }

    private static void drawApple(Canvas canvas, float cx, float cy, float radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFFC3C44);
        canvas.drawCircle(cx, cy, radius, paint);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        float head = radius * 0.42f;
        canvas.drawCircle(cx - head * 0.15f, cy + head * 0.35f, head, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.4f, radius / 5f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawLine(cx + head * 0.7f, cy + head * 0.15f, cx + head * 0.85f, cy - radius * 0.72f, paint);
    }
}
