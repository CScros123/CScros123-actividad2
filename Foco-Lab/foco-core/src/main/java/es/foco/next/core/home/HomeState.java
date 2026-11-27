package es.foco.next.core.home;

import es.foco.next.core.AbsoluteRules;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Etapa 1.2: EstadoActual de Foco Home. Función pura (sin Android): recibe los hechos recientes del
 * log y el estado anterior y devuelve el nuevo. No usa temporizadores: quien la llama lo hace al
 * escribir un evento en el log o al mostrar el inicio, con la hora de ese momento.
 *
 * <p>Reglas (ventana de los últimos {@link #WINDOW_MS}):
 *
 * <ul>
 *   <li>NOCHE de 21:00 a 07:00, o con la hora automática apagada (como la Regla 2).
 *   <li>DESPERTAR: desde el primer desbloqueo después de las 05:00 hasta las 07:00 o hasta
 *       completar el reto de despertar. Gana a NOCHE solo en esa franja; las reglas siguen siendo
 *       las de noche (sin ocio, sin banco).
 *   <li>DISTRAIDO: 5 min o más de ocio o 3 intentos bloqueados.
 *   <li>PRODUCTIVO: 10 min o más de uso productivo o retos y 0 de ocio.
 *   <li>NEUTRO: todo lo demás.
 * </ul>
 *
 * Histéresis: un estado nuevo de día se aplica si se mantiene {@link #HYSTERESIS_MS}. PRODUCTIVO se
 * mantiene {@link #PRODUCTIVE_HOLD_MS} después de parar frente a NEUTRO (DISTRAIDO no espera ese
 * margen, solo la histéresis normal). NOCHE y DESPERTAR se aplican al momento: son horarios.
 */
public final class HomeState {
    private HomeState() {}

    public enum Estado {
        PRODUCTIVO("Productivo"),
        NEUTRO("Tranquilo"),
        DISTRAIDO("Distraído"),
        NOCHE("Noche"),
        DESPERTAR("Despertar");
        public final String label;

        Estado(String label) {
            this.label = label;
        }
    }

    public static final long MIN = 60_000L;
    public static final long WINDOW_MS = 30 * MIN;
    public static final long PRODUCTIVE_MS = 10 * MIN;
    public static final long DISTRACTED_OCIO_MS = 5 * MIN;
    public static final int DISTRACTED_ATTEMPTS = 3;
    public static final long HYSTERESIS_MS = 5 * MIN;
    public static final long PRODUCTIVE_HOLD_MS = 15 * MIN;

    /** Un reto superado cuenta como este tiempo productivo (una Práctica corta dura 3 min). */
    public static final long RETO_EQUIV_MS = 3 * MIN;

    public static final int WAKE_FROM_HOUR = 5;

    public enum Kind {
        PRODUCTIVE,
        RETO,
        OCIO,
        ATTEMPT
    }

    /** Un hecho del log: cuándo terminó y cuánto duró (0 para retos e intentos). */
    public static final class Fact {
        public final long wall, ms;
        public final Kind kind;

        public Fact(long wall, Kind kind, long ms) {
            this.wall = wall;
            this.kind = kind;
            this.ms = Math.max(0, ms);
        }
    }

    /** Lo que se guarda entre cálculos. Inmutable. */
    public static final class Snapshot {
        public final Estado estado;
        public final long since;
        public final Estado candidate;
        public final long candidateSince;
        public final long lastProductiveAt;
        public final String motivo;

        public Snapshot(
                Estado estado,
                long since,
                Estado candidate,
                long candidateSince,
                long lastProductiveAt,
                String motivo) {
            this.estado = estado;
            this.since = since;
            this.candidate = candidate;
            this.candidateSince = candidateSince;
            this.lastProductiveAt = lastProductiveAt;
            this.motivo = motivo == null ? "" : motivo;
        }

        public static Snapshot initial(long now) {
            return new Snapshot(Estado.NEUTRO, now, null, -1, -1, "Sin datos todavía");
        }
    }

    /** Totales de la ventana. */
    public static final class Window {
        public long productiveMs, ocioMs;
        public int retos, attempts;

        public long investedMs() {
            return productiveMs + retos * RETO_EQUIV_MS;
        }
    }

    public static Window window(List<Fact> facts, long now) {
        Window w = new Window();
        if (facts == null) return w;
        for (Fact f : facts) {
            if (f.wall > now || now - f.wall > WINDOW_MS) continue;
            switch (f.kind) {
                case PRODUCTIVE:
                    w.productiveMs += f.ms;
                    break;
                case RETO:
                    w.retos++;
                    break;
                case OCIO:
                    w.ocioMs += f.ms;
                    break;
                case ATTEMPT:
                    w.attempts++;
                    break;
            }
        }
        return w;
    }

    /** Estado de día sin histéresis, con su motivo. */
    static Estado raw(Window w) {
        if (w.ocioMs >= DISTRACTED_OCIO_MS || w.attempts >= DISTRACTED_ATTEMPTS)
            return Estado.DISTRAIDO;
        if (w.investedMs() >= PRODUCTIVE_MS && w.ocioMs == 0) return Estado.PRODUCTIVO;
        return Estado.NEUTRO;
    }

    static String reason(Estado e, Window w) {
        switch (e) {
            case DISTRAIDO:
                return w.ocioMs >= DISTRACTED_OCIO_MS
                        ? (w.ocioMs / MIN) + " min de ocio en la última media hora"
                        : w.attempts + " intentos bloqueados en la última media hora";
            case PRODUCTIVO:
                return (w.investedMs() / MIN) + " min invertidos en la última media hora";
            default:
                return "Ni ocio ni foco largo en la última media hora";
        }
    }

    /**
     * @param firstUnlockAfterFive hora (epoch ms) del primer desbloqueo de hoy después de las
     *     05:00, o -1
     * @param wakeDone el reto de despertar de hoy ya se completó
     */
    public static Snapshot step(
            Snapshot prev,
            List<Fact> facts,
            long now,
            ZoneId zone,
            boolean autoTime,
            long firstUnlockAfterFive,
            boolean wakeDone) {
        if (prev == null) prev = Snapshot.initial(now);
        LocalDateTime t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone);
        // DESPERTAR y NOCHE: horarios, sin histéresis.
        if (wakeWindow(t, autoTime, firstUnlockAfterFive, zone) && !wakeDone)
            return enter(prev, Estado.DESPERTAR, now, "Buenos días: tres pasos para empezar");
        if (AbsoluteRules.night(t, autoTime))
            return enter(
                    prev,
                    Estado.NOCHE,
                    now,
                    autoTime
                            ? "Ocio cerrado hasta las " + AbsoluteRules.nightEnd() + ":00"
                            : "Hora automática apagada: Foco lo trata como noche");
        Window w = window(facts, now);
        Estado raw = raw(w);
        long lastProd = raw == Estado.PRODUCTIVO ? now : prev.lastProductiveAt;
        // Al salir de la noche o del despertar, se empieza en el estado de día sin esperar.
        if (prev.estado == Estado.NOCHE || prev.estado == Estado.DESPERTAR)
            return new Snapshot(raw, now, null, -1, lastProd, reason(raw, w));
        if (raw == prev.estado)
            return new Snapshot(prev.estado, prev.since, null, -1, lastProd, reason(raw, w));
        // PRODUCTIVO se mantiene 15 min tras parar, frente a NEUTRO.
        if (prev.estado == Estado.PRODUCTIVO
                && raw == Estado.NEUTRO
                && lastProd >= 0
                && now - lastProd < PRODUCTIVE_HOLD_MS)
            return new Snapshot(
                    Estado.PRODUCTIVO,
                    prev.since,
                    null,
                    -1,
                    lastProd,
                    "Sigues en buena racha de foco (pausa corta)");
        long candSince =
                prev.candidate == raw && prev.candidateSince >= 0 ? prev.candidateSince : now;
        if (now - candSince >= HYSTERESIS_MS)
            return new Snapshot(raw, now, null, -1, lastProd, reason(raw, w));
        return new Snapshot(prev.estado, prev.since, raw, candSince, lastProd, prev.motivo);
    }

    private static Snapshot enter(Snapshot prev, Estado e, long now, String motivo) {
        long since = prev.estado == e ? prev.since : now;
        return new Snapshot(e, since, null, -1, prev.lastProductiveAt, motivo);
    }

    static boolean wakeWindow(
            LocalDateTime t, boolean autoTime, long firstUnlockAfterFive, ZoneId zone) {
        if (!autoTime || firstUnlockAfterFive < 0) return false;
        int h = t.getHour();
        if (h < WAKE_FROM_HOUR || h >= AbsoluteRules.nightEnd()) return false;
        LocalDateTime u = LocalDateTime.ofInstant(Instant.ofEpochMilli(firstUnlockAfterFive), zone);
        return u.toLocalDate().equals(t.toLocalDate()) && u.getHour() >= WAKE_FROM_HOUR;
    }

    /** Recorta hechos viejos (más de la ventana) para guardarlos. */
    public static List<Fact> prune(List<Fact> facts, long now) {
        List<Fact> out = new ArrayList<>();
        if (facts == null) return out;
        for (Fact f : facts) if (now - f.wall <= WINDOW_MS && f.wall <= now + MIN) out.add(f);
        return out;
    }

    // ---------- Días cumplidos (racha_dias sin precipicio) ----------

    /** Un día cuenta como cumplido con 20 min invertidos o un reto superado. */
    public static final long DAY_DONE_MS = 20 * MIN;

    public static boolean dayDone(long investedTodayMs, int retosToday) {
        return investedTodayMs >= DAY_DONE_MS || retosToday > 0;
    }

    /**
     * racha_dias: días cumplidos EN TOTAL. Nunca vuelve a 0: descansar un día no borra nada (la
     * misma decisión que en Cognitive Lab). Devuelve el nuevo total.
     */
    public static int countDay(int total, String lastCountedDay, LocalDate today, boolean done) {
        if (!done || today.toString().equals(lastCountedDay)) return total;
        return total + 1;
    }

    /** Hitos que se celebran (etapa 6). */
    public static final int[] MILESTONES = {3, 7, 14};

    public static boolean milestone(int before, int after) {
        for (int m : MILESTONES) if (before < m && after >= m) return true;
        return false;
    }
}
