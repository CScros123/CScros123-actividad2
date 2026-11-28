package personal.qqq.braintraining

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

/* Fase 3 (docs/DISENO-JUEGOS-V2.md §5): Turno anti-DDoS. A belt of packet cards rises to the server to the beat. The beat comes
 * from the frame clock (DdosBeat, integer nanoseconds), never from delay(); one frame loop per screen, stopped when not resumed,
 * when the wave ends or with animations off (then the belt steps one packet at a time). A fixed pool of 12 card slots: packet i
 * always uses slot i % 12, so nothing is allocated while the belt moves. Rules and scoring: lab-core/Ddos.kt (DdosShift).
 *
 * Paso 2 (docs/AUDITORIA-JUEGOS-V3.md §4.2): a partida is 5 chained waves (Ddos.plan); the board is rebuilt for each one and
 * inherits the combo streak and the points from the logs of the waves before. The boss seals the new rule in wave 3, wave 5
 * is the peak. Every beat clicks (FeedbackCue.PULSE, accent when a packet enters). A mistake no longer stops the belt (only at
 * level 1): the card is marked and the lesson goes to the footer. */

private val ShiftBg = Color(0xFF140F1F)
private val Belt = Color(0xFF1F1830)
private val Magenta = Color(0xFFFF6B9A)
private val CardInk = Color(0xFFEDE6F7)
private val CardDim = Color(0xFF9C90B5)
private val BeltH = 340.dp
private val CardH = 62.dp
private const val SLOTS = 12
/**
 * Boss briefing before a wave that brings rules (§5 "parte del jefe de 2 s"); the other waves get a
 * short banner.
 */
internal const val DDOS_BRIEF_MS = Ddos.BRIEF_MS
/**
 * Level 1 still stops the belt for a moment on a mistake, so the lesson can be read; from level 2
 * the belt never stops.
 */
private const val FREEZE_MS = 1_000L
/** How long the rule that caught a mistake stays lit in the header. */
private const val FOCUS_MS = 1_600L
/** A fallen server gets a longer look before the next wave. */
private const val DOWN_MS = 1_800L

@Stable
private class DdosWorld(val task: DdosTask, val tuning: DdosTuning, previous: List<DdosLog>) {
    /** The combo streak crosses waves: this one starts where the last one ended. */
    val shift = DdosShift(task, previous.lastOrNull()?.streak ?: 0)
    /** Points of the waves already played in this partida. */
    val base = previous.sumOf { it.score }
    val n = task.packets.size
    val bpm = task.bpm + tuning.bpmDelta
    val interval = Ddos.intervalMs(bpm)
    val travel = Ddos.TRAVEL * interval
    val total = Ddos.waveMs(bpm, n)
    val beat = DdosBeat(bpm)
    val briefMs = Ddos.briefMs(task.wave)
    val freezeMs = if (task.level == 1) FREEZE_MS else 0L
    var focusUntil = 0L
    /** Belt clock (ms, from [beat] with motion) and animation clock (ns, always runs). */
    var clock = 0L
    var animNs = 0L
    val anim
        get() = animNs / 1_000_000L

    var spawned = 0
    var frozenUntil = 0L
    var downAt = -1L
    var ended = false
    val slot = IntArray(SLOTS) { -1 }
    val blockedAt = LongArray(n) { -1L }
    val blockedP = FloatArray(n)
    val wrongAt = LongArray(n) { -1L }
    val passAt = LongArray(n) { -1L }
    val fromIp = IntArray(n)
    val particles = Particles()
    var beltX = 0f
    var beltY = 0f
    var beltW = 0f
    // Compose state: only what changes on events.
    var version by mutableIntStateOf(0)
    var tier by mutableIntStateOf(shift.tier)
    var focus by mutableIntStateOf(-1)
    var points by mutableIntStateOf(base)
    var briefing by mutableStateOf(true)
    var deployed by mutableStateOf(false)
    var loadPct by mutableIntStateOf(0)
    var correct by mutableIntStateOf(0)
    var legit by mutableIntStateOf(0)
    var bad by mutableIntStateOf(0)
    var message by mutableStateOf<Pair<Int, String>?>(null)
    var log by mutableStateOf<DdosLog?>(null)

