package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

/** Fase 7, lote piloto: Subredes, Phishing y Puertos. */
class PilotTest {
    private fun ip(s: String) = s.split(".").fold(0L) { a, o -> a * 256 + o.toLong() }

    @Test
    fun ipv4Math() {
        val a = ip("192.168.10.77")
        assertEquals("255.255.255.192", Ipv4.text(Ipv4.mask(26)))
        assertEquals("192.168.10.64", Ipv4.text(Ipv4.network(a, 26)))
        assertEquals("192.168.10.127", Ipv4.text(Ipv4.broadcast(a, 26)))
        assertEquals(62L, Ipv4.hosts(26))
        assertEquals(254L, Ipv4.hosts(24))
        assertEquals(2L, Ipv4.hosts(30))
        assertEquals(0L, Ipv4.hosts(31))
        assertEquals(0L, Ipv4.hosts(32))
        assertEquals(0L, Ipv4.mask(0))
        assertEquals(Ipv4.ALL, Ipv4.mask(32))
        assertEquals("255.255.240.0", Ipv4.text(Ipv4.mask(20)))
        assertEquals("10.0.0.0", Ipv4.text(Ipv4.network(ip("10.0.15.200"), 20)))
        assertEquals("10.0.15.255", Ipv4.text(Ipv4.broadcast(ip("10.0.15.200"), 20)))
        assertEquals(1, Ipv4.bit(a, 0))
        assertEquals(1, Ipv4.bit(a, 31))
        assertEquals(77L, Ipv4.octet(a, 3))
        assertEquals(192L, Ipv4.octet(a, 0))
        assertEquals(27, Ipv4.common(ip("192.168.10.77"), ip("192.168.10.90")))
        assertEquals(32, Ipv4.common(a, a))
    }

    @Test
    fun pilotChallengesAreDeterministicBySeed() {
        for (id in Pilot.ids) for (l in 1..10) for (s in 0L..99L) {
            assertEquals(Pilot.make(id, l, s), Pilot.make(id, l, s))
            assertEquals(Rounds.make(id, l, s), Rounds.make(id, l, s))
            val c = Pilot.make(id, l, s)
            assertEquals(s, c.item)
            assertTrue(c.format in Pilot.formats(id, l))
            assertTrue(c.explanation.isNotBlank())
            assertTrue(c.deadlineMs >= 5000)
            // A forced format rebuilds the exact same challenge (spaced repetition depends on it).
            assertEquals(c, Pilot.make(id, l, s, forced = c.format))
        }
    }

    @Test
    fun subnetAnswersAreValid() {
        val seen = mutableSetOf<Int>()
        for (l in 1..10) for (s in 0L..299L) {
            val c = Pilot.make("subnet", l, s)
            val t = c.subnet!!
            assertEquals(Kind.SUBNET, c.kind)
            assertEquals(t.answer, c.answer)
            assertTrue(t.answer in 1..31)
            assertNotEquals(t.answer, t.start)
            assertEquals(l < 7, t.hints)
            assertTrue(c.accepts(listOf(t.answer)))
            assertFalse(c.accepts(listOf(t.answer + 1)))
            assertFalse(c.accepts(listOf(t.answer - 1)))
            when (t.format) {
                Pilot.HOSTS -> {
                    assertTrue(Ipv4.hosts(t.answer) >= t.hosts)
                    assertTrue(Ipv4.hosts(t.answer + 1) < t.hosts)
                    assertTrue(c.prompt.contains("${t.hosts} equipos"))
                    if (l <= 3) assertTrue(t.answer >= 25)
                }
                Pilot.MASK -> {
                    assertTrue(c.prompt.contains(Ipv4.text(Ipv4.mask(t.answer))))
                    if (l <= 3) assertTrue(t.answer >= 24)
                }
                Pilot.PAIR -> {
                    assertTrue(l >= 4)
                    assertNotEquals(t.addr, t.other)
                    assertEquals(t.answer, Ipv4.common(t.addr, t.other))
                    assertEquals(Ipv4.network(t.addr, t.answer), Ipv4.network(t.other, t.answer))
                    assertNotEquals(
                        Ipv4.network(t.addr, t.answer + 1),
                        Ipv4.network(t.other, t.answer + 1),
                    )
                    if (l <= 6) assertTrue(t.answer >= 24)
                }
                else -> fail("Formato desconocido ${t.format}")
            }
            seen += t.format
        }
        assertEquals(setOf(Pilot.HOSTS, Pilot.MASK, Pilot.PAIR), seen)
    }

