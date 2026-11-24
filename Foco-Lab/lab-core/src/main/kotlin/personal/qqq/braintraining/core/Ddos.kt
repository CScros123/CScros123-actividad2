package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * Fase 3 (docs/DISENO-JUEGOS-V2.md §5): Turno anti-DDoS. You are the filter in front of the server:
 * packets arrive on a belt, to the beat; tap the malicious ones to block them and let the
 * legitimate ones through.
 *
 * Paso 2 (docs/AUDITORIA-JUEGOS-V3.md §4.2): a partida is [Ddos.WAVES] chained waves ([Ddos.plan]).
 * Waves 1–2 use the rules the player already knows, in wave 3 the boss seals the new rule of the
 * level and wave 5 is the peak (+[Ddos.PEAK_BPM] BPM). The combo streak crosses waves ([DdosShift]
 * starts from the streak the previous wave left).
 *
 * Packets are structured data; [Ddos.verdict] is the rule engine with an explicit priority
 * (exception > deployment > blocks), and the generator's intended label is checked against it in
 * the tests.
 */
data class DdosPacket(
    val ip: String,
    val port: Int,
    val flag: Int,
    val size: Int,
    val lane: Int = 0,
    val bad: Boolean = false,
    val rule: Int = Ddos.NONE,
) {
    val flagText
        get() = Ddos.flagNames[flag]

    val sizeText
        get() = if (size >= 1000) "${size/1000}.${size%1000/100}k" else "$size"

    val label
        get() = "$ip :$port $flagText $sizeText B"
}

/** [rules] is the full set of the level (after the seal) and [size] the packets of each wave. */
data class DdosLevel(
    val rules: Set<Int>,
    val bpm: Int,
    val goal: Int,
    val lanes: Int,
    val size: Int = Ddos.WAVE,
)

/**
 * One wave of a partida: its rules, tempo, the rule the boss seals when it starts ([Ddos.NONE] if
 * none) and whether it is the peak.
 */
data class DdosWave(
    val index: Int,
    val rules: Set<Int>,
    val bpm: Int,
    val goal: Int,
    val lanes: Int,
    val size: Int,
    val sealed: Int,
    val peak: Boolean,
)

/**
 * One wave. [switchAt] is the packet index from which the deployment opens 8080 (−1 when the level
 * has no deployment).
 */
/**
 * [wave] is the position in the partida (0-based), [sealed] the rule sealed when this wave starts
 * and [peak] the +BPM wave.
 */
data class DdosTask(
    val level: Int,
    val seed: Long,
    val packets: List<DdosPacket>,
    val rules: Set<Int>,
    val bpm: Int,
    val goal: Int,
    val lanes: Int,
    val switchAt: Int,
    val wave: Int = Ddos.SEAL_WAVE,
    val sealed: Int = Ddos.NONE,
    val peak: Boolean = false,
) {
    val blacklist
        get() = Ddos.RATE in rules
}

data class DdosVerdict(val block: Boolean, val rule: Int)

/** Tempo change announced for the next wave (§5 "Zona de reto visible"). */
data class DdosTuning(val bpmDelta: Int = 0, val note: String? = null)

/**
 * What one wave leaves in the round's detail. [failed] counts mistakes per rule, [learned] the
 * lessons earned (album), [streak] the combo streak the next wave inherits.
 */
