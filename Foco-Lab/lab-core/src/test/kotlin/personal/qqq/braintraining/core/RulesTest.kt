package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    @Test
    fun generators() {
        for (g in Games.all) for (l in 1..10) for (s in 0L..49L) {
            val c = Generator.make(g.id, l, s)
            assertEquals(c, Generator.make(g.id, l, s))
            assertTrue(c.deadlineMs > 0)
            if (c.choices.isNotEmpty()) assertTrue(c.answer in c.choices.indices)
            if (c.kind == Kind.GRID) {
                assertEquals(c.targets.size, c.targets.distinct().size)
                assertTrue(c.targets.all { it in 0 until c.columns * c.columns })
            }
            if (
                g.id in
                    listOf("math", "series", "working", "pattern", "matrix", "choice") + NetSec.ids
            )
                assertEquals(c.choices.size, c.choices.distinct().size)
            if (g.id == "search") assertEquals(1, c.choices.count { it == c.choices[c.answer] })
        }
    }

    @Test
    fun adaptation() {
        val p = Adaptive.next(Progress(3), "grid", 11, 12, null)
        assertEquals(3, p.level)
        assertEquals(1, p.up)
        assertEquals(4, Adaptive.next(p, "grid", 11, 12, null).level)
        assertEquals(0, Adaptive.next(p, "grid", 9, 12, null).up)
        assertEquals(p, Adaptive.next(p, "grid", 4, 5, null))
        assertEquals(
            2,
            Adaptive.next(Adaptive.next(Progress(3), "grid", 6, 12, null), "grid", 6, 12, null)
                .level,
        )
    }

    @Test
    fun clampsAndTiming() {
        assertEquals(10, Adaptive.next(Progress(10, up = 1), "grid", 12, 12, null).level)
        assertEquals(1, Adaptive.next(Progress(1, down = 1), "grid", 0, 12, null).level)
        assertEquals(0, Adaptive.next(Progress(), "flanker", 12, 12, 10000).up)
        assertEquals(0, Adaptive.next(Progress(), "reaction", 12, 12, null).up)
        assertEquals(1, Adaptive.next(Progress(), "reaction", 12, 12, 300).up)
    }

    @Test
    fun statistics() {
        assertNull(median(emptyList()))
        assertEquals(3L, median(listOf(1, 5, 3)))
        assertEquals(4L, median(listOf(2, 6)))
        assertEquals(0, Adaptive.xp(3, 4))
        assertEquals(20, Adaptive.xp(12, 12))
    }
}
