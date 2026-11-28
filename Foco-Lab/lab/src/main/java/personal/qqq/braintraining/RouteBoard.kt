package personal.qqq.braintraining

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

/* Fase 3 (docs/DISENO-JUEGOS-V2.md §4): Ruta de paquetes. One Canvas draws the metro map from precalculated positions; the
 * frame loop only runs while something moves (demonstration, replay, pulses, sparks) and never with animations off: then the
 * demonstration numbers the routers and the verdict appears at once. Rules: lab-core/Route.kt. */

private val Paper = Color(0xFF12161F)
private val RouterInk = Color(0xFF12161F)
private val Lines =
    arrayOf(Color(0xFFFFB300), Color(0xFF7CF5FF), Color(0xFFB69CFF), Color(0xFF7CF5FF))
private val Costly = Color(0xFFFF6B9A)
/** Snap radius for dragging over routers (§4: 80 px). */
private const val SNAP_PX = 80f

@Stable
private class RouteWorld(val task: RouteTask, val tuning: RouteTuning) {
    val n = task.n
    val hopMs = tuning.hopMs
    val demoMs = task.demo.size.coerceAtLeast(2).let { (it - 1) * hopMs } + 300
    var animNs = 0L
    val anim
        get() = animNs / 1_000_000L

    /** Router centres in px (set when the canvas is measured). */
    val px = FloatArray(n)
    val py = FloatArray(n)
    val pulseAt = LongArray(n) { -10_000L }
    var lastTrail = 0L
    var replayAt = -1L
    var wrongAt = -1L
    var pickAt = -10_000L
    val particles = Particles()
    var mapX = 0f
    var mapY = 0f
    var drawStart = 0L
    var motion = true
    /** Packet position written by [packetAt] (no allocation per frame). */
    var qx = 0f
    var qy = 0f
    var qn = -1
    // Compose state
    var phase by mutableIntStateOf(0) // 0 demo, 1 draw, 2 replay, 3 done
    val picks = mutableStateListOf(task.source)
    var dropped by mutableStateOf(false)
    var wake by mutableIntStateOf(0)
    var message by mutableStateOf<String?>(null)
    var check by mutableStateOf<RouteCheck?>(null)

    fun nearest(x: Float, y: Float, radius: Float): Int {
        var best = -1
        var d = radius * radius
        for (i in 0 until n) {
            val dx = px[i] - x
            val dy = py[i] - y
            val e = dx * dx + dy * dy
            if (e <= d) {
                d = e
                best = i
            }
        }
        return best
    }

    fun busy(): Boolean {
        if (
            phase == 0 ||
                phase == 2 ||
                particles.active ||
                anim - pickAt < Juice.Move ||
                anim - wrongAt < Juice.Hold
        )
            return true
        for (i in 0 until n) if (anim - pulseAt[i] < 300) return true
        return false
    }
}

/**
 * Ruta de paquetes. [previous] holds the logs of the rounds already played (announced tuning),
 * [index] this round, [ghost] whether the record run got this round right.
 */
