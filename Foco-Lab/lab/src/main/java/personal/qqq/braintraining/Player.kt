package personal.qqq.braintraining

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow
import personal.qqq.braintraining.design.Mint

@Composable
fun Player(vm: BrainViewModel) {
    val motion = LocalMotionEnabled.current
    // Keeps drawing the last run while the exit transition of LiteApp plays (vm.run is already null
    // then).
    val live = vm.run
    var last by remember { mutableStateOf(live) }
    if (live != null) last = live
    val run = live ?: last ?: return
    val g = Games.get(run.game)
    val format = Formats.of(run.game)
    val c = remember(run.game, run.level, run.seed, run.index, run.nonce) { run.challenge }
    var exit by remember { mutableStateOf(false) }
    var flash by remember(run.id, run.index, run.nonce) { mutableIntStateOf(-1) }
    var digit by remember(run.id, run.index, run.nonce) { mutableStateOf("") }
    BackHandler {
        if (run.phase == Phase.RESULT) vm.closeResult()
        else {
            vm.pause()
            exit = true
        }
    }
    if (exit)
        AlertDialog(
            onDismissRequest = { exit = false },
            title = { Text("¿Salir del entrenamiento?") },
            text = { Text("Puedes seguir jugando desde la pausa o salir de esta partida.") },
            confirmButton = {
                TextButton({
                    exit = false
                    vm.abandon()
                }) {
                    Text("Salir")
                }
            },
            dismissButton = { TextButton({ exit = false }) { Text("Seguir aquí") } },
        )
    LaunchedEffect(run.id, run.index, run.nonce, run.phase) {
        if (run.phase in listOf(Phase.OBSERVE, Phase.WAIT, Phase.ANSWER)) {
            withFrameNanos {}
            vm.presented()
        }
        when (run.phase) {
            Phase.OBSERVE -> {
                when (c.kind) {
                    Kind.SEQUENCE -> {
                        for (i in c.targets) {
                            flash = i
                            delay(c.exposureMs)
                            flash = -1
                            delay(220)
                        }
                    }
                    Kind.WORKING -> {
                        for (s in c.stream) {
                            digit = s
                            delay(c.exposureMs)
                            digit = ""
                            delay(150)
                        }
                    }
                    else -> delay(c.exposureMs)
                }
                vm.reveal()
            }
            Phase.WAIT -> {
                delay(c.exposureMs)
                vm.reveal()
            }
            Phase.ANSWER -> Unit
            else -> Unit
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier.padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({
                    if (run.phase == Phase.RESULT) vm.closeResult()
                    else {
                        vm.pause()
                        exit = true
                    }
                }) {
                    SmallIcon(Icons.Rounded.Close, "Salir")
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(g.title, style = MaterialTheme.typography.titleLarge)
                    AreaChip(g.area)
                }
                if (run.phase in listOf(Phase.OBSERVE, Phase.WAIT, Phase.ANSWER))
                    IconButton({ vm.pause() }) { SmallIcon(Icons.Rounded.Pause, "Pausar") }
            }
            // Phase changes (intro → rounds → saving → result) cross-fade with a small lift; a
            // plain cut with animations off (Lote B).
            val stage =
                when (run.phase) {
                    Phase.INTRO -> 0
                    Phase.PAUSED -> 1
                    Phase.SAVING -> 2
                    Phase.RESULT -> 3
                    else -> 4
                }
            AnimatedContent(
                stage,
                transitionSpec = {
                    if (!motion) fadeIn(snap()) togetherWith fadeOut(snap())
                    else
                        (fadeIn(tween(260, delayMillis = 80)) +
                            slideInVertically(tween(300)) { it / 14 }) togetherWith
                            fadeOut(tween(160))
                },
                label = "Fase",
            ) { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when (shown) {
                        0 -> {
                            GameArt(
                                g,
                                Modifier.fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(26.dp)),
                            )
                            Text(
                                "Tu siguiente desafío",
                                style = MaterialTheme.typography.headlineLarge,
                            )
                            Panel {
                                Text(g.instructions)
                                Text(
                                    if (vm.endless)
                                        (if (format.chained)
                                            "Modo infinito: las ${format.plural} siguen hasta que decidas salir."
                                        else
                                            "Modo infinito sin cronómetro. Aprende de cada solución.")
                                    else format.about,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (g.id == Orbit.ID)
                                OrbitModePicker(OrbitMode.of(run.variant)) {
                                    vm.chooseOrbitMode(it)
                                }
                            Primary("Empezar", { vm.begin() })
                        }
                        1 -> {
                            Spacer(Modifier.height(30.dp))
                            Text("Toma un respiro.", style = MaterialTheme.typography.displaySmall)
                            Text(
                                "La ronda interrumpida empezará con un reto nuevo.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Primary("Continuar", { vm.begin() })
                            TextButton({ exit = true }) { Text("Terminar sin guardar este bloque") }
                        }
                        2 -> {
                            Text(
                                "Guardando tu progreso…",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            CircularProgressIndicator()
                            if (vm.saveFailed) Primary("Reintentar", { vm.finish() })
                        }
                        3 -> {
                            vm.result?.let { res ->
                                val all = vm.results.collectAsState().value
                                val best = remember(res.id) { recordFor(res, all) }
                                ResultScreen(
                                    res,
                                    vm.previous,
                                    vm.planSize - run.remaining.size,
                                    vm.planSize,
                                    run.remaining.isNotEmpty(),
                                    { vm.continuePlan() },
                                    { vm.closeResult() },
                                    all,
                                    vm.mistakes,
                                    best,
                                    vm.againInRow,
                                    if (run.remaining.isEmpty()) {
                                        { vm.again() }
                                    } else null,
                                ) {
                                    if (res.game == Orbit.ID) OrbitAttention(res)
                                    PracticeRewards(vm)
                                }
                            }
                        }
                        else -> {
                            // Órbitas 2: one segmented bar holds rounds, points and the record
                            // ghost; the board core is the only "Observa".
                            if (c.kind == Kind.ORBIT)
                                OrbitHeader(
                                    run,
                                    vm.boardLogs(run),
                                    if (vm.endless) null else rememberOrbitGhost(vm, run),
                                    if (vm.endless) vm.endlessRound else null,
                                )
                            else {
                                Row(Modifier.fillMaxWidth()) {
                                    Label(
                                        if (vm.endless)
                                            "${format.unit.uppercase()} ${vm.endlessRound+1}"
                                        else
                                            "${format.unit.uppercase()} ${run.index+1} / ${format.units}"
                                    )
                                    if (c.review) {
                                        Spacer(Modifier.width(8.dp))
                                        Label("· Repaso", MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Label(
                                        when (run.phase) {
                                            Phase.OBSERVE -> "Observa"
                                            Phase.WAIT -> "Espera"
                                            Phase.FEEDBACK -> "Resultado"
                                            else -> "Tu turno"
                                        },
                                        MaterialTheme.colorScheme.primary,
                                    )
                                }
                                RoundProgress(vm, run, areaColor(g.area))
                            }
                            val enter =
                                remember(run.id, run.index, run.nonce) {
                                    Animatable(if (motion) 0f else 1f)
                                }
                            LaunchedEffect(run.id, run.index, run.nonce) {
                                if (motion)
                                    enter.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                            }
                            Column(
                                Modifier.graphicsLayer {
                                    alpha = enter.value
                                    translationX = (1f - enter.value) * 48.dp.toPx()
                                },
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                if (g.id == "compass") CompassBoard(c)
                                if (c.source.isNotEmpty())
                                    Label(Games.get(c.source).title, areaColor(g.area))
                                if (g.id == "balance" || c.source == "balance") BalanceBoard(c)
                                if (g.area == Area.NETSEC && c.board.isNotEmpty())
                                    CodeBox(c.board[0])
                                if (c.kind !in BOARD_KINDS)
                                    GlyphText(
                                        if (run.phase == Phase.OBSERVE && c.kind == Kind.WORKING)
                                            "Recuerda los últimos números"
                                        else c.prompt,
                                        style =
                                            when {
                                                g.id == "flanker" ->
                                                    MaterialTheme.typography.headlineLarge
                                                c.prompt.length > 65 ||
                                                    g.area == Area.NETSEC && c.prompt.length > 40 ->
                                                    MaterialTheme.typography.bodyLarge
                                                else -> MaterialTheme.typography.headlineMedium
                                            },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    )
                                when (c.kind) {
                                    Kind.GRID,
                                    Kind.SEQUENCE -> {
                                        val observe = run.phase == Phase.OBSERVE
                                        val feedback = run.phase == Phase.FEEDBACK
                                        BoardGrid(c.columns, c.columns * c.columns) { i ->
                                            val lit =
                                                if (feedback) i in c.targets
                                                else if (observe) {
                                                    if (c.kind == Kind.GRID) i in c.targets
                                                    else i == flash
                                                } else i in run.picked
                                            // Cells light up with a color tween and a short pop
                                            // (Destellos, Ecos); feedback marks missed and wrong
                                            // cells.
                                            val missed =
                                                feedback &&
                                                    c.kind == Kind.GRID &&
                                                    i in c.targets &&
                                                    i !in run.picked
                                            val extra =
                                                feedback &&
                                                    c.kind == Kind.GRID &&
                                                    i in run.picked &&
                                                    i !in c.targets
                                            val cellColor by
                                                animateColorAsState(
                                                    when {
                                                        extra -> MaterialTheme.colorScheme.error
                                                        lit -> MaterialTheme.colorScheme.primary
                                                        else ->
                                                            MaterialTheme.colorScheme.surfaceVariant
                                                    },
                                                    if (motion) tween(if (observe) 90 else 180)
                                                    else snap(),
                                                    label = "Casilla",
                                                )
                                            val pop by
                                                animateFloatAsState(
                                                    if (lit && observe) 1.07f else 1f,
                                                    if (motion)
                                                        spring(
                                                            dampingRatio = .45f,
                                                            stiffness = 600f,
                                                        )
                                                    else snap(),
                                                    label = "Encendido",
                                                )
                                            Box(
                                                Modifier.fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .graphicsLayer {
                                                        scaleX = pop
                                                        scaleY = pop
                                                    }
                                                    .clip(RoundedCornerShape(14.dp))
                                                    .background(cellColor)
                                                    .then(
                                                        if (missed)
                                                            Modifier.border(
                                                                3.dp,
                                                                MaterialTheme.colorScheme.error,
                                                                RoundedCornerShape(14.dp),
                                                            )
                                                        else Modifier
                                                    )
                                                    .pressClick(
                                                        enabled = run.phase == Phase.ANSWER,
                                                        feedbackOnClick = false,
                                                    ) {
                                                        vm.choose(i)
                                                    }
                                                    .semantics {
                                                        contentDescription =
                                                            "Casilla ${i+1}${if(lit)", iluminada" else ""}"
                                                    },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (feedback && c.kind == Kind.SEQUENCE)
                                                    Text(
                                                        c.targets
                                                            .mapIndexedNotNull { n, v ->
                                                                if (v == i) (n + 1).toString()
                                                                else null
                                                            }
                                                            .joinToString(","),
                                                        color =
                                                            if (lit)
                                                                MaterialTheme.colorScheme.onPrimary
                                                            else
                                                                MaterialTheme.colorScheme
                                                                    .onSurfaceVariant,
                                                        style = MaterialTheme.typography.labelLarge,
                                                    )
                                                else if (!observe && i in run.picked)
                                                    Icon(
                                                        Icons.Rounded.Check,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                    )
                                                if (
                                                    feedback &&
                                                        c.kind == Kind.GRID &&
                                                        (missed || extra)
                                                )
                                                    Icon(
                                                        if (missed) Icons.Rounded.Visibility
                                                        else Icons.Rounded.Close,
                                                        if (missed) "Faltó" else "Sobraba",
                                                        tint =
                                                            if (extra)
                                                                MaterialTheme.colorScheme.onError
                                                            else MaterialTheme.colorScheme.error,
                                                    )
                                            }
                                        }
                                        if (run.phase == Phase.ANSWER) {
                                            if (c.kind == Kind.GRID)
                                                Primary(
                                                    "Comprobar (${run.picked.size})",
                                                    { vm.checkGrid() },
                                                )
                                            else
                                                Text(
                                                    "${run.picked.size} de ${c.targets.size} pasos",
                                                    Modifier.fillMaxWidth(),
                                                    textAlign = TextAlign.Center,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                        }
                                    }
                                    Kind.WORKING -> {
                                        if (run.phase == Phase.OBSERVE)
                                            Box(
                                                Modifier.fillMaxWidth()
                                                    .height(180.dp)
                                                    .clip(RoundedCornerShape(24.dp))
                                                    .background(MaterialTheme.colorScheme.surface),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AnimatedContent(
                                                    digit,
                                                    transitionSpec = {
                                                        if (!motion)
                                                            fadeIn(snap()) togetherWith
                                                                fadeOut(snap())
                                                        else
                                                            (fadeIn(tween(120)) +
                                                                scaleIn(
                                                                    tween(160),
                                                                    initialScale = .7f,
                                                                )) togetherWith
                                                                (fadeOut(tween(100)) +
                                                                    slideOutVertically(tween(120)) {
                                                                        -it / 4
                                                                    })
                                                    },
                                                    label = "Dígito",
                                                ) { d ->
                                                    Text(
                                                        d,
                                                        fontSize = 88.sp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                    )
                                                }
                                            }
                                        else Choices(c, run, vm)
                                    }
                                    Kind.REACTION -> {
                                        val active = run.phase == Phase.ANSWER
                                        // The panel turns mint with a tween, a pop and an expanding
                                        // ring when the signal fires.
                                        val panel by
                                            animateColorAsState(
                                                if (active) Mint
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                if (motion) tween(120) else snap(),
                                                label = "Señal",
                                            )
                                        val punch by
                                            animateFloatAsState(
                                                if (active) 1f else .97f,
                                                if (motion)
                                                    spring(dampingRatio = .4f, stiffness = 700f)
                                                else snap(),
                                                label = "Escala",
                                            )
                                        val ring =
                                            remember(run.id, run.index, run.nonce) {
                                                Animatable(0f)
                                            }
                                        LaunchedEffect(active) {
                                            if (active && motion)
                                                ring.animateTo(
                                                    1f,
                                                    tween(520, easing = FastOutSlowInEasing),
                                                )
                                        }
                                        Box(
                                            Modifier.fillMaxWidth()
                                                .height(230.dp)
                                                .graphicsLayer {
                                                    scaleX = punch
                                                    scaleY = punch
                                                }
                                                .drawBehind {
                                                    val v = ring.value
                                                    if (v > 0f && v < 1f)
                                                        drawRoundRect(
                                                            Mint.copy(alpha = (1f - v) * .7f),
                                                            topLeft =
                                                                androidx.compose.ui.geometry.Offset(
                                                                    -v * 24.dp.toPx(),
                                                                    -v * 24.dp.toPx(),
                                                                ),
                                                            size =
                                                                androidx.compose.ui.geometry.Size(
                                                                    size.width + v * 48.dp.toPx(),
                                                                    size.height + v * 48.dp.toPx(),
                                                                ),
                                                            cornerRadius =
                                                                androidx.compose.ui.geometry
                                                                    .CornerRadius(
                                                                        28.dp.toPx() +
                                                                            v * 24.dp.toPx()
                                                                    ),
                                                            style =
                                                                androidx.compose.ui.graphics
                                                                    .drawscope
                                                                    .Stroke(
                                                                        4.dp.toPx() * (1f - v) + 1f
                                                                    ),
                                                        )
                                                }
                                                .clip(RoundedCornerShape(28.dp))
                                                .background(panel)
                                                .pressClick(
                                                    enabled =
                                                        run.phase in
                                                            listOf(Phase.WAIT, Phase.ANSWER),
                                                    feedbackOnClick = false,
                                                ) {
                                                    vm.choose(0)
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Bolt,
                                                    null,
                                                    Modifier.size(54.dp),
                                                    tint =
                                                        if (active) Color(0xFF103829)
                                                        else
                                                            MaterialTheme.colorScheme
                                                                .onSurfaceVariant,
                                                )
                                                Text(
                                                    if (active) "¡Ahora!"
                                                    else if (run.phase == Phase.FEEDBACK)
                                                        "Ronda terminada"
                                                    else "Espera…",
                                                    style = MaterialTheme.typography.headlineLarge,
                                                    color =
                                                        if (active) Color(0xFF103829)
                                                        else MaterialTheme.colorScheme.onSurface,
                                                )
                                            }
                                        }
                                    }
                                    Kind.MATRIX -> {
                                        val revealed = run.phase == Phase.FEEDBACK
                                        BoardGrid(3, 9) { i ->
                                            // In feedback the "?" flips into the right symbol.
                                            val flip by
                                                animateFloatAsState(
                                                    if (i == 8 && revealed) 1f else 0f,
                                                    if (motion)
                                                        tween(380, easing = FastOutSlowInEasing)
                                                    else snap(),
                                                    label = "Revelar",
                                                )
                                            val text =
                                                if (i == 8 && flip >= .5f) c.choices[c.answer]
                                                else c.board[i]
                                            Box(
                                                Modifier.fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .graphicsLayer {
                                                        if (i == 8) {
                                                            rotationY = flip * 180f
                                                            cameraDistance = 12f * density
                                                        }
                                                    }
                                                    .clip(RoundedCornerShape(15.dp))
                                                    .background(
                                                        if (i == 8 && flip >= .5f)
                                                            MaterialTheme.colorScheme
                                                                .secondaryContainer
                                                        else MaterialTheme.colorScheme.surface
                                                    ),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Box(
                                                    Modifier.graphicsLayer {
                                                        if (i == 8 && flip >= .5f) rotationY = 180f
                                                    }
                                                ) {
                                                    if (isGlyph(text))
                                                        Glyph(
                                                            text,
                                                            44.dp,
                                                            MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    else
                                                        Text(
                                                            text,
                                                            fontSize = 34.sp,
                                                            color =
                                                                if (i == 8)
                                                                    MaterialTheme.colorScheme
                                                                        .primary
                                                                else
                                                                    MaterialTheme.colorScheme
                                                                        .onSurface,
                                                        )
                                                }
                                            }
                                        }
                                        Choices(c, run, vm)
                                    }
                                    // Fase 7 pilot mechanics: the board reports the final answer,
                                    // the ViewModel scores it.
                                    Kind.SUBNET ->
                                        SubnetBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            run.picked,
                                        ) {
                                            vm.submit(listOf(it))
                                        }
                                    Kind.INBOX ->
                                        InboxBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            run.picked,
                                        ) {
                                            vm.submit(listOf(it))
                                        }
                                    Kind.FIREWALL ->
                                        FirewallBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                        ) {
                                            vm.submit(it)
                                        }
                                    Kind.BITS ->
                                        BitsBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            run.picked,
                                        ) {
                                            vm.submit(listOf(it))
                                        }
                                    // Fase 2 boards: they play the whole round and report the core
                                    // verdict and log.
                                    Kind.SUITCASE -> {
                                        val h = rememberBoardHistory(vm, run)
                                        SuitcaseBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            vm.boardLogs(run).take(run.index),
                                            h.record,
                                            h.ghost.getOrNull(run.index)?.let {
                                                SuitcaseLog.decode(it)?.length
                                            },
                                        ) { ok, d ->
                                            vm.boardDone(ok, d)
                                        }
                                    }
                                    // Fase 3 boards.
                                    Kind.DDOS -> {
                                        val h = rememberBoardHistory(vm, run)
                                        DdosBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            vm.boardLogs(run).take(run.index),
                                            run.index,
                                            h.ghost.getOrNull(run.index),
                                        ) { ok, d ->
                                            vm.boardDone(ok, d)
                                        }
                                    }
                                    Kind.ROUTE -> {
                                        val h = rememberBoardHistory(vm, run)
                                        RouteBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            vm.boardLogs(run).take(run.index),
                                            run.index,
                                            h.ghost.getOrNull(run.index)?.let {
                                                RouteLog.decode(it)?.ok
                                            },
                                        ) { ok, d ->
                                            vm.boardDone(ok, d)
                                        }
                                    }
                                    Kind.PAIRS -> {
                                        val h = rememberBoardHistory(vm, run)
                                        PairsBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            vm.boardLogs(run).take(run.index),
                                            run.index,
                                            h.ghost.getOrNull(run.index)?.let {
                                                PairsLog.decode(it)?.attempts
                                            },
                                            vm.wallet.theme,
                                        ) { ok, d ->
                                            vm.boardDone(ok, d)
                                        }
                                    }
                                    Kind.VIGIL -> {
                                        val h = rememberBoardHistory(vm, run)
                                        VigilBoard(
                                            c,
                                            run.phase == Phase.ANSWER,
                                            run.phase == Phase.FEEDBACK,
                                            vm.boardLogs(run).take(run.index),
                                            run.index,
                                            h.ghost.getOrNull(run.index)?.let {
                                                VigilLog.decode(it)?.detected
                                            },
                                        ) { ok, d ->
                                            vm.boardDone(ok, d)
                                        }
                                    }
                                    Kind.ORBIT ->
                                        OrbitBoard(c.orbit!!, run.phase == Phase.ANSWER) {
                                            ok,
                                            d,
                                            stop ->
                                            vm.boardDone(ok, d, stop)
                                        }
                                    else -> Choices(c, run, vm)
                                }
                            }
                            // Chained formats (paso 2) keep their own summary on the board: no
                            // dots, ghost bar or solution panel between units.
                            if (!format.chained) {
                                RoundDots(run, vm.endless, areaColor(g.area))
                                if (!vm.endless) RunGhost(vm, run)
                            }
                            AnimatedVisibility(
                                visible = run.phase == Phase.FEEDBACK,
                                enter =
                                    fadeIn(tween(if (motion) 180 else 0)) +
                                        slideInVertically(tween(if (motion) 220 else 0)) {
                                            if (motion) it / 8 else 0
                                        },
                                exit =
                                    if (motion) fadeOut(tween(160)) + shrinkVertically(tween(220))
                                    else ExitTransition.None,
                            ) {
                                val o = run.outcomes.lastOrNull()
                                if (o != null)
                                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Panel {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            ) {
                                                val accent =
                                                    if (o.correct)
                                                        MaterialTheme.colorScheme.secondary
                                                    else MaterialTheme.colorScheme.error
                                                Box(
                                                    Modifier.size(44.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(
                                                            if (o.correct)
                                                                MaterialTheme.colorScheme
                                                                    .secondaryContainer
                                                            else
                                                                MaterialTheme.colorScheme
                                                                    .errorContainer
                                                        ),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(
                                                        if (o.correct) Icons.Rounded.CheckCircle
                                                        else Icons.Rounded.Lightbulb,
                                                        null,
                                                        tint = accent,
                                                    )
                                                }
                                                Text(
                                                    if (o.correct) "Bien hecho"
                                                    else
                                                        when (o.reason) {
                                                            "early" -> "Te adelantaste"
                                                            "timeout" -> "Se acabó el tiempo"
                                                            "implausible" ->
                                                                "Respuesta demasiado anticipada"
                                                            else -> "Sigue practicando"
                                                        },
                                                    style = MaterialTheme.typography.titleLarge,
                                                )
                                            }
                                            Text(
                                                "Solución paso a paso",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                c.explanation,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            if (o.ms != null && g.area == Area.SPEED)
                                                Text(
                                                    "${o.ms} ms",
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                        }
                                        Primary(
                                            if (run.outcomes.size == format.units)
                                                "Ver mi resultado"
                                            else "Siguiente ronda",
                                            { vm.next() },
                                        )
                                    }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BoardGrid(columns: Int, count: Int, cell: @Composable (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for (row in 0 until (count + columns - 1) / columns) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                for (col in 0 until columns) {
                    val i = row * columns + col
                    Box(Modifier.weight(1f)) { if (i < count) cell(i) }
                }
            }
        }
    }
}

/**
 * Answer cards: staggered entrance per round, a pop on the right answer and a shake on a wrong one.
 */
@Composable
fun Choices(c: Challenge, run: Run, vm: BrainViewModel) =
    ChoiceCards(c, run.phase == Phase.ANSWER, run.phase == Phase.FEEDBACK, run.picked) {
        vm.choose(it)
    }

/**
 * Shared by the 12-round player and the Relámpago session. Code-like choices (logs, SQL) go in one
 * monospace column.
 */
@Composable
fun ChoiceCards(
    c: Challenge,
    answering: Boolean,
    feedback: Boolean,
    picked: List<Int>,
    choose: (Int) -> Unit,
) {
    val motion = LocalMotionEnabled.current
    val glyphs = c.choices.all { isGlyph(it) }
    BoardGrid(if (c.mono) 1 else if (c.choices.size > 6) 3 else 2, c.choices.size) { i ->
        val right = feedback && i == c.answer
        val wrong = feedback && i in picked && !right
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        val scale by
            animateFloatAsState(
                if (pressed && motion) .94f else 1f,
                if (motion) spring(stiffness = 750f) else snap(),
                label = "Respuesta",
            )
        val appear = remember(c) { Animatable(if (motion) 0f else 1f) }
        LaunchedEffect(c) {
            if (motion) {
                delay(i * 45L)
                appear.animateTo(1f, spring(dampingRatio = .7f, stiffness = 320f))
            }
        }
        val pop = remember(c) { Animatable(1f) }
        val shake = remember(c) { Animatable(0f) }
        LaunchedEffect(right, wrong) {
            if (motion) {
                if (right) {
                    pop.animateTo(if (c.mono) 1.03f else 1.08f, tween(110))
                    pop.animateTo(1f, spring(dampingRatio = .4f, stiffness = 500f))
                }
                if (wrong) for (x in listOf(12f, -10f, 7f, -4f, 0f)) shake.animateTo(x, tween(45))
            }
        }
        val colors = MaterialTheme.colorScheme
        val fill by
            animateColorAsState(
                if (right) colors.secondaryContainer
                else if (wrong) colors.errorContainer else colors.surface,
                if (motion) tween(160) else snap(),
                label = "Resultado",
            )
        val content =
            if (right) colors.onSecondaryContainer
            else if (wrong) colors.onErrorContainer else colors.onSurface
        OutlinedButton(
            { choose(i) },
            Modifier.fillMaxWidth()
                .heightIn(
                    min =
                        if (c.mono) 56.dp
                        else if (glyphs) 96.dp else if (c.choices.size <= 4) 100.dp else 80.dp
                )
                .drawBehind {
                    if (right)
                        drawRoundRect(
                            Color(0xFF2FCB7E)
                                .copy(
                                    alpha =
                                        .35f * pop.value.minus(.92f).coerceAtLeast(0f) * 6f + .15f
                                ),
                            topLeft = androidx.compose.ui.geometry.Offset(-8f, -8f),
                            size =
                                androidx.compose.ui.geometry.Size(
                                    size.width + 16f,
                                    size.height + 16f,
                                ),
                            cornerRadius =
                                androidx.compose.ui.geometry.CornerRadius(
                                    if (c.mono) 22.dp.toPx() else 30.dp.toPx()
                                ),
                        )
                }
                .graphicsLayer {
                    val k = scale * pop.value * (.85f + .15f * appear.value)
                    scaleX = k
                    scaleY = k
                    alpha = appear.value
                    translationY = (1f - appear.value) * 40f * density
                    translationX = shake.value * density
                },
            enabled = answering,
            shape = RoundedCornerShape(if (c.mono) 16.dp else 22.dp),
            interactionSource = source,
            contentPadding =
                if (c.mono) PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                else ButtonDefaults.ContentPadding,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = fill,
                    contentColor = colors.onSurface,
                    disabledContainerColor = fill,
                    disabledContentColor =
                        if (right) colors.onSecondaryContainer
                        else if (wrong) colors.onErrorContainer else colors.onSurfaceVariant,
                ),
            border =
                BorderStroke(
                    if (right || wrong) 2.dp else 1.dp,
                    if (right) colors.secondary
                    else if (wrong) colors.error else colors.outlineVariant,
                ),
        ) {
            if (glyphs)
                Glyph(
                    c.choices[i],
                    if (c.choices.size > 6) 50.dp else 56.dp,
                    if (answering) colors.onSurface else content,
                )
            else if (c.mono)
                Text(
                    c.choices[i],
                    Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Start,
                )
            else
                Text(
                    c.choices[i],
                    fontSize =
                        if (c.choices.size > 6) 30.sp
                        else if (c.choices.size == 2 && c.choices.any { it.length > 9 }) 22.sp
                        else 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            if (right) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = colors.secondary)
            }
        }
    }
}

/** Payload or snippet shown above the choices (e.g. the attack in Consulta Segura). */
@Composable
fun CodeBox(code: String) {
    Text(
        code,
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF11151E))
            .border(1.dp, areaColor(Area.NETSEC).copy(alpha = .4f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        color = Color(0xFFFFD27A),
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}

@Composable
fun ResultContent(r: ResultRow, hasNext: Boolean, next: () -> Unit, home: () -> Unit) {
    CompletionMark()
    Label("PARTIDA COMPLETADA", MaterialTheme.colorScheme.primary)
    Text("Bien jugado.", style = MaterialTheme.typography.displaySmall)
    Panel {
        Text("${r.correct} / ${r.total}", style = MaterialTheme.typography.displaySmall)
        Text("Respuestas correctas", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Primary("Volver al inicio", home)
}

/** Countdown state lives here so timer ticks do not recompose the board or choices. */
@Composable
fun RoundProgress(vm: BrainViewModel, run: Run, color: Color = MaterialTheme.colorScheme.primary) {
    var fraction by remember(run.id, run.index, run.nonce, run.phase) { mutableFloatStateOf(1f) }
    // Fase 2 boards keep their own clock (the chain, the tramo): no countdown bar, no timeout.
    val timed =
        run.phase == Phase.ANSWER && !vm.untimed(run.game) && run.challenge.kind !in BOARD_KINDS
    LaunchedEffect(run.id, run.index, run.nonce, run.phase, timed) {
        if (timed)
            while (isActive) {
                fraction = vm.answerFraction()
                if (fraction <= 0f) {
                    vm.timeout()
                    break
                }
                delay(50)
            }
    }
    val round by
        animateFloatAsState(
            (run.index + 1) / run.format.units.toFloat(),
            if (LocalMotionEnabled.current) spring(dampingRatio = .8f, stiffness = 120f)
            else snap(),
            label = "Ronda",
        )
    val barColor = if (timed && fraction < .25f) MaterialTheme.colorScheme.error else color
    LinearProgressIndicator(
        progress = { if (timed) fraction else round },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        color = barColor,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        drawStopIndicator = {},
    )
    if (run.phase == Phase.ANSWER && vm.untimed(run.game))
        Label("Sin cronómetro · piensa y comprueba", color)
}

/**
 * One dot per round: green right, red wrong, outlined pending. Fills the lower part of the screen
 * with useful context.
 */
@Composable
fun RoundDots(run: Run, endless: Boolean, color: Color) {
    if (endless) return
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until run.format.units) {
                val o = run.outcomes.getOrNull(i)
                val current = i == run.outcomes.size && run.phase != Phase.FEEDBACK
                val fill =
                    when {
                        o == null -> Color.Transparent
                        o.correct -> Color(0xFF2FCB7E)
                        else -> Color(0xFFFF6B6B)
                    }
                Box(
                    Modifier.size(if (current) 14.dp else 11.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(
                            2.dp,
                            if (o == null)
                                (if (current) color else MaterialTheme.colorScheme.outlineVariant)
                            else fill,
                            CircleShape,
                        )
                )
            }
        }
        Text(
            "${run.outcomes.count{it.correct}} aciertos de ${run.outcomes.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Lote C: ghost of the best 12-round run of this game, compared round by round with the current
 * one.
 */
@Composable
fun RunGhost(vm: BrainViewModel, run: Run) {
    val ghost =
        remember(run.game, run.id) {
            vm.results.value
                .filter { it.game == run.game && it.id != run.id }
                .maxByOrNull { it.correct }
                ?.let { row ->
                    runCatching {
                            val a = org.json.JSONArray(row.trials)
                            NearMiss.ghost(
                                (0 until a.length()).map {
                                    a.getJSONObject(it).getBoolean("correct")
                                }
                            )
                        }
                        .getOrNull()
                }
        } ?: return
    GhostBar(
        run.outcomes.count { it.correct },
        NearMiss.ghostAt(ghost, run.outcomes.size),
        run.format.units,
        "Fantasma del récord",
    )
}

/** Kinds whose board plays the whole round (Fase 2 and 3). */
internal val BOARD_KINDS =
    setOf(Kind.SUITCASE, Kind.VIGIL, Kind.DDOS, Kind.ROUTE, Kind.PAIRS, Kind.ORBIT)

/**
 * Record (longest suitcase chain ever, this run included) and the per-round logs of the record run
 * (the ghost).
 */
internal class BoardHistory(val record: Int, val ghost: List<String>)

@Composable
internal fun rememberBoardHistory(vm: BrainViewModel, run: Run): BoardHistory {
    val rows =
        remember(run.game, run.id) {
            vm.results.value.filter { it.game == run.game && it.id != run.id }
        }
    val best = remember(rows) { rows.maxByOrNull { it.correct }?.let(::boardLogs) ?: emptyList() }
    val past =
        remember(rows) {
            rows.flatMap(::boardLogs).mapNotNull { SuitcaseLog.decode(it)?.length }.maxOrNull() ?: 0
        }
    val now =
        vm.boardLogs(run).take(run.index).mapNotNull { SuitcaseLog.decode(it)?.length }.maxOrNull()
            ?: 0
    return BoardHistory(maxOf(past, now), best)
}
