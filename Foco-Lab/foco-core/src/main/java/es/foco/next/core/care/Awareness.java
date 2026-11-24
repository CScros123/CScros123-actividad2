package es.foco.next.core.care;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * v33: «¿Lo que veo ahora me aporta?». Reglas puras (sin Android) de la pregunta de conciencia.
 *
 * <ul>
 *   <li>Tras {@link #FIRST_CHECK_MS} seguidos en una app de ocio, Foco abre una ventana aparte (el
 *       video queda en pausa) y pregunta.
 *   <li><b>No</b>: la app se cierra y suena música tranquila {@link #MUSIC_MS}. Si vuelves a esa
 *       app antes de {@link #REOPEN_MS}, la pregunta sale al abrirla.
 *   <li><b>Sí</b>: escribes qué te aporta, con al menos {@link #MIN_WORDS} palabras distintas, y
 *       sigues {@link #EXTEND_MS}. Luego vuelve la pregunta, con otra nota.
 *   <li>Al salir de una sesión de {@link #EXIT_ASK_MS} o más en la que no hubo pregunta: «¿Valió la
 *       pena?» (sí / más o menos / no).
 * </ul>
 *
 * Una sesión es el tiempo seguido en la misma app; se corta si estás fuera más de {@link
 * #SESSION_GAP_MS} (salir y volver enseguida no la reinicia).
 */
public final class Awareness {
    private Awareness() {}

    public static final long MIN = 60_000L;
    public static final long FIRST_CHECK_MS = 15 * MIN;
    public static final long EXTEND_MS = 5 * MIN;
    public static final long SESSION_GAP_MS = 10 * MIN;
    public static final long REOPEN_MS = 20 * MIN;
    public static final long EXIT_ASK_MS = 10 * MIN;
    public static final long MUSIC_MS = 3 * MIN;
    public static final int MIN_WORDS = 15;
    /** «¿Valió la pena?» como mucho estas veces al día. */
    public static final int WORTH_PER_DAY = 6;

    /** Qué te aporta (atajos de la nota). */
    public enum Kind {
        APRENDO("Aprendo"),
        RELAJA("Me relaja"),
        CONECTA("Hablo con alguien");

        public final String label;

        Kind(String label) {
            this.label = label;
        }

        public static Kind parse(String s) {
            for (Kind k : values()) if (k.name().equalsIgnoreCase(s)) return k;
            return APRENDO;
        }
    }

    /** Respuestas de «¿Valió la pena?». */
    public enum Worth {
        SI("sí"),
        MAS_O_MENOS("más o menos"),
        NO("no");

        public final String label;

        Worth(String label) {
            this.label = label;
        }

        public static Worth parse(String s) {
            for (Worth w : values()) if (w.name().equalsIgnoreCase(s)) return w;
            return null;
        }
    }

    /**
     * Palabras que cuentan para la nota: distintas (sin mayúsculas ni tildes de por medio) y de al
     * menos 2 letras. Así «asdf asdf asdf» cuenta 1, no 3.
     */
    public static int words(String text) {
        if (text == null) return 0;
        Set<String> seen = new HashSet<>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() < 2) continue;
            seen.add(w);
        }
        return seen.size();
    }

    public static boolean noteOk(String text) {
        return words(text) >= MIN_WORDS;
    }

    /**
     * Cuánto falta para preguntar (0 = ya).
     *
     * @param sessionMs tiempo seguido en la app
     * @param extendedUntil fin de los 5 min tras un «sí» en esta sesión (0 si no hubo)
     * @param reopenedAfterNo se volvió a la app poco después de decir «no»: se pregunta al abrirla
     */
    public static long dueIn(long sessionMs, long extendedUntil, long now, boolean reopenedAfterNo) {
        if (extendedUntil > 0) return Math.max(0, extendedUntil - now);
        if (reopenedAfterNo) return 0;
        return Math.max(0, FIRST_CHECK_MS - Math.max(0, sessionMs));
    }

    /** ¿Se pregunta «¿Valió la pena?» al salir? */
    public static boolean askWorth(long sessionMs, boolean askedInSession, int askedToday) {
        return !askedInSession && sessionMs >= EXIT_ASK_MS && askedToday < WORTH_PER_DAY;
    }

    /** «32 min» o «1 h 10». */
    public static String duration(long ms) {
        long m = Math.max(0, ms) / MIN;
        if (m < 60) return m + " min";
        long h = m / 60, r = m % 60;
        return r == 0 ? h + " h" : h + " h " + (r < 10 ? "0" : "") + r;
    }
}
