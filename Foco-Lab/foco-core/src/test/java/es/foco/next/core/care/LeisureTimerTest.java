package es.foco.next.core.care;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LeisureTimerTest {
    @Test
    public void minutosRedondeadosSinSegundos() {
        assertEquals("🦉 5 min", LeisureTimer.text(5 * 60_000L, 0, false));
        assertEquals("🦉 4 min", LeisureTimer.text(3 * 60_000L + 1, 0, false));
        assertEquals("🦉 último minuto", LeisureTimer.text(42_000, 0, false));
        assertEquals("🦉 otros 5 en 30 min", LeisureTimer.text(0, 30 * 60_000L, true));
        assertTrue(LeisureTimer.lastMinute(42_000, false));
        assertFalse(LeisureTimer.lastMinute(0, true));
        for (String t : new String[] {LeisureTimer.text(61_000, 0, false), LeisureTimer.text(0, 90_000, true)})
            assertFalse(t, t.contains(" s") || t.contains(":"));
    }
}
