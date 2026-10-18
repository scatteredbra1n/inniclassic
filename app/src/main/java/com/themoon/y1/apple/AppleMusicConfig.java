package com.themoon.y1.apple;

import java.io.File;
import java.io.FileInputStream;

/**
 * MusicKit needs a developer token (a JWT signed with an Apple Music private key).
 * The private key is never stored here. The token comes from local.properties at
 * build time, or from a one-line file on the SD card so a flashed device can be
 * configured without rebuilding.
 */
public final class AppleMusicConfig {
    public static final String TOKEN_FILE = "/storage/sdcard0/.apple_music_developer_token";

    private AppleMusicConfig() {}

    public static String resolve(String buildToken, File file) {
        String fromFile = readTokenFile(file);
        if (!fromFile.isEmpty()) return fromFile;
        return buildToken == null ? "" : buildToken.trim();
    }

    public static boolean isUsable(String token) {
        return looksLikeJwt(token);
    }

    /** A MusicKit developer token is a three-segment JWT. Anything else is not configured. */
    public static boolean looksLikeJwt(String token) {
        if (token == null) return false;
        String trimmed = token.trim();
        int first = trimmed.indexOf('.');
        int second = trimmed.lastIndexOf('.');
        return first > 0 && second > first + 1 && second < trimmed.length() - 1
                && trimmed.indexOf('.', first + 1) == second;
    }

    private static String readTokenFile(File file) {
        if (file == null || !file.isFile()) return "";
        try {
            byte[] data = new byte[(int) Math.min(file.length(), 8192)];
            FileInputStream in = new FileInputStream(file);
            int read = in.read(data);
            in.close();
            if (read <= 0) return "";
            return new String(data, 0, read, "UTF-8").trim();
        } catch (Exception ignored) {
            return "";
        }
    }
}
