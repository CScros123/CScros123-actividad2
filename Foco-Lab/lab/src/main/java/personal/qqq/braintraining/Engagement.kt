package personal.qqq.braintraining

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import personal.qqq.braintraining.core.*

/*
 * Lote C, enganche ético (docs/EXAMEN-JUEGOS.md, sección 6): cofre, misiones, álbum, "Otra vez" y fantasma del récord.
 * Nada de esto da minutos de ocio ni cambia los bloqueos de Foco. Todas las animaciones respetan LocalMotionEnabled.
 */

internal val ChestGold = Color(0xFFFFB300)
internal val ChestInk = Color(0xFF2B1D00)

/** Title and text of a reward, as the user reads it. */
internal fun describe(r: Reward): Pair<String, String> =
    when (r.kind) {
        RewardKind.POINTS ->
            "+${r.points} puntos" to "Puntos extra para tu nivel. Tu tiempo de ocio no cambia."
        RewardKind.CARD -> {
            val c = Album.get(r.card ?: "")
            "Carta nueva: ${c?.title ?: "concepto"}" to (c?.lesson ?: "")
        }
        RewardKind.COSMETIC -> {
            val c = Rewards.cosmetic(r.cosmetic)
            (c?.title ?: "Aspecto nuevo") to
                (if (c?.theme == true)
                    "Nuevo tema para Parejas de incidentes: solo cambia el aspecto, nunca la dificultad. Puedes cambiarlo en el Álbum."
                else if (c?.skin == true)
                    "Ya la llevas en la Ruta espacial. Puedes cambiarla en el Álbum."
                else "Nuevo color para tu inicio. Puedes cambiarlo en el Álbum.")
        }
        RewardKind.PHRASE -> "Frase rara" to "«${Rewards.phrases.getOrElse(r.phrase) { "" }}»"
    }

/** Earned, unopened chests and the open button. Shows nothing when there is no chest. */
@Composable
internal fun ChestPanel(vm: BrainViewModel, autoOpen: Boolean = false) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val pending = remember(prefs) { vm.wallet.pending.size }
    var opening by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<Reward?>(null) }
    fun open() {
        if (opening) return
        opening = true
        reward = null
        vm.openChest {
            reward = it
            if (it == null) opening = false
        }
    }
    var auto by remember { mutableStateOf(autoOpen) }
    LaunchedEffect(auto, pending) {
        if (auto && pending > 0 && reward == null && !opening) {
            auto = false
            open()
        }
    }
    if (reward != null) {
        ChestReveal(reward!!) {
            reward = null
            opening = false
        }
        return
    }
    if (pending == 0) return
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3A2A00), Color(0xFF1E1606))))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("🎁", style = MaterialTheme.typography.headlineMedium)
        Column(Modifier.weight(1f)) {
            Text(
                if (pending == 1) "Tienes un cofre" else "Tienes $pending cofres",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Lo ganaste practicando. Dentro: puntos, una carta, un aspecto o una frase.",
                color = Color(0xFFE8D9B0),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Button(
            { open() },
            enabled = !opening,
            colors =
                ButtonDefaults.buttonColors(containerColor = ChestGold, contentColor = ChestInk),
        ) {
            Text("Abrir")
        }
    }
}

/**
 * The chest shakes, opens and the reward card flips into view, with confetti (static with
 * animations off).
 */
