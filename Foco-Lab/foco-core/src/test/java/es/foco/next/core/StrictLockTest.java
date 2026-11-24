package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.*;
import org.junit.Test;

public class StrictLockTest {
    private static final long H = StrictLock.HOUR, W0 = 1_700_000_000_000L;

    @Test
    public void timedLockExpiresWithMonotonicTime() {
        StrictLock l = new StrictLock();
        l.lockFor(H, W0, 10_000, 5);
        assertTrue(l.locked());
        assertFalse(l.release());
        l.observe(W0 + H / 2, 10_000 + H / 2, 5);
        assertTrue(l.locked());
        assertEquals(H / 2, l.remaining());
        l.observe(W0 + H, 10_000 + H, 5);
        assertFalse(l.locked());
        assertTrue(l.release());
        assertFalse(l.active);
    }

    @Test
    public void wallClockBackwardsKeepsLock() {
        StrictLock l = new StrictLock();
        l.lockFor(4 * H, W0, 1_000, 1);
        l.observe(W0 - 10 * H, 1_000 + H, 1);
        assertTrue(l.locked());
        assertEquals(3 * H, l.remaining()); // misma sesión: cuenta el monótono
        l.observe(W0 - 9 * H, 500, 2);
        assertTrue(l.locked());
        assertEquals(3 * H, l.remaining()); // reinicio con reloj atrasado: no avanza
    }

    @Test
    public void wallClockForwardWithoutRebootDoesNotUnlock() {
        StrictLock l = new StrictLock();
        l.lockFor(8 * H, W0, 1_000, 3);
        l.observe(W0 + 100 * H, 1_000 + 60_000, 3);
        assertTrue(l.locked());
        assertEquals(8 * H - 60_000, l.remaining());
    }

    @Test
    public void rebootCountsWallTimeForward() {
        StrictLock l = new StrictLock();
        l.lockFor(H, W0, 50_000, 3);
        l.observe(W0 + 2 * H, 20_000, 4);
        assertFalse(l.locked());
    }

    @Test
    public void unknownBootUsesSmallerDelta() {
        StrictLock l = new StrictLock();
        l.lockFor(H, W0, 1_000, -1);
        l.observe(W0 + 5 * H, 1_000 + 60_000, -1);
        assertTrue(l.locked());
        assertEquals(H - 60_000, l.remaining());
    }

    @Test
    public void alwaysNeeds24hRequest() {
        StrictLock l = new StrictLock();
        l.lockAlways(W0, 1_000, 1);
        assertTrue(l.locked());
        assertEquals(-1, l.remaining());
        l.observe(W0 + 1000 * H, 1_000 + 1000 * H, 1);
        assertTrue(l.locked());
        assertTrue(l.requestUnlock(W0 + 1000 * H, 1_000 + 1000 * H, 1));
        assertFalse(l.requestUnlock(W0 + 1000 * H, 1_000 + 1000 * H, 1));
        assertTrue(l.requested());
        l.observe(W0 + 1023 * H, 1_000 + 1023 * H, 1);
        assertTrue(l.locked());
        assertEquals(H, l.remaining());
        assertTrue(l.cancelRequest());
        assertFalse(l.requested());
        l.observe(W0 + 1030 * H, 1_000 + 1030 * H, 1);
        assertTrue(l.locked());
        l.requestUnlock(W0 + 1030 * H, 1_000 + 1030 * H, 1);
        l.observe(W0 + 1054 * H, 1_000 + 1054 * H, 1);
        assertFalse(l.locked());
        assertTrue(l.release());
    }

    @Test
    public void longerLockWins() {
        StrictLock l = new StrictLock();
        l.lockFor(8 * H, W0, 1_000, 1);
        l.lockFor(H, W0, 1_000, 1);
        assertEquals(8 * H, l.remaining());
        l.lockAlways(W0, 1_000, 1);
        l.lockFor(H, W0, 1_000, 1);
        assertTrue(l.always);
        assertEquals(-1, l.remaining());
    }

    @Test
    public void untilSeven() {
        ZoneId z = ZoneId.of("Europe/Madrid");
        assertEquals(9 * H, StrictLock.untilSeven(ZonedDateTime.of(2026, 11, 6, 22, 0, 0, 0, z)));
        assertEquals(2 * H, StrictLock.untilSeven(ZonedDateTime.of(2026, 11, 6, 5, 0, 0, 0, z)));
        assertEquals(24 * H, StrictLock.untilSeven(ZonedDateTime.of(2026, 11, 6, 7, 0, 0, 0, z)));
        assertEquals(
                0,
                StrictLock.until(
                        ZonedDateTime.of(2026, 11, 6, 7, 0, 0, 0, z),
                        LocalDateTime.of(2026, 11, 5, 7, 0)));
        assertEquals(
                3 * H,
                StrictLock.until(
                        ZonedDateTime.of(2026, 11, 6, 7, 0, 0, 0, z),
                        LocalDateTime.of(2026, 11, 6, 10, 0)));
    }

    @Test
    public void encodeRoundTrip() {
        StrictLock l = new StrictLock();
        l.lockAlways(W0, 1_000, 7);
        l.requestUnlock(W0 + H, 1_000 + H, 7);
        StrictLock r = StrictLock.decode(l.encode());
        assertEquals(l.encode(), r.encode());
        assertTrue(r.locked());
        assertTrue(r.requested());
        assertFalse(StrictLock.decode(null).active);
        assertFalse(StrictLock.decode("basura").active);
        assertFalse(StrictLock.decode("1;x;y;z;1;2;3;4").active);
    }
}
