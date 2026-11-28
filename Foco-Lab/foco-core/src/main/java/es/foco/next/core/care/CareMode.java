package es.foco.next.core.care;

import java.time.ZonedDateTime;

/**
 * De control a cuidado: tres modos que elige el usuario. Puro y testeable (sin Android).
 *
 * <ul>
 *   <li>CUIDADO: el ocio no se cuenta ni se limita. Sin avisos de ocio; como mucho una sugerencia
 *       suave cada 2 horas. Los números de "Hoy" se ocultan.
 *   <li>NORMAL: 5 minutos de ocio por hora. Al agotarlos no se bloquea: recordatorios (máximo 3 por
 *       hora) con una propuesta concreta.
 *   <li>ENFOQUE: el comportamiento estricto (bloqueo al agotar los 5 minutos, banco, Regla 4) con
 *       duración elegida (1 h, 2 h o hasta el inicio de la noche). No se puede terminar antes,
 *       salvo pasando a CUIDADO. Al vencer vuelve al modo anterior.
 * </ul>
 *
 * En todos los modos siguen la noche, Telegram y los dominios de la Regla 1 (eso no vive aquí).
 *
 * <p>Cambios: a ENFOQUE y a CUIDADO, siempre e inmediato; a NORMAL, inmediato salvo durante un
 * ENFOQUE que no ha vencido.
 */
public final class CareMode {
    private CareMode() {}

    public enum Modo {
        CUIDADO,
        NORMAL,
        ENFOQUE
    }

    public enum Duracion {
        UNA_HORA,
        DOS_HORAS,
        HASTA_LA_NOCHE
    }

    public static final long HOUR = 60 * 60_000L;

    /** CUIDADO: una sugerencia suave como mucho cada 2 horas. */
    public static final long CARE_SUGGESTION_EVERY_MS = 2 * HOUR;

    /** CUIDADO: solo se sugiere algo tras un rato largo de ocio desde la última sugerencia. */
    public static final long CARE_SUGGESTION_AFTER_MS = 30 * 60_000L;

    /** NORMAL: como mucho 3 recordatorios por hora. */
    public static final int NORMAL_REMINDERS_PER_HOUR = 3;

    /** CUIDADO con tiempo: duraciones que se eligen al entrar. */
    public static final int[] CARE_CHOICES_MIN = {15, 60, 180};

    /** Tope diario de CUIDADO. */
    public static final long CARE_DAY_MAX_MS = 2 * HOUR;

    /** v32: con el ánimo de hoy en 1 o 2 el tope sube a 3 h (antes no tenía tope). */
    public static final long CARE_DAY_MAX_LOW_MS = 3 * HOUR;

    /**
     * Estado guardado. {@code previo} es al que se vuelve cuando vence el modo actual:
     *
     * <ul>
     *   <li>ENFOQUE: vuelve a NORMAL o a CUIDADO (si se entró desde CUIDADO y a este aún le queda).
     *   <li>CUIDADO: vuelve a NORMAL o a ENFOQUE (si se entró desde un ENFOQUE que aún no ha
     *       vencido).
     * </ul>
     *
     * {@code cuidadoHasta} -1 = CUIDADO sin final (estado antiguo).
     */
    public static final class State {
        public final Modo modo, previo;

        /** Fin del ENFOQUE (reloj de pared, ms), o -1. Se conserva en CUIDADO si se vuelve a él. */
        public final long enfoqueHasta;

        /** Fin del CUIDADO, o -1. Se conserva en ENFOQUE si se vuelve a él. */
        public final long cuidadoHasta;

        public State(Modo modo, Modo previo, long enfoqueHasta) {
            this(modo, previo, enfoqueHasta, -1);
        }

        public State(Modo modo, Modo previo, long enfoqueHasta, long cuidadoHasta) {
            this.modo = modo == null ? Modo.NORMAL : modo;
            Modo p = previo == null || previo == this.modo ? Modo.NORMAL : previo;
            if (this.modo == Modo.NORMAL) p = Modo.NORMAL;
            this.previo = p;
            boolean keepFocus =
                    this.modo == Modo.ENFOQUE || (this.modo == Modo.CUIDADO && p == Modo.ENFOQUE);
            boolean keepCare =
                    this.modo == Modo.CUIDADO || (this.modo == Modo.ENFOQUE && p == Modo.CUIDADO);
            this.enfoqueHasta = keepFocus ? enfoqueHasta : -1;
            this.cuidadoHasta = keepCare ? cuidadoHasta : -1;
        }

