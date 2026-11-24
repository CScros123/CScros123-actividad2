package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * Órbitas 2 (docs/ORBITAS-2.md): memoria visoespacial pura. Los nodos no llevan números; un cometa
 * recorre la órbita de un nodo al siguiente y el jugador repite el camino tocando lugares, no
 * cifras.
 *
 * Las 12 rondas se reparten en tres fases:
 * - rondas 1–4, calibración: caminos de 3 a 5 nodos cada vez más rápidos;
 * - rondas 5–8, polaridad: nodos de doble anillo (doble toque) y nodos magenta (se toca el
 *   diametralmente opuesto);
 * - rondas 9–12, rotación: antes del turno la órbita gira 1 o 2 posiciones (45° o 90° con 8 nodos).
 *   Desde la ronda 7 aparecen destellos fuera de la órbita; no tocarlos da el bono de enfoque (+25
 *   % de puntos).
 *
 * Los nodos conservan su identidad al girar: [OrbitTask.expected] siempre habla de identidades y el
 * tablero traduce cada toque a la identidad que hay en ese punto después del giro.
 */
enum class OrbitMode(val key: String, val title: String, val about: String) {
    CLASSIC("", "Clásico", "12 rondas en tres fases de dificultad."),
    MIRROR("mirror", "Espejo", "Repite el camino al revés: del último nodo al primero."),
    EXPAND(
        "expand",
        "Órbita expansiva",
        "Supervivencia: 6, luego 8 y 10 nodos. El primer fallo termina la partida y no cambia tu nivel.",
    );