data class DdosLog(
    val correct: Int,
    val total: Int,
    val blockedLegit: Int,
    val passedBad: Int,
    val maxTier: Int,
    val down: Boolean,
    val score: Int,
    val maxLoad: Int,
    val failed: Map<Int, Int> = emptyMap(),
    val learned: Set<Int> = emptySet(),
    val goal: Int = 85,
    val bpm: Int = 90,
    val streak: Int = 0,
) {
    val accuracy
        get() = if (total == 0) 100 else correct * 100 / total

    val passed
        get() = !down && accuracy >= goal

    val perfect
        get() = !down && correct == total

    fun encode() =
        "correct=$correct;total=$total;legit=$blockedLegit;bad=$passedBad;tier=$maxTier;down=$down;score=$score;load=$maxLoad;failed=${failed.toSortedMap().entries.joinToString(","){"${it.key}:${it.value}"}};learned=${learned.sorted().joinToString(",")};goal=$goal;bpm=$bpm;streak=$streak"

    companion object {
        fun decode(text: String?): DdosLog? =
            runCatching {
                    val m =
                        text!!.split(';').associate {
                            val i = it.indexOf('=')
                            it.substring(0, i) to it.substring(i + 1)
                        }
                    val failed =
                        m["failed"]
                            .orEmpty()
                            .split(',')
                            .filter { it.isNotBlank() }
                            .associate {
                                val (k, v) = it.split(':')
                                k.toInt() to v.toInt()
                            }
                    DdosLog(
                        m.getValue("correct").toInt(),
                        m.getValue("total").toInt(),
                        m.getValue("legit").toInt(),
                        m.getValue("bad").toInt(),
                        m.getValue("tier").toInt(),
                        m.getValue("down").toBooleanStrict(),
                        m.getValue("score").toInt(),
                        m.getValue("load").toInt(),
                        failed,
                        m["learned"]
                            .orEmpty()
                            .split(',')
                            .filter { it.isNotBlank() }
                            .map { it.toInt() }
                            .toSet(),
                        m["goal"]?.toInt() ?: 85,
                        m["bpm"]?.toInt() ?: 90,
                        m["streak"]?.toInt() ?: 0,
                    )
                }
                .getOrNull()
    }
}

/**
 * Beat clock: integer nanoseconds summed frame by frame, beat index computed with integer
 * arithmetic from the total. No float accumulation, so the beat never drifts (acceptance: < 5 ms in
 * 60 s).
 */
class DdosBeat(val bpm: Int) {
    var clockNs = 0L
        private set

    /**
     * Advances the clock and returns how many beats started in this step (the pulse clicks to play:
     * 0 on most frames, 1 on a beat).
     */
    fun advance(dtNs: Long): Int {
        val before = beat
        clockNs += dtNs
        return (beat - before).toInt()
    }

    val beat
        get() = clockNs * bpm / 60_000_000_000L

    val clockMs
        get() = clockNs / 1_000_000L

    fun timeOfBeatNs(k: Long) = k * 60_000_000_000L / bpm
}

