package com.themoon.y1.apple;

import android.content.Context;
import android.content.SharedPreferences;

import com.themoon.y1.BuildConfig;
import com.themoon.y1.MainActivity;
import com.themoon.y1.models.SongItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Stores the MusicKit music-user token and the fetched library on the device.
 * The developer token is only a configuration value; a missing one leaves the
 * library empty and says so.
 */
public final class AppleMusicLibrary {
    private static AppleMusicLibrary instance;

    private final SharedPreferences prefs;
    private final OkHttpClient client = buildHttpClient();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    private AppleMusicLibrary(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences("Y1_APPLE_MUSIC", Context.MODE_PRIVATE);
    }

    public static synchronized AppleMusicLibrary getInstance(Context context) {
        if (instance == null) instance = new AppleMusicLibrary(context);
        return instance;
    }

    public String developerToken() {
        return AppleMusicConfig.resolve(BuildConfig.MUSICKIT_DEVELOPER_TOKEN, new File(AppleMusicConfig.TOKEN_FILE));
    }

    public boolean isConfigured() {
        return AppleMusicConfig.isUsable(developerToken());
    }

    public boolean isPaired() {
        return !userToken().isEmpty();
    }

    public boolean isSyncing() {
        return prefs.getBoolean("syncing", false);
    }

    public String lastError() {
        return prefs.getString("error", "");
    }

    public int songCount() {
        return storedSongs().size();
    }

    public List<SongItem> storedSongs() {
        try {
            return AppleMusicSongs.fromStorageJson(prefs.getString("library", "[]"));
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    public String statusJson() {
        String error = lastError() == null ? "" : lastError();
        return "{\"configured\":" + isConfigured()
                + ",\"paired\":" + isPaired()
                + ",\"songCount\":" + songCount()
                + ",\"syncing\":" + isSyncing()
                + ",\"error\":\"" + escapeJson(error) + "\"}";
    }

    /** Saves the token from the phone page and starts a library download. Does not invent songs. */
    public void saveUserTokenAndSync(String musicUserToken) {
        if (!isConfigured()) {
            prefs.edit().putString("error", "Apple Music is not configured").putBoolean("syncing", false).apply();
            return;
        }
        if (musicUserToken == null || musicUserToken.trim().isEmpty()) {
            prefs.edit().putString("error", "Apple did not return a music-user token").putBoolean("syncing", false).apply();
            return;
        }
        prefs.edit().putString("user_token", musicUserToken.trim()).putString("error", "").putBoolean("syncing", true).apply();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                fetchLibrary();
            }
        });
    }

    public void refresh() {
        if (!isConfigured() || !isPaired()) return;
        prefs.edit().putBoolean("syncing", true).putString("error", "").apply();
        worker.execute(new Runnable() {
            @Override
            public void run() {
                fetchLibrary();
            }
        });
    }

    public void unpair() {
        prefs.edit().remove("user_token").putString("library", "[]").putString("error", "").putBoolean("syncing", false).apply();
        publish();
    }

    private void fetchLibrary() {
        try {
            List<SongItem> songs = new ArrayList<>();
            String path = "/v1/me/library/songs?limit=100";
            int pages = 0;
            while (path != null && pages < 100 && songs.size() < 10000) {
                pages++;
                String body = get(path);
                AppleMusicSongs.Page page = AppleMusicSongs.parseLibraryPage(body);
                songs.addAll(page.songs);
                path = page.next;
            }
            prefs.edit().putString("library", AppleMusicSongs.toStorageJson(songs))
                    .putString("error", "")
                    .putBoolean("syncing", false)
                    .apply();
            publish();
        } catch (Exception e) {
            String message = e.getMessage() == null ? "Could not load the Apple Music library" : e.getMessage();
            prefs.edit().putString("error", message).putBoolean("syncing", false).apply();
        }
    }

    private String get(String path) throws java.io.IOException {
        String token = developerToken();
        String user = userToken();
        Request request = new Request.Builder()
                .url("https://api.music.apple.com" + path)
                .header("Authorization", "Bearer " + token)
                .header("Music-User-Token", user)
                .build();
        Response response = client.newCall(request).execute();
        String body = response.body() == null ? "" : response.body().string();
        if (!response.isSuccessful()) {
            throw new java.io.IOException("Apple Music library request failed (" + response.code() + ")");
        }
        return body;
    }

    private String userToken() {
        String token = prefs.getString("user_token", "");
        return token == null ? "" : token.trim();
    }

    private void publish() {
        final MainActivity main = MainActivity.instance;
        if (main == null) return;
        main.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                main.applyAppleMusicLibrary(true);
            }
        });
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
    }

    private static OkHttpClient buildHttpClient() {
        try {
            final javax.net.ssl.X509TrustManager trustAll = new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
            };
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS", "Conscrypt");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{trustAll}, new java.security.SecureRandom());
            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAll)
                    .hostnameVerifier(new javax.net.ssl.HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, javax.net.ssl.SSLSession session) { return true; }
                    })
                    .build();
        } catch (Exception e) {
            return new OkHttpClient();
        }
    }

    public static List<SongItem> empty() {
        return Collections.emptyList();
    }
}
