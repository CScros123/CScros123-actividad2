package es.foco.next.core;

import java.time.*;
import java.util.*;
import java.util.function.Predicate;

/**
 * Lote 1: decide si una app queda bloqueada por algún bloqueo con nombre. Puro y determinista. Las
 * esenciales, llamadas y pantalla apagada se filtran en el servicio antes de llamar aquí.
 */
public final class RuleEngine {
    private RuleEngine() {}

    public static final long MAX_DELTA = 15 * 60_000L;

    public static final class Decision {
        public boolean blocked;

        /** Bloqueo que manda (el que se reinicia más tarde si hay varios). */
        public BlockRule rule;

        /**
         * Uso restante mínimo entre los bloqueos activos de la app (Long.MAX_VALUE si no hay
         * ninguno).
         */
        public long remaining = Long.MAX_VALUE;

        public long resetsIn;

        /** Cuándo volver a evaluar (ms, 0 = nada pendiente). */
        public long next;

        public int pausesLeft;
        public boolean changed;
    }

    /** Reinicia ventana o día y pausas del día. Devuelve true si cambió algo. */
    public static boolean roll(BlockRule r, RuleState.Entry e, long wall, String day) {
        boolean changed = false;
        if (r.period == BlockRule.Period.DAILY) {
            if (!day.equals(e.usageDay)) {
                e.usageDay = day;
                e.usedMillis = 0;
                e.windowStart = -1;
                changed = true;
            }
        } else if (e.windowStart >= 0
                && (wall >= e.windowStart + r.windowMillis() || wall < e.windowStart)) {
            e.windowStart = -1;
            e.usedMillis = 0;
            changed = true;
        } else if (e.windowStart < 0 && e.usedMillis != 0) {
            e.usedMillis = 0;
            changed = true;
        }
        if (!day.equals(e.pausesDay)) {
            e.pausesDay = day;
            e.pausesUsed = 0;
            changed = true;
        }
        if (e.pausedUntil > 0
                && (wall >= e.pausedUntil || e.pausedUntil - wall > r.pauseMinutes * 60_000L)) {
            e.pausedUntil = 0;
            changed = true;
        }
        return changed;
    }

    public static boolean paused(RuleState.Entry e, long wall) {
        return e.pausedUntil > wall;
    }

    /**
     * Límite alcanzado (o dentro del horario de bloqueo) y sin pausa: el bloqueo está bloqueando.
     */
    public static boolean active(BlockRule r, RuleState s, long wall, String day) {
        return active(r, s, wall, day, utc(wall));
    }

    /** Lote 2: igual, con la hora local para los horarios. */
    public static boolean active(BlockRule r, RuleState s, ZonedDateTime now) {
        return active(r, s, millis(now), now.toLocalDate().toString(), now);
    }

    static boolean active(BlockRule r, RuleState s, long wall, String day, ZonedDateTime now) {
        if (r == null || !r.enabled || (!r.allowlist && r.packages.isEmpty())) return false;
        RuleState.Entry e = s.of(r.id);
        roll(r, e, wall, day);
        if (paused(e, wall)) return false;
        if (r.scheduled() && !inWindow(r, now)) return false;
        if (r.scheduled() && r.scheduleMode == BlockRule.ScheduleMode.BLOCK_IN_WINDOW) return true;
        return e.usedMillis >= r.limitMillis();
    }

    public static boolean anyActive(List<BlockRule> rules, RuleState s, long wall, String day) {
        for (BlockRule r : rules) if (active(r, s, wall, day)) return true;
        return false;
    }

    public static boolean anyActive(List<BlockRule> rules, RuleState s, ZonedDateTime now) {
        for (BlockRule r : rules) if (active(r, s, now)) return true;
        return false;
    }

