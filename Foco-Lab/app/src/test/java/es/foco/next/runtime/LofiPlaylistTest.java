package es.foco.next.runtime;

import static org.junit.Assert.*;

import org.junit.Test;

public class LofiPlaylistTest {
    @Test
    public void alternatesSlotsAndRotatesWithinEach() {
        LofiPlaylist p = new LofiPlaylist(new int[][] {{0, 2, 4}, {1, 3}});
        int slot = 0, last = -1;
        StringBuilder seen = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            int t = p.current(slot);
            assertNotEquals(last, t);
            seen.append(t);
            last = t;
            slot = p.next(slot);
        }
        // Slots alternate (even tracks, odd tracks) and each slot rotates through all its tracks.
        assertEquals("0123410321", seen.toString());
    }

    @Test
    public void singleTrackSlotsNeverRepeatBackToBack() {
        LofiPlaylist p = new LofiPlaylist(new int[][] {{5}, {6}});
        assertEquals(5, p.current(0));
        assertEquals(1, p.next(0));
        assertEquals(6, p.current(1));
        assertEquals(0, p.next(1));
        assertEquals(5, p.current(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsEmptySlot() {
        new LofiPlaylist(new int[][] {{}, {1}});
    }

    @Test
    public void restoredPositionAndAdvanceGiveADifferentTrackEachBlock() {
        LofiPlaylist p = new LofiPlaylist(new int[][] {{0, 2, 4}, {1, 3}});
        p.restore(0, 0);
        p.advance(0);
        assertEquals(2, p.current(0));
        LofiPlaylist again = new LofiPlaylist(new int[][] {{0, 2, 4}, {1, 3}});
        again.restore(0, p.position(0));
        again.advance(0);
        assertEquals(4, again.current(0));
    }
}