        public static State inicial() {
            return new State(Modo.NORMAL, Modo.NORMAL, -1, -1);
        }
    }

    /** Un cambio de modo, para registrarlo (modo_cambiado: de, a, motivo). */
    public static final class Change {
        public final Modo de, a;
        public final String motivo;
        public final State next;

        Change(Modo de, Modo a, String motivo, State next) {
            this.de = de;
            this.a = a;
            this.motivo = motivo;
            this.next = next;
        }

        /** Detalle para el log: "de=NORMAL;a=CUIDADO;motivo=elegido". */
        public String detail() {
            return "de=" + de + ";a=" + a + ";motivo=" + motivo;
        }
    }

    private static boolean ended(long until, long now) {
        return until >= 0 && now >= until;
    }

    /** Aplica los finales vencidos (puede encadenar: Cuidado → Enfoque → Normal). */
    static State resolve(State s, long now) {
        if (s == null) return State.inicial();
        for (int i = 0; i < 3; i++) {
            if (s.modo == Modo.ENFOQUE && ended(s.enfoqueHasta, now)) {
                boolean care = s.previo == Modo.CUIDADO && !ended(s.cuidadoHasta, now);
                s =
                        care
                                ? new State(Modo.CUIDADO, Modo.NORMAL, -1, s.cuidadoHasta)
                                : State.inicial();
            } else if (s.modo == Modo.CUIDADO && ended(s.cuidadoHasta, now)) {
                boolean focus = s.previo == Modo.ENFOQUE && s.enfoqueHasta > now;
                s =
                        focus
                                ? new State(Modo.ENFOQUE, Modo.NORMAL, s.enfoqueHasta, -1)
                                : State.inicial();
            } else break;
        }
        return s;
    }

    /** El modo que vale ahora (con los finales ya aplicados). */
    public static Modo effective(State s, long now) {
        return resolve(s, now).modo;
    }

    /**
     * Si venció ENFOQUE o CUIDADO, el cambio de vuelta (fin_enfoque / fin_cuidado); si no, null.
     */
    public static Change settle(State s, long now) {
        if (s == null) return null;
        State r = resolve(s, now);
        if (r.modo == s.modo
                && r.enfoqueHasta == s.enfoqueHasta
                && r.cuidadoHasta == s.cuidadoHasta) return null;
        return new Change(
                s.modo, r.modo, s.modo == Modo.ENFOQUE ? "fin_enfoque" : "fin_cuidado", r);
    }

    /**
     * A ENFOQUE y a CUIDADO, siempre. A NORMAL no durante un ENFOQUE, ni durante un CUIDADO que
     * vuelve a un ENFOQUE aún activo (sería salir del Enfoque por la puerta de atrás).
     */
    public static boolean canSwitch(State s, Modo to, long now) {
        if (to == Modo.ENFOQUE || to == Modo.CUIDADO) return true;
        State r = resolve(s, now);
        if (r.modo == Modo.ENFOQUE) return false;
        return !(r.modo == Modo.CUIDADO && r.previo == Modo.ENFOQUE && r.enfoqueHasta > now);
    }

    /** Minutos que quedan de CUIDADO (redondeados hacia arriba); 0 si no está o no tiene final. */
    public static long careMinutesLeft(State s, long now) {
        State r = resolve(s, now);
        if (r.modo != Modo.CUIDADO || r.cuidadoHasta < 0) return 0;
        return (r.cuidadoHasta - now + 59_999) / 60_000;
    }

    /** Lo que queda del tope diario de CUIDADO: 2 h, o 3 h si el ánimo de hoy fue 1 o 2 (v32). */
    public static long careBudgetLeftMs(long usedTodayMs, boolean moodLow) {
        long max = moodLow ? CARE_DAY_MAX_LOW_MS : CARE_DAY_MAX_MS;
        return Math.max(0, max - Math.max(0, usedTodayMs));
    }

