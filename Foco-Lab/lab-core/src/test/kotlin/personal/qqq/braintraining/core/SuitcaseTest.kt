package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

/** Fase 2: La maleta del técnico (docs/DISENO-JUEGOS-V2.md §2). */
class SuitcaseTest {
    @Test
    fun sameSeedSameGame() {
        for (l in 1..10) for (s in 0L..99L) {
            assertEquals(Suitcase.make(l, s), Suitcase.make(l, s))
            assertEquals(Generator.make("suitcase", l, s), Generator.make("suitcase", l, s))
            val a = SuitcaseTray(Suitcase.make(l, s), 5)
            val b = SuitcaseTray(Suitcase.make(l, s), 5)
            assertArrayEquals(a.slots, b.slots)
        }
    }

    @Test
    fun levelTableGrows() {
        val targets = (1..10).map { Suitcase.level(it).target }
        assertEquals(listOf(5, 5, 7, 7, 9, 9, 11, 11, 13, 13), targets)
        for (l in 2..10) {
            val a = Suitcase.level(l - 1)
            val b = Suitcase.level(l)
            assertTrue(b.tray >= a.tray)
            assertTrue(b.categories.size >= a.categories.size)
        }
        assertEquals(listOf(SuitCategory.CABLE), Suitcase.level(1).categories)
        assertFalse(Suitcase.level(4).twins)
        assertTrue(Suitcase.level(5).twins)
        assertTrue((0L..199L).any { Suitcase.make(7, it).rule == SuitRule.LAYER })
        assertTrue((0L..199L).none { Suitcase.make(6, it).rule != SuitRule.ARRIVAL })
        assertTrue((0L..199L).any { Suitcase.make(9, it).rule == SuitRule.TRACE })
        assertTrue((0L..199L).none { Suitcase.make(8, it).rule == SuitRule.TRACE })
        // About 1 round in 4 uses the layer rule at level 7–8.
        val layer = (0L..399L).count { Suitcase.make(7, it).rule == SuitRule.LAYER }
        assertTrue(layer in 60..140)
    }

    @Test
    fun chainsAreValidAndDecoysNeverBelong() {
        for (l in 1..10) for (s in 0L..149L) {
            val t = Suitcase.make(l, s)
            val lv = Suitcase.level(l)
            assertEquals(lv.target, t.target)
            assertEquals(t.target, t.chain.size)
            assertEquals(t.chain.size, t.chain.map { it.key }.distinct().size)
            assertTrue(t.start in 2 until t.target)
            val keys = t.chain.map { it.key }.toSet()
            val labels = t.chain.map { it.label }.toSet()
            assertTrue(t.decoys.none { it.key in keys || it.label in labels })
            assertTrue("$l $s ${t.decoys.size}", t.decoys.size >= Suitcase.MAX_TRAY - 1)
            when (t.rule) {
                SuitRule.ARRIVAL -> {
                    assertTrue(t.chain.all { it.category in lv.categories })
                    if (lv.categories.size > 1)
                        assertTrue(t.chain.map { it.category }.distinct().size > 1)
                }
                SuitRule.LAYER -> {
                    assertTrue(t.chain.all { it.category == SuitCategory.DEVICE })
                    val o = t.order(t.target)
                    assertEquals(o.map { it.layer }.sorted(), o.map { it.layer })
                }
                SuitRule.TRACE -> {
                    assertEquals(1, t.chain.count { it.label == "* * *" })
                    assertTrue(t.chain.first().label.startsWith("192.168.1.1"))
                }
            }
            if (lv.twins && t.rule == SuitRule.ARRIVAL) {
                val twins = t.chain.mapNotNull { it.twin }.filter { it !in keys }
                if (twins.isNotEmpty())
                    assertTrue(t.decoys.take(twins.distinct().size).all { it.key in twins })
            }
        }
    }

