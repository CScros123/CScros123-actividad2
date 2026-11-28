package personal.qqq.braintraining.core

import kotlin.random.Random

/**
 * Fase 2 (docs/DISENO-JUEGOS-V2.md §3): Vigía de alertas. Night shift at the SOC: log lines rise
 * through a monitor and a few of them are real attacks. One round of the 12-round flow is one ~30 s
 * "tramo".
 *
 * Lines are structured ([VigilLine.kind] + fields) so the anomaly rules ([Vigil.triggers]) read
 * data, not text; the text is only for the eyes. The generator places the attack groups and fills
 * the rest with noise that, by construction, never satisfies a rule; the tests check both sides
 * with the rule engine.
 */
data class VigilLine(
    val time: Int,
    val src: String,
    val kind: Int,
    val user: String = "",
    val port: Int = 0,
    val city: String = "",
    val mb: Int = 0,
    val channel: Int = 0,
    val group: Int = -1,
    val why: Int = Vigil.WHY_NORMAL,
) {
    val clock
        get() = "%02d:%02d:%02d".format(time / 3600 % 24, time / 60 % 60, time % 60)

    val event
        get() =
            when (kind) {
                Vigil.FAIL -> "Failed pwd $user"
                Vigil.LOGIN -> "login ok u=$user"
                Vigil.LOGOUT -> "logout u=$user"
                Vigil.VPN -> "vpn ok u=$user $city"
                Vigil.SYN -> ":$port syn"
                Vigil.OUT -> if (mb >= 1000) "out ${mb/1000}.${mb%1000/100} GB" else "out $mb MB"
                Vigil.GET -> "GET /api 200"
                Vigil.DNS -> "dns query"
                Vigil.NTP -> "ntp sync"
                else -> "cron backup ok"
            }
}

/**
 * An attack: its [lines] (stream indices, ascending), the [pattern] and [detectAt], the line where
 * the rule fires.
 */
data class VigilGroup(val pattern: Int, val lines: List<Int>, val detectAt: Int) {
    val last
        get() = lines.last()
}

data class VigilLevel(
    val patterns: Int,
    val ratePercent: Int,
    val intervalMs: Long,
    val goal: Int,
    val maxFalse: Int,
    val decoys: Boolean,
    val channels: Int,
    val targets: Int,
)

/**
 * One tramo. [intervalMs] is the base time between two lines (the board ramps it up 3 % every 10
 * s).
 */
data class VigilTask(
    val level: Int,
    val seed: Long,
    val guard: String,
    val lines: List<VigilLine>,
    val groups: List<VigilGroup>,
    val intervalMs: Long,
    val goal: Int,
    val maxFalse: Int,
    val channels: Int,
) {
    /** Group of line [i], or null for noise. */
    fun groupOf(i: Int) = lines.getOrNull(i)?.group?.takeIf { it >= 0 }?.let { groups[it] }
}

/** What one tramo leaves in the round's detail. Shields are counted in halves (3 shields = 6). */
data class VigilLog(
    val hits: Int,
    val lapses: Int,
    val falses: Int,
    val targets: Int,
    val shields: Int,
    val freezes: Int,
    val found: Set<Int> = emptySet(),
    val missed: Set<Int> = emptySet(),
    val goal: Int = 80,
    val maxFalse: Int = 2,
) {
    val detected
        get() = if (targets == 0) 100 else hits * 100 / targets

    val passed
        get() = shields > 0 && detected >= goal && falses <= maxFalse

    val perfect
        get() = lapses == 0 && falses == 0 && hits == targets

    fun encode() =
        "hits=$hits;lapses=$lapses;falses=$falses;targets=$targets;shields=$shields;freezes=$freezes;found=${found.sorted().joinToString(",")};missed=${missed.sorted().joinToString(",")};goal=$goal;max=$maxFalse"

    companion object {
        fun decode(text: String?): VigilLog? =
            runCatching {
                    val m =
                        text!!.split(';').associate {
                            val i = it.indexOf('=')
                            it.substring(0, i) to it.substring(i + 1)
                        }
                    fun set(k: String) =
                        m[k]
                            .orEmpty()
                            .split(',')
                            .filter { it.isNotBlank() }
                            .map { it.toInt() }
                            .toSet()
                    VigilLog(
                        m.getValue("hits").toInt(),
                        m.getValue("lapses").toInt(),
                        m.getValue("falses").toInt(),
                        m.getValue("targets").toInt(),
                        m.getValue("shields").toInt(),
                        m.getValue("freezes").toInt(),
                        set("found"),
                        set("missed"),
                        m["goal"]?.toInt() ?: 80,
                        m["max"]?.toInt() ?: 2,
                    )
                }
                .getOrNull()
    }
}