    /**
     * Pasar a CUIDADO durante {@code minutes}, recortado a lo que quede del tope diario. Vuelve al
     * modo de antes al acabar. null si hoy ya no queda tiempo de Cuidado (y el ánimo no es bajo).
     */
    public static Change toCare(
            State s, int minutes, long now, long usedTodayMs, boolean moodLow, String motivo) {
        long budget = careBudgetLeftMs(usedTodayMs, moodLow);
        if (budget < 60_000L) return null;
        long dur = Math.min(Math.max(1, minutes) * 60_000L, budget);
        State r = resolve(s, now);
        Modo cur = r.modo;
        Modo back = cur == Modo.CUIDADO ? r.previo : cur;
        long focusUntil = back == Modo.ENFOQUE ? r.enfoqueHasta : -1;
        String why =
                (motivo == null || motivo.isEmpty() ? "elegido" : motivo)
                        + "_"
                        + (dur / 60_000)
                        + "min";
        return new Change(
                cur, Modo.CUIDADO, why, new State(Modo.CUIDADO, back, focusUntil, now + dur));
    }

    /** Minutos que faltan del ENFOQUE (redondeados hacia arriba); 0 fuera de ENFOQUE. */
    public static long focusMinutesLeft(State s, long now) {
        State r = resolve(s, now);
        if (r.modo != Modo.ENFOQUE) return 0;
        return (r.enfoqueHasta - now + 59_999) / 60_000;
    }

    /**
     * Fin del ENFOQUE. "Hasta la noche" = hoy a la hora de inicio de la noche; si ya pasó (o faltan
     * menos de 15 min), -1: esa opción no está disponible.
     */
    public static long focusEnd(Duracion d, ZonedDateTime now, int nightStart) {
        long t = now.toInstant().toEpochMilli();
        switch (d) {
            case UNA_HORA:
                return t + HOUR;
            case DOS_HORAS:
                return t + 2 * HOUR;
            default:
                ZonedDateTime end = now.toLocalDate().atTime(nightStart, 0).atZone(now.getZone());
                long e = end.toInstant().toEpochMilli();
                if (now.getHour() < 5 || e - t < 15 * 60_000L) return -1;
                return e;
        }
    }

    /**
     * Pasar a ENFOQUE. Si ya estaba en ENFOQUE gana el final más tardío (solo endurece) y se
     * conserva el modo al que volver. null si la duración no está disponible.
     */
    public static Change toFocus(State s, Duracion d, ZonedDateTime now, int nightStart) {
        long end = focusEnd(d, now, nightStart);
        if (end < 0) return null;
        long t = now.toInstant().toEpochMilli();
        State r = resolve(s, t);
        Modo cur = r.modo;
        Modo back = cur == Modo.ENFOQUE ? r.previo : cur;
        long until = cur == Modo.ENFOQUE ? Math.max(end, r.enfoqueHasta) : end;
        // Si se entra desde CUIDADO, se guarda su final para volver a él (si aún le queda).
        long careUntil = back == Modo.CUIDADO ? r.cuidadoHasta : -1;
        return new Change(
                cur,
                Modo.ENFOQUE,
                "elegido_" + d.name().toLowerCase(),
                new State(Modo.ENFOQUE, back, until, careUntil));
    }

    /**
     * Pasar a NORMAL (o a CUIDADO sin final, compatibilidad). null si no está permitido (NORMAL
     * durante un ENFOQUE). Para CUIDADO con tiempo, {@link #toCare}.
     */
    public static Change to(State s, Modo to, long now, String motivo) {
        if (to == Modo.ENFOQUE || !canSwitch(s, to, now)) return null;
        State r = resolve(s, now);
        Modo cur = r.modo;
        String why = motivo == null || motivo.isEmpty() ? "elegido" : motivo;
        if (to == Modo.CUIDADO) {
            Modo back = cur == Modo.CUIDADO ? r.previo : cur;
            return new Change(
                    cur,
                    to,
                    why,
                    new State(Modo.CUIDADO, back, back == Modo.ENFOQUE ? r.enfoqueHasta : -1, -1));
        }
        return new Change(cur, to, why, State.inicial());
    }

    // ---------- Qué hace cada modo ----------

    /** Qué pasa con una app de ocio ahora. */
    public enum Leisure {
        /** Noche: cerrado en todos los modos (protege el sueño). */
        NIGHT,
        /** CUIDADO: abierto y sin contar. */
        NOT_COUNTED,
        /** NORMAL pasado de los 5 min: abierto, con recordatorio. */
        REMIND,
        /** ENFOQUE pasado de los 5 min (o sin saldo): cerrado. */
        BLOCK,
        /** Dentro de los 5 min (NORMAL o ENFOQUE): abierto y contando. */
        OPEN
    }

