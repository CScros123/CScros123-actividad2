package es.foco.next.core;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * Foco 1.0, etapa 4 (docs/FOCO-1.0-DEFINICION.md §4): Weekend Lock, de viernes 18:00 a domingo
 * 23:59, hora local. El estado se calcula a partir de la hora, no se guarda: tras un reinicio el
 * resultado es el mismo. Lógica pura.
 */
public final class WeekendLock {
    private WeekendLock() {}

    public static final int START_HOUR = 18;

    public static boolean active(LocalDateTime t) {
        DayOfWeek d = t.getDayOfWeek();
        return d == DayOfWeek.SATURDAY
                || d == DayOfWeek.SUNDAY
                || (d == DayOfWeek.FRIDAY && t.getHour() >= START_HOUR);
    }

    /** Lunes a viernes: los días con bloque gratis (si no hay Weekend Lock). */
    public static boolean weekday(LocalDateTime t) {
        DayOfWeek d = t.getDayOfWeek();
        return d != DayOfWeek.SATURDAY && d != DayOfWeek.SUNDAY;
    }

    /**
     * Fin del Weekend Lock en curso: el lunes siguiente a las 00:00 (un minuto después del domingo
     * 23:59).
     */
    public static LocalDateTime end(LocalDateTime t) {
        return t.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay();
    }

    /** Viernes en que empezó el Weekend Lock en curso (identifica el fin de semana en el log). */
    public static String id(LocalDateTime t) {
        return t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY)).toString();
    }
}