@Composable
fun RouteBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    previous: List<String>,
    index: Int,
    ghost: Boolean?,
    done: (Boolean, String) -> Unit,
) {
    val base = c.route ?: return
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val colors = MaterialTheme.colorScheme
    val logs = remember(c) { previous.mapNotNull(RouteLog::decode) }
    val tuning = remember(c) { Route.tuning(logs) }
    val task = remember(c) { Route.tuned(base, tuning) }
    val world = remember(c) { RouteWorld(task, tuning) }
    world.motion = motion
    val scope = rememberCoroutineScope()
    val shake = rememberShake()
    val density = LocalDensity.current
    var frame by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)

    fun finish(check: RouteCheck) {
        val log =
            RouteLog(
                check.ok,
                task.kind,
                task.hops,
                check.matched,
                check.error,
                task.level,
                Route.learned(task, check.ok),
                (System.nanoTime() / 1_000_000L - world.drawStart).coerceAtLeast(0),
            )
        world.phase = 3
        if (check.ok) {
            Juice.hit(
                world.particles,
                world.mapX + world.px[task.answer.last()],
                world.mapY + world.py[task.answer.last()],
                motion,
            )
            cue(FeedbackCue.COMBO)
        } else {
            val bad = world.picks.getOrNull(check.matched + 1) ?: world.picks.last()
            Juice.shatter(
                world.particles,
                world.mapX + world.px[bad],
                world.mapY + world.py[bad],
                motion,
            )
            cue(FeedbackCue.RETRY)
            scope.launch { shake.play(Shake.SMALL, motion) }
        }
        world.wrongAt = world.anim
        world.wake++
        scope.launch {
            delay(Juice.Hold.toLong())
            done(check.ok, log.encode())
        }
    }
    fun submit() {
        if (!answering || world.phase != 1 || (world.picks.size < 2 && !world.dropped)) return
        val check = Route.check(task, world.picks.toList(), world.dropped)
        world.check = check
        cue(FeedbackCue.TAP)
        if (motion) {
            world.phase = 2
            world.replayAt = world.anim
            world.wake++
        } else finish(check)
    }
    fun pick(i: Int, drag: Boolean = false) {
        if (!answering || world.phase != 1 || i !in 0 until world.n) return
        val last = world.picks.last()
        if (i == last) {
            if (!drag && world.picks.size > 1) {
                world.picks.removeAt(world.picks.lastIndex)
                cue(FeedbackCue.TAP)
            }
            return
        }
        if (world.dropped) {
            world.message = "El paquete ya está descartado: deshaz ✖ para seguir la ruta."
            return
        }
        if (!task.linked(last, i)) {
            if (!drag)
                world.message =
                    "No hay enlace directo entre ${task.names[last]} y ${task.names[i]}" +
                        if (task.edge(last, i) == task.down && task.down >= 0) " (está caído)."
                        else "."
            return
        }
        world.picks += i
        world.pulseAt[i] = world.anim
        world.pickAt = world.anim
        world.message = null
        cue(FeedbackCue.TAP)
        world.wake++
    }
    fun undo() {
        if (!answering || world.phase != 1) return
        if (world.dropped) world.dropped = false
        else if (world.picks.size > 1) world.picks.removeAt(world.picks.lastIndex)
    }

    // Demonstration with animations off: numbered routers for the same time, then the map is yours.
    LaunchedEffect(world, answering, motion) {
        if (!answering || world.phase != 0) return@LaunchedEffect
        if (!motion) {
            delay(world.demoMs + Juice.Reveal)
            world.phase = 1
            world.drawStart = System.nanoTime() / 1_000_000L
        }
    }
    // The single frame loop, only while something moves.
    LaunchedEffect(world, answering, motion, resumed, world.wake) {
        if (!answering && world.phase != 3 || !resumed || !motion) return@LaunchedEffect
        var last = 0L
        while (isActive && world.busy()) withFrameNanos { now ->
            val dt = if (last == 0L) 0L else (now - last).coerceAtMost(50_000_000L)
            last = now
            world.animNs += dt
            val t = world.anim
            if (world.phase == 0) {
                packetAt(world, task.demo, t, world.hopMs)
                val x = world.qx
                val y = world.qy
                val node = world.qn
                if (node >= 0 && t - world.pulseAt[node] > world.hopMs / 2) world.pulseAt[node] = t
                if (t - world.lastTrail >= 16) {
                    world.lastTrail = t
                    Juice.trail(world.particles, world.mapX + x, world.mapY + y, Juice.Info, true)
                }
                if (t >= world.demoMs) {
                    world.phase = 1
                    world.drawStart = System.nanoTime() / 1_000_000L
                    cue(FeedbackCue.WHOOSH)
                }
            } else if (world.phase == 2) {
                val local = t - world.replayAt
                val path = world.picks
                packetAt(world, path, local, world.hopMs * 6 / 10)
                val x = world.qx
                val y = world.qy
                val node = world.qn
                if (node >= 0 && local - world.pulseAt[node] > 200) world.pulseAt[node] = t
                if (t - world.lastTrail >= 16) {
                    world.lastTrail = t
                    Juice.trail(
                        world.particles,
                        world.mapX + x,
                        world.mapY + y,
                        Juice.NetAmber,
                        true,
                    )
                }
                if (local >= (path.size - 1) * world.hopMs * 6 / 10 + 120) finish(world.check!!)
            }
            world.particles.step(dt / 1e9f)
            frame = now
            FrameProbe.frames++
        }
    }

    val measurer = rememberTextMeasurer()
    val labels =
        remember(task, density) {
            Array(task.n) {
                measurer.measure(
                    task.names[it],
                    TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Black, color = RouterInk),
                )
            }
        }
    val numbers =
        remember(density) {
            Array(16) {
                measurer.measure(
                    "${it + 1}",
                    TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Paper),
                )
            }
        }
    val two =
        remember(density) {
            measurer.measure(
                "2",
                TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White),
            )
        }
    val cross =
        remember(density) {
            measurer.measure(
                "✖",
                TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Black, color = Juice.Bad),
            )
        }

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header: challenge pill and TTL.
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Paper)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Nv${task.level} · ${task.n} routers · ${task.hops} saltos · ${logs.count { it.ok }}/${index}",
                        Modifier.weight(1f),
                        color = Color(0xFFE9EEF7),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (ghost != null)
                        Text(
                            if (ghost) "récord: ✓" else "récord: ✗",
                            color = Color(0xFFB8BEC9),
                            style = MaterialTheme.typography.labelMedium,
                        )
                }
                if (task.ttl > 0)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier =
                            Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "TTL ${task.ttl}"
                            },
                    ) {
                        Text(
                            "TTL ${task.ttl}",
                            color = Juice.NetAmber,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                        val used = (world.picks.size - 1).coerceAtLeast(0)
                        for (k in 0 until task.ttl) Box(
                            Modifier.size(width = 10.dp, height = 16.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (k < task.ttl - used) Juice.NetAmber
                                    else Color.White.copy(alpha = .15f)
                                )
                        )
                    }
                tuning.note?.let {
                    Text(it, color = Juice.NetAmber, style = MaterialTheme.typography.labelLarge)
                }
            }
            Canvas(
                Modifier.fillMaxWidth()
                    .aspectRatio(.78f)
                    .shake(shake, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Paper)
                    .onGloballyPositioned {
                        val p = it.positionInParent()
                        world.mapX = p.x
                        world.mapY = p.y
                        val w = it.size.width
                        val h = it.size.height
                        for (i in 0 until task.n) {
                            world.px[i] = task.xs[i] * w
                            world.py[i] = task.ys[i] * h
                        }
                    }
                    .pointerInput(world, answering) {
                        detectTapGestures { o ->
                            world.nearest(o.x, o.y, SNAP_PX * 1.2f).let { if (it >= 0) pick(it) }
                        }
                    }
                    .pointerInput(world, answering) {
                        detectDragGestures { ch, _ ->
                            world.nearest(ch.position.x, ch.position.y, SNAP_PX).let {
                                if (it >= 0) pick(it, drag = true)
                            }
                        }
                    }
                    .semantics {
                        contentDescription =
                            "Mapa de red con ${task.n} routers. " +
                                when (world.phase) {
                                    0 -> "Observa la ruta del paquete."
                                    1 ->
                                        "Ruta: " +
                                            world.picks.joinToString(" → ") { task.names[it] } +
                                            (if (world.dropped) " ✖ descartado" else "")
                                    else -> "Ruta enviada."
                                }
                        if (world.phase == 1)
                            customActions =
                                (0 until task.n).map { i ->
                                    CustomAccessibilityAction("Router ${task.names[i]}") {
                                        pick(i)
                                        true
                                    }
                                }
                    }
            ) {
                frame
                drawRouteMap(world, task, labels, numbers, two, cross, feedback || world.phase == 3)
            }
            AnimatedContent(
                world.phase >= 1,
                transitionSpec = {
                    if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                    else fadeIn(tween(Juice.Quick)) togetherWith fadeOut(tween(Juice.Quick))
                },
                label = "Fase",
            ) { drawing ->
                if (!drawing)
                    Text(
                        "Observa: el paquete va de A a B" +
                            if (task.kind == Route.DOWN) " (la línea roja está caída)" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                    )
                else
                    Text(
                        Route.prompt(task),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
            }
            world.message?.let {
                Text(
                    it,
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant)
                        .padding(12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (world.phase <= 1)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        ::undo,
                        enabled =
                            answering &&
                                world.phase == 1 &&
                                (world.picks.size > 1 || world.dropped),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Undo, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Deshacer")
                    }
                    if (task.ttl > 0)
                        OutlinedButton(
                            {
                                if (answering && world.phase == 1 && world.picks.size > 1) {
                                    world.dropped = !world.dropped
                                    cue(FeedbackCue.TAP)
                                }
                            },
                            enabled = answering && world.phase == 1 && world.picks.size > 1,
                            shape = RoundedCornerShape(14.dp),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor =
                                        if (world.dropped) Juice.Bad.copy(alpha = .2f)
                                        else Color.Transparent
                                ),
                        ) {
                            Text("✖ descartado")
                        }
                    Spacer(Modifier.weight(1f))
                    Button(
                        ::submit,
                        enabled =
                            answering &&
                                world.phase == 1 &&
                                (world.picks.size > 1 || world.dropped),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar")
                    }
                }
            world.check
                ?.takeIf { world.phase == 3 }
                ?.let { ch ->
                    Panel {
                        Text(
                            if (ch.ok) "¡Llegó! Ruta exacta"
                            else "Fallo de ${Route.errorNames[ch.error]}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (ch.ok) Juice.Good else Juice.Bad,
                        )
                        Text(
                            if (ch.ok) Route.lessons[Route.learned(task, true).maxOrNull() ?: 0]
                            else
                                "Ruta correcta (numerada en el mapa): " +
                                    task.answer.joinToString(" → ") { task.names[it] } +
                                    (if (task.dropped) " ✖" else "") +
                                    ". " +
                                    when (ch.error) {
                                        Route.ORDER -> "Tenías los routers, no el orden."
                                        Route.TTL_ERROR -> Route.lessons[6]
                                        else ->
                                            if (task.kind == Route.ALT)
                                                Route.lessons[if (task.hasCosts) 5 else 4]
                                            else "Te desviaste en el salto ${ch.matched + 1}."
                                    },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
        }
        Canvas(Modifier.matchParentSize()) {
            frame
            with(world.particles) { draw() }
        }
    }
}

