package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

class NetSecTest {
    @Test
    fun sixGamesInOrder() {
        assertEquals(NetSec.ids, Games.all.filter { it.area == Area.NETSEC }.map { it.id })
    }

    @Test
    fun challengesAreValid() {
        for (id in NetSec.ids) for (l in 1..10) for (s in 0L..199L) {
            val c = Generator.make(id, l, s)
            assertEquals(
                if (id == "phishing" || (id == "sqlsafe" && c.board.isEmpty())) 2 else 4,
                c.choices.size,
            )
            assertEquals(c.choices.size, c.choices.distinct().size)
            assertTrue(c.explanation.isNotBlank())
            assertTrue(c.deadlineMs >= 5000)
        }
    }

    @Test
    fun bitsScaleWithLevel() {
        for (s in 0L..99L) {
            val c = Generator.make("bits", 1, s)
            val bin =
                if (c.choices[0].all { it in "01" } && c.choices[0].length == 4) c.choices[c.answer]
                else c.prompt.lines()[0]
            assertEquals(4, bin.length)
        }
        for (s in 0L..99L) {
            val c = Generator.make("bits", 10, s)
            val bin =
                if (c.prompt.lines()[0].length == 8 && c.prompt.lines()[0].all { it in "01" })
                    c.prompt.lines()[0]
                else c.choices[c.answer]
            assertEquals(8, bin.length)
            val v =
                if (bin == c.prompt.lines()[0]) c.choices[c.answer].toInt()
                else c.prompt.lines()[0].toInt()
            assertEquals(v, bin.toInt(2))
        }
    }

    @Test
    fun subnetNetworkIsCorrect() {
        for (s in 0L..199L) {
            val c = Generator.make("subnet", 3, s)
            if (!c.prompt.contains("red?")) continue
            val (addr, p) = c.prompt.lines()[0].split(" /")
            val ip = addr.split(".").fold(0L) { a, o -> a * 256 + o.toLong() }
            val size = 1L shl (32 - p.toInt())
            val net = ip / size * size
            assertEquals(
                "${net shr 24 and 255}.${net shr 16 and 255}.${net shr 8 and 255}.${net and 255}",
                c.choices[c.answer],
            )
        }
    }
}
