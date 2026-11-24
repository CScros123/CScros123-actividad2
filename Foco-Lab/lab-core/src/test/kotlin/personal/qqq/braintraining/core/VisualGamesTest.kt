package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

class VisualGamesTest {
    @Test
    fun balanceCanBeSolvedFromTheDisplayedEquation() {
        for (level in 1..10) for (seed in 0L..299L) {
            val c = Generator.make("balance", level, seed)
            val numbers = Regex("\\d+").findAll(c.prompt).map { it.value.toInt() }.toList()
            val (copies, known, total) = numbers
            assertEquals(0, (total - known) % copies)
            val answer = (total - known) / copies
            assertEquals(answer, c.choices[c.answer].toInt())
            assertEquals(total, copies * answer + known)
            assertEquals(numbers, c.board.map { it.toInt() })
            assertEquals(4, c.choices.distinct().size)
            assertTrue(c.explanation.contains("Comprueba"))
        }
    }

    @Test
    fun compassObeysBothRulesAndEveryDirection() {
        val seen = mutableSetOf<Pair<Boolean, Int>>()
        for (level in 1..10) for (seed in 0L..199L) {
            val c = Generator.make("compass", level, seed)
            val direction = c.board[0].toInt()
            val opposite = c.prompt == "DIRECCIÓN OPUESTA"
            val arrows = listOf("↑", "→", "↓", "←")
            val requested =
                if (opposite)
                    mapOf("↑" to "↓", "↓" to "↑", "→" to "←", "←" to "→")[arrows[direction]]!!
                else arrows[direction]
            assertTrue(c.choices[c.answer].startsWith(requested))
            assertEquals(opposite, c.board[1].toBoolean())
            if (level < 4) assertTrue(opposite)
            seen += opposite to direction
        }
        assertEquals(8, seen.size)
        assertEquals(0, Adaptive.next(Progress(), "compass", 12, 12, 13000).up)
        assertEquals(1, Adaptive.next(Progress(), "compass", 12, 12, 3000).up)
    }

    @Test
    fun orbitIsABoardRoundWithoutNumbersThatGrowsGradually() {
        for (level in 1..10) for (seed in 0L..199L) for (round in listOf(0, 5, 10)) {
            val c = Generator.make("orbit", level, seed, round)
            assertEquals(Kind.ORBIT, c.kind)
            val t = c.orbit!!
            assertEquals(8, t.nodes)
            assertTrue(c.targets.all { it in 0 until t.nodes })
            assertTrue(t.steps.size in 3..9)
            assertEquals(c, Generator.make("orbit", level, seed, round))
        }
        assertEquals(3, Generator.make("orbit", 1, 0, 0).orbit!!.steps.size)
        assertEquals(9, Generator.make("orbit", 10, 0, 11).orbit!!.steps.size)
    }
}
