package personal.qqq.braintraining

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

/* Fase 2 (docs/DISENO-JUEGOS-V2.md §2): La maleta del técnico. The board plays the whole chain (show → recall → grow) and
 * reports SuitcaseLog through [done]; the rules (tray window, recall order, scoring, tuning) live in lab-core/Suitcase.kt. */

private val Leather = Color(0xFF2A1E14)
private val LeatherHi = Color(0xFF3B2A1B)
private val Stitch = Color(0xFFFFB300).copy(alpha = .45f)

private enum class SuitStage {
    SHOW,
    RECALL,
    HELP,
    CLOSING,
    END,
}

/**
 * Live state of one chain. Compose state only for what the UI shows; geometry for the flights is
 * plain fields.
 */
@Stable
private class SuitPlay(val task: SuitcaseTask, val tuning: SuitTuning) {
    var stage by mutableStateOf(SuitStage.SHOW)
    /** Items already in the suitcase. */
    var length by mutableIntStateOf(0)
    var showing by mutableStateOf<SuitItem?>(null)
    var tray by mutableStateOf<SuitcaseTray?>(null)
    /** Bumped on every tray change (the tray itself is a plain object). */
    var version by mutableIntStateOf(0)
    var helped by mutableStateOf(false)
    var helpSlot by mutableIntStateOf(-1)
    var wrongSlot by mutableIntStateOf(-1)
    var best by mutableIntStateOf(0)
    var info by mutableStateOf<SuitItem?>(null)
    var miss = ""
    var tapped = ""
    val seen = mutableListOf<String>()
    val step
        get() = tray?.step ?: 0

    fun log() =
        SuitcaseLog(
            best,
            helped,
            task.target,
            miss,
            tapped,
            seen.filter { Suitcase.item(it) != null },
        )

    // Flights (overlay): start/end in board coordinates, label measured once at launch.
    var root: LayoutCoordinates? = null
    var suitcase: LayoutCoordinates? = null
    var card: LayoutCoordinates? = null
    val slots = arrayOfNulls<LayoutCoordinates>(Suitcase.MAX_TRAY)
    val fly = Animatable(1f)
    var flyFrom = Offset.Zero
    var flyTo = Offset.Zero
    var flyText: TextLayoutResult? = null
    val particles = Particles()
    /** Bumped when sparks are emitted: wakes the frame loop. */
    var kick by mutableIntStateOf(0)
}

private fun icon(c: SuitCategory): ImageVector =
    when (c) {
        SuitCategory.CABLE -> Icons.Rounded.Cable
        SuitCategory.PORT -> Icons.Rounded.SettingsEthernet
        SuitCategory.COMMAND -> Icons.Rounded.Terminal
        SuitCategory.DEVICE -> Icons.Rounded.Router
        SuitCategory.HOP -> Icons.Rounded.Route
    }

private fun mono(c: SuitCategory) = c != SuitCategory.CABLE && c != SuitCategory.DEVICE

private fun center(root: LayoutCoordinates?, c: LayoutCoordinates?): Offset? {
    if (root == null || c == null || !root.isAttached || !c.isAttached) return null
    return root.localPositionOf(c, Offset(c.size.width / 2f, c.size.height / 2f))
}

/**
 * La maleta del técnico. [previous] are the logs of the rounds already played (announced tuning),
 * [record] the longest chain ever (a longer one celebrates), [ghost] the chain the record run
 * reached in this same round (grey LEDs).
 */
