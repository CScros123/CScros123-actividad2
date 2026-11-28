package personal.qqq.braintraining.core

import kotlin.random.Random

enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
}

data class PairCard(val id: Int, val pair: Int, val text: String, val left: Boolean)

data class PairDeck(val left: List<PairCard>, val right: List<PairCard>, val matches: Int = 0) {
    val cards
        get() = left + right

    fun selectMatch(a: Int, b: Int, random: Random): PairDeck? {
        val one = cards.firstOrNull { it.id == a } ?: return null
        val two = cards.firstOrNull { it.id == b } ?: return null
        if (one.id == two.id || one.left == two.left || one.pair != two.pair) return null
        fun reordered(cards: List<PairCard>): List<PairCard> {
            val remaining = cards.filter { it.pair != one.pair }
            if (remaining.size < 2) return remaining
            fun permutations(items: List<PairCard>): List<List<PairCard>> =
                if (items.size < 2) listOf(items)
                else items.flatMap { item -> permutations(items - item).map { listOf(item) + it } }
            // Compare with the original visual row, including the removed card's position.
            val moved =
                permutations(remaining).filter { order ->
                    order.indices.all { index -> cards.indexOf(order[index]) != index }
                }
            return moved.random(random)
        }
        return PairDeck(reordered(left), reordered(right), matches + 1)
    }
}

/**
 * What the cards show: arithmetic, or networking facts (ports ↔ services, CIDR ↔ masks, prefixes ↔
 * hosts).
 */
enum class PairTopic {
    MATH,
    NET,
}

object PairGame {
    /**
     * Networking bank (Lote B). Left and right texts are unique across the whole bank, so any 4
     * entries make a valid deck.
     */
    fun netBank(difficulty: Difficulty): List<Pair<String, String>> {
        val ports =
            NetSec.portList
                .take(
                    when (difficulty) {
                        Difficulty.EASY -> 8
                        Difficulty.MEDIUM -> 13
                        Difficulty.HARD -> NetSec.portList.size
                    }
                )
                .map { "Puerto ${it.port}" to it.service }
        if (difficulty == Difficulty.EASY) return ports
        val masks =
            (if (difficulty == Difficulty.MEDIUM) listOf(8, 16, 24, 25, 26, 27, 28)
                else (8..30).toList())
                .map { "/$it" to Ipv4.text(Ipv4.mask(it)) }
        val hosts =
            if (difficulty == Difficulty.HARD)
                listOf(24, 26, 27, 28, 29, 30).map {
                    "Hosts de un /$it" to "${Ipv4.hosts(it)} hosts"
                }
            else emptyList()
        return ports + masks + hosts
    }

    fun newDeck(
        difficulty: Difficulty,
        random: Random,
        matches: Int = 0,
        topic: PairTopic = PairTopic.MATH,
    ): PairDeck {
        if (topic == PairTopic.NET)
            return deck(netBank(difficulty).shuffled(random).take(4), random, matches)
        return math(difficulty, random, matches)
    }

    /** A deck from any bank of (left, right) pairs; the right texts must be distinct. */
    fun deck(pairs: List<Pair<String, String>>, random: Random, matches: Int = 0): PairDeck {
        require(pairs.map { it.second }.distinct().size == pairs.size)
        val left = pairs.mapIndexed { i, p -> PairCard(i * 2, i, p.first, true) }.shuffled(random)
        val right =
            pairs.mapIndexed { i, p -> PairCard(i * 2 + 1, i, p.second, false) }.shuffled(random)
        return PairDeck(left, right, matches)
    }

    private fun math(difficulty: Difficulty, random: Random, matches: Int): PairDeck {
        val range =
            when (difficulty) {
                Difficulty.EASY -> 1..9
                Difficulty.MEDIUM -> 3..20
                Difficulty.HARD -> 4..35
            }
        val values = mutableSetOf<Int>()
        val equations = mutableListOf<Pair<String, Int>>()
        while (equations.size < 4) {
            val a = range.random(random)
            val b = range.random(random)
            val op =
                when (difficulty) {
                    Difficulty.EASY -> random.nextInt(2)
                    Difficulty.MEDIUM -> random.nextInt(3)
                    Difficulty.HARD -> random.nextInt(3)
                }
            val value =
                when (op) {
                    0 -> a + b
                    1 -> a - b
                    else -> a * b
                }
            if (values.add(value))
                equations +=
                    (when (op) {
                        0 -> "$a + $b"
                        1 -> "$a − $b"
                        else -> "$a × $b"
                    }) to value
        }
        val left =
            equations
                .mapIndexed { i, pair -> PairCard(i * 2, i, pair.first, true) }
                .shuffled(random)
        val right =
            equations
                .mapIndexed { i, pair -> PairCard(i * 2 + 1, i, pair.second.toString(), false) }
                .shuffled(random)
        return PairDeck(left, right, matches)
    }
}

