package es.foco.next.log;

import static org.junit.Assert.*;

import java.time.LocalDate;
import org.junit.Test;

/** Fase 4: 90-day retention and export ranges (pure JVM). */
public class LogRetentionTest {
    private static final LocalDate TODAY = LocalDate.parse("2026-10-31");

    @Test
    public void keepsTodayAndEightyNinePreviousDays() {
        assertEquals("2026-08-03", LogRetention.firstKeptDay(TODAY));
        assertFalse(LogRetention.expired("2026-08-03", TODAY));
        assertTrue(LogRetention.expired("2026-08-02", TODAY));
        assertFalse(LogRetention.expired("2026-10-31", TODAY));
    }

    @Test
    public void exportRangesIncludeToday() {
        assertEquals("2026-10-02", LogRetention.firstDay(TODAY, 30));
        assertEquals("2026-08-03", LogRetention.firstDay(TODAY, 90));
        assertEquals("2026-10-31", LogRetention.firstDay(TODAY, 1));
    }

    @Test
    public void leapYearsAndYearBoundaries() {
        assertEquals("2027-12-03", LogRetention.firstKeptDay(LocalDate.parse("2028-03-01")));
        assertEquals("2025-12-03", LogRetention.firstDay(LocalDate.parse("2026-01-01"), 30));
    }

    @Test
    public void invalidRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> LogRetention.firstDay(TODAY, 0));
        assertThrows(IllegalArgumentException.class, () -> LogRetention.firstDay(TODAY, 91));
        assertThrows(IllegalArgumentException.class, () -> LogRetention.firstDay(null, 30));
    }

    @Test
    public void emptyOrMissingDaysCountAsExpired() {
        assertTrue(LogRetention.expired("", TODAY));
        assertTrue(LogRetention.expired(null, TODAY));
    }
}
