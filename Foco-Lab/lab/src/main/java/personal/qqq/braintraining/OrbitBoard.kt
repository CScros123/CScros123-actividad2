package personal.qqq.braintraining

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.*
import kotlinx.coroutines.isActive
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

/* Órbitas 2 (docs/ORBITAS-2.md). One Canvas draws the orbit; the board plays the whole round (demonstration, turn, answer and
 * verdict) on its own clock, which only advances while frames arrive, and reports the verdict and its log like the other
 * board games. Nodes carry no numbers: the player remembers places. With animations off the comet, its trail and the smooth
 * turn are skipped (the lights and the turn still happen, they are the game). Rules: lab-core/Orbit.kt. */

private val Deck = Color(0xFF0E1329)
private val DeckGlow = Color(0xFF151B3D)
private val DeckEdge = Color(0xFF283066)
private val NodeFill = Color(0xFF161A36)
private val NodeRing = Color(0xFF4A4B88)
private val CoreFill = Color(0xFF0A0D1F)
private val OrbitInk = Color(0xFFEEF0FF)
private val OrbitMute = Color(0xFF8B91C0)
internal val OrbitCyan = Color(0xFF5CF0E6)
internal val OrbitAmber = Color(0xFFFFB547)
internal val OrbitEmerald = Color(0xFF2FCB7E)
internal val OrbitCoral = Color(0xFFFF6B6B)
private val OrbitMagenta = Color(0xFFFF4FD2)

private object OrbitPhase {
    const val INTRO = 0
    const val SHOW = 1
    const val TURN = 2
    const val ANSWER = 3
    const val VERDICT = 4
    const val REPORTED = 5
}

@Stable
private class OrbitWorld(val task: OrbitTask) {
    val n = task.nodes
    val input = OrbitInput(task)
    val travel = task.stepMs * 42 / 100
    val lightDur = task.stepMs * 52 / 100
    /**
     * Board clock in ms: advances by the frame delta (at most 50 ms), so it stops while the screen
     * is not drawn.
     */
    var clock = 0L
    var phase by mutableIntStateOf(OrbitPhase.INTRO)
    var phaseAt = 0L
    /** Positions turned so far (0 → [OrbitTask.turn] during the turn). */
    var rot = 0f
    val trailA = FloatArray(TRAIL)
    val trailT = LongArray(TRAIL)
    var trailHead = 0
    var trailSize = 0
    var lastA = Float.NaN
    val flashAt = LongArray(n) { -10_000L }
    val flashColor = Array(n) { OrbitEmerald }
    val flashDur = LongArray(n) { 450L }
    val fired = BooleanArray(task.steps.size * 2)
    var decoyHit = false
    var decoyHitAt = -10_000L
    var decoyX = 0f
    var decoyY = 0f
    var lastTapAt = 0L
    var halfRt = 0L
    val times = ArrayList<Pair<Int, Long>>()
    var ok = false
    var focus = false
    var points = 0
    var reveal = -1
    var step by mutableIntStateOf(0)
    var frame by mutableLongStateOf(0L)
    val stars =
        List(60) { i ->
            val r = kotlin.random.Random(task.seed + i)
            floatArrayOf(
                r.nextFloat(),
                r.nextFloat(),
                .6f + r.nextFloat() * 1.4f,
                r.nextFloat() * 6.28f,
            )
        }

    fun elapsed() = clock - phaseAt

    fun go(p: Int) {
        phase = p
        phaseAt = clock
    }

    fun flash(k: Int, color: Color, ms: Long = 450L) {
        flashAt[k] = clock
        flashColor[k] = color
        flashDur[k] = ms
    }

    /** Screen angle (radians) of node [k] after turning [rot] positions; 0 is the top. */
    fun angle(k: Int) = (-PI / 2 + (k + rot) * 2 * PI / n).toFloat()

    /** Comet angle during the demonstration, or NaN between paths. */
    fun comet(e: Long): Float {
        val i = (e / task.stepMs).toInt()
        if (i < 0 || i >= task.steps.size) return Float.NaN
        val local = e - i * task.stepMs
        val to = angle(task.steps[i].node)
        val from = if (i == 0) to - 1.6f * (2 * PI / n).toFloat() else angle(task.steps[i - 1].node)
        var d = (to - from) % (2 * PI).toFloat()
        if (d > PI) d -= (2 * PI).toFloat()
        if (d < -PI) d += (2 * PI).toFloat()
        return from + d * ease((local.toFloat() / travel).coerceIn(0f, 1f))
    }

