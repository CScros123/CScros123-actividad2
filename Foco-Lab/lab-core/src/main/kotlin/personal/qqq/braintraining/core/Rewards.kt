package personal.qqq.braintraining.core

import java.time.LocalDate
import kotlin.random.Random

/**
 * Lote C, cofre sorpresa. Reglas (docs/EXAMEN-JUEGOS.md, sección 6):
 * - Solo se gana terminando práctica (12 rondas, Relámpago, Práctica 3 min o las misiones del día).
 * - Como mucho [Rewards.DAILY_MAX] cofres ganados al día; los no abiertos se guardan (sin caducidad
 *   ni castigo).
 * - Contenido variable pero determinista: el cofre n.º k del día d siempre da lo mismo.
 * - Nunca da minutos de ocio ni toca los bloqueos de Foco: [Reward] no tiene ningún campo de
 *   tiempo.
 */
enum class RewardKind {
    POINTS,
    CARD,
    COSMETIC,
    PHRASE,
}

enum class ChestSource {
    RUN,
    FLASH,
    SHORT,
    MISSIONS,
}

/**
 * What a chest gives. Only points, an album card, a cosmetic or a phrase: there is deliberately no
 * time field.
 */
data class Reward(
    val kind: RewardKind,
    val points: Int = 0,
    val card: String? = null,
    val cosmetic: String? = null,
    val phrase: Int = -1,
)

/**
 * A purely visual item: a rocket skin (Ruta espacial), an accent for the home hero or a board theme
 * of Parejas de incidentes ([theme], Fase 3). [color] is ARGB.
 */
data class Cosmetic(
    val id: String,
    val title: String,
    val skin: Boolean,
    val color: Long,
    val theme: Boolean = false,
)

object Rewards {
    const val DAILY_MAX = 3
    val cosmetics =
        listOf(
            Cosmetic("skin:aurora", "Nave Aurora", true, 0xFF1EDB90),
            Cosmetic("skin:cobalto", "Nave Cobalto", true, 0xFF3567E4),
            Cosmetic("skin:solar", "Nave Solar", true, 0xFFFFB300),
            Cosmetic("skin:violeta", "Nave Violeta", true, 0xFFB69CFF),
            Cosmetic("accent:menta", "Acento Menta", false, 0xFF1EDB90),
            Cosmetic("accent:coral", "Acento Coral", false, 0xFFFF6B9A),
            Cosmetic("accent:ambar", "Acento Ámbar", false, 0xFFFFB300),
            Cosmetic("accent:cielo", "Acento Cielo", false, 0xFF7CF5FF),
            // Fase 3: Parejas de incidentes themes (only back, palette and font; never difficulty).
            // "Clásico ámbar" is the default.
            Cosmetic("theme:terminal", "Tema Terminal verde", false, 0xFF39FF88, theme = true),
            Cosmetic("theme:neon", "Tema Neón SOC", false, 0xFFFF6B9A, theme = true),
            Cosmetic("theme:papel", "Tema Papel de incidente", false, 0xFFC9B48A, theme = true),
        )

    fun cosmetic(id: String?) = cosmetics.firstOrNull { it.id == id }

    /** Rare phrases: short, original, about practice and curiosity. */
    val phrases =
        listOf(
            "Un puerto cerrado a tiempo vale más que cien alertas.",
            "La curiosidad es el mejor antivirus: pregunta antes de pulsar.",
            "Lo que hoy cuesta, mañana sale solo.",
            "Cada máscara de red es una frontera que ya sabes dibujar.",
            "Despacio también es rápido cuando no hay que repetir.",
            "Un buen log cuenta una historia; tú ya sabes leerla.",
            "Practicar poco y a menudo gana a practicar mucho una vez.",
            "El dominio real está justo antes de la extensión. Ahora lo ves.",
            "Equivocarse en un juego es la forma más barata de aprender.",
            "Hoy has entrenado la atención que mañana te protege.",
            "El mínimo privilegio también sirve para el tiempo: dale solo lo necesario.",
            "Terminar a tiempo también es una victoria.",
        )

