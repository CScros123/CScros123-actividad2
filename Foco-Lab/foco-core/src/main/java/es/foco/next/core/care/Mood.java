package es.foco.next.core.care;

/**
 * Pregunta de ánimo: "¿Cómo estás?" del 1 al 5 y una palabra opcional. Se guarda solo en el
 * teléfono. Con 1 o 2 se sugiere CUIDADO con un botón; nunca se cambia solo. Puro.
 */
public final class Mood {
    private Mood() {}

    public static final int MIN = 1, MAX = 5, LOW = 2, GOOD = 4;

    public static boolean valid(int score) {
        return score >= MIN && score <= MAX;
    }

    /** 1 o 2: proponer CUIDADO (solo proponer). */
    public static boolean suggestCare(int score) {
        return valid(score) && score <= LOW;
    }

    public static boolean good(int score) {
        return valid(score) && score >= GOOD;
    }

    /** Una sola palabra, sin saltos de línea ni separadores del log, de hasta 24 caracteres. */
    public static String word(String raw) {
        if (raw == null) return "";
        String w = raw.trim().replaceAll("[\\r\\n,;|]", " ").trim();
        int sp = w.indexOf(' ');
        if (sp > 0) w = w.substring(0, sp);
        return w.length() > 24 ? w.substring(0, 24) : w;
    }

    /** Respuesta corta y sin juicio a lo que contestó. */
    public static String reply(int score) {
        switch (score) {
            case 1:
                return "Gracias por contármelo. Hoy no hace falta exigirte nada.";
            case 2:
                return "Vale. Si quieres, hoy vamos más suave.";
            case 3:
                return "Un día normal. A tu ritmo.";
            case 4:
                return "Qué bien. Aprovecha esa energía en algo tuyo.";
            case 5:
                return "¡Genial! Que te dure.";
            default:
                return "";
        }
    }

    public static String face(int score) {
        switch (score) {
            case 1:
                return "😞";
            case 2:
                return "😕";
            case 3:
                return "😐";
            case 4:
                return "🙂";
            default:
                return "😄";
        }
    }
}
