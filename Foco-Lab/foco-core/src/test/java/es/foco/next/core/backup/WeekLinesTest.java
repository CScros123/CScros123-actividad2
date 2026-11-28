package es.foco.next.core.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class WeekLinesTest {
    @Test
    public void semanaDeNextDnsConNumeros() {
        Map<String, Long> social = new LinkedHashMap<>();
        social.put("tiktok", 340L);
        social.put("instagram", 20L);
        List<String> l = WeekLines.dnsWeek(125, 1000, social, 7, true);
        assertEquals(3, l.size());
        assertTrue(l.get(0).contains("125") && l.get(0).contains("12.5 %"));
        assertTrue(l.get(1).contains("360") && l.get(1).contains("TikTok"));
        assertTrue(l.get(2).contains("7 avisos"));
        // Sin números (modo Cuidado): las mismas ideas, sin cifras.
        for (String s : WeekLines.dnsWeek(125, 1000, social, 7, false))
            assertFalse(s.matches(".*\\d.*"));
        assertTrue(WeekLines.dnsWeek(0, 0, new LinkedHashMap<>(), 0, true).isEmpty());
    }

    @Test
    public void resumenDelBuhoLoBuenoPrimero() {
        String s = WeekLines.owlWeekly(300, 200, 400, 500, "22:00");
        assertTrue(s.indexOf("invertidos") < s.indexOf("Ocio"));
        assertTrue(s.contains("100 más"));
        assertTrue(s.contains("100 menos"));
        assertTrue(s.contains("22:00"));
        // Si el ocio subió, no se dice nada que regañe.
        String up = WeekLines.owlWeekly(100, 200, 600, 300, "—");
        assertFalse(up.contains("más que") && up.contains("ocio"));
        assertFalse(up.contains("hora más difícil"));
    }

    @Test
    public void unResumenPorSemana() {
        LocalDate sunday = LocalDate.of(2026, 11, 29);
        assertTrue(WeekLines.weeklyDue(sunday, ""));
        assertFalse(WeekLines.weeklyDue(sunday, "2026-11-29"));
        // Lunes: si el domingo no salió, sale ahora; si salió, no.
        assertTrue(WeekLines.weeklyDue(sunday.plusDays(1), "2026-11-22"));
        assertFalse(WeekLines.weeklyDue(sunday.plusDays(1), "2026-11-29"));
        assertFalse(WeekLines.weeklyDue(sunday.plusDays(3), ""));
        assertEquals(sunday, WeekLines.weekSunday(sunday.plusDays(1)));
    }
}