/**
 * Position of a packet that follows [path] at [hopMs] per hop after [t] ms: linear along the line,
 * braking (EaseOut, 80 ms) at each router.
 */
private fun packetAt(w: RouteWorld, path: List<Int>, t: Long, hopMs: Long) {
    if (path.size < 2) {
        w.qx = w.px[path[0]]
        w.qy = w.py[path[0]]
        w.qn = path[0]
        return
    }
    val hop = (t / hopMs).toInt().coerceIn(0, path.size - 2)
    val local = (t - hop * hopMs).coerceIn(0L, hopMs)
    val brake = 80L
    val run = hopMs - brake
    // Linear until 80 ms before the router, then EaseOut into it, then a short stop.
    val f =
        if (local < run - brake) local.toFloat() / run
        else
            ((run - brake) +
                Juice.EaseOut.transform(
                    ((local - (run - brake)).toFloat() / brake).coerceIn(0f, 1f)
                ) * brake) / run
    val a = path[hop]
    val b = path[hop + 1]
    val k = f.coerceIn(0f, 1f)
    w.qx = w.px[a] + (w.px[b] - w.px[a]) * k
    w.qy = w.py[a] + (w.py[b] - w.py[a]) * k
    w.qn = if (k >= 1f) b else if (local < 40) a else -1
}