/** Speed factor of the monitor and the note that announces it (§3 "Zona de reto visible"). */
data class VigilTuning(val speed: Float = 1f, val note: String? = null)

object Vigil {
    const val ID = "vigil"
    // Patterns
    const val BRUTE = 0
    const val ODD = 1
    const val SCAN = 2
    const val TRAVEL = 3
    const val EXFIL = 4
    val patternKeys = listOf("bruteforce", "oddhour", "portscan", "travel", "exfil")
    val patternNames =
        listOf("Fuerza bruta", "Hora rara", "Escaneo de puertos", "Viaje imposible", "Exfiltración")
    val patternShort = listOf("FB", "HR", "ES", "VI", "EX")
    val lessons =
        listOf(
            "Fuerza bruta = misma IP, muchos fallos, poco tiempo: 5 o más «Failed password» en menos de 60 s. Mira la columna src.",
            "Un usuario de oficina que entra a las 3 de la madrugada es sospechoso, salvo que esté de guardia (mira la cabecera).",
            "Escaneo de puertos: una misma IP prueba puertos seguidos (:21, :22, :23…), aunque las líneas no vayan juntas.",
            "Viaje imposible: el mismo usuario entra desde dos ciudades lejanas con minutos de diferencia.",
            "Exfiltración: un pico de bytes salientes (gigas en vez de megas) desde un equipo interno.",
        )
    // Line kinds
    const val FAIL = 0
    const val LOGIN = 1
    const val LOGOUT = 2
    const val VPN = 3
    const val SYN = 4
    const val OUT = 5
    const val GET = 6
    const val DNS = 7
    const val NTP = 8
    const val CRON = 9
    // Why a noise line is normal (false alarm message)
    const val WHY_NORMAL = 0
    const val WHY_SPREAD = 1
    const val WHY_GUARD = 2
    const val WHY_SINGLE = 3
    const val WHY_SERVICE = 4
    const val WHY_SMALL = 5
    val whyText =
        listOf(
            "Tráfico normal del turno: nada fuera de lo habitual.",
            "Distintas IPs internas ≈ error de configuración, no fuerza bruta dirigida.",
            "Está de guardia hoy (mira la cabecera): entrar de noche es su trabajo.",
            "Un fallo aislado es normal: alguien se equivocó al teclear.",
            "Cuenta de servicio: entra cada noche para las copias.",
            "Unos megas salientes son tráfico normal; la exfiltración son gigas.",
        )
    /**
     * Visible rows of the monitor: an attack must have scrolled out by the end of the tramo, so the
     * last group line is ≤ n − VISIBLE − 1.
     */
    const val VISIBLE = 8
    const val TRAMO_MS = 30_000L
    /** Paso 3 (docs/AUDITORIA-JUEGOS-V3.md): a partida is 4 chained tramos. */
    const val TRAMOS = 4
    const val SHIELDS = 6
    const val FREEZES = 3
    const val FREEZE_MS = 2_000L
    val office = listOf("ana", "luis", "sara", "pepe", "marta", "iker", "noa")
    val services = listOf("backup", "monitor", "cron")
    val cities = listOf("MAD", "TYO", "NYC", "SYD", "SAO")

