package es.foco.next.core.home;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lo que el registro del 21/11 enseñó (06:12 Duolingo 10 s → 1 h 45 min de vídeo corto; Foco
 * apagado hasta las 10:07; Instagram 11 veces en 90 s; el inicio de Samsung de vuelta). Cuatro
 * cuidados del búho, sin bloqueos nuevos. Puro y testeable.
 */
public final class OwlCare {
    private OwlCare() {}

    // ---------- 1. Despertar con el búho ----------

    /** Ventana del ritual: del primer desbloqueo desde las 5:00 hasta mediodía. */
    public static final int MORNING_FROM = HomeState.WAKE_FROM_HOUR, MORNING_UNTIL = 12;

    /** ¿Toca el ritual? Una vez al día, en el primer desbloqueo de la mañana. */
    public static boolean morningDue(LocalDateTime now, String lastRitualDay, boolean enabled) {
        if (!enabled) return false;
        int h = now.getHour();
        return h >= MORNING_FROM
                && h < MORNING_UNTIL
                && !now.toLocalDate().toString().equals(lastRitualDay);
    }

    /** Texto de la pausa de noche cuando ya es de mañana (antes del fin de la noche). */
    public static String earlyMorning(int nightEnd) {
        return "Buenos días. Aún es pronto: el ocio abre a las "
                + nightEnd
                + ":00. ¿Empezamos con luz, agua y algo tuyo?";
    }

    /** true si es de mañana pero sigue siendo "noche" (5:00 hasta el fin de la noche). */
    public static boolean earlyMorning(LocalDateTime now, int nightEnd) {
        int h = now.getHour();
        return h >= MORNING_FROM && h < nightEnd;
    }

    // ---------- 2. Inquietud ----------

    public static final long RESTLESS_WINDOW_MS = 2 * 60_000L, RESTLESS_COOLDOWN_MS = 30 * 60_000L;
    public static final int RESTLESS_OPENS = 6;

    /**
     * 6 o más aperturas de apps de ocio en 2 minutos, y como mucho un aviso cada 30 min. {@code
     * opens}: instantes (ms) de cada apertura, en cualquier orden.
     */
    public static boolean restless(List<Long> opens, long now, long lastNotice) {
        if (lastNotice >= 0 && now - lastNotice < RESTLESS_COOLDOWN_MS) return false;
        int n = 0;
        if (opens != null) for (long t : opens) if (t <= now && now - t <= RESTLESS_WINDOW_MS) n++;
        return n >= RESTLESS_OPENS;
    }

    // ---------- 3. Foco estuvo apagado ----------

    /** Un hueco cuenta a partir de 10 min (un reinicio rápido no merece aviso). */
    public static final long OFF_MIN_MS = 10 * 60_000L;

    /** Aviso "estoy apagado" como mucho cada 6 h. */
    public static final long OFF_NOTICE_EVERY_MS = 6 * 60 * 60_000L;

    public static boolean gapWorthTelling(long offSince, long backAt) {
        return offSince > 0 && backAt - offSince >= OFF_MIN_MS;
    }

    /** "Estuve apagado de 05:00 a 10:07. Gracias por encenderme." Sin culpa. */
    public static String gapText(long offSince, long backAt, ZoneId zone) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("H:mm");
        String a = Instant.ofEpochMilli(offSince).atZone(zone).format(f);
        String b = Instant.ofEpochMilli(backAt).atZone(zone).format(f);
        boolean otherDay =
                !Instant.ofEpochMilli(offSince)
                        .atZone(zone)
                        .toLocalDate()
                        .equals(Instant.ofEpochMilli(backAt).atZone(zone).toLocalDate());
        return "Estuve apagado de "
                + a
                + (otherDay ? " (ayer)" : "")
                + " a "
                + b
                + ". Gracias por encenderme; ya te acompaño otra vez.";
    }

    public static boolean offNoticeDue(long lastNotice, long now, boolean night) {
        return !night && (lastNotice < 0 || now - lastNotice >= OFF_NOTICE_EVERY_MS);
    }

    public static final String OFF_TEXT =
            "Estoy apagado y así no puedo acompañarte. Si quieres, enciéndeme en Accesibilidad › Foco.";

    // ---------- 4. Foco Home dejó de ser el inicio ----------

    /** Una vez al día, solo si Foco Home llegó a ser el inicio alguna vez. */
    public static boolean roleNoticeDue(
            boolean everHeld, boolean heldNow, String lastNoticeDay, String today) {
        return everHeld && !heldNow && !today.equals(lastNoticeDay);
    }

    public static final String ROLE_TEXT =
            "Volviste al inicio de Samsung. Si fue sin querer, tócame y me vuelves a poner como inicio.";
}
