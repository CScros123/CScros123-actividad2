package es.foco.next.core.home;

/**
 * Etapa 5.4: DNS automático de respaldo. Puro: recibe resultados de pruebas de resolución y dice qué
 * modo de DNS privado poner. Si el proveedor falla {@link #FAILS_TO_FALLBACK} veces seguidas, pasa
 * a automático {@link #FALLBACK_MS}, lo restaura y vuelve a probar.
 */
public final class DnsFallback {
    public static final int FAILS_TO_FALLBACK = 2;
    public static final long CHECK_EVERY_MS = 15 * 60_000L, FALLBACK_MS = 10 * 60_000L;

    public enum Mode {
        PROVIDER,
        AUTOMATIC
    }

    public final int fails;
    public final long fallbackUntil; // -1 si no hay respaldo activo
    public final Mode mode;

    public DnsFallback(int fails, long fallbackUntil, Mode mode) {
        this.fails = Math.max(0, fails);
        this.fallbackUntil = fallbackUntil;
        this.mode = mode == null ? Mode.PROVIDER : mode;
    }

    public static DnsFallback start() {
        return new DnsFallback(0, -1, Mode.PROVIDER);
    }

    /** Resultado de una prueba con el proveedor puesto. */
    public DnsFallback probe(boolean resolved, long now) {
        if (mode == Mode.AUTOMATIC) return tick(now);
        if (resolved) return new DnsFallback(0, -1, Mode.PROVIDER);
        int f = fails + 1;
        if (f >= FAILS_TO_FALLBACK) return new DnsFallback(0, now + FALLBACK_MS, Mode.AUTOMATIC);
        return new DnsFallback(f, -1, Mode.PROVIDER);
    }

    /** Pasados los 10 min de respaldo, se vuelve al proveedor (y se probará de nuevo). */
    public DnsFallback tick(long now) {
        if (mode == Mode.AUTOMATIC && fallbackUntil >= 0 && now >= fallbackUntil)
            return new DnsFallback(0, -1, Mode.PROVIDER);
        return this;
    }

    /** Milisegundos hasta la próxima comprobación. */
    public long nextCheckIn(long now) {
        if (mode == Mode.AUTOMATIC) return Math.max(1_000, fallbackUntil - now);
        return fails > 0 ? 60_000L : CHECK_EVERY_MS; // tras un fallo, se reintenta en 1 min
    }
}