    init {
        val seen = HashMap<String, Int>()
        task.packets.forEachIndexed { i, p ->
            val k = (seen[p.ip] ?: 0) + 1
            seen[p.ip] = k
            fromIp[i] = k
        }
    }

    fun progress(i: Int) = ((clock - Ddos.arrivalMs(i, bpm)).toFloat() / travel).coerceIn(0f, 1f)

    fun onBelt(i: Int) = i < spawned && (shift.state[i] == 0 || shift.state[i] == 4)

    fun say(text: String) {
        message = (message?.first ?: 0) + 1 to text
    }

    /** Rule sealed by the boss when this wave starts (Ddos.NONE in the other waves). */
    val newRule = task.sealed
}

/** Name of a rule as the boss writes it. */
private fun ruleName(r: Int) = Ddos.ruleNames.getOrElse(r) { "" }

/**
 * Turno anti-DDoS. [previous] holds the logs of the waves already played (announced tempo,
 * inherited combo and points), [index] this unit of the partida (the wave itself comes in the task)
 * and [ghost] the log of the same wave in the record run (its peak load is drawn as a faint line on
 * the load bar).
 */
@Composable
fun DdosBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    previous: List<String>,
    index: Int,
    ghost: String?,
    done: (Boolean, String) -> Unit,
) {
    val task = c.ddos ?: return
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val logs = remember(c) { previous.mapNotNull(DdosLog::decode) }
    val world = remember(c) { DdosWorld(task, Ddos.tuning(logs), logs) }
    val ghostLog = remember(ghost) { DdosLog.decode(ghost) }
    val scope = rememberCoroutineScope()
    val shake = rememberShake()
    val density = LocalDensity.current
    var frame by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val beltPx = with(density) { BeltH.toPx() }
    val cardPx = with(density) { CardH.toPx() }
    fun yOf(p: Float) = (beltPx - cardPx) * (1f - p)

    fun end() {
        if (world.ended) return
        world.ended = true
        val log = world.shift.log(world.bpm)
        world.log = log
        // Confetti across the real width of the belt (it used to come out of a 1 px strip on the
        // left edge).
        if (log.perfect) {
            Juice.confetti(
                world.particles,
                world.beltX,
                world.beltW,
                world.beltY + beltPx * .3f,
                motion,
            )
            cue(FeedbackCue.COMPLETE)
        }
        scope.launch {
            delay(if (log.down) DOWN_MS else Ddos.AFTER_MS)
            done(log.passed, log.encode())
        }
    }
    fun error(i: Int, rule: Int) {
        world.frozenUntil = world.anim + world.freezeMs
        world.focusUntil = world.anim + world.freezeMs + FOCUS_MS
        world.focus = rule
        world.say(
            if (rule in Ddos.lessons.indices) Ddos.lessons[rule]
            else "Ese paquete era legítimo: ninguna regla lo bloquea."
        )
    }
    fun sync() {
        world.tier = world.shift.tier
        world.points = world.base + world.shift.score
        world.correct = world.shift.correct
        world.legit = world.shift.blockedLegit
        world.bad = world.shift.passedBad
        world.loadPct = world.shift.load.toInt()
    }
    fun blocked(i: Int) {
        world.blockedAt[i] = world.anim
        world.blockedP[i] = world.progress(i)
        val cw = if (task.lanes == 2) world.beltW / 2 else world.beltW
        Juice.burst(
            world.particles,
            Juice.Burst.HIT,
            world.beltX + world.laneX(i, world.beltW) + cw / 2,
            world.beltY + yOf(world.blockedP[i]) + cardPx / 2,
            Color(0xFFFF8A3D),
            motion,
        )
        cue(if (world.shift.tier > 1) FeedbackCue.COMBO else FeedbackCue.CORRECT)
    }
    /** Exits, arrivals and the deployment after the belt moved. */
    fun process() {
        while (world.spawned < world.n && world.clock >= Ddos.arrivalMs(world.spawned, world.bpm)) {
            val i = world.spawned++
            world.slot[i % SLOTS] = i
            if (i == task.switchAt) {
                world.deployed = true
                world.say("Despliegue: 8080 abierto. A partir de ahora sus paquetes son legítimos.")
                cue(FeedbackCue.WHOOSH)
            }
            if (world.shift.arrive(i) == DdosShift.Result.OK) {
                blocked(i)
                sync()
            }
            world.version++
        }
        for (i in 0 until world.spawned) {
            if (!world.onBelt(i) || world.progress(i) < 1f) continue
            when (world.shift.deliver(i)) {
                DdosShift.Result.OK -> {
                    if (world.shift.tier > world.tier) cue(FeedbackCue.COMBO)
                }
                DdosShift.Result.BAD_PASSED -> {
                    world.passAt[i] = world.anim
                    cue(FeedbackCue.RETRY)
                    error(i, world.shift.truth[i].rule)
                    if (world.shift.down) {
                        world.downAt = world.anim
                        cue(FeedbackCue.ALARM)
                        scope.launch { shake.play(Shake.BIG, motion) }
                        world.say(
                            "¡Servidor caído! Demasiados paquetes maliciosos pasaron. ${Ddos.lessons[Ddos.LOAD]}"
                        )
                    } else scope.launch { shake.play(Shake.MEDIUM, motion) }
                }
                else -> Unit
            }
            sync()
            world.version++
        }
        if (world.shift.down || world.clock >= world.total) end()
    }
    fun tap(i: Int, ban: Boolean = false) {
        if (
            !answering ||
                world.ended ||
                world.briefing ||
                !world.onBelt(i) ||
                world.shift.state[i] != 0
        )
            return
        when (if (ban) world.shift.ban(i) else world.shift.block(i)) {
            DdosShift.Result.OK -> {
                blocked(i)
                if (ban)
                    world.say(
                        "${task.packets[i].ip} a la lista negra: sus siguientes paquetes se bloquean solos."
                    )
            }
            DdosShift.Result.LEGIT_BLOCKED -> {
                world.wrongAt[i] = world.anim
                cue(FeedbackCue.RETRY)
                error(
                    i,
                    world.shift.truth[i].rule.takeIf { it == Ddos.MONITOR || it == Ddos.DEPLOY }
                        ?: -1,
                )
                if (world.shift.truth[i].rule == Ddos.NONE)
                    world.say(
                        "Legítimo bloqueado: ninguna regla lo atrapa. Bloquear clientes reales también cuesta (negocio perdido)."
                    )
            }
            else -> Unit
        }
        sync()
        world.version++
    }

    // The single loop of this screen; the belt clock is the beat clock.
    LaunchedEffect(world, answering, motion, resumed) {
        if (!answering || !resumed) return@LaunchedEffect
        if (motion) {
            var last = 0L
            while (
                isActive &&
                    (!world.ended ||
                        world.particles.active ||
                        world.anim - (world.downAt.takeIf { it >= 0 } ?: -10_000L) < 600)
            ) withFrameNanos { now ->
                val dt = if (last == 0L) 0L else (now - last).coerceAtMost(50_000_000L)
                last = now
                world.animNs += dt
                if (world.briefing && world.anim >= world.briefMs) world.briefing = false
                if (!world.ended && !world.briefing && world.anim >= world.frozenUntil) {
                    // The pulse: one click per beat, straight from the beat clock (no timer of its
                    // own, so it cannot drift from the belt).
                    val beats = world.beat.advance(dt)
                    val before = world.clock
                    world.clock = world.beat.clockMs
                    if (beats > 0)
                        cue(
                            if (world.beat.beat % Ddos.BEATS_PER_PACKET == 0L)
                                FeedbackCue.PULSE_ACCENT
                            else FeedbackCue.PULSE
                        )
                    world.shift.decay(world.clock - before)
                    process()
                }
                if (world.focus >= 0 && world.anim >= world.focusUntil) world.focus = -1
                world.particles.step(dt / 1e9f)
                frame = now
                FrameProbe.frames++
            }
        } else {
            if (world.briefing) {
                delay(world.briefMs - world.anim)
                world.animNs = world.briefMs * 1_000_000L
                world.briefing = false
            }
            while (isActive && !world.ended) {
                var next =
                    if (world.spawned < world.n) Ddos.arrivalMs(world.spawned, world.bpm)
                    else world.total
                for (i in 0 until world.spawned) if (world.onBelt(i)) {
                    next = minOf(next, Ddos.arrivalMs(i, world.bpm) + world.travel)
                    break
                }
                val wait = (minOf(next, world.total) - world.clock).coerceAtLeast(16L)
                delay(wait)
                world.animNs += wait * 1_000_000L
                world.clock += wait
                world.shift.decay(wait)
                val spawned = world.spawned
                process()
                frame++
                // Without animations the belt steps packet by packet: the pulse is the accent of
                // each packet that enters.
                if (world.spawned > spawned && !world.ended) cue(FeedbackCue.PULSE_ACCENT)
                if (world.frozenUntil > world.anim) {
                    val f = world.frozenUntil - world.anim
                    delay(f)
                    world.animNs += f * 1_000_000L
                }
                if (world.focus >= 0 && world.anim >= world.focusUntil) world.focus = -1
            }
        }
    }

    // The seal and the peak are announced once, when their wave starts.
    LaunchedEffect(world) {
        if (!answering) return@LaunchedEffect
        if (task.peak) cue(FeedbackCue.WHOOSH)
        // The lesson of the sealed rule stays in the footer while the first packets rise, once the
        // boss's part is gone.
        if (task.sealed != Ddos.NONE) {
            cue(FeedbackCue.UNLOCK)
            delay(world.briefMs)
            world.say("Regla nueva sellada · ${Ddos.lessons[task.sealed]}")
        }
    }

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DdosHeader(world, task, ghostLog) { frame }
            Box(
                Modifier.fillMaxWidth()
                    .height(BeltH)
                    .shake(shake, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(Belt)
                    .onGloballyPositioned {
                        val p = it.positionInParent()
                        world.beltX = p.x
                        world.beltY = p.y
                        world.beltW = it.size.width.toFloat()
                    }
                    .semantics {
                        contentDescription =
                            "Cinta de paquetes. Toca un paquete para bloquearlo; deja pasar los legítimos."
                    }
            ) {
                Canvas(Modifier.matchParentSize()) {
                    frame
                    drawBelt(world, cardPx)
                }
                val v = world.version
                for (k in 0 until SLOTS) key(k) {
                    DdosCard(world, k, v, { frame }, beltPx, cardPx, answering, ::tap)
                }
                Canvas(Modifier.matchParentSize().clipToBounds()) {
                    frame
                    drawDebris(world, cardPx, beltPx)
                }
                BossBrief(world, task, motion)
            }
            DdosFooter(world, task, logs, feedback)
        }
        Canvas(Modifier.matchParentSize()) {
            frame
            with(world.particles) { draw() }
        }
    }
}