    fun level(l: Int): VigilLevel {
        require(l in 1..10)
        return when (l) {
            // Paso 3: the decoy (failures for admin from several internal IPs) arrives at level 2,
            // so the src column has to be read.
            1 -> VigilLevel(1, 15, 1200, 80, 2, false, 1, 2)
            2 -> VigilLevel(1, 15, 1200, 80, 2, true, 1, 2)
            3,
            4 -> VigilLevel(2, 12, 1000, 80, 2, true, 1, 2)
            5,
            6 -> VigilLevel(3, 10, 900, 85, 2, true, 1, 3)
            7,
            8 -> VigilLevel(4, 9, 900, 85, 1, true, 1, 3)
            else -> VigilLevel(5, 8, 800, 90, 1, true, 2, 3)
        }
    }

    /** Lines in a tramo: 30 s at the base interval with the 3 %/10 s ramp (≈ +3 % on average). */
    fun lineCount(intervalMs: Long) = (TRAMO_MS * 1.03 / intervalMs).toInt()

    private fun ext(r: Random) =
        "${listOf(203,198,185,45)[r.nextInt(4)]}.${r.nextInt(1,255)}.${r.nextInt(0,255)}.${r.nextInt(1,255)}"

    private fun internal(r: Random) = "10.0.${r.nextInt(1,9)}.${r.nextInt(2,250)}"

    /**
     * Paso 3: every tramo carries all its attacks. When one does not fit, the tramo is rebuilt from
     * a derived seed (same [seed], same tramo).
     */
    fun make(level: Int, seed: Long): VigilTask {
        val want = level(level).targets
        var t = build(level, seed)
        var k = 1
        while (t.groups.size < want && k <= 30) {
            t = build(level, seed + k * 7919L).copy(seed = seed)
            k++
        }
        return t
    }

