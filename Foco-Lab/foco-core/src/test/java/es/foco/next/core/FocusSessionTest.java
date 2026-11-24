package es.foco.next.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class FocusSessionTest {
    static final long MIN = 60_000L, T0 = 1_800_000_000_000L;

    @Test
    public void quickBlockEndsAtUntil() {
        FocusSession q = FocusSession.quick(T0, 30);
        assertTrue(q.focusing(T0));
        assertTrue(q.focusing(T0 + 30 * MIN - 1));
        assertFalse(q.focusing(T0 + 30 * MIN));
        assertEquals(T0 + 30 * MIN, q.phase(T0 + 5 * MIN).endsAt);
        assertEquals("Bloqueo rápido", q.title(T0));
        assertTrue(q.phase(T0 + 31 * MIN).done);
    }

    @Test
    public void pomodoroPhases() {
        FocusSession p = FocusSession.pomodoro(T0, 25, 5, 4);
        assertEquals(T0 + (4 * 25 + 3 * 5) * MIN, p.end());
        FocusSession.Phase f = p.phase(T0 + 10 * MIN);
        assertTrue(f.focus);
        assertEquals(1, f.cycle);
        assertEquals(4, f.cycles);
        assertEquals(T0 + 25 * MIN, f.endsAt);
        FocusSession.Phase b = p.phase(T0 + 25 * MIN);
        assertFalse(b.focus);
        assertFalse(b.done);
        assertEquals(1, b.cycle);
        assertEquals(T0 + 30 * MIN, b.endsAt);
        assertFalse(p.focusing(T0 + 29 * MIN));
        FocusSession.Phase f2 = p.phase(T0 + 30 * MIN);
        assertTrue(f2.focus);
        assertEquals(2, f2.cycle);
        assertEquals(T0 + 55 * MIN, f2.endsAt);
        assertEquals("Pomodoro 2/4 · enfoque", p.title(T0 + 31 * MIN));
        assertEquals("Pomodoro 2/4 · descanso", p.title(T0 + 56 * MIN));
        FocusSession.Phase last = p.phase(T0 + (3 * 30 + 24) * MIN);
        assertTrue(last.focus);
        assertEquals(4, last.cycle);
        assertEquals(p.end(), last.endsAt); // sin descanso al final
        assertTrue(p.phase(p.end()).done);
        assertFalse(p.focusing(p.end()));
    }

    @Test
    public void clockBackwardsStaysConservativeButBounded() {
        FocusSession p = FocusSession.pomodoro(T0, 25, 5, 2);
        FocusSession.Phase f = p.phase(T0 - 10 * MIN);
        assertTrue(f.focus);
        assertEquals(1, f.cycle);
        assertEquals(T0 + 25 * MIN, f.endsAt);
        assertTrue(p.phase(T0 - 25 * 60 * MIN).done);
    }

    @Test
    public void clampKeepsValuesSane() {
        FocusSession p = FocusSession.pomodoro(T0, 0, 0, 99);
        assertEquals(1, p.focusMinutes);
        assertEquals(1, p.breakMinutes);
        assertEquals(12, p.cycles);
        assertEquals(p.end(), p.until);
    }
}
