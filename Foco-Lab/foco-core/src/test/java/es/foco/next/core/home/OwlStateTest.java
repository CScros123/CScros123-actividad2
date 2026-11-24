package es.foco.next.core.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import es.foco.next.core.home.OwlState.Pose;
import es.foco.next.core.home.OwlState.Recent;
import org.junit.Test;

public class OwlStateTest {
    private static final long NOW = 1_000_000_000L;

    @Test
    public void nightWinsOverEverything() {
        assertEquals(Pose.SLEEPING, OwlState.resolve(true, true, Recent.LECTURA, NOW - 1000, false, NOW, Pose.STRETCH));
    }

    @Test
    public void musicWinsOverRecentApp() {
        assertEquals(Pose.LISTENING_MUSIC, OwlState.resolve(false, true, Recent.OCIO, NOW - 1000, true, NOW, Pose.CALM));
    }

    @Test
    public void readingLastsFourMinutes() {
        assertEquals(Pose.READING, OwlState.resolve(false, false, Recent.LECTURA, NOW - 3 * 60_000, false, NOW, Pose.CURIOUS));
        assertEquals(Pose.CURIOUS, OwlState.resolve(false, false, Recent.LECTURA, NOW - 4 * 60_000, false, NOW, Pose.CURIOUS));
    }

    @Test
    public void backOnlyWhenLeisureWentOver() {
        assertEquals(Pose.ANGRY_BACK, OwlState.resolve(false, false, Recent.OCIO, NOW - 60_000, true, NOW, Pose.CALM));
        assertEquals(Pose.CALM, OwlState.resolve(false, false, Recent.OCIO, NOW - 60_000, false, NOW, Pose.CALM));
    }

    @Test
    public void nonIdleFallbackIsCalm() {
        assertEquals(Pose.CALM, OwlState.resolve(false, false, Recent.NONE, 0, false, NOW, Pose.READING));
    }

    @Test
    public void randomCoversAllIdlePoses() {
        assertEquals(Pose.CALM, OwlState.randomIdle(0));
        assertEquals(Pose.STRETCH, OwlState.randomIdle(.5));
        assertEquals(Pose.CURIOUS, OwlState.randomIdle(.7));
        assertEquals(Pose.READING_IDLE, OwlState.randomIdle(.99));
    }

    @Test
    public void readingApps() {
        assertTrue(OwlState.reading("com.amazon.kindle", ""));
        assertTrue(OwlState.reading("com.google.android.apps.books", "Play Libros"));
        assertTrue(OwlState.reading("x.y", "El Libro Total"));
        assertFalse(OwlState.reading("com.instagram.android", "Instagram"));
    }

    @Test
    public void nightNoticeCooldown() {
        assertTrue(OwlState.nightNoticeDue(0, NOW));
        assertFalse(OwlState.nightNoticeDue(NOW - 44 * 60_000, NOW));
        assertTrue(OwlState.nightNoticeDue(NOW - 45 * 60_000, NOW));
        assertEquals(OwlState.NIGHT_TEXTS[0], OwlState.nightText(3));
    }
}
