package personal.qqq.braintraining.core

import java.time.LocalDate
import kotlin.random.Random

/**
 * Lote C, misiones diarias: 3 retos cortos al día, sacados de los juegos ya desbloqueados. STREAK:
 * aciertos en una partida de ese juego (antes seguidos; 12 rondas o Relámpago). SCORE: aciertos de
 * una partida de 12 rondas. FLASH: puntos en una partida de Relámpago. SHORT: aciertos en una
 * Práctica 3 min. Sin rachas que castiguen: las misiones de ayer simplemente desaparecen.
 */
enum class MissionKind {
    STREAK,
    SCORE,
    FLASH,
    SHORT,
    CHAIN,
    CLEAN,
}

/**
 * CHAIN (Fase 2): reach a suitcase chain of [target]. CLEAN: [target] Vigía tramos without lapses
 * at level ≥ [minLevel].
 */
data class DailyMission(
    val id: String,
    val kind: MissionKind,
    val game: String,
    val target: Int,
    val title: String,
    val minLevel: Int = 1,
)

/** One finished piece of practice, as the missions see it. */
/**
 * [best] is the game's own measure (longest suitcase chain, Vigía tramos without lapses) and
 * [level] the level it was played at.
 */
data class Play(
    val source: ChestSource,
    val game: String,
    val correct: Int = 0,
    val bestStreak: Int = 0,
    val score: Int = 0,
    val best: Int = 0,
    val level: Int = 1,
)

/**
 * Progress of today's missions (pref "missions:<fecha>"): best value per mission id, and whether
 * the chest was given.
 */
data class MissionProgress(
    val values: Map<String, Int> = emptyMap(),
    val rewarded: Boolean = false,
) {
    fun value(m: DailyMission) = (values[m.id] ?: 0).coerceAtMost(m.target)

    fun done(m: DailyMission) = (values[m.id] ?: 0) >= m.target

    fun allDone(ms: List<DailyMission>) = ms.isNotEmpty() && ms.all(::done)

    fun encode() =
        "rewarded=$rewarded\n" +
            values.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}\n" }

    companion object {
        fun decode(text: String): MissionProgress {
            var rewarded = false
            val values = mutableMapOf<String, Int>()
            text
                .lines()
                .filter { it.isNotBlank() }
                .forEach { l ->
                    val i = l.indexOf('=')
                    require(i > 0)
                    val k = l.substring(0, i)
                    val v = l.substring(i + 1)
                    if (k == "rewarded") {
                        require(v == "true" || v == "false")
                        rewarded = v == "true"
                    } else {
                        require(Regex("[a-z0-9:]+").matches(k))
                        values[k] = v.toInt().also { require(it >= 0) }
                    }
                }
            return MissionProgress(values, rewarded)
        }

        fun decodeOrEmpty(text: String?) =
            text?.let { runCatching { decode(it) }.getOrNull() } ?: MissionProgress()
    }
}

object Missions {
    const val COUNT = 3

    private fun noun(game: String, n: Int) =
        when (game) {
            "bits" -> if (n == 1) "conversión" else "conversiones"
            "subnet" -> if (n == 1) "subred" else "subredes"
            "ports" -> if (n == 1) "puerto" else "puertos"
            "phishing" -> if (n == 1) "correo" else "correos"
            "sqlsafe" -> if (n == 1) "consulta" else "consultas"
            else -> if (n == 1) "registro" else "registros"
        }

    private fun title(game: String) = runCatching { Games.get(game).title }.getOrDefault(game)

