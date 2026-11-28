package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.junit.Test;

public class PurchaseGuardTest {
    static final String P = PurchaseGuard.PLAY;

    static ZonedDateTime at(int h) {
        return ZonedDateTime.of(2026, 11, 6, h, 0, 0, 0, ScheduleTest.MAD);
    }

    static BlockRule buys(String id, boolean scheduled) {
        BlockRule r = new BlockRule();
        r.id = id;
        r.name = "Compras";
        r.blockPurchases = true;
        if (scheduled) {
            r.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
            r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 9 * 60, 17 * 60));
        }
        r.clamp();
        return r;
    }

    @Test
    public void playBillingSheetIsPurchase() {
        assertTrue(
                PurchaseGuard.isPurchase(
                        P,
                        "android.widget.FrameLayout",
                        Arrays.asList("Google Play", "Monedas x100", "MXN 49.00", "Comprar")));
        assertTrue(
                PurchaseGuard.isPurchase(
                        P,
                        null,
                        Arrays.asList("Pro mensual", "4,99 €/mes", "Suscribirse", "Google Play")));
        assertTrue(
                PurchaseGuard.isPurchase(
                        P,
                        "com.google.android.finsky.billing.acquire.LockToPortraitUiBuilderHostActivity",
                        Arrays.asList("Gems", "$0.99", "1-tap buy")));
        assertTrue(
                PurchaseGuard.isPurchase(
                        P, null, Arrays.asList("Google Play", "US$ 2.99", "Subscribe")));
    }

    @Test
    public void otherPackagesAndStorePagesAreNot() {
        assertFalse(
                PurchaseGuard.isPurchase(
                        "com.game", null, Arrays.asList("Google Play", "$0.99", "Comprar")));
        assertFalse(
                PurchaseGuard.isPurchase(
                        P,
                        "com.google.android.finsky.activities.MainActivity",
                        Arrays.asList(
                                "Juego",
                                "Contiene anuncios · Compras en la app",
                                "Instalar",
                                "Google Play",
                                "$0.99 - $99.99 por artículo")));
        assertFalse(
                PurchaseGuard.isPurchase(
                        P, null, Arrays.asList("Juego", "Instalar", "Comprar", "$0.99")));
        assertFalse(
                PurchaseGuard.isPurchase(
                        P, null, Arrays.asList("Google Play", "Comprar"))); // sin precio
        assertFalse(
                PurchaseGuard.isPurchase(
                        P, null, Arrays.asList("Google Play", "$4.99"))); // sin botón de compra
        assertFalse(
                PurchaseGuard.isPurchase(
                        P,
                        null,
                        Arrays.asList("Comprar", "$4.99"))); // ni Google Play ni clase de pago
        assertFalse(PurchaseGuard.isPurchase(P, null, null));
    }

    @Test
    public void activeFollowsEnabledPauseAndWindow() {
        RuleState s = new RuleState();
        BlockRule always = buys("a", false), day = buys("d", true);
        assertTrue(PurchaseGuard.active(always, s, at(22)));
        assertTrue(PurchaseGuard.active(day, s, at(10)));
        assertFalse(PurchaseGuard.active(day, s, at(20)));
        always.enabled = false;
        assertFalse(PurchaseGuard.active(always, s, at(10)));
        BlockRule off = buys("o", false);
        off.blockPurchases = false;
        assertFalse(PurchaseGuard.active(off, s, at(10)));
        assertSame(day, PurchaseGuard.blocker(Arrays.asList(always, off, day), s, at(10)));
        assertNull(PurchaseGuard.blocker(Arrays.asList(always, off, day), s, at(20)));
        s.of("d").pausedUntil = at(10).toInstant().toEpochMilli() + 60_000;
        assertFalse(PurchaseGuard.active(day, s, at(10)));
    }

    @Test
    public void strictCountsScheduledFiltersOnlyInsideWindow() {
        RuleState s = new RuleState();
        BlockRule unscheduled = buys("u", false);
        BlockRule web = new BlockRule();
        web.id = "w";
        web.name = "Web";
        web.sites.add("youtube.com");
        web.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
        web.schedules.add(Schedule.of(Schedule.ALL_DAYS, 9 * 60, 17 * 60));
        web.clamp();
        assertFalse(RuleEngine.strictActive(Collections.singletonList(unscheduled), s, at(10)));
        assertTrue(RuleEngine.strictActive(Arrays.asList(unscheduled, web), s, at(10)));
        assertFalse(RuleEngine.strictActive(Arrays.asList(unscheduled, web), s, at(20)));
        assertTrue(RuleEngine.strictActive(Collections.singletonList(buys("d", true)), s, at(12)));
    }

    @Test
    public void duplicateGetsNewIdCopyNameAndIsDisabled() {
        BlockRule r = buys("x", true);
        r.name = "Redes";
        r.packages.add("com.a");
        r.sites.add("x.com");
        BlockRule d = r.duplicate("y");
        assertEquals("y", d.id);
        assertEquals("Redes (copia)", d.name);
        assertFalse(d.enabled);
        assertTrue(d.blockPurchases);
        assertEquals(r.packages, d.packages);
        assertEquals(r.sites, d.sites);
        assertEquals(1, d.schedules.size());
        assertNotSame(r.schedules.get(0), d.schedules.get(0));
        assertTrue(r.enabled);
        r.name = "123456789012345678901234567890123456789X";
        d = r.duplicate("z");
        d.clamp();
        assertTrue(d.name.endsWith(" (copia)"));
        assertTrue(d.name.length() <= 40);
    }
}