@Composable
fun SuitcaseBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    previous: List<String>,
    record: Int,
    ghost: Int?,
    done: (Boolean, String) -> Unit,
) {
    val task = c.suitcase ?: return
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val colors = MaterialTheme.colorScheme
    val tuning = remember(c) { Suitcase.tuning(previous.mapNotNull(SuitcaseLog::decode)) }
    val play = remember(c) { SuitPlay(task, tuning) }
    val scope = rememberCoroutineScope()
    val shake = rememberShake()
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val flyStyle = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    var frame by remember { mutableLongStateOf(0L) }
    val lid = remember(c) { Animatable(0f) }
    val breathe = remember(c) { Animatable(1f) }
    val lock = remember(c) { Animatable(0f) }
    var celebrate by remember(c) { mutableStateOf<Any?>(null) }

    // One frame loop, only while sparks are alive (the flight is an Animatable); never with
    // animations off.
    LaunchedEffect(play, motion) {
        if (!motion) return@LaunchedEffect
        snapshotFlow { play.kick }
            .collect {
                var last = 0L
                while (play.particles.active) withFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(.05f)
                    last = now
                    play.particles.step(dt)
                    frame = now
                    FrameProbe.frames++
                }
                frame = 0L
            }
    }

    fun launchFlight(from: Offset?, label: String) {
        val to = center(play.root, play.suitcase) ?: return
        if (!motion || from == null) return
        play.flyFrom = from
        play.flyTo = to
        play.flyText = measurer.measure(label, flyStyle)
        scope.launch {
            play.fly.snapTo(0f)
            play.fly.animateTo(1f, Juice.tween(Juice.Move, true, Juice.EaseIn))
        }
    }
    fun sparkAtSuitcase(hit: Boolean) {
        val at = center(play.root, play.suitcase) ?: return
        if (hit) Juice.hit(play.particles, at.x, at.y, motion)
        else Juice.spark(play.particles, at.x, at.y, Juice.NetAmber, motion)
        play.kick++
    }
    suspend fun breatheOnce() {
        if (motion) {
            breathe.animateTo(1.04f, Juice.tween(Juice.Tick, true))
            breathe.animateTo(1f, Juice.bouncy(true))
        }
    }

    suspend fun present(i: Int) {
        val item = task.chain[i]
        play.stage = SuitStage.SHOW
        play.showing = item
        play.seen += item.key
        cue(FeedbackCue.WHOOSH)
        lid.go(1f, Juice.tween(Juice.Quick, motion), motion)
        delay(task.showMs + tuning.extraMs)
        launchFlight(center(play.root, play.card), item.label)
        play.showing = null
        lid.go(0f, Juice.tween(Juice.Reveal, motion, Juice.Overshoot), motion)
        play.length = i + 1
        cue(FeedbackCue.TAP)
        sparkAtSuitcase(false)
        breatheOnce()
        delay(if (motion) Juice.Quick.toLong() else 0L)
    }
    fun startRecall() {
        play.tray = SuitcaseTray(task, play.length, tuning.extraDecoys)
        play.version++
        play.stage = SuitStage.RECALL
    }
    fun finish(success: Boolean) {
        play.stage = SuitStage.END
        val rec = play.best > record && record > 0
        if (rec) {
            celebrate = play.best
            cue(FeedbackCue.LEVEL_UP)
        }
        scope.launch {
            delay(Juice.Hold.toLong())
            done(play.log().clean, play.log().encode())
        }
        if (!success) scope.launch { shake.play(Shake.SMALL, motion) }
    }
    fun chainDone() {
        play.best = play.length
        sparkAtSuitcase(true)
        scope.launch {
            lock.snapTo(0f)
            lock.go(1f, Juice.tween(Juice.Reveal, motion, Juice.Overshoot), motion)
        }
        if (play.length >= task.target) {
            finish(true)
            return
        }
        play.stage = SuitStage.CLOSING
        scope.launch {
            delay(Juice.Reveal.toLong())
            present(play.length)
            startRecall()
        }
    }
    fun tap(slot: Int) {
        val tray = play.tray ?: return
        if (play.stage != SuitStage.RECALL) return
        val item = tray.slots[slot] ?: return
        val from = center(play.root, play.slots[slot])
        if (tray.take(slot)) {
            play.version++
            play.wrongSlot = -1
            cue(FeedbackCue.COMBO)
            launchFlight(from, item.label)
            scope.launch { breatheOnce() }
            if (tray.done) chainDone()
            return
        }
        // Mistake: the tile bounces back. First one in this chain: the right tile glows 1 s and
        // goes in (chain "con ayuda").
        play.wrongSlot = slot
        cue(FeedbackCue.RETRY)
        scope.launch { shake.play(Shake.SMALL, motion) }
        if (play.helped) {
            play.best = play.length - 1
            finish(false)
            return
        }
        play.helped = true
        play.miss = tray.expected?.key ?: ""
        play.tapped = item.key
        val right = tray.slotOf(tray.expected)
        play.helpSlot = right
        play.stage = SuitStage.HELP
        scope.launch {
            delay(1000)
            val rItem = tray.slots[right]
            val rFrom = center(play.root, play.slots[right])
            tray.take(right)
            play.version++
            play.helpSlot = -1
            play.wrongSlot = -1
            rItem?.let { launchFlight(rFrom, it.label) }
            play.stage = SuitStage.RECALL
            if (tray.done) chainDone()
        }
    }
    LaunchedEffect(c, answering) {
        if (!answering || play.length > 0) return@LaunchedEffect
        delay(if (motion) Juice.Move.toLong() else 0L)
        for (i in 0 until task.start) present(i)
        startRecall()
    }
    // The description bubble of a long press fades after a moment.
    LaunchedEffect(play.info) {
        if (play.info != null) {
            delay(2600)
            play.info = null
        }
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { play.root = it }) {
        Column(
            Modifier.fillMaxWidth().shake(shake),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Challenge strip: level, objective, progress and record (§2 "Zona de reto visible").
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Leather)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Rounded.Luggage, null, Modifier.size(20.dp), tint = Juice.NetAmber)
                Text(
                    "Nivel ${task.level} · obj ${task.target} · ${play.length}/${task.target}",
                    Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                    color = Color(0xFFFFE1A6),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (record > 0)
                    Text(
                        "récord $record",
                        color = Juice.NetAmber.copy(alpha = .8f),
                        style = MaterialTheme.typography.labelLarge,
                    )
            }
            val banner =
                when (task.rule) {
                    SuitRule.LAYER ->
                        "Regla de capas: mételos ordenados por capa OSI, no por llegada"
                    SuitRule.TRACE ->
                        "Traceroute: recuerda el orden de los saltos, incluido el «* * *»"
                    else -> null
                }
            if (banner != null || tuning.note != null)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    banner?.let {
                        Text(
                            it,
                            Modifier.weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Juice.NetAmber.copy(alpha = .16f))
                                .padding(10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                        )
                    }
                    tuning.note?.let {
                        Text(
                            it,
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(Juice.Info.copy(alpha = .16f))
                                .padding(10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSurface,
                        )
                    }
                }
            SuitcaseArt(play, ghost, lid, breathe, lock)
            NewItemCard(play)
            SuitTray(play, motion) { tap(it) }
            val hint =
                when {
                    play.info != null -> null
                    play.helped && play.stage != SuitStage.END -> task.hint
                    task.rule == SuitRule.LAYER -> Suitcase.layerHint
                    else -> null
                }
            hint?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.Lightbulb, null, Modifier.size(18.dp), tint = Juice.NetAmber)
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (feedback || play.stage == SuitStage.END) {
                val log = play.log()
                Text(
                    when {
                        log.clean -> "¡Cadena completa! ${log.length} de ${log.target}"
                        log.reached -> "Cadena completa con ayuda · vale el 50 %"
                        else -> "Cadena de ${log.length} · objetivo ${log.target}"
                    },
                    Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (log.clean) Juice.Good else colors.onSurface,
                )
            }
        }
        // Overlay: the tile that flies into the suitcase and the sparks. Reads [frame] only while
        // something moves.
        Canvas(Modifier.matchParentSize()) {
            frame
            val f = play.fly.value
            val t = play.flyText
            if (f < 1f && t != null) {
                val p = play.flyFrom + (play.flyTo - play.flyFrom) * f
                val k = 1f - .4f * f
                val w = (t.size.width + 28.dp.toPx()) * k
                val h = (t.size.height + 18.dp.toPx()) * k
                drawRoundRect(
                    Leather.copy(alpha = 1f - .5f * f),
                    Offset(p.x - w / 2, p.y - h / 2),
                    Size(w, h),
                    CornerRadius(12.dp.toPx() * k),
                )
                drawRoundRect(
                    Juice.NetAmber.copy(alpha = 1f - .5f * f),
                    Offset(p.x - w / 2, p.y - h / 2),
                    Size(w, h),
                    CornerRadius(12.dp.toPx() * k),
                    style = Stroke(1.5.dp.toPx()),
                )
                scale(k, k, p) {
                    drawText(
                        t,
                        topLeft = Offset(p.x - t.size.width / 2f, p.y - t.size.height / 2f),
                        alpha = 1f - .4f * f,
                    )
                }
            }
            with(play.particles) { draw() }
        }
        CelebrationBurst(celebrate, Modifier.matchParentSize())
    }
}