    /**
     * Lote 5: para el modo estricto. Bloqueos activos y, además, reglas solo de filtro (web,
     * compras o secciones) que tienen horario y están dentro de su franja. Las reglas de filtro sin
     * horario no bloquean Ajustes.
     */
    public static boolean strictActive(List<BlockRule> rules, RuleState s, ZonedDateTime now) {
        for (BlockRule r : rules) {
            if (active(r, s, now)) return true;
            if (r.scheduled()
                    && (WebFilter.active(r, s, now)
                            || PurchaseGuard.active(r, s, now)
                            || InAppFilter.active(r, s, now))) return true;
        }
        return false;
    }

    /** Lote 2: la hora local cae dentro de alguna ventana del bloqueo (sin horario = siempre). */
    public static boolean inWindow(BlockRule r, ZonedDateTime now) {
        if (!r.scheduled()) return true;
        for (Schedule x : r.schedules) if (x.contains(now)) return true;
        return false;
    }

    /**
     * Lote 2: ms hasta el próximo inicio o fin de ventana (0 si no hay horario). Para programar la
     * reevaluación.
     */
    public static long nextBoundary(BlockRule r, ZonedDateTime now) {
        ZonedDateTime best = null;
        if (r.scheduled())
            for (Schedule x : r.schedules) {
                ZonedDateTime b = x.nextBoundary(now);
                if (b != null && (best == null || b.isBefore(best))) best = b;
            }
        return best == null ? 0 : Math.max(1, millis(best) - millis(now));
    }

    /**
     * Lote 2: ms hasta que termine la ventana actual (ventanas encadenadas cuentan como una). 0 si
     * fuera de horario.
     */
    public static long windowEndsIn(BlockRule r, ZonedDateTime now) {
        if (!r.scheduled() || !inWindow(r, now)) return 0;
        ZonedDateTime t = now;
        for (int i = 0; i < 32; i++) {
            long b = nextBoundary(r, t);
            if (b <= 0) return 0;
            t = t.plusNanos(b * 1_000_000L);
            if (!inWindow(r, t)) return Math.max(1, millis(t) - millis(now));
        }
        return Math.max(1, millis(t) - millis(now));
    }

