package es.foco.next.core.home;

import static org.junit.Assert.*;

import es.foco.next.core.AppConfig;
import es.foco.next.core.home.HomeSections.Section;
import es.foco.next.core.home.HomeState.Estado;
import es.foco.next.core.home.SettingsWatch.Screen;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Etapas 2, 3, 5 y 6. Escrito sin ejecutar (sin compilar, por indicación del usuario). */
public class HomeModulesTest {
    private static AppConfig config() {
        AppConfig c = new AppConfig();
        c.watchedApps.add("com.game");
        c.productiveApps.add("com.notes");
        return c;
    }

    @Test
    public void sections() {
        AppConfig c = config();
        assertEquals(Section.BLOQUEADA, HomeSections.of(c, "org.telegram.messenger"));
        assertEquals(Section.OCIO, HomeSections.of(c, "com.instagram.android"));
        assertEquals(Section.OCIO, HomeSections.of(c, "com.game"));
        assertEquals(Section.PRODUCTIVA, HomeSections.of(c, "com.notes"));
        assertEquals(Section.HERRAMIENTA, HomeSections.of(c, "com.calc"));
    }

    @Test
    public void leisureNeverInDock() {
        AppConfig c = config();
        List<String> chosen =
                Arrays.asList(
                        "com.notes",
                        "com.game",
                        "com.instagram.android",
                        "org.telegram.messenger",
                        "com.calc",
                        "com.notes");
        List<String> dock = HomeSections.dock(c, chosen, null, false);
        assertEquals(Arrays.asList("com.notes", "com.calc"), dock);
    }

    @Test
    public void nightDockOnlyEssentials() {
        AppConfig c = config();
        List<String> dock =
                HomeSections.dock(
                        c,
                        Arrays.asList("com.notes", "com.whatsapp", "com.android.dialer"),
                        null,
                        true);
        assertEquals(Arrays.asList("com.whatsapp", "com.android.dialer"), dock);
    }

    @Test
    public void searchIgnoresAccents() {
        assertEquals(3, HomeSections.score("Cámara", "cam"));
        assertEquals(2, HomeSections.score("Google Maps", "map"));
        assertEquals(-1, HomeSections.score("Notas", "xyz"));
    }

    @Test
    public void palettesHaveNoBlackOrRed() {
        for (Estado e : Estado.values())
            for (int col : HomePalette.of(e).all())
                assertTrue(Integer.toHexString(col), HomePalette.safe(col));
        assertEquals(0xFFDCE4DC, HomePalette.NEUTRO.background); // prototipo aprobado
        assertEquals(0xFF667B66, HomePalette.NEUTRO.hill);
        assertEquals(0xFF8A5A2B, HomePalette.DESPERTAR.accent);
        assertTrue(HomePalette.NOCHE.moon);
    }

    @Test
    public void plantHasFiveSizes() {
        assertEquals(0, HomePalette.plantSize(0));
        assertEquals(1, HomePalette.plantSize(10));
        assertEquals(2, HomePalette.plantSize(30));
        assertEquals(3, HomePalette.plantSize(60));
        assertEquals(4, HomePalette.plantSize(500));
    }

    @Test
    public void settingsScreens() {
        assertEquals(
                Screen.WIFI,
                SettingsWatch.classify("com.android.settings.wifi.WifiSettings", null, "Foco"));
        assertEquals(
                Screen.PRIVATE_DNS,
                SettingsWatch.classify(
                        "SubSettings",
                        Arrays.asList(
                                "DNS privado",
                                "Desactivado",
                                "Automático",
                                "Nombre de host del proveedor de DNS privado"),
                        "Foco"));
        // La fila "DNS privado" en la lista de conexiones no es el diálogo: no se protege.
        assertEquals(
                Screen.OTHER,
                SettingsWatch.classify(
                        "SubSettings",
                        Arrays.asList("Más ajustes de conexión", "DNS privado", "VPN?"),
                        "Foco"));
        // La lista general de Accesibilidad tampoco (Foco aparece como una fila más).
        assertNotEquals(
                Screen.ACCESSIBILITY_FOCO,
                SettingsWatch.classify(
                        "SubSettings", Arrays.asList("Accesibilidad", "Foco", "TalkBack"), "Foco"));
        assertEquals(
                Screen.APP_INFO_FOCO,
                SettingsWatch.classify(
                        "SubSettings",
                        Arrays.asList("Foco", "Forzar detención", "Desinstalar"),
                        "Foco"));
        assertEquals(
                Screen.ACCESSIBILITY_FOCO,
                SettingsWatch.classify("SubSettings", Arrays.asList("Foco", "Usar Foco"), "Foco"));
        assertEquals(
                Screen.DATE_TIME,
                SettingsWatch.classify("SubSettings", Arrays.asList("Fecha y hora"), "Foco"));
        assertEquals(
                Screen.OTHER,
                SettingsWatch.classify("SubSettings", Arrays.asList("Pantalla"), "Foco"));
        // DNS roto: no se cierra, para que se pueda arreglar.
        assertFalse(SettingsWatch.enforce(Screen.PRIVATE_DNS, false));
        assertTrue(SettingsWatch.enforce(Screen.PRIVATE_DNS, true));
        assertTrue(SettingsWatch.enforce(Screen.APP_INFO_FOCO, false));
        assertFalse(SettingsWatch.enforce(Screen.WIFI, true));
    }

