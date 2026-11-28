package es.foco.next.core.care;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lo bueno que el usuario ya hace y Foco no veía: apps de idiomas, lectura, Biblia, estudio y notas
 * cuentan como tiempo invertido en los resúmenes aunque no estén en la lista de productivas. Solo
 * sirve para MEDIR (informes): no aprueba nada, no da minutos al banco y no cambia la Regla 3. Una
 * app que está en ocio sigue siendo ocio (quien llama lo comprueba antes). Puro.
 */
public final class GoodApps {
    private GoodApps() {}

    public static final Set<String> KNOWN =
            new HashSet<>(
                    Arrays.asList(
                            "com.duolingo",
                            "com.sirma.mobile.bible.android", // YouVersion
                            "com.anthropic.claude",
                            "com.google.android.apps.books",
                            "com.amazon.kindle",
                            "com.khanacademy.android",
                            "com.ichi2.anki",
                            "notion.id",
                            "com.google.android.keep",
                            "com.google.android.apps.docs.editors.docs",
                            "com.microsoft.office.onenote",
                            "com.samsung.android.app.notes",
                            "org.coursera.android",
                            "com.babbel.mobile.android.en",
                            "com.memrise.android.memrisecompanion",
                            "com.blinkslabs.blinkist.android",
                            "com.audible.application"));

    private static final String[] HINTS = {
        "bible", "biblia", "duolingo", "kindle", "anki", "dictionary", "diccionario"
    };

    /** Para las frases de "lo bueno primero". */
    public enum Kind {
        LECTURA,
        BIBLIA,
        IDIOMAS,
        ESTUDIO,
        NOTAS,
        IA
    }

    /** Tipo de app buena, o null. Las etiquetas visibles ayudan cuando el paquete no basta. */
    public static Kind kind(String pkg, String label) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        String l = label == null ? "" : label.toLowerCase(Locale.ROOT);
        if (p.contains("bible")
                || p.contains("biblia")
                || l.contains("biblia")
                || l.contains("bible")) return Kind.BIBLIA;
        if (p.contains("duolingo") || p.contains("babbel") || p.contains("memrise"))
            return Kind.IDIOMAS;
        if (p.equals("com.anthropic.claude")) return Kind.IA;
        if (p.contains("books")
                || p.contains("kindle")
                || p.contains("blinkist")
                || p.contains("audible")
                || l.contains("libro")
                || l.contains("lectura")
                || l.contains("kindle")) return Kind.LECTURA;
        if (p.contains("keep")
                || p.contains("notes")
                || p.contains("onenote")
                || p.contains("notion")
                || p.contains("docs")) return Kind.NOTAS;
        if (good(pkg)) return Kind.ESTUDIO;
        return null;
    }

    /**
     * "Hoy leíste, practicaste idiomas y superaste un reto." Sin números. Si no hay nada aún, una
     * frase neutra y amable (nunca de falta).
     */
    public static String phrase(java.util.Set<Kind> kinds, int retos) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        if (kinds != null) {
            if (kinds.contains(Kind.BIBLIA)) parts.add("leíste la Biblia");
            if (kinds.contains(Kind.LECTURA)) parts.add("leíste");
            if (kinds.contains(Kind.IDIOMAS)) parts.add("practicaste idiomas");
            if (kinds.contains(Kind.ESTUDIO)) parts.add("estudiaste");
            if (kinds.contains(Kind.NOTAS)) parts.add("escribiste tus notas");
            if (kinds.contains(Kind.IA)) parts.add("trabajaste con Claude");
        }
        if (retos > 0) parts.add(retos == 1 ? "superaste un reto" : "superaste varios retos");
        if (parts.isEmpty()) return "Hoy todavía está por escribir. Cualquier paso pequeño cuenta.";
        StringBuilder s = new StringBuilder("Hoy ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) s.append(i == parts.size() - 1 ? " y " : ", ");
            s.append(parts.get(i));
        }
        return s.append('.').toString();
    }

    public static boolean good(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        if (KNOWN.contains(pkg)) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        for (String h : HINTS) if (p.contains(h)) return true;
        return false;
    }
}