    /**
     * Lote 2: la regla se aplica a esta app (lista blanca: todas salvo las suyas y las exentas).
     */
    public static boolean applies(BlockRule r, String pkg, Predicate<String> exempt) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (!r.allowlist) return r.packages.contains(pkg);
        return !r.packages.contains(pkg) && (exempt == null || !exempt.test(pkg));
    }

    static long millis(ZonedDateTime t) {
        return t.toInstant().toEpochMilli();
    }

    static ZonedDateTime utc(long wall) {
        return Instant.ofEpochMilli(wall).atZone(ZoneOffset.UTC);
    }

    public static long resetsIn(BlockRule r, RuleState.Entry e, long wall, long millisToday) {
        if (r.period == BlockRule.Period.DAILY) return Math.max(1, 24 * 60 * 60_000L - millisToday);
        if (e.windowStart < 0) return 0;
        return Math.max(1, e.windowStart + r.windowMillis() - wall);
    }

    public static int pausesLeft(BlockRule r, RuleState.Entry e) {
        return Math.max(0, r.maxPauses - e.pausesUsed);
    }

    /**
     * @param delta tiempo en primer plano de {@code pkg} desde la última llamada (ya filtrado por
     *     el servicio)
     * @param millisToday ms transcurridos desde la medianoche local (para el reinicio diario)
     */
    public static Decision decide(
            List<BlockRule> rules,
            RuleState s,
            String pkg,
            long wall,
            String day,
            long millisToday,
            long delta) {
        return decide(rules, s, pkg, wall, day, millisToday, utc(wall), null, delta);
    }

    /**
     * Lote 2: con hora local (horarios) y {@code exempt}: apps que una lista blanca nunca bloquea
     * (esenciales, Inicio, teléfono, Ajustes, Foco, apps sin icono de lanzador).
     */
    public static Decision decide(
            List<BlockRule> rules,
            RuleState s,
            String pkg,
            ZonedDateTime now,
            Predicate<String> exempt,
            long delta) {
        long wall = millis(now);
        long today = wall - millis(now.toLocalDate().atStartOfDay(now.getZone()));
        return decide(rules, s, pkg, wall, now.toLocalDate().toString(), today, now, exempt, delta);
    }

    static Decision decide(
            List<BlockRule> rules,
            RuleState s,
            String pkg,
            long wall,
            String day,
            long millisToday,
            ZonedDateTime now,
            Predicate<String> exempt,
            long delta) {
        Decision d = new Decision();
        delta = Math.max(0, Math.min(MAX_DELTA, delta));
        long next = 0;
        for (BlockRule r : rules) {
            if (r == null) continue;
            RuleState.Entry e = s.of(r.id);
            if (roll(r, e, wall, day)) d.changed = true;
            if (e.pausedUntil > 0) next = min(next, e.pausedUntil - wall);
            if (r.enabled && r.scheduled()) {
                long b = nextBoundary(r, now);
                if (b > 0) next = min(next, b);
            }
            if (!r.enabled || !applies(r, pkg, exempt)) continue;
            if (paused(e, wall)) continue;
            if (r.scheduled() && !inWindow(r, now))
                continue; // fuera de horario: ni cuenta ni bloquea
            if (r.scheduled() && r.scheduleMode == BlockRule.ScheduleMode.BLOCK_IN_WINDOW) {
                block(d, r, e, windowEndsIn(r, now));
                continue;
            }
            long limit = r.limitMillis();
            if (e.usedMillis < limit && delta > 0) {
                if (r.period == BlockRule.Period.WINDOW && e.windowStart < 0)
                    e.windowStart = wall - delta;
                e.usedMillis = Math.min(limit, e.usedMillis + delta);
                d.changed = true;
            } else if (e.usedMillis < limit
                    && r.period == BlockRule.Period.WINDOW
                    && e.windowStart < 0) {
                e.windowStart = wall;
                d.changed = true; // la ventana empieza al abrir una app del bloqueo
            }
            long left = Math.max(0, limit - e.usedMillis);
            if (left > 0) {
                d.remaining = Math.min(d.remaining, left);
                next = min(next, left);
                continue;
            }
            long reset = resetsIn(r, e, wall, millisToday);
            if (r.scheduled()) {
                long end = windowEndsIn(r, now);
                if (end > 0 && (reset <= 0 || end < reset)) reset = end;
            }
            block(d, r, e, reset);
            if (reset > 0) next = min(next, reset);
        }
        if (d.blocked) d.remaining = 0;
        d.next = next;
        return d;
    }

    private static void block(Decision d, BlockRule r, RuleState.Entry e, long reset) {
        if (!d.blocked || reset > d.resetsIn) {
            d.blocked = true;
            d.rule = r;
            d.resetsIn = reset;
            d.pausesLeft = pausesLeft(r, e);
        }
    }

    /** Pausa el bloqueo N minutos si quedan pausas hoy. */
    public static boolean pause(BlockRule r, RuleState s, long wall, String day) {
        if (r == null) return false;
        RuleState.Entry e = s.of(r.id);
        roll(r, e, wall, day);
        if (paused(e, wall) || e.pausesUsed >= r.maxPauses) return false;
        e.pausesUsed++;
        e.pausedUntil = wall + r.pauseMinutes * 60_000L;
        return true;
    }

    /**
     * Lote 1: añade una app recién instalada a los bloqueos que lo piden. Devuelve los nombres
     * afectados.
     */
    public static List<String> addInstalled(List<BlockRule> rules, String pkg) {
        List<String> names = new ArrayList<>();
        for (BlockRule r : rules)
            if (r.autoAddNew && !r.allowlist && r.packages.add(pkg)) names.add(r.name);
        return names;
    }

    private static long min(long next, long v) {
        v = Math.max(1, v);
        return next <= 0 ? v : Math.min(next, v);
    }
}