object Ddos {
    const val ID = "ddos"
    /**
     * Packets of a wave up to level 6; faster levels carry more ([DdosLevel.size]) so a partida
     * never drops under two minutes.
     */
    const val WAVE = 20
    /**
     * A partida: [WAVES] chained waves, the new rule sealed when wave [SEAL_WAVE] starts,
     * +[PEAK_BPM] BPM in wave [PEAK_WAVE].
     */
    const val WAVES = 5
    const val SEAL_WAVE = 2
    const val PEAK_WAVE = 4
    const val PEAK_BPM = 10
    const val MAX_TIER = 4
    /**
     * Boss briefing before a wave that brings rules (first wave and the seal), short banner before
     * the others, pause after each wave.
     */
    const val BRIEF_MS = 2_000L
    const val BANNER_MS = 900L
    const val AFTER_MS = 900L
    /**
     * Beats between two packets and packet intervals a card takes to rise from the belt entry to
     * the server.
     */
    const val BEATS_PER_PACKET = 2
    const val TRAVEL = 4
    const val MONITOR_IP = "10.0.0.5"
    // Rules, in priority order of the engine (lessons 6+ are extra album cards).
    const val NONE = -1
    const val TELNET = 0
    const val SYN = 1
    const val RATE = 2
    const val DEPLOY = 3
    const val MONITOR = 4
    const val AMP = 5
    const val BLACKLIST = 6
    const val SSH = 7
    const val PRIORITY = 8
    const val LOAD = 9
    const val DNS = 10
    // Flags
    const val ACK = 0
    const val PSH = 1
    const val SYN_OK = 2
    const val SYN_HALF = 3
    const val RESP = 4
    val flagNames = listOf("ACK", "PSH", "SYN+ACK", "SYN sin ACK", "RESP")
    val ruleNames =
        listOf(
            "Bloquea :23",
            "SYN flood",
            "> 3 por IP",
            "8080 despliegue",
            "Monitor 10.0.0.5",
            "Amplificación DNS",
        )
    val ruleShort = listOf(":23 ✕", "SYN ✕", ">3/IP ✕", "8080", "10.0.0.5 ✓", "DNS grande ✕")
    val lessonKeys =
        listOf(
            "telnet",
            "synflood",
            "ratelimit",
            "deploy",
            "exception",
            "amplification",
            "blacklist",
            "ssh",
            "priority",
            "load",
            "dns53",
        )
    val lessonTitles =
        listOf(
            "Telnet (23)",
            "SYN flood",
            "Rate-limit por IP",
            "Despliegue: 8080 abierto",
            "Excepción del monitor",
            "Amplificación DNS",
            "Lista negra",
            "SSH (22) sí pasa",
            "Prioridad de reglas",
            "Carga del servidor",
            "DNS legítimo",
        )
    val lessons =
        listOf(
            "Telnet (23) envía credenciales en claro: bloquéalo y usa SSH (22).",
            "SYN flood: miles de SYN que nunca completan el saludo (falta el ACK) llenan la tabla de conexiones medio abiertas.",
            "Rate-limit: más de 3 paquetes de la misma IP en la ventana; el tercero todavía pasa, el cuarto se bloquea.",
            "Las reglas cambian con los despliegues: cuando 8080 pasa a producción, sus paquetes son legítimos.",
            "El monitor interno 10.0.0.5 siempre pasa: una excepción explícita va antes que cualquier bloqueo.",
            "Amplificación DNS: respuestas enormes que nadie pidió, dirigidas a tu servidor con la IP falsificada.",
            "Una IP en la lista negra se bloquea sola en sus siguientes paquetes: úsala con IPs claramente hostiles.",
            "SSH (22) es la consola cifrada: es el sustituto seguro de Telnet y no se bloquea.",
            "Las reglas se leen por prioridad: excepción > despliegue > bloqueos. La primera que encaja decide.",
            "Cada paquete malicioso que pasa sube la carga; al 100 % el servidor cae y la oleada se pierde.",
            "Las respuestas DNS normales son pequeñas (menos de 512 B): esas sí pasan.",
        )

    /**
     * The level as it ends: every rule after the seal. Each pair of levels adds one rule, sealed in
     * the middle of the partida.
     */
    fun level(l: Int): DdosLevel {
        require(l in 1..10)
        return when (l) {
            1,
            2 -> DdosLevel(setOf(TELNET, SYN), 90, 85, 1)
            3,
            4 -> DdosLevel(setOf(TELNET, SYN, RATE), 100, 85, 1)
            5,
            6 -> DdosLevel(setOf(TELNET, SYN, RATE, DEPLOY), 110, 85, 1)
            7,
            8 -> DdosLevel(setOf(TELNET, SYN, RATE, DEPLOY, MONITOR), 125, 88, 1, 24)
            else -> DdosLevel(setOf(TELNET, SYN, RATE, DEPLOY, MONITOR, AMP), 140, 90, 2, 28)
        }
    }

    /** The rule the boss seals in wave [SEAL_WAVE] of a partida of level [l]: the newest one. */
    fun sealed(l: Int) = level(l).rules.max()

    /**
     * Wave [wave] (0-based) of a partida: known rules first, the new one from [SEAL_WAVE], the peak
     * at [PEAK_WAVE].
     */
    fun plan(l: Int, wave: Int): DdosWave {
        require(wave in 0 until WAVES)
        val lv = level(l)
        val new = sealed(l)
        val open = wave >= SEAL_WAVE
        val rules = if (open) lv.rules else lv.rules - new
        val peak = wave == PEAK_WAVE
        return DdosWave(
            wave,
            rules,
            lv.bpm + if (peak) PEAK_BPM else 0,
            lv.goal,
            if (AMP in rules) 2 else 1,
            lv.size,
            if (wave == SEAL_WAVE) new else NONE,
            peak,
        )
    }

    /** Milliseconds between two packets at [bpm]. */
    fun intervalMs(bpm: Int) = BEATS_PER_PACKET * 60_000L / bpm

