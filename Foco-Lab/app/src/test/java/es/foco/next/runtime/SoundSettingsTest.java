package es.foco.next.runtime;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.KeyEvent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowMediaPlayer;
import org.robolectric.util.ReflectionHelpers;

/** Fase 6: volúmenes separados, "sin sonido", pausa multimedia y lo-fi que no sigue sonando. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class SoundSettingsTest {
    private Context c;
    private AudioManager audio;
    private final Object owner = new Object();

    @Before
    public void setup() {
        c = RuntimeEnvironment.getApplication();
        ReflectionHelpers.callStaticMethod(CalmAudio.class, "resetForTests");
        c.getSharedPreferences(SoundSettings.PREFS, 0).edit().clear().commit();
        audio = c.getSystemService(AudioManager.class);
        Shadows.shadowOf(audio)
                .setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        ShadowMediaPlayer.setMediaInfoProvider(
                source -> new ShadowMediaPlayer.MediaInfo(120000, 0));
    }

    @After
    public void close() {
        CalmAudio.leave(owner);
        ReflectionHelpers.callStaticMethod(CalmAudio.class, "resetForTests");
    }

    @Test
    public void defaultsMatchFoco058() {
        assertFalse(SoundSettings.silent(c));
        assertEquals(100, SoundSettings.musicVolume(c));
        assertEquals(100, SoundSettings.effectsVolume(c));
        assertTrue(SoundSettings.pauseOthers(c));
        assertEquals(.28f, SoundSettings.musicGain(c), .0001f);
    }

    @Test
    public void volumesAreSeparateAndClamped() {
        SoundSettings.setMusicVolume(c, 50);
        SoundSettings.setEffectsVolume(c, 250);
        assertEquals(50, SoundSettings.musicVolume(c));
        assertEquals(100, SoundSettings.effectsVolume(c));
        assertEquals(.14f, SoundSettings.musicGain(c), .0001f);
        SoundSettings.setEffectsVolume(c, -3);
        assertEquals(0, SoundSettings.effectsVolume(c));
        assertEquals(50, SoundSettings.musicVolume(c));
    }

    @Test
    public void silentMutesMusicAndPreventsAutoplay() {
        SoundSettings.setSilent(c, true);
        assertEquals(0f, SoundSettings.musicGain(c), 0f);
        assertFalse(CalmAudio.enabled(c));
        CalmAudio.enter(c, owner, () -> {}, true);
        assertFalse(CalmAudio.pending());
        assertFalse(CalmAudio.wanted());
    }

    @Test
    public void silentStopsMusicThatIsAlreadyPlaying() {
        CalmAudio.enter(c, owner, () -> {}, false);
        assertTrue(CalmAudio.pending());
        var service = Robolectric.buildService(CalmAudioService.class).create();
        try {
            service.get().onStartCommand(new Intent(), 0, 1);
            assertTrue(CalmAudio.active());
            SoundSettings.setMusicVolume(c, 25);
            MediaPlayer player = ReflectionHelpers.getField(service.get(), "player");
            assertNotNull(player);
            SoundSettings.setSilent(c, true);
            assertFalse(CalmAudio.active());
            assertFalse(CalmAudio.wanted());
        } finally {
            service.destroy();
        }
    }

    @Test
    public void explicitMusicButtonClearsSilent() {
        SoundSettings.setSilent(c, true);
        CalmAudio.enter(c, owner, () -> {}, false);
        CalmAudio.toggle(c);
        assertFalse(SoundSettings.silent(c));
        assertTrue(CalmAudio.wanted());
    }

    @Test
    public void removingTheTaskStopsLofi() {
        CalmAudio.enter(c, owner, () -> {}, false);
        var service = Robolectric.buildService(CalmAudioService.class).create();
        try {
            service.get().onStartCommand(new Intent(), 0, 1);
            assertTrue(CalmAudio.active());
            service.get().onTaskRemoved(new Intent());
            assertFalse(CalmAudio.active());
        } finally {
            service.destroy();
        }
    }

    @Test
    public void mediaPauseOnlyWhenSomethingPlaysAndNoCall() {
        assertTrue(MediaPause.shouldDispatch(true, true, AudioManager.MODE_NORMAL));
        assertFalse(MediaPause.shouldDispatch(true, false, AudioManager.MODE_NORMAL));
        assertFalse(MediaPause.shouldDispatch(true, true, AudioManager.MODE_IN_CALL));
        assertFalse(MediaPause.shouldDispatch(false, true, AudioManager.MODE_NORMAL));
        Shadows.shadowOf(audio).setIsMusicActive(false);
        assertFalse(MediaPause.pauseOthers(c));
    }

    @Test
    public void mediaPauseSendsPauseDownThenUp() {
        KeyEvent[] keys = MediaPause.keyEvents(1000);
        assertEquals(2, keys.length);
        assertEquals(KeyEvent.ACTION_DOWN, keys[0].getAction());
        assertEquals(KeyEvent.ACTION_UP, keys[1].getAction());
        for (KeyEvent k : keys) assertEquals(KeyEvent.KEYCODE_MEDIA_PAUSE, k.getKeyCode());
    }

    @Test
    public void mediaPauseNeverThrowsWhenMusicIsActive() {
        Shadows.shadowOf(audio).setIsMusicActive(true);
        MediaPause.pauseOthers(c); // el resultado depende del sistema; no debe lanzar
        SoundSettings.setPauseOthers(c, false);
        assertFalse(MediaPause.pauseOthers(c));
    }
}
