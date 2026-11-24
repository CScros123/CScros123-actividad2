package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * IPv4 helpers shared by the subnet board, its generator and its tests. Addresses are unsigned
 * 32-bit values kept in a Long.
 */
object Ipv4 {
    const val ALL = 0xFFFFFFFFL

    /** Mask with [p] leading ones (0..32). */
    fun mask(p: Int): Long = if (p <= 0) 0L else (ALL shl (32 - p)) and ALL

    fun network(a: Long, p: Int) = a and mask(p)

    fun broadcast(a: Long, p: Int) = network(a, p) or (mask(p) xor ALL)

    /** Usable hosts: 2^(32−p) − 2. /31 and /32 are shown as 0 (no classic subnet). */
    fun hosts(p: Int): Long = if (p >= 31) 0L else (1L shl (32 - p)) - 2

    fun text(x: Long) = "${x shr 24 and 255}.${x shr 16 and 255}.${x shr 8 and 255}.${x and 255}"

    /** Bit [i] counted from the left (0 = most significant). */
    fun bit(a: Long, i: Int): Int = ((a shr (31 - i)) and 1L).toInt()

    fun octet(x: Long, k: Int): Long = (x shr (8 * (3 - k))) and 255

    /**
     * Number of equal leading bits: the longest prefix that keeps [a] and [b] in the same network.
     */
    fun common(a: Long, b: Long): Int {
        var p = 0
        while (p < 32 && bit(a, p) == bit(b, p)) p++
        return p
    }
}

/**
 * Subnet board. [answer] is the only prefix accepted; [other] is the second address of a PAIR task
 * (−1 otherwise). [hints] shows live readouts that give the answer away (low levels).
 */
data class SubnetTask(
    val format: Int,
    val addr: Long,
    val other: Long,
    val hosts: Long,
    val answer: Int,
    val start: Int,
    val hints: Boolean,
)

/**
 * One inbox card. [clueStart]..[clueEnd] (end exclusive) is the exact span of [text] highlighted
 * after answering.
 */
data class InboxTask(
    val text: String,
    val phishing: Boolean,
    val clueStart: Int,
    val clueEnd: Int,
    val channel: Int,
) {
    val clue
        get() = text.substring(clueStart, clueEnd)
}

data class FirewallRow(val port: Int, val service: String, val use: String)

/**
 * Firewall panel: [start] is the initial open/closed state of each row, [expected] the state that
 * solves the task. [labels]: the rows show the service next to the port (levels 1–2 only). [key]
 * names the scenario (no repeats in a session), [why] is the one-line lesson shown once the rules
 * are applied and [hint] the method (never the answer). [rules] is the ordered allow/deny list of
 * an ORDER task (empty otherwise): [expected] is what [FirstMatch] lets through.
 */
data class FirewallTask(
    val format: Int,
    val rows: List<FirewallRow>,
    val start: List<Boolean>,
    val expected: List<Boolean>,
    val labels: Boolean = true,
    val key: String = "",
    val why: String = "",
    val hint: String = "",
    val rules: List<FwRule> = emptyList(),
) {
    val expectedOpen
        get() = expected.indices.filter { expected[it] }

    fun accepts(open: List<Int>) = open.toSet() == expectedOpen.toSet()
}

/**
 * Bit switches (Lote B): [width] switches, starting at value [start]; the round is solved when they
 * add up to [target]. [hints] shows the live decimal sum.
 */
data class BitsTask(
    val format: Int,
    val width: Int,
    val start: Int,
    val target: Int,
    val hints: Boolean,
) {
    /** Place values from left to right: 2^(width−1) … 1. */
    val weights
        get() = (width - 1 downTo 0).map { 1 shl it }

    fun bit(value: Int, i: Int) = (value shr (width - 1 - i)) and 1
}

/** What a played round leaves behind for the variety rule and spaced repetition. */
data class RoundMark(val item: Long, val format: Int, val correct: Boolean)

/**
 * Pilot answer check. FIREWALL: [picked] are the indices left open. Other kinds: [picked] holds the
 * single answer.
 */
fun Challenge.accepts(picked: List<Int>): Boolean =
    when (kind) {
        Kind.FIREWALL -> firewall?.accepts(picked) ?: false
        else -> picked == listOf(answer)
    }

