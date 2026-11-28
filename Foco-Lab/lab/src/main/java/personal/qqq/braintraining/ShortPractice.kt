package personal.qqq.braintraining

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.json.JSONObject
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.ProgressRow
import personal.qqq.braintraining.data.ResultRow

/** One planned round: game and its saved adaptive level. */
internal data class ShortRound(val game: String, val level: Int)

/**
 * Mission game first (same rule as "Misión de hoy"), then the other unlocked NETSEC games, each at
 * its saved level.
 */
internal fun shortPlan(rows: List<ResultRow>, progress: List<ProgressRow>): List<ShortRound> {
    val unlocked = NetSec.ids.filter { netsecUnlocked(rows, it) }
    val correct =
        NetSec.ids
            .mapNotNull { id -> rows.firstOrNull { it.game == id }?.let { id to it.correct } }
            .toMap()
    val ended =
        NetSec.ids
            .mapNotNull { id -> rows.firstOrNull { it.game == id }?.let { id to it.ended } }
            .toMap()
    val first = ShortSession.mission(unlocked, correct, ended)
    return ShortSession.plan(first, unlocked).map { id ->
        ShortRound(id, (progress.firstOrNull { it.game == id }?.level ?: 1).coerceIn(1, 10))
    }
}

/**
 * Fase 2: práctica corta abierta desde Foco. Hasta 6 retos o 3 minutos activos (ShortSession), con
 * las mismas tarjetas de respuesta y explicación que el resto de Lab; Subredes, Puertos, Anzuelo y
 * Bits usan su tablero ([Quick], Lote B). No guarda un resultado de 12 rondas ni cambia el nivel;
 * solo deja un resumen de mejor esfuerzo en la preferencia "short_session:last". No da minutos de
 * ocio.
 */
@Composable
fun ShortPracticeScreen(vm: BrainViewModel, close: () -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { p ->
        Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) { ShortPracticeContent(vm, close) }
        }
    }
}

@Composable
private fun ShortPracticeContent(vm: BrainViewModel, close: () -> Unit) {
    val feedback = LocalFeedback.current
    var plan by remember { mutableStateOf<List<ShortRound>?>(null) }
    LaunchedEffect(Unit) {
        plan =
            try {
                shortPlan(vm.store.dao.allResults(), vm.store.dao.allProgress())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ShortSession.plan(NetSec.ids.first(), emptyList()).map { ShortRound(it, 1) }
            }
    }
    var session by remember { mutableStateOf(ShortSession()) }
    LaunchedEffect(Unit) { vm.clearNews() }
    var shown by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<List<Int>?>(null) }
    val seen = remember { mutableListOf<String>() }
    var summary by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(true) }
    val seed = remember { System.nanoTime() }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) running = false }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    BackHandler { close() }
    // Active time only: the clock stops while paused or in the background.
    LaunchedEffect(running, plan != null, summary) {
        if (running && plan != null && !summary) {
            var previous = SystemClock.elapsedRealtime()
            while (!session.done) {
                delay(250)
                val now = SystemClock.elapsedRealtime()
                session = session.tick(now - previous)
                previous = now
            }
        }
    }
    LaunchedEffect(session.timeUp) { if (session.timeUp) summary = true }
    val rounds = plan
    val clock =
        SessionClock(
            running,
            { if (!summary && !session.done) running = !running },
            ((session.remainingMs + 999) / 1000).toInt(),
            session.remainingMs / ShortSession.MAX_ACTIVE_MS.toFloat(),
        )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SessionHeader("Práctica · 3 min", clock, "${session.correct}/${session.answered}", close)
        if (rounds == null) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        if (summary) {
            ShortSummary(vm, session, rounds, close)
            return@Column
        }
        if (!running) PausedBanner { running = true }
        val round = rounds[shown.coerceIn(0, rounds.size - 1)]
        val g = Games.get(round.game)
        val c =
            remember(shown, round) {
                Quick.make(round.game, round.level, seed + shown * 10007L, seen.toSet()).also { q ->
                    q.inbox?.let { seen += it.text }
                    q.firewall?.let { seen += it.key }
                }
            }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatChip(
                "Reto ${shown+1} de ${ShortSession.MAX_CHALLENGES}",
                Icons.Rounded.Shield,
                Modifier.weight(1f),
            )
            StatChip(
                "${g.title} · nivel ${round.level}",
                Icons.Rounded.Lightbulb,
                Modifier.weight(1f),
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
        QuickChallenge(c, picked == null && running, picked != null, picked ?: emptyList()) { p ->
            if (picked != null || !running || session.done) return@QuickChallenge
            picked = p
            val ok = c.accepts(p)
            session = session.answer(ok)
            if (ok) vm.unlockCard(round.game, c.explanation)
            feedback(if (ok) FeedbackCue.CORRECT else FeedbackCue.RETRY)
        }
        val p = picked
        if (p != null) {
            val ok = c.accepts(p)
            Panel {
                Text(
                    if (ok) "Correcto" else "Casi",
                    style = MaterialTheme.typography.titleLarge,
                    color =
                        if (ok) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                )
                Text(c.explanation, style = MaterialTheme.typography.bodyMedium)
            }
            Primary(
                if (session.done) "Ver resumen" else "Siguiente reto",
                {
                    if (session.done) summary = true
                    else {
                        shown = session.answered
                        picked = null
                    }
                },
            )
        }
    }
}

@Composable
private fun ShortSummary(
    vm: BrainViewModel,
    s: ShortSession,
    rounds: List<ShortRound>,
    close: () -> Unit,
) {
    val feedback = LocalFeedback.current
    LaunchedEffect(Unit) {
        feedback(FeedbackCue.COMPLETE)
        // Lote C: finishing the practice counts for the missions and can earn a chest (never
        // leisure minutes).
        // Etapa 0 (Regla 4): antes no se mandaba nivel y Play usaba 1 por defecto. Ahora va el
        // nivel más bajo de las rondas jugadas (si hay duda, se paga menos: Foco solo endurece).
        val level = rounds.take(s.answered.coerceAtLeast(1)).minOfOrNull { it.level } ?: 1
        if (s.answered >= 3)
            vm.reward(Play(ChestSource.SHORT, "short", s.correct, 0, s.correct, level = level))
        // Best effort: a lost write never affects Foco or the blocking.
        runCatching {
            vm.pref(
                "short_session:last",
                JSONObject()
                    .put("ended", System.currentTimeMillis())
                    .put("answered", s.answered)
                    .put("correct", s.correct)
                    .put("activeMs", s.activeMs)
                    .put("game", rounds.first().game)
                    .put("level", rounds.first().level)
                    .toString(),
            )
        }
    }
    Panel {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CompletionMark()
            Text(
                if (s.timeUp) "Tiempo terminado" else "Práctica terminada",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "${s.correct} de ${s.answered}",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "Redes y seguridad · ${Games.get(rounds.first().game).title} · ${s.activeMs/1000} s activos",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Tu tiempo de ocio no cambia: practicar no suma minutos. Para subir de nivel juega 12 rondas completas en Lab.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PracticeRewards(vm)
        Primary("Volver", close)
    }
}