/**
 * The suitcase: leather body, stitching, handle, latch and one LED per link (§2 "Diseño visual").
 * Canvas only.
 */
@Composable
private fun SuitcaseArt(
    play: SuitPlay,
    ghost: Int?,
    lid: Animatable<Float, AnimationVector1D>,
    breathe: Animatable<Float, AnimationVector1D>,
    lock: Animatable<Float, AnimationVector1D>,
) {
    val measurer = rememberTextMeasurer()
    val length = play.length
    val step = play.step
    val target = play.task.target
    val stage = play.stage
    val count =
        remember(length) {
            measurer.measure(
                "$length",
                TextStyle(color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black),
            )
        }
    val caption = remember {
        measurer.measure(
            "MALETA",
            TextStyle(
                color = Juice.NetAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            ),
        )
    }
    val dash = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) }
    val failed = stage == SuitStage.END && !play.log().reached
    Canvas(
        Modifier.fillMaxWidth()
            .height(196.dp)
            .graphicsLayer {
                scaleX = breathe.value
                scaleY = breathe.value
            }
            .onGloballyPositioned { play.suitcase = it }
            .semantics {
                contentDescription =
                    "Maleta con $length objetos de $target. " +
                        when (stage) {
                            SuitStage.SHOW -> "Observa el objeto nuevo."
                            SuitStage.RECALL,
                            SuitStage.HELP -> "Paso ${step + 1} de $length."
                            else -> ""
                        }
            }
    ) {
        val w = size.width
        val h = size.height
        val bodyTop = h * .2f
        val r = 22.dp.toPx()
        // Handle
        drawRoundRect(
            LeatherHi,
            Offset(w * .38f, h * .02f),
            Size(w * .24f, h * .24f),
            CornerRadius(14.dp.toPx()),
            style = Stroke(7.dp.toPx()),
        )
        // Body and the lid, which lifts while a new item is shown.
        val open = lid.value * 12.dp.toPx()
        drawRoundRect(Leather, Offset(0f, bodyTop), Size(w, h - bodyTop), CornerRadius(r))
        if (open > .5f)
            drawRect(
                Juice.NetAmber.copy(alpha = .25f * lid.value),
                Offset(10.dp.toPx(), bodyTop + h * .26f - open),
                Size(w - 20.dp.toPx(), open),
            )
        drawRoundRect(LeatherHi, Offset(0f, bodyTop - open), Size(w, h * .26f), CornerRadius(r))
        drawRoundRect(
            Stitch,
            Offset(8.dp.toPx(), bodyTop + 8.dp.toPx()),
            Size(w - 16.dp.toPx(), h - bodyTop - 16.dp.toPx()),
            CornerRadius(r * .7f),
            style = Stroke(1.5.dp.toPx(), pathEffect = dash),
        )
        // Latch: glows green when a chain closes.
        val lk = lock.value
        val latch = Offset(w / 2 - 18.dp.toPx(), bodyTop + h * .26f - 10.dp.toPx() - open)
        drawRoundRect(
            lerp(Juice.NetAmber, Juice.Good, lk.coerceIn(0f, 1f)),
            latch,
            Size(36.dp.toPx(), 20.dp.toPx()),
            CornerRadius(6.dp.toPx()),
        )
        if (lk in .01f..1.2f)
            drawCircle(
                Juice.Good.copy(alpha = (1.2f - lk).coerceIn(0f, .5f)),
                30.dp.toPx() * lk,
                latch + Offset(18.dp.toPx(), 10.dp.toPx()),
            )
        // Caption and counter
        drawText(caption, topLeft = Offset(22.dp.toPx(), bodyTop + h * .3f))
        drawText(count, topLeft = Offset(22.dp.toPx(), bodyTop + h * .38f))
        // LEDs: recalled in this pass (bright), packed (amber), record ghost (grey ring), empty
        // (outline); red when the chain broke.
        val n = maxOf(target, ghost ?: 0).coerceAtMost(14)
        val gap = 6.dp.toPx()
        val led = ((w - 44.dp.toPx() - gap * (n - 1)) / n).coerceAtMost(16.dp.toPx())
        val y = h - 26.dp.toPx()
        val x0 = w - 22.dp.toPx() - n * led - (n - 1) * gap
        for (i in 0 until n) {
            val cx = x0 + i * (led + gap) + led / 2
            val fill =
                when {
                    failed && i == length -> Juice.Bad
                    i < step && (stage == SuitStage.RECALL || stage == SuitStage.HELP) -> Juice.Good
                    i < length -> Juice.NetAmber
                    else -> Color.Transparent
                }
            if (fill != Color.Transparent) {
                drawCircle(fill.copy(alpha = .25f), led * .9f, Offset(cx, y))
                drawCircle(fill, led / 2, Offset(cx, y))
            } else
                drawCircle(
                    Color.White.copy(alpha = .22f),
                    led / 2,
                    Offset(cx, y),
                    style = Stroke(1.5.dp.toPx()),
                )
            if (ghost != null && i < ghost)
                drawCircle(
                    Color(0xFFB8BEC9).copy(alpha = .7f),
                    led / 2 + 3.dp.toPx(),
                    Offset(cx, y),
                    style = Stroke(1.dp.toPx()),
                )
            if (i == target - 1)
                drawLine(
                    Juice.NetAmber.copy(alpha = .6f),
                    Offset(cx + led / 2 + gap / 2, y - led),
                    Offset(cx + led / 2 + gap / 2, y + led),
                    1.dp.toPx(),
                )
        }
    }
}

