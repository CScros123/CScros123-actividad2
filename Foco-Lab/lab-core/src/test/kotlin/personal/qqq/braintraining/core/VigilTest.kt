package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

/** Fase 2: Vigía de alertas (docs/DISENO-JUEGOS-V2.md §3). */
class VigilTest {
    @Test
    fun sameSeedSameTramo() {
        for (l in 1..10) for (s in 0L..99L) {
            assertEquals(Vigil.make(l, s), Vigil.make(l, s))
            assertEquals(Generator.make("vigil", l, s), Generator.make("vigil", l, s))
        }
        assertNotEquals(Vigil.make(3, 1).lines, Vigil.make(3, 2).lines)
    }

    @Test
    fun levelTable() {
        val lv = (1..10).map(Vigil::level)
        assertEquals(
            listOf(1200L, 1200L, 1000L, 1000L, 900L, 900L, 900L, 900L, 800L, 800L),
            lv.map { it.intervalMs },
        )
        assertEquals(listOf(80, 80, 80, 80, 85, 85, 85, 85, 90, 90), lv.map { it.goal })
        assertEquals(listOf(2, 2, 2, 2, 2, 2, 1, 1, 1, 1), lv.map { it.maxFalse })
        for (i in 1 until 10) {
            assertTrue(lv[i].patterns >= lv[i - 1].patterns)
            assertTrue(lv[i].ratePercent <= lv[i - 1].ratePercent)
        }
        assertEquals(2, lv[9].channels)
        assertFalse(lv[0].decoys)
        assertTrue(lv[1].decoys)
        assertTrue(lv[6].decoys)
    }

    /**
     * The rule engine and the generator agree: every attack fires exactly once at its detectAt, and
     * no noise line ever fires.
     */
    @Test
    fun rulesMatchTheGeneratedAttacks() {
        val seen = mutableSetOf<Int>()
        for (l in 1..10) for (s in 0L..199L) {
            val t = Vigil.make(l, s)
            val fired = Vigil.triggers(t.lines, t.guard)
            assertTrue("$l $s", t.groups.isNotEmpty())
            val expected = t.groups.map { it.detectAt }.toSet()
            assertEquals("$l $s", expected, fired.indices.filter { fired[it] >= 0 }.toSet())
            t.groups.forEach { g ->
                assertEquals(g.pattern, fired[g.detectAt])
                assertTrue(g.pattern < Vigil.level(l).patterns)
                seen += g.pattern
                // Attacks scroll out before the tramo ends, so every one is judged.
                assertTrue(g.last <= t.lines.size - Vigil.VISIBLE - 2)
                assertTrue(g.lines.all { t.lines[it].group == t.groups.indexOf(g) })
            }
            assertEquals(t.lines.sortedBy { it.time }, t.lines)
            assertEquals(t.lines.size, t.lines.map { it.time }.distinct().size)
            // Rare targets: attack lines stay a minority.
            assertTrue(t.groups.sumOf { it.lines.size } * 100 / t.lines.size <= 45)
        }
        assertEquals((0..4).toSet(), seen)
    }

    @Test
    fun decoysLookLikeAttacksButAreNot() {
        val t =
            (0L..50L)
                .map { Vigil.make(7, it) }
                .first { it.lines.any { l -> l.why == Vigil.WHY_SPREAD } }
        val spread = t.lines.filter { it.why == Vigil.WHY_SPREAD }
        assertEquals(5, spread.size)
        assertEquals(5, spread.map { it.src }.distinct().size)
        assertTrue(spread.all { it.src.startsWith("10.") && it.kind == Vigil.FAIL })
        assertTrue(
            (0L..50L)
                .map { Vigil.make(1, it) }
                .none { it.lines.any { l -> l.why == Vigil.WHY_SPREAD } }
        )
        // The guard logs in at night and it is not an anomaly.
        val g =
            (0L..80L)
                .map { Vigil.make(3, it) }
                .first { it.lines.any { l -> l.kind == Vigil.LOGIN && l.user == it.guard } }
        assertTrue(
            Vigil.triggers(g.lines, g.guard).let { f ->
                g.lines.indices
                    .filter { g.lines[it].kind == Vigil.LOGIN && g.lines[it].user == g.guard }
                    .all { f[it] == -1 }
            }
        )
    }

    @Test
    fun shiftScoresHitsLapsesAndFalseAlarms() {
        val t = (0L..50L).map { Vigil.make(5, it) }.first { it.groups.size >= 2 }
        val s = VigilShift(t)
        val a = t.groups[0]
        val b = t.groups.filter { it != a }.minBy { it.last }
        val early = a.lines.first().takeIf { it < a.detectAt }
        if (early != null) assertEquals(VigilShift.Mark.EARLY, s.mark(early, early + 1))
        assertEquals(VigilShift.Mark.HIT, s.mark(a.lines.first(), a.detectAt + 1))
        assertEquals(VigilShift.Mark.IGNORED, s.mark(a.last, t.lines.size))
        val noise = t.lines.indices.first { t.lines[it].group < 0 }
        assertEquals(VigilShift.Mark.FALSE, s.mark(noise, noise + 1))
        assertEquals(Vigil.SHIELDS - 1, s.shields)
        assertEquals(listOf(b), s.advance(b.last + 1))
        assertEquals(Vigil.SHIELDS - 3, s.shields)
        assertTrue(s.advance(b.last + 1).isEmpty())
        val log = s.finish(1)
        assertEquals(t.groups.size - 1, log.lapses)
        assertEquals(1, log.hits)
        assertEquals(1, log.falses)
        assertEquals(setOf(a.pattern), log.found)
        assertEquals(log, VigilLog.decode(log.encode()))
        assertNull(VigilLog.decode("x"))
    }