    /** Seed of the chest number [count] (0-based) earned on [day]. */
    fun seed(day: LocalDate, count: Int): Long =
        day.toEpochDay() * 1_000_003L + count * 7919L + 0x5EEDL

    /**
     * Opens chest [count] of [day]. Deterministic for the same inputs. Cards are picked among
     * [locked] cards, cosmetics among those not in [owned]; when a kind has nothing left it falls
     * back to points or a phrase.
     */
    fun roll(
        day: LocalDate,
        count: Int,
        locked: Collection<String>,
        owned: Set<String> = emptySet(),
    ): Reward {
        val r = Random(seed(day, count))
        val x = r.nextInt(100)
        val kind =
            when {
                x < 35 -> RewardKind.POINTS
                x < 70 -> RewardKind.CARD
                x < 85 -> RewardKind.COSMETIC
                else -> RewardKind.PHRASE
            }
        val points = listOf(10, 10, 15, 15, 20, 25, 30, 50)[r.nextInt(8)]
        val cards = locked.sorted()
        val free = cosmetics.filter { it.id !in owned }
        return when (kind) {
            RewardKind.CARD ->
                if (cards.isNotEmpty()) Reward(RewardKind.CARD, card = cards[r.nextInt(cards.size)])
                else Reward(RewardKind.POINTS, points = points)
            RewardKind.COSMETIC ->
                if (free.isNotEmpty())
                    Reward(RewardKind.COSMETIC, cosmetic = free[r.nextInt(free.size)].id)
                else Reward(RewardKind.PHRASE, phrase = r.nextInt(phrases.size))
            RewardKind.PHRASE -> Reward(RewardKind.PHRASE, phrase = r.nextInt(phrases.size))
            RewardKind.POINTS -> Reward(RewardKind.POINTS, points = points)
        }
    }
}

/** One earned, unopened chest: the day and its 0-based number that day (its seed). */
data class ChestTicket(val day: LocalDate, val count: Int)

/**
 * Everything the engagement system keeps (pref "rewards"): earned chests, album, bonus points and
 * cosmetics. Immutable; [encode]/[decode] use a small line format so the core stays free of JSON
 * libraries.
 */