/** "NUEVO" card while an item is shown; otherwise what to do now. */
@Composable
private fun NewItemCard(play: SuitPlay) {
    val motion = LocalMotionEnabled.current
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().heightIn(min = 76.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            play.showing,
            transitionSpec = {
                if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                else
                    (fadeIn(tween(Juice.Quick)) +
                        scaleIn(
                            tween(Juice.Reveal, easing = Juice.Overshoot),
                            initialScale = .6f,
                        )) togetherWith
                        (fadeOut(tween(Juice.Quick)) +
                            scaleOut(tween(Juice.Move, easing = Juice.EaseIn), targetScale = .6f))
            },
            label = "Nuevo",
        ) { item ->
            if (item != null)
                Row(
                    Modifier.fillMaxWidth()
                        .onGloballyPositioned { play.card = it }
                        .clip(RoundedCornerShape(18.dp))
                        .background(Juice.NetAmber.copy(alpha = .14f))
                        .border(1.5.dp, Juice.NetAmber, RoundedCornerShape(18.dp))
                        .padding(14.dp)
                        .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "NUEVO",
                        style = MaterialTheme.typography.labelLarge,
                        color = Juice.NetAmber,
                        fontWeight = FontWeight.Black,
                    )
                    Icon(icon(item.category), null, Modifier.size(26.dp), tint = Juice.NetAmber)
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            fontFamily = if (mono(item.category)) FontFamily.Monospace else null,
                        )
                        Text(
                            item.detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            else
                Text(
                    when (play.stage) {
                        SuitStage.RECALL ->
                            "Mete los ${play.length} objetos en orden · paso ${play.step + 1}"
                        SuitStage.HELP -> "Era esta: entra con ayuda y sigues"
                        SuitStage.CLOSING -> "¡Bien! La maleta se cierra…"
                        SuitStage.END -> "Maleta cerrada"
                        else -> "Observa…"
                    },
                    Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                )
        }
    }
}

