package personal.qqq.braintraining

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.ceil
import kotlin.math.floor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

/* Fase 2 (docs/DISENO-JUEGOS-V2.md §3): Vigía de alertas. One Canvas draws the monitor from primitive arrays; one frame loop
 * per screen, stopped when the screen is not resumed, when the tramo ends or with animations off (then the monitor steps
 * one line at a time with plain delays). Rules and scoring: lab-core/Vigil.kt (VigilShift). */

private val Night = Color(0xFF0E1420)
private val Dusk = Color(0xFF2E2210)
private val LogInk = Color(0xFFC8D3E6)
private val LogDim = Color(0xFF7D8BA3)
/** Height of one monitor row (two text lines: clock · src, then the event). */
internal val VigilRow = 48.dp

/**
 * World of one tramo: primitive arrays for the draw phase, Compose state only for what the header
 * and texts show.
 */
@Stable
private class VigilWorld(val task: VigilTask, val tuning: VigilTuning, val freezesBefore: Int) {
    val shift = VigilShift(task)
    val n = task.lines.size
    val total = Vigil.timeFor(n.toFloat(), task.intervalMs, tuning.speed)
    /**
     * Lines scrolled (continuous), tramo clock (stops while frozen) and animation clock (always
     * runs), in ms.
     */
    var pos = 0f
    var clock = 0L
    var anim = 0L
    var frozenUntil = 0L
    var lastBeat = 0L
    val hitAt = LongArray(n) { -1L }
    val falseAt = LongArray(n) { -1L }
    val lapseAt = LongArray(n) { -1L }
    /** Lapse wall: line of each stub, the y it flew from and when. */
    val wall = IntArray(8)
    val wallAt = LongArray(8)
    var wallCount = 0
    val particles = Particles()
    var ended = false
    var root: LayoutCoordinates? = null
    var monitor: LayoutCoordinates? = null
    var shieldsAt: LayoutCoordinates? = null
    // Compose state
    var hits by mutableIntStateOf(0)
    var falses by mutableIntStateOf(0)
    var lapses by mutableIntStateOf(0)
    var shields by mutableIntStateOf(Vigil.SHIELDS)
    var freezes by mutableIntStateOf(0)
    var frozen by mutableStateOf(false)
    var top by mutableIntStateOf(0)
    var message by mutableStateOf<Pair<Int, String>?>(null)
    var log by mutableStateOf<VigilLog?>(null)
    val freezesLeft
        get() = (Vigil.FREEZES - freezesBefore - freezes).coerceAtLeast(0)

    val emitted
        get() = ceil(pos).toInt().coerceIn(0, n)

    fun say(text: String) {
        message = (message?.first ?: 0) + 1 to text
    }
}

/**
 * Vigía de alertas. [previous] are the logs of the tramos already played (announced tuning, freezes
 * left), [index] this tramo (0–3), [ghost] the detection % the record run had in this same tramo.
 * [feedback] is accepted for symmetry with the other boards: the board keeps showing its final
 * state (relevo) during the feedback phase.
 */
