package es.foco.next.core.backup;

import java.util.regex.Pattern;

/**
 * v47: ningún texto que sale del teléfono (JSON, hoja, errores guardados) lleva la clave de NextDNS
 * ni un token de Google. Se aplica a todo lo exportado justo antes de enviarlo.
 */
public final class Secrets {
    private Secrets() {}

    public static final String HIDDEN = "[oculto]";

    // Tokens de acceso de Google (ya29.…) y cabeceras que pudieran colarse en un error.
    private static final Pattern GOOGLE_TOKEN = Pattern.compile("ya29\\.[A-Za-z0-9._\\-]+");
    private static final Pattern HEADER =
            Pattern.compile("(?i)(x-api-key|authorization)\\s*[:=]\\s*[^\\s,;\"]+( [^\\s,;\"]+)?");

    /** Quita del texto cada secreto dado (y cualquier token o cabecera con aspecto de serlo). */
    public static String redact(String text, String... secrets) {
        if (text == null || text.isEmpty()) return text;
        String out = text;
        if (secrets != null)
            for (String s : secrets)
                if (s != null && s.trim().length() >= 8) out = out.replace(s.trim(), HIDDEN);
        out = GOOGLE_TOKEN.matcher(out).replaceAll(HIDDEN);
        out = HEADER.matcher(out).replaceAll("$1: " + HIDDEN);
        return out;
    }

    /** ¿Queda algún secreto en el texto? (para las pruebas y como última comprobación). */
    public static boolean leaks(String text, String... secrets) {
        if (text == null) return false;
        if (secrets != null)
            for (String s : secrets)
                if (s != null && s.trim().length() >= 8 && text.contains(s.trim())) return true;
        return GOOGLE_TOKEN.matcher(text).find();
    }
}
