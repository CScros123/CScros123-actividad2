package es.foco.next.log;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Fase 4: per-day view of the Registro screen (pure JVM). */
public class LogSummaryTest {
    @Test
    public void groupsByDayNewestFirstAndCountsPerApp() {
        List<LogSummary.Count> counts =
                Arrays.asList(
                        new LogSummary.Count("2026-10-30", "BUDGET_LOCK", "feed.app", 1),
                        new LogSummary.Count("2026-10-31", "BUDGET_LOCK", "feed.app", 1),
                        new LogSummary.Count("2026-10-31", "ATTEMPT_WHILE_LOCKED", "feed.app", 3),
                        new LogSummary.Count("2026-10-31", "ATTEMPT_WHILE_LOCKED", "video.app", 5),
                        new LogSummary.Count("2026-10-31", "HOME", "", 2),
                        new LogSummary.Count("2026-10-31", "ALT_OPENED", "reader.app", 1),
                        new LogSummary.Count("2026-10-31", "PRACTICE_STARTED", "", 1),
                        new LogSummary.Count("2026-10-31", "DNS_OFF_LOCK", "", 1),
                        new LogSummary.Count("2026-10-31", "FUTURE_TYPE", "x.y", 9));
        List<LogSummary.Day> days =
                LogSummary.build(
                        counts,
                        Collections.singletonList(
                                new LogSummary.Usage("2026-10-31", "feed.app", 240_000, 320_000)));
        assertEquals(2, days.size());
        LogSummary.Day d = days.get(0);
        assertEquals("2026-10-31", d.day);
        assertEquals(2, d.blocks);
        assertEquals(8, d.attempts);
        assertEquals(2, d.homes);
        assertEquals(1, d.alternatives);
        assertEquals(1, d.practices);
        assertEquals(
                "most attempts+blocks first",
                Arrays.asList("video.app", "feed.app"),
                new ArrayList<>(d.apps.keySet()));
        assertEquals(3, d.apps.get("feed.app").attempts);
        assertEquals(1, d.apps.get("feed.app").blocks);
        assertEquals(240_000, d.apps.get("feed.app").observedMs);
        assertEquals(320_000, d.apps.get("feed.app").usageStatsMs);
        assertEquals(-1, d.apps.get("video.app").usageStatsMs);
        assertEquals("2026-10-30", days.get(1).day);
    }

    @Test
    public void usageOnlyDaysAppearAndZeroRowsAreSkipped() {
        List<LogSummary.Day> days =
                LogSummary.build(
                        null,
                        Arrays.asList(
                                new LogSummary.Usage("2026-10-29", "a.b", 0, -1),
                                new LogSummary.Usage("2026-10-28", "a.b", 1000, -1)));
        assertEquals(1, days.size());
        assertEquals("2026-10-28", days.get(0).day);
        assertFalse(days.get(0).empty());
    }

    @Test
    public void nothingGivesNoDays() {
        assertTrue(LogSummary.build(Collections.emptyList(), Collections.emptyList()).isEmpty());
    }
}