    @Test
    fun zeroShieldsEndsTheTramoOnly() {
        val t = Vigil.make(1, 3)
        val s = VigilShift(t)
        t.lines.indices
            .filter { t.lines[it].group < 0 }
            .take(Vigil.SHIELDS)
            .forEach { s.mark(it, t.lines.size) }
        assertTrue(s.over)
        assertEquals(0, s.shields)
        assertFalse(s.log(0).passed)
        assertEquals(VigilShift.Mark.IGNORED, s.mark(t.groups[0].detectAt, t.lines.size))
    }

    @Test
    fun passingNeedsGoalAndFewFalseAlarms() {
        assertTrue(VigilLog(2, 0, 2, 2, 4, 0, goal = 80, maxFalse = 2).passed)
        assertFalse(VigilLog(2, 0, 3, 2, 3, 0, goal = 80, maxFalse = 2).passed)
        assertFalse(VigilLog(1, 1, 0, 2, 4, 0, goal = 80, maxFalse = 2).passed)
        assertTrue(VigilLog(3, 0, 0, 3, 6, 0).perfect)
    }

    @Test
    fun tensionRampAndTuning() {
        val i = 1000L
        assertEquals(0f, Vigil.scrolled(0, i), 0f)
        assertEquals(10f * 1.015f, Vigil.scrolled(10_000, i), .01f)
        // Speed (lines/s) grows 3 % every 10 s.
        val v0 = Vigil.scrolled(1000, i) - Vigil.scrolled(0, i)
        val v10 = Vigil.scrolled(11_000, i) - Vigil.scrolled(10_000, i)
        assertEquals(1.03f, v10 / v0, .005f)
        for (n in listOf(1f, 8f, 25f)) assertEquals(n, Vigil.scrolled(Vigil.timeFor(n, i), i), .01f)
        // A full tramo lasts about 30 s at base speed.
        for (l in 1..10) {
            val t = Vigil.make(l, 1)
            val ms = Vigil.timeFor(t.lines.size.toFloat(), t.intervalMs)
            assertTrue("$l $ms", ms in 27_000L..31_000L)
        }
        val perfect = VigilLog(3, 0, 0, 3, 6, 0)
        val failed = VigilLog(0, 3, 0, 3, 0, 0)
        assertEquals(1.1f, Vigil.tuning(listOf(perfect, perfect)).speed, 0f)
        assertEquals(.9f, Vigil.tuning(listOf(failed, failed)).speed, 0f)
        assertEquals(1f, Vigil.tuning(listOf(perfect, failed)).speed, 0f)
        assertNull(Vigil.tuning(listOf(perfect)).note)
        assertEquals(listOf(100, 0), Vigil.curve(listOf(perfect, failed)))
    }

    /**
     * Paso 3 (docs/AUDITORIA-JUEGOS-V3.md): 4 chained tramos, every attack present, decoy from
     * level 2, level by attacks detected.
     */
    @Test
    fun partidaOfFourTramosWithAllItsAttacks() {
        val f = Formats.of(Vigil.ID)
        assertEquals(4, f.units)
        assertTrue(f.chained)
        assertEquals("4 tramos", f.label)
        for (l in 1..10) for (s in 0L..299L) {
            val t = Vigil.make(l, s)
            assertEquals("level $l seed $s", Vigil.level(l).targets, t.groups.size)
            assertEquals(t, Vigil.make(l, s))
            val fired = Vigil.triggers(t.lines, t.guard)
            t.lines.indices.forEach { i ->
                if (t.lines[i].group < 0) assertEquals("level $l seed $s line $i", -1, fired[i])
            }
            t.groups.forEach { g -> assertEquals(g.pattern, fired[g.detectAt]) }
        }
        // The decoy shows "admin" outside attacks from level 2, never at level 1.
        for (l in 2..10) assertTrue(
            "level $l",
            (0L..99L).count { s -> Vigil.make(l, s).lines.any { it.why == Vigil.WHY_SPREAD } } >= 1,
        )
        // From level 3 the first attack is not always the newest pattern.
        for (l in listOf(3, 5, 7, 9)) {
            val firsts = (0L..199L).map { Vigil.make(l, it).groups.first().pattern }
            assertTrue(firsts.count { it == Vigil.level(l).patterns - 1 } <= 150)
        }
        // Adaptive counts attacks detected over the partida (on 100, −5 per false alarm); old
        // 12-tramo results keep counting tramos.
        fun log(h: Int, f: Int = 0) = VigilLog(h, 3 - h, f, 3, 6, 0).encode()
        assertEquals(
            91 to 100,
            Formats.measure(Vigil.ID, 4, 4, listOf(log(3), log(3), log(3), log(2))),
        )
        assertEquals(
            81 to 100,
            Formats.measure(Vigil.ID, 4, 4, listOf(log(3, 1), log(3), log(3, 1), log(2))),
        )
        assertEquals(11 to 12, Formats.measure(Vigil.ID, 11, 12, List(12) { log(3) }))
        assertTrue(Formats.valid(Vigil.ID, 4))
        assertTrue(Formats.valid(Vigil.ID, 12))
    }
}