    fun pushTrail(a: Float) {
        if (a.isNaN()) {
            lastA = Float.NaN
            return
        }
        if (!lastA.isNaN()) {
            var d = (a - lastA) % (2 * PI).toFloat()
            if (d > PI) d -= (2 * PI).toFloat()
            if (d < -PI) d += (2 * PI).toFloat()
            val parts = min(60, ceil(abs(d) / .012f).toInt())
            for (s in 1 until parts) add(lastA + d * s / parts)
        }
        add(a)
        lastA = a
    }

    private fun add(a: Float) {
        trailA[trailHead] = a
        trailT[trailHead] = clock
        trailHead = (trailHead + 1) % TRAIL
        trailSize = min(TRAIL, trailSize + 1)
    }

    /**
     * Light (0..1) of node [k] during the demonstration, its colour and whether it shows the double
     * ring.
     */
    fun light(k: Int, e: Long): Triple<Float, Color, Boolean> {
        var best = 0f
        var color = OrbitEmerald
        var double = false
        task.steps.forEachIndexed { i, s ->
            if (s.node != k) return@forEachIndexed
            val on = i * task.stepMs + travel
            val l =
                if (s.kind == Orbit.DOUBLE)
                    max(
                        bump(e, on, lightDur * 45 / 100),
                        bump(e, on + lightDur * 55 / 100, lightDur * 45 / 100),
                    )
                else bump(e, on, lightDur)
            if (l > best) {
                best = l
                color = if (s.kind == Orbit.INVERSE) OrbitMagenta else OrbitEmerald
                double = s.kind == Orbit.DOUBLE
            }
        }
        return Triple(best, color, double)
    }

    companion object {
        const val TRAIL = 256
    }
}

private fun ease(t: Float) = if (t < .5f) 4 * t * t * t else 1 - (-2 * t + 2).pow(3) / 2

private fun bump(t: Long, start: Long, dur: Long): Float {
    val u = (t - start).toFloat() / dur
    return if (u < 0f || u > 1f) 0f else sin(PI.toFloat() * u)
}

/**
 * Plays one round of Órbitas 2. [done] gets the verdict, the encoded [OrbitLog] and whether the
 * partida stops here (Órbita expansiva after a miss).
 */