/**
 * The tray: fixed slots (2×3 … 3×4). Tap = into the suitcase; long press (or the accessibility
 * action) = description.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuitTray(play: SuitPlay, motion: Boolean, tap: (Int) -> Unit) {
    val tray = play.tray
    play.version
    val size = tray?.size ?: play.task.tray
    val columns = if (size % 3 == 0 && size != 12) 3 else 4
    val enabled = play.stage == SuitStage.RECALL
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        play.info?.let {
            Text(
                "${it.label} · ${it.detail}",
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.inverseSurface)
                    .padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                color = colors.inverseOnSurface,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        BoardGrid(columns, size) { i ->
            val item = tray?.slots?.getOrNull(i)
            val help = i == play.helpSlot
            val wrong = i == play.wrongSlot
            val appear = remember(item?.key) { Animatable(if (motion && tray != null) .6f else 1f) }
            LaunchedEffect(item?.key) {
                if (motion) appear.animateTo(1f, tween(Juice.Reveal, easing = Juice.Overshoot))
            }
            val bounce = remember { Animatable(0f) }
            LaunchedEffect(wrong) {
                if (wrong && motion) {
                    bounce.snapTo(-14f)
                    bounce.animateTo(0f, Juice.snappy(true))
                }
            }
            val border by
                animateColorAsState(
                    when {
                        help -> Juice.Good
                        wrong -> Juice.Bad
                        else -> Juice.NetAmber.copy(alpha = if (enabled) .9f else .35f)
                    },
                    Juice.tween(Juice.Quick, motion),
                    label = "Borde",
                )
            val pulse =
                if (help && motion)
                    rememberInfiniteTransition(label = "Ayuda")
                        .animateFloat(
                            1f,
                            1.06f,
                            infiniteRepeatable(tween(Juice.Move), RepeatMode.Reverse),
                            label = "Pulso",
                        )
                        .value
                else 1f
            Box(
                Modifier.fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .onGloballyPositioned { play.slots[i] = it }
                    .graphicsLayer {
                        val k = appear.value * pulse
                        scaleX = k
                        scaleY = k
                        translationY = bounce.value * density
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (help) Juice.Good.copy(alpha = .18f)
                        else if (wrong) Juice.Bad.copy(alpha = .16f) else colors.surface
                    )
                    .border(if (help || wrong) 3.dp else 1.5.dp, border, RoundedCornerShape(16.dp))
                    .then(
                        if (item != null)
                            Modifier.combinedClickable(
                                enabled = enabled,
                                onClickLabel = "Meter en la maleta",
                                onLongClickLabel = "Ver descripción",
                                onLongClick = { play.info = item },
                                onClick = { tap(i) },
                            )
                        else Modifier
                    )
                    .semantics {
                        contentDescription = item?.let { "Ficha ${it.label}" } ?: "Hueco vacío"
                        role = Role.Button
                    }
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (item != null)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            icon(item.category),
                            null,
                            Modifier.size(20.dp),
                            tint = if (enabled) Juice.NetAmber else colors.onSurfaceVariant,
                        )
                        Text(
                            item.label,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (item.label.length > 12) 13.sp else 15.sp,
                            lineHeight = 17.sp,
                            fontFamily = if (mono(item.category)) FontFamily.Monospace else null,
                            color = if (enabled) colors.onSurface else colors.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
            }
        }
    }
}

/**
 * Result extras (§2 "Pantalla de resultado"): longest chain, clean / helped, the confusion to work
 * on and the record ghost as grey LEDs.
 */