/**
 * Fase 3 (docs/DISENO-JUEGOS-V2.md §6): Parejas de incidentes. A board of face-down incident cards;
 * match port ↔ service, attack ↔ defense, alert ↔ cause, and from level 7 trios (attack, alert,
 * defense). [topic] groups entries that would be confusable on the same board (the "Fuerza bruta"
 * pair and the brute-force trio): a board takes at most one entry per topic, so every text on a
 * board is unique and has exactly one partner.
 */
data class IncidentEntry(
    val key: String,
    val group: Int,
    val texts: List<String>,
    val lesson: String,
    val topic: String = key,
) {
    val trio
        get() = texts.size == 3
}

data class IncidentCard(val id: Int, val entry: Int, val text: String, val role: Int)

/**
 * One board. [cards] are in layout order (cell i shows card i at the start), [target] the attempts
 * goal.
 */
data class IncidentBoard(
    val level: Int,
    val seed: Long,
    val cols: Int,
    val rows: Int,
    val entries: List<IncidentEntry>,
    val cards: List<IncidentCard>,
    val target: Int,
    val shuffle: Boolean,
) {
    val groups
        get() = entries.size

    val trios
        get() = entries.count { it.trio }
}

/**
 * A purely cosmetic look for the board (chest cosmetic "theme:*"); it never reaches
 * [IncidentPairs.make].
 */
data class PairsTheme(
    val id: String,
    val title: String,
    val back: Long,
    val face: Long,
    val accent: Long,
    val ink: Long,
    val mono: Boolean,
)

data class PairsLog(
    val attempts: Int,
    val target: Int,
    val groups: Int,
    val maxStreak: Int,
    val informed: Int,
    val shuffles: Int,
    val completed: Boolean,
    val keys: List<String> = emptyList(),
    val score: Int = 0,
) {
    val passed
        get() = completed && attempts <= target

    fun encode() =
        "attempts=$attempts;target=$target;groups=$groups;streak=$maxStreak;informed=$informed;shuffles=$shuffles;completed=$completed;score=$score;keys=${keys.joinToString(",")}"

    companion object {
        fun decode(text: String?): PairsLog? =
            runCatching {
                    val m =
                        text!!.split(';').associate {
                            val i = it.indexOf('=')
                            it.substring(0, i) to it.substring(i + 1)
                        }
                    PairsLog(
                        m.getValue("attempts").toInt(),
                        m.getValue("target").toInt(),
                        m.getValue("groups").toInt(),
                        m.getValue("streak").toInt(),
                        m.getValue("informed").toInt(),
                        m.getValue("shuffles").toInt(),
                        m.getValue("completed").toBooleanStrict(),
                        m["keys"].orEmpty().split(',').filter { it.isNotBlank() },
                        m["score"]?.toInt() ?: 0,
                    )
                }
                .getOrNull()
    }
}

object IncidentPairs {
    const val ID = "pairs"
    const val PORT = 0
    const val ATTACK = 1
    const val ALERT = 2
    const val TRIO = 3
    val groupNames =
        listOf(
            "Puerto ↔ servicio",
            "Ataque ↔ defensa",
            "Alerta ↔ causa",
            "Ataque · alerta · defensa",
        )
    val themes =
        listOf(
            PairsTheme(
                "theme:clasico",
                "Clásico ámbar",
                0xFFFFB300,
                0xFF1F2636,
                0xFFFFB300,
                0xFFF2F4F8,
                false,
            ),
            PairsTheme(
                "theme:terminal",
                "Terminal verde",
                0xFF0F3D1E,
                0xFF07130B,
                0xFF39FF88,
                0xFFB8FFD0,
                true,
            ),
            PairsTheme(
                "theme:neon",
                "Neón SOC",
                0xFF5A1E8C,
                0xFF140C24,
                0xFFFF6B9A,
                0xFFF4E9FF,
                false,
            ),
            PairsTheme(
                "theme:papel",
                "Papel de incidente",
                0xFFC9B48A,
                0xFFF4ECDC,
                0xFFB0442C,
                0xFF2B2216,
                true,
            ),
        )