/**
 * Fase 7, lote piloto: Subredes, Phishing y Puertos con mecánica propia. The 12-round session uses
 * [make] through [Rounds]; Relámpago and the 3-minute practice use it through [Quick] (Lote B).
 * [Generator.make] keeps the 4-option versions (history and tests). Every challenge depends only on
 * (game, level, seed, format), so a failed item can be rebuilt exactly for review.
 */
object Pilot {
    val ids = setOf("subnet", "phishing", "ports", "bits")
    // Subnet formats
    const val HOSTS = 0
    const val MASK = 1
    const val PAIR = 2
    // Phishing formats (channel of the message)
    const val MAIL = 0
    const val LINK = 1
    // Ports formats
    const val BLOCK = 0
    const val ALLOW = 1
    const val AUDIT = 2
    const val ORDER = 3
    // Bits formats: compose a number from zero, or edit a register that already holds another value
    const val BUILD = 0
    const val EDIT = 1

    fun formats(id: String, l: Int): List<Int> =
        when (id) {
            "subnet" -> if (l <= 3) listOf(HOSTS, MASK) else listOf(HOSTS, MASK, PAIR)
            "phishing" -> listOf(MAIL, LINK)
            // ORDER (rule order) arrives at 8 and is half of the rounds at 9–10; the repeated entry
            // only weights the draw.
            "ports" ->
                when {
                    l <= 3 -> listOf(BLOCK, ALLOW)
                    l <= 7 -> listOf(BLOCK, ALLOW, AUDIT)
                    l == 8 -> listOf(BLOCK, ALLOW, AUDIT, ORDER)
                    else -> listOf(ORDER, AUDIT, ORDER, BLOCK)
                }
            "bits" -> listOf(BUILD, EDIT)
            else -> listOf(0)
        }

    /**
     * Always consumes one draw, so a forced format rebuilds the same challenge as the original
     * round.
     */
    private fun format(r: Random, allowed: List<Int>, forced: Int?, avoid: Int): Int {
        val drawn = allowed[r.nextInt(allowed.size)]
        return when {
            forced != null -> forced
            drawn == avoid && allowed.size > 1 ->
                allowed[(allowed.indexOf(drawn) + 1) % allowed.size]
            else -> drawn
        }
    }

    /**
     * [exclude] holds what this session already showed (bait texts, firewall scenario keys):
     * another one is picked while any is left.
     */
    fun make(
        id: String,
        level: Int,
        seed: Long,
        forced: Int? = null,
        avoid: Int = -1,
        exclude: Set<String> = emptySet(),
    ): Challenge {
        require(level in 1..10)
        val r = Random(seed)
        val f = format(r, formats(id, level), forced, avoid)
        val c =
            when (id) {
                "subnet" -> subnet(level, r, f)
                "phishing" -> phishing(level, r, f, exclude)
                "ports" -> ports(level, r, f, exclude)
                "bits" -> bits(level, r, f)
                else -> error("Juego sin mecánica piloto: $id")
            }
        return c.copy(format = f, item = seed)
    }

    // ---------- Subredes ----------
    private fun startFor(p: Int) = if (p == 24) 20 else 24

