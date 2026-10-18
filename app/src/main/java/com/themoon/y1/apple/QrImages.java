package com.themoon.y1.apple;

import android.graphics.Bitmap;

/** Draws a QR matrix into a black-and-white bitmap with a quiet zone. */
public final class QrImages {
    private QrImages() {}

    public static Bitmap bitmap(String text, int scale, int border) {
        QrCode code = QrCode.encode(text);
        int modules = code.size + border * 2;
        int pixels = Math.max(1, modules * Math.max(1, scale));
        int[] colors = new int[pixels * pixels];
        for (int y = 0; y < pixels; y++) {
            int moduleY = y / scale - border;
            for (int x = 0; x < pixels; x++) {
                int moduleX = x / scale - border;
                boolean dark = moduleX >= 0 && moduleY >= 0 && moduleX < code.size && moduleY < code.size
                        && code.getModule(moduleX, moduleY);
                colors[y * pixels + x] = dark ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(colors, 0, pixels, 0, 0, pixels, pixels);
        return bitmap;
    }
}