    fun theme(id: String?) = themes.firstOrNull { it.id == id } ?: themes.first()

    val bank: List<IncidentEntry> by lazy {
        val l = mutableListOf<IncidentEntry>()
        NetSec.portList.forEach { p ->
            l +=
                IncidentEntry(
                    "p${p.port}",
                    PORT,
                    listOf("Puerto ${p.port}", p.service),
                    "${p.port} ↔ ${p.service}: ${p.lesson}",
                )
        }
        l +=
            IncidentEntry(
                "p465",
                PORT,
                listOf("Puerto 465", "SMTPS"),
                "465 ↔ SMTPS: es SMTP dentro de TLS. El 25 es el SMTP clásico entre servidores, sin cifrar.",
            )
        l +=
            IncidentEntry(
                "p993",
                PORT,
                listOf("Puerto 993", "IMAPS"),
                "993 ↔ IMAPS: el buzón IMAP cifrado con TLS.",
            )
        fun atk(k: String, a: String, d: String, lesson: String, topic: String = k) =
            IncidentEntry(k, ATTACK, listOf(a, d), lesson, topic)
        l +=
            atk(
                "sqliparam",
                "Inyección SQL",
                "Consultas parametrizadas",
                "SQLi ↔ consultas parametrizadas: el dato nunca se concatena en la consulta.",
                "sqli",
            )
        l +=
            atk(
                "xssescape",
                "XSS",
                "Escapar la salida HTML",
                "XSS ↔ escapar la salida: el texto del usuario se muestra como texto, nunca como código.",
                "xss",
            )
        l +=
            atk(
                "phishingmfa",
                "Phishing",
                "MFA",
                "Phishing ↔ MFA: aunque roben la contraseña, falta el segundo factor.",
            )
        l +=
            atk(
                "ransomwareoffline",
                "Ransomware",
                "Copias offline",
                "Ransomware ↔ copias offline: una copia desconectada no se puede cifrar.",
                "ransom",
            )
        l +=
            atk(
                "brutelockout",
                "Fuerza bruta",
                "Bloqueo tras intentos",
                "Fuerza bruta ↔ bloqueo tras intentos: limita los fallos por cuenta e IP.",
                "brute",
            )
        l +=
            atk(
                "mitmtls",
                "Man in the middle",
                "TLS + HSTS",
                "MITM ↔ TLS + HSTS: el canal cifrado y forzado impide leer o cambiar el tráfico.",
            )
        l +=
            atk(
                "ddoscdn",
                "DDoS",
                "Rate-limit y CDN",
                "DDoS ↔ rate-limit y CDN: repartir y limitar el tráfico absorbe la avalancha.",
                "ddos",
            )
        l +=
            atk(
                "csrftoken",
                "CSRF",
                "Token anti-CSRF",
                "CSRF ↔ token anti-CSRF: cada formulario lleva un secreto que otra web no conoce.",
            )
        l +=
            atk(
                "privescleast",
                "Escalada de privilegios",
                "Mínimo privilegio",
                "Escalada ↔ mínimo privilegio: si la cuenta puede poco, el atacante también.",
            )
        fun alert(k: String, a: String, c: String, lesson: String, topic: String = k) =
            IncidentEntry(k, ALERT, listOf(a, c), lesson, topic)
        l +=
            alert(
                "failedbrute",
                "Failed password ×50",
                "Adivinar contraseñas",
                "«Failed password» ×50 desde una IP ↔ alguien adivina contraseñas (fuerza bruta).",
                "brute",
            )
        l +=
            alert(
                "synflood",
                "SYN ×10 000 sin ACK",
                "SYN flood",
                "Miles de SYN sin ACK ↔ SYN flood: conexiones medio abiertas que llenan la tabla.",
                "ddos",
            )
        l +=
            alert(
                "nightlogin",
                "Login 03:12 de oficina",
                "Acceso fuera de horario",
                "Un usuario de oficina entrando de madrugada ↔ acceso sospechoso fuera de horario.",
            )
        l +=
            alert(
                "exfilgb",
                "Salida de 4 GB a las 3:00",
                "Exfiltración de datos",
                "Gigas saliendo de noche desde un equipo interno ↔ exfiltración de datos.",
            )
        l +=
            alert(
                "scanports",
                ":21 :22 :23 seguidos",
                "Escaneo de puertos",
                "Una IP probando puertos seguidos ↔ escaneo de puertos.",
            )
        l +=
            alert(
                "travelcities",
                "MAD y TYO en 5 min",
                "Viaje imposible",
                "El mismo usuario en dos ciudades lejanas en minutos ↔ viaje imposible.",
            )
        l +=
            alert(
                "dnsamp",
                "DNS de 3 kB sin pregunta",
                "Amplificación DNS",
                "Respuestas DNS enormes que nadie pidió ↔ amplificación DNS.",
                "ddos",
            )
        fun trio(k: String, a: String, b: String, c: String, lesson: String, topic: String) =
            IncidentEntry(k, TRIO, listOf(a, b, c), lesson, topic)
        l +=
            trio(
                "triosqli",
                "Inyección SQL",
                "' OR 1=1 -- en el login",
                "Consultas parametrizadas",
                "SQLi: la alerta es la comilla en el login; la defensa, consultas parametrizadas.",
                "sqli",
            )
        l +=
            trio(
                "triobrute",
                "Fuerza bruta",
                "Failed password ×50",
                "Bloqueo tras intentos",
                "Fuerza bruta: ráfaga de fallos en el log; la defensa, bloquear tras unos intentos.",
                "brute",
            )
        l +=
            trio(
                "trioransom",
                "Ransomware",
                "Archivos .locked",
                "Copias offline",
                "Ransomware: los archivos cambian de extensión; la defensa, copias offline.",
                "ransom",
            )
        l +=
            trio(
                "trioxss",
                "XSS",
                "<script> en un comentario",
                "Escapar la salida HTML",
                "XSS: código en un comentario; la defensa, escapar la salida.",
                "xss",
            )
        l +=
            trio(
                "trioddos",
                "DDoS",
                "Tráfico ×100 en 1 min",
                "Rate-limit y CDN",
                "DDoS: el tráfico se multiplica de golpe; la defensa, rate-limit y CDN.",
                "ddos",
            )
        l.toList()
    }