    companion object {
        fun of(key: String?) = values().firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** One light of the path. [kind]: [Orbit.SINGLE], [Orbit.DOUBLE] or [Orbit.INVERSE]. */
data class OrbitStep(val node: Int, val kind: Int = Orbit.SINGLE)

/**
 * A flash outside the orbit, [atMs] after the demonstration starts; [x] and [y] are normalized
 * board coordinates (0..1).
 */
data class OrbitDecoy(val atMs: Long, val x: Float, val y: Float)

data class OrbitTask(
    val level: Int,
    val seed: Long,
    val round: Int,
    val mode: OrbitMode,
    val nodes: Int,
    val steps: List<OrbitStep>,
    val stepMs: Long,
    val turn: Int,
    val decoys: List<OrbitDecoy>,
) {
    val stage
        get() = Orbit.stage(round)

    private val order
        get() = if (mode == OrbitMode.MIRROR) steps.reversed() else steps

    /**
     * Node identities to touch, in order (the opposite node for a magenta light, the path reversed
     * in Espejo).
     */
    val expected: List<Int>
        get() =
            order.map { if (it.kind == Orbit.INVERSE) Orbit.opposite(it.node, nodes) else it.node }

    /** Whether each expected touch is a double tap. */
    val doubles: List<Boolean>
        get() = order.map { it.kind == Orbit.DOUBLE }

    /** Demonstration length: one step per light plus a short tail for the last one. */
    val showMs
        get() = steps.size * stepMs + stepMs * 35 / 100

    val specials
        get() = steps.count { it.kind != Orbit.SINGLE }

    /** Time to answer: generous, it measures memory, not speed. */
    val answerMs
        get() =
            Orbit.ANSWER_BASE_MS +
                steps.size * Orbit.ANSWER_PER_LIGHT_MS +
                specials * Orbit.ANSWER_PER_SPECIAL_MS +
                (if (mode == OrbitMode.MIRROR) steps.size * Orbit.ANSWER_MIRROR_PER_LIGHT_MS
                else 0L)

    /** Base points of the round (later phases are worth more). */
    val points
        get() = 100 + stage * 25
}

/** What the input of one round leads to. */
enum class OrbitTap {
    STEP,
    HALF,
    DONE,
    WRONG,
    IGNORED,
}

/**
 * Judges the touches of one round (pure, so it is tested without a screen). Times are milliseconds
 * of the board clock. A double node needs its second touch within [Orbit.DOUBLE_WINDOW_MS]; call
 * [tick] every frame so a missing second touch fails.
 */
class OrbitInput(val task: OrbitTask) {
    var index = 0
        private set

    var error = Orbit.OK
        private set

    /** Node waiting for its second touch (−1 none) and when the first one landed. */
    var pending = -1
        private set

    private var pendingAt = 0L
    private var lastNode = -1
    private var lastAt = -1000L
    val finished
        get() = error != Orbit.OK || index >= task.expected.size

    fun tap(node: Int, at: Long): OrbitTap {
        if (finished) return OrbitTap.IGNORED
        // A finger that bounces on the glass is one touch, not two.
        if (node == lastNode && at - lastAt < Orbit.DEBOUNCE_MS) return OrbitTap.IGNORED
        lastNode = node
        lastAt = at
        if (pending >= 0) {
            if (node == pending && at - pendingAt <= Orbit.DOUBLE_WINDOW_MS) {
                pending = -1
                return advance()
            }
            error = Orbit.DOUBLE_MISSED
            return OrbitTap.WRONG
        }
        if (node != task.expected[index]) {
            error = Orbit.WRONG
            return OrbitTap.WRONG
        }
        if (task.doubles[index]) {
            pending = node
            pendingAt = at
            return OrbitTap.HALF
        }
        return advance()
    }

    fun tick(now: Long, answerStart: Long): OrbitTap? {
        if (finished) return null
        if (pending >= 0 && now - pendingAt > Orbit.DOUBLE_WINDOW_MS) {
            error = Orbit.DOUBLE_MISSED
            return OrbitTap.WRONG
        }
        if (now - answerStart > task.answerMs) {
            error = Orbit.TIME
            return OrbitTap.WRONG
        }
        return null
    }

    /** The identity the player had to touch when the round failed (or the next one). */
    val missed
        get() = task.expected.getOrElse(index) { -1 }

    private fun advance(): OrbitTap {
        index++
        return if (index >= task.expected.size) OrbitTap.DONE else OrbitTap.STEP
    }
}

/**
 * What one round leaves in its detail: verdict, phase, board, length, error, focus bonus, points
 * and the reaction time of every right touch by screen quadrant ("q:ms") plus the quadrant of the
 * node that was missed (−1 none).
 */
data class OrbitLog(
    val ok: Boolean,
    val mode: String,
    val stage: Int,
    val nodes: Int,
    val length: Int,
    val error: Int,
    val focus: Boolean,
    val points: Int,
    val times: List<Pair<Int, Long>> = emptyList(),
    val missQ: Int = -1,
) {
    fun encode() =
        "ok=$ok;mode=${mode.ifEmpty{"classic"}};stage=$stage;nodes=$nodes;len=$length;error=$error;focus=$focus;points=$points;rt=${times.joinToString(","){"${it.first}:${it.second}"}};miss=$missQ"

    companion object {
        fun decode(text: String?): OrbitLog? =
            runCatching {
                    val m =
                        text!!.split(';').associate {
                            val i = it.indexOf('=')
                            it.substring(0, i) to it.substring(i + 1)
                        }
                    OrbitLog(
                        m.getValue("ok").toBooleanStrict(),
                        m.getValue("mode").let { if (it == "classic") "" else it },
                        m.getValue("stage").toInt(),
                        m.getValue("nodes").toInt(),
                        m.getValue("len").toInt(),
                        m.getValue("error").toInt(),
                        m.getValue("focus").toBooleanStrict(),
                        m.getValue("points").toInt(),
                        m["rt"]
                            .orEmpty()
                            .split(',')
                            .filter { it.isNotBlank() }
                            .map {
                                val p = it.split(':')
                                p[0].toInt() to p[1].toLong()
                            },
                        m["miss"]?.toInt() ?: -1,
                    )
                }
                .getOrNull()
    }
}

/**
 * Attention map of a partida: per screen quadrant (0 upper right, 1 lower right, 2 lower left, 3
 * upper left).
 */
data class OrbitQuadrant(val touches: Int, val meanMs: Long?, val misses: Int)

object Orbit {
    const val ID = "orbit"
    const val SINGLE = 0
    const val DOUBLE = 1
    const val INVERSE = 2
    const val OK = 0
    const val WRONG = 1
    const val DOUBLE_MISSED = 2
    const val TIME = 3
    const val ROUNDS = 12

    // ---------------------------------------------------------------------------------------------
    // AJUSTES DE ÓRBITAS: todos los números que se tocan para cambiar el ritmo o la dificultad.
    // Tiempos en milisegundos. Tras cambiarlos: ./gradlew :lab-core:test (OrbitTest los comprueba).
    // ---------------------------------------------------------------------------------------------

    /** Duración de cada luz del camino. Igual en todas las rondas, niveles y modos. */
    const val STEP_MS = 1100L
    /** Pausa con "OBSERVA" antes de que empiece el camino. */
    const val INTRO_MS = 700L
    /** Duración del giro de la órbita (rondas 9–12). */
    const val TURN_MS = 1350L
    /** Cuánto se ve "BIEN" o "FALLO" antes de pasar a la ronda siguiente. */
    const val VERDICT_OK_MS = 1150L
    const val VERDICT_FAIL_MS = 1500L
    /**
     * Tiempo para responder: base + por cada luz + por cada nodo especial + extra por luz en
     * Espejo.
     */
    const val ANSWER_BASE_MS = 2400L
    const val ANSWER_PER_LIGHT_MS = 1050L
    const val ANSWER_PER_SPECIAL_MS = 400L
    const val ANSWER_MIRROR_PER_LIGHT_MS = 250L
    /** Margen para el segundo toque de un nodo de doble anillo. */
    const val DOUBLE_WINDOW_MS = 500L
    /** Dos toques en el mismo nodo más seguidos que esto cuentan como uno (rebote del dedo). */
    const val DEBOUNCE_MS = 70L
    /** Lo que tarda en apagarse la estela del cometa. */
    const val TRAIL_MS = 600L
    /** Destellos señuelo: cuánto dura cada uno y desde qué ronda (0 = primera) aparecen 1 y 2. */
    const val DECOY_LIFE_MS = 420L
    const val ONE_DECOY_FROM_ROUND = 6
    const val TWO_DECOYS_FROM_ROUND = 9
    /** Bono de enfoque, en % de los puntos de la ronda. */
    const val FOCUS_BONUS = 25
    val quadrantNames =
        listOf("superior derecho", "inferior derecho", "inferior izquierdo", "superior izquierdo")
    val stageNames = listOf("Calibración espacial", "Polaridad y doble pulso", "Rotación orbital")
    val stageTitles =
        listOf(
            "Recuerda el camino de luz",
            "Doble anillo: dos toques · Magenta: el opuesto",
            "La órbita girará antes de tu turno",
        )

    /** 0-based [round] → phase 0, 1 or 2 (endless play keeps cycling through the 12 rounds). */
    fun stage(round: Int) =
        (round.coerceAtLeast(0) % ROUNDS).let { if (it < 4) 0 else if (it < 8) 1 else 2 }

    fun nodes(mode: OrbitMode, round: Int) =
        if (mode == OrbitMode.EXPAND) listOf(6, 8, 10)[stage(round)] else 8

    fun opposite(node: Int, nodes: Int) = (node + nodes / 2) % nodes

    /**
     * Screen angle of [node] after the orbit turned [turn] positions, in degrees clockwise from the
     * top.
     */
    fun angle(node: Int, nodes: Int, turn: Int) =
        (((node + turn) % nodes + nodes) % nodes) * 360.0 / nodes

    /** Quadrant of a screen angle (clockwise from the top): 0 upper right … 3 upper left. */
    fun quadrant(degrees: Double) =
        ((((degrees % 360.0) + 360.0) % 360.0 + .5) % 360.0 / 90.0).toInt().coerceIn(0, 3)

    fun quadrant(node: Int, nodes: Int, turn: Int) = quadrant(angle(node, nodes, turn))

    /**
     * Clock hour (1..12) of a screen angle, to name a node for screen readers without numbering it.
     */
    fun clock(degrees: Double) =
        Math.round((((degrees % 360.0) + 360.0) % 360.0) / 30.0).toInt().let {
            if (it % 12 == 0) 12 else it % 12
        }

    /** Error code names for the summary. */
    fun errorText(error: Int) =
        when (error) {
            WRONG -> "era este nodo"
            DOUBLE_MISSED -> "era doble toque"
            TIME -> "sin tiempo"
            else -> ""
        }

    fun make(level: Int, seed: Long, round: Int, mode: OrbitMode = OrbitMode.CLASSIC): OrbitTask {
        require(level in 1..10)
        val r = Random(seed)
        val unit = round.coerceAtLeast(0) % ROUNDS
        val stage = stage(unit)
        val local = unit % 4
        val n = nodes(mode, unit)
        // Path length: the phase sets the base and the level adds up to three more lights.
        val base =
            when (stage) {
                0 -> listOf(3, 4, 4, 5)[local]
                1 -> listOf(4, 5, 5, 6)[local]
                else -> listOf(4, 5, 5, 6)[local]
            }
        val length =
            (base +
                    (level - 1) / 3 +
                    (if (mode == OrbitMode.EXPAND && round >= ROUNDS)
                        (round / ROUNDS).coerceAtMost(3)
                    else 0))
                .coerceAtMost(10)
        val stepMs = STEP_MS
        val nodesSeq = ArrayList<Int>(length)
        var prev = -1
        repeat(length) {
            var k: Int
            do {
                k = r.nextInt(n)
            } while (k == prev)
            nodesSeq += k
            prev = k
        }
        val kinds = IntArray(length) { SINGLE }
        val specials =
            when (stage) {
                1 ->
                    when (local) {
                        0 -> listOf(DOUBLE)
                        1 -> listOf(INVERSE)
                        else -> listOf(DOUBLE, INVERSE).shuffled(r)
                    }
                2 ->
                    if (local >= 2) listOf(if (r.nextBoolean()) DOUBLE else INVERSE)
                    else emptyList()
                else -> emptyList()
            }
        val slots = (0 until length).shuffled(r)
        specials.forEachIndexed { i, k -> kinds[slots[i]] = k }
        val steps = nodesSeq.mapIndexed { i, k -> OrbitStep(k, kinds[i]) }
        val turn =
            if (stage == 2)
                (if (n >= 8 && r.nextBoolean()) 2 else 1) * (if (r.nextBoolean()) 1 else -1)
            else 0
        val showMs = length * stepMs + stepMs * 35 / 100
        val decoyCount =
            if (unit >= TWO_DECOYS_FROM_ROUND) 2 else if (unit >= ONE_DECOY_FROM_ROUND) 1 else 0
        val decoys =
            List(decoyCount) {
                    OrbitDecoy(350L + r.nextLong((showMs - 800L).coerceAtLeast(1L)), 0f, 0f).let { d
                        ->
                        val (x, y) = outside(r)
                        d.copy(x = x, y = y)
                    }
                }
                .sortedBy { it.atMs }
        return OrbitTask(level, seed, round, mode, n, steps, stepMs, turn, decoys)
    }

    /**
     * A point of the board outside the ring of nodes (the ring reaches 0.36 + 0.075 from the
     * centre).
     */
    private fun outside(r: Random): Pair<Float, Float> {
        repeat(80) {
            val x = .07f + r.nextFloat() * .86f
            val y = .07f + r.nextFloat() * .86f
            val dx = x - .5f
            val dy = y - .5f
            if (dx * dx + dy * dy > .475f * .475f) return x to y
        }
        return .08f to .08f
    }

    fun challenge(
        level: Int,
        seed: Long,
        round: Int,
        mode: OrbitMode = OrbitMode.CLASSIC,
    ): Challenge {
        val t = make(level, seed, round, mode)
        return Challenge(
            Kind.ORBIT,
            stageTitles[t.stage],
            targets = t.expected,
            deadlineMs = 600000,
            orbit = t,
            explanation = explanation(t),
        )
    }

    fun explanation(t: OrbitTask) = buildString {
        append("El camino tenía ${t.steps.size} luces")
        if (t.specials > 0) append(", ${t.specials} especiales")
        append(".")
        if (t.mode == OrbitMode.MIRROR) append(" En Espejo se repite del último al primero.")
        if (t.turn != 0) append(" La órbita giró ${kotlin.math.abs(t.turn)*360/t.nodes}°.")
    }

    /** Points of a round: base points, plus 25 % when it was right and every flash was ignored. */
    fun points(t: OrbitTask, ok: Boolean, focus: Boolean) =
        if (!ok) 0 else t.points + (if (focus) t.points * FOCUS_BONUS / 100 else 0)

    /** Running points of a partida, round by round (for the record ghost). */
    fun cumulative(details: List<String>): List<Int> {
        var s = 0
        return details.map {
            s += OrbitLog.decode(it)?.points ?: 0
            s
        }
    }

    /** Mode of a saved partida (from its first round log). */
    fun modeOf(details: List<String>) =
        OrbitMode.of(details.firstNotNullOfOrNull { OrbitLog.decode(it) }?.mode)

    fun attention(details: List<String>): List<OrbitQuadrant> {
        val logs = details.mapNotNull { OrbitLog.decode(it) }
        return (0..3).map { q ->
            val ms = logs.flatMap { l -> l.times.filter { it.first == q }.map { it.second } }
            OrbitQuadrant(
                ms.size,
                if (ms.isEmpty()) null else ms.sum() / ms.size,
                logs.count { it.missQ == q },
            )
        }
    }
}