@Composable
internal fun OrbitBoard(
    task: OrbitTask,
    answering: Boolean,
    done: (Boolean, String, Boolean) -> Unit,
) {
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val note = LocalNote.current
    val world = remember(task) { OrbitWorld(task) }
    val report by rememberUpdatedState(done)
    val measurer = rememberTextMeasurer()

    fun startAnswer() {
        world.go(OrbitPhase.ANSWER)
        world.lastTapAt = world.clock
    }
    fun succeed() {
        world.ok = true
        world.focus = task.decoys.isNotEmpty() && !world.decoyHit
        world.points = Orbit.points(task, true, world.focus)
        // The whole path sounds together as a chord (three voices at most: the sound pool has three
        // streams).
        task.expected.distinct().sorted().take(3).forEach { note(it, true) }
        world.go(OrbitPhase.VERDICT)
    }
    fun fail(tapped: Int) {
        world.ok = false
        world.reveal = world.input.missed
        if (tapped >= 0 && tapped != world.reveal) world.flash(tapped, OrbitCoral, 600L)
        cue(FeedbackCue.RETRY)
        world.go(OrbitPhase.VERDICT)
    }
    fun update() {
        val e = world.elapsed()
        when (world.phase) {
            OrbitPhase.INTRO -> if (e >= Orbit.INTRO_MS) world.go(OrbitPhase.SHOW)
            OrbitPhase.SHOW -> {
                task.steps.forEachIndexed { i, s ->
                    val on = i * task.stepMs + world.travel
                    if (e >= on && !world.fired[2 * i]) {
                        world.fired[2 * i] = true
                        note(s.node, false)
                    }
                    if (
                        s.kind == Orbit.DOUBLE &&
                            e >= on + world.lightDur * 55 / 100 &&
                            !world.fired[2 * i + 1]
                    ) {
                        world.fired[2 * i + 1] = true
                        note(s.node, true)
                    }
                }
                if (motion) world.pushTrail(world.comet(e))
                if (e >= task.showMs) {
                    world.pushTrail(Float.NaN)
                    if (task.turn != 0) {
                        world.go(OrbitPhase.TURN)
                        cue(FeedbackCue.WHOOSH)
                    } else startAnswer()
                }
            }
            OrbitPhase.TURN -> {
                val p = ((e - 200f) / (Orbit.TURN_MS - 400f)).coerceIn(0f, 1f)
                world.rot = task.turn * if (motion) ease(p) else if (e >= 200) 1f else 0f
                if (e >= Orbit.TURN_MS) {
                    world.rot = task.turn.toFloat()
                    startAnswer()
                }
            }
            OrbitPhase.ANSWER -> if (world.input.tick(world.clock, world.phaseAt) != null) fail(-1)
            OrbitPhase.VERDICT ->
                if (e >= if (world.ok) Orbit.VERDICT_OK_MS else Orbit.VERDICT_FAIL_MS) {
                    world.go(OrbitPhase.REPORTED)
                    val missQ =
                        if (world.ok || world.reveal < 0) -1
                        else Orbit.quadrant(world.reveal, task.nodes, task.turn)
                    val log =
                        OrbitLog(
                            world.ok,
                            task.mode.key,
                            task.stage,
                            task.nodes,
                            task.steps.size,
                            world.input.error,
                            world.focus,
                            world.points,
                            world.times.toList(),
                            missQ,
                        )
                    report(world.ok, log.encode(), !world.ok && task.mode == OrbitMode.EXPAND)
                }
        }
    }
    fun tapNode(best: Int) {
        if (world.phase != OrbitPhase.ANSWER) return
        val wasPending = world.input.pending >= 0
        when (world.input.tap(best, world.clock)) {
            OrbitTap.HALF -> {
                world.halfRt = world.clock - world.lastTapAt
                world.lastTapAt = world.clock
                note(best, false)
                world.flash(best, OrbitCyan, 300L)
            }
            OrbitTap.STEP,
            OrbitTap.DONE -> {
                val rt = if (wasPending) world.halfRt else world.clock - world.lastTapAt
                world.lastTapAt = world.clock
                world.times += Orbit.quadrant(best, task.nodes, task.turn) to rt
                note(best, wasPending)
                world.flash(best, OrbitEmerald)
                world.step = world.input.index
                if (world.input.finished) succeed()
            }
            OrbitTap.WRONG -> fail(best)
            OrbitTap.IGNORED -> Unit
        }
    }

    fun tap(x: Float, y: Float, w: Float, h: Float) {
        val s = min(w, h)
        val cx = w / 2
        val cy = h / 2
        when (world.phase) {
            OrbitPhase.SHOW -> {
                val e = world.elapsed()
                for (d in task.decoys) {
                    val age = e - d.atMs
                    if (
                        age in -50L..Orbit.DECOY_LIFE_MS + 350L &&
                            hypot(x - (cx - s / 2 + d.x * s), y - (cy - s / 2 + d.y * s)) < s * .09f
                    ) {
                        world.decoyHit = true
                        world.decoyHitAt = world.clock
                        world.decoyX = d.x
                        world.decoyY = d.y
                        cue(FeedbackCue.TAP)
                    }
                }
            }
            OrbitPhase.ANSWER -> {
                val r = s * .36f
                val nr = s * if (task.nodes >= 10) .066f else .074f
                var best = -1
                var bd = Float.MAX_VALUE
                for (k in 0 until task.nodes) {
                    val a = world.angle(k)
                    val d = hypot(x - (cx + cos(a) * r), y - (cy + sin(a) * r))
                    if (d < bd) {
                        bd = d
                        best = k
                    }
                }
                if (best >= 0 && bd <= nr * 1.45f) tapNode(best)
            }
        }
    }
    LaunchedEffect(world, answering) {
        if (!answering) return@LaunchedEffect
        var last = -1L
        while (isActive && world.phase != OrbitPhase.REPORTED) {
            withFrameNanos { t ->
                world.clock += if (last < 0) 0L else ((t - last) / 1_000_000L).coerceIn(0L, 50L)
                last = t
                update()
                world.frame = t
            }
        }
    }

    val mirror = task.mode == OrbitMode.MIRROR
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Label(
            "Fase ${task.stage + 1} · ${Orbit.stageNames[task.stage]}" +
                if (task.mode == OrbitMode.EXPAND) " · ${task.nodes} nodos" else "",
            MaterialTheme.colorScheme.primary,
        )
        Text(
            if (mirror && task.stage == 0) "Recuerda el camino y repítelo al revés"
            else Orbit.stageTitles[task.stage],
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
        )
    }
    val description =
        when (world.phase) {
            OrbitPhase.INTRO,
            OrbitPhase.SHOW -> "Órbita de ${task.nodes} nodos. Observa el camino de luz."
            OrbitPhase.TURN -> "La órbita está girando."
            OrbitPhase.ANSWER -> "Tu turno: paso ${world.step + 1} de ${task.expected.size}."
            else ->
                if (world.ok) "Bien: más ${world.points} puntos."
                else "Fallo: ${Orbit.errorText(world.input.error)}."
        }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        Canvas(
            Modifier.fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, DeckEdge, RoundedCornerShape(28.dp))
                .semantics { contentDescription = description }
                .pointerInput(world) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        tap(
                            down.position.x,
                            down.position.y,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                    }
                }
        ) {
            world.frame // redraw on every frame of the board clock
            drawOrbit(world, motion) { text, color, px, at, bold ->
                val layout =
                    measurer.measure(
                        text,
                        TextStyle(
                            color = color,
                            fontSize = px.toSp(),
                            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
                            letterSpacing = if (bold) (px * .04f).toSp() else 0f.toSp(),
                        ),
                    )
                drawText(
                    layout,
                    topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f),
                )
            }
        }
        // Screen readers: one invisible target per node, named by its clock position (no numbers on
        // screen, none read aloud).
        val side = min(maxWidth.value, maxHeight.value).dp
        val node = side * (if (task.nodes >= 10) .132f else .148f)
        val turned = if (world.phase >= OrbitPhase.ANSWER) task.turn else 0
        for (k in 0 until task.nodes) {
            val deg = Orbit.angle(k, task.nodes, turned)
            val a = Math.toRadians(deg - 90.0)
            Box(
                Modifier.offset(
                        side / 2 + side * .36f * cos(a).toFloat() - node / 2,
                        side / 2 + side * .36f * sin(a).toFloat() - node / 2,
                    )
                    .size(node)
                    .semantics {
                        contentDescription = "Nodo a las ${Orbit.clock(deg)}"
                        role = Role.Button
                        onClick {
                            tapNode(k)
                            true
                        }
                    }
            )
        }
    }
}