private val Dash = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))
private val Dots = PathEffect.dashPathEffect(floatArrayOf(4f, 12f))

private fun DrawScope.drawRouteMap(
    w: RouteWorld,
    t: RouteTask,
    labels: Array<TextLayoutResult>,
    numbers: Array<TextLayoutResult>,
    two: TextLayoutResult,
    cross: TextLayoutResult,
    reveal: Boolean,
) {
    val line = 7.dp.toPx()
    val r = 15.dp.toPx()
    // Links: coloured metro lines; cost-2 links pink with their cost; the down link red and dashed.
    for (e in 0 until t.edgeCount) {
        val a = t.edges[2 * e]
        val b = t.edges[2 * e + 1]
        val down =
            e == t.down &&
                (w.phase == 0 && t.kind == Route.DOWN || w.phase >= 1 && t.kind == Route.ALT)
        val color = if (down) Juice.Bad else if (t.costs[e] > 1) Costly else Lines[e % Lines.size]
        drawLine(
            color.copy(alpha = if (down) .9f else .75f),
            Offset(w.px[a], w.py[a]),
            Offset(w.px[b], w.py[b]),
            if (down) line * .6f else line,
            StrokeCap.Round,
            if (down) Dash else null,
        )
        if (t.costs[e] > 1) {
            val mx = (w.px[a] + w.px[b]) / 2
            val my = (w.py[a] + w.py[b]) / 2
            drawCircle(Costly, 10.dp.toPx(), Offset(mx, my))
            drawText(two, topLeft = Offset(mx - two.size.width / 2, my - two.size.height / 2))
        }
    }
    // Your route, drawn progressively (the last segment grows).
    if (w.phase >= 1) {
        for (k in 0 until w.picks.size - 1) {
            val a = w.picks[k]
            val b = w.picks[k + 1]
            val grow =
                if (k == w.picks.size - 2)
                    ((w.anim - w.pickAt) / Juice.Move.toFloat()).coerceIn(0f, 1f).let {
                        if (!w.motion) 1f else it
                    }
                else 1f
            drawLine(
                Color.White,
                Offset(w.px[a], w.py[a]),
                Offset(w.px[a] + (w.px[b] - w.px[a]) * grow, w.py[a] + (w.py[b] - w.py[a]) * grow),
                line * .55f,
                StrokeCap.Round,
            )
        }
    }
    // After the verdict: the right route in white dots with numbers (§4 "Pistas").
    if (reveal && w.check?.ok == false)
        for (k in 0 until t.answer.size - 1) {
            val a = t.answer[k]
            val b = t.answer[k + 1]
            drawLine(
                Color.White.copy(alpha = .9f),
                Offset(w.px[a], w.py[a]),
                Offset(w.px[b], w.py[b]),
                line * .45f,
                StrokeCap.Round,
                Dots,
            )
        }
    // Routers: white circles with a dark ring, pulse ring when the packet passes.
    for (i in 0 until t.n) {
        val p = ((w.anim - w.pulseAt[i]) / 300f)
        if (p in 0f..1f)
            drawCircle(
                Juice.Info.copy(alpha = 1f - p),
                r * (1f + .6f * p),
                Offset(w.px[i], w.py[i]),
                style = Stroke(3.dp.toPx()),
            )
        val chosen = w.phase >= 1 && i in w.picks
        drawCircle(
            if (i == t.source || i == t.target) Juice.NetAmber else Color.White,
            r,
            Offset(w.px[i], w.py[i]),
        )
        drawCircle(RouterInk, r, Offset(w.px[i], w.py[i]), style = Stroke(3.dp.toPx()))
        if (chosen)
            drawCircle(
                Juice.Info,
                r + 4.dp.toPx(),
                Offset(w.px[i], w.py[i]),
                style = Stroke(3.dp.toPx()),
            )
        val l = labels[i]
        drawText(l, topLeft = Offset(w.px[i] - l.size.width / 2, w.py[i] - l.size.height / 2))
    }
    // Step numbers: always in the demonstration with animations off (and after a mistake), next to
    // each router.
    val numbered = (w.phase == 0 && !w.motion) || (reveal && w.check?.ok == false)
    val path = if (w.phase == 0) t.demo else t.answer
    if (numbered)
        for (k in 1 until path.size) {
            val i = path[k]
            var again = 0
            for (j in 1 until k) if (path[j] == i) again++
            val cx = w.px[i] + r * .9f + again * 18.dp.toPx()
            val cy = w.py[i] - r * .9f
            drawCircle(Juice.Info, 10.dp.toPx(), Offset(cx, cy))
            val num = numbers[(k - 1).coerceAtMost(numbers.size - 1)]
            drawText(num, topLeft = Offset(cx - num.size.width / 2, cy - num.size.height / 2))
        }
    // The packet during the demonstration and the replay.
    if (w.phase == 0 && w.motion || w.phase == 2) {
        if (w.phase == 0) packetAt(w, t.demo, w.anim, w.hopMs)
        else packetAt(w, w.picks, w.anim - w.replayAt, w.hopMs * 6 / 10)
        val x = w.qx
        val y = w.qy
        drawCircle(Juice.Info.copy(alpha = .35f), 14.dp.toPx(), Offset(x, y))
        drawCircle(Color.White, 7.dp.toPx(), Offset(x, y))
    }
    if (w.dropped && w.picks.size > 1) {
        val i = w.picks.last()
        drawText(cross, topLeft = Offset(w.px[i] - cross.size.width / 2, w.py[i] + r + 2.dp.toPx()))
    }
}