data class Wallet(
    val day: LocalDate? = null,
    val earnedToday: Int = 0,
    val pending: List<ChestTicket> = emptyList(),
    val bonus: Int = 0,
    val cards: Set<String> = emptySet(),
    val cosmetics: Set<String> = emptySet(),
    val skin: String? = null,
    val accent: String? = null,
    val phrases: Set<Int> = emptySet(),
    val opened: Int = 0,
    val theme: String? = null,
) {
    fun earnedOn(today: LocalDate) = if (day == today) earnedToday else 0

    fun canEarn(today: LocalDate) = earnedOn(today) < Rewards.DAILY_MAX

    /** Adds one chest, or returns null when today's [Rewards.DAILY_MAX] is reached. */
    fun earn(today: LocalDate): Wallet? {
        if (!canEarn(today)) return null
        val n = earnedOn(today)
        return copy(day = today, earnedToday = n + 1, pending = pending + ChestTicket(today, n))
    }

    /** Opens the oldest pending chest and applies its reward. Null when there is none. */
    fun open(): Pair<Wallet, Reward>? {
        val t = pending.firstOrNull() ?: return null
        val reward = Rewards.roll(t.day, t.count, Album.keys - cards, cosmetics)
        return apply(reward).copy(pending = pending.drop(1), opened = opened + 1) to reward
    }

    fun apply(reward: Reward): Wallet =
        when (reward.kind) {
            RewardKind.POINTS -> copy(bonus = bonus + reward.points)
            RewardKind.CARD -> copy(cards = cards + reward.card!!)
            RewardKind.COSMETIC -> {
                val c = Rewards.cosmetic(reward.cosmetic)!!
                when {
                    c.theme -> copy(cosmetics = cosmetics + c.id, theme = c.id)
                    else ->
                        copy(
                            cosmetics = cosmetics + c.id,
                            skin = if (c.skin) c.id else skin,
                            accent = if (c.skin) accent else c.id,
                        )
                }
            }
            RewardKind.PHRASE -> copy(phrases = phrases + reward.phrase)
        }

    fun unlock(card: String) = if (card in Album.keys) copy(cards = cards + card) else this

    fun equip(id: String?): Wallet {
        val c = Rewards.cosmetic(id)
        return when {
            id == null -> this
            c == null || c.id !in cosmetics -> this
            c.theme -> copy(theme = if (theme == c.id) null else c.id)
            c.skin -> copy(skin = c.id)
            else -> copy(accent = c.id)
        }
    }

    fun encode(): String = buildString {
        append("v=1\n")
        if (day != null) append("day=$day\n")
        append("earned=$earnedToday\n")
        append("pending=")
            .append(pending.joinToString(",") { "${it.day}#${it.count}" })
            .append('\n')
        append("bonus=$bonus\n")
        append("opened=$opened\n")
        append("cards=").append(cards.sorted().joinToString(",")).append('\n')
        append("cosmetics=").append(cosmetics.sorted().joinToString(",")).append('\n')
        if (skin != null) append("skin=$skin\n")
        if (accent != null) append("accent=$accent\n")
        if (theme != null) append("theme=$theme\n")
        append("phrases=").append(phrases.sorted().joinToString(",")).append('\n')
    }

    companion object {
        private fun list(v: String?) =
            v?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        /**
         * Strict parser (also used to validate a restored backup): throws on anything unexpected.
         */
        fun decode(text: String): Wallet {
            val map =
                text
                    .lines()
                    .filter { it.isNotBlank() }
                    .associate {
                        val i = it.indexOf('=')
                        require(i > 0)
                        it.substring(0, i) to it.substring(i + 1)
                    }
            require(map["v"] == "1")
            val known =
                setOf(
                    "v",
                    "day",
                    "earned",
                    "pending",
                    "bonus",
                    "opened",
                    "cards",
                    "cosmetics",
                    "skin",
                    "accent",
                    "phrases",
                    "theme",
                )
            require(map.keys.all { it in known })
            val pending =
                list(map["pending"]).map {
                    val (d, c) = it.split('#')
                    ChestTicket(
                        LocalDate.parse(d),
                        c.toInt().also { n -> require(n in 0 until Rewards.DAILY_MAX) },
                    )
                }
            val earned = map["earned"]?.toInt() ?: 0
            require(earned in 0..Rewards.DAILY_MAX)
            val bonus = map["bonus"]?.toInt() ?: 0
            require(bonus >= 0)
            val opened = map["opened"]?.toInt() ?: 0
            require(opened >= 0)
            val cosmetics = list(map["cosmetics"]).toSet()
            require(cosmetics.all { Rewards.cosmetic(it) != null })
            val phrases = list(map["phrases"]).map { it.toInt() }.toSet()
            require(phrases.all { it in Rewards.phrases.indices })
            val skin = map["skin"]
            val accent = map["accent"]
            require(skin == null || skin in cosmetics)
            require(accent == null || accent in cosmetics)
            val theme = map["theme"]
            require(theme == null || theme in cosmetics)
            // Unknown card keys (a bank changed in a later version) are dropped, not fatal.
            return Wallet(
                map["day"]?.let(LocalDate::parse),
                earned,
                pending,
                bonus,
                list(map["cards"]).filter { it in Album.keys }.toSet(),
                cosmetics,
                skin,
                accent,
                phrases,
                opened,
                theme,
            )
        }

        fun decodeOrEmpty(text: String?) =
            text?.let { runCatching { decode(it) }.getOrNull() } ?: Wallet()
    }
}