private fun DrawScope.drawOrbit(
    w: OrbitWorld,
    motion: Boolean,
    text: DrawScope.(String, Color, Float, Offset, Boolean) -> Unit,
) {
    val t = w.task
    val s = size.minDimension
    val c = center
    val r = s * .36f
    val nr = s * if (t.nodes >= 10) .066f else .074f
    val e = w.elapsed()
    drawRect(Brush.radialGradient(listOf(DeckGlow, Deck), c, s * .75f))
    val ox = c.x - s / 2
    val oy = c.y - s / 2
    for (st in w.stars) drawCircle(
        Color(0xFFC8D2FF)
            .copy(alpha = if (motion) .22f + .2f * sin(w.clock / 1250f + st[3]) else .3f),
        st[2] * density * .7f,
        Offset(ox + st[0] * s, oy + st[1] * s),
    )
    drawCircle(NodeRing.copy(alpha = .55f), r, c, style = Stroke(2.dp.toPx()))
    if (w.phase == OrbitPhase.TURN)
        drawCircle(
            OrbitAmber.copy(
                alpha = .35f * sin(PI.toFloat() * (e.toFloat() / Orbit.TURN_MS).coerceIn(0f, 1f))
            ),
            r + nr + 10.dp.toPx(),
            c,
            style = Stroke(3.dp.toPx()),
        )
    // Trail: fades in 600 ms.
    if (motion)
        for (i in 0 until w.trailSize) {
            val idx = (w.trailHead - 1 - i + OrbitWorld.TRAIL) % OrbitWorld.TRAIL
            val life = 1f - (w.clock - w.trailT[idx]).toFloat() / Orbit.TRAIL_MS
            if (life <= 0f) continue
            val a = w.trailA[idx]
            drawCircle(
                OrbitCyan.copy(alpha = .16f * life),
                (2f + 7f * life) * density,
                Offset(c.x + cos(a) * r, c.y + sin(a) * r),
                blendMode = BlendMode.Plus,
            )
        }
    for (k in 0 until t.nodes) {
        val a = w.angle(k)
        val p = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
        var light = 0f
        var color = OrbitEmerald
        var double = false
        if (w.phase == OrbitPhase.SHOW) {
            val (l, col, d) = w.light(k, e)
            light = l
            color = col
            double = d
        }
        val f = 1f - (w.clock - w.flashAt[k]).toFloat() / w.flashDur[k]
        if (f > light) {
            light = f
            color = w.flashColor[k]
            double = false
        }
        drawNode(p, nr, light.coerceIn(0f, 1f), color, double, w.phase == OrbitPhase.ANSWER)
        if (w.input.pending == k && w.phase == OrbitPhase.ANSWER)
            drawArc(
                OrbitCyan,
                -90f,
                360f *
                    (1f - (w.clock - w.lastTapAt).toFloat() / Orbit.DOUBLE_WINDOW_MS).coerceIn(
                        0f,
                        1f,
                    ),
                false,
                Offset(p.x - nr - 7.dp.toPx(), p.y - nr - 7.dp.toPx()),
                Size((nr + 7.dp.toPx()) * 2, (nr + 7.dp.toPx()) * 2),
                style = Stroke(2.dp.toPx()),
            )
        if (w.phase >= OrbitPhase.VERDICT && !w.ok && w.reveal == k)
            drawCircle(
                OrbitEmerald.copy(alpha = .6f + .4f * sin(w.clock / 120f)),
                nr + 7.dp.toPx(),
                p,
                style =
                    Stroke(
                        2.5f.dp.toPx(),
                        pathEffect =
                            PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 5.dp.toPx()),
                                if (motion) -w.clock * .02f * density else 0f,
                            ),
                    ),
            )
    }
    if (w.phase == OrbitPhase.SHOW) {
        if (motion) {
            val a = w.comet(e)
            if (!a.isNaN()) {
                val h = Offset(c.x + cos(a) * r, c.y + sin(a) * r)
                val rr = 18.dp.toPx()
                drawCircle(
                    Brush.radialGradient(
                        listOf(
                            Color.White.copy(alpha = .95f),
                            OrbitCyan.copy(alpha = .7f),
                            OrbitCyan.copy(alpha = 0f),
                        ),
                        h,
                        rr,
                    ),
                    rr,
                    h,
                )
            }
        }
        for (d in t.decoys) {
            val age = e - d.atMs
            if (age < 0 || age > Orbit.DECOY_LIFE_MS) continue
            val v = sin(PI.toFloat() * age / Orbit.DECOY_LIFE_MS)
            val at = Offset(ox + d.x * s, oy + d.y * s)
            val rr = (6f + 14f * v) * density
            val path = Path()
            for (i in 0 until 8) {
                val rad = if (i % 2 == 1) rr * .22f else rr
                val ang = i * PI.toFloat() / 4 + (if (motion) age / 330f else 0f)
                if (i == 0) path.moveTo(at.x + cos(ang) * rad, at.y + sin(ang) * rad)
                else path.lineTo(at.x + cos(ang) * rad, at.y + sin(ang) * rad)
            }
            path.close()
            drawPath(path, OrbitAmber.copy(alpha = .85f * v), blendMode = BlendMode.Plus)
        }
    }
    val hit = (w.clock - w.decoyHitAt).toFloat() / 400f
    if (hit in 0f..1f)
        drawCircle(
            OrbitCoral.copy(alpha = 1f - hit),
            (8f + 20f * hit) * density,
            Offset(ox + w.decoyX * s, oy + w.decoyY * s),
            style = Stroke(2.dp.toPx()),
        )
    // Core: the only "Observa" on screen, wrapped in its time ring.
    val cr = r * .36f
    val ring = cr - 6.dp.toPx()
    val stroke = 5.dp.toPx()
    drawCircle(CoreFill, cr, c)
    drawCircle(Color.White.copy(alpha = .06f), ring, c, style = Stroke(stroke))
    val box = Offset(c.x - ring, c.y - ring)
    val boxSize = Size(ring * 2, ring * 2)
    var label = "OBSERVA"
    var sub = ""
    var labelColor = OrbitInk
    when (w.phase) {
        OrbitPhase.INTRO -> if (t.mode == OrbitMode.MIRROR) sub = "luego, al revés"
        OrbitPhase.SHOW -> {
            val frac = (e.toFloat() / t.showMs).coerceIn(0f, 1f)
            val parts = 48
            for (i in 0 until (parts * frac).toInt().coerceAtLeast(1)) drawArc(
                lerp(OrbitCyan, OrbitAmber, i / parts.toFloat()),
                -90f + i * 360f / parts,
                360f / parts + .6f,
                false,
                box,
                boxSize,
                style = Stroke(stroke),
            )
        }
        OrbitPhase.TURN -> {
            label = "GIRA"
            labelColor = OrbitAmber
            drawArc(
                OrbitAmber,
                -90f + (if (motion) e * .3f * sign(t.turn.toFloat()) else 0f),
                70f,
                false,
                box,
                boxSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        OrbitPhase.ANSWER -> {
            label = "TU TURNO"
            labelColor = OrbitEmerald
            sub = "${w.input.index} de ${t.expected.size}"
            val left = (1f - e.toFloat() / t.answerMs).coerceIn(0f, 1f)
            drawArc(
                if (left < .25f) OrbitCoral else OrbitEmerald,
                -90f,
                360f * left,
                false,
                box,
                boxSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        else -> {
            label = if (w.ok) "BIEN" else "FALLO"
            labelColor = if (w.ok) OrbitEmerald else OrbitCoral
            sub =
                if (w.ok) "+${w.points}" + (if (w.focus) " · enfoque" else "")
                else Orbit.errorText(w.input.error)
            drawCircle(labelColor, ring, c, style = Stroke(stroke))
        }
    }
    val fs = max(12.dp.toPx(), cr * .26f)
    text(label, labelColor, fs, Offset(c.x, c.y - if (sub.isEmpty()) 0f else fs * .4f), true)
    if (sub.isNotEmpty()) text(sub, OrbitMute, fs * .62f, Offset(c.x, c.y + fs * .72f), false)
}

private fun DrawScope.drawNode(
    p: Offset,
    r: Float,
    light: Float,
    color: Color,
    double: Boolean,
    armed: Boolean,
) {
    if (light > .01f)
        drawCircle(
            Brush.radialGradient(
                listOf(color.copy(alpha = .45f * light), color.copy(alpha = 0f)),
                p,
                r * 2.4f,
            ),
            r * 2.4f,
            p,
            blendMode = BlendMode.Plus,
        )
    drawCircle(NodeFill, r, p)
    drawCircle(
        if (light > .01f) color.copy(alpha = .35f + .65f * light)
        else NodeRing.copy(alpha = if (armed) 1f else .75f),
        r,
        p,
        style = Stroke((2f + 1.5f * light) * density),
    )
    drawCircle(
        if (light > .01f) color.copy(alpha = .15f + .85f * light)
        else Color(0xFF060814).copy(alpha = .55f),
        r * .42f,
        p,
    )
    if (double && light > .01f)
        drawCircle(color.copy(alpha = light), r + 7.dp.toPx(), p, style = Stroke(1.8f * density))
}

// ---------- Unified progress bar (rounds, points and the record ghost in one place) ----------

/**
 * Running points of the best earlier partida in the same mode, round by round; null when there is
 * none yet.
 */
@Composable
internal fun rememberOrbitGhost(vm: BrainViewModel, run: Run): List<Int>? =
    remember(run.id, run.variant) {
        val mode = OrbitMode.of(run.variant)
        vm.results.value
            .asSequence()
            .filter { it.game == Orbit.ID && it.id != run.id }
            .map(::boardLogs)
            .filter { logs ->
                logs.any { OrbitLog.decode(it) != null } && Orbit.modeOf(logs) == mode
            }
            .map(Orbit::cumulative)
            .maxByOrNull { it.lastOrNull() ?: 0 }
            ?.takeIf { (it.lastOrNull() ?: 0) > 0 }
    }

/**
 * One segmented bar of 12 blocks (emerald right, coral wrong), the points line and the gold ghost
 * tick inside it.
 */
@Composable
internal fun OrbitHeader(run: Run, logs: List<String>, ghost: List<Int>?, endlessRound: Int?) {
    val motion = LocalMotionEnabled.current
    val colors = MaterialTheme.colorScheme
    val points = Orbit.cumulative(logs).lastOrNull() ?: 0
    val played = logs.size
    val ghostNow = ghost?.let { if (played == 0) 0 else it[min(played, it.size) - 1] }
    val pulse =
        if (motion)
            rememberInfiniteTransition(label = "Ronda actual")
                .animateFloat(
                    1f,
                    .45f,
                    infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "Pulso",
                )
                .value
        else 1f
    val current = areaColor(Area.MEMORY)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(
                (if (endlessRound != null) "Ronda ${endlessRound + 1}"
                else "Ronda ${run.index + 1} / ${Orbit.ROUNDS}") + " · $points pts"
            )
            Spacer(Modifier.weight(1f))
            if (ghost != null && ghostNow != null) {
                val diff = points - ghostNow
                Text(
                    when {
                        played == 0 -> "Fantasma · récord ${ghost.last()}"
                        diff > 0 -> "Fantasma: +$diff"
                        diff == 0 -> "Fantasma: empatado"
                        else -> "Fantasma: −${-diff}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (played > 0 && diff > 0) OrbitEmerald else ChestGold,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().height(8.dp).semantics {
                contentDescription =
                    "Ronda ${run.index + 1} de ${Orbit.ROUNDS}: ${run.outcomes.count { it.correct }} aciertos"
            },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (i in 0 until Orbit.ROUNDS) {
                val o = run.outcomes.getOrNull(i)
                val fill =
                    when {
                        o == null ->
                            if (i == run.index) current.copy(alpha = pulse)
                            else colors.surfaceVariant
                        o.reason == "survival" -> colors.surfaceVariant.copy(alpha = .4f)
                        o.correct -> OrbitEmerald
                        else -> OrbitCoral
                    }
                Box(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(fill)
                )
            }
        }
        if (ghost != null && ghostNow != null) {
            val scale = max(max(ghost.last(), points), 1).toFloat()
            val me by
                animateFloatAsState(
                    points / scale,
                    if (motion) tween(400) else snap(),
                    label = "Puntos",
                )
            val gx by
                animateFloatAsState(
                    ghostNow / scale,
                    if (motion) tween(400) else snap(),
                    label = "Fantasma",
                )
            Canvas(
                Modifier.fillMaxWidth().height(9.dp).semantics {
                    contentDescription = "Llevas $points puntos; tu récord iba por $ghostNow"
                }
            ) {
                val y = size.height / 2
                val h = 3.dp.toPx()
                drawRoundRect(
                    colors.surfaceVariant,
                    Offset(0f, y - h / 2),
                    Size(size.width, h),
                    androidx.compose.ui.geometry.CornerRadius(h),
                )
                drawRoundRect(
                    current.copy(alpha = .75f),
                    Offset(0f, y - h / 2),
                    Size(size.width * me, h),
                    androidx.compose.ui.geometry.CornerRadius(h),
                )
                drawLine(
                    ChestGold,
                    Offset(size.width * gx, 0f),
                    Offset(size.width * gx, size.height),
                    2.dp.toPx(),
                )
            }
        }
    }
}

// ---------- Intro: mode picker ----------

@Composable
internal fun OrbitModePicker(selected: OrbitMode, choose: (OrbitMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Label("Modo")
        OrbitMode.values().forEach { m ->
            val on = m == selected
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (on) colors.primary.copy(alpha = .10f).compositeOver(colors.surface)
                        else colors.surface
                    )
                    .border(
                        if (on) 2.dp else 1.dp,
                        if (on) colors.primary else colors.outlineVariant,
                        RoundedCornerShape(18.dp),
                    )
                    .selectable(on, role = Role.RadioButton) { choose(m) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(on, null)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        m.about,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------- Result: attention map by quadrant ----------

@Composable
internal fun OrbitAttention(r: ResultRow) {
    val logs = remember(r.id) { boardLogs(r) }
    val parsed = remember(r.id) { logs.mapNotNull(OrbitLog::decode) }
    if (parsed.isEmpty()) return
    val quadrants = remember(r.id) { Orbit.attention(logs) }
    val colors = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val es = Locale("es")
    fun seconds(ms: Long) = String.format(es, "%.2f s", ms / 1000.0)
    val heat =
        quadrants.map { q ->
            if (q.touches == 0 && q.misses == 0) null
            else (q.meanMs ?: 1500L) / 1000f + q.misses * .6f
        }
    val lo = heat.filterNotNull().minOrNull() ?: 0f
    val hi = heat.filterNotNull().maxOrNull() ?: 0f
    Panel {
        Label("Mapa de atención espacial", colors.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                    "${parsed.sumOf { it.points }}" to "puntos",
                    "${parsed.count { it.focus }}" to "bonos de enfoque",
                    OrbitMode.of(parsed.first().mode).title to "modo",
                )
                .forEach { (v, l) ->
                    Column(Modifier.weight(1f)) {
                        Text(v, style = MaterialTheme.typography.titleMedium)
                        Text(
                            l,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
        }
        val ink = colors.onSurface
        val mute = colors.onSurfaceVariant
        val empty = colors.surfaceVariant
        val missColor = colors.error
        Canvas(
            Modifier.fillMaxWidth().height(230.dp).semantics {
                contentDescription = "Mapa de la órbita por cuadrantes con tiempo medio y fallos"
            }
        ) {
            val s = size.minDimension
            val rr = s * .36f
            val wdt = s * .09f
            drawCircle(empty, rr, center, style = Stroke(1.dp.toPx()))
            quadrants.forEachIndexed { i, q ->
                val v = heat[i]
                val color =
                    if (v == null) empty
                    else {
                        val x = if (hi > lo) (v - lo) / (hi - lo) else 0f
                        if (x < .5f) lerp(OrbitCyan, OrbitAmber, x * 2)
                        else lerp(OrbitAmber, OrbitCoral, (x - .5f) * 2)
                    }
                drawArc(
                    color,
                    -90f + i * 90f + 4f,
                    82f,
                    false,
                    Offset(center.x - rr, center.y - rr),
                    Size(rr * 2, rr * 2),
                    style = Stroke(wdt, cap = StrokeCap.Round),
                )
                val am = Math.toRadians(-90.0 + i * 90 + 45).toFloat()
                val at = Offset(center.x + cos(am) * rr * .55f, center.y + sin(am) * rr * .55f)
                val top =
                    measurer.measure(
                        q.meanMs?.let(::seconds) ?: "—",
                        TextStyle(
                            color = ink,
                            fontSize = (s * .05f).toSp(),
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                drawText(top, topLeft = Offset(at.x - top.size.width / 2f, at.y - top.size.height))
                val bottom =
                    measurer.measure(
                        if (q.misses == 1) "1 fallo" else "${q.misses} fallos",
                        TextStyle(
                            color = if (q.misses > 0) missColor else mute,
                            fontSize = (s * .04f).toSp(),
                        ),
                    )
                drawText(bottom, topLeft = Offset(at.x - bottom.size.width / 2f, at.y + 2f))
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            Text(
                "Rápido y preciso",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            Box(
                Modifier.width(80.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(OrbitCyan, OrbitAmber, OrbitCoral)))
            )
            Text(
                "Lento o con fallos",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
        }
        val timed = quadrants.withIndex().filter { it.value.meanMs != null }
        if (timed.size >= 2) {
            val slow = timed.maxBy { it.value.meanMs!! }
            val fast = timed.minBy { it.value.meanMs!! }
            Text(
                "Reaccionas más despacio en el cuadrante ${Orbit.quadrantNames[slow.index]} (${seconds(slow.value.meanMs!!)} de media) y más rápido en el ${Orbit.quadrantNames[fast.index]} (${seconds(fast.value.meanMs!!)}).",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val worst =
            quadrants.withIndex().filter { it.value.misses > 0 }.maxByOrNull { it.value.misses }
        Text(
            if (worst != null)
                "La mayoría de tus fallos están en el cuadrante ${Orbit.quadrantNames[worst.index]}. En la próxima sesión, fija la mirada en el núcleo y sigue esa zona con la visión periférica."
            else "Ningún fallo por cuadrante: tu atención se reparte bien por toda la órbita.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
    }
}
