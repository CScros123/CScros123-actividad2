package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import java.util.function.Predicate;
import org.junit.Test;

public class RuleScheduleTest {
    static final long MIN = 60_000L;

    static ZonedDateTime at(int d, int h, int mi) {
        return ZonedDateTime.of(2026, 11, d, h, mi, 0, 0, ScheduleTest.MAD);
    }

    static BlockRule night(BlockRule.ScheduleMode mode, int limit, String... pkgs) {
        BlockRule r = RuleEngineTest.rule("n", BlockRule.Period.DAILY, limit, pkgs);
        r.scheduleMode = mode;
        r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60));
        return r;
    }

    static RuleEngine.Decision use(
            BlockRule r, RuleState s, String pkg, ZonedDateTime t, long delta) {
        return RuleEngine.decide(List.of(r), s, pkg, t, null, delta);
    }

    @Test
    public void blockInWindowBlocksOnlyInsideAndReportsEnd() {
        BlockRule r = night(BlockRule.ScheduleMode.BLOCK_IN_WINDOW, 60, "feed.app");
        RuleState s = new RuleState();
        RuleEngine.Decision out = use(r, s, "feed.app", at(6, 21, 30), 0);
        assertFalse(out.blocked);
        assertEquals(30 * MIN, out.next); // recheck at 22:00
        RuleEngine.Decision in = use(r, s, "feed.app", at(6, 22, 0), 0);
        assertTrue(in.blocked);
        assertEquals(9 * 60 * MIN, in.resetsIn);
        assertTrue(RuleEngine.active(r, s, at(6, 23, 0)));
        assertFalse(RuleEngine.active(r, s, at(7, 7, 0)));
        assertFalse(use(r, s, "feed.app", at(7, 7, 0), 0).blocked);
    }

    @Test
    public void limitInWindowCountsOnlyInside() {
        BlockRule r = night(BlockRule.ScheduleMode.LIMIT_IN_WINDOW, 10, "feed.app");
        RuleState s = new RuleState();
        use(r, s, "feed.app", at(6, 21, 0), 10 * MIN);
        assertEquals(0, s.of("n").usedMillis);
        assertFalse(use(r, s, "feed.app", at(6, 22, 9), 9 * MIN).blocked);
        RuleEngine.Decision d = use(r, s, "feed.app", at(6, 22, 10), MIN);
        assertTrue(d.blocked);
        assertEquals(
                110 * MIN,
                d.resetsIn); // reinicio diario a medianoche antes que el fin de la ventana
    }

    @Test
    public void pauseStillAppliesToScheduledRule() {
        BlockRule r = night(BlockRule.ScheduleMode.BLOCK_IN_WINDOW, 0, "feed.app");
        RuleState s = new RuleState();
        ZonedDateTime t = at(6, 23, 0);
        assertTrue(RuleEngine.pause(r, s, t.toInstant().toEpochMilli(), "2026-11-06"));
        assertFalse(use(r, s, "feed.app", t.plusMinutes(1), 0).blocked);
        assertTrue(use(r, s, "feed.app", t.plusMinutes(5), 0).blocked);
    }

    @Test
    public void allowlistBlocksEverythingButSelectedAndExempt() {
        BlockRule r = night(BlockRule.ScheduleMode.BLOCK_IN_WINDOW, 0, "kindle.app");
        r.allowlist = true;
        RuleState s = new RuleState();
        Predicate<String> exempt = p -> p.equals("phone.app") || p.equals("es.foco.next");
        ZonedDateTime t = at(6, 23, 0);
        assertTrue(RuleEngine.decide(List.of(r), s, "feed.app", t, exempt, 0).blocked);
        assertTrue(RuleEngine.decide(List.of(r), s, "game.app", t, exempt, 0).blocked);
        assertFalse(RuleEngine.decide(List.of(r), s, "kindle.app", t, exempt, 0).blocked);
        assertFalse(RuleEngine.decide(List.of(r), s, "phone.app", t, exempt, 0).blocked);
        assertFalse(RuleEngine.decide(List.of(r), s, "es.foco.next", t, exempt, 0).blocked);
        assertFalse(RuleEngine.decide(List.of(r), s, "feed.app", at(7, 8, 0), exempt, 0).blocked);
        assertTrue(RuleEngine.active(r, s, t)); // cuenta para modo estricto aunque tenga pocas apps
    }

    @Test
    public void allowlistWithEmptyListStillActive() {
        BlockRule r = night(BlockRule.ScheduleMode.BLOCK_IN_WINDOW, 0);
        r.allowlist = true;
        assertTrue(RuleEngine.active(r, new RuleState(), at(6, 23, 0)));
        assertTrue(
                RuleEngine.decide(
                                List.of(r),
                                new RuleState(),
                                "feed.app",
                                at(6, 23, 0),
                                p -> false,
                                0)
                        .blocked);
    }

    @Test
    public void chainedWindowsEndTogether() {
        BlockRule r = night(BlockRule.ScheduleMode.BLOCK_IN_WINDOW, 0, "feed.app");
        r.schedules.clear();
        r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 20 * 60, 22 * 60));
        r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 22 * 60, 23 * 60));
        assertEquals(3 * 60 * MIN, RuleEngine.windowEndsIn(r, at(6, 20, 0)));
        assertEquals(2 * 60 * MIN, RuleEngine.nextBoundary(r, at(6, 20, 0)));
    }

    @Test
    public void missingScheduleKeepsOldBehaviour() {
        BlockRule r = RuleEngineTest.rule("a", BlockRule.Period.DAILY, 10, "feed.app");
        r.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
        r.clamp();
        assertEquals(BlockRule.ScheduleMode.ALWAYS_LIMIT, r.scheduleMode);
        assertFalse(use(r, new RuleState(), "feed.app", at(6, 23, 0), 0).blocked);
        BlockRule c = night(BlockRule.ScheduleMode.LIMIT_IN_WINDOW, 5, "x");
        c.allowlist = true;
        BlockRule k = c.copy();
        assertEquals(1, k.schedules.size());
        assertNotSame(c.schedules.get(0), k.schedules.get(0));
        assertTrue(k.allowlist);
        assertEquals(c.scheduleMode, k.scheduleMode);
    }
}
