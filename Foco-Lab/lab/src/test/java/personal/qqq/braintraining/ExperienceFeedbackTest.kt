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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ExperienceFeedbackTest {
    private class FeedbackView(context: Context) : View(context) {
        var visible = true
        var focused = true
        var pulses = 0

        override fun isShown() = visible

        override fun hasWindowFocus() = focused

        override fun performHapticFeedback(constant: Int): Boolean {
            pulses++
            return true
        }
    }

    private lateinit var view: FeedbackView
    private lateinit var feedback: ExperienceFeedback
    private lateinit var sounds: ShadowSoundPool
    private lateinit var audio: AudioManager
    private lateinit var notifications: NotificationManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        view = FeedbackView(context)
        feedback = ExperienceFeedback(context, view)
        // Without a hardware vibrator the cue falls back to the view's haptic feedback, which is
        // what this test counts.
        ReflectionHelpers.setField(feedback, "vibrator", null)
        sounds = shadowOf(ReflectionHelpers.getField<SoundPool>(feedback, "pool"))
        listOf(R.raw.lab_touch, R.raw.lab_correct, R.raw.lab_retry, R.raw.lab_complete).forEach {
            sounds.notifyResourceLoaded(it, true)
        }
        audio = context.getSystemService(AudioManager::class.java)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        notifications = context.getSystemService(NotificationManager::class.java)
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    @After
    fun close() {
        feedback.release()
    }

    @Test
    fun eachCueUsesItsOwnShortNonLoopingSound() {
        listOf(FeedbackCue.TAP, FeedbackCue.CORRECT, FeedbackCue.RETRY, FeedbackCue.COMPLETE)
            .forEach { feedback.play(it, true, true) }
        for (resource in
            listOf(R.raw.lab_touch, R.raw.lab_correct, R.raw.lab_retry, R.raw.lab_complete)) {
            val plays = sounds.getResourcePlaybacks(resource)
            assertEquals(1, plays.size)
            assertEquals(0, plays.single().loop)
            assertTrue(plays.single().leftVolume in 0.01f..0.5f)
        }
        assertEquals(4, view.pulses)
    }

    @Test
    fun levelUpAndUnlockReuseSamplesAtAHigherPitch() {
        feedback.play(FeedbackCue.LEVEL_UP, true, true)
        feedback.play(FeedbackCue.UNLOCK, true, true)
        val up = sounds.getResourcePlaybacks(R.raw.lab_complete).single()
        val unlock = sounds.getResourcePlaybacks(R.raw.lab_correct).single()
        assertEquals(0, up.loop)
        assertEquals(0, unlock.loop)
        assertTrue(up.rate > 1f)
        assertTrue(unlock.rate > 1f)
        assertTrue(up.leftVolume in 0.01f..0.5f)
        assertTrue(unlock.leftVolume in 0.01f..0.5f)
        assertEquals(2, view.pulses)
    }

    @Test
    fun soundAndHapticPreferencesAreIndependent() {
        feedback.play(FeedbackCue.CORRECT, false, true)
        assertFalse(sounds.wasResourcePlayed(R.raw.lab_correct))
        assertEquals(1, view.pulses)
        feedback.play(FeedbackCue.CORRECT, true, false)
        assertTrue(sounds.wasResourcePlayed(R.raw.lab_correct))
        assertEquals(1, view.pulses)
    }

    @Test
    fun silenceVibrateAndDoNotDisturbSuppressSounds() {
        for (mode in listOf(AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE)) {
            audio.ringerMode = mode
            feedback.play(FeedbackCue.COMPLETE, true, false)
            assertFalse(sounds.wasResourcePlayed(R.raw.lab_complete))
        }
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        notifications.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        feedback.play(FeedbackCue.COMPLETE, true, true)
        assertFalse(sounds.wasResourcePlayed(R.raw.lab_complete))
        assertEquals(0, view.pulses)
    }

    @Test
    fun hiddenOrUnfocusedWindowNeverProducesEffects() {
        view.visible = false
        feedback.play(FeedbackCue.TAP, true, true)
        view.visible = true
        view.focused = false
        feedback.play(FeedbackCue.TAP, true, true)
        assertFalse(sounds.wasResourcePlayed(R.raw.lab_touch))
        assertEquals(0, view.pulses)
    }
}
