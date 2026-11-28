package personal.qqq.braintraining

import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Shared juice (docs/DISENO-JUEGOS-V2.md §1): the only place where the Fase 2 games take durations,
 * curves, shakes and particle presets from. Every helper takes `motion` ([LocalMotionEnabled]): off
 * ⇒ snap(), no shake (a fixed red border for 400 ms instead), no particles.
 */
internal object Juice {
    // 1.1 Durations (ms)
    const val Tick = 90
    const val Quick = 140
    const val Move = 240
    const val Reveal = 420
    const val Settle = 650
    const val Hold = 900
    const val Celebrate = 1400
    // 1.2 Curves
    val EaseOut: Easing = FastOutSlowInEasing
    val EaseIn: Easing = FastOutLinearInEasing
    val Linear: Easing = LinearEasing
    val Overshoot: Easing = CubicBezierEasing(.34f, 1.56f, .64f, 1f)

    fun <T> snappy(motion: Boolean): AnimationSpec<T> =
        if (motion) spring(dampingRatio = .82f, stiffness = 260f) else snap()

    fun <T> bouncy(motion: Boolean): AnimationSpec<T> =
        if (motion) spring(dampingRatio = .5f, stiffness = 500f) else snap()

    fun <T> tween(
        ms: Int,
        motion: Boolean,
        easing: Easing = EaseOut,
        delay: Int = 0,
    ): AnimationSpec<T> =
        if (motion) androidx.compose.animation.core.tween(ms, delay, easing) else snap()

    /**
     * Step response (0 → 1) of a spring after [t] seconds, for things drawn in the draw phase (no
     * Animatable per item). Defaults are [snappy]'s (damping .82, stiffness 260); allocation-free,
     * so it can run inside `draw`.
     */
    fun springAt(t: Float, damping: Float = .82f, stiffness: Float = 260f): Float {
        if (t <= 0f) return 0f
        val w = kotlin.math.sqrt(stiffness)
        val wd = w * kotlin.math.sqrt(1f - damping * damping)
        return 1f -
            exp(-damping * w * t) * (kotlin.math.cos(wd * t) + damping * w / wd * sin(wd * t))
    }

    // 1.6 Palette
    val Good = Color(0xFF1EDB90)
    val Bad = Color(0xFFFF5470)
    val Info = Color(0xFF7CF5FF)
    val NetAmber = Color(0xFFFFB300)
    val NetInk = Color(0xFF2B1D00)
    val NetBg = Color(0xFF161B26)

    // 1.4 Particle presets (count, speed px/s, gravity px/s²)
    enum class Burst(val count: Int, val speed: Float, val gravity: Float) {
        SPARK(10, 260f, 0f),
        HIT(18, 420f, 0f),
        SHATTER(14, 360f, 1200f),
        CONFETTI(14, 520f, 900f),
    }

    fun burst(p: Particles, preset: Burst, x: Float, y: Float, color: Color, motion: Boolean) =
        p.burst(x, y, color, preset.count, motion, preset.speed, preset.gravity)

    fun spark(p: Particles, x: Float, y: Float, accent: Color, motion: Boolean) =
        burst(p, Burst.SPARK, x, y, accent, motion)

    fun hit(p: Particles, x: Float, y: Float, motion: Boolean) =
        burst(p, Burst.HIT, x, y, Good, motion)

    fun shatter(p: Particles, x: Float, y: Float, motion: Boolean) =
        burst(p, Burst.SHATTER, x, y, Bad, motion)

    /** 5 × 14 sparks in the celebration colours across [width], at height [y]. */
    fun confetti(p: Particles, width: Float, y: Float, motion: Boolean) =
        confetti(p, 0f, width, y, motion)

    /**
     * X of confetti burst [i] over a strip that starts at [left] and is [width] wide: the bursts
     * spread from 20 % to 80 % of it.
     */
    fun confettiX(i: Int, left: Float, width: Float) = left + width * (.2f + .15f * i)

    /**
     * Confetti over a strip that does not start at the origin of the particle canvas (a board
     * inside a padded page).
     */
    fun confetti(p: Particles, left: Float, width: Float, y: Float, motion: Boolean) =
        CelebrationColors.forEachIndexed { i, c ->
            burst(p, Burst.CONFETTI, confettiX(i, left, width), y, c, motion)
        }

    /** Trail: one slow spark (call it every ~16 ms while something moves), life 0,35 s. */
    fun trail(p: Particles, x: Float, y: Float, accent: Color, motion: Boolean) =
        p.emit(
            x,
            y,
            (p.random.nextFloat() - .5f) * 80f,
            (p.random.nextFloat() - .5f) * 80f,
            .35f,
            accent,
            2.5f + p.random.nextFloat() * 1.5f,
            motion,
        )
}

/** Animates with motion, snaps without: no frame is awaited at all when "Animaciones" is off. */
internal suspend fun Animatable<Float, AnimationVector1D>.go(
    target: Float,
    spec: AnimationSpec<Float>,
    motion: Boolean,
) {
    if (motion) animateTo(target, spec) else snapTo(target)
}

/** Counts the frames of the Fase 2 game loops (tests check that none runs with animations off). */
internal object FrameProbe {
    @Volatile var frames = 0L
}

/**
 * 1.3 Screen shake presets: amplitude (dp), oscillations and duration (ms), exponentially damped.
 */
internal enum class Shake(val dp: Float, val oscillations: Int, val ms: Int) {
    SMALL(6f, 3, 180),
    MEDIUM(12f, 4, 260),
    BIG(20f, 5, 360),
}

/**
 * Plays a [Shake] on `graphicsLayer.translationX` only (never layout); with animations off, a red
 * border for 400 ms.
 */
@Stable
internal class ShakeState {
    private val progress = Animatable(1f)
    private var preset = Shake.SMALL
    /** Red-border fallback (animations off). */
    var alert by mutableStateOf(false)
        private set

    suspend fun play(s: Shake, motion: Boolean) {
        if (motion) {
            preset = s
            progress.snapTo(0f)
            progress.animateTo(
                1f,
                androidx.compose.animation.core.tween(s.ms, easing = LinearEasing),
            )
        } else {
            alert = true
            try {
                delay(400)
            } finally {
                alert = false
            }
        }
    }

    /** Current offset in px; read inside graphicsLayer so it never recomposes. */
    fun offset(density: Float): Float {
        val p = progress.value
        if (p >= 1f) return 0f
        return preset.dp * density * exp(-4f * p) * sin(2f * PI.toFloat() * preset.oscillations * p)
    }
}

@Composable internal fun rememberShake() = remember { ShakeState() }

internal fun Modifier.shake(s: ShakeState, shape: Shape = RoundedCornerShape(24.dp)): Modifier =
    graphicsLayer { translationX = s.offset(density) }
        .then(if (s.alert) Modifier.border(2.dp, Juice.Bad, shape) else Modifier)
