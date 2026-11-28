package es.foco.next.core;

import static es.foco.next.core.ScreenTime.*;
import static org.junit.Assert.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.Test;

public class ScreenTimeTest {
    private static final ZoneId Z = ZoneId.of("Europe/Madrid");
    private static final LocalDate D = LocalDate.of(2026, 3, 10);

    private static long at(int h, int m) {
        return D.atStartOfDay(Z).plusHours(h).plusMinutes(m).toInstant().toEpochMilli();
    }

    private static Mark r(long t, String p) {
        return new Mark(t, p, "A", RESUME);
    }

    private static Mark p(long t, String p) {
        return new Mark(t, p, "A", PAUSE);
    }

    private static long dayEnd() {
        return D.plusDays(1).atStartOfDay(Z).toInstant().toEpochMilli();
    }

    @Test
    public void resumePauseCounts() {
        Map<String, Map<String, Long>> m =
                perDay(
                        Arrays.asList(
                                r(at(9, 0), "a"),
                                p(at(9, 10), "a"),
                                r(at(10, 0), "b"),
                                p(at(10, 5), "b")),
                        at(0, 0),
                        dayEnd(),
                        Z);
        assertEquals(10 * MIN, (long) m.get(D.toString()).get("a"));
        assertEquals(5 * MIN, (long) m.get(D.toString()).get("b"));
    }

    @Test
    public void missingPauseClosedByNextApp() {
        Map<String, Long> d =
                perDay(
                                Arrays.asList(
                                        r(at(9, 0), "a"), r(at(9, 20), "b"), p(at(9, 30), "b")),
                                at(0, 0),
                                dayEnd(),
                                Z)
                        .get(D.toString());
        assertEquals(20 * MIN, (long) d.get("a"));
        assertEquals(10 * MIN, (long) d.get("b"));
    }

    @Test
    public void overlappingResumesNotDoubled() {
        Map<String, Long> d =
                perDay(
                                Arrays.asList(
                                        r(at(9, 0), "a"), r(at(9, 5), "a"), p(at(9, 10), "a")),
                                at(0, 0),
                                dayEnd(),
                                Z)
                        .get(D.toString());
        assertEquals(10 * MIN, (long) d.get("a"));
    }

    @Test
    public void intraAppNavigationKeepsOpen() {
        List<Mark> ms =
                Arrays.asList(
                        new Mark(at(9, 0), "a", "A1", RESUME),
                        new Mark(at(9, 5), "a", "A2", RESUME),
                        new Mark(at(9, 6), "a", "A1", PAUSE),
                        new Mark(at(9, 15), "a", "A2", PAUSE));
        assertEquals(15 * MIN, (long) perDay(ms, at(0, 0), dayEnd(), Z).get(D.toString()).get("a"));
    }

    @Test
    public void strayPauseAndCloseAll() {
        Map<String, Long> d =
                perDay(
                                Arrays.asList(
                                        p(at(8, 0), "x"),
                                        r(at(9, 0), "a"),
                                        new Mark(at(9, 4), "", "", CLOSE_ALL),
                                        p(at(9, 30), "a")),
                                at(0, 0),
                                dayEnd(),
                                Z)
                        .get(D.toString());
        assertEquals(4 * MIN, (long) d.get("a"));
        assertNull(d.get("x"));
    }

    @Test
    public void openAtEndIsClipped() {
        Map<String, Long> d =
                perDay(Collections.singletonList(r(at(23, 0), "a")), at(0, 0), at(23, 30), Z)
                        .get(D.toString());
        assertEquals(30 * MIN, (long) d.get("a"));
    }

    @Test
    public void splitsAtLocalMidnight() {
        long next = dayEnd() + 20 * MIN;
        Map<String, Map<String, Long>> m =
                perDay(Arrays.asList(r(at(23, 50), "a"), p(next, "a")), at(0, 0), next + MIN, Z);
        assertEquals(10 * MIN, (long) m.get(D.toString()).get("a"));
        assertEquals(20 * MIN, (long) m.get(D.plusDays(1).toString()).get("a"));
    }

