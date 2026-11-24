package es.foco.next.core;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class UsageShiftTest {
    static final long M = 60_000L;
    static final String TT = "com.zhiliaoapp.musically",
            IG = "com.instagram.android",
            WA = "com.whatsapp",
            COD = "com.activision.callofduty.shooter";

    static Map<String, Long> m(Object... kv) {
        Map<String, Long> o = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2)
            o.put((String) kv[i], ((Number) kv[i + 1]).longValue() * M);
        return o;
    }

    static final Set<String> WATCHED = new HashSet<>(Arrays.asList(TT, IG, COD));

    static String label(String p) {
        return p.equals(TT)
                ? "TikTok"
                : p.equals(IG)
                        ? "Instagram"
                        : p.equals(WA) ? "WhatsApp" : p.equals(COD) ? "Call of Duty" : p;
    }

    @Test
    public void tiktokDownInstagramUp() {
        UsageShift.Shift s =
                UsageShift.detect(m(TT, 700, IG, 100), m(TT, 400, IG, 220), WATCHED, null);
        assertNotNull(s);
        assertEquals(TT, s.down.pkg);
        assertEquals(1, s.up.size());
        assertEquals(IG, s.up.get(0).pkg);
        assertEquals(
                "Desplazamiento: bajaste TikTok (−43 %) pero Instagram subió (+120 %)",
                UsageShift.text(s, UsageShiftTest::label));
    }

    @Test
    public void essentialAppCanBeTheOneThatGrows() {
        UsageShift.Shift s =
                UsageShift.detect(
                        m(TT, 300, COD, 200, WA, 60, IG, 100),
                        m(TT, 0, COD, 50, WA, 180, IG, 250),
                        WATCHED,
                        null);
        assertEquals(TT, s.down.pkg);
        assertEquals(Arrays.asList(IG, WA), Arrays.asList(s.up.get(0).pkg, s.up.get(1).pkg));
        assertEquals(
                "Desplazamiento: bajaste TikTok (−100 %) pero subieron Instagram (+150 %) y WhatsApp (+200 %)",
                UsageShift.text(s, UsageShiftTest::label));
    }

    @Test
    public void thresholds() {
        // Baja 29 min (no llega a 30): nada.
        assertNull(UsageShift.detect(m(TT, 100, IG, 40), m(TT, 71, IG, 100), WATCHED, null));
        // Baja 30 min pero solo el 20 %: nada.
        assertNull(UsageShift.detect(m(TT, 150, IG, 40), m(TT, 120, IG, 100), WATCHED, null));
        // Sube 19 min: nada. Sube 20 min pero solo el 40 %: nada.
        assertNull(UsageShift.detect(m(TT, 100, IG, 40), m(TT, 50, IG, 59), WATCHED, null));
        assertNull(UsageShift.detect(m(TT, 100, IG, 50), m(TT, 50, IG, 70), WATCHED, null));
        // Justo en los umbrales: sí.
        assertNotNull(UsageShift.detect(m(TT, 100, IG, 40), m(TT, 70, IG, 60), WATCHED, null));
        // Una app nueva que sube 20 min cuenta.
        UsageShift.Shift s =
                UsageShift.detect(m(TT, 100), m(TT, 50, "new.game", 45), WATCHED, null);
        assertEquals("new.game", s.up.get(0).pkg);
        assertEquals("+45 min", UsageShift.pct(s.up.get(0)));
    }

    @Test
    public void onlyWatchedAppsCountAsTheDrop() {
        // WhatsApp baja mucho, pero no es de ocio: no es un desplazamiento.
        assertNull(UsageShift.detect(m(WA, 600, IG, 100), m(WA, 100, IG, 300), WATCHED, null));
        assertNull(
                UsageShift.detect(m(TT, 600), m(TT, 100, IG, 300), Collections.emptySet(), null));
        // Foco y el launcher nunca cuentan como la que sube.
        assertNull(
                UsageShift.detect(
                        m(TT, 600),
                        m(TT, 100, "es.foco.next", 300, "com.miui.home", 300),
                        WATCHED,
                        Collections.singleton("es.foco.next")));
    }

    @Test
    public void biggestDropAndAtMostTwoRises() {
        UsageShift.Shift s =
                UsageShift.detect(
                        m(TT, 300, COD, 500, "a", 10, "b", 10, "c", 10),
                        m(TT, 100, COD, 100, "a", 100, "b", 60, "c", 80),
                        WATCHED,
                        null);
        assertEquals(COD, s.down.pkg);
        assertEquals(2, s.up.size());
        assertEquals("a", s.up.get(0).pkg);
        assertEquals("c", s.up.get(1).pkg);
        assertEquals("", UsageShift.text(null, UsageShiftTest::label));
    }
}