    /** When packet [i] enters the belt (ms, from the beat clock). */
    fun arrivalMs(i: Int, bpm: Int) = i.toLong() * BEATS_PER_PACKET * 60_000L / bpm

    /** Length of a wave of [n] packets: the last one reaches the server. */
    fun waveMs(bpm: Int, n: Int = WAVE) = arrivalMs(n - 1, bpm) + TRAVEL * intervalMs(bpm)

    /**
     * Briefing before wave [wave]: the boss's part when it brings rules, a short banner otherwise.
     */
    fun briefMs(wave: Int) = if (wave == 0 || wave == SEAL_WAVE) BRIEF_MS else BANNER_MS

    /** Length of a whole partida without tempo tuning (Q6: 2–5 min at every level). */
    fun partidaMs(l: Int) =
        (0 until WAVES).sumOf { w ->
            val p = plan(l, w)
            briefMs(w) + waveMs(p.bpm, p.size) + AFTER_MS
        }

    /**
     * The rule engine for one packet. [fromIp] is how many packets of the same IP arrived earlier
     * in this wave, [deployed] whether the deployment already opened 8080. Priority: monitor
     * exception > deployment (8080) > telnet > SYN flood > rate-limit > amplification.
     */
    fun verdict(p: DdosPacket, rules: Set<Int>, fromIp: Int, deployed: Boolean): DdosVerdict =
        when {
            MONITOR in rules && p.ip == MONITOR_IP -> DdosVerdict(false, MONITOR)
            DEPLOY in rules && p.port == 8080 -> DdosVerdict(!deployed, DEPLOY)
            TELNET in rules && p.port == 23 -> DdosVerdict(true, TELNET)
            SYN in rules && p.flag == SYN_HALF -> DdosVerdict(true, SYN)
            RATE in rules && fromIp >= 3 -> DdosVerdict(true, RATE)
            AMP in rules && p.port == 53 && p.flag == RESP && p.size >= 1000 ->
                DdosVerdict(true, AMP)
            else -> DdosVerdict(false, NONE)
        }

    /** Verdict of every packet of the wave, in arrival order. */
    fun label(packets: List<DdosPacket>, rules: Set<Int>, switchAt: Int): List<DdosVerdict> {
        val count = HashMap<String, Int>()
        return packets.mapIndexed { i, p ->
            val n = count[p.ip] ?: 0
            count[p.ip] = n + 1
            verdict(p, rules, n, switchAt in 0..i)
        }
    }

    fun label(t: DdosTask) = label(t.packets, t.rules, t.switchAt)

    /**
     * Share of malicious packets by number of active rules: it grows with the level, legitimate
     * traffic always stays above half.
     */
    private val badShare = listOf(.22f, .22f, .30f, .36f, .40f, .44f, .46f)
    /**
     * Documentation and CGNAT ranges, drawn per packet for friends and foes alike: the prefix of an
     * IP never tells who is hostile.
     */
    private val nets =
        listOf(
            "203.0.113",
            "185.220.101",
            "45.155.205",
            "91.240.118",
            "198.51.100",
            "192.0.2",
            "100.64.12",
            "172.16.4",
            "100.72.9",
            "198.18.7",
        )

