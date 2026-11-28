package es.foco.next.core;

/**
 * Lote 2: bloqueo rápido o Pomodoro. No es un bloqueo con nombre: bloquea a la vez todas las apps
 * marcadas en cualquier bloqueo (salvo listas blancas) y las del Foco clásico, sin pausas. En
 * Pomodoro solo las fases de enfoque bloquean; los descansos desbloquean. Puro: todas las cuentas a
 * partir del reloj de pared.
 */
public final class FocusSession {
    public enum Kind {
        QUICK,
        POMODORO
    }

    public static final int[] QUICK_CHOICES = {15, 30, 60, 120};
    public Kind kind = Kind.QUICK;
    public long start, until;
    public int focusMinutes = 25, breakMinutes = 5, cycles = 4;

    public static final class Phase {
        public boolean done, focus;
        public int cycle, cycles;
        public long endsAt;
    }

    public static FocusSession quick(long now, int minutes) {
        FocusSession s = new FocusSession();
        s.kind = Kind.QUICK;
        s.start = now;
        s.until = now + Math.max(1, Math.min(24 * 60, minutes)) * 60_000L;
        return s;
    }

    public static FocusSession pomodoro(long now, int focus, int pause, int cycles) {
        FocusSession s = new FocusSession();
        s.kind = Kind.POMODORO;
        s.start = now;
        s.focusMinutes = focus;
        s.breakMinutes = pause;
        s.cycles = cycles;
        s.clamp();
        s.until = s.end();
        return s;
    }

    public void clamp() {
        if (kind == null) kind = Kind.QUICK;
        focusMinutes = Math.max(1, Math.min(180, focusMinutes));
        breakMinutes = Math.max(1, Math.min(60, breakMinutes));
        cycles = Math.max(1, Math.min(12, cycles));
        if (kind == Kind.POMODORO) until = end();
    }

    public FocusSession copy() {
        FocusSession s = new FocusSession();
        s.kind = kind;
        s.start = start;
        s.until = until;
        s.focusMinutes = focusMinutes;
        s.breakMinutes = breakMinutes;
        s.cycles = cycles;
        return s;
    }

    public long end() {
        if (kind == Kind.QUICK) return until;
        return start
                + cycles * focusMinutes * 60_000L
                + (cycles - 1) * (long) breakMinutes * 60_000L;
    }

    public Phase phase(long wall) {
        Phase p = new Phase();
        p.cycles = kind == Kind.QUICK ? 1 : cycles;
        // Un reloj atrasado más de un día no puede alargar la sesión indefinidamente.
        if (wall >= end() || wall < start - 24 * 60 * 60_000L) {
            p.done = true;
            return p;
        }
        long t = Math.max(0, wall - start);
        if (kind == Kind.QUICK) {
            p.focus = true;
            p.cycle = 1;
            p.endsAt = until;
            return p;
        }
        long f = focusMinutes * 60_000L,
                per = f + breakMinutes * 60_000L,
                k = t / per,
                within = t % per;
        p.cycle = (int) k + 1;
        p.focus = within < f;
        p.endsAt = start + k * per + (p.focus ? f : per);
        return p;
    }

    public boolean focusing(long wall) {
        Phase p = phase(wall);
        return !p.done && p.focus;
    }

    /** "Bloqueo rápido" o "Pomodoro 2/4 · enfoque" / "· descanso". */
    public String title(long wall) {
        if (kind == Kind.QUICK) return "Bloqueo rápido";
        Phase p = phase(wall);
        if (p.done) return "Pomodoro terminado";
        return "Pomodoro " + p.cycle + "/" + p.cycles + " · " + (p.focus ? "enfoque" : "descanso");
    }
}