    /**
     * Decisión del ocio por modo. La noche manda en todos los modos; Telegram y la web de las redes
     * de la Regla 1 se bloquean antes y aparte (no pasan por aquí).
     */
    public static Leisure leisure(Modo m, boolean night, boolean overLimit) {
        if (night) return Leisure.NIGHT;
        if (m == Modo.CUIDADO) return Leisure.NOT_COUNTED;
        if (overLimit) return m == Modo.ENFOQUE ? Leisure.BLOCK : Leisure.REMIND;
        return Leisure.OPEN;
    }

    /** Telegram (bloqueo total): siempre, en todos los modos. */
    public static boolean alwaysBlocked(String pkg) {
        return es.foco.next.core.Categories.totalBlock(pkg);
    }

    /** El ocio cuenta para los 5 minutos por hora. */
    public static boolean countsLeisure(Modo m) {
        return m != Modo.CUIDADO;
    }

    /** Al agotar los 5 minutos se bloquea (y los bloqueos con nombre y el banco funcionan). */
    public static boolean blocksAtLimit(Modo m) {
        return m == Modo.ENFOQUE;
    }

    /**
     * v26: en Enfoque el ocio solo se abre con la hora libre de Foco Core (sin banco ni bloque
     * gratis).
     */
    public static boolean usesBank(Modo m) {
        return m == Modo.ENFOQUE;
    }

    /** Recordatorios con propuesta al pasar de los 5 minutos (sin bloquear). */
    public static boolean leisureReminders(Modo m) {
        return m == Modo.NORMAL;
    }

    public static boolean careSuggestions(Modo m) {
        return m == Modo.CUIDADO;
    }

    /** Números en "Hoy": ocultos en CUIDADO o si el usuario lo eligió. */
    public static boolean showNumbers(Modo m, boolean hideNumbers) {
        return m != Modo.CUIDADO && !hideNumbers;
    }

    /** ¿Toca una sugerencia de cuidado? */
    public static boolean careSuggestionDue(long now, long lastSuggestion, long ocioSinceLast) {
        if (lastSuggestion >= 0 && now - lastSuggestion < CARE_SUGGESTION_EVERY_MS) return false;
        return ocioSinceLast >= CARE_SUGGESTION_AFTER_MS;
    }

    /** NORMAL: ¿cabe otro recordatorio? Máximo 3 en la última hora. */
    public static boolean normalReminderAllowed(long now, java.util.List<Long> sentLastHour) {
        int n = 0;
        if (sentLastHour != null)
            for (long at : sentLastHour) if (now - at < HOUR && at <= now) n++;
        return n < NORMAL_REMINDERS_PER_HOUR;
    }

    /**
     * Pie de la tarjeta del búho: métricas compactas ("3 hábitos hoy · 5 min/h disponibles"). La
     * frase de lo bueno de hoy vive solo en la tarjeta Hoy. Sin números (Cuidado u "Ocultar
     * números"): solo palabras.
     */
    public static String owlLine(
            int habits, Modo m, boolean night, int nightEnd, long availableMin, boolean numbers) {
        String h =
                !numbers
                        ? (habits > 0 ? "ya hiciste algo bueno hoy" : "algo pequeño ya cuenta")
                        : habits == 0
                                ? "empieza con algo pequeño"
                                : habits + (habits == 1 ? " hábito hoy" : " hábitos hoy");
        String o;
        if (night) o = "ocio cerrado hasta las " + nightEnd + ":00";
        else if (m == Modo.CUIDADO) o = "hoy el ocio no se cuenta";
        else if (!numbers)
            o = availableMin > 0 ? "ocio disponible esta hora" : "ocio de esta hora ya usado";
        else if (m == Modo.ENFOQUE) o = "ocio con tu hora libre de Foco Core";
        else o = availableMin + " min/h disponibles";
        return h + " · " + o;
    }

    public static String label(Modo m) {
        switch (m) {
            case CUIDADO:
                return "Cuidado";
            case ENFOQUE:
                return "Enfoque";
            default:
                return "Normal";
        }
    }

    public static String description(Modo m) {
        switch (m) {
            case CUIDADO:
                return "Un rato suave (15 min, 1 h o 3 h; hasta 2 h al día). El ocio no se cuenta; al acabar vuelves a tu modo de antes.";
            case ENFOQUE:
                return "Tú elegiste concentrarte: al acabar los 5 minutos de ocio se cierra, con banco. Tiene final claro.";
            default:
                return "5 minutos de ocio y después 30 min de pausa. Si te pasas, no se cierra nada: te propongo algo, como mucho 3 veces por hora.";
        }
    }
}
