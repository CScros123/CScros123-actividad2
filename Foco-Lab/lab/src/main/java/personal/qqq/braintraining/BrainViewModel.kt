package personal.qqq.braintraining

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.*
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.*
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.*

enum class Phase {
    INTRO,
    OBSERVE,
    WAIT,
    ANSWER,
    FEEDBACK,
    PAUSED,
    SAVING,
    RESULT,
}

/**
 * [item] and [format] (Fase 7) identify the pilot challenge played, for the variety rule and spaced
 * repetition; null/−1 for other games. [detail] (Fase 2) is the log a self-contained board leaves
 * (SuitcaseLog / VigilLog encoded), empty otherwise.
 */
data class Outcome(
    val correct: Boolean,
    val ms: Long?,
    val active: Long,
    val ended: Long,
    val reason: String,
    val responseMs: Long? = null,
    val mode: String = "unknown",
    val item: Long? = null,
    val format: Int = -1,
    val detail: String = "",
) {
    fun json() =
        JSONObject()
            .put("correct", correct)
            .put("ms", ms ?: JSONObject.NULL)
            .put("active", active)
            .put("ended", ended)
            .put("reason", reason)
            .put("responseMs", responseMs ?: JSONObject.NULL)
            .put("mode", mode)
            .apply {
                if (item != null) {
                    put("item", item)
                    put("format", format)
                }
                if (detail.isNotEmpty()) put("detail", detail)
            }

    fun mark() = item?.let { RoundMark(it, format, correct) }
}

/**
 * [history] holds the rounds played before this one (pilot games only), fixed when the round starts
 * so the challenge does not change during feedback.
 */
data class Run(
    val id: String,
    val game: String,
    val level: Int,
    val seed: Long,
    val index: Int = 0,
    val nonce: Int = 0,
    val phase: Phase = Phase.INTRO,
    val outcomes: List<Outcome> = emptyList(),
    val picked: List<Int> = emptyList(),
    val remaining: List<String> = emptyList(),
    val history: List<RoundMark> = emptyList(),
    val variant: String = "",
) {
    /**
     * [variant]: the mode chosen on the intro screen (Órbitas 2: "" clásico, "mirror" Espejo,
     * "expand" Órbita expansiva).
     */
    val challenge by lazy {
        if (game == Orbit.ID)
            Orbit.challenge(
                level,
                seed + index * 10007L + nonce * 999983L,
                index,
                OrbitMode.of(variant),
            )
        else Rounds.make(game, level, seed + index * 10007L + nonce * 999983L, history, index)
    }
    /** Paso 2: how many units this partida has and whether they are chained (see [GameFormat]). */
    val format
        get() = Formats.of(game)

    fun json() =
        JSONObject()
            .put("id", id)
            .put("game", game)
            .put("level", level)
            .put("seed", seed)
            .put("index", index)
            .put("nonce", nonce)
            .put("outcomes", JSONArray().apply { outcomes.forEach { put(it.json()) } })
            .put("remaining", JSONArray(remaining))
            .apply { if (variant.isNotEmpty()) put("variant", variant) }
            .toString()
}

const val ARCADE_HISTORY = 30

class BrainViewModel(app: Application) : AndroidViewModel(app) {
    private val feedbackEvents = MutableSharedFlow<FeedbackCue>(extraBufferCapacity = 16)
    val feedback = feedbackEvents.asSharedFlow()
    val store = Store(app)

    override fun onCleared() {
        val job = viewModelScope.coroutineContext[Job]
        super.onCleared()
        CoroutineScope(Dispatchers.IO).launch {
            job?.join()
            store.close()
        }
    }

    val results = store.dao.results().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val progress = store.dao.progress().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val preferences =
        store.dao.preferences().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val goals = store.dao.goals().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    var run by mutableStateOf<Run?>(null)
        private set

    var result by mutableStateOf<ResultRow?>(null)
        private set

    var ready by mutableStateOf(false)
        private set

    var saveFailed by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
    private var answerStart: Long? = null
    private var activeStart: Long? = null
    private val writes = Mutex()
    val learningMode
        get() = preferences.value.firstOrNull { it.key == "learning" }?.value != "false"

    var endless by mutableStateOf(false)
        private set

    var endlessRound by mutableIntStateOf(0)
        private set

    fun untimed(game: String) = endless

