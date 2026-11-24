package es.foco.next.runtime;

import static org.junit.Assert.*;

import es.foco.next.runtime.PauseMusic.Action;
import es.foco.next.runtime.PauseMusic.Choice;
import org.junit.Test;

public class PauseMusicTest {
    @Test
    public void parseDefaultsToLofi() {
        assertEquals(Choice.LOFI, PauseMusic.parse(null));
        assertEquals(Choice.LOFI, PauseMusic.parse("x"));
        assertEquals(Choice.SPOTIFY, PauseMusic.parse("spotify"));
    }

    @Test
    public void planFollowsChoice() {
        assertEquals(Action.LOFI, PauseMusic.plan(Choice.LOFI, null, true));
        assertEquals(Action.NONE, PauseMusic.plan(Choice.NONE, null, false));
        assertEquals(Action.SEARCH, PauseMusic.plan(Choice.SPOTIFY, "", true));
        assertEquals(
                Action.OPEN_LINK, PauseMusic.plan(Choice.SPOTIFY, "spotify:playlist:abc", true));
        assertEquals(
                Action.OPEN_LINK,
                PauseMusic.plan(Choice.APPLE, "https://music.apple.com/es/playlist/x", true));
        assertEquals(
                Action.SEARCH,
                PauseMusic.plan(Choice.APPLE, "https://open.spotify.com/playlist/x", true));
    }

    @Test
    public void notInstalledFallsBackToLofi() {
        assertEquals(Action.LOFI, PauseMusic.plan(Choice.SPOTIFY, "spotify:playlist:abc", false));
        assertEquals(Action.LOFI, PauseMusic.plan(Choice.APPLE, null, false));
    }

    @Test
    public void streamingDoesNotPauseOthersAndIsAllowed() {
        assertTrue(PauseMusic.pausesOthers(Choice.LOFI));
        assertTrue(PauseMusic.pausesOthers(Choice.NONE));
        assertFalse(PauseMusic.pausesOthers(Choice.SPOTIFY));
        assertFalse(PauseMusic.pausesOthers(Choice.APPLE));
        assertTrue(PauseMusic.allowed(Choice.SPOTIFY, "com.spotify.music"));
        assertFalse(PauseMusic.allowed(Choice.LOFI, "com.spotify.music"));
        assertFalse(PauseMusic.allowed(Choice.SPOTIFY, "com.apple.android.music"));
    }
}
