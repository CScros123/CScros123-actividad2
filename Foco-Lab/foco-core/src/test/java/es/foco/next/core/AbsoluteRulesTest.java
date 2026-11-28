package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.Test;

/** Reglas absolutas. Escrito sin ejecutar (sin compilar, por indicación del usuario). */
public class AbsoluteRulesTest {
    // Martes 17 de noviembre de 2026.
    private static LocalDateTime at(int h, int m) {
        return LocalDateTime.of(2026, 11, 17, h, m);
    }

    @Test
    public void nightBoundaries() {
        assertFalse(AbsoluteRules.esNocheAbsoluta(at(20, 59)));
        assertTrue(AbsoluteRules.esNocheAbsoluta(at(21, 0)));
        assertTrue(AbsoluteRules.esNocheAbsoluta(at(6, 59)));
        assertFalse(AbsoluteRules.esNocheAbsoluta(at(7, 0)));
    }

    @Test
    public void autoTimeOffAtThreeBlocksLikeNight() {
        assertTrue(AbsoluteRules.night(at(15, 0), false));
        assertFalse(AbsoluteRules.night(at(15, 0), true));
    }

    @Test
    public void instagramCannotBeProductive() {
        assertTrue(Categories.neverProductive("com.instagram.android", false));
        AppConfig c = new AppConfig();
        c.productiveApps.add("com.instagram.android");
        List<String> fixed = AbsoluteRules.enforce(c);
        assertFalse(c.productiveApps.contains("com.instagram.android"));
        assertTrue(c.watchedApps.contains("com.instagram.android"));
        assertTrue(fixed.contains("com.instagram.android:en_productivas"));
    }

    @Test
    public void importWithoutTikTokKeepsTikTokInLeisure() {
        AppConfig imported = new AppConfig();
        imported.watchedApps.add("feed.app");
        assertTrue(
                FocusEngine.watched(
                        imported, "com.zhiliaoapp.musically")); // ya antes de corregir el plan
        List<String> fixed = AbsoluteRules.enforce(imported);
        assertTrue(imported.watchedApps.contains("com.zhiliaoapp.musically"));
        assertTrue(fixed.contains("com.zhiliaoapp.musically:faltaba_en_ocio"));
    }

    @Test
    public void socialIsNeverEssentialNorAlternative() {
        AppConfig c = new AppConfig();
        c.sensitiveApps.add("com.google.android.youtube");
        c.returnApp = "com.google.android.youtube";
        assertFalse(FocusEngine.essential(c, "com.google.android.youtube"));
        assertTrue(FocusEngine.watched(c, "com.google.android.youtube"));
        AbsoluteRules.enforce(c);
        assertEquals("", c.returnApp);
        assertFalse(c.sensitiveApps.contains("com.google.android.youtube"));
    }

    @Test
    public void socialDomainsWithSubdomains() {
        assertTrue(AbsoluteRules.socialDomain("https://m.youtube.com/watch?v=1"));
        assertTrue(AbsoluteRules.socialDomain("www.tiktok.com/@x"));
        assertTrue(AbsoluteRules.socialDomain("x.com"));
        assertFalse(AbsoluteRules.socialDomain("https://box.com/"));
        assertFalse(AbsoluteRules.socialDomain("https://es.wikipedia.org/wiki/Reddit"));
    }

    @Test
    public void leavingLeisureTakesTwentyFourHours() {
        long h = 60 * 60_000L;
        assertFalse(AbsoluteRules.leaveDue(0, 1000, 5, 23 * h, 1000 + 23 * h, 5, true));
        assertTrue(AbsoluteRules.leaveDue(0, 1000, 5, 24 * h, 1000 + 24 * h, 5, true));
        // Adelantar el reloj en el mismo arranque no sirve.
        assertFalse(AbsoluteRules.leaveDue(0, 1000, 5, 48 * h, 1000 + h, 5, true));
        // Tras un reinicio cuenta el reloj, solo con la hora automática encendida.
        assertTrue(AbsoluteRules.leaveDue(0, 1000, 5, 25 * h, 10, 6, true));
        assertFalse(AbsoluteRules.leaveDue(0, 1000, 5, 25 * h, 10, 6, false));
        // No se puede pedir de noche ni en Weekend Lock.
        assertFalse(AbsoluteRules.canRequestLeave(at(22, 0), true, false));
        assertFalse(AbsoluteRules.canRequestLeave(at(12, 0), true, true));
        assertTrue(AbsoluteRules.canRequestLeave(at(12, 0), true, false));
    }

    @Test
    public void thirtyDaysOfLeisureMemory() {
        LocalDate today = LocalDate.of(2026, 11, 17);
        assertTrue(AbsoluteRules.recentLeisure("2026-10-18", today));
        assertFalse(AbsoluteRules.recentLeisure("2026-10-17", today));
        assertFalse(AbsoluteRules.recentLeisure("", today));
    }

    @Test
    public void productivityAppIsNotLimitedInObservation() {
        final int CATEGORY_PRODUCTIVITY = 7; // ApplicationInfo.CATEGORY_PRODUCTIVITY
        assertFalse(AbsoluteRules.limitInObservation(CATEGORY_PRODUCTIVITY));
        assertFalse(AbsoluteRules.limitInObservation(-1));
        assertTrue(AbsoluteRules.limitInObservation(AbsoluteRules.CATEGORY_GAME));
        assertTrue(AbsoluteRules.neutral(AbsoluteRules.CATEGORY_AUDIO));
    }

    @Test
    public void socialCountsEvenWithPlanDisabled() {
        AppConfig c = new AppConfig();
        c.enabled = false;
        FocusState s = new FocusState();
        FocusEngine e = new FocusEngine();
        FocusEngine.Input in = new FocusEngine.Input();
        in.pkg = "com.zhiliaoapp.musically";
        in.day = "2026-11-17";
        in.wall = 1_000_000;
        in.elapsed = 1000;
        in.boot = 1;
        in.interactive = true;
        e.step(c, s, in);
        in.elapsed += 30_000;
        in.wall += 30_000;
        e.step(c, s, in);
        assertTrue(s.usedMillis > 0);
    }
}