    /**
     * Wave [wave] of a partida of [level] ([plan]); the default is the seal wave, with every rule
     * of the level. The mix changes from wave to wave: each one draws how many packets every rule
     * gets, so no two waves of a partida ask for the same thing.
     */
    fun make(level: Int, seed: Long, wave: Int = SEAL_WAVE): DdosTask {
        val pl = plan(level, wave)
        val rules = pl.rules
        val r = Random(seed)
        val n = pl.size
        val slots = arrayOfNulls<DdosPacket>(n)
        val used = mutableSetOf(MONITOR_IP)
        fun fresh(prefix: List<String> = nets): String {
            while (true) {
                val ip = "${prefix[r.nextInt(prefix.size)]}.${r.nextInt(2,254)}"
                if (used.add(ip)) return ip
            }
        }
        fun free() = (0 until n).filter { slots[it] == null }
        fun put(i: Int, p: DdosPacket) {
            slots[i] = p
        }
        fun freeSlot(range: IntRange = 0 until n): Int? {
            val f = free().filter { it in range }
            return if (f.isEmpty()) null else f[r.nextInt(f.size)]
        }
        fun bad() = slots.count { it?.bad == true }
        val switchAt = if (DEPLOY in rules) n / 2 else -1
        // Rate-limit: one IP sends 5 ordinary packets; the 4th and 5th are the ones to block.
        if (RATE in rules) {
            val ip = fresh()
            free().shuffled(r).take(5).sorted().forEachIndexed { k, i ->
                put(
                    i,
                    DdosPacket(
                        ip,
                        443,
                        if (k % 2 == 0) ACK else PSH,
                        r.nextInt(4, 15) * 100,
                        bad = k >= 3,
                        rule = if (k >= 3) RATE else NONE,
                    ),
                )
            }
        }
        if (DEPLOY in rules) {
            repeat(2) {
                freeSlot(0 until switchAt)?.let {
                    put(
                        it,
                        DdosPacket(
                            fresh(),
                            8080,
                            ACK,
                            r.nextInt(3, 12) * 100,
                            bad = true,
                            rule = DEPLOY,
                        ),
                    )
                }
            }
            repeat(2) {
                freeSlot(switchAt until n)?.let {
                    put(it, DdosPacket(fresh(), 8080, PSH, r.nextInt(3, 12) * 100, rule = DEPLOY))
                }
            }
        }
        if (MONITOR in rules) {
            // The monitor polls through Telnet and SNMP: the exception wins over the port-23 block
            // (and over a half-open SYN in its seal wave).
            freeSlot()?.let {
                put(it, DdosPacket(MONITOR_IP, 23, ACK, r.nextInt(60, 180), rule = MONITOR))
            }
            freeSlot()?.let {
                put(it, DdosPacket(MONITOR_IP, 161, ACK, r.nextInt(60, 180), rule = MONITOR))
            }
            if (pl.sealed == MONITOR)
                freeSlot()?.let {
                    put(it, DdosPacket(MONITOR_IP, 443, SYN_HALF, 60, rule = MONITOR))
                }
        }
        if (AMP in rules) {
            repeat(2) {
                freeSlot()?.let {
                    put(
                        it,
                        DdosPacket(
                            fresh(),
                            53,
                            RESP,
                            r.nextInt(28, 43) * 100,
                            lane = 1,
                            bad = true,
                            rule = AMP,
                        ),
                    )
                }
            }
            repeat(2) {
                freeSlot()?.let {
                    put(
                        it,
                        DdosPacket(fresh(listOf("10.0.53")), 53, RESP, r.nextInt(80, 480), lane = 1),
                    )
                }
            }
        }
        fun telnet() =
            freeSlot()?.let {
                put(
                    it,
                    DdosPacket(
                        fresh(),
                        23,
                        listOf(ACK, SYN_OK)[r.nextInt(2)],
                        r.nextInt(60, 121),
                        bad = true,
                        rule = TELNET,
                    ),
                )
            }
        fun syn() =
            freeSlot()?.let {
                put(
                    it,
                    DdosPacket(
                        fresh(),
                        listOf(80, 443)[r.nextInt(2)],
                        SYN_HALF,
                        60,
                        bad = true,
                        rule = SYN,
                    ),
                )
            }
        fun amp() =
            freeSlot()?.let {
                put(
                    it,
                    DdosPacket(
                        fresh(),
                        53,
                        RESP,
                        r.nextInt(28, 43) * 100,
                        lane = 1,
                        bad = true,
                        rule = AMP,
                    ),
                )
            }
        // Every simple rule gets at least two packets (three for the rule sealed in this wave)…
        if (SYN in rules) repeat(if (pl.sealed == SYN) 3 else 2) { syn() }
        repeat(2) { telnet() }
        // …and the rest of the malicious share is drawn among them, so each wave leans on a
        // different rule.
        val target = ((n * badShare[rules.size]).toInt() + r.nextInt(-1, 2)).coerceAtLeast(3)
        val extra = buildList {
            add(TELNET)
            if (SYN in rules) add(SYN)
            if (AMP in rules) add(AMP)
        }
        while (bad() < target && free().isNotEmpty()) when (extra[r.nextInt(extra.size)]) {
            TELNET -> telnet()
            SYN -> syn()
            else -> amp()
        }
        // Legitimate traffic: each client IP at most twice (never trips the rate-limit), HTTPS,
        // HTTP and SSH.
        val pool = List(n / 2) { fresh() }
        val uses = IntArray(pool.size)
        for (i in 0 until n) if (slots[i] == null) {
            var k = r.nextInt(pool.size)
            while (uses[k] >= 2) k = (k + 1) % pool.size
            uses[k]++
            put(
                i,
                DdosPacket(
                    pool[k],
                    listOf(443, 443, 80, 22)[r.nextInt(4)],
                    listOf(ACK, PSH, SYN_OK)[r.nextInt(3)],
                    r.nextInt(60, 1461),
                ),
            )
        }
        return DdosTask(
            level,
            seed,
            slots.map { it!! },
            rules,
            pl.bpm,
            pl.goal,
            pl.lanes,
            switchAt,
            wave,
            pl.sealed,
            pl.peak,
        )
    }