/**
 * Result extras (§4): exact routes, longest route remembered and the kind of mistake (order,
 * detour, TTL).
 */
@Composable
fun RouteResult(r: ResultRow, history: List<ResultRow>) {
    val logs = remember(r.id) { boardLogs(r).mapNotNull(RouteLog::decode) }
    if (logs.isEmpty()) return
    val ghost =
        remember(r.id) {
            history
                .filter { it.game == r.game && it.id != r.id }
                .maxByOrNull { it.correct }
                ?.let { b -> boardLogs(b).map { RouteLog.decode(it)?.ok == true } }
        }
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultFigure(
                "${logs.count { it.ok }}/${logs.size}",
                "rutas exactas",
                Modifier.weight(1f),
            )
            ResultFigure(
                "${logs.filter { it.ok }.maxOfOrNull { it.hops } ?: 0}",
                "saltos máx.",
                Modifier.weight(1f),
            )
            ResultFigure("${Route.bestRun(logs)}", "seguidas", Modifier.weight(1f))
        }
        Canvas(
            Modifier.fillMaxWidth().height(28.dp).semantics {
                contentDescription =
                    "Rutas por ronda: " +
                        logs.joinToString(", ") {
                            if (it.ok) "bien" else Route.errorNames[it.error]
                        }
            }
        ) {
            val col = size.width / 12f
            for (i in 0 until 12) {
                val x = col * i + col / 2
                val l = logs.getOrNull(i)
                drawCircle(
                    if (l == null) Color.White.copy(alpha = .1f)
                    else if (l.ok) Juice.Good else Juice.Bad,
                    col * .28f,
                    Offset(x, size.height / 2),
                )
                if (ghost?.getOrNull(i) == true)
                    drawCircle(
                        Color(0xFFB8BEC9),
                        col * .38f,
                        Offset(x, size.height / 2),
                        style = Stroke(1.5.dp.toPx()),
                    )
            }
        }
        val orders = logs.count { it.error == Route.ORDER }
        val detours = logs.count { it.error == Route.DETOUR }
        val ttl = logs.count { it.error == Route.TTL_ERROR }
        if (orders + detours + ttl > 0)
            Text(
                "Fallos: orden $orders · desvío $detours · TTL $ttl",
                style = MaterialTheme.typography.bodyMedium,
                color = Juice.NetAmber,
            )
        Route.weakest(logs)?.let {
            Text(
                when (it) {
                    Route.ORDER ->
                        "Recuerdas los routers pero no el orden: repítelo en voz baja salto a salto."
                    Route.TTL_ERROR -> Route.lessons[6]
                    else -> "Te desvías: fíjate en el router donde gira la ruta."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