@Composable
internal fun ChestReveal(reward: Reward, done: () -> Unit) {
    val motion = LocalMotionEnabled.current
    val shake = remember { Animatable(0f) }
    val flip = remember { Animatable(if (motion) 0f else 1f) }
    var shown by remember { mutableStateOf(!motion) }
    LaunchedEffect(reward) {
        if (motion) {
            for (x in listOf(8f, -8f, 6f, -6f, 3f, 0f)) shake.animateTo(x, tween(60))
            shown = true
            flip.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    val (title, text) = describe(reward)
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF2A1F05), Color(0xFF15110A))))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!shown)
                Text(
                    "🎁",
                    Modifier.graphicsLayer {
                        translationX = shake.value * density
                        rotationZ = shake.value
                    },
                    style = MaterialTheme.typography.displayMedium,
                )
            else
                Column(
                    Modifier.fillMaxWidth()
                        .graphicsLayer {
                            rotationY = (1f - flip.value) * 90f
                            cameraDistance = 12f * density
                            alpha = .3f + .7f * flip.value
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(ChestGold)
                        .padding(18.dp)
                        .semantics { contentDescription = "$title. $text" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        when (reward.kind) {
                            RewardKind.POINTS -> Icons.Rounded.Stars
                            RewardKind.CARD -> Icons.Rounded.Style
                            RewardKind.COSMETIC -> Icons.Rounded.Palette
                            RewardKind.PHRASE -> Icons.Rounded.FormatQuote
                        },
                        null,
                        Modifier.size(36.dp),
                        tint = ChestInk,
                    )
                    Text(
                        title,
                        color = ChestInk,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text,
                        color = ChestInk,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            if (shown) TextButton(done) { Text("Guardar", color = Color.White) }
        }
        CelebrationBurst(if (shown) reward else null, Modifier.matchParentSize())
    }
}

/**
 * Under every finished practice: missions just completed, the chest earned (or the daily cap) and
 * new album cards. Celebrates a completed mission and album milestones with confetti.
 */
