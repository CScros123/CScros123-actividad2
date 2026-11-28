package es.foco.next.log;

import java.time.LocalDate;

/**
 * Fase 4: 90-day retention and export ranges. Days are ISO dates (yyyy-MM-dd), so text order is
 * date order.
 */
public final class LogRetention {
    private LogRetention() {}

    public static final int DAYS = 90;

    /** First day kept: today and the 89 previous days (90 days in total). */
    public static String firstKeptDay(LocalDate today) {
        return firstDay(today, DAYS);
    }

    /** First day of a range of {@code days} days ending today (inclusive). Only 1..90. */
    public static String firstDay(LocalDate today, int days) {
        if (today == null) throw new IllegalArgumentException("Fecha vacía");
        if (days < 1 || days > DAYS) throw new IllegalArgumentException("Rango no válido: " + days);
        return today.minusDays(days - 1L).toString();
    }

    public static boolean expired(String day, LocalDate today) {
        return day == null || day.isEmpty() || day.compareTo(firstKeptDay(today)) < 0;
    }
}