    private fun subnet(l: Int, r: Random, f: Int): Challenge {
        val first = listOf(10L, 172L, 192L)[r.nextInt(3)]
        val addr = (first shl 24) or r.nextLong(0, 1L shl 24)
        val hints = l < 7
        val deadline = (60000 - l * 2500).toLong()
        return when (f) {
            HOSTS -> {
                val p =
                    r.nextInt(
                        when {
                            l <= 3 -> 25
                            l <= 6 -> 22
                            else -> 16
                        },
                        31,
                    )
                val n = r.nextLong(maxOf(2L, Ipv4.hosts(p + 1) + 1), Ipv4.hosts(p) + 1)
                Challenge(
                    Kind.SUBNET,
                    "Una red necesita $n equipos.\nAjusta la máscara de la subred más pequeña donde quepan.",
                    answer = p,
                    deadlineMs = deadline,
                    subnet = SubnetTask(f, addr, -1L, n, p, startFor(p), hints),
                    explanation =
                        "/$p: 2^${32-p} − 2 = ${Ipv4.hosts(p)} hosts ≥ $n; un /${p+1} solo admite ${Ipv4.hosts(p+1)}.\nCada bit que pasa de red a host duplica las direcciones.",
                )
            }
            MASK -> {
                val p =
                    r.nextInt(
                        when {
                            l <= 3 -> 24
                            l <= 6 -> 16
                            else -> 8
                        },
                        31,
                    )
                val m = Ipv4.mask(p)
                val full = p / 8
                val k = minOf(full, 3)
                val oct = Ipv4.octet(m, k)
                val rest =
                    if (p % 8 != 0) " y $oct = ${oct.toString(2).padStart(8,'0')} (${p%8} unos)"
                    else ""
                Challenge(
                    Kind.SUBNET,
                    "La máscara es ${Ipv4.text(m)}.\nArrastra la frontera hasta su prefijo.",
                    answer = p,
                    deadlineMs = deadline,
                    subnet = SubnetTask(f, addr, -1L, Ipv4.hosts(p), p, startFor(p), hints),
                    explanation =
                        "${Ipv4.text(m)} = /$p: $full ${if(full==1)"octeto" else "octetos"} a 255 (${full*8} bits)$rest.\nEl prefijo cuenta los unos de la máscara.",
                )
            }
            else -> {
                // First differing bit k (from the right); bits below it are random.
                val k = r.nextInt(if (l <= 6) 8 else 16)
                val low = (1L shl k) - 1
                val other = ((addr xor (1L shl k)) and (Ipv4.ALL xor low)) or r.nextLong(1L shl k)
                val p = 31 - k
                Challenge(
                    Kind.SUBNET,
                    "¿Cuál es el prefijo más largo con el que A y B siguen en la misma red?",
                    answer = p,
                    deadlineMs = deadline,
                    subnet = SubnetTask(f, addr, other, Ipv4.hosts(p), p, startFor(p), hints),
                    explanation =
                        "A y B coinciden en los primeros $p bits: /$p deja a ambas en ${Ipv4.text(Ipv4.network(addr,p))}.\nCon /${p+1} el bit ${p+1} ya es distinto y quedarían en redes separadas.",
                )
            }
        }
    }

    // ---------- Phishing ----------
    fun channel(text: String) = if (text.startsWith("De:")) MAIL else LINK

    private fun phishing(l: Int, r: Random, f: Int, exclude: Set<String>): Challenge {
        val tier = 1 + (l - 1) * 3 / 10
        val pool = NetSec.baits.filter { it.tier in (tier - 1).coerceAtLeast(1)..tier }
        val inChannel = pool.filter { channel(it.text) == f }.ifEmpty { pool }
        // Unseen baits of the same channel first, then of the other channel; only an exhausted pool
        // repeats.
        val fresh =
            inChannel
                .filter { it.text !in exclude }
                .ifEmpty { pool.filter { it.text !in exclude } }
                .ifEmpty { inChannel }
        val b = fresh[r.nextInt(fresh.size)]
        val start = b.text.indexOf(b.mark)
        return Challenge(
            Kind.INBOX,
            "Desliza a la derecha si es seguro y a la izquierda si es una trampa.",
            listOf("Seguro", "Trampa"),
            if (b.phishing) 1 else 0,
            deadlineMs = (22000 - l * 1000).toLong(),
            inbox = InboxTask(b.text, b.phishing, start, start + b.mark.length, channel(b.text)),
            explanation = (if (b.phishing) "Trampa: " else "Seguro: ") + b.clue,
        )
    }