    @Test
    fun everyBaitHasItsClueInTheText() {
        for (b in NetSec.baits) {
            assertTrue(b.mark, b.mark.isNotEmpty())
            assertTrue("${b.mark} no está en ${b.text}", b.text.contains(b.mark))
        }
    }

    @Test
    fun phishingCardsHighlightTheClue() {
        for (l in 1..10) for (s in 0L..199L) {
            val c = Pilot.make("phishing", l, s)
            val t = c.inbox!!
            assertEquals(Kind.INBOX, c.kind)
            assertEquals(listOf("Seguro", "Trampa"), c.choices)
            assertEquals(if (t.phishing) 1 else 0, c.answer)
            assertTrue(t.clueStart >= 0 && t.clueEnd > t.clueStart && t.clueEnd <= t.text.length)
            assertTrue(NetSec.baits.any { it.text == t.text && it.mark == t.clue })
            assertEquals(c.format, t.channel) // every tier has both mails and links
            assertTrue(c.explanation.startsWith(if (t.phishing) "Trampa:" else "Seguro:"))
        }
    }

    @Test
    fun firewallTasksAreSolvableAndScale() {
        for (l in 1..10) for (s in 0L..299L) {
            val c = Pilot.make("ports", l, s)
            val t = c.firewall!!
            assertEquals(Kind.FIREWALL, c.kind)
            assertEquals(if (l <= 3) 4 else if (l <= 6) 5 else 6, t.rows.size)
            assertEquals(t.rows.size, t.rows.map { it.port }.distinct().size)
            assertEquals(t.rows.map { it.port }.sorted(), t.rows.map { it.port })
            assertEquals(t.rows.size, t.start.size)
            assertEquals(t.rows.size, t.expected.size)
            assertNotEquals(t.start, t.expected)
            when (t.format) {
                Pilot.BLOCK -> assertTrue(t.start.all { it })
                Pilot.ALLOW -> assertTrue(t.start.none { it })
                Pilot.AUDIT -> assertTrue(l >= 4)
            }
            assertTrue(c.accepts(t.expectedOpen))
            assertFalse(c.accepts(t.start.indices.filter { t.start[it] }))
            if (l <= 6 && t.format != Pilot.AUDIT)
                assertEquals(1, t.start.indices.count { t.start[it] != t.expected[it] })
        }
    }

    private fun session(
        id: String,
        level: Int,
        base: Long,
        wrong: (Int) -> Boolean,
    ): List<Challenge> {
        val h = mutableListOf<RoundMark>()
        val out = mutableListOf<Challenge>()
        for (i in 0 until 12) {
            val c = Rounds.make(id, level, base + i * 10007L, h)
            out += c
            h += RoundMark(c.item, c.format, !wrong(i))
        }
        return out
    }

    @Test
    fun neverTheSameFormatTwiceInARow() {
        for (id in Pilot.ids) for (l in 1..10) for (s in 0L..39L) for (pattern in 0..2) {
            val rounds =
                session(id, l, s * 7919) { i ->
                    when (pattern) {
                        0 -> false
                        1 -> true
                        else -> (i + s).toInt() % 3 == 0
                    }
                }
            for (i in 1 until rounds.size) assertNotEquals(
                "$id nivel $l semilla $s ronda $i",
                rounds[i - 1].format,
                rounds[i].format,
            )
        }
    }

