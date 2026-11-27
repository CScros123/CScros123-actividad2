package es.foco.next.core.home;

/**
 * Postura del búho, por prioridad: noche > música en vivo > app reciente (4 min) > azar de día.
 * Lógica pura (sin Android) para poder probarla.
 */
public final class OwlState {
    private OwlState() {}

    public enum Pose {
        SLEEPING,
        LISTENING_MUSIC,
        READING,
        ANGRY_BACK,
        CALM,
        STRETCH,
        CURIOUS,
        READING_IDLE;

        /** Posturas que se eligen al azar de día. */
        public boolean idle() {
            return this == CALM || this == STRETCH || this == CURIOUS || this == READING_IDLE;
        }
    }

    /** Categoría de la última app en primer plano antes de volver al inicio. */
    public enum Recent {
        NONE,
        LECTURA,
        OCIO
    }

    public static final long MEMORY_MS = 4 * 60_000L;
    public static final long NIGHT_NOTICE_COOLDOWN_MS = 45 * 60_000L;

    /** Lectura: El Libro Total, Biblia, Play Libros, Kindle y parecidas. */
    public static boolean reading(String pkg, String label) {
        String p = pkg == null ? "" : pkg.toLowerCase(java.util.Locale.ROOT);
        String l = label == null ? "" : label.toLowerCase(java.util.Locale.ROOT);
        return p.contains("libro")
                || p.contains("books")
                || p.contains("kindle")
                || p.contains("bible")
                || p.contains("biblia")
                || l.contains("libro")
                || l.contains("biblia")
                || l.contains("bible")
                || l.contains("kindle");
    }

    /**
     * @param recentAt cuándo se salió de esa app (ms de pared)
     * @param recentOver la app de ocio se usó pasando el límite
     * @param idle postura de azar vigente (si no es de azar, se usa CALM)
     */
    public static Pose resolve(
            boolean night,
            boolean music,
            Recent recent,
            long recentAt,
            boolean recentOver,
            long now,
            Pose idle) {
        if (night) return Pose.SLEEPING;
        if (music) return Pose.LISTENING_MUSIC;
        if (fresh(recentAt, now)) {
            if (recent == Recent.LECTURA) return Pose.READING;
            if (recent == Recent.OCIO && recentOver) return Pose.ANGRY_BACK;
        }
        return idle != null && idle.idle() ? idle : Pose.CALM;
    }

    public static boolean fresh(long at, long now) {
        return at > 0 && now >= at && now - at < MEMORY_MS;
    }

    /** Azar de día: la calma pesa el doble que las demás. {@code roll} en [0, 1). */
    public static Pose randomIdle(double roll) {
        if (roll < .4) return Pose.CALM;
        if (roll < .6) return Pose.STRETCH;
        if (roll < .8) return Pose.CURIOUS;
        return Pose.READING_IDLE;
    }

    // ---------------- Aviso de descanso nocturno ----------------

    public static final String[] NIGHT_TEXTS = {
        "No molestar por favor, estoy durmiendo. Tú deberías hacer lo mismo, hoy ya hiciste muchas cosas. Incluso dormir es una forma de ganar en la vida.",
        "Buenas noches, bro. Ya diste lo mejor hoy; soltar el teléfono ahora también cuenta como victoria.",
        "Foco cerró por hoy. Descansa la mente, mañana seguimos con fuerza.",
    };

    public static boolean nightNoticeDue(long lastAt, long now) {
        return lastAt <= 0 || now < lastAt || now - lastAt >= NIGHT_NOTICE_COOLDOWN_MS;
    }

    public static String nightText(int index) {
        return NIGHT_TEXTS[Math.floorMod(index, NIGHT_TEXTS.length)];
    }
}
