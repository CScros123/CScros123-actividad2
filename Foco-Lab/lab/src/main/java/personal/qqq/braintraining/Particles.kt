package personal.qqq.braintraining

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One short-lived spark: position and speed in px, [life] counts down to 0 seconds. Recycled by
 * [Particles].
 */
internal class Spark {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var color = Color.White
    var size = 1f
    /**
     * Extra gravity (px/s²) of this spark's preset, added to the one passed to [Particles.step].
     */
    var gravity = 0f

    init {
        created++
    }

    companion object {
        /** Instances ever built: the pool test checks it stops growing after warm-up. */
        @Volatile
        var created = 0L
            private set
    }
}

/**
 * Shared particle system (Lote B; pooled in Fase 2, docs/DISENO-JUEGOS-V2.md §1.4): a fixed pool of
 * [CAPACITY] sparks kept in an array with a live count. [burst], [emit] and [step] never allocate:
 * dead sparks are swapped to the end and reused; when the pool is full the oldest new request is
 * simply dropped. Nothing is emitted when animations are off.
 */
internal class Particles(val random: Random = Random(System.nanoTime())) {
    private val pool = Array(CAPACITY) { Spark() }
    /** Sparks alive now (the first [live] entries of the pool). */
    var live = 0
        private set

    val active
        get() = live > 0

    /** Radial explosion at ([x], [y]). */
    fun burst(
        x: Float,
        y: Float,
        color: Color,
        count: Int,
        motion: Boolean,
        speed: Float = 380f,
        gravity: Float = 0f,
    ) {
        if (!motion) return
        repeat(count) {
            val a = random.nextFloat() * 6.283f
            val v = 120f + random.nextFloat() * speed
            val life = .4f + random.nextFloat() * .5f
            emit(
                x,
                y,
                cos(a) * v,
                sin(a) * v,
                life,
                color,
                3f + random.nextFloat() * 5f,
                motion,
                gravity,
            )
        }
    }

    /** One spark; false when animations are off or the pool is full. */
    fun emit(
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        life: Float,
        color: Color,
        size: Float,
        motion: Boolean,
        gravity: Float = 0f,
    ): Boolean {
        if (!motion || live >= CAPACITY) return false
        val p = pool[live++]
        p.x = x
        p.y = y
        p.vx = vx
        p.vy = vy
        p.life = life
        p.maxLife = life
        p.color = color
        p.size = size
        p.gravity = gravity
        return true
    }

    fun clear() {
        live = 0
    }

    /**
     * Moves every spark by [dt] seconds with drag and [gravity] (px/s²) plus its own, and recycles
     * the dead ones.
     */
    fun step(dt: Float, gravity: Float = 0f) {
        var i = 0
        while (i < live) {
            val p = pool[i]
            p.life -= dt
            if (p.life <= 0f) {
                live--
                pool[i] = pool[live]
                pool[live] = p
                continue
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.vx *= .985f
            p.vy = p.vy * .985f + (gravity + p.gravity) * dt
            i++
        }
    }

    fun DrawScope.draw() {
        for (i in 0 until live) {
            val p = pool[i]
            val t = p.life / p.maxLife
            drawCircle(p.color.copy(alpha = t), p.size * (.4f + t * .6f), Offset(p.x, p.y))
        }
    }

    companion object {
        const val CAPACITY = 256
    }
}

internal val CelebrationColors =
    listOf(
        Color(0xFFFFB300),
        Color(0xFF1EDB90),
        Color(0xFF7CF5FF),
        Color(0xFFFF6B9A),
        Color(0xFFB69CFF),
    )

/**
 * Confetti burst over its parent each time [trigger] changes to a non-null value (rank up, unlock,
 * record). Draws nothing and runs no frame loop when animations are off.
 */
@Composable
internal fun CelebrationBurst(trigger: Any?, modifier: Modifier = Modifier.fillMaxSize()) {
    if (!LocalMotionEnabled.current || trigger == null) return
    val particles = remember { Particles() }
    var frame by remember { mutableLongStateOf(0L) }
    var size by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    LaunchedEffect(trigger, size) {
        if (size.width == 0f) return@LaunchedEffect
        Juice.confetti(particles, size.width, size.height * .35f, true)
        var last = 0L
        while (particles.active) withFrameNanos { now ->
            val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.05f)
            last = now
            particles.step(dt)
            frame = now
        }
        frame = 0L
    }
    Canvas(modifier) {
        size = this.size
        frame
        with(particles) { draw() }
    }
}