    @Test
    fun failedItemsComeBackLaterInTheSession() {
        for (id in Pilot.ids) for (l in 1..10) for (s in 0L..29L) {
            val rounds = session(id, l, s * 104729) { it == 0 }
            val again =
                rounds.withIndex().filter { it.index > 0 && it.value.item == rounds[0].item }
            assertEquals("$id nivel $l semilla $s", 1, again.size)
            val (k, c) = again.single()
            assertTrue(k in (1 + Rounds.GAP)..6)
            assertTrue(c.review)
            assertEquals(rounds[0], c.copy(review = false))
        }
        // All wrong: each item is shown at most 1 + MAX_REPEATS times.
        for (id in Pilot.ids) for (s in 0L..29L) {
            val rounds = session(id, 5, s) { true }
            rounds
                .groupBy { it.item }
                .values
                .forEach { assertTrue(it.size <= 1 + Rounds.MAX_REPEATS) }
            assertTrue(rounds.any { it.review })
        }
        // All right: no reviews.
        for (id in Pilot.ids) assertTrue(session(id, 5, 3) { false }.none { it.review })
    }

    @Test
    fun otherGamesAndFlashModeAreUnchanged() {
        for (g in Games.all) for (l in 1..10) for (s in 0L..9L) {
            if (g.id !in Pilot.ids)
                assertEquals(
                    Generator.make(g.id, l, s),
                    Rounds.make(g.id, l, s, listOf(RoundMark(1L, 0, false))),
                )
        }
        // Relámpago still plays the 4-option version of the pilot games.
        for (id in Pilot.ids) for (l in 1..10) assertEquals(
            Kind.CHOICE,
            Generator.make(id, l, 1).kind,
        )
    }

    // ---------- Lote B ----------
    @Test
    fun noBaitRepeatsInA12RoundSessionExceptMarkedReviews() {
        for (l in 1..10) for (s in 0L..59L) for (pattern in 0..2) {
            val rounds =
                session("phishing", l, s * 7919 + 11) { i ->
                    when (pattern) {
                        0 -> false
                        1 -> true
                        else -> (i + s).toInt() % 3 == 0
                    }
                }
            val fresh = rounds.filter { !it.review }.map { it.inbox!!.text }
            assertEquals("nivel $l semilla $s patrón $pattern", fresh.size, fresh.distinct().size)
            // A review shows exactly the bait it reviews.
            rounds
                .filter { it.review }
                .forEach { r ->
                    assertEquals(rounds.first { it.item == r.item && !it.review }.inbox, r.inbox)
                }
        }
    }

    @Test
    fun quickModesUseTheBoardsAndSkipSeenBaits() {
        for (l in 1..10) {
            assertEquals(Kind.SUBNET, Quick.make("subnet", l, 5).kind)
            assertEquals(Kind.FIREWALL, Quick.make("ports", l, 5).kind)
            assertEquals(Kind.INBOX, Quick.make("phishing", l, 5).kind)
            assertEquals(Kind.BITS, Quick.make("bits", l, 5).kind)
            assertEquals(Generator.make("logs", l, 5), Quick.make("logs", l, 5))
            val seen = linkedSetOf<String>()
            val tier = 1 + (l - 1) * 3 / 10
            val size = NetSec.baits.count { it.tier in (tier - 1).coerceAtLeast(1)..tier }
            for (k in 0 until size) {
                val t = Quick.make("phishing", l, k * 31L, seen).inbox!!.text
                assertTrue("nivel $l reto $k", seen.add(t))
            }
            Quick.make("phishing", l, 999L, seen) // exhausted: still builds a card
        }
    }

    @Test
    fun bitSwitchesAddUpToTheTarget() {
        for (l in 1..10) for (s in 0L..199L) {
            val c = Pilot.make("bits", l, s)
            val t = c.bits!!
            assertEquals(Kind.BITS, c.kind)
            assertEquals(4 + (l - 1) * 4 / 9, t.width)
            assertTrue(t.target in 1 until (1 shl t.width))
            assertEquals(t.target, c.answer)
            assertEquals(t.target, t.weights.indices.sumOf { t.bit(t.target, it) * t.weights[it] })
            if (t.format == Pilot.BUILD) assertEquals(0, t.start)
            else {
                assertNotEquals(t.target, t.start)
                assertTrue(t.start in 1 until (1 shl t.width))
            }
            assertTrue(c.accepts(listOf(t.target)))
            assertFalse(c.accepts(listOf(t.start)))
            assertEquals(l < 8, t.hints)
        }
    }
}