    @Test
    fun theExpectedItemIsAlwaysOnTheTray() {
        for (l in 1..10) for (s in 0L..79L) for (extra in 0..1) {
            val t = Suitcase.make(l, s)
            for (len in t.start..t.target) {
                val tray = SuitcaseTray(t, len, extra)
                assertEquals(minOf(t.tray + extra, Suitcase.MAX_TRAY), tray.size)
                while (!tray.done) {
                    assertEquals(
                        "tray $l $s $len",
                        tray.size,
                        tray.slots.mapNotNull { it?.key }.distinct().size,
                    )
                    val slot = tray.slotOf(tray.expected)
                    assertTrue("$l $s $len step ${tray.step}", slot >= 0)
                    // A wrong slot changes nothing.
                    val wrong = tray.slots.indices.first { it != slot }
                    val before = tray.slots.toList()
                    assertFalse(tray.take(wrong))
                    assertEquals(before, tray.slots.toList())
                    assertTrue(tray.take(slot))
                }
                assertEquals(len, tray.step)
            }
        }
    }

    @Test
    fun layerRuleReordersAsDevicesArrive() {
        val t =
            (0L..400L)
                .map { Suitcase.make(7, it) }
                .first {
                    it.rule == SuitRule.LAYER &&
                        it.chain.take(4).map { c -> c.layer } !=
                            it.chain.take(4).map { c -> c.layer }.sorted()
                }
        assertNotEquals(t.chain.take(4), t.order(4))
        assertEquals(t.chain.take(4).toSet(), t.order(4).toSet())
    }

    @Test
    fun scoringHelpAndLogs() {
        assertEquals(100, SuitcaseLog(9, false, 9).points)
        assertTrue(SuitcaseLog(9, false, 9).clean)
        assertEquals(50, SuitcaseLog(9, true, 9).points)
        assertFalse(SuitcaseLog(9, true, 9).clean)
        assertTrue(SuitcaseLog(9, true, 9).reached)
        assertEquals(66, SuitcaseLog(6, false, 9).points)
        assertFalse(SuitcaseLog(6, false, 9).reached)
        assertEquals(100, SuitcaseLog(14, false, 9).points)
        val log = SuitcaseLog(7, true, 9, "port23", "port22", listOf("cat6", "port22"))
        assertEquals(log, SuitcaseLog.decode(log.encode()))
        assertNull(SuitcaseLog.decode("basura"))
        assertNull(SuitcaseLog.decode(null))
        assertEquals(
            "Te cuesta 23 vs 22",
            Suitcase.weakest(listOf(log, log, SuitcaseLog(5, true, 9, "cat6"))),
        )
    }

    @Test
    fun tuningIsAnnounced() {
        val fail = SuitcaseLog(3, true, 9)
        val clean = SuitcaseLog(9, false, 9)
        assertEquals(SuitTuning(), Suitcase.tuning(listOf(fail)))
        assertEquals(500L, Suitcase.tuning(listOf(clean, fail, fail)).extraMs)
        assertEquals(1, Suitcase.tuning(listOf(clean, clean, clean)).extraDecoys)
        assertEquals(0, Suitcase.tuning(listOf(clean, clean, fail)).extraDecoys)
        listOf(Suitcase.tuning(listOf(fail, fail)), Suitcase.tuning(listOf(clean, clean, clean)))
            .forEach { assertNotNull(it.note) }
    }

    @Test
    fun bankKeysAreStableAndUnique() {
        assertEquals(Suitcase.bank.size, Suitcase.keys.distinct().size)
        assertEquals("port22", Suitcase.item("port22")!!.key)
        assertEquals("port23", Suitcase.item("port22")!!.twin)
        assertTrue(
            Suitcase.bank.filter { it.category == SuitCategory.DEVICE }.all { it.layer in 1..7 }
        )
        assertTrue(Suitcase.bank.mapNotNull { it.twin }.all { Suitcase.item(it) != null })
    }
}
