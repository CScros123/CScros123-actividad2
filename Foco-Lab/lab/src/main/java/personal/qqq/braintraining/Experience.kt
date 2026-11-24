package personal.qqq.braintraining

import android.animation.ValueAnimator
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.VibrationEffect
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle

/**
 * LEVEL_UP (rank, level or record) and UNLOCK (Lote B) reuse the COMPLETE and CORRECT samples at a
 * higher pitch. Fase 2 (docs/DISENO-JUEGOS-V2.md §1.5), no new files either: COMBO is CORRECT one
 * semitone higher per step (max +7, reset by RETRY, COMPLETE or WHOOSH), ALARM two alternating
 * beeps, WHOOSH a low, soft touch.
 */
/**
 * PULSE and PULSE_ACCENT (paso 2): the metronome click of Turno anti-DDoS, sound only, under every
 * other effect.
 */
enum class FeedbackCue {
    TAP,
    CORRECT,
    RETRY,
    COMPLETE,
    LEVEL_UP,
    UNLOCK,
    COMBO,
    ALARM,
    WHOOSH,
    PULSE,
    PULSE_ACCENT,
}

/** Short local sounds and haptics. No music loop, network or forced volume. */
internal class ExperienceFeedback(context: Context, private val view: View) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val vibrator =
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator?.takeIf {
            it.hasVibrator()
        }

    /**
     * Waveforms tuned for a precise actuator: a crisp tick, a soft double pulse for correct, a
     * firmer buzz for wrong.
     */
    private fun effect(cue: FeedbackCue): VibrationEffect? =
        when (cue) {
            FeedbackCue.TAP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            FeedbackCue.CORRECT ->
                if (vibrator?.hasAmplitudeControl() == true)
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 18, 60, 24),
                        intArrayOf(0, 140, 0, 200),
                        -1,
                    )
                else VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            FeedbackCue.RETRY ->
                if (vibrator?.hasAmplitudeControl() == true)
                    VibrationEffect.createWaveform(longArrayOf(0, 55), intArrayOf(0, 255), -1)
                else VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            FeedbackCue.COMPLETE ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 25, 70, 25, 70, 45),
                    intArrayOf(0, 120, 0, 170, 0, 230),
                    -1,
                )
            FeedbackCue.LEVEL_UP ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 20, 50, 20, 50, 20, 50, 60),
                    intArrayOf(0, 110, 0, 150, 0, 190, 0, 255),
                    -1,
                )
            FeedbackCue.UNLOCK ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 90, 50),
                    intArrayOf(0, 150, 0, 230),
                    -1,
                )
            FeedbackCue.COMBO -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            FeedbackCue.ALARM ->
                VibrationEffect.createWaveform(
                    longArrayOf(0, 30, 70, 30, 70, 30),
                    intArrayOf(0, 160, 0, 160, 0, 160),
                    -1,
                )
            FeedbackCue.WHOOSH,
            FeedbackCue.PULSE,
            FeedbackCue.PULSE_ACCENT -> null
        }

    private val pool =
        runCatching {
                SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
            }
            .getOrNull()
    private val loaded = mutableSetOf<Int>()
    // Fase 6: puente con los ajustes de sonido de Foco y racha de aciertos de la sesión (escala
    // pentatónica).
    private val host = context.applicationContext
    private var streak = 0
    private var combo = 0
    private val streams = mutableListOf<Int>()
    private val samples: Map<FeedbackCue, Int>

    init {
        pool?.setOnLoadCompleteListener { _, id, status -> if (status == 0) loaded.add(id) }
        val ids =
            listOf(R.raw.lab_touch, R.raw.lab_correct, R.raw.lab_retry, R.raw.lab_complete)
                .associateWith { resource ->
                    runCatching { pool?.load(context, resource, 1) ?: 0 }.getOrDefault(0)
                }
        samples = FeedbackCue.values().associateWith { ids.getValue(GameTones.sample(it)) }
    }

    fun play(cue: FeedbackCue, sound: Boolean, haptics: Boolean) {
        streak =
            when (cue) {
                FeedbackCue.CORRECT -> streak + 1
                // Un fallo ya no hunde el tono a la nota más baja: baja solo un paso.
                FeedbackCue.RETRY -> (streak - 1).coerceAtLeast(0)
                FeedbackCue.COMPLETE -> 0
                else -> streak
            } // Fase 6
        combo =
            when (cue) {
                FeedbackCue.COMBO -> combo + 1
                FeedbackCue.RETRY,
                FeedbackCue.COMPLETE,
                FeedbackCue.WHOOSH -> 0
                else -> combo
            }
        if (!view.isShown || !view.hasWindowFocus()) return
        val quiet =
            notifications?.currentInterruptionFilter?.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            } ?: false
        if (quiet) return
        val pulse = cue == FeedbackCue.PULSE || cue == FeedbackCue.PULSE_ACCENT
        if (haptics && cue != FeedbackCue.WHOOSH && !pulse) {
            val v = vibrator
            if (v != null) runCatching { effect(cue)?.let(v::vibrate) }
            else
                view.performHapticFeedback(
                    if (cue == FeedbackCue.RETRY) HapticFeedbackConstants.REJECT
                    else HapticFeedbackConstants.CONFIRM
                )
        } // Android still applies the user's system vibration intensity.
        val id = samples[cue] ?: return
        // Fase 6: "sin sonido" y volumen de efectos de Foco; acierto con tono según la racha, error
        // suave.
        val volume = GameTones.baseVolume(cue) * HostSound.effects(host)
        if (
            sound &&
                !HostSound.silent(host) &&
                volume > 0f &&
                audio?.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
                id in loaded
        ) {
            val rate =
                when (cue) {
                    FeedbackCue.CORRECT -> GameTones.correctRate(streak)
                    FeedbackCue.RETRY -> GameTones.RETRY_RATE
                    FeedbackCue.LEVEL_UP -> GameTones.LEVEL_UP_RATE
                    FeedbackCue.UNLOCK -> GameTones.UNLOCK_RATE
                    FeedbackCue.COMBO -> GameTones.comboRate(combo)
                    FeedbackCue.ALARM -> GameTones.ALARM_HIGH
                    FeedbackCue.WHOOSH -> GameTones.WHOOSH_RATE
                    FeedbackCue.PULSE -> GameTones.PULSE_RATE
                    else -> 1f
                }
            // The pulse has the lowest priority: when the three streams are busy it is the click
            // that gives way, never an effect.
            pool?.play(id, volume, volume, if (pulse) 0 else 1, 0, rate)?.let {
                streams.add(it)
                if (streams.size > 8) streams.removeAt(0)
            }
            // ALARM: the second, lower beep 150 ms later (300 ms in all).
            if (cue == FeedbackCue.ALARM)
                view.postDelayed({ pool?.play(id, volume, volume, 1, 0, GameTones.ALARM_LOW) }, 150)
        }
    }

    /**
     * Órbitas 2: the note of orbit position [position] (pentatonic, see [GameTones.note]); no
     * haptics, same silence rules as [play].
     */
    fun note(position: Int, sound: Boolean, soft: Boolean = false) {
        if (!sound || !view.isShown || !view.hasWindowFocus() || HostSound.silent(host)) return
        val quiet =
            notifications?.currentInterruptionFilter?.let {
                it != NotificationManager.INTERRUPTION_FILTER_ALL &&
                    it != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            } ?: false
        val id =
            samples[FeedbackCue.CORRECT] ?: return // the short chime: a clean pitch to transpose
        val volume = GameTones.NOTE_VOLUME * (if (soft) .7f else 1f) * HostSound.effects(host)
        if (
            quiet ||
                volume <= 0f ||
                audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
                id !in loaded
        )
            return
        pool?.play(id, volume, volume, 1, 0, GameTones.note(position))?.let {
            streams.add(it)
            if (streams.size > 8) streams.removeAt(0)
        }
    }

    fun stop() {
        streams.forEach { pool?.stop(it) }
        streams.clear()
    }

    fun release() {
        stop()
        pool?.release()
        loaded.clear()
    }
}

