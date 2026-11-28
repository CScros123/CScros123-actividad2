package es.foco.next.core.backup;

import java.util.List;

/**
 * v47: lo que dice el búho tras la copia de cada noche. Tono cercano, sin culpa y sin números. No
 * repite una frase usada en los últimos 3 días.
 */
public final class BackupLines {
    private BackupLines() {}

    public static final int NO_REPEAT_DAYS = 3;

    public static final String[] DONE = {
        "Ey bro, ya hice la copia de hoy. Cuando quieras la analizamos.",
        "Listo, tu día ya está guardado. Mañana lo vemos si quieres.",
        "Copia hecha. Tu día está a salvo en tu Drive.",
        "Ya guardé lo de hoy, bro. A descansar.",
        "Tu día quedó guardado. Lo bueno también cuenta.",
        "Hecho: hoja y gráfico al día. Cuando quieras, los miramos.",
        "Todo guardado en tu Drive. Buen trabajo hoy.",
        "Copia lista, bro. Si mañana quieres, vemos cómo fue la semana.",
        "Ya está: tu historial creció un día más.",
        "Guardé tu día. Nada se pierde aunque cambies de teléfono.",
    };

    public static final String FAILED = "No pude subir la copia de hoy, lo intento más tarde.";
    public static final String RECONNECT =
            "Vuelve a conectar Drive para seguir guardando tus reportes.";

    /**
     * Índice de la frase de hoy. {@code recent}: índices usados en los últimos días (el más nuevo
     * al final). {@code seed}: algo que cambie cada día (p. ej. el día del año).
     */
    public static int pick(List<Integer> recent, int seed) {
        int n = DONE.length;
        int start = Math.floorMod(seed * 7 + 3, n);
        int from = recent == null ? 0 : Math.max(0, recent.size() - NO_REPEAT_DAYS);
        for (int k = 0; k < n; k++) {
            int i = (start + k) % n;
            boolean used = false;
            if (recent != null)
                for (int j = from; j < recent.size(); j++)
                    if (recent.get(j) != null && recent.get(j) == i) used = true;
            if (!used) return i;
        }
        return start;
    }
}