@Composable
fun SuitcaseResult(r: ResultRow, history: List<ResultRow>) {
    val logs = remember(r.id) { boardLogs(r).map(SuitcaseLog::decode) }
    val ghost =
        remember(r.id) {
            history
                .filter { it.game == r.game && it.id != r.id }
                .maxByOrNull { it.correct }
                ?.let { b -> boardLogs(b).map { SuitcaseLog.decode(it)?.length ?: 0 } }
        }
    val valid = logs.filterNotNull()
    if (valid.isEmpty()) return
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultFigure("${valid.maxOf { it.length }}", "cadena máxima", Modifier.weight(1f))
            ResultFigure("${valid.count { it.clean }}", "limpias", Modifier.weight(1f))
            ResultFigure("${valid.count { it.helped }}", "con ayuda", Modifier.weight(1f))
        }
        Suitcase.weakest(valid)?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = Juice.NetAmber)
        }
        val top = maxOf(valid.maxOf { it.target }, ghost?.maxOrNull() ?: 0)
        Canvas(
            Modifier.fillMaxWidth().height(70.dp).semantics {
                contentDescription =
                    "Cadena por ronda: " +
                        logs.joinToString(", ") { "${it?.length ?: 0}" } +
                        (ghost?.let { "; récord: " + it.joinToString(", ") } ?: "")
            }
        ) {
            val col = size.width / 12f
            val dot = (size.height / top).coerceAtMost(col * .6f) * .8f
            for (i in 0 until 12) {
                val x = col * i + col / 2
                val len = logs.getOrNull(i)?.length ?: 0
                val g = ghost?.getOrNull(i) ?: 0
                for (k in 0 until maxOf(len, g)) {
                    val y = size.height - (k + .5f) * size.height / top
                    if (k < len)
                        drawCircle(
                            if (logs[i]?.clean == true) Juice.Good else Juice.NetAmber,
                            dot / 2,
                            Offset(x, y),
                        )
                    if (k < g)
                        drawCircle(
                            Color(0xFFB8BEC9).copy(alpha = .8f),
                            dot / 2 + 2.dp.toPx(),
                            Offset(x, y),
                            style = Stroke(1.dp.toPx()),
                        )
                }
            }
        }
        Text(
            if (ghost != null) "Puntos: esta partida · anillos grises: tu récord"
            else "Puntos: eslabones por ronda",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ResultFigure(value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The per-round board logs of a saved result ("detail" of each trial; empty for older rows). */
internal fun boardLogs(r: ResultRow): List<String> =
    runCatching {
            val a = JSONArray(r.trials)
            (0 until a.length()).map { a.getJSONObject(it).optString("detail", "") }
        }
        .getOrDefault(emptyList())