    fun entry(key: String) = bank.firstOrNull { it.key == key }

    /** Columns, rows, attempts factor (×10) and trios per level (§6 table). */
    fun shape(l: Int): IntArray {
        require(l in 1..10)
        return when (l) {
            1,
            2 -> intArrayOf(3, 4, 16, 0)
            3,
            4 -> intArrayOf(4, 4, 16, 0)
            5,
            6 -> intArrayOf(4, 5, 17, 0)
            7,
            8 -> intArrayOf(4, 6, 18, 2)
            else -> intArrayOf(4, 6, 19, 2)
        }
    }

    /**
     * The board for ([level], [seed]). [theme] is accepted only to make explicit that it changes
     * nothing: the same seed gives the same board whatever theme the player wears (acceptance
     * test).
     */
    @Suppress("UNUSED_PARAMETER")
    fun make(level: Int, seed: Long, theme: String? = null): IncidentBoard {
        val (cols, rows, factor, trios) = shape(level).let { arrayOf(it[0], it[1], it[2], it[3]) }
        val r = Random(seed)
        val groups = mutableListOf<IncidentEntry>()
        val topics = mutableSetOf<String>()
        fun take(e: IncidentEntry) {
            groups += e
            topics += e.topic
        }
        fun pick(pool: List<IncidentEntry>, k: Int) {
            for (e in pool.shuffled(r)) {
                if (k <= 0 || groups.count { it in pool } >= k) break
                if (e.topic !in topics) take(e)
            }
        }
        val cardsWanted = cols * rows
        if (trios > 0) pick(bank.filter { it.group == TRIO }, trios)
        val pairsWanted = (cardsWanted - 3 * groups.size) / 2
        if (level >= 5) {
            listOf("sqli", "xss").forEach { t ->
                if (t !in topics) bank.first { it.group == ATTACK && it.topic == t }.let(::take)
            }
        }
        val alerts =
            if (level >= 5) minOf(3, pairsWanted - groups.count { it.group != TRIO }) else 0
        pick(bank.filter { it.group == ALERT }, alerts)
        val attacks = if (level >= 3) (pairsWanted - groups.count { it.group != TRIO }) / 2 else 0
        pick(bank.filter { it.group == ATTACK }, groups.count { it.group == ATTACK } + attacks)
        // Ports fill the rest; from level 3 the two SMTP ports may meet (situation 2).
        if (level >= 3 && r.nextBoolean() && groups.count { it.group != TRIO } + 2 <= pairsWanted) {
            take(entry("p25")!!)
            take(entry("p465")!!)
        }
        pick(
            bank.filter { it.group == PORT },
            groups.count { it.group == PORT } + (pairsWanted - groups.count { it.group != TRIO }),
        )
        val cards =
            groups
                .flatMapIndexed { g, e ->
                    e.texts.mapIndexed { role, t -> IncidentCard(0, g, t, role) }
                }
                .shuffled(r)
                .mapIndexed { i, c -> c.copy(id = i) }
        require(cards.size == cardsWanted)
        val target =
            kotlin.math.ceil(factor * (groups.size + groups.count { it.trio }) / 10.0).toInt()
        return IncidentBoard(level, seed, cols, rows, groups, cards, target, level >= 9)
    }