    /** Explanations of the rounds failed in the current NETSEC run, for "Esto repasa hoy". */
    var mistakes by mutableStateOf<List<String>>(emptyList())
        private set

    val dark
        get() = preferences.value.firstOrNull { it.key == "dark" }?.value == "true"

    val goal
        get() = preferences.value.firstOrNull { it.key == "goal" }?.value?.toIntOrNull() ?: 8

    val zone
        get() =
            runCatching {
                    ZoneId.of(
                        preferences.value.firstOrNull { it.key == "zone" }?.value
                            ?: ZoneId.systemDefault().id
                    )
                }
                .getOrDefault(ZoneId.systemDefault())

    private val eventDate = MutableStateFlow(LocalDate.now(zone))

    fun refreshEventDate() {
        eventDate.value = LocalDate.now(zone)
    }

    init {
        viewModelScope.launch {
            try {
                if (store.dao.allPreferences().none { it.key == "zone" })
                    store.dao.putPreference(PreferenceRow("zone", ZoneId.systemDefault().id))
                store.ensureEvents()
                store.migrateRoute()
                refreshEventDate()
                store.dao.draft()?.let { row ->
                    val o = JSONObject(row.json)
                    val a = o.getJSONArray("outcomes")
                    val outcomes =
                        (0 until a.length()).map {
                            val t = a.getJSONObject(it)
                            Outcome(
                                t.getBoolean("correct"),
                                if (t.isNull("ms")) null else t.getLong("ms"),
                                t.getLong("active"),
                                t.getLong("ended"),
                                t.getString("reason"),
                                if (t.isNull("responseMs")) null else t.getLong("responseMs"),
                                t.optString("mode", "unknown"),
                                if (t.has("item") && !t.isNull("item")) t.getLong("item") else null,
                                t.optInt("format", -1),
                                t.optString("detail", ""),
                            )
                        }
                    val rem = o.getJSONArray("remaining")
                    val units = Formats.of(o.getString("game")).units
                    // A partida paused before its game changed format (12 waves, now 5) can be
                    // neither continued nor saved: it is dropped.
                    if (outcomes.size > units) {
                        writes.withLock { store.dao.deleteDraft() }
                        return@let
                    }
                    val phase = if (outcomes.size >= units) Phase.SAVING else Phase.PAUSED
                    run =
                        Run(
                            o.getString("id"),
                            o.getString("game"),
                            o.getInt("level"),
                            o.getLong("seed"),
                            outcomes.size,
                            o.optInt("nonce") + 1,
                            phase,
                            outcomes,
                            remaining = (0 until rem.length()).map { rem.getString(it) },
                            history = outcomes.mapNotNull { it.mark() },
                            variant = o.optString("variant", ""),
                        )
                    if (phase == Phase.SAVING) finish()
                }
                syncChest()
            } catch (e: Exception) {
                message = "No se pudo recuperar la sesión. Puedes iniciar una nueva."
            } finally {
                ready = true
            }
        }
    }

    fun pref(key: String, value: String) {
        viewModelScope.launch {
            try {
                writes.withLock { store.dao.putPreference(PreferenceRow(key, value)) }
            } catch (e: Exception) {
                message = "No se pudo guardar el ajuste."
            }
        }
    }

    fun level(game: String) = progress.value.firstOrNull { it.game == game }?.level ?: 1

    fun start(game: String, remaining: List<String> = emptyList(), endlessMode: Boolean = false) {
        if (!ready || run != null) return
        result = null
        answerStart = null
        activeStart = null
        saveFailed = false
        endless = endlessMode
        endlessRound = 0
        mistakes = emptyList()
        clearNews()
        againInRow = 0
        if (planSize == 0) planSize = remaining.size + 1
        run =
            Run(
                UUID.randomUUID().toString(),
                game,
                level(game),
                System.nanoTime(),
                remaining = remaining,
                variant = if (game == Orbit.ID) orbitMode.key else "",
            )
    }

    /**
     * Órbitas 2: the last mode chosen (pref "orbit:mode"); a guided plan or a mission always plays
     * it in that mode.
     */
    val orbitMode
        get() = OrbitMode.of(preferences.value.firstOrNull { it.key == "orbit:mode" }?.value)

    /** Mode picked on the intro screen of Órbitas, before "Empezar". */
    fun chooseOrbitMode(mode: OrbitMode) {
        val r = run ?: return
        if (r.game != Orbit.ID || r.phase != Phase.INTRO) return
        run = r.copy(variant = mode.key)
        pref("orbit:mode", mode.key)
    }

