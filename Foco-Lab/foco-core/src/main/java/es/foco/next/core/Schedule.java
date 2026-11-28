package es.foco.next.core;

import java.time.*;

/**
 * Lote 2: ventana horaria de un bloqueo. Días de la semana (bit 0 = lunes … bit 6 = domingo) y hora
 * de inicio/fin en minutos locales. Si fin < inicio la ventana cruza la medianoche (22:00–07:00) y
 * el día marcado es el del inicio. Inicio == fin = el día entero. Todo se calcula en hora local con
 * ZonedDateTime (los cambios de hora no la rompen).
 */
public final class Schedule {
    public static final int ALL_DAYS = 0x7F;
    public int days = ALL_DAYS;
    public int startMinute = 22 * 60, endMinute = 7 * 60;

    public static Schedule of(int days, int startMinute, int endMinute) {
        Schedule s = new Schedule();
        s.days = days;
        s.startMinute = startMinute;
        s.endMinute = endMinute;
        return s;
    }

    public static int bit(DayOfWeek d) {
        return 1 << (d.getValue() - 1);
    }

    public boolean on(DayOfWeek d) {
        return (days & bit(d)) != 0;
    }

    public Schedule copy() {
        return of(days, startMinute, endMinute);
    }

    public void clamp() {
        days &= ALL_DAYS;
        startMinute = Math.floorMod(startMinute, 24 * 60);
        endMinute = Math.floorMod(endMinute, 24 * 60);
    }

    public boolean contains(ZonedDateTime t) {
        int m = t.getHour() * 60 + t.getMinute();
        LocalDate d = t.toLocalDate();
        if (startMinute == endMinute) return on(d.getDayOfWeek());
        if (startMinute < endMinute)
            return on(d.getDayOfWeek()) && m >= startMinute && m < endMinute;
        return (m >= startMinute && on(d.getDayOfWeek()))
                || (m < endMinute && on(d.minusDays(1).getDayOfWeek()));
    }

    /**
     * Próximo inicio o fin de ventana estrictamente posterior a {@code t}, o null si no hay días
     * marcados.
     */
    public ZonedDateTime nextBoundary(ZonedDateTime t) {
        ZonedDateTime best = null;
        LocalDate today = t.toLocalDate();
        ZoneId z = t.getZone();
        for (int i = -1; i <= 8; i++) {
            LocalDate d = today.plusDays(i);
            if (!on(d.getDayOfWeek())) continue;
            ZonedDateTime s, e;
            if (startMinute == endMinute) {
                s = d.atStartOfDay(z);
                e = d.plusDays(1).atStartOfDay(z);
            } else {
                s = at(d, startMinute, z);
                e = at(startMinute < endMinute ? d : d.plusDays(1), endMinute, z);
            }
            if (s.isAfter(t) && (best == null || s.isBefore(best))) best = s;
            if (e.isAfter(t) && (best == null || e.isBefore(best))) best = e;
        }
        return best;
    }

    private static ZonedDateTime at(LocalDate d, int minute, ZoneId z) {
        return ZonedDateTime.of(d, LocalTime.of(minute / 60, minute % 60), z);
    }

    public static String hhmm(int minute) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static final String[] DAY_LETTERS = {"L", "M", "X", "J", "V", "S", "D"};

    public String daysText() {
        if (days == ALL_DAYS) return "todos los días";
        if (days == 0x1F) return "lun–vie";
        if (days == 0x60) return "fin de semana";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((days & (1 << i)) != 0) b.append(DAY_LETTERS[i]);
        return b.toString();
    }

    public String describe() {
        String d = daysText();
        return startMinute == endMinute
                ? d + " (todo el día)"
                : d + " " + hhmm(startMinute) + "–" + hhmm(endMinute);
    }
}