val LocalMotionEnabled = staticCompositionLocalOf { true }
val LocalFeedback = staticCompositionLocalOf<(FeedbackCue) -> Unit> { {} }
/** Órbitas 2: plays the note of an orbit position (index, soft). */
val LocalNote = staticCompositionLocalOf<(Int, Boolean) -> Unit> { { _, _ -> } }

@Composable
fun ExperienceHost(vm: BrainViewModel, content: @Composable () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val sound = prefs.firstOrNull { it.key == "sound" }?.value != "false"
    val haptics = prefs.firstOrNull { it.key == "haptics" }?.value != "false"
    var systemMotion by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    val motion =
        prefs.firstOrNull { it.key == "motion" }?.value != "false" &&
            systemMotion &&
            !HostMotion.reduced(LocalContext.current)
    val context = LocalContext.current
    val view = LocalView.current
    val owner = LocalLifecycleOwner.current
    val feedback = remember(context, view) { ExperienceFeedback(context, view) }
    val play by rememberUpdatedState<(FeedbackCue) -> Unit>({ feedback.play(it, sound, haptics) })
    val note by
        rememberUpdatedState<(Int, Boolean) -> Unit>({ position, soft ->
            feedback.note(position, sound, soft)
        })
    DisposableEffect(feedback, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) feedback.stop()
            if (event == Lifecycle.Event.ON_RESUME)
                systemMotion = ValueAnimator.areAnimatorsEnabled()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            feedback.release()
        }
    }
    LaunchedEffect(vm, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.feedback.collect { play(it) }
        }
    }
    CompositionLocalProvider(
        LocalMotionEnabled provides motion,
        LocalFeedback provides { play(it) },
        LocalNote provides { p, soft -> note(p, soft) },
        content = content,
    )
}

/** One source drives ripple, press scale and click. */
fun Modifier.pressClick(
    enabled: Boolean = true,
    feedbackOnClick: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current
    val feedback = LocalFeedback.current
    val scale by
        animateFloatAsState(
            if (pressed && motion) .965f else 1f,
            if (motion) spring(dampingRatio = .7f, stiffness = 700f) else snap(),
            label = "Pulsación",
        )
    this.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = androidx.compose.foundation.LocalIndication.current,
            enabled = enabled,
        ) {
            if (feedbackOnClick) feedback(FeedbackCue.TAP)
            onClick()
        }
}
