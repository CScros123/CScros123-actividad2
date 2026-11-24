package personal.qqq.braintraining

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

/* Fase 3 (docs/DISENO-JUEGOS-V2.md §6): Parejas de incidentes. One composable per card with graphicsLayer (3D flip, jump, shake),
 * no global Canvas; the only frame loop is the particle overlay, and only while sparks live (never with animations off). Board,
 * trios, partial shuffle and hints: lab-core/PairGame.kt (PairsPlay). The theme (chest cosmetic) only changes back, palette and
 * font. */

@Stable
private class PairsWorld(val board: IncidentBoard) {
    val play = PairsPlay(board)
    val particles = Particles()
    /** Cell bounds in the board box (px), for the light arc and the sparks. */
    val cx = FloatArray(board.cards.size)
    val cy = FloatArray(board.cards.size)
    var version by mutableIntStateOf(0)
    var arc by mutableStateOf<IntArray?>(null)
    var missed by mutableStateOf<Set<Int>>(emptySet())
    var swapped by mutableStateOf<IntArray?>(null)
    var hint by mutableIntStateOf(-1)
    var lesson by mutableStateOf<Pair<Int, String>?>(null)
    var notice by mutableStateOf<String?>(null)
    var log by mutableStateOf<PairsLog?>(null)
    var sparks by mutableIntStateOf(0)

    fun say(text: String) {
        lesson = (lesson?.first ?: 0) + 1 to text
    }
}

/**
 * Parejas de incidentes. [previous] are the logs of the boards already played, [index] this board
 * and [ghost] the attempts the record run needed on this board; [theme] the equipped "theme:*"
 * cosmetic (null: Clásico ámbar).
 */