    /** Three perfect waves in a row: +5 BPM; server down in the last one: −10 BPM. Announced. */
    fun tuning(previous: List<DdosLog>): DdosTuning {
        val last = previous.lastOrNull()
        return when {
            last?.down == true ->
                DdosTuning(-10, "🧭 −10 BPM: el servidor cayó en la oleada anterior")
            previous.size >= 3 && previous.takeLast(3).all { it.perfect } ->
                DdosTuning(5, "⚡ +5 BPM: tres oleadas perfectas")
            else -> DdosTuning()
        }
    }

    /**
     * Combo tier of a streak: ×2 at 5, ×3 at 10, ×4 at 15. The streak crosses waves, so every tier
     * is reachable (Q8).
     */
    fun tier(streak: Int) =
        when {
            streak >= 15 -> 4
            streak >= 10 -> 3
            streak >= 5 -> 2
            else -> 1
        }

    /**
     * What a whole partida adds up to: packets (what [Adaptive] counts), points and the best combo.
     */
    fun summary(logs: List<DdosLog>) =
        DdosRun(
            logs.sumOf { it.correct },
            logs.sumOf { it.total },
            logs.sumOf { it.score },
            logs.maxOfOrNull { it.maxTier } ?: 1,
            logs.count { it.passed },
            logs.size,
        )

    /**
     * Decoded logs of a saved partida in the 5-wave format, or null (older 12-wave results, other
     * games).
     */
    fun run(details: List<String>): DdosRun? {
        if (details.size != WAVES) return null
        val logs = details.map { DdosLog.decode(it) ?: return null }
        return summary(logs)
    }

    fun explanation(t: DdosTask) =
        "Reglas de la oleada: " +
            t.rules.sorted().joinToString(" · ") { ruleNames[it] } +
            ".\n" +
            t.rules.sorted().joinToString("\n") { lessons[it] }

    /** Rule most failed over a run. */
    fun weakest(logs: List<DdosLog>): Int? =
        logs
            .flatMap { it.failed.entries }
            .groupBy({ it.key }, { it.value })
            .mapValues { it.value.sum() }
            .maxByOrNull { it.value }
            ?.key
}

/** Totals of a partida ([Ddos.summary]). */
data class DdosRun(
    val correct: Int,
    val total: Int,
    val score: Int,
    val maxTier: Int,
    val passed: Int,
    val waves: Int,
) {
    val accuracy
        get() = if (total == 0) 100 else correct * 100 / total
}

/**
 * Live state of one wave, independent from drawing: the board calls [arrive] when a packet enters
 * the belt, [block] on a tap, [deliver] when a card reaches the server and [decay] with the clock.
 * Tests drive it directly. [carry] is the combo streak inherited from the previous wave of the
 * partida.
 */