    @Test
    public void dnsFallbackAfterTwoFailsForTenMinutes() {
        long t = 1_000_000;
        DnsFallback d = DnsFallback.start().probe(false, t);
        assertEquals(DnsFallback.Mode.PROVIDER, d.mode);
        d = d.probe(false, t + 60_000);
        assertEquals(DnsFallback.Mode.AUTOMATIC, d.mode);
        assertEquals(DnsFallback.Mode.AUTOMATIC, d.tick(t + 60_000 + 9 * 60_000).mode);
        assertEquals(DnsFallback.Mode.PROVIDER, d.tick(t + 60_000 + 10 * 60_000).mode);
        assertEquals(0, DnsFallback.start().probe(false, t).probe(true, t).fails);
    }

    @Test
    public void companionLimits() {
        Companion.Budget b = new Companion.Budget();
        long now = 10_000_000;
        assertTrue(Companion.allowed(Companion.Trigger.PRODUCTIVE_20, b, now, false, false, false));
        assertFalse(Companion.allowed(Companion.Trigger.PRODUCTIVE_20, b, now, false, true, false));
        assertFalse(Companion.allowed(Companion.Trigger.PRODUCTIVE_20, b, now, false, false, true));
        assertFalse(Companion.allowed(Companion.Trigger.PRODUCTIVE_20, b, now, true, false, false));
        assertTrue(Companion.allowed(Companion.Trigger.NIGHT_SUMMARY, b, now, true, false, false));
        b.shownToday = 8;
        assertFalse(Companion.allowed(Companion.Trigger.RETO_PASSED, b, now, false, false, false));
        // Recordatorios en ocio: cada 5 min y máximo 3 por hora (no cuentan en los 8).
        assertTrue(
                Companion.allowed(
                        Companion.Trigger.LEISURE_NO_BALANCE, b, now, false, false, false));
        b.lastLeisureReminder = now - 4 * 60_000;
        assertFalse(
                Companion.allowed(
                        Companion.Trigger.LEISURE_NO_BALANCE, b, now, false, false, false));
        b.lastLeisureReminder = now - 5 * 60_000;
        b.leisureRemindersLastHour.addAll(
                Arrays.asList(now - 50 * 60_000L, now - 20 * 60_000L, now - 5 * 60_000L));
        assertFalse(
                Companion.allowed(
                        Companion.Trigger.LEISURE_NO_BALANCE, b, now, false, false, false));
    }

    @Test
    public void companionBankAndRotation() {
        for (Companion.Category c : Companion.Category.values()) {
            List<Companion.Message> pool = Companion.of(c);
            assertTrue(c + " tiene al menos 10", pool.size() >= 10);
            for (Companion.Message m : pool) {
                String t = m.text.toLowerCase();
                assertFalse(
                        m.text,
                        t.contains("fallaste") || t.contains("culpa") || t.contains("no pierdas"));
            }
        }
        LocalDate today = LocalDate.of(2026, 11, 17);
        Map<String, String> shown = new HashMap<>();
        for (Companion.Message m : Companion.of(Companion.Category.ANIMO))
            if (!m.id.equals("animo4")) shown.put(m.id, today.minusDays(1).toString());
        assertEquals("animo4", Companion.pick(Companion.Category.ANIMO, shown, today, 99).id);
        assertEquals(
                "Llevas 25 min seguidos en lo tuyo. Así se avanza.",
                Companion.fill(Companion.of(Companion.Category.ANIMO).get(0).text, 25, "", ""));
        assertTrue(Companion.noFreeBlockTomorrow(16 * 60_000L));
        assertFalse(Companion.noFreeBlockTomorrow(15 * 60_000L));
    }
}