@Composable
fun PairsBoard(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    previous: List<String>,
    index: Int,
    ghost: Int?,
    theme: String?,
    done: (Boolean, String) -> Unit,
) {
    val board = c.pairs ?: return
    val motion = LocalMotionEnabled.current
    val cue = LocalFeedback.current
    val colors = MaterialTheme.colorScheme
    val look = IncidentPairs.theme(theme)
    val world = remember(c) { PairsWorld(board) }
    val scope = rememberCoroutineScope()
    var frame by remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val play = world.play

    fun mid(a: Int, b: Int) =
        Offset((world.cx[a] + world.cx[b]) / 2, (world.cy[a] + world.cy[b]) / 2)
    fun cellOf(id: Int) = play.cells.indexOf(id)
    fun flip(cell: Int) {
        if (!answering || world.log != null) return
        val id = play.cells[cell]
        when (play.flip(id)) {
            PairsPlay.Flip.OPENED,
            PairsPlay.Flip.PARTIAL -> {
                cue(FeedbackCue.TAP)
                world.hint = -1
            }
            PairsPlay.Flip.MATCH,
            PairsPlay.Flip.TRIO -> {
                val e = play.entryOf(id)
                val cells =
                    board.cards.filter { it.entry == board.cards[id].entry }.map { cellOf(it.id) }
                world.arc = cells.toIntArray()
                Juice.hit(
                    world.particles,
                    mid(cells[0], cells[1]).x,
                    mid(cells[0], cells[1]).y,
                    motion,
                )
                world.sparks++
                cue(FeedbackCue.COMBO)
                world.say(e.lesson)
                if (play.streak == 4) world.notice = "🔥 racha ×2 puntos"
                if (play.done) {
                    val log = play.log()
                    world.log = log
                    cue(FeedbackCue.COMPLETE)
                    Juice.confetti(
                        world.particles,
                        world.cx.maxOrNull()?.times(1.1f) ?: 0f,
                        world.cy.minOrNull() ?: 0f,
                        motion,
                    )
                    world.sparks++
                    scope.launch {
                        delay(Juice.Celebrate.toLong())
                        done(log.passed, log.encode())
                    }
                }
            }
            PairsPlay.Flip.MISS -> {
                cue(FeedbackCue.RETRY)
                scope.launch {
                    delay(if (motion) Juice.Reveal + 300L else 300L)
                    world.missed = play.open.map(::cellOf).toSet()
                    val swap = play.hide()
                    world.missed = emptySet()
                    world.version++
                    if (swap != null) {
                        world.swapped = swap
                        world.notice = "⚠ 2 cartas cambiaron de sitio (4 fallos seguidos)"
                        cue(FeedbackCue.WHOOSH)
                    }
                    val h = play.hint()
                    if (h >= 0) {
                        world.hint = cellOf(h)
                        world.notice = "La viste aquí: esa carta lleva más tiempo sin mirarse."
                    }
                }
            }
            PairsPlay.Flip.IGNORED -> Unit
        }
        world.version++
    }
    fun lessonAt(cell: Int) {
        val id = play.cells[cell]
        if (play.matched[id]) world.say(play.entryOf(id).lesson)
    }

    // Frame loop only for sparks, and only with animations on.
    LaunchedEffect(world, world.sparks, motion, resumed) {
        if (!motion || !resumed) return@LaunchedEffect
        var last = 0L
        while (isActive && world.particles.active) withFrameNanos { now ->
            val dt = if (last == 0L) 0L else (now - last).coerceAtMost(50_000_000L)
            last = now
            world.particles.step(dt / 1e9f)
            frame = now
            FrameProbe.frames++
        }
    }
    LaunchedEffect(world.lesson) {
        val l = world.lesson ?: return@LaunchedEffect
        delay(4_000)
        if (world.lesson == l) world.lesson = null
    }
    LaunchedEffect(world.notice) {
        val n = world.notice ?: return@LaunchedEffect
        delay(2_500)
        if (world.notice == n) world.notice = null
    }
    LaunchedEffect(world.arc) {
        if (world.arc != null) {
            delay(Juice.Reveal.toLong())
            world.arc = null
        }
    }

    @Suppress("UNUSED_VARIABLE") val v = world.version
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(look.face))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Nv${board.level} · ${board.cards.size} cartas · ≤ ${board.target} intentos · llevas ${play.attempts}",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = Color(look.ink),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Tablero ${index + 1}/12 · ${look.title}" +
                        (ghost?.let { " · récord aquí $it intentos" } ?: ""),
                    color = Color(look.ink).copy(alpha = .7f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            if (play.streak >= 2)
                Text("🔥×${play.streak}", color = Color(look.accent), fontWeight = FontWeight.Black)
        }
        Box(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (r in 0 until board.rows) Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until board.cols) {
                        val cell = r * board.cols + col
                        key(cell) {
                            IncidentCardView(
                                world,
                                cell,
                                r,
                                col,
                                look,
                                motion,
                                answering,
                                Modifier.weight(1f),
                                ::flip,
                                ::lessonAt,
                            )
                        }
                    }
                }
            }
            Canvas(Modifier.matchParentSize()) {
                frame
                world.arc?.let { a ->
                    if (a.size >= 2)
                        for (k in 0 until a.size - 1) {
                            val p = Offset(world.cx[a[k]], world.cy[a[k]])
                            val q = Offset(world.cx[a[k + 1]], world.cy[a[k + 1]])
                            drawLine(
                                Color(look.accent).copy(alpha = .8f),
                                p,
                                q,
                                5.dp.toPx(),
                                StrokeCap.Round,
                            )
                            drawCircle(Color.White, 6.dp.toPx(), p)
                            drawCircle(Color.White, 6.dp.toPx(), q)
                        }
                }
                with(world.particles) { draw() }
            }
        }
        AnimatedContent(
            world.notice,
            transitionSpec = {
                if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                else fadeIn(tween(Juice.Quick)) togetherWith fadeOut(tween(Juice.Quick))
            },
            label = "Aviso",
        ) { n ->
            if (n != null)
                Text(
                    n,
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    color = Juice.NetAmber,
                    style = MaterialTheme.typography.labelLarge,
                )
            else Spacer(Modifier.height(1.dp))
        }
        // Lesson strip under the board (§6): fixed height so the board never jumps.
        Box(
            Modifier.fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surfaceVariant)
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(
                world.lesson,
                transitionSpec = {
                    if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                    else
                        (fadeIn(tween(Juice.Quick)) +
                            slideInVertically(tween(Juice.Move)) { it / 3 }) togetherWith
                            fadeOut(tween(Juice.Quick))
                },
                label = "Lección",
            ) { l ->
                Text(
                    l?.second
                        ?: "Voltea dos cartas. Toca dos veces una pareja hecha para releer su lección.",
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (l == null) colors.onSurfaceVariant else colors.onSurface,
                )
            }
        }
        world.log?.let { log ->
            Panel {
                Text(
                    if (log.passed) "¡Tablero limpio!"
                    else "Tablero completo, por encima del objetivo",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (log.passed) Juice.Good else colors.onSurface,
                )
                Text(
                    "${log.attempts} intentos (objetivo ≤ ${log.target}) · racha máx. ${log.maxStreak} · ${log.score} puntos",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun IncidentCardView(
    w: PairsWorld,
    cell: Int,
    row: Int,
    col: Int,
    look: PairsTheme,
    motion: Boolean,
    answering: Boolean,
    modifier: Modifier,
    flip: (Int) -> Unit,
    lessonAt: (Int) -> Unit,
) {
    @Suppress("UNUSED_VARIABLE") val v = w.version
    val play = w.play
    val id = play.cells[cell]
    val card = play.card(id)
    val matched = play.matched[id]
    val up = matched || id in play.open || w.log != null
    val density = LocalDensity.current
    val turn = remember { Animatable(0f) }
    LaunchedEffect(up, motion) {
        turn.go(if (up) 180f else 0f, Juice.tween(Juice.Reveal, motion), motion)
    }
    // Jump when matched, wave when the board is complete (40 ms per diagonal step), shake on a
    // miss, slide on a partial shuffle.
    val jump = remember { Animatable(1f) }
    LaunchedEffect(matched) {
        if (matched && motion) {
            jump.animateTo(1.12f, tween(Juice.Tick))
            jump.animateTo(1f, Juice.bouncy(true))
        }
    }
    val wave = remember { Animatable(0f) }
    LaunchedEffect(w.log != null) {
        if (w.log != null && motion) {
            delay(40L * (row + col))
            wave.animateTo(360f, tween(Juice.Reveal, easing = Juice.EaseOut))
        }
    }
    val shake = rememberShake()
    LaunchedEffect(cell in w.missed) { if (cell in w.missed) shake.play(Shake.SMALL, motion) }
    val slide = remember { Animatable(0f) }
    LaunchedEffect(w.swapped) {
        val s = w.swapped ?: return@LaunchedEffect
        if (cell in s && motion) {
            slide.snapTo(1f)
            slide.animateTo(0f, tween(Juice.Move, easing = Juice.EaseOut))
        }
        if (cell == s[0]) {
            delay(Juice.Move.toLong())
            w.swapped = null
        }
    }
    val blink = remember { Animatable(0f) }
    LaunchedEffect(w.hint == cell) {
        if (w.hint == cell) {
            if (motion) {
                blink.animateTo(1f, tween(Juice.Quick))
                blink.animateTo(0f, tween(Juice.Settle))
            } else {
                blink.snapTo(1f)
                delay(1_000)
                blink.snapTo(0f)
            }
        }
    }
    val swap = w.swapped
    val desc =
        when {
            matched -> "Pareja hecha: ${card.text}"
            up -> "Carta: ${card.text}"
            else -> "Carta boca abajo, fila ${row + 1}, columna ${col + 1}"
        }
    val face = turn.value >= 90f
    Box(
        modifier
            .aspectRatio(.75f)
            .onGloballyPositioned {
                val b = it.boundsInParent()
                val parent = it.parentLayoutCoordinates
                val o = parent?.boundsInParent()?.topLeft ?: Offset.Zero
                w.cx[cell] = b.center.x + o.x
                w.cy[cell] = b.center.y + o.y
            }
            .shake(shake, RoundedCornerShape(14.dp))
            .graphicsLayer {
                rotationY = turn.value + wave.value
                cameraDistance = 12f * density.density
                val s = jump.value
                scaleX = s
                scaleY = s
                if (swap != null && cell in swap) {
                    val other = if (swap[0] == cell) swap[1] else swap[0]
                    translationX = (w.cx[other] - w.cx[cell]) * slide.value
                    translationY = (w.cy[other] - w.cy[cell]) * slide.value
                }
                shadowElevation = (2f + 6f * (turn.value / 180f)) * density.density
                shape = RoundedCornerShape(14.dp)
                clip = true
            }
            .background(if (face) Color(look.face) else Color(look.back))
            .then(
                if (blink.value > 0f)
                    Modifier.border(
                        3.dp,
                        Color.White.copy(alpha = blink.value),
                        RoundedCornerShape(14.dp),
                    )
                else if (matched)
                    Modifier.border(2.dp, Color(look.accent), RoundedCornerShape(14.dp))
                else Modifier
            )
            .pointerInput(cell, answering) {
                detectTapGestures(onDoubleTap = { lessonAt(cell) }) { flip(cell) }
            }
            .semantics {
                contentDescription = desc
                role = Role.Button
                onClick(label = if (up) null else "Voltear") {
                    flip(cell)
                    true
                }
                if (matched)
                    customActions =
                        listOf(
                            CustomAccessibilityAction("Ver lección") {
                                lessonAt(cell)
                                true
                            }
                        )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (face)
            Text(
                card.text,
                Modifier.graphicsLayer { rotationY = 180f }.padding(6.dp),
                color = Color(look.ink),
                fontFamily =
                    if (look.mono || card.text.startsWith("Puerto") || card.text.startsWith(":"))
                        FontFamily.Monospace
                    else FontFamily.Default,
                fontSize = if (card.text.length > 18) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
            )
        else
            Box(
                Modifier.fillMaxSize().drawBehind {
                    // Circuit back: traces and pads in the theme colour, drawn from the card size
                    // (no allocation).
                    val ink = Color(look.face).copy(alpha = .35f)
                    val sw = 2.dp.toPx()
                    val pad = 3.dp.toPx()
                    for (k in 1..3) {
                        val y = size.height * k / 4f
                        drawLine(
                            ink,
                            Offset(size.width * .12f, y),
                            Offset(size.width * (.4f + .12f * k), y),
                            sw,
                        )
                        drawCircle(ink, pad, Offset(size.width * (.4f + .12f * k), y))
                    }
                    drawLine(
                        ink,
                        Offset(size.width * .7f, size.height * .2f),
                        Offset(size.width * .7f, size.height * .8f),
                        sw,
                    )
                    drawCircle(
                        ink,
                        size.minDimension * .12f,
                        Offset(size.width / 2, size.height / 2),
                        style = Stroke(sw),
                    )
                }
            )
    }
}

/**
 * Result extras (§6): attempts vs optimum, longest streak, pairs learned and how often the second
 * flip was informed.
 */
@Composable
fun PairsResult(r: ResultRow, history: List<ResultRow>) {
    val logs = remember(r.id) { boardLogs(r).mapNotNull(PairsLog::decode) }
    if (logs.isEmpty()) return
    val ghost =
        remember(r.id) {
            history
                .filter { it.game == r.game && it.id != r.id }
                .maxByOrNull { it.correct }
                ?.let { b -> boardLogs(b).map { PairsLog.decode(it)?.attempts ?: 0 } }
        }
    Panel {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ResultFigure(
                "${logs.sumOf { it.attempts }}/${logs.sumOf { it.target }}",
                "intentos / objetivo",
                Modifier.weight(1f),
            )
            ResultFigure("${logs.maxOf { it.maxStreak }}", "racha máx.", Modifier.weight(1f))
            ResultFigure(
                "${logs.flatMap { it.keys }.distinct().size}",
                "parejas vistas",
                Modifier.weight(1f),
            )
        }
        val attempts = logs.sumOf { it.attempts }
        if (attempts > 0)
            Text(
                "Segundo volteo informado: ${logs.sumOf { it.informed } * 100 / attempts} % (la carta ya la habías visto).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        Canvas(
            Modifier.fillMaxWidth().height(48.dp).semantics {
                contentDescription =
                    "Intentos por tablero: " +
                        logs.joinToString(", ") { "${it.attempts} de ${it.target}" }
            }
        ) {
            val col = size.width / 12f
            val top =
                maxOf(logs.maxOf { maxOf(it.attempts, it.target) }, ghost?.maxOrNull() ?: 0)
                    .coerceAtLeast(1)
            for (i in 0 until 12) {
                val x = col * i + col * .2f
                val bw = col * .6f
                val l = logs.getOrNull(i) ?: continue
                val h = size.height * l.attempts / top
                drawRect(
                    if (l.passed) Juice.Good else Juice.NetAmber,
                    Offset(x, size.height - h),
                    androidx.compose.ui.geometry.Size(bw, h),
                )
                val ty = size.height * (1f - l.target.toFloat() / top)
                drawLine(
                    Color.White.copy(alpha = .6f),
                    Offset(x - 2f, ty),
                    Offset(x + bw + 2f, ty),
                    1.dp.toPx(),
                )
                ghost?.getOrNull(i)?.let { g ->
                    val gy = size.height * (1f - g.toFloat() / top)
                    drawLine(
                        Color(0xFFB8BEC9),
                        Offset(x - 2.dp.toPx(), gy),
                        Offset(x + bw + 2.dp.toPx(), gy),
                        2.dp.toPx(),
                    )
                }
            }
        }
    }
}
