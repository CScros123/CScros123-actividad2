package es.foco.next.core.care;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

/**
 * Foco Core (2+2): estado del widget, tareas vencidas con margen, penalización de 1 h, hora libre
 * al completar el día y análisis de la semana. Lógica pura: los datos vienen de RoutineStore.
 */
public final class FocoCore {
    private FocoCore() {}

    public enum State {
        NO_PLAN,
        RUNNING,
        PENALTY,
        COMPLETE
    }

    /** Margen tras la hora límite antes de marcar incumplida. */
    public static final long GRACE_MS = 10 * 60_000L;

    public static final long PENALTY_MS = 60 * 60_000L;
    public static final long FREE_MS = 60 * 60_000L;
    public static final int MIN_TASKS = 2;

    public static State state(int total, int done, long penaltyUntil, long now) {
        return state(total, done, total, penaltyUntil, now);
    }

    /**
     * {@code planned}: tareas propias de hoy (eventos y listas de esa fecha o de ese día de la
     * semana). Las rutinas de siempre no cuentan como plan: con menos de 2 propias, el búho pide
     * anotar los pendientes del día.
     */
    public static State state(int total, int done, int planned, long penaltyUntil, long now) {
        if (penaltyUntil > now) return State.PENALTY;
        if (planned < MIN_TASKS) return State.NO_PLAN;
        if (done >= total) return State.COMPLETE;
        return State.RUNNING;
    }

    /** Minutos del día. */
    public static int minuteOfDay(LocalTime t) {
        return t.getHour() * 60 + t.getMinute();
    }

    /** ¿Ya pasó la hora límite más el margen de 10 min? */
    public static boolean missed(Routine.Task t, boolean done, LocalTime now) {
        return missedAt(t, done, minuteOfDay(now));
    }

    /** ¿Toca el aviso previo? (desde la hora límite hasta que vence el margen). */
    public static boolean warnDue(Routine.Task t, boolean done, LocalTime now) {
        return warnDueAt(t, done, minuteOfDay(now));
    }

    /**
     * v32 (R9): minuto dentro del día lógico de Foco Core (empieza a las 04:00): de 00:00 a 03:59
     * sigue siendo el día anterior, así que cuenta como 24:00 a 27:59.
     */
    public static int logicalMinute(LocalTime t) {
        int m = minuteOfDay(t);
        return t.getHour() < 4 ? m + 24 * 60 : m;
    }

    public static boolean missedAt(Routine.Task t, boolean done, int minute) {
        return t.window >= 0 && !done && minute * 60_000L >= t.window * 60_000L + GRACE_MS;
    }

    public static boolean warnDueAt(Routine.Task t, boolean done, int minute) {
        return t.window >= 0 && !done && minute >= t.window && !missedAt(t, false, minute);
    }

    // v36: aviso insistente antes de la hora límite (de una tarea o de toda la franja).
    /** Empieza a insistir estos minutos antes de la hora. */
    public static final int NAG_LEAD_MIN = 30;

    /** Cada cuánto insiste. */
    public static final long NAG_EVERY_MS = 5 * 60_000L;

    /** Horas límite por defecto de las franjas: mañana a las 9:00, tarde y noche a las 23:00. */
    public static final int MORNING_DEADLINE = 9 * 60, NIGHT_DEADLINE = 23 * 60;

    /**
     * Hora límite en el día de Foco (que empieza a las 04:00): 0:30 cuenta como 24:30. -1 = sin
     * hora.
     */
    public static int logicalDeadline(int minuteOfDay) {
        if (minuteOfDay < 0) return -1;
        return minuteOfDay < 4 * 60 ? minuteOfDay + 24 * 60 : minuteOfDay;
    }

    /**
     * ¿Toca insistir? Desde {@link #NAG_LEAD_MIN} antes de la hora hasta que vence el margen de 10
     * min, mientras no esté hecho. {@code deadline} y {@code minute} en minutos del día de Foco.
     */
    public static boolean nagAt(int deadline, boolean done, int minute) {
        return deadline >= 0
                && !done
                && minute >= deadline - NAG_LEAD_MIN
                && minute * 60_000L < deadline * 60_000L + GRACE_MS;
    }

    /** ¿Ya pasaron 5 min desde el último aviso? */
    public static boolean nagAgain(long lastNagMs, long nowMs) {
        return lastNagMs <= 0 || nowMs - lastNagMs >= NAG_EVERY_MS - 30_000L;
    }

    /** «Quedan 12 min (hasta las 9:00)» o «Ya es la hora: 6 min de margen». */
    public static String nagText(int deadline, int minute) {
        int left = deadline - minute;
        int h = (deadline / 60) % 24, m = deadline % 60;
        String at = h + ":" + (m < 10 ? "0" : "") + m;
        if (left > 0) return "Quedan " + left + " min (hasta las " + at + ")";
        long grace = GRACE_MS / 60_000L + left;
        return "Ya son las " + at + ": " + Math.max(0, grace) + " min de margen";
    }

    /**
     * Penalización por una tarea vencida: 1 h desde ahora, pero no se acumula (si ya hay una activa
     * no se crea otra) y nunca de noche (la noche ya cierra el ocio). 0 si no corresponde.
     */
    public static long penaltyUntil(long now, long currentUntil, boolean night) {
        if (night || currentUntil > now) return 0;
        return now + PENALTY_MS;
    }