    @Test
    public void weeklySumsAndTotals() {
        Map<String, Map<String, Long>> days = new TreeMap<>();
        days.put(D.toString(), Map.of("a", 10L, "b", 5L));
        days.put(D.minusDays(2).toString(), Map.of("a", 3L));
        List<String> week = days(D, 7);
        assertEquals(7, week.size());
        assertEquals(D.toString(), week.get(6));
        assertArrayEquals(new long[] {0, 0, 0, 0, 3, 0, 15}, totals(days, week));
        assertEquals(13L, (long) sum(days, week).get("a"));
    }

    @Test
    public void topExcludesNoiseAndOwnApp() {
        Map<String, Long> apps =
                Map.of(
                        "com.android.systemui",
                        100L,
                        "com.sec.android.app.launcher",
                        90L,
                        "es.foco.next",
                        80L,
                        "x",
                        10L,
                        "y",
                        20L,
                        "z",
                        0L);
        List<Map.Entry<String, Long>> t = top(apps, Set.of("es.foco.next"), 5);
        assertEquals(2, t.size());
        assertEquals("y", t.get(0).getKey());
        assertEquals("x", t.get(1).getKey());
    }

    @Test
    public void trendRespectsThreshold() {
        Map<String, Long> before =
                Map.of("a", 10 * MIN, "b", 30 * MIN, "c", 10 * MIN, "com.android.systemui", 0L);
        Map<String, Long> after =
                Map.of(
                        "a",
                        40 * MIN,
                        "b",
                        5 * MIN,
                        "c",
                        13 * MIN,
                        "d",
                        6 * MIN,
                        "com.android.systemui",
                        90 * MIN);
        Trend t = trend(before, after, Set.of(), DEFAULT_TREND_MIN, 3);
        assertEquals(2, t.up.size());
        assertEquals("a", t.up.get(0).pkg);
        assertEquals(30 * MIN, t.up.get(0).change());
        assertEquals("d", t.up.get(1).pkg);
        assertEquals(1, t.down.size());
        assertEquals("b", t.down.get(0).pkg);
    }

    @Test
    public void trendLimitsCount() {
        Map<String, Long> after =
                Map.of("a", 10 * MIN, "b", 20 * MIN, "c", 30 * MIN, "d", 40 * MIN);
        Trend t = trend(Map.of(), after, null, MIN, 3);
        assertEquals(3, t.up.size());
        assertEquals("d", t.up.get(0).pkg);
    }

    @Test
    public void perHourSplitsAtHoursAndMidnight() {
        // 20:40–22:10 en «a» y 23:30–00:20 en «b» (cruza a otro día). Mismo total que perDay.
        long next = D.plusDays(1).atStartOfDay(Z).plusMinutes(20).toInstant().toEpochMilli();
        List<Mark> ms =
                Arrays.asList(
                        r(at(20, 40), "a"), p(at(22, 10), "a"), r(at(23, 30), "b"), p(next, "b"));
        Map<String, Map<String, long[]>> h = perHour(ms, at(0, 0), next + MIN, Z);
        long[] a = h.get(D.toString()).get("a");
        assertEquals(20 * MIN, a[20]);
        assertEquals(60 * MIN, a[21]);
        assertEquals(10 * MIN, a[22]);
        assertEquals(0, a[19]);
        assertEquals(30 * MIN, h.get(D.toString()).get("b")[23]);
        assertEquals(20 * MIN, h.get(D.plusDays(1).toString()).get("b")[0]);
        Map<String, Map<String, Long>> d = perDay(ms, at(0, 0), next + MIN, Z);
        assertEquals(90 * MIN, (long) d.get(D.toString()).get("a"));
        assertTrue(perHour(null, 0, 1, Z).isEmpty());
    }
}