    private fun build(level: Int, seed: Long): VigilTask {
        val lv = level(level)
        val r = Random(seed)
        val guard = office[r.nextInt(office.size)]
        val n = lineCount(lv.intervalMs)
        val slots = arrayOfNulls<VigilLine>(n)
        val groups = mutableListOf<VigilGroup>()
        val first = 3
        val lastSlot = n - VISIBLE - 2
        val usedIps = mutableSetOf<String>()
        fun freshIp(maker: (Random) -> String): String {
            while (true) {
                val ip = maker(r)
                if (usedIps.add(ip)) return ip
            }
        }
        /**
         * Picks [count] free slots inside a window of [span] starting in first..lastSlot; null when
         * there is no room.
         */
        fun place(count: Int, span: Int, minGap: Int = 1): List<Int>? {
            repeat(40) {
                val s = r.nextInt(first, maxOf(first + 1, lastSlot - span + 2))
                val free = (s until minOf(s + span, lastSlot + 1)).filter { slots[it] == null }
                if (free.size >= count) {
                    val pick = mutableListOf<Int>()
                    for (i in free.shuffled(r)) {
                        if (pick.all { kotlin.math.abs(it - i) >= minGap }) pick += i
                        if (pick.size == count) break
                    }
                    if (pick.size == count) return pick.sorted()
                }
            }
            return null
        }
        // Attack patterns: one per target, rotating through the patterns the level knows (newest
        // first so it gets practised).
        val patterns = (0 until lv.patterns).toList()
        // At most one impossible travel per tramo: it uses the guard's account, whose ordinary VPN
        // logins are noise.
        var travel = 0
        // Paso 3: the first target is the newest pattern only half of the time, so it no longer
        // takes half of every partida.
        val wanted =
            List(lv.targets) {
                val p =
                    if (it == 0 && r.nextBoolean()) patterns.last()
                    else patterns[r.nextInt(patterns.size)]
                if (p == TRAVEL && travel++ > 0) BRUTE else p
            }
        for (p in wanted) {
            val g = groups.size
            val ch = if (lv.channels == 2 && p in setOf(SCAN, EXFIL)) 1 else 0
            when (p) {
                BRUTE -> {
                    val at = place(5, 7) ?: continue
                    val ip = freshIp(::ext)
                    at.forEach { slots[it] = VigilLine(0, ip, FAIL, "admin", group = g) }
                    groups += VigilGroup(p, at, at[4])
                }
                ODD -> {
                    val at = place(1, 1) ?: continue
                    val u = office.filter { it != guard }.random(r)
                    slots[at[0]] =
                        VigilLine(0, internal(r).also { usedIps += it }, LOGIN, u, group = g)
                    groups += VigilGroup(p, at, at[0])
                }
                SCAN -> {
                    val k = r.nextInt(3, 5)
                    val at = place(k, 10, 2) ?: continue
                    val ip = freshIp(::ext)
                    val base =
                        listOf(
                            listOf(21, 22, 23, 25),
                            listOf(80, 81, 82, 83),
                            listOf(3306, 3307, 3308, 3309),
                            listOf(8080, 8081, 8082, 8083),
                        )[r.nextInt(4)]
                    at.forEachIndexed { i, s ->
                        slots[s] = VigilLine(0, ip, SYN, port = base[i], channel = ch, group = g)
                    }
                    groups += VigilGroup(p, at, at[2])
                }
                TRAVEL -> {
                    val at = place(2, 10, 5) ?: continue
                    val ip1 = freshIp(::ext)
                    val ip2 = freshIp(::ext)
                    val far = cities.drop(1).random(r)
                    slots[at[0]] = VigilLine(0, ip1, VPN, guard, city = "MAD", group = g)
                    slots[at[1]] = VigilLine(0, ip2, VPN, guard, city = far, group = g)
                    groups += VigilGroup(p, at, at[1])
                }
                else -> {
                    val at = place(1, 1) ?: continue
                    slots[at[0]] =
                        VigilLine(
                            0,
                            freshIp(::internal),
                            OUT,
                            mb = r.nextInt(12, 48) * 100,
                            channel = ch,
                            group = g,
                        )
                    groups += VigilGroup(p, at, at[0])
                }
            }
        }
        // Decoy (level 7+): five failed passwords for admin from five different internal IPs.
        // Short tramos (levels 2–4) have room for three of them; from level 5 they are five, like a
        // real brute force.
        if (lv.decoys) {
            place(if (n < 30) 3 else 5, if (n < 30) 10 else 7)?.forEach {
                slots[it] = VigilLine(0, freshIp(::internal), FAIL, "admin", why = WHY_SPREAD)
            }
        }
        val travelFrom =
            groups.filter { it.pattern == TRAVEL }.minOfOrNull { it.lines.first() } ?: n
        // Noise: never two failures nor two syn from the same IP, logins only by services or the
        // guard, small outbound transfers.
        for (i in 0 until n) if (slots[i] == null) {
            val k = r.nextInt(if (lv.patterns >= 2) 9 else 7)
            slots[i] =
                when (k) {
                    0 -> VigilLine(0, freshIp(::internal), GET)
                    1 -> VigilLine(0, freshIp(::internal), DNS)
                    2 -> VigilLine(0, "10.0.0.1", NTP)
                    3 -> VigilLine(0, freshIp(::internal), FAIL, office.random(r), why = WHY_SINGLE)
                    4 ->
                        VigilLine(
                            0,
                            freshIp(::ext),
                            SYN,
                            port = listOf(443, 80)[r.nextInt(2)],
                            channel = if (lv.channels == 2) 1 else 0,
                        )
                    5 ->
                        VigilLine(
                            0,
                            freshIp(::internal),
                            OUT,
                            mb = r.nextInt(2, 180),
                            channel = if (lv.channels == 2) 1 else 0,
                            why = WHY_SMALL,
                        )
                    6 -> VigilLine(0, "10.0.0.5", LOGIN, services.random(r), why = WHY_SERVICE)
                    7 ->
                        if (lv.patterns >= 4 && i < travelFrom && r.nextBoolean())
                            VigilLine(0, "10.8.0.2", VPN, guard, city = "MAD", why = WHY_GUARD)
                        else VigilLine(0, freshIp(::internal), LOGIN, guard, why = WHY_GUARD)
                    else ->
                        VigilLine(
                            0,
                            "10.0.0.5",
                            if (r.nextBoolean()) CRON else LOGOUT,
                            services.random(r),
                        )
                }
        }
        // Log clock: 02:58 + 4–7 s per line, so a 6-line window stays under 60 s.
        var t = 2 * 3600 + 58 * 60 + r.nextInt(0, 60)
        val lines =
            slots.map { l ->
                t += r.nextInt(4, 8)
                l!!.copy(time = t)
            }
        return VigilTask(
            level,
            seed,
            guard,
            lines,
            groups,
            lv.intervalMs,
            lv.goal,
            lv.maxFalse,
            lv.channels,
        )
    }