    // ---------- Bits ----------
    private fun bits(l: Int, r: Random, f: Int): Challenge {
        val n = 4 + (l - 1) * 4 / 9
        val max = 1 shl n
        val v = r.nextInt(1, max)
        var start = 0
        if (f == EDIT) {
            start = r.nextInt(1, max - 1)
            if (start >= v) start++
        }
        val t = BitsTask(f, n, start, v, l < 8)
        val bin = { x: Int -> x.toString(2).padStart(n, '0') }
        val sum = t.weights.filter { v and it != 0 }
        val changed =
            t.weights.indices.filter { t.bit(start, it) != t.bit(v, it) }.map { t.weights[it] }
        val prompt =
            if (f == BUILD) "Enciende los bits que suman $v."
            else "El registro vale $start (${bin(start)}).\nCambia los bits para que valga $v."
        val explanation =
            "${bin(v)} = ${sum.joinToString(" + ")} = $v.\nCada posición vale el doble que la de su derecha: ${t.weights.joinToString(" · ")}." +
                if (f == EDIT)
                    "\nHabía que cambiar ${changed.size} ${if(changed.size==1)"bit" else "bits"}: ${changed.joinToString(", ")}."
                else ""
        return Challenge(
            Kind.BITS,
            prompt,
            answer = v,
            deadlineMs = (30000 - l * 1500).toLong(),
            bits = t,
            explanation = explanation,
        )
    }

    // ---------- Puertos ----------
    /** Scenario prompts and, from level 8, rule order (Paso 1, see Firewall.kt). */
    private fun ports(l: Int, r: Random, f: Int, exclude: Set<String>): Challenge =
        PortScenarios.make(l, r, f, exclude)
}

/**
 * Round planner for the 12-round session (and the untimed mode).
 * - Variety: a pilot game never repeats the format of the previous round.
 * - Spaced repetition: a failed pilot item comes back, rebuilt from its seed, after at least [GAP]
 *   other rounds; at most [MAX_REPEATS] times, and only while its latest attempt is still a
 *   failure. Other games keep [Generator.make] exactly as before.
 */
object Rounds {
    const val GAP = 2
    const val MAX_REPEATS = 2

    fun make(
        game: String,
        level: Int,
        seed: Long,
        history: List<RoundMark> = emptyList(),
        unit: Int = -1,
    ): Challenge {
        if (game !in Pilot.ids) return Generator.make(game, level, seed, unit)
        val prev = history.lastOrNull()?.format ?: -1
        val due = due(history, prev)
        val (before, seen) =
            if (game == "phishing" || game == "ports") replay(level, history, game)
            else emptyMap<Long, Set<String>>() to emptySet()
        return if (due != null)
            Pilot.make(
                    game,
                    level,
                    due.item,
                    forced = due.format,
                    exclude = before[due.item] ?: emptySet(),
                )
                .copy(review = true)
        else Pilot.make(game, level, seed, avoid = prev, exclude = seen)
    }

    /**
     * Anzuelo without repeats: rebuilds the baits already played in order (each one excluded the
     * earlier ones), so no extra state is saved. Returns, per item, the exclusion it was built with
     * (to rebuild a review exactly) and every text seen.
     */
    fun replay(
        level: Int,
        history: List<RoundMark>,
        game: String = "phishing",
    ): Pair<Map<Long, Set<String>>, Set<String>> {
        val before = mutableMapOf<Long, Set<String>>()
        val seen = linkedSetOf<String>()
        for (m in history) {
            if (m.item in before) continue // a review shows the same bait again
            before[m.item] = seen.toSet()
            Pilot.make(game, level, m.item, forced = m.format, exclude = before[m.item]!!).let { c
                ->
                (c.inbox?.text ?: c.firewall?.key)?.let { seen += it }
            }
        }
        return before to seen
    }

    /**
     * The oldest failed item that is due for review and does not repeat the previous format, or
     * null.
     */
    fun due(history: List<RoundMark>, prev: Int): RoundMark? {
        for (i in history.indices) {
            val m = history[i]
            if (m.correct || i > history.size - 1 - GAP || m.format == prev) continue
            if ((i + 1 until history.size).any { history[it].item == m.item })
                continue // a later attempt exists: judge that one instead
            if (history.count { it.item == m.item } > MAX_REPEATS) continue
            return m
        }
        return null
    }
}

/**
 * Quick modes (Relámpago and the 3-minute practice, Lote B): pilot games use their boards, the rest
 * the classic generator. [seen] holds bait texts already shown, so Anzuelo does not repeat while
 * unseen ones remain.
 */
object Quick {
    fun make(game: String, level: Int, seed: Long, seen: Set<String> = emptySet()): Challenge =
        if (game in Pilot.ids) Pilot.make(game, level, seed, exclude = seen)
        else Generator.make(game, level, seed)
}
