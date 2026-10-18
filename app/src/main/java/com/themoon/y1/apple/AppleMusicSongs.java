package com.themoon.y1.apple;

import com.themoon.y1.models.SongItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Turns Apple Music library JSON into songs that share the on-device library. */
public final class AppleMusicSongs {
    public static final String PATH_MARKER = "/.apple-music/";
    public static final String ROOT = "/storage/sdcard0/Music/.apple-music/";

    public static final int LOCAL = 1;
    public static final int APPLE = 2;
    public static final int MIXED = 3;

    private AppleMusicSongs() {}

    public static final class Page {
        public final List<SongItem> songs;
        public final String next;

        Page(List<SongItem> songs, String next) {
            this.songs = songs;
            this.next = next;
        }
    }

    public static final class PlayQueue {
        public final List<File> files;
        public final int index;
        public final boolean targetIsApple;

        PlayQueue(List<File> files, int index, boolean targetIsApple) {
            this.files = files;
            this.index = index;
            this.targetIsApple = targetIsApple;
        }
    }

    public static boolean isApple(SongItem song) {
        return song != null && isAppleFile(song.file);
    }

    public static boolean isAppleFile(File file) {
        return file != null && file.getAbsolutePath().contains(PATH_MARKER);
    }

    public static File fileFor(String appleId) {
        String safe = appleId == null ? "song" : appleId.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isEmpty()) safe = "song";
        return new File(ROOT + safe);
    }

    /** One page of GET /v1/me/library/songs. Throws instead of inventing tracks. */
    @SuppressWarnings("unchecked")
    public static Page parseLibraryPage(String json) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map)) throw new IllegalArgumentException("Apple Music library was not an object");
        Map<String, Object> object = (Map<String, Object>) root;
        Object data = object.get("data");
        if (!(data instanceof List)) {
            throw new IllegalArgumentException(errorMessage(object));
        }
        List<SongItem> songs = new ArrayList<>();
        for (Object entry : (List<Object>) data) {
            if (!(entry instanceof Map)) continue;
            SongItem song = songFrom((Map<String, Object>) entry);
            if (song != null) songs.add(song);
        }
        String next = null;
        Object nextValue = object.get("next");
        if (nextValue instanceof String && !((String) nextValue).trim().isEmpty()) {
            next = normalizeNext((String) nextValue);
        }
        return new Page(songs, next);
    }

    public static void mergeInto(List<SongItem> library, List<SongItem> appleSongs) {
        Iterator<SongItem> it = library.iterator();
        while (it.hasNext()) {
            if (isApple(it.next())) it.remove();
        }
        if (appleSongs != null) library.addAll(appleSongs);
    }

    /**
     * Local playback keeps every on-device file. An Apple Music row is not a file,
     * so it is left out of the queue. If the row the user chose is Apple Music,
     * the caller should explain that instead of starting playback.
     */
    public static PlayQueue localQueue(List<File> files, int index) {
        if (files == null || files.isEmpty()) return new PlayQueue(new ArrayList<File>(), 0, false);
        if (index < 0 || index >= files.size()) index = 0;
        if (isAppleFile(files.get(index))) return new PlayQueue(new ArrayList<File>(), 0, true);
        List<File> local = new ArrayList<>();
        int localIndex = 0;
        for (int i = 0; i < files.size(); i++) {
            if (isAppleFile(files.get(i))) continue;
            if (i == index) localIndex = local.size();
            local.add(files.get(i));
        }
        return new PlayQueue(local, localIndex, false);
    }

    public static String toStorageJson(List<SongItem> songs) {
        StringBuilder out = new StringBuilder();
        out.append('[');
        boolean first = true;
        if (songs != null) {
            for (SongItem song : songs) {
                if (!isApple(song)) continue;
                if (!first) out.append(',');
                first = false;
                String id = song.file.getName();
                out.append('{');
                field(out, "id", id);
                out.append(',');
                field(out, "title", song.title);
                out.append(',');
                field(out, "artist", song.artist);
                out.append(',');
                field(out, "album", song.album);
                out.append(',');
                field(out, "year", song.year);
                out.append(',');
                field(out, "genre", song.genre);
                out.append(',');
                field(out, "albumArtist", song.albumArtist);
                out.append(',');
                field(out, "composer", song.composer);
                out.append(",\"track\":").append(song.trackNumber);
                out.append('}');
            }
        }
        out.append(']');
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    public static List<SongItem> fromStorageJson(String json) {
        List<SongItem> songs = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return songs;
        Object root = MiniJson.parse(json);
        if (!(root instanceof List)) return songs;
        for (Object entry : (List<Object>) root) {
            if (!(entry instanceof Map)) continue;
            Map<String, Object> object = (Map<String, Object>) entry;
            String id = text(object.get("id"));
            if (id.isEmpty()) continue;
            SongItem song = new SongItem(fileFor(id), textOr(object.get("title"), "Unknown Title"),
                    text(object.get("artist")), text(object.get("album")), text(object.get("year")),
                    text(object.get("genre")), text(object.get("albumArtist")), text(object.get("composer")));
            song.source = "apple";
            song.trackNumber = number(object.get("track"));
            songs.add(song);
        }
        return songs;
    }

    @SuppressWarnings("unchecked")
    private static SongItem songFrom(Map<String, Object> entry) {
        String id = text(entry.get("id"));
        if (id.isEmpty()) return null;
        Object attributes = entry.get("attributes");
        if (!(attributes instanceof Map)) return null;
        Map<String, Object> attr = (Map<String, Object>) attributes;
        String title = textOr(attr.get("name"), "Unknown Title");
        String artist = text(attr.get("artistName"));
        String album = text(attr.get("albumName"));
        String composer = text(attr.get("composerName"));
        String genre = firstString(attr.get("genreNames"));
        String year = yearOf(text(attr.get("releaseDate")));
        SongItem song = new SongItem(fileFor(id), title, artist, album, year, genre, artist, composer);
        song.source = "apple";
        song.trackNumber = number(attr.get("trackNumber"));
        return song;
    }

    @SuppressWarnings("unchecked")
    private static String errorMessage(Map<String, Object> object) {
        Object errors = object.get("errors");
        if (errors instanceof List && !((List<Object>) errors).isEmpty()) {
            Object first = ((List<Object>) errors).get(0);
            if (first instanceof Map) {
                Object detail = ((Map<String, Object>) first).get("detail");
                if (detail instanceof String && !((String) detail).trim().isEmpty()) {
                    return (String) detail;
                }
                Object title = ((Map<String, Object>) first).get("title");
                if (title instanceof String && !((String) title).trim().isEmpty()) return (String) title;
            }
        }
        return "Apple Music did not return a library";
    }

    private static String normalizeNext(String next) {
        String value = next.trim();
        String host = "https://api.music.apple.com";
        if (value.startsWith(host)) value = value.substring(host.length());
        if (!value.startsWith("/")) return null;
        return value;
    }

    private static String firstString(Object value) {
        if (!(value instanceof List) || ((List<?>) value).isEmpty()) return "";
        Object first = ((List<?>) value).get(0);
        return first instanceof String ? (String) first : "";
    }

    private static String yearOf(String releaseDate) {
        if (releaseDate.length() >= 4 && isDigits(releaseDate.substring(0, 4))) return releaseDate.substring(0, 4);
        return "";
    }

    private static boolean isDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

    private static int number(Object value) {
        if (value instanceof Number) return ((Number) value).intValue();
        return 0;
    }

    private static String text(Object value) {
        return value instanceof String ? (String) value : "";
    }

    private static String textOr(Object value, String fallback) {
        String text = text(value);
        return text.isEmpty() ? fallback : text;
    }

    private static void field(StringBuilder out, String key, String value) {
        out.append('"').append(key).append("\":\"").append(escape(value)).append('"');
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
