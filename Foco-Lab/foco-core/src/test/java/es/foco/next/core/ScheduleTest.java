package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.junit.Test;

public class ScheduleTest {
    static final ZoneId MAD = ZoneId.of("Europe/Madrid");

    static ZonedDateTime at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, MAD);
    }

    static final int MON_FRI = 0x1F;

    @Test
    public void overnightWindowCrossesMidnight() {
        Schedule s = Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60);
        assertTrue(s.contains(at(2026, 11, 6, 23, 30)));
        assertTrue(s.contains(at(2026, 11, 7, 6, 59)));
        assertFalse(s.contains(at(2026, 11, 7, 7, 0)));
        assertFalse(s.contains(at(2026, 11, 6, 21, 59)));
        assertTrue(s.contains(at(2026, 11, 6, 22, 0)));
    }

    @Test
    public void overnightUsesTheStartDay() {
        // 2026-11-06 es viernes. Solo lun–vie: viernes 23:00 dentro, sábado 03:00 dentro (empezó el
        // viernes), sábado 23:00 fuera.
        Schedule s = Schedule.of(MON_FRI, 22 * 60, 7 * 60);
        assertEquals(DayOfWeek.FRIDAY, at(2026, 11, 6, 0, 0).getDayOfWeek());
        assertTrue(s.contains(at(2026, 11, 6, 23, 0)));
        assertTrue(s.contains(at(2026, 11, 7, 3, 0)));
        assertFalse(s.contains(at(2026, 11, 7, 23, 0)));
        assertFalse(s.contains(at(2026, 11, 8, 3, 0))); // domingo madrugada: empezó el sábado
        assertTrue(s.contains(at(2026, 11, 10, 3, 0))); // martes madrugada: empezó el lunes
    }

    @Test
    public void sameDayWindowAndDays() {
        Schedule s =
                Schedule.of(
                        Schedule.bit(DayOfWeek.SATURDAY) | Schedule.bit(DayOfWeek.SUNDAY),
                        9 * 60,
                        13 * 60);
        assertTrue(s.contains(at(2026, 11, 7, 9, 0)));
        assertTrue(s.contains(at(2026, 11, 8, 12, 59)));
        assertFalse(s.contains(at(2026, 11, 8, 13, 0)));
        assertFalse(s.contains(at(2026, 11, 6, 10, 0)));
    }

    @Test
    public void startEqualsEndIsWholeDay() {
        Schedule s = Schedule.of(Schedule.bit(DayOfWeek.FRIDAY), 0, 0);
        assertTrue(s.contains(at(2026, 11, 6, 0, 0)));
        assertTrue(s.contains(at(2026, 11, 6, 23, 59)));
        assertFalse(s.contains(at(2026, 11, 7, 0, 0)));
    }

    @Test
    public void nextBoundary() {
        Schedule s = Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60);
        assertEquals(at(2026, 11, 6, 22, 0), s.nextBoundary(at(2026, 11, 6, 15, 0)));
        assertEquals(
                at(2026, 11, 7, 7, 0),
                s.nextBoundary(at(2026, 11, 6, 22, 0))); // en el borde: el siguiente es el fin
        assertEquals(at(2026, 11, 7, 7, 0), s.nextBoundary(at(2026, 11, 7, 2, 0)));
        Schedule weekend = Schedule.of(Schedule.bit(DayOfWeek.SATURDAY), 9 * 60, 13 * 60);
        assertEquals(at(2026, 11, 14, 9, 0), weekend.nextBoundary(at(2026, 11, 7, 13, 0)));
        assertNull(Schedule.of(0, 0, 60).nextBoundary(at(2026, 11, 6, 0, 0)));
    }

    @Test
    public void dstChangesAreComputedInLocalTime() {
        // 2027-03-28 adelanta 02:00→03:00 en Madrid; 2026-10-25 atrasa 03:00→02:00.
        Schedule s = Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60);
        ZonedDateTime spring = at(2027, 3, 28, 1, 59).plusMinutes(1); // 03:00 local
        assertEquals(3, spring.getHour());
        assertTrue(s.contains(spring));
        assertEquals(at(2027, 3, 28, 7, 0), s.nextBoundary(spring));
        ZonedDateTime fall =
                at(2026, 10, 25, 2, 30).withLaterOffsetAtOverlap(); // segunda vez que son las 02:30
        assertTrue(s.contains(fall));
        assertEquals(at(2026, 10, 25, 7, 0), s.nextBoundary(fall));
        // Una ventana que empieza en la hora que no existe empieza al saltar el reloj.
        Schedule gap = Schedule.of(Schedule.ALL_DAYS, 2 * 60 + 30, 5 * 60);
        ZonedDateTime b = gap.nextBoundary(at(2027, 3, 28, 1, 0));
        assertEquals(3, b.getHour());
        assertTrue(gap.contains(b));
        assertFalse(gap.contains(at(2027, 3, 28, 1, 59)));
    }

    @Test
    public void sameInstantDifferentZoneUsesLocalClock() {
        Schedule s = Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60);
        Instant i = at(2026, 11, 6, 23, 0).toInstant();
        assertTrue(s.contains(i.atZone(MAD)));
        assertFalse(s.contains(i.atZone(ZoneId.of("America/New_York")))); // allí son las 17:00
    }

    @Test
    public void describe() {
        assertEquals(
                "todos los días 22:00–07:00",
                Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60).describe());
        assertEquals("lun–vie 09:00–17:30", Schedule.of(MON_FRI, 9 * 60, 17 * 60 + 30).describe());
    }
}