@Composable
fun VigilBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    previous: List<String>,
    index: Int,
    ghost: Int?,
    done: (Boolean, String) -> Unit,
) {
    val task = c.vigil ?: return
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val colors = MaterialTheme.colorScheme
    val logs = remember(c) { previous.mapNotNull(VigilLog::decode) }
    val world = remember(c) { VigilWorld(task, Vigil.tuning(logs), logs.sumOf { it.freezes }) }
    val scope = rememberCoroutineScope()
    val shake = rememberShake()
    val density = LocalDensity.current
    var frame by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val rowPx = with(density) { VigilRow.toPx() }

    fun at(c: LayoutCoordinates?, local: Offset): Offset? {
        val r = world.root ?: return null
        if (c == null || !c.isAttached || !r.isAttached) return null
        return r.localPositionOf(c, local)
    }
    fun end() {
        if (world.ended) return
        world.ended = true
        val log = world.shift.finish(world.freezes)
        world.log = log
        if (log.perfect) {
            at(world.shieldsAt, Offset.Zero)?.let { o ->
                val s = world.shieldsAt!!.size
                for (k in 0 until 3) Juice.spark(
                    world.particles,
                    o.x + s.width * (k + .5f) / 3f,
                    o.y + s.height / 2f,
                    Juice.Info,
                    motion,
                )
            }
            cue(FeedbackCue.COMPLETE)
        }
        if (world.shift.over) scope.launch { shake.play(Shake.BIG, motion) }
        scope.launch {
            delay(Juice.Hold.toLong())
            done(log.passed, log.encode())
        }
    }
    /** Exits, lapses, tension beat and the end of the tramo after the monitor moved. */
    fun process() {
        val exited = floor(world.pos).toInt() - Vigil.VISIBLE
        for (g in world.shift.advance(exited)) {
            g.lines.forEach { world.lapseAt[it] = world.anim }
            if (world.wallCount < world.wall.size) {
                val i = world.wallCount++
                world.wall[i] = g.last
                world.wallAt[i] = world.anim
            }
            world.lapses = world.shift.lapses
            world.shields = world.shift.shields
            world.say("Se te pasó: ${Vigil.patternNames[g.pattern]}. ${Vigil.lessons[g.pattern]}")
            cue(FeedbackCue.ALARM)
            scope.launch { shake.play(Shake.MEDIUM, motion) }
        }
        if (world.clock > world.total - 10_000 && world.clock - world.lastBeat >= 2_000) {
            world.lastBeat = world.clock
            cue(FeedbackCue.WHOOSH)
        }
        val t = floor(world.pos).toInt()
        if (t != world.top) world.top = t
        if (world.pos >= world.n || world.shift.over) end()
    }
    fun tapLine(k: Int) {
        if (!answering || world.ended || k !in 0 until world.emitted) return
        when (world.shift.mark(k, world.emitted)) {
            VigilShift.Mark.HIT -> {
                val g = task.groupOf(k)!!
                g.lines.forEach { world.hitAt[it] = world.anim }
                world.hits = world.shift.hits
                world.message = null
                val y = monitorH(density) - (world.pos - k) * rowPx + rowPx / 2
                at(world.monitor, Offset(0f, y))?.let {
                    Juice.hit(world.particles, it.x, it.y, motion)
                }
                cue(FeedbackCue.CORRECT)
                world.say("✓ ${Vigil.patternNames[g.pattern]} detectada")
            }
            VigilShift.Mark.EARLY ->
                world.say(
                    "Todavía no es un patrón: un fallo aislado es normal. Espera a ver más líneas."
                )
            VigilShift.Mark.FALSE -> {
                world.falseAt[k] = world.anim
                world.falses = world.shift.falses
                world.shields = world.shift.shields
                cue(FeedbackCue.RETRY)
                world.say("Falsa alarma (−½ escudo). ${Vigil.whyText[task.lines[k].why]}")
                if (world.shift.over) end()
            }
            VigilShift.Mark.IGNORED -> Unit
        }
    }
    fun freeze() {
        if (!answering || world.ended || world.frozen || world.freezesLeft == 0) return
        world.freezes++
        world.frozen = true
        world.frozenUntil = world.anim + Vigil.FREEZE_MS
        cue(FeedbackCue.TAP)
    }

    // The single loop of this screen. Frames only while visible, answering and with motion;
    // otherwise discrete steps.
    LaunchedEffect(world, answering, motion, resumed) {
        if (!answering || !resumed) return@LaunchedEffect
        if (motion) {
            var last = 0L
            while (isActive && (!world.ended || world.particles.active)) withFrameNanos { now ->
                val dt = if (last == 0L) 0L else ((now - last) / 1_000_000L).coerceAtMost(50L)
                last = now
                world.anim += dt
                if (world.frozen && world.anim >= world.frozenUntil) world.frozen = false
                if (!world.ended) {
                    if (!world.frozen) {
                        world.clock += dt
                        world.pos =
                            Vigil.scrolled(world.clock, task.intervalMs, world.tuning.speed)
                                .coerceAtMost(world.n.toFloat())
                    }
                    process()
                }
                world.particles.step(dt / 1000f)
                frame = now
                FrameProbe.frames++
            }
        } else {
            while (isActive && !world.ended) {
                val next = floor(world.pos) + 1f
                val wait =
                    (Vigil.timeFor(next, task.intervalMs, world.tuning.speed) - world.clock)
                        .coerceAtLeast(16L)
                delay(wait)
                world.anim += wait
                world.clock += wait
                if (world.frozen) {
                    delay(Vigil.FREEZE_MS)
                    world.anim += Vigil.FREEZE_MS
                    world.frozen = false
                }
                world.pos = next.coerceAtMost(world.n.toFloat())
                process()
                frame++
            }
        }
    }

    val measurer = rememberTextMeasurer()
    val topStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = LogDim)
    val eventStyle =
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = LogInk,
        )
    // Text measured once per tramo, never in draw.
    val tops =
        remember(task, density) {
            Array(world.n) {
                measurer.measure("${task.lines[it].clock}  ${task.lines[it].src}", topStyle)
            }
        }
    val events =
        remember(task, density) {
            Array(world.n) { measurer.measure(task.lines[it].event, eventStyle) }
        }
    val badges =
        remember(density) {
            arrayOf(
                measurer.measure("AUTH", topStyle.copy(fontSize = 11.sp, color = Juice.Info)),
                measurer.measure("FW", topStyle.copy(fontSize = 11.sp, color = Juice.NetAmber)),
            )
        }
    val stubs =
        remember(density) {
            Array(Vigil.patternShort.size) {
                measurer.measure(
                    Vigil.patternShort[it],
                    TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White),
                )
            }
        }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { world.root = it }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            VigilHeader(world, task, index, ghost)
            Row(
                Modifier.fillMaxWidth()
                    .height(monitorDp())
                    .shake(shake, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Night),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val top = world.top
                val visible =
                    (maxOf(0, top - Vigil.VISIBLE)..minOf(world.n - 1, world.emitted - 1)).toList()
                Canvas(
                    Modifier.weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .onGloballyPositioned { world.monitor = it }
                        .pointerInput(world, answering) {
                            detectTapGestures(onLongPress = { freeze() }) { o ->
                                tapLine(floor(world.pos - (size.height - o.y) / rowPx).toInt())
                            }
                        }
                        .semantics {
                            contentDescription =
                                "Monitor de registros, ${visible.size} líneas visibles. Toca una línea para marcarla como alerta; mantén pulsado para congelar."
                            customActions =
                                visible.reversed().map { k ->
                                    val l = task.lines[k]
                                    CustomAccessibilityAction(
                                        "Marcar ${l.clock} ${l.src} ${l.event}"
                                    ) {
                                        tapLine(k)
                                        true
                                    }
                                }
                        }
                ) {
                    frame
                    drawMonitor(world, tops, events, badges, rowPx)
                }
                Canvas(
                    Modifier.width(54.dp).fillMaxHeight().background(Color(0xFF0A0F18)).semantics {
                        contentDescription = "Muro de lapsos: ${world.lapses}"
                    }
                ) {
                    frame
                    drawWall(world, stubs)
                }
            }
            VigilFooter(world, feedback, logs, task) { freeze() }
        }
        Canvas(Modifier.matchParentSize()) {
            frame
            with(world.particles) { draw() }
        }
    }
}

