package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.junit.Test;

public class BlockSuggestionsTest {
    static final long M = 60_000L, H = 60 * M;
    static final String TT = "com.zhiliaoapp.musically",
            IG = "com.instagram.android",
            FB = "com.facebook.katana",
            WA = "com.whatsapp",
            COD = "com.activision.callofduty.shooter",
            MAPS = "com.google.android.apps.maps";
    static final LocalDate TODAY = LocalDate.of(2026, 11, 9); // lunes

    /** 28 días como los del usuario: picos 20–22 h, sábados de 8–10 h, WhatsApp en aumento. */
    static Map<String, Map<String, Long>> daily = new TreeMap<>();

    static Map<String, Map<String, long[]>> hourly = new TreeMap<>();

    static void fill() {
        daily.clear();
        hourly.clear();
        for (int i = 0; i < 28; i++) {
            LocalDate d = TODAY.minusDays(i);
            boolean sat = d.getDayOfWeek() == DayOfWeek.SATURDAY;
            Map<String, long[]> h = new LinkedHashMap<>();
            long[] ig = new long[24], cod = new long[24], wa = new long[24], maps = new long[24];
            ig[20] = 50 * M;
            ig[21] = 45 * M;
            ig[13] = 10 * M;
            cod[20] = 30 * M;
            cod[21] = 30 * M;
            cod[22] = 10 * M;
            wa[9] = 20 * M;
            wa[20] = 20 * M;
            maps[8] = 15 * M;
            if (sat) {
                for (int k = 10; k < 20; k++) {
                    ig[k] += 25 * M;
                    cod[k] += 20 * M;
                }
            }
            h.put(IG, ig);
            h.put(COD, cod);
            h.put(WA, wa);
            h.put(MAPS, maps);
            hourly.put(d.toString(), h);
            Map<String, Long> t = new LinkedHashMap<>();
            for (Map.Entry<String, long[]> e : h.entrySet()) {
                long s = 0;
                for (long v : e.getValue()) s += v;
                t.put(e.getKey(), s);
            }
            daily.put(d.toString(), t);
        }
    }

    static Set<String> leisure() {
        return new LinkedHashSet<>(Arrays.asList(TT, IG, FB, COD));
    }

    static String label(String p) {
        return p.equals(IG)
                ? "Instagram"
                : p.equals(COD) ? "Call of Duty" : p.equals(TT) ? "TikTok" : p;
    }

    @Test
    public void peakWindowFromHourlyData() {
        fill();
        BlockSuggestions.Window w =
                BlockSuggestions.peak(
                        hourly, ScreenTime.days(TODAY, 28), new HashSet<>(Arrays.asList(IG, COD)));
        assertTrue(w.measured);
        assertEquals(20 * 60, w.start);
        assertEquals(22 * 60, w.end);
        assertEquals("20:00–22:00", w.text());
    }

    @Test
    public void peakPrefersThreeHoursWhenSpread() {
        Map<String, Map<String, long[]>> h = new TreeMap<>();
        long[] v = new long[24];
        v[22] = 60 * M;
        v[23] = 60 * M;
        v[0] = 60 * M;
        h.put("2026-11-01", Collections.singletonMap(IG, v));
        BlockSuggestions.Window w =
                BlockSuggestions.peak(h, Collections.singletonList("2026-11-01"), null);
        assertEquals(22 * 60, w.start);
        assertEquals(60, w.end); // cruza la medianoche
    }

    @Test
    public void peakFallsBackWithoutData() {
        BlockSuggestions.Window w = BlockSuggestions.peak(null, ScreenTime.days(TODAY, 28), null);
        assertFalse(w.measured);
        assertEquals("20:00–23:00", w.text());
        Map<String, Map<String, long[]>> h = new TreeMap<>();
        long[] v = new long[24];
        v[3] = 30 * M;
        h.put("2026-11-01", Collections.singletonMap(IG, v));
        assertFalse(
                BlockSuggestions.peak(h, Collections.singletonList("2026-11-01"), null).measured);
    }

    @Test
    public void weakDayIsSaturday() {
        fill();
        assertEquals(
                DayOfWeek.SATURDAY,
                BlockSuggestions.weakDay(daily, ScreenTime.days(TODAY, 28), null));
        // Días iguales: ninguno destaca.
        Map<String, Map<String, Long>> flat = new TreeMap<>();
        for (String d : ScreenTime.days(TODAY, 14))
            flat.put(d, Collections.singletonMap(IG, 3 * H));
        assertNull(BlockSuggestions.weakDay(flat, ScreenTime.days(TODAY, 14), null));
        assertNull(
                "pocos días",
                BlockSuggestions.weakDay(
                        Collections.singletonMap(
                                TODAY.toString(), Collections.singletonMap(IG, 9 * H)),
                        ScreenTime.days(TODAY, 14),
                        null));
    }

