package es.foco.next.core;

import java.time.*;

/**
 * Modo estricto reforzado: candado de tiempo. Lógica pura y testeable.
 *
 * <p>El tiempo que cuenta para soltar el candado es un contador propio ({@link #credited}) que solo
 * avanza con el reloj monótono ({@code elapsedRealtime}) mientras no haya reinicio. Tras un
 * reinicio solo puede avanzar con el reloj de pared, y nunca hacia atrás: si la hora del teléfono
 * se retrasa, el candado sigue activo; si se adelanta sin reiniciar, tampoco cuenta. Así cambiar la
 * fecha y hora no acorta la espera.
 *
 * <p>"Siempre": no caduca solo. "Solicitar desactivar" arranca una espera de 24 h; al terminar se
 * puede apagar. La solicitud se puede cancelar.
 */
public final class StrictLock {
    public static final long HOUR = 3_600_000L, DAY = 24 * HOUR;

    /** Candado puesto (modo estricto encendido con duración). */
    public boolean active;

    /** "Siempre (24 h de espera para desactivar)". */
    public boolean always;

    /**
     * Valor de {@link #credited} a partir del cual se puede apagar; -1 = sin fecha (siempre sin
     * solicitud).
     */
    public long unlockAt = -1;

    /** Tiempo de confianza acumulado (ms). */
    public long credited;

    /** Última observación: reloj de pared, reloj monótono, número de arranque. */
    public long lastWall, lastElapsed;

    public int lastBoot = -1;

    /**
     * Avanza el contador con una nueva observación del reloj. Devuelve true si algo cambió (para
     * guardar).
     */
    public boolean observe(long wall, long elapsed, int boot) {
        long delta;
        if (boot == lastBoot && boot != -1 && elapsed >= lastElapsed)
            delta = elapsed - lastElapsed; // mismo arranque: reloj monótono
        else if (boot == -1 && lastBoot == -1 && elapsed >= lastElapsed && lastElapsed > 0)
            delta =
                    Math.min(
                            elapsed - lastElapsed,
                            Math.max(0, wall - lastWall)); // sin número de arranque: el menor
        else
            delta = Math.max(0, wall - lastWall); // reinicio: pared, nunca hacia atrás (lastWall es
        // la máxima vista)
        if (lastWall == 0 && lastElapsed == 0) delta = 0; // primera observación
        boolean changed =
                delta != 0 || wall != lastWall || elapsed != lastElapsed || boot != lastBoot;
        credited += delta;
        // Si la pared retrocede, se conserva la máxima vista: tras un reinicio no se cuenta el
        // tiempo "recuperado".
        lastWall = Math.max(lastWall, wall);
        lastElapsed = elapsed;
        lastBoot = boot;
        return changed;
    }

    /** Pone el candado durante {@code millis} (lo más largo gana si ya había uno). */
    public void lockFor(long millis, long wall, long elapsed, int boot) {
        observe(wall, elapsed, boot);
        long until = credited + Math.max(0, millis);
        if (active && always) return; // "siempre" ya es lo más estricto
        unlockAt = active ? Math.max(unlockAt, until) : until;
        active = true;
        always = false;
    }

    public void lockAlways(long wall, long elapsed, int boot) {
        observe(wall, elapsed, boot);
        active = true;
        always = true;
        unlockAt = -1;
    }

    /** Siempre: arranca la espera de 24 h. Devuelve false si no aplica o ya estaba solicitada. */
    public boolean requestUnlock(long wall, long elapsed, int boot) {
        observe(wall, elapsed, boot);
        if (!active || !always || unlockAt >= 0) return false;
        unlockAt = credited + DAY;
        return true;
    }

    public boolean cancelRequest() {
        if (!active || !always || unlockAt < 0) return false;
        unlockAt = -1;
        return true;
    }

    public boolean requested() {
        return active && always && unlockAt >= 0;
    }

    /** true mientras el candado impide apagar el modo estricto. */
    public boolean locked() {
        return active && (unlockAt < 0 || credited < unlockAt);
    }

    /**
     * Milisegundos que faltan; -1 si no hay fecha ("siempre" sin solicitud); 0 si ya se puede
     * apagar.
     */
    public long remaining() {
        if (!active) return 0;
        if (unlockAt < 0) return -1;
        return Math.max(0, unlockAt - credited);
    }

    /** Quita el candado. Solo si ya no está bloqueado. */
    public boolean release() {
        if (locked()) return false;
        active = false;
        always = false;
        unlockAt = -1;
        return true;
    }

    /** Milisegundos hasta las 07:00 siguientes (hoy si aún no son las 7; si no, mañana). */
    public static long untilSeven(ZonedDateTime now) {
        ZonedDateTime t = now.toLocalDate().atTime(7, 0).atZone(now.getZone());
        if (!t.isAfter(now)) t = now.toLocalDate().plusDays(1).atTime(7, 0).atZone(now.getZone());
        return Duration.between(now, t).toMillis();
    }

    /** Milisegundos hasta una fecha y hora elegidas; 0 si ya pasó. */
    public static long until(ZonedDateTime now, LocalDateTime target) {
        return Math.max(0, Duration.between(now, target.atZone(now.getZone())).toMillis());
    }

    public String encode() {
        return "1;"
                + (active ? 1 : 0)
                + ";"
                + (always ? 1 : 0)
                + ";"
                + unlockAt
                + ";"
                + credited
                + ";"
                + lastWall
                + ";"
                + lastElapsed
                + ";"
                + lastBoot;
    }

    public static StrictLock decode(String s) {
        StrictLock l = new StrictLock();
        if (s == null) return l;
        try {
            String[] p = s.split(";");
            if (p.length < 8 || !"1".equals(p[0])) return l;
            l.active = "1".equals(p[1]);
            l.always = "1".equals(p[2]);
            l.unlockAt = Long.parseLong(p[3]);
            l.credited = Math.max(0, Long.parseLong(p[4]));
            l.lastWall = Long.parseLong(p[5]);
            l.lastElapsed = Long.parseLong(p[6]);
            l.lastBoot = Integer.parseInt(p[7]);
        } catch (RuntimeException e) {
            return new StrictLock();
        }
        return l;
    }
}
