package es.foco.next.core.home;

/**
 * Peaje de intención ("Aduana", pura). Antes de abrir una app de ocio desde el inicio, 5 s de
 * respiración y elegir 1, 3 o 5 min. El bloque se paga igual que siempre (5 min: gratis del día o 5
 * del banco; Regla 4 intacta): elegir menos solo hace que Foco cierre la app antes. Solo endurece:
 * nunca da más de 5 minutos.
 */
public final class Aduana {
    private Aduana() {}

    public static final long BREATH_MS = 5_000;

    /**
     * Una respiración completa (inhalar 1,25 s + exhalar 1,25 s) antes de poder elegir: corta los
     * toques automáticos por memoria muscular sin hacer esperar de más.
     */
    public static final long UNLOCK_MS = 2_500;

    /** Opción destacada como recomendada. */
    public static final int RECOMMENDED_MIN = 1;

    public static final int[] CHOICES_MIN = {1, 3, 5};

    /** Lo elegido vale como mucho una ventana de ocio (60 min) y después se olvida. */
    public static final long VALID_MS = 60 * 60_000L;

    /** Límite elegido para una app. */
    public static final class Limit {
        public final String pkg;
        public final long chosenAt, until;

        public Limit(String pkg, long chosenAt, int minutes) {
            this.pkg = pkg == null ? "" : pkg;
            this.chosenAt = chosenAt;
            int m = Math.max(1, Math.min(5, minutes));
            this.until = chosenAt + m * 60_000L;
        }

        public Limit(String pkg, long chosenAt, long until) {
            this.pkg = pkg == null ? "" : pkg;
            this.chosenAt = chosenAt;
            this.until = Math.min(until, chosenAt + 5 * 60_000L);
        }

        boolean live(long now) {
            return !pkg.isEmpty() && now >= chosenAt && now - chosenAt < VALID_MS;
        }

        /** El tiempo elegido ya se cumplió para esta app: Foco la cierra. */
        public boolean expired(String foreground, long now) {
            return live(now) && pkg.equals(foreground) && now >= until;
        }

        /** Milisegundos hasta que se cumpla (para reevaluar justo a tiempo); 0 si no aplica. */
        public long msLeft(String foreground, long now) {
            return live(now) && pkg.equals(foreground) && now < until ? until - now : 0;
        }
    }

    public static boolean validChoice(int minutes) {
        for (int c : CHOICES_MIN) if (c == minutes) return true;
        return false;
    }
}
