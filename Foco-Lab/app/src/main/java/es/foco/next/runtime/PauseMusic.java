package es.foco.next.runtime;

import java.util.Locale;

/**
 * "Música durante la pausa": decide qué hacer al bloquear según la elección del usuario. Lógica
 * pura, sin Android.
 */
public final class PauseMusic {
    public enum Choice {
        LOFI,
        SPOTIFY,
        APPLE,
        NONE
    }

    public enum Action {
        LOFI,
        NONE,
        OPEN_LINK,
        SEARCH
    }

    public static final String SPOTIFY_PKG = "com.spotify.music",
            APPLE_PKG = "com.apple.android.music";

    private PauseMusic() {}

    public static Choice parse(String value) {
        if (value == null) return Choice.LOFI;
        try {
            return Choice.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Choice.LOFI;
        }
    }

    /** Paquete de la app de streaming, o null para lo-fi / ninguna. */
    public static String packageFor(Choice c) {
        return c == Choice.SPOTIFY ? SPOTIFY_PKG : c == Choice.APPLE ? APPLE_PKG : null;
    }

    /** Con Spotify/Apple Music no se pausa a otras apps (sería pausar la música elegida). */
    public static boolean pausesOthers(Choice c) {
        return c == Choice.LOFI || c == Choice.NONE;
    }

    /** La app de streaming elegida nunca cuenta como ocio mientras esté elegida. */
    public static boolean allowed(Choice c, String pkg) {
        String p = packageFor(c);
        return p != null && p.equals(pkg);
    }

    /**
     * Enlace aceptable para esa app: https de open.spotify.com, spotify: o https de
     * music.apple.com.
     */
    public static boolean validLink(Choice c, String link) {
        if (link == null) return false;
        String l = link.trim().toLowerCase(Locale.ROOT);
        if (c == Choice.SPOTIFY)
            return l.startsWith("spotify:") || l.startsWith("https://open.spotify.com/");
        if (c == Choice.APPLE) return l.startsWith("https://music.apple.com/");
        return false;
    }

    /** Acción al empezar un bloqueo. Si la app no está instalada se cae al lo-fi de Foco. */
    public static Action plan(Choice c, String link, boolean installed) {
        if (c == Choice.NONE) return Action.NONE;
        if (c == Choice.LOFI || !installed) return Action.LOFI;
        return validLink(c, link) ? Action.OPEN_LINK : Action.SEARCH;
    }
}