    /** Size of the current guided plan (1 for a single exercise). */
    var planSize by mutableIntStateOf(0)
        private set

    /** The previous result of the game just finished, for "better than last time". */
    var previous by mutableStateOf<ResultRow?>(null)
        private set

    /** Accuracy (0..1) per area over the last 10 results of that area; null when never played. */
    fun areaAccuracy(area: Area): Float? {
        val rows = results.value.filter { Games.get(it.game).area == area }.take(10)
        return if (rows.isEmpty()) null
        else rows.sumOf { it.correct }.toFloat() / rows.sumOf { it.total }
    }

    /**
     * Misiones de hoy: the Redes mission ([mission]) and then the two least recently played visible
     * Mente games (never played first).
     */
    fun dailyPlan(): List<String> {
        val rows = results.value
        val mind =
            MIND_GAMES.sortedBy { g -> rows.firstOrNull { it.game == g.id }?.ended ?: 0L }
                .take(2)
                .map { it.id }
        return energy.trim(listOf(mission()) + mind)
    }

    /**
     * Misión de hoy: among unlocked NETSEC games, the one with the lowest last accuracy (never
     * played first), then the least recent.
     */
    fun mission(): String {
        val rows = results.value
        return NetSec.ids
            .filter { netsecUnlocked(rows, it) }
            .minWith(
                compareBy<String> { id -> rows.firstOrNull { it.game == id }?.correct ?: -1 }
                    .thenBy { id -> rows.firstOrNull { it.game == id }?.ended ?: 0L }
            )
    }

    fun daily() {
        if (run != null) return
        val plan = dailyPlan()
        planSize = plan.size
        start(plan.first(), plan.drop(1))
    }

    /**
     * Constancia sin castigo (sustituye a la racha de días seguidos, que caía a 0 tras descansar):
     * días practicados en total, esta semana y la media propia de las semanas anteriores.
     */
    fun consistency(rows: List<ResultRow> = results.value): Consistency.Summary =
        Consistency.summarize(rows.map { LabDay.of(it.ended, zone) }, LabDay.today(zone))

    /**
     * Saves the run in progress to the draft table so it survives process death (restored in init
     * as a paused run). Writes go through [writes] in call order, so abandon/finish never race with
     * an older draft. Endless runs are not saved.
     */
    private fun persist() {
        val r = run ?: return
        if (endless || r.phase == Phase.RESULT) return
        val json = r.json()
        viewModelScope.launch {
            try {
                writes.withLock { store.dao.putDraft(DraftRow(json = json)) }
            } catch (e: Exception) {
                /* best effort: the run keeps going in memory */
            }
        }
    }

    /** "Mostrar ejercicios clásicos" (Ajustes): hidden games appear again in Mente. */
    val showClassic
        get() = preferences.value.firstOrNull { it.key == "classic" }?.value == "true"

    /**
     * Arcade history (Parejas, Pulso, Ruta espacial, Relámpago): newest first, at most
     * [ARCADE_HISTORY] entries in "arcade:<game>".
     */
    fun arcadeHistory(game: String): List<JSONObject> =
        preferences.value
            .firstOrNull { it.key == "arcade:$game" }
            ?.value
            ?.let { v ->
                runCatching {
                        val a = JSONArray(v)
                        (0 until a.length()).map { a.getJSONObject(it) }
                    }
                    .getOrNull()
            } ?: emptyList()

    fun arcadeBest(game: String) = arcadeHistory(game).maxOfOrNull { it.optInt("score") }

    /**
     * Saves one arcade session; [extra] holds game-specific fields (combo, nivel, salidas
     * anticipadas…).
     */
    fun saveArcade(game: String, score: Int, activeMs: Long, extra: Map<String, Any> = emptyMap()) {
        viewModelScope.launch {
            try {
                writes.withLock {
                    val old =
                        store.dao.getPreference("arcade:$game")?.value?.let {
                            runCatching { JSONArray(it) }.getOrNull()
                        } ?: JSONArray()
                    val entry =
                        JSONObject()
                            .put("ended", System.currentTimeMillis())
                            .put("score", score)
                            .put("activeMs", activeMs)
                    extra.forEach { (k, v) -> entry.put(k, v) }
                    val next = JSONArray().put(entry)
                    for (i in 0 until minOf(old.length(), ARCADE_HISTORY - 1)) next.put(old.get(i))
                    store.dao.putPreference(PreferenceRow("arcade:$game", next.toString()))
                }
            } catch (e: Exception) {
                message = "No se pudo guardar la partida."
            }
        }
    }

