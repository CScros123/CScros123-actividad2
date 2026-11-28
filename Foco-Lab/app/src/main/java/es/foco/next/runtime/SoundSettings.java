package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Fase 6: ajustes de sonido de Foco. Viven en las preferencias "foco-soundscape" (las mismas de la
 * música 0.58). CONTRATO con Lab: personal.qqq.braintraining.HostSound lee {@link #SILENT} y {@link
 * #EFFECTS} de este mismo archivo (misma app, mismo proceso). Si cambias nombres o rangos, cambia
 * ambos lados.
 */
public final class SoundSettings {
    public static final String PREFS = "foco-soundscape";
    public static final String SILENT = "silent",
            MUSIC = "music_volume",
            EFFECTS = "effects_volume",
            PAUSE_OTHERS = "pause_others",
            PAUSE_MUSIC = "pause_music",
            PLAYLIST_LINK = "playlist_link";

    /**
     * Volumen de la música de 0.58 (0,28 del volumen del sistema); el ajuste del usuario lo escala
     * de 0 a 100 %.
     */
    public static final float MUSIC_BASE = .28f;

    private SoundSettings() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, 0);
    }

    public static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    public static boolean silent(Context c) {
        return prefs(c).getBoolean(SILENT, false);
    }

    public static int musicVolume(Context c) {
        return clamp(prefs(c).getInt(MUSIC, 100));
    }

    public static int effectsVolume(Context c) {
        return clamp(prefs(c).getInt(EFFECTS, 100));
    }

    public static boolean pauseOthers(Context c) {
        return prefs(c).getBoolean(PAUSE_OTHERS, true);
    }

    /** Ganancia del reproductor de música: 0 con "sin sonido". */
    public static float musicGain(Context c) {
        return gain(silent(c), musicVolume(c));
    }

    static float gain(boolean silent, int percent) {
        return silent ? 0f : MUSIC_BASE * clamp(percent) / 100f;
    }

    public static void setSilent(Context c, boolean on) {
        prefs(c).edit().putBoolean(SILENT, on).apply();
        CalmAudio.soundSettingsChanged(c);
    }

    public static void setMusicVolume(Context c, int percent) {
        prefs(c).edit().putInt(MUSIC, clamp(percent)).apply();
        CalmAudio.soundSettingsChanged(c);
    }

    public static void setEffectsVolume(Context c, int percent) {
        prefs(c).edit().putInt(EFFECTS, clamp(percent)).apply();
    }

    public static PauseMusic.Choice pauseMusic(Context c) {
        return PauseMusic.parse(prefs(c).getString(PAUSE_MUSIC, null));
    }

    public static void setPauseMusic(Context c, PauseMusic.Choice v) {
        prefs(c).edit().putString(PAUSE_MUSIC, v.name()).apply();
    }

    public static String playlistLink(Context c) {
        return prefs(c).getString(PLAYLIST_LINK, "");
    }

    public static void setPlaylistLink(Context c, String v) {
        prefs(c).edit().putString(PLAYLIST_LINK, v == null ? "" : v.trim()).apply();
    }

    /** v48: poner tu música de Apple Music al entrar en Enfoque. */
    public static boolean appleOnFocus(Context c) {
        return prefs(c).getBoolean("apple_enfoque", false);
    }

    public static void setAppleOnFocus(Context c, boolean on) {
        prefs(c).edit().putBoolean("apple_enfoque", on).apply();
    }

    public static void setPauseOthers(Context c, boolean on) {
        prefs(c).edit().putBoolean(PAUSE_OTHERS, on).apply();
    }
}
