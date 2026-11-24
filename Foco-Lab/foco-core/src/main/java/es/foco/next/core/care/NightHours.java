package es.foco.next.core.care;

import java.time.LocalDate;

/**
 * Horario de la noche ajustable con la regla "aflojar con calma, endurecer al instante": si el
 * nuevo horario cubre al menos las mismas horas, se aplica ya; si suelta alguna hora, se aplica al
 * día siguiente. Límites para proteger el sueño: empieza entre las 19 y las 23 y termina entre las
 * 5 y las 9. Puro.
 */
public final class NightHours {
    public static final int MIN_START = 19, MAX_START = 23, MIN_END = 5, MAX_END = 9;
    public static final NightHours DEFAULT = new NightHours(21, 7);

    public final int start, end;

    public NightHours(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public static boolean valid(int start, int end) {
        return start >= MIN_START && start <= MAX_START && end >= MIN_END && end <= MAX_END;
    }

    /** Cubre al menos las mismas horas que {@code o} (empieza igual o antes y acaba igual o después). */
    public boolean covers(NightHours o) {
        return start <= o.start && end >= o.end;
    }

    @Override
    public boolean equals(Object x) {
        return x instanceof NightHours && ((NightHours) x).start == start && ((NightHours) x).end == end;
    }

    @Override
    public int hashCode() {
        return start * 31 + end;
    }

    public String label() {
        return pad(start) + " a " + pad(end);
    }

    private static String pad(int h) {
        return (h < 10 ? "0" : "") + h + ":00";
    }

    /** Lo guardado: el horario vigente y, si hay, uno pendiente que empieza {@code pendingFrom}. */
    public static final class Setting {
        public final NightHours active, pending;
        public final LocalDate pendingFrom;

        public Setting(NightHours active, NightHours pending, LocalDate pendingFrom) {
            this.active = active == null ? DEFAULT : active;
            this.pending = pendingFrom == null ? null : pending;
            this.pendingFrom = pending == null ? null : pendingFrom;
        }

        public static Setting initial() {
            return new Setting(DEFAULT, null, null);
        }
    }

    /** El horario que vale hoy. */
    public static NightHours effective(Setting s, LocalDate today) {
        if (s == null) return DEFAULT;
        if (s.pending != null && !today.isBefore(s.pendingFrom)) return s.pending;
        return s.active;
    }

    /** Consolida: si lo pendiente ya empezó, pasa a vigente. */
    public static Setting settle(Setting s, LocalDate today) {
        if (s == null) return Setting.initial();
        if (s.pending != null && !today.isBefore(s.pendingFrom)) return new Setting(s.pending, null, null);
        return s;
    }

    /**
     * Pide un horario nuevo. Endurecer (o igual): inmediato y se descarta lo pendiente. Aflojar
     * (aunque sea en parte): pendiente desde mañana. Valores fuera de límites: sin cambios.
     */
    public static Setting request(Setting s, NightHours wanted, LocalDate today) {
        Setting now = settle(s, today);
        if (wanted == null || !valid(wanted.start, wanted.end)) return now;
        if (wanted.covers(now.active)) return new Setting(wanted, null, null);
        return new Setting(now.active, wanted, today.plusDays(1));
    }
}