    // ---------- Lote C: cofre, misiones y álbum ----------
    /**
     * Chests, album, bonus points and cosmetics (pref "rewards"). Never holds or grants leisure
     * time.
     */
    val wallet
        get() = Wallet.decodeOrEmpty(preferences.value.firstOrNull { it.key == "rewards" }?.value)

    fun today(): LocalDate = LabDay.today(zone)

    /** Check-in de energía de hoy (un toque; NORMAL si no se elige). Solo recorta el plan del día. */
    var energy by mutableStateOf(Energy.NORMAL)
        private set

    fun updateEnergy(e: Energy) {
        energy = e
    }

    fun missionsToday(rows: List<ResultRow> = results.value): List<DailyMission> =
        Missions.today(
            today(),
            NetSec.ids.filter { netsecUnlocked(rows, it) },
            (MIND_GAMES + ARCADE_LAB_GAMES).map { it.id },
            progress.value.associate { it.game to it.level },
        )

    fun missionProgress() =
        MissionProgress.decodeOrEmpty(
            preferences.value.firstOrNull { it.key == "missions:${today()}" }?.value
        )

    /** What the last finished practice gave: shown under its summary. */
    data class RewardNews(
        val chest: Boolean,
        val capReached: Boolean,
        val missionsDone: List<DailyMission>,
        val allMissions: Boolean,
    )

    var news by mutableStateOf<RewardNews?>(null)
        private set

    /**
     * Cards unlocked since the last [start]; [milestone] is set when the album crosses a multiple
     * of 10.
     */
    var newCards by mutableStateOf<List<AlbumCard>>(emptyList())
        private set

    var milestone by mutableStateOf<Int?>(null)
        private set

    /** "Otra vez" pressed in a row (NearMiss.MAX_AGAIN), reset by any other choice. */
    var againInRow by mutableIntStateOf(0)

    fun clearNews() {
        news = null
        newCards = emptyList()
        milestone = null
    }

    private suspend fun readWallet() =
        Wallet.decodeOrEmpty(store.dao.getPreference("rewards")?.value)

    private suspend fun saveWallet(w: Wallet) {
        store.dao.putPreference(PreferenceRow("rewards", w.encode()))
        ChestSummary.write(getApplication(), w.pending.size)
    }

    /**
     * A finished practice (12 rondas, Relámpago o Práctica 3 min): mission progress and, up to 3 a
     * day, a chest.
     */
    fun reward(play: Play) {
        val day = today()
        val missions = missionsToday()
        viewModelScope.launch {
            try {
                writes.withLock {
                    var w = readWallet()
                    val key = "missions:$day"
                    val before = MissionProgress.decodeOrEmpty(store.dao.getPreference(key)?.value)
                    var after = Missions.record(missions, before, play)
                    // Sin cofres sorpresa (premio variable = mecánica de retención): practicar
                    // solo suma misiones y cartas que enseñan algo.
                    val chest = false
                    val all = after.allDone(missions) && !after.rewarded
                    if (all) after = after.copy(rewarded = true)
                    store.dao.putPreference(PreferenceRow(key, after.encode()))
                    saveWallet(w)
                    val done = missions.filter { after.done(it) && !before.done(it) }
                    // Foco 1.0, etapa 1: cada práctica terminada y cada misión cumplida van al log
                    // del Inspector de Foco.
                    // Etapa 3: el valor dice si el reto se superó (1) o se falló (0): un reto
                    // fallado no da minutos.
                    val passed =
                        when (play.source) {
                            ChestSource.RUN -> play.correct * 10 >= Formats.of(play.game).units * 6
                            ChestSource.SHORT -> play.correct >= 3
                            ChestSource.FLASH -> play.score >= 30
                            else -> true
                        }
                    // Reglas absolutas (Regla 4): el valor es el nivel del reto superado (0 si se
                    // falló); Foco paga 2/4/5 según el nivel.
                    InspectorQueue.add(
                        getApplication(),
                        InspectorQueue.RETO,
                        play.game,
                        if (passed) play.level.coerceAtLeast(1) else 0,
                        "superado=$passed;modo=${play.source.name.lowercase()};nivel=${play.level};aciertos=${play.correct};puntos=${play.score}",
                        "reto:${System.currentTimeMillis()}",
                    )
                    // Definición 1.0 revisada: las misiones diarias no son logro; solo la subida de
                    // nivel (en finish).
                    news = RewardNews(chest, false, done, all)
                    if (done.isNotEmpty()) feedbackEvents.tryEmit(FeedbackCue.LEVEL_UP)
                }
            } catch (e: Exception) {
                /* best effort: practice is saved even if the reward is not */
            }
        }
    }

