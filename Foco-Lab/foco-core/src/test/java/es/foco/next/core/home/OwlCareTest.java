package es.foco.next.core.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class OwlCareTest {
    private static final long MIN = 60_000L;

    @Test
    public void ritualDeLaManana() {
        LocalDateTime six = LocalDateTime.of(2026, 11, 21, 6, 12);
        assertTrue(OwlCare.morningDue(six, "2026-11-20", true));
        assertFalse(OwlCare.morningDue(six, "2026-11-21", true)); // una vez al día
        assertFalse(OwlCare.morningDue(six, "", false)); // apagado por el usuario
        assertFalse(OwlCare.morningDue(six.withHour(4), "", true));
        assertFalse(OwlCare.morningDue(six.withHour(13), "", true));
        assertTrue(OwlCare.earlyMorning(six, 7));
        assertFalse(OwlCare.earlyMorning(six.withHour(7), 7));
        assertFalse(OwlCare.earlyMorning(six.withHour(23), 7));
        assertTrue(OwlCare.earlyMorning(7).contains("7:00"));
    }

    @Test
    public void inquietudSeisAperturasEnDosMinutos() {
        long now = 100 * MIN;
        List<Long> opens = new ArrayList<>();
        for (int i = 0; i < 5; i++) opens.add(now - i * 10_000L);
        assertFalse(OwlCare.restless(opens, now, -1));
        opens.add(now - 60_000L);
        assertTrue(OwlCare.restless(opens, now, -1)); // Instagram 11 veces en 90 s, como el 21/11
        assertFalse(OwlCare.restless(opens, now, now - 10 * MIN)); // como mucho cada 30 min
        assertTrue(OwlCare.restless(opens, now, now - 31 * MIN));
        List<Long> slow = new ArrayList<>();
        for (int i = 0; i < 6; i++) slow.add(now - i * 60_000L); // 6 en 5 min: no
        assertFalse(OwlCare.restless(slow, now, -1));
    }

    @Test
    public void focoApagadoSeCuentaSinCulpa() {
        ZoneId z = ZoneId.of("America/Panama");
        long off = ZonedDateTime.of(2026, 11, 21, 5, 0, 0, 0, z).toInstant().toEpochMilli();
        long back = ZonedDateTime.of(2026, 11, 21, 10, 7, 0, 0, z).toInstant().toEpochMilli();
        assertTrue(OwlCare.gapWorthTelling(off, back));
        assertFalse(OwlCare.gapWorthTelling(back - 5 * MIN, back));
        assertFalse(OwlCare.gapWorthTelling(0, back));
        assertEquals("Estuve apagado de 5:00 a 10:07. Gracias por encenderme; ya te acompaño otra vez.", OwlCare.gapText(off, back, z));
        assertTrue(OwlCare.offNoticeDue(-1, back, false));
        assertFalse(OwlCare.offNoticeDue(back - 60 * MIN, back, false));
        assertFalse(OwlCare.offNoticeDue(-1, back, true)); // de noche, nada
    }

    @Test
    public void inicioPerdidoUnaVezAlDia() {
        assertTrue(OwlCare.roleNoticeDue(true, false, "2026-11-20", "2026-11-21"));
        assertFalse(OwlCare.roleNoticeDue(true, false, "2026-11-21", "2026-11-21"));
        assertFalse(OwlCare.roleNoticeDue(false, false, "", "2026-11-21")); // nunca fue el inicio
        assertFalse(OwlCare.roleNoticeDue(true, true, "", "2026-11-21"));
    }
}
