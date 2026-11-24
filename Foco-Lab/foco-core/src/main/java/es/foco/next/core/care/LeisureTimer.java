package es.foco.next.core.care;

/**
 * Cronómetro de las apps vigiladas: una píldora pequeña con los minutos de ocio que quedan.
 * Medir sin vigilar: minutos redondeados hacia arriba, nunca segundos; el último minuto se dice con
 * palabras. Pasado el límite en modo Normal (sin bloqueo), cuánto falta para otros 5. Puro.
 */
public final class LeisureTimer {
    private LeisureTimer() {}

    private static long up(long ms) {
        return (Math.max(0, ms) + 59_999) / 60_000;
    }

    /** {@code over}: ya se gastaron los 5 min de esta ventana (modo Normal, sin bloqueo). */
    public static String text(long budgetLeftMs, long windowLeftMs, boolean over) {
        if (over) {
            long w = up(windowLeftMs);
            return w <= 0 ? "🦉 en nada, otros 5" : "🦉 otros 5 en " + w + " min";
        }
        long m = up(budgetLeftMs);
        return m <= 1 ? "🦉 último minuto" : "🦉 " + m + " min";
    }

    public static boolean lastMinute(long budgetLeftMs, boolean over) {
        return !over && budgetLeftMs > 0 && budgetLeftMs <= 60_000;
    }
}