    /**
     * Today's 3 missions, deterministic for ([day], inputs): one streak in an unlocked network
     * game, one score in a 12-round game ([mind] or network) and one Relámpago or Práctica 3 min.
     * Targets grow a little with [levels].
     */
    fun today(
        day: LocalDate,
        unlocked: List<String>,
        mind: List<String>,
        levels: Map<String, Int> = emptyMap(),
    ): List<DailyMission> {
        val r = Random(day.toEpochDay() * 31L + 17L)
        // Retired games (Games.retired) never come back through missions, even if a caller still
        // lists them.
        val net =
            unlocked
                .filter { it in NetSec.ids && it !in Games.retired }
                .ifEmpty { listOf(NetSec.ids.first()) }
        val mind = mind.filter { it !in Games.retired }
        fun lvl(g: String) = (levels[g] ?: 1).coerceIn(1, 10)
        val a = net[r.nextInt(net.size)]
        val sa = 3 + (lvl(a) - 1) / 3
        val streak =
            DailyMission(
                "streak:$a",
                MissionKind.STREAK,
                a,
                sa,
                "Acierta $sa ${noun(a,sa)} en una partida",
            )
        val scorePool = (mind + net).distinct().filter { it != a }.ifEmpty { net }
        val b = scorePool[r.nextInt(scorePool.size)]
        val sb = if (lvl(b) >= 5) 9 else 8
        val score =
            when (b) {
                Suitcase.ID -> {
                    val t = minOf(10, Suitcase.level(lvl(b)).target)
                    DailyMission(
                        "chain:$b",
                        MissionKind.CHAIN,
                        b,
                        t,
                        "Llega a una cadena de $t en ${title(b)}",
                    )
                }
                Vigil.ID ->
                    if (lvl(b) >= 5)
                        DailyMission(
                            "clean:$b",
                            MissionKind.CLEAN,
                            b,
                            1,
                            "Un tramo sin lapsos en ${title(b)} (nivel ≥ 5)",
                            5,
                        )
                    else
                        DailyMission(
                            "clean:$b",
                            MissionKind.CLEAN,
                            b,
                            1,
                            "Un tramo sin lapsos en ${title(b)}",
                        )
                // Fase 3: best measure per game (Play.best): routes in a row, highest combo tier in
                // a wave, longest pairs streak.
                Route.ID ->
                    if (lvl(b) >= 5)
                        DailyMission(
                            "clean:$b",
                            MissionKind.CLEAN,
                            b,
                            5,
                            "5 rutas seguidas en ${title(b)} (nivel ≥ 5)",
                            5,
                        )
                    else
                        DailyMission(
                            "clean:$b",
                            MissionKind.CLEAN,
                            b,
                            3,
                            "3 rutas seguidas en ${title(b)}",
                        )
                Ddos.ID ->
                    DailyMission("chain:$b", MissionKind.CHAIN, b, 3, "Combo ×3 en ${title(b)}")
                IncidentPairs.ID ->
                    DailyMission(
                        "chain:$b",
                        MissionKind.CHAIN,
                        b,
                        5,
                        "Racha de 5 parejas en ${title(b)}",
                    )
                else ->
                    DailyMission(
                        "score:$b",
                        MissionKind.SCORE,
                        b,
                        sb,
                        "Completa ${title(b)} con $sb/12",
                    )
            }
        val third =
            if (r.nextBoolean()) {
                val c = net[r.nextInt(net.size)]
                val pts = 60 + 10 * (lvl(c) / 3)
                DailyMission(
                    "flash:$c",
                    MissionKind.FLASH,
                    c,
                    pts,
                    "Haz $pts puntos en ${title(c)} · Relámpago",
                )
            } else
                DailyMission(
                    "short",
                    MissionKind.SHORT,
                    "short",
                    4,
                    "Acierta 4 retos en una Práctica 3 min",
                )
        return listOf(streak, score, third)
    }

    /** What [play] adds to [p]: each mission keeps its best value. */
    fun record(missions: List<DailyMission>, p: MissionProgress, play: Play): MissionProgress {
        val values = p.values.toMutableMap()
        missions.forEach { m ->
            val v =
                when (m.kind) {
                    MissionKind.STREAK ->
                        // Aciertos de la partida, no seguidos: un fallo no borra lo conseguido.
                        if (play.game == m.game && play.source != ChestSource.SHORT)
                            maxOf(play.correct, play.bestStreak)
                        else 0
                    MissionKind.SCORE ->
                        if (play.game == m.game && play.source == ChestSource.RUN) play.correct
                        else 0
                    MissionKind.FLASH ->
                        if (play.game == m.game && play.source == ChestSource.FLASH) play.score
                        else 0
                    MissionKind.SHORT -> if (play.source == ChestSource.SHORT) play.correct else 0
                    MissionKind.CHAIN ->
                        if (play.game == m.game && play.source == ChestSource.RUN) play.best else 0
                    MissionKind.CLEAN ->
                        if (
                            play.game == m.game &&
                                play.source == ChestSource.RUN &&
                                play.level >= m.minLevel
                        )
                            play.best
                        else 0
                }
            if (v > (values[m.id] ?: 0)) values[m.id] = v
        }
        return p.copy(values = values)
    }

    /** Longest run of trues. */
    fun bestStreak(correct: List<Boolean>): Int {
        var best = 0
        var cur = 0
        correct.forEach {
            if (it) {
                cur++
                best = maxOf(best, cur)
            } else cur = 0
        }
        return best
    }
}

/**
 * Lote C, "Otra vez" y fantasma del récord. Un casi récord es quedarse a muy poco del mejor
 * resultado (1 acierto de 12, o un 10 % de los puntos). "Otra vez" se ofrece como mucho [MAX_AGAIN]
 * veces seguidas; después se sugiere terminar.
 */
object NearMiss {
    const val MAX_AGAIN = 2

    fun gap(best: Int) = maxOf(1, best / 10)

    fun isNear(score: Int, best: Int?) =
        best != null && best > 0 && score < best && best - score <= gap(best)

    fun offerAgain(score: Int, best: Int?, againInRow: Int) =
        isNear(score, best) && againInRow < MAX_AGAIN

    /**
     * Cumulative correct answers after each round of the best run: ghost[i] = aciertos tras i+1
     * rondas.
     */
    fun ghost(correct: List<Boolean>): List<Int> {
        var n = 0
        return correct.map {
            if (it) n++
            n
        }
    }

    /** Ghost value after [rounds] rounds (0 before the first). */
    fun ghostAt(ghost: List<Int>, rounds: Int) =
        if (rounds <= 0 || ghost.isEmpty()) 0 else ghost[minOf(rounds, ghost.size) - 1]
}
