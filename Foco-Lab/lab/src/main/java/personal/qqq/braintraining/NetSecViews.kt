package personal.qqq.braintraining

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ResultRow

private val Amber = Color(0xFFFFB300)
private val AmberInk = Color(0xFF2B1D00)

enum class NetMode(val label: String, val detail: String) {
    NORMAL(
        "Normal",
        "12 rondas con tiempo. Guarda tu resultado, sube de nivel y desbloquea el siguiente reto.",
    ),
    FLASH(
        "Relámpago",
        "60 segundos: responde todo lo que puedas. Los aciertos seguidos multiplican ×2 y ×3.",
    ),
    UNTIMED(
        "Sin tiempo",
        "Estudio sin cronómetro, con la solución explicada tras cada respuesta. No guarda resultados.",
    ),
}

/**
 * A game is open when it is the first one or the previous one has a saved result with at least
 * 8/12.
 */
fun netsecUnlocked(results: List<ResultRow>, id: String): Boolean {
    val i = NetSec.ids.indexOf(id)
    return i <= 0 || results.any { it.game == NetSec.ids[i - 1] && it.correct >= 8 }
}

private val ranks = listOf("Novato" to 0, "Técnico" to 100, "Analista" to 300, "Experto" to 700)

fun netsecRank(points: Int) = ranks.last { points >= it.second }

fun netsecNextRank(points: Int) = ranks.firstOrNull { it.second > points }

private fun clock(ms: Long) = "${ms/60000}:${"%02d".format(ms/1000%60)}"

