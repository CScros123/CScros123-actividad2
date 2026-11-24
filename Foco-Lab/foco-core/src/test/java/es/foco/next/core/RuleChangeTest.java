package es.foco.next.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class RuleChangeTest {
    private static BlockRule rule() {
        BlockRule r = new BlockRule();
        r.id = "a";
        r.name = "Redes";
        r.packages.add("com.instagram.android");
        r.limitMinutes = 30;
        r.maxPauses = 1;
        r.pauseMinutes = 5;
        return r;
    }

    @Test
    public void addAndDelete() {
        assertTrue(RuleChange.isStricter(null, rule()));
        assertFalse(RuleChange.isStricter(rule(), null));
        BlockRule off = rule();
        off.enabled = false;
        assertTrue(RuleChange.isStricter(off, null));
    }

    @Test
    public void disable() {
        BlockRule n = rule();
        n.enabled = false;
        assertFalse(RuleChange.isStricter(rule(), n));
        BlockRule off = rule();
        off.enabled = false;
        assertTrue(RuleChange.isStricter(off, rule()));
    }

    @Test
    public void apps() {
        BlockRule n = rule();
        n.packages.add("com.tiktok");
        assertTrue(RuleChange.isStricter(rule(), n));
        n.packages.remove("com.instagram.android");
        assertFalse(RuleChange.isStricter(rule(), n));
    }

    @Test
    public void allowlistInverts() {
        BlockRule o = rule();
        o.allowlist = true;
        BlockRule n = o.copy();
        n.packages.add("x");
        assertFalse(RuleChange.isStricter(o, n));
        BlockRule m = o.copy();
        m.packages.clear();
        assertTrue(RuleChange.isStricter(o, m));
        BlockRule t = rule();
        t.allowlist = true;
        assertFalse(RuleChange.isStricter(rule(), t));
    }

    @Test
    public void limits() {
        BlockRule n = rule();
        n.limitMinutes = 10;
        assertTrue(RuleChange.isStricter(rule(), n));
        n.limitMinutes = 31;
        assertFalse(RuleChange.isStricter(rule(), n));
        BlockRule p = rule();
        p.maxPauses = 2;
        assertFalse(RuleChange.isStricter(rule(), p));
        p.maxPauses = 0;
        assertTrue(RuleChange.isStricter(rule(), p));
        p.pauseMinutes = 10;
        assertFalse(RuleChange.isStricter(rule(), p));
    }

    @Test
    public void namesAreFree() {
        BlockRule n = rule();
        n.name = "Otro";
        assertTrue(RuleChange.isStricter(rule(), n));
    }

    @Test
    public void protections() {
        BlockRule o = rule();
        o.blockPurchases = true;
        o.sites.add("youtube.com");
        BlockRule n = o.copy();
        n.blockPurchases = false;
        assertFalse(RuleChange.isStricter(o, n));
        BlockRule s = o.copy();
        s.sites.clear();
        assertFalse(RuleChange.isStricter(o, s));
        BlockRule k = o.copy();
        k.keywords.add("shorts");
        k.blockUnsupportedBrowsers = true;
        assertTrue(RuleChange.isStricter(o, k));
    }

    @Test
    public void sections() {
        BlockRule o = rule();
        o.sections.add("youtube_shorts");
        o.sections.add("instagram_reels");
        BlockRule more = o.copy();
        more.sections.add("facebook_reels");
        assertTrue(RuleChange.isStricter(o, more));
        BlockRule fewer = o.copy();
        fewer.sections.remove("instagram_reels");
        assertFalse(RuleChange.isStricter(o, fewer));
        assertEquals("no se pueden quitar secciones bloqueadas", RuleChange.reason(o, fewer));
        BlockRule none = o.copy();
        none.sections.clear();
        assertFalse(RuleChange.isStricter(o, none));
        BlockRule add = rule();
        BlockRule withSec = add.copy();
        withSec.sections.add("youtube_shorts");
        assertTrue(RuleChange.isStricter(add, withSec));
    }

    @Test
    public void schedules() {
        BlockRule o = rule();
        o.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
        o.schedules.add(Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60));
        BlockRule wider = o.copy();
        wider.schedules.clear();
        wider.schedules.add(Schedule.of(Schedule.ALL_DAYS, 21 * 60, 8 * 60));
        assertTrue(RuleChange.isStricter(o, wider));
        BlockRule shorter = o.copy();
        shorter.schedules.clear();
        shorter.schedules.add(Schedule.of(Schedule.ALL_DAYS, 23 * 60, 7 * 60));
        assertFalse(RuleChange.isStricter(o, shorter));
        BlockRule fewerDays = o.copy();
        fewerDays.schedules.get(0).days = 0x1F;
        assertFalse(RuleChange.isStricter(o, fewerDays));
        BlockRule split = o.copy();
        split.schedules.clear();
        split.schedules.add(Schedule.of(Schedule.ALL_DAYS, 22 * 60, 0));
        split.schedules.add(Schedule.of(Schedule.ALL_DAYS, 0, 7 * 60));
        assertTrue(RuleChange.isStricter(o, split));
        BlockRule always = rule();
        BlockRule addSched = always.copy();
        addSched.scheduleMode = BlockRule.ScheduleMode.LIMIT_IN_WINDOW;
        addSched.schedules.add(Schedule.of(Schedule.ALL_DAYS, 9 * 60, 17 * 60));
        assertFalse(RuleChange.isStricter(always, addSched));
        assertTrue(RuleChange.isStricter(addSched, always));
        BlockRule mode = o.copy();
        mode.scheduleMode = BlockRule.ScheduleMode.LIMIT_IN_WINDOW;
        assertFalse(RuleChange.isStricter(o, mode));
    }
}
