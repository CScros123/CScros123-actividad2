package es.foco.next.runtime;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

/**
 * Lanza Spotify / Apple Music al bloquear. Nunca lanza excepciones; devuelve la acción realmente
 * hecha.
 */
public final class PauseMusicLauncher {
    private static long firedFor = Long.MIN_VALUE;

    private PauseMusicLauncher() {}

    /**
     * Una sola vez por bloqueo: el fin del bloqueo (redondeado al minuto) identifica el episodio.
     */
    public static PauseMusic.Action start(Context c, long leftMs) {
        PauseMusic.Choice choice = SoundSettings.pauseMusic(c);
        String pkg = PauseMusic.packageFor(choice);
        PauseMusic.Action action =
                PauseMusic.plan(
                        choice, SoundSettings.playlistLink(c), pkg != null && installed(c, pkg));
        if (action == PauseMusic.Action.LOFI || action == PauseMusic.Action.NONE) return action;
        long episode = (System.currentTimeMillis() + leftMs) / 60_000;
        if (Math.abs(episode - firedFor) <= 1) return action; // ya se lanzó en este bloqueo
        try {
            Intent i;
            if (action == PauseMusic.Action.OPEN_LINK)
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(SoundSettings.playlistLink(c).trim()));
            else {
                i = new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH);
                i.putExtra(
                        MediaStore.EXTRA_MEDIA_FOCUS,
                        MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE);
                i.putExtra(SearchManager.QUERY, "lofi");
            }
            i.setPackage(pkg);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            firedFor = episode;
            return action;
        } catch (RuntimeException e) {
            return PauseMusic.Action.LOFI;
        }
    }

    private static boolean installed(Context c, String pkg) {
        try {
            return c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