    /**
     * The rule engine: for each line, the pattern whose rule fires exactly at that line, or −1.
     * Brute force: the 5th failure from one src within 60 s. Odd hour: login of an office user who
     * is not on guard. Port scan: the 3rd distinct port from one src within 120 s. Impossible
     * travel: a VPN login of the same user from another city within 30 min. Exfiltration: ≥ 1 GB
     * outbound.
     */
    fun triggers(lines: List<VigilLine>, guard: String): IntArray {
        val out = IntArray(lines.size) { -1 }
        for (i in lines.indices) {
            val l = lines[i]
            out[i] =
                when (l.kind) {
                    FAIL ->
                        if (
                            (0..i).count {
                                lines[it].kind == FAIL &&
                                    lines[it].src == l.src &&
                                    l.time - lines[it].time < 60
                            } == 5
                        )
                            BRUTE
                        else -1
                    LOGIN -> if (l.user in office && l.user != guard) ODD else -1
                    SYN ->
                        if (
                            (0..i)
                                .filter {
                                    lines[it].kind == SYN &&
                                        lines[it].src == l.src &&
                                        l.time - lines[it].time < 120
                                }
                                .map { lines[it].port }
                                .distinct()
                                .size == 3 &&
                                (0 until i).none {
                                    lines[it].kind == SYN &&
                                        lines[it].src == l.src &&
                                        lines[it].port == l.port
                                }
                        )
                            SCAN
                        else -1
                    VPN ->
                        if (
                            (0 until i).any {
                                lines[it].kind == VPN &&
                                    lines[it].user == l.user &&
                                    lines[it].city != l.city &&
                                    l.time - lines[it].time < 1800
                            }
                        )
                            TRAVEL
                        else -1
                    OUT -> if (l.mb >= 1000) EXFIL else -1
                    else -> -1
                }
        }
        return out
    }

    /**
     * Two perfect tramos in a row: monitor +10 %; two failed: −10 %. Applied to the next tramo and
     * announced.
     */
    fun tuning(previous: List<VigilLog>): VigilTuning {
        val last = previous.takeLast(2)
        return when {
            last.size == 2 && last.all { it.perfect } -> VigilTuning(1.1f, "⚡ monitor +10 %")
            last.size == 2 && last.none { it.passed } -> VigilTuning(.9f, "🧭 monitor −10 %")
            else -> VigilTuning()
        }
    }

    /**
     * Lines scrolled after [ms] of tramo at [speed]: the base rate grows 3 % every 10 s,
     * continuously (no jumps): v(t) = v0·(1 + 0,003·t) ⇒ lines(t) = v0·(t + 0,0015·t²), t in
     * seconds.
     */
    fun scrolled(ms: Long, intervalMs: Long, speed: Float = 1f): Float {
        val t = ms / 1000f
        return speed * 1000f / intervalMs * (t + .0015f * t * t)
    }

