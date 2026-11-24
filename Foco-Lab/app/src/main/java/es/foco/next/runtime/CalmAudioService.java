package es.foco.next.runtime;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.IBinder;
import es.foco.next.R;
import es.foco.next.ui.MainActivity;

/** Foreground only while bundled music plays on a visible Foco surface. Never sticky. */
public final class CalmAudioService extends Service {
    private static final String CHANNEL = "foco-calm-audio", MUTE = "es.foco.next.MUTE_MUSIC";
    private static final int ID = 5301;
    private static CalmAudioService instance;
    private MediaPlayer player;
    // Lista de reproducción: turno 0 (día) y 1 (noche) alternan; cada turno rota sus pistas (ver
    // LofiPlaylist).
    // Para añadir pistas propias: res/raw/nombre_en_minusculas.ogg y una entrada aquí (RAW, TITLES
    // y un turno).
    private static final int[] RAW = {
        R.raw.morning_routine,
        R.raw.colorful_flowers,
        R.raw.ambiente_lluvia_suave,
        R.raw.ambiente_noche,
        R.raw.ambiente_piano,
        R.raw.sendero
    };
    private static final String[] TITLES = {
        "Morning Routine · Ghostrifter Official",
        "Colorful Flowers · Tokyo Music Walker",
        "Lluvia suave · Foco (original)",
        "Noche · Foco (original)",
        "Piano ambiental · Foco (original)",
        "Sendero · Foco (original)"
    };
    private static final LofiPlaylist PLAYLIST =
            new LofiPlaylist(new int[][] {{0, 2, 4}, {1, 3, 5}});
    private AudioFocusRequest focus;
    private AudioManager audio;
    private boolean exclusive;
    private final AudioManager.OnAudioFocusChangeListener focusChanges =
            change -> {
                if (change < 0) {
                    CalmAudio.interrupted();
                    release();
                    stopSelf();
                }
            };

    static void refreshFocus() {
        if (instance != null
                && instance.player != null
                && instance.exclusive != CalmAudio.intervening())
            if (!instance.requestFocus()) {
                CalmAudio.interrupted();
                instance.release();
                instance.stopSelf();
            }
    }

    private boolean requestFocus() {
        if (audio == null || audio.getMode() != AudioManager.MODE_NORMAL) return false;
        if (focus != null) audio.abandonAudioFocusRequest(focus);
        exclusive = CalmAudio.intervening();
        focus =
                new AudioFocusRequest.Builder(
                                exclusive
                                        ? AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                                        : AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusChanges)
                        .build();
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private final AudioAttributes attrs =
            new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        audio = getSystemService(AudioManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && MUTE.equals(intent.getAction())) {
            CalmAudio.mute(this);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!CalmAudio.wanted()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL, "Música tranquila", NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
            startForeground(ID, notification());
            if (player == null) {
                if (!requestFocus()) {
                    CalmAudio.interrupted();
                    release();
                    stopSelf();
                    return START_NOT_STICKY;
                }
                // Cada bloqueo empieza en la pista siguiente a la del bloqueo anterior, aunque
                // Android haya cerrado Foco.
                int slot = CalmAudio.track();
                restorePositions();
                PLAYLIST.advance(slot);
                savePositions();
                play(slot);
            }
        } catch (RuntimeException e) {
            CalmAudio.interrupted();
            release();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void restorePositions() {
        android.content.SharedPreferences p = getSharedPreferences("foco-soundscape", 0);
        for (int i = 0; i < 2; i++) PLAYLIST.restore(i, p.getInt("lofi_position_" + i, -1));
    }

    private void savePositions() {
        getSharedPreferences("foco-soundscape", 0)
                .edit()
                .putInt("lofi_position_0", PLAYLIST.position(0))
                .putInt("lofi_position_1", PLAYLIST.position(1))
                .apply();
    }

    private void play(int index) {
        if (player != null) player.release();
        int track = PLAYLIST.current(index);
        CalmAudio.nowPlaying(TITLES[track]);
        player = MediaPlayer.create(this, RAW[track], attrs, 0);
        if (player == null) {
            CalmAudio.interrupted();
            stopSelf();
            return;
        }
        float gain = SoundSettings.musicGain(this);
        player.setVolume(gain, gain);
        player.setLooping(false); // Fase 6: volumen de música
        player.setOnCompletionListener(
                p -> {
                    if (CalmAudio.wanted()) {
                        int nextSlot = PLAYLIST.next(index);
                        savePositions();
                        play(nextSlot);
                    } else {
                        release();
                        stopSelf();
                    }
                });
        player.setOnErrorListener(
                (p, what, extra) -> {
                    CalmAudio.interrupted();
                    release();
                    stopSelf();
                    return true;
                });
        player.start();
        CalmAudio.ready(index);
        getSystemService(NotificationManager.class).notify(ID, notification());
    }

    private Notification notification() {
        PendingIntent open =
                PendingIntent.getActivity(
                        this,
                        ID,
                        new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent mute =
                PendingIntent.getService(
                        this,
                        ID,
                        new Intent(this, CalmAudioService.class).setAction(MUTE),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.leaf)
                .setContentTitle("Foco · un momento tranquilo")
                .setContentText(CalmAudio.title())
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Silenciar", mute).build())
                .build();
    }

    static void applyVolume() {
        if (instance != null && instance.player != null) {
            float gain = SoundSettings.musicGain(instance);
            try {
                instance.player.setVolume(gain, gain);
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Fase 6: si se cierra Foco desde Recientes, el lo-fi no sigue sonando. */
    @Override
    public void onTaskRemoved(Intent root) {
        CalmAudio.interrupted();
        release();
        stopSelf();
        super.onTaskRemoved(root);
    }

    static void stopPlayback() {
        if (instance != null) instance.release();
    }

    private void release() {
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
            player = null;
        }
        if (audio != null && focus != null) {
            audio.abandonAudioFocusRequest(focus);
            focus = null;
        }
    }

    @Override
    public void onDestroy() {
        release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (instance == this) instance = null;
        CalmAudio.destroyed();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }
}
