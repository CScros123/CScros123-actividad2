package es.foco.next.core;

import java.time.LocalDateTime;

/**
 * Etapa 0: lo que dice la tarjeta "Tu tiempo de ocio". Pura (sin Android) para poder probarla.
 *
 * <p>Antes la tarjeta restaba {@code BUDGET - usado}; de noche el motor no cuenta uso (Regla 2),
 * así que a las 6:26 mostraba "5 min 0 s disponibles". Ahora la noche se decide primero y siempre
 * da 0. Foco solo endurece: si hay duda (hora automática apagada) también es noche.
 */
public final class LeisureStatus {
    /** Minutos disponibles en la ventana actual (0 de noche o con ocio bloqueado). */
    public final long availableMs;

    /** Motivo corto bajo la cifra ("Ocio cerrado hasta las 7:00", "Ocio disponible"...). */
    public final String reason;

    /** True si la cifra es 0 por la Regla 2 (para pintar la barra llena y no animar nada). */
    public final boolean night;

    private LeisureStatus(long availableMs, String reason, boolean night) {
        this.availableMs = availableMs;
        this.reason = reason;
        this.night = night;
    }

    public static final String NIGHT_REASON =
            "Ocio cerrado hasta las " + AbsoluteRules.nightEnd() + ":00";
    public static final String NO_AUTOTIME_REASON =
            "Ocio cerrado: la hora automática está apagada y Foco lo trata como noche";

    /**
     * @param usedMs uso de la ventana actual según el motor
     * @param locked el motor bloqueó el ocio en esta ventana
     * @param windowLeftMs lo que falta para que termine la ventana (0 si no hay)
     */
    public static LeisureStatus of(
            LocalDateTime now,
            boolean autoTime,
            boolean enabled,
            boolean measuring,
            long usedMs,
            boolean locked,
            long windowLeftMs) {
        if (AbsoluteRules.night(now, autoTime))
            return new LeisureStatus(
                    0,
                    autoTime
                            ? "Ocio cerrado hasta las " + AbsoluteRules.nightEnd() + ":00"
                            : NO_AUTOTIME_REASON,
                    true);
        long available = locked ? 0 : Math.max(0, FocusEngine.BUDGET - Math.max(0, usedMs));
        String state =
                !enabled
                        ? "Plan desactivado"
                        : !measuring
                                ? "Activa Accesibilidad para medir"
                                : locked ? "Ocio bloqueado" : "Ocio disponible";
        String window =
                windowLeftMs > 0
                        ? " · la ventana termina en " + ((windowLeftMs + 59_999) / 60_000) + " min"
                        : " · sin ventana en curso";
        return new LeisureStatus(available, state + window, false);
    }

    /** "Peor horario": sin ocio no hay peor horario (los bloqueos solos no bastan). */
    public static String worstHourLine(long ocioMs, int hour) {
        return ocioMs <= 0 || hour < 0
                ? "Sin datos todavía"
                : "de " + hour + ":00 a " + (hour + 1) + ":00";
    }
}
