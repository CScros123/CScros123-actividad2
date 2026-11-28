package es.foco.next.runtime;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import java.util.List;

/**
 * v48: tu música de Apple Music, directa. Abre tu playlist exacta (enlace music.apple.com), la pone
 * a sonar y, con el acceso a notificaciones concedido ({@link MediaListener}), Foco ve qué suena y
 * puede pausar o pasar a la siguiente. Sin MusicKit: no hace falta cuenta de desarrollador ni
 * claves. Suena al entrar en Enfoque (si lo activas), en la pausa del bloqueo y con el botón del
 * inicio.
 */
public final class AppleMusic {
    private AppleMusic() {}

    public static final String PKG = PauseMusic.APPLE_PKG;

    /** Tras abrir la app, cuánto esperar para darle a reproducir (tarda en crear su sesión). */
    static final long START_DELAY_MS = 2_500, RETRY_DELAY_MS = 3_000;

    public static boolean installed(Context c) {
        try {
            return c.getPackageManager().getLaunchIntentForPackage(PKG) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Tu enlace de Apple Music (Ajustes › Sonido), o "" si no hay uno válido. */
    public static String link(Context c) {
        String l = SoundSettings.playlistLink(c).trim();
        return PauseMusic.validLink(PauseMusic.Choice.APPLE, l) ? l : "";
    }

    /** ¿Está concedido el acceso para ver y controlar la música? */
    public static boolean canControl(Context c) {
        try {
            android.app.NotificationManager nm =
                    c.getSystemService(android.app.NotificationManager.class);
            return nm != null
                    && nm.isNotificationListenerAccessGranted(
                            new ComponentName(c, MediaListener.class));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Abre la pantalla de Android para conceder el acceso (solo «Foco · música»). */
    public static Intent accessSettings() {
        return new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Abre tu playlist (o Apple Music si no hay enlace) y la pone a sonar. Devuelve el texto para la
     * persona.
     */
    public static String play(Context c) {
        Context app = c.getApplicationContext();
        if (!installed(app)) return "Apple Music no está instalada.";
        String link = link(app);
        try {
            Intent i =
                    link.isEmpty()
                            ? app.getPackageManager().getLaunchIntentForPackage(PKG)
                            : new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(PKG);
            if (i == null) return "Apple Music no está instalada.";
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            app.startActivity(i);
        } catch (RuntimeException e) {
            return "No pude abrir Apple Music.";
        }
        startLater(app, link);
        return link.isEmpty()
                ? "Abriendo Apple Music. Guarda el enlace de tu playlist en Ajustes › Sonido."
                : "Poniendo tu playlist.";
    }

    /** Tras abrir la app: reproducir (la playlist exacta si Apple Music lo admite). */
    static void startLater(Context app, String link) {
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> start(app, link), START_DELAY_MS);
        // Si aún no sonaba (la app tardó en arrancar), un segundo intento.
        h.postDelayed(
                () -> {
                    if (!playing(app)) start(app, link);
                },
                START_DELAY_MS + RETRY_DELAY_MS);
    }

    static void start(Context app, String link) {
        MediaController m = controller(app);
        if (m != null) {
            PlaybackState s = m.getPlaybackState();
            long actions = s == null ? 0 : s.getActions();
            try {
                if (!link.isEmpty() && (actions & PlaybackState.ACTION_PLAY_FROM_URI) != 0)
                    m.getTransportControls().playFromUri(Uri.parse(link), null);
                else m.getTransportControls().play();
                return;
            } catch (RuntimeException ignored) {
            }
        }
        // Sin acceso: la tecla «reproducir» va a la app de música activa (Apple Music, recién
        // abierta).
        key(app, KeyEvent.KEYCODE_MEDIA_PLAY);
    }

    /** La sesión de Apple Music, si hay acceso y está abierta. */
    static MediaController controller(Context c) {
        if (!canControl(c)) return null;
        try {
            MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
            if (msm == null) return null;
            List<MediaController> list =
                    msm.getActiveSessions(new ComponentName(c, MediaListener.class));
            for (MediaController m : list) if (PKG.equals(m.getPackageName())) return m;
        } catch (RuntimeException ignored) {
            // SecurityException si se retiró el acceso.
        }
        return null;
    }

    public static boolean playing(Context c) {
        MediaController m = controller(c);
        PlaybackState s = m == null ? null : m.getPlaybackState();
        return s != null && s.getState() == PlaybackState.STATE_PLAYING;
    }

    /** "Canción · Artista" de lo que suena en Apple Music, o "" si no se sabe. */
    public static String nowPlaying(Context c) {
        MediaController m = controller(c);
        MediaMetadata md = m == null ? null : m.getMetadata();
        if (md == null) return "";
        String title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
        if (title == null || title.isEmpty()) return "";
        return artist == null || artist.isEmpty() ? title : title + " · " + artist;
    }

    /** Pausa o sigue. Sin acceso, usa la tecla multimedia. */
    public static void toggle(Context c) {
        MediaController m = controller(c);
        if (m == null) {
            key(c, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            return;
        }
        if (playing(c)) m.getTransportControls().pause();
        else m.getTransportControls().play();
    }

    public static void next(Context c) {
        MediaController m = controller(c);
        if (m != null) m.getTransportControls().skipToNext();
        else key(c, KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    private static void key(Context c, int code) {
        try {
            AudioManager a = c.getSystemService(AudioManager.class);
            if (a == null) return;
            a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
            a.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
        } catch (RuntimeException ignored) {
        }
    }
}