private fun DdosWorld.laneX(i: Int, width: Float) =
    if (task.lanes == 2) (if (task.packets[i].lane == 0) 0f else width / 2f) else 0f

@Composable
private fun DdosCard(
    w: DdosWorld,
    k: Int,
    version: Int,
    frame: () -> Long,
    beltPx: Float,
    cardPx: Float,
    answering: Boolean,
    tap: (Int, Boolean) -> Unit,
) {
    val i = w.slot[k]
    if (i < 0 || !w.onBelt(i)) return
    @Suppress("UNUSED_VARIABLE") val v = version
    val p = w.task.packets[i]
    val density = LocalDensity.current
    val lanes = w.task.lanes
    val rule = w.task.rules
    // Only the decisive fields go big (§5 risk): port and flags; IP and size small (14 sp).
    val desc =
        "Paquete ${p.ip} puerto ${p.port} ${p.flagText} ${p.sizeText} bytes" +
            (if (Ddos.RATE in rule) ", número ${w.fromIp[i]} de esta IP" else "") +
            (if (lanes == 2) if (p.lane == 0) ", carril web" else ", carril DNS" else "")
    val wrong = w.wrongAt[i] >= 0
    Box(
        Modifier.fillMaxWidth(if (lanes == 2) .5f else 1f)
            .height(CardH)
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .graphicsLayer {
                frame()
                translationX =
                    w.laneX(i, w.beltW) +
                        if (wrong)
                            (1f - Juice.springAt((w.anim - w.wrongAt[i]) / 1000f)) * 36.dp.toPx()
                        else 0f
                val prog = w.progress(i)
                translationY = (beltPx - cardPx) * (1f - prog)
                // Entry on the beat: EaseOut over the first packet interval.
                val e = Juice.EaseOut.transform((prog * Ddos.TRAVEL).coerceIn(0f, 1f))
                alpha = .35f + .65f * e
                scaleX = .92f + .08f * e
                scaleY = scaleX
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2B2240))
            .border(
                if (wrong) 2.dp else 1.dp,
                if (wrong) Juice.Bad else Color.White.copy(alpha = .12f),
                RoundedCornerShape(14.dp),
            )
            .pointerInput(i, answering) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = { if (dy < -40f) tap(i, true) },
                ) { _, d ->
                    dy += d
                }
            }
            .clickable(enabled = answering, onClickLabel = "Bloquear") { tap(i, false) }
            .semantics(mergeDescendants = true) {
                contentDescription = desc
                if (w.task.blacklist)
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Lista negra ${p.ip}") {
                                tap(i, true)
                                true
                            }
                        )
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.ip + if (Ddos.RATE in rule) "  ·${w.fromIp[i]}" else "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = CardDim,
                    maxLines = 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        ":${p.port}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (p.port == 23 || p.port == 8080) Juice.NetAmber else CardInk,
                    )
                    Text(
                        p.flagText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (p.flag == Ddos.SYN_HALF) Magenta else CardInk,
                        maxLines = 1,
                    )
                }
            }
            Text(
                p.sizeText,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = if (p.size >= 1000 && p.port == 53) Magenta else CardDim,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBelt(w: DdosWorld, cardPx: Float) {
    // Treads moving with the beat clock and the server line at the top.
    val step = 28.dp.toPx()
    val off = (w.clock % w.interval).toFloat() / w.interval * step * 2
    var y = size.height - off % step
    while (y > 0f) {
        drawLine(Color.White.copy(alpha = .04f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y -= step
    }
    if (w.task.lanes == 2)
        drawLine(
            Color.White.copy(alpha = .1f),
            Offset(size.width / 2, 0f),
            Offset(size.width / 2, size.height),
            2.dp.toPx(),
        )
    drawRect(Magenta.copy(alpha = .35f), Offset.Zero, Size(size.width, 3.dp.toPx()))
    // Combo ×4 (the top tier, reachable since the streak crosses waves): the edge of the belt
    // shines with a moving gradient.
    if (w.tier >= Ddos.MAX_TIER) {
        val t = (sin(w.anim / 260f) + 1f) / 2f
        drawRoundRect(
            lerp(Magenta, Juice.NetAmber, t),
            Offset.Zero,
            size,
            CornerRadius(20.dp.toPx()),
            style = Stroke(4.dp.toPx()),
        )
    }
    // Server down: desaturated for 600 ms.
    if (w.downAt >= 0) {
        val p = ((w.anim - w.downAt) / 600f).coerceIn(0f, 1f)
        drawRect(Color(0xFF808080).copy(alpha = .55f * (1f - p * .5f)))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDebris(
    w: DdosWorld,
    cardPx: Float,
    beltPx: Float,
) {
    val g = 1800f
    val half = (if (w.task.lanes == 2) size.width / 2 else size.width) / 2 - 10.dp.toPx()
    val h = cardPx - 6.dp.toPx()
    for (i in 0 until w.spawned) {
        val b = w.blockedAt[i]
        if (b >= 0) {
            val t = (w.anim - b) / 1000f
            if (t < .7f) {
                val y = (beltPx - cardPx) * (1f - w.blockedP[i]) + 3.dp.toPx() + g * t * t / 2
                val x0 = w.laneX(i, size.width) + 10.dp.toPx()
                val a = (1f - t / .7f)
                rotate(-18f * t * 2, Offset(x0 + half / 2, y + h / 2)) {
                    drawRoundRect(
                        Juice.Bad.copy(alpha = .8f * a),
                        Offset(x0 - 30f * t, y),
                        Size(half, h),
                        CornerRadius(12.dp.toPx()),
                    )
                }
                rotate(18f * t * 2, Offset(x0 + half * 1.5f, y + h / 2)) {
                    drawRoundRect(
                        Juice.NetAmber.copy(alpha = .8f * a),
                        Offset(x0 + half + 30f * t, y),
                        Size(half, h),
                        CornerRadius(12.dp.toPx()),
                    )
                }
            }
        }
        val f = w.passAt[i]
        if (f >= 0) {
            val t = ((w.anim - f) / Juice.Move.toFloat()).coerceIn(0f, 1f)
            if (t < 1f) {
                val x0 = w.laneX(i, size.width) + 10.dp.toPx()
                drawRoundRect(
                    Juice.Bad.copy(alpha = 1f - t),
                    Offset(x0, -t * cardPx),
                    Size(half * 2, h * (1f - t * .6f)),
                    CornerRadius(12.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.BossBrief(w: DdosWorld, task: DdosTask, motion: Boolean) {
    AnimatedVisibility(
        w.briefing,
        Modifier.align(Alignment.Center),
        enter =
            if (motion)
                fadeIn(tween(Juice.Quick)) +
                    scaleIn(tween(Juice.Reveal, easing = Juice.Overshoot), initialScale = 1.4f)
            else EnterTransition.None,
        exit = if (motion) fadeOut(tween(Juice.Quick)) else ExitTransition.None,
    ) {
        val ink = Color(0xFF2B1D10)
        Column(
            Modifier.padding(24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFFF4ECDC))
                .border(3.dp, Magenta, RoundedCornerShape(18.dp))
                .padding(18.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                // Wave 3: the boss seals the new rule of the level.
                w.newRule != Ddos.NONE -> {
                    Text(
                        "PARTE DEL JEFE DE TURNO",
                        color = Color(0xFF5A2440),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "Nueva regla: ${ruleName(w.newRule)}",
                        color = ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        Ddos.lessons[w.newRule],
                        color = ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "SELLADO ✓",
                        Modifier.graphicsLayer { rotationZ = -8f }
                            .border(2.dp, Juice.Bad, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = Juice.Bad,
                        fontWeight = FontWeight.Black,
                    )
                }
                // Wave 1: the rules in force today.
                task.wave == 0 -> {
                    Text(
                        "PARTE DEL JEFE DE TURNO",
                        color = Color(0xFF5A2440),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        if (task.rules.size == 1) "Regla de hoy: ${ruleName(task.rules.first())}"
                        else "Reglas de hoy",
                        color = ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (task.rules.size == 1)
                        Text(
                            Ddos.lessons[task.rules.first()],
                            color = ink,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    else
                        Text(
                            task.rules.sorted().joinToString(" · ") { ruleName(it) },
                            color = ink,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    Text(
                        "En la oleada ${Ddos.SEAL_WAVE + 1} llega una regla nueva.",
                        color = Color(0xFF5A2440),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                task.peak -> {
                    Text(
                        "OLEADA ${task.wave + 1}/${Ddos.WAVES} · PICO",
                        color = Juice.Bad,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
                    Text(
                        "+${Ddos.PEAK_BPM} BPM. Aguanta el combo.",
                        color = ink,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                else ->
                    Text(
                        "OLEADA ${task.wave + 1}/${Ddos.WAVES}",
                        color = Color(0xFF5A2440),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                    )
            }
        }
    }
}

@Composable
private fun DdosHeader(w: DdosWorld, task: DdosTask, ghost: DdosLog?, frame: () -> Long) {
    val motion = LocalMotionEnabled.current
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ShiftBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Nv${task.level} · ${w.bpm} BPM · ≥ ${task.goal} %",
                Modifier.weight(1f),
                color = CardInk,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            // Points of the whole partida, rolling up as they are earned.
            val shown by
                animateIntAsState(
                    w.points,
                    if (motion) tween(Juice.Quick) else snap(),
                    label = "Puntos",
                )
            Text(
                "$shown pts",
                Modifier.semantics { contentDescription = "${w.points} puntos" },
                color = CardInk,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            val pop by
                animateFloatAsState(
                    if (w.tier > 1) 1f + .06f * (w.tier - 1) else .9f,
                    Juice.bouncy(motion),
                    label = "Combo",
                )
            Text(
                "COMBO ×${w.tier}",
                Modifier.graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color =
                    if (w.tier >= Ddos.MAX_TIER) Juice.NetAmber
                    else if (w.tier > 1) Magenta else CardDim,
                fontWeight = FontWeight.Black,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = .9f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp), tint = ShiftBg)
                Spacer(Modifier.width(6.dp))
                Text(
                    "SERVIDOR",
                    color = ShiftBg,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Black,
                )
            }
            // Load bar from the live state (no recomposition per frame); the number for screen
            // readers and animations off.
            Canvas(Modifier.weight(1f).height(12.dp)) {
                frame()
                val l = (w.shift.load / 100f).coerceIn(0f, 1f)
                drawRoundRect(
                    Color.White.copy(alpha = .1f),
                    cornerRadius = CornerRadius(size.height),
                )
                var pulse = false
                for (i in 0 until w.n) if (w.passAt[i] >= 0 && w.anim - w.passAt[i] < 400)
                    pulse = true
                drawRoundRect(
                    if (pulse || l > .66f) Juice.Bad
                    else if (l > .33f) Juice.NetAmber else Juice.Good,
                    size = Size(size.width * l, size.height),
                    cornerRadius = CornerRadius(size.height),
                )
                ghost?.let { g ->
                    val x = size.width * g.maxLoad / 100f
                    drawLine(
                        Color(0xFFB8BEC9).copy(alpha = .7f),
                        Offset(x, -2f),
                        Offset(x, size.height + 2f),
                        2.dp.toPx(),
                    )
                }
            }
            Text(
                "carga ${w.loadPct} %",
                Modifier.semantics {
                    contentDescription = "Carga del servidor ${w.loadPct} por ciento"
                },
                color = CardInk,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "Oleada ${task.wave + 1}/${Ddos.WAVES}" + if (task.peak) " · PICO" else "",
                color = if (task.peak) Juice.NetAmber else CardDim,
                style = MaterialTheme.typography.labelLarge,
            )
            task.rules.sorted().forEach { r ->
                val text =
                    (if (r == Ddos.DEPLOY) (if (w.deployed) "8080 ✓" else "8080 ✕")
                    else Ddos.ruleShort[r]) + if (r == task.sealed) " · nueva" else ""
                val hot = w.focus == r
                Text(
                    text,
                    Modifier.clip(RoundedCornerShape(8.dp))
                        .background(if (hot) Magenta else Color.White.copy(alpha = .08f))
                        .border(
                            1.dp,
                            if (hot) Color.White
                            else if (r == task.sealed) Magenta else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    color = if (hot) ShiftBg else CardInk,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        w.tuning.note?.let {
            Text(it, color = Juice.NetAmber, style = MaterialTheme.typography.labelLarge)
        }
        if (ghost != null)
            Text(
                "récord en esta oleada: ${ghost.accuracy} % · carga máx. ${ghost.maxLoad} %",
                color = Color(0xFFB8BEC9),
                style = MaterialTheme.typography.labelMedium,
            )
    }
}

@Composable
private fun DdosFooter(w: DdosWorld, task: DdosTask, logs: List<DdosLog>, feedback: Boolean) {
    val motion = LocalMotionEnabled.current
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Bien ${w.correct} · legítimos bloqueados ${w.legit} · maliciosos que pasaron ${w.bad}",
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
        )
        @Suppress("UNUSED_VARIABLE") val v = w.version
        if (task.blacklist)
            Text(
                if (w.shift.blacklist.isEmpty())
                    "Lista negra vacía · desliza una tarjeta hacia arriba para añadir su IP"
                else "Lista negra: " + w.shift.blacklist.joinToString("  ") { "$it ✕" },
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelLarge,
                color = if (w.shift.blacklist.isEmpty()) colors.onSurfaceVariant else Juice.Bad,
            )
        AnimatedContent(
            w.message,
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
        LaunchedEffect(w.message) {
            val m = w.message ?: return@LaunchedEffect
            delay(3_500)
            if (w.message == m) w.message = null
        }
        val log = w.log
        if (log != null)
            Panel {
                Text(
                    if (log.down) "Fin de oleada: servidor caído"
                    else if (log.passed) "Oleada superada" else "Oleada por debajo del objetivo",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (log.passed) Juice.Good else colors.onSurface,
                )
                Text(
                    "Precisión ${log.accuracy} % (objetivo ${log.goal} %) · combo máx. ×${log.maxTier} · +${log.score} puntos",
                    style = MaterialTheme.typography.bodyMedium,
                )
                WaveBars(
                    (logs + log).map { it.accuracy },
                    null,
                    Modifier.fillMaxWidth().height(40.dp),
                )
            }
    }
}

@Composable
private fun WaveBars(values: List<Int>, ghost: List<Int>?, modifier: Modifier) {
    // One column per wave of the partida (results saved before paso 2 have 12).
    val slots = maxOf(Ddos.WAVES, values.size)
    Canvas(
        modifier.semantics {
            contentDescription = "Precisión por oleada: " + values.joinToString(", ") { "$it %" }
        }
    ) {
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
            values.getOrNull(i)?.let { v ->
                val h = size.height * v / 100f
                drawRoundRect(
                    if (v >= 85) Juice.Good else if (v >= 60) Juice.NetAmber else Juice.Bad,
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
 * Result extras (§5 "Pantalla de resultado"): points of the partida (what the record and "Otra vez"
 * compare), waves passed, best combo, legit blocked (business cost), malicious passed, the rule
 * failed most. The ghost is the record partida of the same format.
 */
@Composable
fun DdosResult(r: ResultRow, history: List<ResultRow>) {
    val logs = remember(r.id) { boardLogs(r).mapNotNull(DdosLog::decode) }
    if (logs.isEmpty()) return
    val run = remember(r.id) { Ddos.summary(logs) }
    val record =
        remember(r.id) {
            history
                .filter { it.game == r.game && it.id != r.id && boardLogs(it).size == logs.size }
                .maxByOrNull { headline(it).score }
        }
    val ghost =
        remember(r.id) {
            record?.let { b -> boardLogs(b).map { DdosLog.decode(it)?.accuracy ?: 0 } }
        }
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultFigure("${run.score}", "puntos", Modifier.weight(1f))
            ResultFigure("×${run.maxTier}", "combo máx.", Modifier.weight(1f))
            ResultFigure("${run.passed}/${run.waves}", "oleadas", Modifier.weight(1f))
        }
        record?.let {
            val best = headline(it).score
            Text(
                if (run.score > best) "¡Nuevo récord! Antes: $best puntos"
                else "Tu récord: $best puntos",
                style = MaterialTheme.typography.labelLarge,
                color =
                    if (run.score > best) Juice.Good else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultFigure(
                "${logs.sumOf { it.blockedLegit }}",
                "legítimos bloqueados",
                Modifier.weight(1f),
            )
            ResultFigure(
                "${logs.sumOf { it.passedBad }}",
                "maliciosos pasaron",
                Modifier.weight(1f),
            )
        }
        Text(
            "Precisión por oleada",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WaveBars(logs.map { it.accuracy }, ghost, Modifier.fillMaxWidth().height(64.dp))
        if (logs.any { it.down })
            Text(
                "Caídas del servidor: ${logs.count { it.down }}",
                style = MaterialTheme.typography.bodyMedium,
                color = Juice.Bad,
            )
        Ddos.weakest(logs)?.let {
            Text(
                "Regla que más fallas: ${Ddos.ruleNames[it]}. ${Ddos.lessons[it]}",
                style = MaterialTheme.typography.bodyMedium,
                color = Juice.NetAmber,
            )
        }
    }
}

/**
 * Paso 2 (formato de partida por juego): what a saved result is measured in. A Turno anti-DDoS
 * partida of 5 waves counts its packets ([value] of [total], what Adaptive uses) and compares
 * points ([score]); every other result counts aciertos.
 */
internal class Headline(
    val value: Int,
    val total: Int,
    val noun: String,
    val score: Int,
    val unit: String,
    val units: String,
)

internal fun headline(r: ResultRow): Headline {
    if (r.game == Ddos.ID)
        Ddos.run(boardLogs(r))?.let {
            return Headline(it.correct, it.total, "paquetes", it.score, "punto", "puntos")
        }
    return Headline(r.correct, r.total, "aciertos", r.correct, "acierto", "aciertos")
}

/** Record to beat for [r]: the best score among earlier results measured the same way. */
internal fun recordFor(r: ResultRow, all: List<ResultRow>): Int? {
    val h = headline(r)
    return all.filter { it.game == r.game && it.id != r.id }
        .map(::headline)
        .filter { it.noun == h.noun }
        .maxOfOrNull { it.score }
}