    /**
     * A correct answer of a network game: the card for that concept joins the album the first time.
     */
    fun unlockCard(game: String, explanation: String) {
        if (game !in NetSec.ids) return
        viewModelScope.launch {
            try {
                writes.withLock {
                    val w = readWallet()
                    val card = Album.forAnswer(game, explanation, w.cards) ?: return@withLock
                    val next = w.unlock(card.key)
                    saveWallet(next)
                    newCards = newCards + card
                    if (Album.milestone(w.cards.size, next.cards.size)) {
                        milestone = next.cards.size
                        feedbackEvents.tryEmit(FeedbackCue.UNLOCK)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    /**
     * Opens the oldest earned chest; [done] gets the reward (null if there was none or the write
     * failed).
     */
    fun openChest(done: (Reward?) -> Unit) {
        viewModelScope.launch {
            val r =
                try {
                    writes.withLock {
                        val w = readWallet()
                        val (next, reward) = w.open() ?: return@withLock null
                        saveWallet(next)
                        reward
                    }
                } catch (e: Exception) {
                    null
                }
            if (r != null) feedbackEvents.tryEmit(FeedbackCue.UNLOCK)
            done(r)
        }
    }

    fun equip(id: String) {
        viewModelScope.launch {
            try {
                writes.withLock { saveWallet(readWallet().equip(id)) }
            } catch (e: Exception) {}
        }
    }

    /** Keeps Foco's read-only chest mirror in step with Room (after a restore, for instance). */
    private fun syncChest() {
        viewModelScope.launch {
            try {
                ChestSummary.write(getApplication(), readWallet().pending.size)
            } catch (e: Exception) {}
        }
    }

    /** "Otra vez": the same game again (counts towards [NearMiss.MAX_AGAIN]). */
    fun again() {
        val g = run?.game ?: return
        val n = againInRow + 1
        closeResult()
        start(g)
        againInRow = n
    }

    fun begin() {
        val r = run ?: return
        answerStart = null
        activeStart = null
        val phase =
            when (r.challenge.kind) {
                Kind.GRID,
                Kind.SEQUENCE,
                Kind.WORKING -> Phase.OBSERVE
                Kind.REACTION -> Phase.WAIT
                else -> Phase.ANSWER
            }
        run = r.copy(phase = phase, picked = emptyList())
        persist()
    }

    fun answerFraction(): Float {
        val r = run ?: return 1f
        val start = answerStart ?: return 1f
        return (1f - (SystemClock.elapsedRealtime() - start).toFloat() / r.challenge.deadlineMs)
            .coerceIn(0f, 1f)
    }

    fun presented() {
        if (activeStart == null) activeStart = SystemClock.elapsedRealtime()
        if (run?.phase == Phase.ANSWER) answerStart = SystemClock.elapsedRealtime()
    }

    fun reveal() {
        val r = run ?: return
        if (r.phase !in listOf(Phase.OBSERVE, Phase.WAIT)) return
        answerStart = null
        run = r.copy(phase = Phase.ANSWER)
    }

    fun choose(index: Int) {
        val r = run ?: return
        if (r.phase == Phase.WAIT && r.game == "reaction") {
            score(false, "early", null)
            return
        }
        if (r.phase != Phase.ANSWER || answerStart == null) return
        val c = r.challenge
        if (!untimed(r.game) && SystemClock.elapsedRealtime() - answerStart!! >= c.deadlineMs) {
            timeout()
            return
        }
        when (c.kind) {
            Kind.GRID -> {
                run = r.copy(picked = if (index in r.picked) r.picked - index else r.picked + index)
                feedbackEvents.tryEmit(FeedbackCue.TAP)
            }
            Kind.SEQUENCE -> {
                val picked = r.picked + index
                run = r.copy(picked = picked)
                if (picked.size == c.targets.size)
                    score(picked == c.targets, if (picked == c.targets) "ok" else "wrong")
                else feedbackEvents.tryEmit(FeedbackCue.TAP)
            }
            Kind.REACTION -> {
                val e = SystemClock.elapsedRealtime() - answerStart!!
                score(e >= 100, if (e >= 100) "ok" else "implausible", if (e >= 100) e else null)
            }
            else -> {
                run = r.copy(picked = listOf(index))
                score(index == c.answer, if (index == c.answer) "ok" else "wrong")
            }
        }
    }

    /**
     * Fase 7 pilot boards (subnet, inbox, firewall): [picked] is the prefix, the verdict (0 seguro,
     * 1 trampa) or the indices left open.
     */
    fun submit(picked: List<Int>) {
        val r = run ?: return
        if (r.phase != Phase.ANSWER || answerStart == null) return
        val c = r.challenge
        if (!untimed(r.game) && SystemClock.elapsedRealtime() - answerStart!! >= c.deadlineMs) {
            timeout()
            return
        }
        run = r.copy(picked = picked)
        val ok = c.accepts(picked)
        score(ok, if (ok) "ok" else "wrong")
    }

    /**
     * Fase 2 boards (La maleta, Vigía): the board plays the whole round itself (its own clock,
     * observe and recall phases) and reports the verdict computed by the core rules plus its
     * [detail] log.
     */
    fun boardDone(ok: Boolean, detail: String, stop: Boolean = false) {
        val r = run ?: return
        if (r.phase != Phase.ANSWER || answerStart == null) return
        run = r.copy(picked = emptyList())
        score(ok, if (ok) "ok" else "wrong", detail = detail, stop = stop)
    }

    /**
     * Logs of the board rounds already played in this run (for the announced in-game tuning and the
     * record).
     */
    fun boardLogs(r: Run = run!!) = r.outcomes.map { it.detail }

    /**
     * Cards a board round earned: every item seen (suitcase), pattern detected (vigil), lesson
     * learned (route, ddos) or pair made (pairs), the first time.
     */
    private fun unlockBoardCards(game: String, detail: String) {
        viewModelScope.launch {
            try {
                writes.withLock {
                    var w = readWallet()
                    val before = w.cards.size
                    val cards = Album.forBoard(game, detail, w.cards)
                    if (cards.isEmpty()) return@withLock
                    cards.forEach { w = w.unlock(it.key) }
                    saveWallet(w)
                    newCards = newCards + cards
                    if (Album.milestone(before, w.cards.size)) {
                        milestone = w.cards.size
                        feedbackEvents.tryEmit(FeedbackCue.UNLOCK)
                    }
                }
            } catch (e: Exception) {}
        }
    }

    fun checkGrid() {
        val r = run ?: return
        if (r.phase != Phase.ANSWER || answerStart == null) return
        if (
            !untimed(r.game) &&
                SystemClock.elapsedRealtime() - answerStart!! >= r.challenge.deadlineMs
        ) {
            timeout()
            return
        }
        val ok = r.picked.toSet() == r.challenge.targets.toSet()
        score(ok, if (ok) "ok" else "wrong")
    }

    fun timeout() {
        if (run?.phase == Phase.ANSWER && answerStart != null && !untimed(run!!.game))
            score(false, "timeout", null)
    }

    /**
     * [stop] (Órbita expansiva): the partida ends here; the rounds not played are saved as
     * "survival" so it can be finished.
     */
    private fun score(
        ok: Boolean,
        reason: String,
        time: Long? = answerStart?.let { SystemClock.elapsedRealtime() - it },
        detail: String = "",
        stop: Boolean = false,
    ) {
        val r = run ?: return
        if (r.phase !in listOf(Phase.ANSWER, Phase.WAIT)) return
        val c = r.challenge
        val pilot = r.game in Pilot.ids
        val now = SystemClock.elapsedRealtime()
        val o =
            Outcome(
                ok,
                if (ok && !untimed(r.game)) time else null,
                (now - (activeStart ?: now)).coerceIn(0, MAX_ROUND_ACTIVE_MS),
                System.currentTimeMillis(),
                reason,
                time?.coerceIn(0, MAX_ROUND_ACTIVE_MS),
                if (untimed(r.game)) "untimed" else "timed",
                if (pilot) c.item else null,
                if (pilot) c.format else -1,
                detail,
            )
        if (!ok && !endless && Games.get(r.game).area == Area.NETSEC && c.explanation !in mistakes)
            mistakes = mistakes + c.explanation
        if (ok && Games.get(r.game).area == Area.NETSEC) unlockCard(r.game, c.explanation)
        if (detail.isNotEmpty()) unlockBoardCards(r.game, detail)
        val rest =
            if (stop && !endless)
                List((r.format.units - r.outcomes.size - 1).coerceAtLeast(0)) {
                    Outcome(false, null, 0, o.ended, "survival", null, o.mode)
                }
            else emptyList()
        answerStart = null
        activeStart = null
        run = r.copy(phase = Phase.FEEDBACK, outcomes = r.outcomes + o + rest)
        // Chained formats (paso 2): the board already gave its own signal and summary, so the next
        // unit starts at once, with no panel.
        if (r.format.chained) {
            next()
            return
        }
        feedbackEvents.tryEmit(if (ok) FeedbackCue.CORRECT else FeedbackCue.RETRY)
        persist()
    }

    fun next() {
        val r = run ?: return
        if (r.phase != Phase.FEEDBACK) return
        val units = r.format.units
        if (endless) {
            endlessRound++
            val index = (r.index + 1) % units
            run =
                r.copy(
                    index = index,
                    seed = if (index == 0) System.nanoTime() else r.seed,
                    outcomes = emptyList(),
                    picked = emptyList(),
                    history = (r.history + r.outcomes.mapNotNull { it.mark() }).takeLast(12),
                )
            begin()
            return
        }
        if (r.outcomes.size >= units) {
            run = r.copy(phase = Phase.SAVING)
            persist()
            finish()
        } else {
            run =
                r.copy(
                    index = r.outcomes.size,
                    picked = emptyList(),
                    history = r.outcomes.mapNotNull { it.mark() },
                )
            begin()
        }
    }

    fun pause() {
        val r = run ?: return
        if (r.phase !in listOf(Phase.OBSERVE, Phase.WAIT, Phase.ANSWER)) return
        answerStart = null
        activeStart = null
        run = r.copy(phase = Phase.PAUSED, nonce = r.nonce + 1, picked = emptyList())
        persist()
    }

    fun abandon() {
        run = null
        planSize = 0
        answerStart = null
        activeStart = null
        result = null
        viewModelScope.launch {
            try {
                writes.withLock { store.dao.deleteDraft() }
            } catch (e: Exception) {
                message = "No se pudo descartar la sesión."
            }
        }
    }

    fun closeResult() {
        run = null
        result = null
        planSize = 0
    }

    fun continuePlan() {
        val rem = run?.remaining ?: emptyList()
        run = null
        result = null
        if (rem.isNotEmpty()) start(rem.first(), rem.drop(1)) else planSize = 0
    }

    fun finish() {
        val r = run ?: return
        val units = r.format.units
        if (r.outcomes.size != units) return
        run = r.copy(phase = Phase.SAVING)
        saveFailed = false
        viewModelScope.launch {
            try {
                val times = r.outcomes.filter { it.correct }.mapNotNull { it.ms }
                val days = JSONObject()
                r.outcomes.forEach { o ->
                    var start = o.ended - o.active
                    while (start < o.ended) {
                        val local = Instant.ofEpochMilli(start).atZone(zone)
                        val midnight =
                            local
                                .toLocalDate()
                                .plusDays(1)
                                .atStartOfDay(zone)
                                .toInstant()
                                .toEpochMilli()
                        val end = minOf(midnight, o.ended)
                        val key = local.toLocalDate().toString()
                        days.put(key, days.optLong(key) + (end - start))
                        start = end
                    }
                }
                val row =
                    ResultRow(
                        r.id,
                        r.game,
                        System.currentTimeMillis(),
                        r.level,
                        r.level,
                        r.outcomes.count { it.correct },
                        units,
                        if (times.size >= 8) median(times) else null,
                        r.outcomes.sumOf { it.active },
                        0,
                        JSONArray().apply { r.outcomes.forEach { put(it.json()) } }.toString(),
                        days.toString(),
                    )
                previous = results.value.firstOrNull { it.game == r.game && it.id != r.id }
                val early = r.outcomes.count { it.reason == "early" }
                result = writes.withLock { store.finish(row, early, goal) }
                result?.let { saved ->
                    if (saved.nextLevel > saved.level)
                        InspectorQueue.add(
                            getApplication(),
                            InspectorQueue.LOGRO,
                            saved.game,
                            saved.nextLevel,
                            "sube al nivel ${saved.nextLevel}",
                            "nivel:${saved.game}:${saved.nextLevel}:${saved.id}",
                        )
                }
                run = r.copy(phase = Phase.RESULT)
                feedbackEvents.tryEmit(FeedbackCue.COMPLETE)
                val best =
                    when (r.game) {
                        Suitcase.ID ->
                            r.outcomes
                                .mapNotNull { SuitcaseLog.decode(it.detail)?.length }
                                .maxOrNull() ?: 0
                        Vigil.ID -> r.outcomes.count { VigilLog.decode(it.detail)?.lapses == 0 }
                        // Fase 3: routes in a row, highest combo tier of a wave, longest pairs
                        // streak of a board.
                        Route.ID -> Missions.bestStreak(r.outcomes.map { it.correct })
                        Ddos.ID ->
                            r.outcomes.mapNotNull { DdosLog.decode(it.detail)?.maxTier }.maxOrNull()
                                ?: 0 // top combo tier of the partida
                        IncidentPairs.ID ->
                            r.outcomes
                                .mapNotNull { PairsLog.decode(it.detail)?.maxStreak }
                                .maxOrNull() ?: 0
                        else -> 0
                    }
                reward(
                    Play(
                        ChestSource.RUN,
                        r.game,
                        row.correct,
                        Missions.bestStreak(r.outcomes.map { it.correct }),
                        best = best,
                        level = r.level,
                    )
                )
            } catch (e: Exception) {
                saveFailed = true
                message = "No se pudo guardar el resultado. Pulsa Reintentar."
            }
        }
    }

    val events =
        combine(results, preferences, eventDate) { rows, prefs, _ ->
                val z =
                    runCatching {
                            ZoneId.of(
                                prefs.firstOrNull { it.key == "zone" }?.value
                                    ?: ZoneId.systemDefault().id
                            )
                        }
                        .getOrDefault(ZoneId.systemDefault())
                val today = LocalDate.now(z)
                val start =
                    runCatching {
                            LocalDate.parse(
                                prefs.firstOrNull { it.key == "eventsStart" }?.value
                                    ?: today.toString()
                            )
                        }
                        .getOrDefault(today)
                val notes =
                    prefs
                        .filter { it.key.startsWith("practice:") }
                        .mapNotNull { p ->
                            runCatching {
                                    val obj = JSONObject(p.value)
                                    AppliedPractice(
                                        LocalDate.parse(p.key.removePrefix("practice:")),
                                        obj.getString("note"),
                                        obj.getLong("ended"),
                                    )
                                }
                                .getOrNull()
                        }
                EventEngine.build(
                    today,
                    start,
                    rows.map { EventSession(it.id, it.game, it.ended) },
                    notes,
                    z,
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                EventEngine.build(
                    LocalDate.now(zone),
                    LocalDate.now(zone),
                    emptyList(),
                    emptyList(),
                    zone,
                ),
            )

    suspend fun registerApplication(date: LocalDate, note: String): Boolean {
        val saved = writes.withLock { store.applyPractice(date, note) }
        refreshEventDate()
        return saved
    }

    fun reset() {
        viewModelScope.launch {
            try {
                writes.withLock { store.reset() }
                message = "Historial eliminado."
            } catch (e: Exception) {
                message = "No se pudo borrar el historial."
            }
        }
    }

    suspend fun backup() = writes.withLock { store.export() }

    suspend fun restore(text: String) {
        writes.withLock {
            store.restore(text)
            store.migrateRoute()
        }
        syncChest()
    }

    private fun computeTotals(rows: List<ResultRow>): Map<LocalDate, Long> {
        val t = mutableMapOf<LocalDate, Long>()
        rows.forEach { r ->
            val d = JSONObject(r.days)
            d.keys().forEach {
                val date = LocalDate.parse(it)
                t[date] = (t[date] ?: 0L) + d.getLong(it)
            }
        }
        return t
    }

    val dailyTotals =
        results
            .map { computeTotals(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun totals() = dailyTotals.value
}