    @Test
    public void buildsNightSaturdayAndWhatsApp() {
        fill();
        List<BlockSuggestions.Suggestion> all =
                BlockSuggestions.build(
                        daily, hourly, TODAY, leisure(), null, BlockSuggestionsTest::label);
        assertEquals(3, all.size());
        BlockSuggestions.Suggestion night = all.get(0);
        assertEquals(BlockSuggestions.Kind.NIGHT, night.kind);
        assertEquals("noche:1200-1320", night.id);
        BlockRule r = night.rule("n1");
        assertEquals("n1", r.id);
        assertTrue(r.enabled);
        assertEquals(BlockSuggestions.NIGHT_NAME, r.name);
        assertEquals(BlockRule.ScheduleMode.BLOCK_IN_WINDOW, r.scheduleMode);
        assertTrue(r.scheduled());
        assertEquals(Schedule.ALL_DAYS, r.schedules.get(0).days);
        assertEquals(20 * 60, r.schedules.get(0).startMinute);
        assertEquals(22 * 60, r.schedules.get(0).endMinute);
        assertEquals(Arrays.asList(IG, COD), new ArrayList<>(r.packages));
        assertFalse("WhatsApp nunca entra en un bloqueo de apps", r.packages.contains(WA));
        assertTrue(night.detail.contains("Instagram y Call of Duty"));
        assertTrue(RuleChange.isStricter(null, r)); // modo estricto: crear siempre se permite
        ZonedDateTime nine = ZonedDateTime.of(2026, 11, 10, 21, 0, 0, 0, ScheduleTest.MAD);
        assertTrue(RuleEngine.inWindow(r, nine));
        BlockSuggestions.Suggestion sat = all.get(1);
        assertEquals(BlockSuggestions.Kind.WEAK_DAY, sat.kind);
        assertEquals("dia:6", sat.id);
        BlockRule s = sat.rule("s1");
        assertEquals("Sábado con límite", s.name);
        assertEquals(BlockRule.ScheduleMode.LIMIT_IN_WINDOW, s.scheduleMode);
        assertEquals(15, s.limitMinutes);
        assertEquals(Schedule.bit(DayOfWeek.SATURDAY), s.schedules.get(0).days);
        assertEquals(s.schedules.get(0).startMinute, s.schedules.get(0).endMinute); // el día entero
        assertTrue(sat.detail.startsWith("Los sábados"));
        BlockSuggestions.Suggestion wa = all.get(2);
        assertEquals(BlockSuggestions.Kind.WHATSAPP, wa.kind);
        assertTrue(InAppFilter.quickOn(wa.rule("w"), InAppFilter.WHATSAPP_SECTIONS));
    }

    @Test
    public void needsTwoWeeksOfData() {
        fill();
        Map<String, Map<String, Long>> few = new TreeMap<>();
        int i = 0;
        for (Map.Entry<String, Map<String, Long>> e : daily.entrySet())
            if (i++ < 13) few.put(e.getKey(), e.getValue());
        assertTrue(BlockSuggestions.build(few, hourly, TODAY, leisure(), null, null).isEmpty());
    }

    @Test
    public void defaultAppsWhenLeisureHasNoTime() {
        Map<String, Map<String, Long>> d = new TreeMap<>();
        for (String x : ScreenTime.days(TODAY, 20)) d.put(x, Collections.singletonMap(MAPS, H));
        List<BlockSuggestions.Suggestion> all =
                BlockSuggestions.build(d, null, TODAY, leisure(), null, null);
        assertEquals(1, all.size());
        BlockRule r = all.get(0).rule("x");
        assertEquals(Arrays.asList(TT, IG, FB, COD), new ArrayList<>(r.packages));
        assertEquals(20 * 60, r.schedules.get(0).startMinute);
        assertEquals(23 * 60, r.schedules.get(0).endMinute);
        assertTrue(
                BlockSuggestions.build(d, null, TODAY, Collections.emptySet(), null, null)
                        .isEmpty());
    }

    @Test
    public void dismissedAndExistingAreHidden() {
        fill();
        List<BlockSuggestions.Suggestion> all =
                BlockSuggestions.build(daily, hourly, TODAY, leisure(), null, null);
        long now = 1_000_000L;
        Map<String, Long> dismissed = new HashMap<>();
        dismissed.put("dia:6", BlockSuggestions.dismiss(now));
        List<BlockSuggestions.Suggestion> v =
                BlockSuggestions.visible(all, Collections.emptyList(), dismissed, now);
        assertEquals(2, v.size());
        assertEquals(BlockSuggestions.Kind.NIGHT, v.get(0).kind);
        // A los 14 días vuelve a salir.
        assertEquals(
                3,
                BlockSuggestions.visible(
                                all,
                                Collections.emptyList(),
                                dismissed,
                                now + 14 * BlockSuggestions.DAY_MS)
                        .size());
        List<BlockRule> rules = new ArrayList<>();
        rules.add(all.get(0).rule("a"));
        rules.add(InAppFilter.quickWhatsApp(null, true, "w"));
        v = BlockSuggestions.visible(all, rules, null, now);
        assertEquals(1, v.size());
        assertEquals("dia:6", v.get(0).id);
        // El atajo apagado no oculta la sugerencia de Estados.
        rules.set(1, InAppFilter.quickWhatsApp(rules.get(1), false, "w"));
        assertEquals(2, BlockSuggestions.visible(all, rules, null, now).size());
    }

    @Test
    public void dayNames() {
        assertEquals("Sábado", BlockSuggestions.dayName(DayOfWeek.SATURDAY));
        assertEquals("lunes", BlockSuggestions.plural("Lunes"));
        assertEquals("domingos", BlockSuggestions.plural("Domingo"));
    }
}
