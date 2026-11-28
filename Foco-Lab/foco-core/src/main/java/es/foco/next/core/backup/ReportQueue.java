package es.foco.next.core.backup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * v47: qué días faltan por subir y cuándo intentarlo. Cada noche (~23:30) se sube el día en curso;
 * como el día de Foco acaba a las 04:00, se sube otra vez la noche siguiente ya cerrado (y entonces
 * cuenta como completo). Si hubo días sin red, se suben todos en orden al volver.
 */
public final class ReportQueue {
    private ReportQueue() {}

    /** Hora a la que se intenta el envío (23:30). */
    public static final LocalTime SEND_AT = LocalTime.of(23, 30);

    /** Como mucho este número de días por vuelta (el registro detallado guarda 30). */
    public static final int MAX_DAYS = 30;

    /**
     * Días a subir, del más antiguo al más nuevo.
     *
     * @param lastComplete último día subido ya cerrado (null si nunca)
     * @param today día de Foco en curso
     * @param connected día en que se conectó Drive (no se sube nada anterior)
     */
    public static List<LocalDate> pending(
            LocalDate lastComplete, LocalDate today, LocalDate connected) {
        List<LocalDate> out = new ArrayList<>();
        if (today == null) return out;
        LocalDate from = lastComplete == null ? connected : lastComplete.plusDays(1);
        if (from == null) from = today;
        if (connected != null && from.isBefore(connected)) from = connected;
        LocalDate oldest = today.minusDays(MAX_DAYS - 1);
        if (from.isBefore(oldest)) from = oldest;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) out.add(d);
        return out;
    }

    /** Un día ya terminó (su 04:00 de cierre pasó) cuando es anterior al día en curso. */
    public static boolean complete(LocalDate day, LocalDate today) {
        return day.isBefore(today);
    }

    /** El nuevo "último día completo" tras subir {@code day} con éxito. */
    public static LocalDate advance(LocalDate lastComplete, LocalDate day, LocalDate today) {
        if (!complete(day, today)) return lastComplete;
        return lastComplete == null || day.isAfter(lastComplete) ? day : lastComplete;
    }

    /** Minutos hasta el próximo 23:30 (para el retraso inicial del trabajo periódico). */
    public static long minutesUntilSend(LocalDateTime now) {
        LocalDateTime next = now.toLocalDate().atTime(SEND_AT);
        if (!next.isAfter(now)) next = next.plusDays(1);
        return java.time.Duration.between(now, next).toMinutes();
    }
}