class DdosShift(val task: DdosTask, carry: Int = 0) {
    val truth = Ddos.label(task)
    /**
     * 0 pending, 1 blocked by the player, 2 blocked by the blacklist, 3 delivered, 4 legit blocked
     * (returns and is delivered without scoring again).
     */
    val state = IntArray(task.packets.size)
    val blacklist = linkedSetOf<String>()
    var streak = carry.coerceAtLeast(0)
        private set

    var maxTier = 1
        private set

    var correct = 0
        private set

    var blockedLegit = 0
        private set

    var passedBad = 0
        private set

    var score = 0
        private set

    var load = 0f
        private set

    var maxLoad = 0f
        private set

    val down
        get() = load >= 100f

    private val failed = IntArray(6)
    private val learned = mutableSetOf<Int>()
    val tier
        get() = Ddos.tier(streak)

    enum class Result {
        OK,
        LEGIT_BLOCKED,
        BAD_PASSED,
        IGNORED,
    }

    private fun good(rule: Int) {
        correct++
        streak++
        val t = Ddos.tier(streak)
        if (t > maxTier) maxTier = t
        score += t
        if (rule >= 0) learned += rule
    }

    private fun bad(rule: Int) {
        // Sin precipicio: un error baja un escalón del combo (×4 → ×3), no lo borra entero.
        streak = ((Ddos.tier(streak) - 2) * 5).coerceAtLeast(0)
        if (rule in 0..5) failed[rule]++
    }

    /** Packet [i] enters the belt: a blacklisted IP is blocked on the spot. */
    fun arrive(i: Int): Result {
        if (state[i] != 0 || down) return Result.IGNORED
        if (task.packets[i].ip in blacklist) return decide(i, 2)
        return Result.IGNORED
    }

    fun block(i: Int): Result =
        if (i !in state.indices || state[i] != 0 || down) Result.IGNORED else decide(i, 1)

    private fun decide(i: Int, how: Int): Result {
        val v = truth[i]
        return if (v.block) {
            state[i] = how
            good(v.rule)
            if (how == 2) learned += Ddos.BLACKLIST
            Result.OK
        } else {
            state[i] = 4
            blockedLegit++
            bad(v.rule)
            Result.LEGIT_BLOCKED
        }
    }

    /**
     * Adds the IP of packet [i] to the blacklist (levels with rate-limit) and blocks that packet.
     */
    fun ban(i: Int): Result {
        if (!task.blacklist || i !in state.indices || down) return Result.IGNORED
        blacklist += task.packets[i].ip
        return block(i)
    }

    /** The card of packet [i] reached the server. */
    fun deliver(i: Int): Result {
        if (down) return Result.IGNORED
        if (state[i] == 4) {
            state[i] = 3
            return Result.IGNORED
        }
        if (state[i] != 0) return Result.IGNORED
        state[i] = 3
        val v = truth[i]
        val p = task.packets[i]
        return if (!v.block) {
            good(v.rule)
            if (v.rule == Ddos.MONITOR) learned += Ddos.PRIORITY
            if (p.port == 22) learned += Ddos.SSH
            if (p.port == 53) learned += Ddos.DNS
            Result.OK
        } else {
            passedBad++
            bad(v.rule)
            load = (load + LOAD_HIT).coerceAtMost(100f)
            if (load > maxLoad) maxLoad = load
            Result.BAD_PASSED
        }
    }

    /** Load falls 2 % per second. */
    fun decay(dtMs: Long) {
        if (!down && load > 0f) load = (load - dtMs * .002f).coerceAtLeast(0f)
    }

    companion object {
        /**
         * Load added by each malicious packet that reaches the server: three close together bring
         * it down.
         */
        const val LOAD_HIT = 40f
    }

    fun log(bpm: Int): DdosLog {
        val l = learned.toMutableSet()
        if (!down && correct * 100 >= task.goal * task.packets.size) l += Ddos.LOAD
        return DdosLog(
            correct,
            task.packets.size,
            blockedLegit,
            passedBad,
            maxTier,
            down,
            score,
            maxLoad.toInt(),
            (0 until 6).filter { failed[it] > 0 }.associateWith { failed[it] },
            l,
            task.goal,
            bpm,
            streak,
        )
    }
}