@Composable
internal fun PracticeRewards(vm: BrainViewModel) {
    val news = vm.news
    val cards = vm.newCards
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            news?.missionsDone?.forEach { m ->
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF1EDB90).copy(alpha = .16f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Rounded.Flag, null, tint = Color(0xFF1EDB90))
                    Text(
                        "Misión cumplida: ${m.title}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            if (news?.allMissions == true)
                Text(
                    "Las 3 misiones de hoy, hechas. Buen trabajo.",
                    color = Color(0xFF1EDB90),
                    style = MaterialTheme.typography.titleSmall,
                )
            if (cards.isNotEmpty())
                Text(
                    (if (cards.size == 1) "1 carta nueva" else "${cards.size} cartas nuevas") +
                        " en tu álbum: " +
                        cards.take(3).joinToString(" · ") { it.title },
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            vm.milestone?.let {
                Text(
                    "¡Álbum: $it de ${Album.cards.size} cartas!",
                    color = ChestGold,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
        CelebrationBurst(
            news?.takeIf { it.missionsDone.isNotEmpty() } ?: vm.milestone,
            Modifier.matchParentSize(),
        )
    }
}

/**
 * "Otra vez" after a near miss (1 short of the record), at most [NearMiss.MAX_AGAIN] times in a
 * row.
 */
@Composable
internal fun AgainOffer(score: Int, best: Int?, againInRow: Int, unit: String, again: () -> Unit) {
    // Desactivado: "¡a 1 de tu récord, otra vez!" es el truco del casi-acierto de las tragaperras.
    // Terminar una partida es un buen final; volver a jugar queda en el menú, sin empujones.
    if (true) return
    if (best == null || !NearMiss.isNear(score, best)) return
    val left = best - score
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "¡A $left $unit de tu récord ($best)!",
            color = ChestGold,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (NearMiss.offerAgain(score, best, againInRow))
            OutlinedButton(again, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Replay, null)
                Spacer(Modifier.width(8.dp))
                Text("Otra vez")
            }
        else
            Text(
                "Ya lo has intentado de nuevo. Buen momento para terminar por hoy: mañana saldrá mejor.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
    }
}

/** Current progress against the ghost of the personal best at the same point (a faint marker). */
@Composable
internal fun GhostBar(current: Int, ghost: Int, max: Int, label: String) {
    if (max <= 0) return
    val motion = LocalMotionEnabled.current
    val c by
        animateFloatAsState(
            (current.toFloat() / max).coerceIn(0f, 1f),
            if (motion) tween(260) else snap(),
            label = "Actual",
        )
    val g by
        animateFloatAsState(
            (ghost.toFloat() / max).coerceIn(0f, 1f),
            if (motion) tween(260) else snap(),
            label = "Fantasma",
        )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val ghostColor = MaterialTheme.colorScheme.outline
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(14.dp).semantics {
                contentDescription = "Llevas $current; tu partida habitual iba por $ghost"
            }
        ) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = r)
            drawRoundRect(
                ghostColor.copy(alpha = .28f),
                size = Size(size.width * g, size.height),
                cornerRadius = r,
            )
            drawRoundRect(fill, size = Size(size.width * c, size.height), cornerRadius = r)
            val x = size.width * g
            drawLine(ghostColor, Offset(x, -2f), Offset(x, size.height + 2f), 3.dp.toPx())
        }
        Text(
            when {
                current > ghost -> "$label: por encima de tu partida habitual (+${current - ghost})"
                current == ghost -> "$label: en tu ritmo habitual"
                else -> "$label: a tu ritmo; todo lo que aciertes suma"
            },
            style = MaterialTheme.typography.labelMedium,
            color =
                if (current >= ghost) Color(0xFF2FCB7E)
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Misiones de hoy (replaces the plain plan card): 3 missions with progress; completing all gives a
 * chest.
 */
@Composable
fun DailyTrainingCard(vm: BrainViewModel, open: (DailyMission) -> Unit) {
    val results by vm.results.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val missions = remember(results, prefs) { vm.missionsToday(results) }
    val progress = remember(prefs) { vm.missionProgress() }
    val all = progress.allDone(missions)
    Box(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B2347), Color(0xFF111528))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label("MISIONES DE HOY", Color(0xFFB8C2D8))
                    Text(
                        "${missions.count { progress.done(it) }} de ${missions.size} cumplidas",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
                if (all)
                    Icon(Icons.Rounded.CheckCircle, "Misiones cumplidas", tint = Color(0xFF2FCB7E))
                else Text("🎁", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                if (all) "Hecho por hoy. Mañana habrá tres misiones nuevas."
                else "Tres retos cortos de tus juegos. Cúmplelos todos y ganas un cofre.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC5CEDF),
            )
            missions.forEach { m ->
                val done = progress.done(m)
                val c =
                    if (m.kind == MissionKind.SHORT) Color(0xFF1EDB90)
                    else areaColor(Games.get(m.game).area)
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .pressClick(enabled = !done) { open(m) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(c.copy(alpha = .22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (done) Icons.Rounded.Check
                            else
                                when (m.kind) {
                                    MissionKind.STREAK -> Icons.Rounded.LocalFireDepartment
                                    MissionKind.SCORE -> Icons.Rounded.Flag
                                    MissionKind.FLASH -> Icons.Rounded.Bolt
                                    MissionKind.SHORT -> Icons.Rounded.Timer
                                    MissionKind.CHAIN -> Icons.Rounded.Luggage
                                    MissionKind.CLEAN -> Icons.Rounded.Shield
                                },
                            null,
                            Modifier.size(19.dp),
                            tint = c,
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            m.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        LinearProgressIndicator(
                            { progress.value(m).toFloat() / m.target },
                            Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                            color = c,
                            trackColor = Color.White.copy(alpha = .12f),
                            drawStopIndicator = {},
                        )
                    }
                    Text(
                        "${progress.value(m)}/${m.target}",
                        color = Color.White.copy(alpha = .6f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (!all)
                PrimaryButton(
                    "Empezar misión",
                    { missions.firstOrNull { !progress.done(it) }?.let(open) },
                    true,
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1EDB90),
                        contentColor = Color(0xFF062D1E),
                    ),
                    Icons.Rounded.PlayArrow,
                )
        }
        CelebrationBurst(if (all) "misiones" else null, Modifier.matchParentSize())
    }
}

/** Home entry to the album. */
@Composable
internal fun AlbumEntry(vm: BrainViewModel, open: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val owned = remember(prefs) { vm.wallet.cards.size }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                RoundedCornerShape(24.dp),
            )
            .pressClick(onClick = open)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(ChestGold, Color(0xFFD08A00)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Style, null, tint = ChestInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Álbum de conceptos", style = MaterialTheme.typography.titleMedium)
            Text(
                "$owned de ${Album.cards.size} cartas. Se ganan acertando en Redes y en los cofres.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                { owned.toFloat() / Album.cards.size },
                Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                color = ChestGold,
                drawStopIndicator = {},
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Album: every card, locked ones as "?"; an unlocked card flips to show its one-line lesson. Also
 * the cosmetics and phrases.
 */
@Composable
internal fun AlbumPage(vm: BrainViewModel, back: () -> Unit) {
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val w = remember(prefs) { vm.wallet }
    LazyVerticalGrid(
        GridCells.Adaptive(150.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Header("Álbum", "${w.cards.size} de ${Album.cards.size} cartas", back)
        }
        item(span = { GridItemSpan(maxLineSpan) }) { ChestPanel(vm) }
        item(span = { GridItemSpan(maxLineSpan) }) { Collection(vm, w) }
        Album.games.forEach { game ->
            val cards = Album.cards.filter { it.game == game }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Label(
                    "${Games.get(game).title} · ${cards.count { it.key in w.cards }}/${cards.size}"
                )
            }
            items(cards, key = { it.key }) { c -> AlbumTile(c, c.key in w.cards) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Collection(vm: BrainViewModel, w: Wallet) {
    Panel {
        Text("Aspecto (libre, sin ganar nada)", style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Rewards.cosmetics.forEach { c ->
                val on = c.id == w.skin || c.id == w.accent || c.id == w.theme
                FilterChip(
                    on,
                    { vm.equip(c.id) },
                    label = { Text(c.title.substringAfter(' ')) },
                    leadingIcon = {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color)))
                    },
                )
            }
        }
        Rewards.phrases
            .filterIndexed { i, _ -> i in w.phrases }
            .forEach {
                Text(
                    "«$it»",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
    }
}

@Composable
private fun AlbumTile(card: AlbumCard, owned: Boolean) {
    val motion = LocalMotionEnabled.current
    var front by remember { mutableStateOf(true) }
    val turn by
        animateFloatAsState(
            if (front) 0f else 180f,
            if (motion) tween(420, easing = FastOutSlowInEasing) else snap(),
            label = "Carta",
        )
    val color = areaColor(Area.NETSEC)
    Box(
        Modifier.fillMaxWidth()
            .height(150.dp)
            .graphicsLayer {
                rotationY = turn
                cameraDistance = 12f * density
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (!owned) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)
                else if (turn <= 90f) color.copy(alpha = .18f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (owned) color.copy(alpha = .6f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(18.dp),
            )
            .pressClick(enabled = owned) { front = !front }
            .padding(12.dp)
            .semantics {
                contentDescription =
                    if (!owned) "Carta bloqueada"
                    else if (front) "${card.title}. Toca para girar" else card.lesson
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            !owned ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.Lock,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            turn <= 90f ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Style, null, tint = color)
                    Text(
                        card.title,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 3,
                    )
                }
            else ->
                Text(
                    card.lesson,
                    Modifier.graphicsLayer { rotationY = 180f }
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall,
                )
        }
    }
}

/**
 * Opened from Foco's pause (LabLaunch.chest): the chest, then the 3-minute practice or the end.
 * Never the catalog.
 */
@Composable
fun ChestScreen(vm: BrainViewModel, practice: () -> Unit, close: () -> Unit) {
    BackHandler { close() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Header("Tu cofre", "Lo ganaste practicando", close)
                if (!vm.ready) {
                    CircularProgressIndicator()
                    return@Column
                }
                val prefs by vm.preferences.collectAsStateWithLifecycle()
                ChestPanel(vm, autoOpen = true)
                if (remember(prefs) { vm.wallet.pending.isEmpty() })
                    Text(
                        "Abrir un cofre no cambia tu tiempo de ocio ni tus bloqueos.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                PrimaryButton("Practicar 3 min", practice, true, icon = Icons.Rounded.PlayArrow)
                TextButton(close, Modifier.fillMaxWidth()) { Text("Terminar por hoy") }
            }
        }
    }
}

/** Relámpago ghost: points every 5 s of the best session ("curve" in its arcade entry). */
internal const val GHOST_STEP_S = 5

internal fun ghostCurve(vm: BrainViewModel, game: String): List<Int> {
    // Línea base propia: la partida mediana, no el récord (compararte con tu mejor día cansa).
    val runs = vm.arcadeHistory(game).sortedBy { it.optInt("score") }
    val best = runs.getOrNull(runs.size / 2) ?: return emptyList()
    val a = best.optJSONArray("curve") ?: return emptyList()
    return (0 until a.length()).map { a.optInt(it) }
}