private fun monitorDp() = VigilRow * Vigil.VISIBLE

private fun monitorH(d: Density) = with(d) { monitorDp().toPx() }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMonitor(
    w: VigilWorld,
    tops: Array<TextLayoutResult>,
    events: Array<TextLayoutResult>,
    badges: Array<TextLayoutResult>,
    row: Float,
) {
    // Night blue that turns amber in the last 5 s of the tramo.
    val tension = ((w.clock - (w.total - 5_000)) / 5_000f).coerceIn(0f, 1f)
    drawRect(lerp(Night, Dusk, tension))
    val pad = 12.dp.toPx()
    val first = maxOf(0, floor(w.pos).toInt() - Vigil.VISIBLE - 1)
    val last = minOf(w.n - 1, ceil(w.pos).toInt() - 1)
    for (k in first..last) {
        val y = size.height - (w.pos - k) * row
        var x = 0f
        var alpha = 1f
        var ink = LogInk
        var dim = LogDim
        val hit = w.hitAt[k]
        val bad = w.falseAt[k]
        val lapse = w.lapseAt[k]
        when {
            hit >= 0 -> {
                val p = ((w.anim - hit) / Juice.Move.toFloat()).coerceIn(0f, 1f)
                x = Juice.EaseIn.transform(p) * size.width
                alpha = 1f - p
                drawRect(Juice.Good.copy(alpha = .28f * alpha), Offset(x, y), Size(size.width, row))
                ink = Juice.Good
                dim = Juice.Good
            }
            lapse >= 0 -> {
                drawRect(Juice.Bad.copy(alpha = .26f), Offset(0f, y), Size(size.width, row))
                ink = Juice.Bad
                dim = Juice.Bad.copy(alpha = .8f)
            }
            bad >= 0 -> {
                val t = w.anim - bad
                val on = t < 360 && (t / Juice.Tick) % 2 == 0L
                drawRect(
                    Juice.NetAmber.copy(alpha = if (on) .35f else .08f),
                    Offset(0f, y),
                    Size(size.width, row),
                )
                ink = Juice.NetAmber.copy(alpha = .8f)
            }
            k % 2 == 1 ->
                drawRect(Color.White.copy(alpha = .025f), Offset(0f, y), Size(size.width, row))
        }
        if (alpha <= 0f) continue
        drawText(tops[k], color = dim, topLeft = Offset(pad + x, y + 5.dp.toPx()), alpha = alpha)
        drawText(events[k], color = ink, topLeft = Offset(pad + x, y + 23.dp.toPx()), alpha = alpha)
        if (w.task.channels == 2) {
            val b = badges[w.task.lines[k].channel.coerceIn(0, 1)]
            drawText(
                b,
                topLeft = Offset(size.width - b.size.width - pad + x, y + 6.dp.toPx()),
                alpha = alpha,
            )
        }
    }
    // Fade at the top edge (where lines leave) and the input edge at the bottom.
    for (i in 0 until 4) drawRect(
        lerp(Night, Dusk, tension).copy(alpha = .55f - i * .13f),
        Offset(0f, i * 6.dp.toPx()),
        Size(size.width, 6.dp.toPx()),
    )
    drawRect(
        Juice.Info.copy(alpha = .35f),
        Offset(0f, size.height - 2.dp.toPx()),
        Size(size.width, 2.dp.toPx()),
    )
    if (w.frozen) {
        drawRect(Juice.Info.copy(alpha = .08f))
        drawRect(Juice.Info.copy(alpha = .7f), Offset.Zero, size, style = Stroke(3.dp.toPx()))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWall(
    w: VigilWorld,
    stubs: Array<TextLayoutResult>,
) {
    val h = 30.dp.toPx()
    val gap = 6.dp.toPx()
    val inset = 5.dp.toPx()
    for (i in 0 until w.wallCount) {
        val p = Juice.springAt((w.anim - w.wallAt[i]) / 1000f)
        val target = gap + i * (h + gap)
        val y = size.height * .5f + (target - size.height * .5f) * p
        val x = -size.width * (1f - p.coerceAtMost(1f))
        drawRoundRect(
            Juice.Bad,
            Offset(inset + x, y),
            Size(size.width - inset * 2, h),
            CornerRadius(8.dp.toPx()),
        )
        val s = stubs[w.task.groupOf(w.wall[i])?.pattern ?: 0]
        drawText(
            s,
            topLeft =
                Offset(
                    inset + x + (size.width - inset * 2 - s.size.width) / 2,
                    y + (h - s.size.height) / 2,
                ),
        )
    }
}

@Composable
private fun VigilHeader(world: VigilWorld, task: VigilTask, index: Int, ghost: Int?) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Night)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Nv${task.level} · detecta ${task.goal} % · falsas ≤ ${task.maxFalse}",
                Modifier.weight(1f),
                color = LogInk,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            val s = world.shields
            Row(
                Modifier.onGloballyPositioned { world.shieldsAt = it }
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "Escudos: ${s / 2}${if (s % 2 == 1) " y medio" else ""} de 3"
                        liveRegion = LiveRegionMode.Polite
                    }
            ) {
                for (k in 0 until 3) {
                    val v = s - k * 2
                    Icon(
                        if (v >= 1) Icons.Rounded.Shield else Icons.Outlined.Shield,
                        null,
                        Modifier.size(22.dp).graphicsLayer { alpha = if (v == 1) .5f else 1f },
                        tint = if (v >= 1) Juice.Info else LogDim,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Guardia: ${task.guard}",
                color = Juice.Info,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                "Tramo ${index + 1}/${Vigil.TRAMOS}",
                color = LogDim,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            if (ghost != null)
                Text(
                    "récord aquí $ghost %",
                    color = Color(0xFFB8BEC9),
                    style = MaterialTheme.typography.labelMedium,
                )
        }
        // Tramo progress, drawn from the world clock (no recomposition per frame).
        Canvas(Modifier.fillMaxWidth().height(5.dp)) {
            drawRoundRect(Color.White.copy(alpha = .12f), cornerRadius = CornerRadius(size.height))
            val f = (world.clock.toFloat() / world.total).coerceIn(0f, 1f)
            drawRoundRect(
                if (f > .83f) Juice.NetAmber else Juice.Info,
                size = Size(size.width * f, size.height),
                cornerRadius = CornerRadius(size.height),
            )
        }
        world.tuning.note?.let {
            Text(it, color = Juice.NetAmber, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun VigilFooter(
    world: VigilWorld,
    feedback: Boolean,
    logs: List<VigilLog>,
    task: VigilTask,
    freeze: () -> Unit,
) {
    val motion = LocalMotionEnabled.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Detectadas ${world.hits}/${task.groups.size} · falsas ${world.falses} · lapsos ${world.lapses}",
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
                color = colors.onSurfaceVariant,
            )
            OutlinedButton(
                freeze,
                enabled = !world.ended && !world.frozen && world.freezesLeft > 0,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Rounded.AcUnit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Congelar (${world.freezesLeft})")
            }
        }
        AnimatedContent(
            world.message,
            transitionSpec = {
                if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                else
                    (fadeIn(tween(Juice.Quick)) +
                        slideInVertically(tween(Juice.Move)) { it / 3 }) togetherWith
                        fadeOut(tween(Juice.Quick))
            },
            label = "Aviso",
        ) { m ->
            if (m != null)
                Text(
                    m.second,
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant)
                        .padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                )
            else Spacer(Modifier.height(1.dp))
        }
        LaunchedEffect(world.message) {
            val m = world.message ?: return@LaunchedEffect
            delay(3_000)
            if (world.message == m) world.message = null
        }
        val log = world.log
        if (log != null) {
            // Relevo: short summary of the tramo and the curve so far (fatigue is visible).
            val curve = Vigil.curve(logs + log)
            Panel {
                Text(
                    if (log.passed) "Relevo: tramo superado"
                    else if (world.shift.over) "Relevo: sin escudos, tramo perdido"
                    else "Relevo: tramo por debajo del objetivo",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (log.passed) Juice.Good else colors.onSurface,
                )
                Text(
                    "Detectadas ${log.hits} de ${log.targets} (${log.detected} %) · lapsos ${log.lapses} · falsas ${log.falses}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TramoBars(curve, null, Modifier.fillMaxWidth().height(40.dp))
            }
        }
    }
}

/** Detection % per tramo as bars, with the record run as grey ticks. */
@Composable
private fun TramoBars(curve: List<Int>, ghost: List<Int>?, modifier: Modifier) {
    Canvas(
        modifier.semantics {
            contentDescription = "Detección por tramo: " + curve.joinToString(", ") { "$it %" }
        }
    ) {
        // One column per tramo of the partida (results saved before paso 3 have 12).
        val slots = maxOf(Vigil.TRAMOS, curve.size)
        val col = size.width / slots
        for (i in 0 until slots) {
            val x = col * i + col * .2f
            val bw = col * .6f
            drawRoundRect(
                Color.White.copy(alpha = .08f),
                Offset(x, 0f),
                Size(bw, size.height),
                CornerRadius(4.dp.toPx()),
            )
            curve.getOrNull(i)?.let { v ->
                val h = size.height * v / 100f
                drawRoundRect(
                    if (v >= 80) Juice.Good else if (v >= 50) Juice.NetAmber else Juice.Bad,
                    Offset(x, size.height - h),
                    Size(bw, h),
                    CornerRadius(4.dp.toPx()),
                )
            }
            ghost?.getOrNull(i)?.let { g ->
                val y = size.height * (1f - g / 100f)
                drawLine(
                    Color(0xFFB8BEC9),
                    Offset(x - 2.dp.toPx(), y),
                    Offset(x + bw + 2.dp.toPx(), y),
                    2.dp.toPx(),
                )
            }
        }
    }
}

/**
 * Result extras (§3 "Pantalla de resultado"): detections / lapses / false alarms, the curve per
 * tramo with the record ghost, the pattern missed most.
 */
@Composable
fun VigilResult(r: ResultRow, history: List<ResultRow>) {
    val logs = remember(r.id) { boardLogs(r).mapNotNull(VigilLog::decode) }
    if (logs.isEmpty()) return
    val ghost =
        remember(r.id) {
            history
                .filter { it.game == r.game && it.id != r.id }
                .maxByOrNull { it.correct }
                ?.let { b -> boardLogs(b).map { VigilLog.decode(it)?.detected ?: 0 } }
        }
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultFigure(
                "${logs.sumOf { it.hits }}/${logs.sumOf { it.targets }}",
                "detecciones",
                Modifier.weight(1f),
            )
            ResultFigure("${logs.sumOf { it.lapses }}", "lapsos", Modifier.weight(1f))
            ResultFigure("${logs.sumOf { it.falses }}", "falsas", Modifier.weight(1f))
        }
        Text(
            "Detección por tramo",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TramoBars(Vigil.curve(logs), ghost, Modifier.fillMaxWidth().height(64.dp))
        if (ghost != null)
            Text(
                "Rayas grises: tu récord en cada tramo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Vigil.weakest(logs)?.let {
            Text(
                "Patrón que más se te escapa: ${Vigil.patternNames[it]}. ${Vigil.lessons[it]}",
                style = MaterialTheme.typography.bodyMedium,
                color = Juice.NetAmber,
            )
        }
    }
}
