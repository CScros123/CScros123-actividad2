package es.foco.next.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class PlanChangeTest {
    private static AppConfig plan(String... watched) {
        AppConfig c = new AppConfig();
        for (String w : watched) c.watchedApps.add(w);
        return c;
    }

    @Test
    public void sameOrStricterIsAllowed() {
        AppConfig old = plan("a.b");
        AppConfig neu = plan("a.b", "c.d");
        neu.intention = "Otra";
        neu.returnApp = "r.s";
        assertNull(PlanChange.reason(old, plan("a.b")));
        assertNull(PlanChange.reason(old, neu));
        old.sensitiveApps.add("x.y");
        assertNull(PlanChange.reason(old, plan("a.b")));
        AppConfig off = plan("a.b");
        off.enabled = false;
        assertNull(PlanChange.reason(off, plan("a.b")));
    }

    @Test
    public void looserIsRejected() {
        AppConfig old = plan("a.b", "c.d");
        assertNotNull(PlanChange.reason(old, plan("a.b")));
        AppConfig off = plan("a.b", "c.d");
        off.enabled = false;
        assertNotNull(PlanChange.reason(old, off));
        AppConfig ess = plan("a.b", "c.d");
        ess.sensitiveApps.add("a.b");
        assertNotNull(PlanChange.reason(old, ess));
    }
}
