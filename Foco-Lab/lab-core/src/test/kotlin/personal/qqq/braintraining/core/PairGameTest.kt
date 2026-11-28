package personal.qqq.braintraining.core

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class PairGameTest {
    @Test
    fun validPairsStayUniqueAndCannotMatchSameSide() {
        for (difficulty in Difficulty.values()) for (seed in 0..50) {
            val deck = PairGame.newDeck(difficulty, Random(seed))
            assertEquals(4, deck.left.size)
            assertEquals(4, deck.right.size)
            assertEquals(4, deck.right.map { it.text }.distinct().size)
            assertNull(deck.selectMatch(deck.left[0].id, deck.left[1].id, Random(seed)))
            assertNull(
                deck.selectMatch(
                    deck.left[0].id,
                    deck.right.first { it.pair != deck.left[0].pair }.id,
                    Random(seed),
                )
            )
        }
    }

    @Test
    fun completedPairIsRemovedAndRemainingCardsChangePositions() {
        for (seed in 0..50) {
            val deck = PairGame.newDeck(Difficulty.MEDIUM, Random(seed))
            val left = deck.left.first()
            val right = deck.right.first { it.pair == left.pair }
            val next = deck.selectMatch(left.id, right.id, Random(seed))!!
            assertEquals(1, next.matches)
            assertEquals(3, next.left.size)
            assertEquals(3, next.right.size)
            assertFalse(next.cards.any { it.pair == left.pair })
            next.left.forEachIndexed { index, card ->
                assertNotEquals(deck.left.indexOf(card), index)
            }
            next.right.forEachIndexed { index, card ->
                assertNotEquals(deck.right.indexOf(card), index)
            }
            for (card in next.left) assertTrue(next.right.any { it.pair == card.pair })
        }
    }

    @Test
    fun newDeckPreservesOngoingCount() {
        assertEquals(8, PairGame.newDeck(Difficulty.HARD, Random(42), 8).matches)
    }

    @Test
    fun everySuccessiveMatchRemainsSolvable() {
        for (seed in 0..100) {
            val random = Random(seed)
            var deck = PairGame.newDeck(Difficulty.HARD, random)
            repeat(4) { count ->
                val before = deck
                val a = before.left.first()
                val b = before.right.first { it.pair == a.pair }
                deck = before.selectMatch(a.id, b.id, random)!!
                assertEquals(count + 1, deck.matches)
                if (deck.left.size >= 2)
                    deck.left.forEachIndexed { index, card ->
                        assertNotEquals(before.left.indexOf(card), index)
                    }
                if (deck.right.size >= 2)
                    deck.right.forEachIndexed { index, card ->
                        assertNotEquals(before.right.indexOf(card), index)
                    }
            }
            assertTrue(deck.cards.isEmpty())
        }
    }

    @Test
    fun networkDecksPairPortsMasksAndHosts() {
        for (difficulty in Difficulty.values()) {
            val bank = PairGame.netBank(difficulty)
            assertEquals(bank.size, bank.map { it.first }.distinct().size)
            assertEquals(bank.size, bank.map { it.second }.distinct().size)
            assertTrue(bank.contains("Puerto 22" to "SSH"))
            if (difficulty != Difficulty.EASY) assertTrue(bank.contains("/24" to "255.255.255.0"))
            if (difficulty == Difficulty.HARD)
                assertTrue(bank.contains("Hosts de un /30" to "2 hosts"))
            for (seed in 0..50) {
                val deck = PairGame.newDeck(difficulty, Random(seed), topic = PairTopic.NET)
                assertEquals(4, deck.left.size)
                assertEquals(4, deck.right.map { it.text }.distinct().size)
                for (card in deck.left) {
                    val match = deck.right.single { it.pair == card.pair }
                    assertTrue(bank.contains(card.text to match.text))
                }
                val a = deck.left.first()
                assertNotNull(
                    deck.selectMatch(a.id, deck.right.first { it.pair == a.pair }.id, Random(seed))
                )
            }
        }
        // The arithmetic deck is unchanged by the new parameter.
        assertEquals(
            PairGame.newDeck(Difficulty.MEDIUM, Random(7)),
            PairGame.newDeck(Difficulty.MEDIUM, Random(7), topic = PairTopic.MATH),
        )
    }
}