    /** Time (ms) when [lines] have scrolled: inverse of [scrolled]. */
    fun timeFor(lines: Float, intervalMs: Long, speed: Float = 1f): Long {
        val a = .0015f
        val b = 1f
        val c = -lines * intervalMs / (1000f * speed)
        val t = (-b + kotlin.math.sqrt(b * b - 4 * a * c)) / (2 * a)
        return (t * 1000).toLong()
    }

    fun explanation(t: VigilTask) =
        "Patrones de este tramo: " +
            t.groups.map { it.pattern }.distinct().joinToString(", ") { patternNames[it] } +
            ".\n" +
            t.groups.map { it.pattern }.distinct().joinToString("\n") { lessons[it] }

    /** Detection % per tramo, the shape of the fatigue curve on the result screen and the ghost. */
    fun curve(logs: List<VigilLog>) = logs.map { it.detected }

    fun weakest(logs: List<VigilLog>): Int? =
        logs.flatMap { it.missed }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
}

/**
 * Live state of one tramo, independent from drawing (the board calls [mark] and [advance]; tests
 * drive it directly). Shields in halves: a lapse costs 2, a false alarm 1; at 0 the tramo is lost
 * (the game goes on with the next one).
 */
class VigilShift(val task: VigilTask) {
    var shields = Vigil.SHIELDS
        private set

    var hits = 0
        private set

    var lapses = 0
        private set

    var falses = 0
        private set

    /** 0 untouched, 1 hit (archived), 2 false alarm, 3 lapse — per line. */
    val state = IntArray(task.lines.size)
    private val resolved = BooleanArray(task.groups.size)
    val found = mutableSetOf<Int>()
    val missed = mutableSetOf<Int>()
    val over
        get() = shields <= 0

    enum class Mark {
        HIT,
        EARLY,
        FALSE,
        IGNORED,
    }

    /** Taps line [i] when [emitted] lines have entered the monitor. */
    fun mark(i: Int, emitted: Int): Mark {
        if (over || i !in task.lines.indices || state[i] != 0) return Mark.IGNORED
        val gi = task.lines[i].group
        if (gi >= 0) {
            if (resolved[gi]) return Mark.IGNORED
            val g = task.groups[gi]
            if (emitted <= g.detectAt) return Mark.EARLY
            resolved[gi] = true
            hits++
            found += g.pattern
            g.lines.forEach { state[it] = 1 }
            return Mark.HIT
        }
        state[i] = 2
        falses++
        shields = (shields - 1).coerceAtLeast(0)
        return Mark.FALSE
    }

    /**
     * Lines that have fully left the top: returns the groups that became lapses now (each once).
     */
    fun advance(exited: Int): List<VigilGroup> {
        if (over) return emptyList()
        // Called every frame: allocates only when an attack actually becomes a lapse.
        var out: MutableList<VigilGroup>? = null
        for (gi in 0 until task.groups.size) {
            val g = task.groups[gi]
            if (!resolved[gi] && g.last < exited) {
                resolved[gi] = true
                lapses++
                missed += g.pattern
                for (k in g.lines) state[k] = 3
                shields = (shields - 2).coerceAtLeast(0)
                (out ?: mutableListOf<VigilGroup>().also { out = it }) += g
            }
        }
        return out ?: emptyList()
    }

    /**
     * Unresolved attacks at the end of the tramo count as lapses (cannot happen with a full scroll,
     * kept for safety).
     */
    fun finish(freezes: Int): VigilLog {
        advance(Int.MAX_VALUE)
        return log(freezes)
    }

    fun log(freezes: Int) =
        VigilLog(
            hits,
            lapses,
            falses,
            task.groups.size,
            shields,
            freezes,
            found.toSet(),
            missed.toSet(),
            task.goal,
            task.maxFalse,
        )
}
