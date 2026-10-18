package com.themoon.y1.apple;

import com.themoon.y1.models.SongItem;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class AppleMusicTest {
    private static final String PAGE = "{"
            + "\"data\":[{\"id\":\"i.abc\",\"type\":\"library-songs\",\"attributes\":{"
            + "\"name\":\"Night Drive\",\"artistName\":\"Ada\",\"albumName\":\"City Lights\","
            + "\"genreNames\":[\"Pop\"],\"releaseDate\":\"2020-05-01\",\"trackNumber\":2,"
            + "\"composerName\":\"Ada\"}}],"
            + "\"next\":\"/v1/me/library/songs?offset=100\"}";

    @Test
    public void missingTokenIsNotConfiguredAndDoesNotInventSongs() {
        assertFalse(AppleMusicConfig.isUsable(""));
        assertFalse(AppleMusicConfig.isUsable("not-a-token"));
        assertFalse(AppleMusicConfig.isUsable(null));
        assertTrue(AppleMusicConfig.isUsable("aaa.bbb.ccc"));
        assertEquals("", AppleMusicConfig.resolve("", new File("/tmp/does-not-exist-apple-token")));
        String page = AppleMusicPage.html("");
        assertTrue(page.contains("not configured"));
        assertFalse(page.contains("developerToken"));
        assertFalse(page.contains("Night Drive"));
        try {
            AppleMusicSongs.parseLibraryPage("{\"errors\":[{\"detail\":\"Unauthorized\"}]}");
            fail("error payload should not become songs");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Unauthorized"));
        }
    }

    @Test
    public void libraryPageMergesBesideLocalSongs() {
        AppleMusicSongs.Page page = AppleMusicSongs.parseLibraryPage(PAGE);
        assertEquals(1, page.songs.size());
        assertEquals("/v1/me/library/songs?offset=100", page.next);
        SongItem apple = page.songs.get(0);
        assertEquals("Night Drive", apple.title);
        assertEquals("Ada", apple.artist);
        assertEquals("City Lights", apple.album);
        assertEquals("2020", apple.year);
        assertEquals("Pop", apple.genre);
        assertEquals(2, apple.trackNumber);
        assertTrue(AppleMusicSongs.isApple(apple));
        assertTrue(apple.file.getAbsolutePath().contains("/Music/"));

        SongItem local = new SongItem(new File("/storage/sdcard0/Music/night.mp3"), "Night Drive", "Ada", "City Lights");
        List<SongItem> library = new ArrayList<>();
        library.add(local);
        AppleMusicSongs.mergeInto(library, page.songs);
        AppleMusicSongs.mergeInto(library, AppleMusicSongs.fromStorageJson(AppleMusicSongs.toStorageJson(page.songs)));
        assertEquals(2, library.size());
        assertFalse(AppleMusicSongs.isApple(library.get(0)));
        assertTrue(AppleMusicSongs.isApple(library.get(1)));

        AppleMusicSongs.mergeInto(library, new ArrayList<SongItem>());
        assertEquals(1, library.size());
        assertEquals(local.file, library.get(0).file);
    }

    @Test
    public void playbackQueueKeepsLocalFiles() {
        File local = new File("/storage/sdcard0/Music/a.mp3");
        File apple = AppleMusicSongs.fileFor("i.abc");
        File other = new File("/storage/sdcard0/Music/b.mp3");
        AppleMusicSongs.PlayQueue chosen = AppleMusicSongs.localQueue(Arrays.asList(apple, local, other), 1);
        assertFalse(chosen.targetIsApple);
        assertEquals(2, chosen.files.size());
        assertEquals(local, chosen.files.get(chosen.index));
        AppleMusicSongs.PlayQueue appleClick = AppleMusicSongs.localQueue(Arrays.asList(local, apple), 1);
        assertTrue(appleClick.targetIsApple);
    }

    @Test
    public void pairingPageUsesMusicKitOnTheLocalHost() {
        String page = AppleMusicPage.html("header.payload.signature");
        assertTrue(page.contains("https://js-cdn.music.apple.com/musickit/v3/musickit.js"));
        assertTrue(page.contains("MusicKit.configure"));
        assertTrue(page.contains("header.payload.signature"));
        assertTrue(page.contains("/api/apple-music/session"));
        assertTrue(page.contains("musicUserToken"));
        assertFalse(page.contains("not configured"));
    }
}
