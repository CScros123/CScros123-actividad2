package es.foco.next.core.home;

import es.foco.next.core.home.HomeState.Estado;
import java.util.ArrayList;
import java.util.List;

/**
 * Cerebro del búho de Foco (puro). Elige el modo a partir de datos REALES y escribe lo que dice.
 * Reglas: nada de culpa ni de minutos inventados (la Regla 4 solo paga retos, uso productivo y subir
 * de nivel); de noche solo calma o palabra del día; sin rojo (la paleta está en la vista).
 */
public final class OwlBrain {
    private OwlBrain() {}

    public enum Mode {
        CALMA("🌿 Modo calma"),
        OCIO("🍂 ¿Te acompaño?"),
        LIBRO("📚 Momento de leer"),
        JUEGO("🧠 Destreza mental"),
        BIBLIA("✨ Palabra del día"),
        LEVANTATE("🏃 Pausa activa"),
        NOCHE("🌙 Descanso"),
        DESPERTAR("☀️ Buenos días");
        public final String badge;

        Mode(String badge) {
            this.badge = badge;
        }
    }

    /** Datos reales que puede usar el búho. */
    public static final class Facts {
        public Estado estado = Estado.NEUTRO;
        public long investedMin, ocioWindowMin, productiveRunMin;
        public String reader = "", bible = "", game = "";
        public boolean readerProductive, night;
        public int gameLevel = 1;
    }

    public static final long STAND_UP_MIN = 45;

    /** Modo que toca ahora, sin que el usuario pida nada. */
    public static Mode auto(Facts f) {
        if (f.estado == Estado.NOCHE) return Mode.NOCHE;
        if (f.estado == Estado.DESPERTAR) return Mode.DESPERTAR;
        if (f.estado == Estado.DISTRAIDO) return Mode.OCIO;
        if (f.productiveRunMin >= STAND_UP_MIN) return Mode.LEVANTATE;
        return Mode.CALMA;
    }

    /** Orden de "Otra idea ↻": empieza por el modo automático y sigue con sugerencias posibles. */
    public static List<Mode> cycle(Facts f, boolean faith) {
        List<Mode> out = new ArrayList<>();
        Mode a = auto(f);
        out.add(a);
        if (f.night || a == Mode.NOCHE) {
            if (faith) out.add(Mode.BIBLIA);
            return out;
        }
        if (!f.game.isEmpty() && a != Mode.JUEGO) out.add(Mode.JUEGO);
        if (!f.reader.isEmpty()) out.add(Mode.LIBRO);
        if (faith) out.add(Mode.BIBLIA);
        if (a != Mode.LEVANTATE && f.productiveRunMin >= 25) out.add(Mode.LEVANTATE);
        if (a != Mode.CALMA) out.add(Mode.CALMA);
        return out;
    }

    /** Palabra del día: Reina-Valera 1909 (dominio público), con acentos actuales. */
    public static final String[][] VERSES = {
        {"Mira que te mando que te esfuerces y seas valiente: no temas ni desmayes, porque Jehová tu Dios será contigo.", "Josué 1:9"},
        {"Sobre toda cosa guardada guarda tu corazón; porque de él mana la vida.", "Proverbios 4:23"},
        {"Estad quietos, y conoced que yo soy Dios.", "Salmo 46:10"},
        {"Los que esperan a Jehová tendrán nuevas fuerzas; levantarán las alas como águilas; correrán, y no se cansarán.", "Isaías 40:31"},
        {"Venid a mí todos los que estáis trabajados y cargados, que yo os haré descansar.", "Mateo 11:28"},
        {"Lámpara es a mis pies tu palabra, y lumbrera a mi camino.", "Salmo 119:105"},
        {"Todo lo puedo en Cristo que me fortalece.", "Filipenses 4:13"},
    };

    private static String pick(String[] options, int idx) {
        return options[Math.floorMod(idx, options.length)];
    }

    /** Lo que dice el búho. {@code bro}: tono cercano ("bro") o neutro. */
    public static String say(Mode m, Facts f, int idx, boolean bro) {
        String b = bro ? ", bro" : "";
        switch (m) {
            case OCIO:
                // Cuidado: preocupado y tierno, nunca regaño. Pregunta y propone; tú decides.
                return pick(new String[] {
                    "Oye" + b + ", llevas un buen rato en el móvil. ¿Cómo estás? Si quieres, te acompaño a otra cosa.",
                    "Descansar está bien" + b + ". Cuando te apetezca, cambiamos de chip juntos.",
                    "Estoy aquí" + b + ". ¿Un reto corto, leer un poco o caminar cinco minutos?",
                }, idx);
            case LIBRO:
                return pick(new String[] {
                    "Mira" + b + ", tienes " + f.reader + " a un toque. ¿Cinco minutos de lectura?",
                    "Cinco páginas hoy valen más que una hora de vídeos cortos.",
                }, idx);
            case JUEGO:
                // Entrenar la mente no da minutos (v26): el ocio solo lo abren las tareas reales.
                return pick(new String[] {
                    "¿Agilizamos la mente" + b + "? " + f.game + " (nivel " + f.gameLevel + ") dura un par de minutos.",
                    "Reto rápido listo: " + f.game + ". Dos minutos y tu cabeza despierta.",
                }, idx);
            case BIBLIA:
                String[] v = VERSES[Math.floorMod(idx, VERSES.length)];
                return v[0] + " (" + v[1] + ")" + (f.bible.isEmpty() ? "" : " Ábrelo en " + f.bible + ".");
            case LEVANTATE:
                return pick(new String[] {
                    "¡Hey" + b + "! Llevas " + f.productiveRunMin + " min seguidos. Levántate, agua y estira un minuto.",
                    "Tu espalda pide un respiro" + b + ". De pie 60 segundos y mira lejos por la ventana.",
                }, idx);
            case NOCHE:
                return pick(new String[] {
                    "Hoy invertiste " + f.investedMin + " min. Descansa" + b + "; el ocio vuelve a las " + es.foco.next.core.AbsoluteRules.nightEnd() + ":00.",
                    "Día cerrado. Mañana, un poco de lo mismo basta.",
                }, idx);
            case DESPERTAR:
                return "Buenos días" + b + ". Primero tu rutina de la mañana; el día empieza ahí.";
            default:
                return pick(new String[] {
                    f.investedMin > 0
                            ? "Todo en orden" + b + ". Llevas " + f.investedMin + " min invertidos hoy."
                            : "Todo en orden" + b + ". Cuando quieras, empieza por algo pequeño.",
                    "Buen ritmo. Tu mente agradece el silencio.",
                    "Tus tareas reales abren tu hora libre. Sin prisa, una a una.",
                }, idx);
        }
    }

    /** Texto del botón de acción (o "" si no hay acción). Nunca promete minutos que no existen. */
    public static String cta(Mode m, Facts f) {
        switch (m) {
            case OCIO:
                return "Vamos a otra cosa";
            case LIBRO:
                return "Abrir " + f.reader;
            case JUEGO:
                return "Jugar " + f.game;
            case BIBLIA:
                return f.bible.isEmpty() ? "" : "Abrir " + f.bible;
            case LEVANTATE:
                return "¡Ya me levanté!";
            case DESPERTAR:
                return "Abrir Foco Core";
            case NOCHE:
                return "";
            default:
                return "Ver sugerencia";
        }
    }
}
