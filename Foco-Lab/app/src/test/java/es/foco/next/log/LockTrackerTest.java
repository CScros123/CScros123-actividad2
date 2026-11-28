package es.foco.next.log;

import static org.junit.Assert.*;

import java.time.ZoneId;
import org.junit.Test;

/**
 * Fase 4: engine decisions → BUDGET_LOCK / ATTEMPT_WHILE_LOCKED (pure JVM, FocusEngine untouched).
 */
public class LockTrackerTest {
    private static final long B = 300_000, HOUR = 3_600_000;
    private final LockTracker t = new LockTracker();

    private BlockEvent next(String pkg, boolean blocked, long remaining, long left, boolean shown) {
        return t.next(pkg, blocked, remaining, left, shown, B, 0, ZoneId.of("UTC"));
    }

    @Test
    public void budgetCrossedWhileInsideTheAppIsOneLockNotAnAttempt() {
        assertNull(next("feed.app", false, B, HOUR, false));
        assertNull(next("feed.app", false, 60_000, HOUR - 240_000, false));
        BlockEvent e = next("feed.app", true, 0, HOUR - 300_000, false);
        assertEquals(BlockEvent.Type.BUDGET_LOCK, e.type);
        assertEquals("feed.app", e.pkg);
        assertEquals(B, e.budgetUsedMs);
        assertEquals(HOUR - 300_000, e.windowLeftMs);
        assertEquals(HOUR - 300_000, e.lockMs);
        assertNull(
                "minute refresh is not an attempt",
                next("feed.app", true, 0, HOUR - 360_000, true));
    }

    @Test
    public void lockAppliedAfterLeavingIsAttributedToTheConsumingApp() {
        next("feed.app", false, 10_000, HOUR - 290_000, false);
        BlockEvent e = next("com.android.settings", false, 0, HOUR - 300_000, false);
        assertEquals(BlockEvent.Type.BUDGET_LOCK, e.type);
        assertEquals("feed.app", e.pkg);
    }

    @Test
    public void reopeningOrSwitchingLockedAppsAreAttempts() {
        next("feed.app", false, 10_000, HOUR, false);
        next("feed.app", true, 0, HOUR - 300_000, false);
        assertNull(next("com.android.settings", false, 0, HOUR - 310_000, true));
        BlockEvent again = next("feed.app", true, 0, HOUR - 320_000, false);
        assertEquals(BlockEvent.Type.ATTEMPT_WHILE_LOCKED, again.type);
        assertEquals("feed.app", again.pkg);
        BlockEvent other = next("video.app", true, 0, HOUR - 330_000, true);
        assertEquals(BlockEvent.Type.ATTEMPT_WHILE_LOCKED, other.type);
        assertEquals("video.app", other.pkg);
    }

    @Test
    public void afterRestartAnExistingLockIsLoggedAsAttemptNotNewLock() {
        BlockEvent e = next("feed.app", true, 0, HOUR / 2, false);
        assertEquals(BlockEvent.Type.ATTEMPT_WHILE_LOCKED, e.type);
    }

    @Test
    public void newWindowCanLockAgain() {
        next("feed.app", false, 1, HOUR, false);
        assertEquals(
                BlockEvent.Type.BUDGET_LOCK, next("feed.app", true, 0, HOUR - 300_000, false).type);
        assertNull(next("com.android.settings", false, B, 0, true));
        next("feed.app", false, 1, HOUR, false);
        assertEquals(
                BlockEvent.Type.BUDGET_LOCK, next("feed.app", true, 0, HOUR - 300_000, false).type);
    }

    @Test
    public void expiredWindowNeverLogs() {
        next("feed.app", false, 1, 1, false);
        assertNull(next("feed.app", false, 0, 0, false));
    }
}