    fun explanation(b: IncidentBoard) =
        "Parejas del tablero: " + b.entries.joinToString(" · ") { it.texts.joinToString(" ↔ ") }
}

/**
 * Live state of one board (pure; the view only animates it). Cells hold card ids and change only on
 * a partial shuffle. An attempt is each time two cards resolve (pair or miss) plus the third card
 * of a trio.
 */
class PairsPlay(val board: IncidentBoard) {
    val cells = IntArray(board.cards.size) { it }
    val matched = BooleanArray(board.cards.size)
    /** Turn when each card was last face-up (−1 never). */
    val seenAt = IntArray(board.cards.size) { -1 }
    val open = mutableListOf<Int>()
    var attempts = 0
        private set

    var streak = 0
        private set

    var maxStreak = 0
        private set

    var missRow = 0
        private set

    var informed = 0
        private set

    var shuffles = 0
        private set

    var score = 0
        private set

    private var turn = 0
    /** Cards waiting to flip back after a miss: the board calls [hide] after a short pause. */
    var pendingMiss = false
        private set

    val keys = mutableListOf<String>()
    val done
        get() = matched.all { it }

    enum class Flip {
        OPENED,
        MATCH,
        PARTIAL,
        TRIO,
        MISS,
        IGNORED,
    }

    fun card(id: Int) = board.cards[id]

    fun entryOf(id: Int) = board.entries[board.cards[id].entry]

    private fun match(ids: List<Int>) {
        ids.forEach { matched[it] = true }
        open.clear()
        streak++
        maxStreak = maxOf(maxStreak, streak)
        missRow = 0
        score += if (streak >= 4) 20 else 10
        keys += entryOf(ids[0]).key
    }

    fun flip(id: Int): Flip {
        if (pendingMiss || done || id !in matched.indices || matched[id] || id in open)
            return Flip.IGNORED
        val wasSeen = seenAt[id] >= 0
        seenAt[id] = turn
        open += id
        if (open.size == 1) return Flip.OPENED
        val first = open[0]
        val e = board.cards[first].entry
        val same = open.all { board.cards[it].entry == e }
        attempts++
        turn++
        if (wasSeen) informed++
        if (!same) {
            pendingMiss = true
            streak = 0
            missRow++
            return Flip.MISS
        }
        if (open.size < board.entries[e].texts.size) return Flip.PARTIAL
        val trio = open.size == 3
        match(open.toList())
        return if (trio) Flip.TRIO else Flip.MATCH
    }

    /**
     * Flips the missed cards back. Returns the two cells swapped by a partial shuffle (level 9–10,
     * after 4 misses in a row), or null.
     */
    fun hide(): IntArray? {
        if (!pendingMiss) return null
        open.clear()
        pendingMiss = false
        if (board.shuffle && missRow >= 4) {
            val free = cells.indices.filter { !matched[cells[it]] }
            if (free.size >= 2) {
                val r = Random(board.seed + attempts)
                val a = free[r.nextInt(free.size)]
                var b = free[r.nextInt(free.size)]
                while (b == a) b = free[r.nextInt(free.size)]
                val t = cells[a]
                cells[a] = cells[b]
                cells[b] = t
                missRow = 0
                shuffles++
                return intArrayOf(a, b)
            }
        }
        return null
    }

    /** After 3 misses in a row: the face-down card seen longest ago ("la viste aquí"), or −1. */
    fun hint(): Int {
        if (missRow < 3) return -1
        var best = -1
        for (id in matched.indices) if (
            !matched[id] &&
                id !in open &&
                seenAt[id] >= 0 &&
                (best < 0 || seenAt[id] < seenAt[best])
        )
            best = id
        return best
    }

    fun log() =
        PairsLog(
            attempts,
            board.target,
            board.groups,
            maxStreak,
            informed,
            shuffles,
            done,
            keys.toList(),
            score,
        )
}
