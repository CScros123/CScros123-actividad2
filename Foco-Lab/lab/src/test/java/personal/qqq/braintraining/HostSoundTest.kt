package personal.qqq.braintraining

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.SoundPool
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSoundPool
import org.robolectric.util.ReflectionHelpers

/**
 * Fase 6: "sin sonido" y volumen de efectos de Foco se aplican a Lab; tonos pentatónicos por racha.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HostSoundTest {
    private class FeedbackView(context: Context) : View(context) {
        override fun isShown() = true

        override fun hasWindowFocus() = true
    }

    private lateinit var context: Context
    private lateinit var feedback: ExperienceFeedback
    private lateinit var sounds: ShadowSoundPool

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(HostSound.PREFS, 0).edit().clear().commit()
        feedback = ExperienceFeedback(context, FeedbackView(context))
        sounds = shadowOf(ReflectionHelpers.getField<SoundPool>(feedback, "pool"))
        listOf(R.raw.lab_touch, R.raw.lab_correct, R.raw.lab_retry, R.raw.lab_complete).forEach {
            sounds.notifyResourceLoaded(it, true)
        }
        context.getSystemService(AudioManager::class.java).ringerMode =
            AudioManager.RINGER_MODE_NORMAL
        context
            .getSystemService(NotificationManager::class.java)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @After
    fun close() {
        feedback.release()
        context.getSharedPreferences(HostSound.PREFS, 0).edit().clear().commit()
    }

    @Test
    fun focoSilentSwitchMutesLabEffects() {
        context
            .getSharedPreferences(HostSound.PREFS, 0)
            .edit()
            .putBoolean(HostSound.SILENT, true)
            .commit()
        FeedbackCue.values().forEach { feedback.play(it, true, false) }
        for (r in
            listOf(
                R.raw.lab_touch,
                R.raw.lab_correct,
                R.raw.lab_retry,
                R.raw.lab_complete,
            )) assertFalse(sounds.wasResourcePlayed(r))
    }

    @Test
    fun focoEffectsVolumeScalesLabEffects() {
        context
            .getSharedPreferences(HostSound.PREFS, 0)
            .edit()
            .putInt(HostSound.EFFECTS, 50)
            .commit()
        feedback.play(FeedbackCue.COMPLETE, true, false)
        assertEquals(
            GameTones.baseVolume(FeedbackCue.COMPLETE) / 2,
            sounds.getResourcePlaybacks(R.raw.lab_complete).single().leftVolume,
            .001f,
        )
    }

    @Test
    fun zeroEffectsVolumePlaysNothing() {
        context
            .getSharedPreferences(HostSound.PREFS, 0)
            .edit()
            .putInt(HostSound.EFFECTS, 0)
            .commit()
        feedback.play(FeedbackCue.CORRECT, true, false)
        assertFalse(sounds.wasResourcePlayed(R.raw.lab_correct))
    }

    @Test
    fun defaultsKeepArcadeBehaviour() {
        assertFalse(HostSound.silent(context))
        assertEquals(1f, HostSound.effects(context), 0f)
    }

    @Test
    fun pentatonicRisesWithStreakAndStaysInSoundPoolRange() {
        var last = 0f
        for (streak in 1..6) {
            val rate = GameTones.correctRate(streak)
            assertTrue(rate > last)
            assertTrue(rate in .5f..2f)
            last = rate
        }
        assertEquals(GameTones.correctRate(6), GameTones.correctRate(40), 0f)
        assertEquals(GameTones.correctRate(1), GameTones.correctRate(0), 0f)
        assertTrue(GameTones.RETRY_RATE < 1f)
        FeedbackCue.values().forEach { assertTrue(GameTones.baseVolume(it) in .01f..0.5f) }
    }
}