    /** Fin de la hora libre: una hora, recortada al comienzo de la noche. 0 si ya no queda. */
    public static long freeUntil(LocalDateTime now, long nowMs, int nightStartHour) {
        LocalDateTime cut = now.toLocalDate().atTime(nightStartHour, 0);
        long left = java.time.Duration.between(now, cut).toMillis();
        if (now.getHour() < 4 || left <= 60_000L) return 0;
        return nowMs + Math.min(FREE_MS, left);
    }

    /** Hora a la que terminarías lo que falta, empezando ya. */
    public static LocalTime projectedFinish(LocalTime now, int remainingMin) {
        return now.plusMinutes(Math.max(0, remainingMin));
    }

    public static String hm(LocalTime t) {
        return t.getHour() + ":" + String.format(Locale.ROOT, "%02d", t.getMinute());
    }

    /**
     * Siguiente tarea: la que vence antes; si ninguna tiene hora, la primera pendiente. -1 si no
     * hay.
     */
    public static int next(List<Routine.Task> tasks, java.util.Set<Integer> done) {
        int best = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (done.contains(i)) continue;
            Routine.Task t = tasks.get(i);
            if (best < 0) best = i;
            else {
                Routine.Task b = tasks.get(best);
                if (t.window >= 0 && (b.window < 0 || t.window < b.window)) best = i;
            }
        }
        return best;
    }

    // ---------------- Análisis de la semana ----------------

    public static final class Day {
        public final int morningTotal,
                morningDone,
                nightTotal,
                nightDone,
                eventTotal,
                eventDone,
                penalties;
        public final long spent, allowance;

        public Day(
                int morningTotal,
                int morningDone,
                int nightTotal,
                int nightDone,
                int eventTotal,
                int eventDone,
                int penalties,
                long spent,
                long allowance) {
            this.morningTotal = morningTotal;
            this.morningDone = morningDone;
            this.nightTotal = nightTotal;
            this.nightDone = nightDone;
            this.eventTotal = eventTotal;
            this.eventDone = eventDone;
            this.penalties = penalties;
            this.spent = spent;
            this.allowance = allowance;
        }

        public int total() {
            return morningTotal + nightTotal + eventTotal;
        }

        public int done() {
            return morningDone + nightDone + eventDone;
        }
    }

    private static int pct(int done, int total) {
        return total <= 0 ? -1 : Math.round(done * 100f / total);
    }

    /**
     * Lo que dice el búho el fin de semana: la franja más difícil, el balance de dinero y 2 o 3
     * recomendaciones concretas. Sin reproches.
     */
    public static String weekly(List<Day> days, long weekBudget, long weekSpent) {
        int mt = 0, md = 0, nt = 0, nd = 0, et = 0, ed = 0, pen = 0;
        for (Day d : days) {
            mt += d.morningTotal;
            md += d.morningDone;
            nt += d.nightTotal;
            nd += d.nightDone;
            et += d.eventTotal;
            ed += d.eventDone;
            pen += d.penalties;
        }
        StringBuilder b = new StringBuilder();
        int all = pct(md + nd + ed, mt + nt + et);
        if (all < 0)
            return "Esta semana no hubo tareas planificadas. El domingo es buen momento para anotar 2 por la mañana y 2 por la tarde.";
        b.append("Cumpliste el ").append(all).append(" % de lo planificado.");
        int pm = pct(md, mt), pn = pct(nd, nt);
        String hard = null;
        int hardPct = 101;
        if (pm >= 0 && pm < hardPct) {
            hard = "la mañana";
            hardPct = pm;
        }
        if (pn >= 0 && pn < hardPct) {
            hard = "la tarde y noche";
            hardPct = pn;
        }
        if (hard != null && hardPct < 100)
            b.append(" La franja más difícil fue ")
                    .append(hard)
                    .append(" (")
                    .append(hardPct)
                    .append(" %).");
        if (weekBudget > 0) {
            long bal = weekBudget - weekSpent;
            b.append(
                    bal >= 0
                            ? " Te sobraron " + Money.fmt(bal) + " del presupuesto."
                            : " Te pasaste " + Money.fmt(-bal) + " del presupuesto.");
        } else if (weekSpent > 0)
            b.append(" Anotaste ").append(Money.fmt(weekSpent)).append(" de gastos.");
        b.append("\n\nPara la próxima semana:");
        int tips = 0;
        if (hard != null && hardPct < 70) {
            b.append("\n· En ")
                    .append(hard)
                    .append(
                            " deja solo tareas fáciles o medias y pasa la difícil a la otra franja.");
            tips++;
        }
        if (pen >= 3) {
            b.append("\n· Tuviste ")
                    .append(pen)
                    .append(
                            " bloqueos por horas límite: dales 30 min más de margen a las que vencen.");
            tips++;
        }
        if (weekBudget > 0 && weekSpent > weekBudget) {
            b.append(
                    "\n· Anota el monto antes de entrar al banco y reparte el presupuesto el domingo.");
            tips++;
        } else if (weekBudget > 0 && weekSpent < weekBudget) {
            b.append("\n· Lo que sobró, apártalo para lo que debes o para algo que planifiques.");
            tips++;
        }
        if (all >= 85 && tips < 3) {
            b.append("\n· Semana fuerte: sube un poco la dificultad de una tarea.");
            tips++;
        }
        if (tips == 0)
            b.append(
                    "\n· Mantén el 2+2: dos tareas por la mañana y dos por la tarde, con su hora.");
        return b.toString();
    }
}
