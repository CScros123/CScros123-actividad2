package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

/**
 * Fase 3, Parejas de incidentes: bank, boards per level, trios, partial shuffle, hints and that
 * themes change nothing.
 */
class IncidentPairsTest {
    @Test
    fun bankKeysAreUniqueAndBoardsHaveOnePartnerPerCard() {
        assertEquals(IncidentPairs.bank.size, IncidentPairs.bank.map { it.key }.distinct().size)
        for (l in 1..10) for (s in 0L..299L) {
            val b = IncidentPairs.make(l, s)
            val shape = IncidentPairs.shape(l)
            assertEquals(shape[0] * shape[1], b.cards.size)
            assertEquals(b.cards.size, b.cards.map { it.text }.distinct().size)
            assertEquals(b.entries.size, b.entries.map { it.topic }.distinct().size)
            assertEquals(shape[3], b.trios)
            assertEquals(b.cards.indices.toList(), b.cards.map { it.id })
            if (l <= 2) assertTrue(b.entries.all { it.group == IncidentPairs.PORT })
            if (l in 3..4) assertTrue(b.entries.any { it.group == IncidentPairs.ATTACK })
            if (l >= 5) {
                assertTrue(b.entries.any { it.group == IncidentPairs.ALERT })
                assertTrue(b.entries.any { it.topic == "sqli" })
                assertTrue(b.entries.any { it.topic == "xss" })
            }
            assertEquals(l >= 9, b.shuffle)
        }
        assertTrue(
            (0L..100L).any { s ->
                IncidentPairs.make(4, s).entries.map { it.key }.containsAll(listOf("p25", "p465"))
            }
        )
    }

    @Test
    fun sameSeedSameBoardWhateverTheTheme() {
        for (l in 1..10) for (s in 0L..40L) {
            val b = IncidentPairs.make(l, s)
            for (t in IncidentPairs.themes) assertEquals(b, IncidentPairs.make(l, s, t.id))
        }
        assertEquals(
            IncidentPairs.themes.drop(1).map { it.id }.toSet(),
            Rewards.cosmetics.filter { it.theme }.map { it.id }.toSet(),
        )
        assertEquals("theme:clasico", IncidentPairs.theme(null).id)
    }

    @Test
    fun targetsFollowTheTable() {
        assertEquals(10, IncidentPairs.make(1, 0).target) // 6 pairs × 1.6
        assertEquals(13, IncidentPairs.make(3, 0).target) // 8 × 1.6
        assertEquals(17, IncidentPairs.make(5, 0).target) // 10 × 1.7
        assertEquals(24, IncidentPairs.make(7, 0).target) // (11 groups + 2 trios) × 1.8
    }

    @Test
    fun perfectPlayMatchesEveryGroup() {
        val b = IncidentPairs.make(8, 3)
        val p = PairsPlay(b)
        for ((g, e) in b.entries.withIndex()) {
            val ids = b.cards.filter { it.entry == g }.map { it.id }
            assertEquals(PairsPlay.Flip.OPENED, p.flip(ids[0]))
            if (e.trio) {
                assertEquals(PairsPlay.Flip.PARTIAL, p.flip(ids[1]))
                assertEquals(PairsPlay.Flip.TRIO, p.flip(ids[2]))
            } else assertEquals(PairsPlay.Flip.MATCH, p.flip(ids[1]))
        }
        assertTrue(p.done)
        val log = p.log()
        assertTrue(log.passed)
        assertEquals(b.groups + b.trios, log.attempts)
        assertEquals(b.groups, log.maxStreak)
        assertEquals(b.entries.map { it.key }, log.keys)
        assertEquals(log, PairsLog.decode(log.encode()))
        assertTrue(log.score > 10 * b.groups) // streak ≥ 4 doubles the points
    }

    @Test
    fun missesFlipBackHintAndPartialShuffle() {
        val b = IncidentPairs.make(9, 7)
        val p = PairsPlay(b)
        // Two cards of different groups, again and again.
        val a = b.cards.first { it.entry == 0 }.id
        val c = b.cards.first { it.entry == 1 && b.entries[1].texts.size == 2 || it.entry == 1 }.id
        p.flip(a)
        assertEquals(PairsPlay.Flip.MISS, p.flip(c))
        assertEquals(PairsPlay.Flip.IGNORED, p.flip(b.cards[5].id))
        assertNull(p.hide())
        p.flip(a)
        p.flip(c)
        p.hide()
        p.flip(a)
        p.flip(c)
        p.hide()
        assertEquals(3, p.missRow)
        assertTrue(p.hint() in listOf(a, c))
        val before = p.cells.copyOf()
        p.flip(a)
        p.flip(c)
        val swap = p.hide()
        assertNotNull(swap)
        assertEquals(1, p.shuffles)
        assertEquals(0, p.missRow)
        assertEquals(before[swap!![0]], p.cells[swap[1]])
        assertEquals(before[swap[1]], p.cells[swap[0]])
        assertTrue(p.informed >= 3)
        assertEquals(4, p.attempts)
        // Lower levels never shuffle.
        val q = PairsPlay(IncidentPairs.make(5, 7))
        val x = q.board.cards.first { it.entry == 0 }.id
        val y = q.board.cards.first { it.entry == 1 }.id
        repeat(5) {
            q.flip(x)
            q.flip(y)
            assertNull(q.hide())
        }
    }

    @Test
    fun everyEntryIsAnAlbumCard() {
        assertTrue(Album.cards.count { it.game == "pairs" } >= 10)
        assertNotNull(Album.get("pairs:sqliparam"))
        val log = PairsLog(10, 10, 6, 6, 4, 0, true, listOf("p22", "sqliparam"))
        assertEquals(
            listOf("pairs:p22", "pairs:sqliparam"),
            Album.forBoard("pairs", log.encode(), emptySet()).map { it.key },
        )
    }
}
