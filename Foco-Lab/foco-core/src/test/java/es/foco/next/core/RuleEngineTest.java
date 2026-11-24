package es.foco.next.core;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class RuleEngineTest {
    static final long MIN = 60_000L, T0 = 1_800_000_000_000L;
    static final String DAY = "2026-11-06", NEXT_DAY = "2026-11-07";

    static BlockRule rule(String id, BlockRule.Period p, int limit, String... pkgs) {
        BlockRule r = new BlockRule();
        r.id = id;
        r.name = "Regla " + id;
        r.period = p;
        r.limitMinutes = limit;
        r.windowMinutes = 60;
        r.maxPauses = 2;
        r.pauseMinutes = 5;
        r.packages.addAll(Arrays.asList(pkgs));
        return r;
    }

    static RuleEngine.Decision use(
            List<BlockRule> rules, RuleState s, String pkg, long wall, long delta) {
        return RuleEngine.decide(rules, s, pkg, wall, DAY, 10 * 60 * MIN, delta);
    }

    @Test
    public void limitReachedBlocksOnlyThatRulesApps() {
        BlockRule a = rule("a", BlockRule.Period.DAILY, 10, "feed.app"),
                b = rule("b", BlockRule.Period.DAILY, 10, "game.app");
        List<BlockRule> rules = List.of(a, b);
        RuleState s = new RuleState();
        assertFalse(use(rules, s, "feed.app", T0, 9 * MIN).blocked);
        RuleEngine.Decision d = use(rules, s, "feed.app", T0 + MIN, MIN);
        assertTrue(d.blocked);
        assertSame(a, d.rule);
        assertEquals(0, d.remaining);
        assertFalse(use(rules, s, "game.app", T0 + MIN, 0).blocked);
        assertFalse(use(rules, s, "other.app", T0 + MIN, 0).blocked);
        assertTrue(RuleEngine.active(a, s, T0 + MIN, DAY));
        assertFalse(RuleEngine.active(b, s, T0 + MIN, DAY));
    }

    @Test
    public void usageOnlyCountsForForegroundApp() {
        BlockRule a = rule("a", BlockRule.Period.DAILY, 10, "feed.app");
        RuleState s = new RuleState();
        use(List.of(a), s, "other.app", T0, 20 * MIN);
        assertEquals(0, s.of("a").usedMillis);
        assertEquals(10 * MIN - 3 * MIN, use(List.of(a), s, "feed.app", T0, 3 * MIN).remaining);
    }

    @Test
    public void windowResetsAfterPeriod() {
        BlockRule a = rule("a", BlockRule.Period.WINDOW, 5, "feed.app");
        List<BlockRule> rules = List.of(a);
        RuleState s = new RuleState();
        use(rules, s, "feed.app", T0, 0); // la ventana empieza al abrir
        RuleEngine.Decision d = use(rules, s, "feed.app", T0 + 5 * MIN, 5 * MIN);
        assertTrue(d.blocked);
        assertEquals(55 * MIN, d.resetsIn);
        assertTrue(use(rules, s, "feed.app", T0 + 59 * MIN, 0).blocked);
        assertFalse(use(rules, s, "feed.app", T0 + 60 * MIN, 0).blocked);
        assertEquals(0, s.of("a").usedMillis);
    }

    @Test
    public void clockMovedBackResetsWindowInsteadOfExtendingIt() {
        BlockRule a = rule("a", BlockRule.Period.WINDOW, 5, "feed.app");
        RuleState s = new RuleState();
        assertTrue(use(List.of(a), s, "feed.app", T0, 6 * MIN).blocked);
        assertFalse(use(List.of(a), s, "feed.app", T0 - 10 * MIN, 0).blocked);
    }

    @Test
    public void dailyRolloverResetsUsageAndPauses() {
        BlockRule a = rule("a", BlockRule.Period.DAILY, 5, "feed.app");
        List<BlockRule> rules = List.of(a);
        RuleState s = new RuleState();
        RuleEngine.Decision d = use(rules, s, "feed.app", T0, 5 * MIN);
        assertTrue(d.blocked);
        assertEquals(14 * 60 * MIN, d.resetsIn);
        assertTrue(RuleEngine.pause(a, s, T0, DAY));
        assertFalse(RuleEngine.decide(rules, s, "feed.app", T0 + 20 * MIN, NEXT_DAY, 0, 0).blocked);
        assertEquals(0, s.of("a").usedMillis);
        assertEquals(0, s.of("a").pausesUsed);
    }

    @Test
    public void pauseLiftsBlockForNMinutesAndCounts() {
        BlockRule a = rule("a", BlockRule.Period.DAILY, 5, "feed.app");
        List<BlockRule> rules = List.of(a);
        RuleState s = new RuleState();
        use(rules, s, "feed.app", T0, 5 * MIN);
        assertTrue(RuleEngine.pause(a, s, T0, DAY));
        assertEquals(1, s.of("a").pausesUsed);
        RuleEngine.Decision d = use(rules, s, "feed.app", T0 + 2 * MIN, 2 * MIN);
        assertFalse(d.blocked);
        assertEquals(3 * MIN, d.next);
        assertFalse(RuleEngine.pause(a, s, T0 + 2 * MIN, DAY)); // ya en pausa
        assertTrue(use(rules, s, "feed.app", T0 + 5 * MIN, 0).blocked);
        assertEquals(1, use(rules, s, "feed.app", T0 + 5 * MIN, 0).pausesLeft);
    }

    @Test
    public void maxPausesRespected() {
        BlockRule a = rule("a", BlockRule.Period.DAILY, 5, "feed.app");
        a.maxPauses = 1;
        RuleState s = new RuleState();
        use(List.of(a), s, "feed.app", T0, 5 * MIN);
        assertTrue(RuleEngine.pause(a, s, T0, DAY));
        assertFalse(RuleEngine.pause(a, s, T0 + 6 * MIN, DAY));
        assertTrue(use(List.of(a), s, "feed.app", T0 + 6 * MIN, 0).blocked);
        a.maxPauses = 0;
        RuleState fresh = new RuleState();
        use(List.of(a), fresh, "feed.app", T0, 5 * MIN);
        assertFalse(RuleEngine.pause(a, fresh, T0, DAY));
    }

    @Test
    public void disabledRuleIgnored() {
        BlockRule a = rule("a", BlockRule.Period.DAILY, 0, "feed.app");
        a.enabled = false;
        RuleState s = new RuleState();
        assertFalse(use(List.of(a), s, "feed.app", T0, MIN).blocked);
        assertFalse(RuleEngine.anyActive(List.of(a), s, T0, DAY));
        a.enabled = true;
        assertTrue(use(List.of(a), s, "feed.app", T0, 0).blocked);
    }

    @Test
    public void overlappingRulesBlockIfAnyBlocks() {
        BlockRule wide = rule("w", BlockRule.Period.DAILY, 60, "feed.app", "game.app"),
                tight = rule("t", BlockRule.Period.WINDOW, 2, "feed.app");
        List<BlockRule> rules = List.of(wide, tight);
        RuleState s = new RuleState();
        RuleEngine.Decision d = use(rules, s, "feed.app", T0, 2 * MIN);
        assertTrue(d.blocked);
        assertSame(tight, d.rule);
        assertEquals(2 * MIN, s.of("w").usedMillis); // el uso cuenta en ambos
        assertFalse(use(rules, s, "game.app", T0, 0).blocked);
        assertTrue(RuleEngine.pause(tight, s, T0, DAY));
        assertFalse(use(rules, s, "feed.app", T0 + MIN, MIN).blocked);
    }

    @Test
    public void essentialsAreFilteredAtCallSite() {
        AppConfig c = new AppConfig();
        assertTrue(FocusEngine.essential(c, "com.whatsapp"));
        // El servicio no consulta el motor para esenciales; aquí comprobamos que un paquete vacío
        // nunca bloquea.
        BlockRule a = rule("a", BlockRule.Period.DAILY, 0, "feed.app");
        assertFalse(use(List.of(a), new RuleState(), "", T0, MIN).blocked);
    }

    @Test
    public void zeroLimitIsTotalBlockWithoutBusyLoop() {
        BlockRule a = rule("a", BlockRule.Period.WINDOW, 0, "feed.app");
        RuleEngine.Decision d = use(List.of(a), new RuleState(), "feed.app", T0, 0);
        assertTrue(d.blocked);
        assertEquals(0, d.next);
    }

    @Test
    public void autoAddOnlyToOptedInRules() {
        BlockRule a = rule("a", BlockRule.Period.DAILY, 5),
                b = rule("b", BlockRule.Period.DAILY, 5);
        a.autoAddNew = true;
        assertEquals(List.of("Regla a"), RuleEngine.addInstalled(List.of(a, b), "new.app"));
        assertTrue(a.packages.contains("new.app"));
        assertFalse(b.packages.contains("new.app"));
    }

    @Test
    public void clampKeepsValuesInRange() {
        BlockRule r = new BlockRule();
        r.name = " ";
        r.maxPauses = 9;
        r.pauseMinutes = 0;
        r.limitMinutes = -3;
        r.clamp();
        assertEquals("Bloqueo", r.name);
        assertEquals(5, r.maxPauses);
        assertEquals(1, r.pauseMinutes);
        assertEquals(0, r.limitMinutes);
    }
}