@Composable
fun NetsecHomeCard(vm: BrainViewModel, open: () -> Unit) {
    val results by vm.results.collectAsStateWithLifecycle()
    val points = areaPoints(results, Area.NETSEC)
    val rank = netsecRank(points)
    val next = netsecNextRank(points)
    val c =
        remember(results) {
            vm.consistency(results.filter { Games.get(it.game).area == Area.NETSEC })
        }
    val fraction = next?.let { (points - rank.second).toFloat() / (it.second - rank.second) } ?: 1f
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2E2108), Color(0xFF141109))))
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Amber.copy(alpha = .35f), Color.Transparent),
                        Offset(size.width * .9f, 0f),
                        size.minDimension * .8f,
                    ),
                    size.minDimension * .8f,
                    Offset(size.width * .9f, 0f),
                )
            }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label("REDES Y SEGURIDAD", Color(0xFFFFD27A))
                Text(
                    "Rango: ${rank.first}",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
            }
            Box(
                Modifier.size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Amber.copy(alpha = .2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Shield, null, Modifier.size(26.dp), tint = Amber)
            }
        }
        LinearProgressIndicator(
            { fraction },
            Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = Amber,
            trackColor = Color.White.copy(alpha = .12f),
            drawStopIndicator = {},
        )
        Text(
            if (next != null) "$points puntos · ${next.second-points} para ${next.first}"
            else "$points puntos · rango máximo",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFE8DCC4),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Rounded.Spa,
                null,
                Modifier.size(20.dp),
                tint = Color(0xFF7CC6A4),
            )
            Text(
                "${c.practicedDays} ${if(c.practicedDays==1)"día practicado" else "días practicados"} · ${c.message()}",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
        // The daily mission now lives in "Misiones de hoy"; this card only opens the section.
        PrimaryButton(
            "Abrir Redes y seguridad",
            open,
            true,
            ButtonDefaults.buttonColors(containerColor = Amber, contentColor = AmberInk),
            Icons.Rounded.Shield,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetsecPage(
    vm: BrainViewModel,
    mode: NetMode,
    setMode: (NetMode) -> Unit,
    back: () -> Unit,
    open: (String) -> Unit,
) {
    val results by vm.results.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val points = areaPoints(results, Area.NETSEC)
    val feedback = LocalFeedback.current
    // Lock opening (Lote B): games unlocked since the last visit open their padlock once.
    // "netsec_seen" lists the ones already shown.
    val seenPref = prefs.firstOrNull { it.key == "netsec_seen" }?.value
    val unlockedIds = NetSec.ids.filter { netsecUnlocked(results, it) }
    val seen = seenPref?.split(",")?.filter { it.isNotEmpty() }?.toSet()
    val fresh = if (seen == null) emptySet() else unlockedIds.toSet() - seen - NetSec.ids.first()
    LaunchedEffect(seenPref == null, fresh) {
        if (seenPref == null) vm.pref("netsec_seen", unlockedIds.joinToString(","))
        else if (fresh.isNotEmpty()) {
            delay(350)
            feedback(FeedbackCue.UNLOCK)
            delay(900)
            vm.pref("netsec_seen", (seen.orEmpty() + fresh).joinToString(","))
        }
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Header("Redes y seguridad", "Rango ${netsecRank(points).first} · $points puntos", back)
        }
        item {
            Panel {
                Text("Modo de juego", style = MaterialTheme.typography.titleMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    NetMode.values().forEachIndexed { i, m ->
                        SegmentedButton(
                            mode == m,
                            { setMode(m) },
                            SegmentedButtonDefaults.itemShape(i, 3),
                            icon = {},
                        ) {
                            Text(m.label, maxLines = 1)
                        }
                    }
                }
                Text(
                    mode.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(NetSec.games, key = { _, g -> g.id }) { i, g ->
            val unlocked = netsecUnlocked(results, g.id)
            val best = results.filter { it.game == g.id }.maxByOrNull { it.correct }
            val flash = prefs.firstOrNull { it.key == "flash_best:${g.id}" }?.value?.toIntOrNull()
            val status =
                when {
                    !unlocked -> "Consigue 8/12 en ${NetSec.games[i-1].title} para desbloquear"
                    mode == NetMode.FLASH ->
                        flash?.let { "Récord relámpago: $it pts" } ?: "Sin récord relámpago"
                    best != null -> "Mejor: ${best.correct}/12 · nivel ${vm.level(g.id)}"
                    else -> "Sin jugar todavía · nivel ${vm.level(g.id)}"
                }
            Row(
                Modifier.fillMaxWidth()
                    .graphicsLayer { alpha = if (unlocked) 1f else .45f }
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                        RoundedCornerShape(22.dp),
                    )
                    .pressClick(enabled = unlocked) { open(g.id) }
                    .padding(12.dp)
                    .semantics(mergeDescendants = true) {
                        if (!unlocked) stateDescription = "Bloqueado"
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GameArt(g, Modifier.size(66.dp).clip(RoundedCornerShape(18.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "${i+1}. ${g.title}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        g.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        status,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (unlocked) Color(0xFFC88A00)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LockBadge(unlocked, g.id in fresh)
            }
        }
    }
}

/**
 * Play icon of a game row; a freshly unlocked game shows its padlock opening first (static with
 * animations off).
 */
@Composable
private fun LockBadge(unlocked: Boolean, fresh: Boolean) {
    val motion = LocalMotionEnabled.current
    val open = remember { Animatable(if (fresh && motion) 0f else 1f) }
    LaunchedEffect(fresh) {
        if (fresh && motion) {
            delay(350)
            open.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
    }
    if (!unlocked) {
        Icon(Icons.Rounded.Lock, "Bloqueado", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Box(contentAlignment = Alignment.Center) {
        val v = open.value
        if (v < 1f)
            Icon(
                Icons.Rounded.LockOpen,
                null,
                Modifier.graphicsLayer {
                    alpha = 1f - v
                    rotationZ = -25f * v
                    val k = 1f + .5f * v
                    scaleX = k
                    scaleY = k
                },
                tint = Amber,
            )
        Icon(
            Icons.Rounded.PlayArrow,
            "Jugar",
            Modifier.graphicsLayer {
                alpha = v
                val k = .5f + .5f * v
                scaleX = k
                scaleY = k
            },
            tint = Amber,
        )
    }
}

/**
 * Relámpago: 60 s of challenges; 3 hits in a row ×2, 6 in a row ×3. Best score kept in
 * "flash_best:<game>", every session in "arcade:flash:<game>". Subredes, Puertos, Anzuelo and Bits
 * keep their board ([Quick], Lote B); Anzuelo never repeats a bait while unseen ones remain.
 */
@Composable
fun FlashSession(vm: BrainViewModel, game: String, back: () -> Unit) {
    val g = Games.get(game)
    val clock = rememberSession(false)
    val feedback = LocalFeedback.current
    val motion = LocalMotionEnabled.current
    val level = remember { vm.level(game) }
    val seed = remember { System.nanoTime() }
    var index by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<List<Int>?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var combo by remember { mutableIntStateOf(0) }
    var bestCombo by remember { mutableIntStateOf(0) }
    var correct by remember { mutableIntStateOf(0) }
    var answered by remember { mutableIntStateOf(0) }
    val seen = remember { mutableListOf<String>() }
    val c =
        remember(index) {
            Quick.make(game, level, seed + index * 10007L, seen.toSet()).also { q ->
                q.inbox?.let { seen += it.text }
                q.firewall?.let { seen += it.key }
            }
        }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val key = "flash_best:$game"
    val record = remember { prefs.firstOrNull { it.key == key }?.value?.toIntOrNull() ?: 0 }
    val over = clock.seconds == 0
    // Lote C: points every 5 s (for the ghost of the next sessions), ghost of the best one, and the
    // end-of-session rewards.
    LaunchedEffect(Unit) { vm.clearNews() }
    val curve = remember { mutableListOf<Int>() }
    val ghost = remember { ghostCurve(vm, "flash:$game") }
    val elapsed = (SESSION_MS / 1000 - clock.seconds).coerceAtLeast(0)
    LaunchedEffect(elapsed) { while (curve.size < elapsed / GHOST_STEP_S) curve += score }
    val multiplier =
        when {
            combo >= 6 -> 3
            combo >= 3 -> 2
            else -> 1
        }
    LaunchedEffect(over) {
        if (over) {
            if (score > record) vm.pref(key, score.toString())
            while (curve.size < SESSION_MS / 1000 / GHOST_STEP_S) curve += score
            vm.saveArcade(
                "flash:$game",
                score,
                SESSION_MS.toLong(),
                mapOf(
                    "correct" to correct,
                    "answered" to answered,
                    "combo" to bestCombo,
                    "curve" to org.json.JSONArray(curve),
                ),
            )
            // Etapa 0 (Regla 4): el nivel guardado del juego, no el 1 por defecto.
            if (answered >= 3)
                vm.reward(
                    Play(ChestSource.FLASH, game, correct, bestCombo, score, level = vm.level(game))
                )
        }
    }
    // Boards need longer to read the correction (boundary, rows, clue) than an answer card.
    LaunchedEffect(picked) {
        val p = picked ?: return@LaunchedEffect
        delay(
            if (c.accepts(p)) if (c.kind == Kind.INBOX) 520 else 380
            else if (c.kind == Kind.CHOICE) 1100 else 1900
        )
        picked = null
        index++
    }
    val bump = remember { Animatable(1f) }
    LaunchedEffect(multiplier) {
        if (motion && multiplier > 1) {
            bump.animateTo(1.25f, tween(110))
            bump.animateTo(1f, spring(dampingRatio = .4f))
        }
    }
    fun choose(p: List<Int>) {
        if (picked != null || !clock.running || over) return
        picked = p
        answered++
        if (c.accepts(p)) {
            vm.unlockCard(game, c.explanation)
            combo++
            correct++
            bestCombo = maxOf(bestCombo, combo)
            score +=
                10 *
                    when {
                        combo >= 6 -> 3
                        combo >= 3 -> 2
                        else -> 1
                    }
            feedback(FeedbackCue.CORRECT)
        } else {
            combo = 0
            feedback(FeedbackCue.RETRY)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SessionHeader("${g.title} · Relámpago", clock, "$score pts", back)
        if (over) {
            SessionSummary(
                "$score",
                "puntos · $correct de $answered correctas · combo máximo $bestCombo\n" +
                    if (score > record) "Superas tu récord anterior: $record"
                    else "Récord: $record",
                back,
                record = score > record && score > 0,
                best = record,
                value = score,
            ) {
                PracticeRewards(vm)
            }
            return@Column
        }
        if (!clock.running) PausedBanner(clock.toggle)
        if (ghost.isNotEmpty())
            GhostBar(
                score,
                NearMiss.ghostAt(ghost, elapsed / GHOST_STEP_S),
                maxOf(ghost.last(), record, score, 1),
                "Fantasma del récord",
            )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatChip("Combo $combo", Icons.Rounded.LocalFireDepartment, Modifier.weight(1f))
            StatChip("$correct aciertos", Icons.Rounded.CheckCircle, Modifier.weight(1f))
            Text(
                "×$multiplier",
                Modifier.graphicsLayer {
                        scaleX = bump.value
                        scaleY = bump.value
                    }
                    .clip(CircleShape)
                    .background(
                        if (multiplier > 1) Amber else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                color =
                    if (multiplier > 1) AmberInk else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
            )
        }
        Text(
            c.prompt,
            Modifier.fillMaxWidth(),
            style =
                if (c.prompt.length > 40) MaterialTheme.typography.bodyLarge
                else MaterialTheme.typography.headlineMedium,
        )
        if (c.board.isNotEmpty()) CodeBox(c.board[0])
        QuickChallenge(c, picked == null && clock.running, picked != null, picked ?: emptyList()) {
            choose(it)
        }
        if (picked?.let { !c.accepts(it) } == true)
            Text(
                c.explanation.substringBefore("\n"),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge,
            )
    }
}

/** Story line, time vs. personal record and rank change for a finished NETSEC game. */
@Composable
fun NetsecResult(r: ResultRow, history: List<ResultRow>) {
    val before = history.filter { it.id != r.id }
    val ptsBefore = areaPoints(before, Area.NETSEC)
    val ptsAfter = ptsBefore + r.xp
    val rankBefore = netsecRank(ptsBefore).first
    val rankAfter = netsecRank(ptsAfter).first
    val record = before.filter { it.game == r.game && it.correct >= 8 }.minOfOrNull { it.activeMs }
    val nextGame =
        if (r.game != NetSec.ids.last() && r.correct >= 8)
            NetSec.games[NetSec.ids.indexOf(r.game) + 1].takeIf { !netsecUnlocked(before, it.id) }
        else null
    val rankUp = rankAfter != rankBefore
    // Rank up and unlock (Lote B): confetti and their own sound, after the completion chime.
    val feedback = LocalFeedback.current
    LaunchedEffect(r.id) {
        if (rankUp || nextGame != null) {
            delay(450)
            feedback(if (rankUp) FeedbackCue.LEVEL_UP else FeedbackCue.UNLOCK)
        }
    }
    Box {
        Panel {
            Text(
                "Aseguraste ${r.correct} de ${r.total} ${NetSec.noun(r.game)}",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFC88A00),
            )
            Text(
                "Tiempo ${clock(r.activeMs)} · " +
                    when {
                        r.correct >= 8 && (record == null || r.activeMs < record) ->
                            "¡nuevo récord personal!"
                        record != null -> "récord ${clock(record)}"
                        else -> "consigue 8/12 para fijar un récord"
                    },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (rankAfter != rankBefore) "¡Subiste a $rankAfter!"
                else "Rango: $rankAfter · $ptsAfter puntos",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (rankAfter != rankBefore) FontWeight.Bold else FontWeight.Normal,
            )
            if (nextGame != null)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val motion = LocalMotionEnabled.current
                    val turn = remember { Animatable(if (motion) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        if (motion) {
                            delay(450)
                            turn.animateTo(1f, spring(dampingRatio = .45f, stiffness = 300f))
                        }
                    }
                    Icon(
                        Icons.Rounded.LockOpen,
                        null,
                        Modifier.size(20.dp).graphicsLayer {
                            rotationZ = -30f * (1f - turn.value)
                            val k = .6f + .4f * turn.value
                            scaleX = k
                            scaleY = k
                        },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Desbloqueaste ${nextGame.title}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
        }
        CelebrationBurst(if (rankUp || nextGame != null) r.id else null, Modifier.matchParentSize())
    }
}

@Composable
fun ReviewPanel(mistakes: List<String>) {
    Panel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Lightbulb, null, tint = Amber)
            Text("Esto repasa hoy", style = MaterialTheme.typography.titleLarge)
        }
        mistakes.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider()
            Text(e, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
