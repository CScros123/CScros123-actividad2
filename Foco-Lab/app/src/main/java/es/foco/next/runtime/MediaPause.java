package es.foco.next.runtime;

import android.content.Context;
import android.media.AudioManager;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Fase 6: al mostrar la pausa, además del foco de audio, envía KEYCODE_MEDIA_PAUSE (DOWN + UP).
 * Solo si hay audio sonando y no hay llamada: así nunca despierta una app de música parada. PAUSE
 * (no PLAY_PAUSE) nunca inicia reproducción. Sin permisos nuevos. El resultado depende de cada app
 * (ver docs/FASE-5-6.md).
 */
public final class MediaPause {
    private MediaPause() {}

    static boolean shouldDispatch(boolean enabled, boolean musicActive, int mode) {
        return enabled && musicActive && mode == AudioManager.MODE_NORMAL;
    }

    static KeyEvent[] keyEvents(long now) {
        return new KeyEvent[] {
            new KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0),
            new KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
        };
    }

    /**
     * @return true si se enviaron las teclas. Nunca lanza: si falla, la pausa sigue igual.
     */
    public static boolean pauseOthers(Context c) {
        try {
            AudioManager audio = c.getSystemService(AudioManager.class);
            if (audio == null
                    || !shouldDispatch(
                            SoundSettings.pauseOthers(c), audio.isMusicActive(), audio.getMode()))
                return false;
            for (KeyEvent e : keyEvents(SystemClock.uptimeMillis())) audio.dispatchMediaKeyEvent(e);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
