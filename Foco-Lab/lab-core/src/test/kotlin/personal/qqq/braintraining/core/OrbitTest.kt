package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

class OrbitTest {
    private fun all(mode: OrbitMode = OrbitMode.CLASSIC) = sequence {
        for (level in 1..10) for (seed in 0L..99L) for (round in 0 until Orbit.ROUNDS) yield(
            Orbit.make(level, seed, round, mode)
        )
    }

    @Test
    fun threePhasesOfFourRounds() {
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2), (0 until 12).map(Orbit::stage))
        for (t in all()) {
            val specials = t.steps.count { it.kind != Orbit.SINGLE }
            when (t.stage) {
                0 -> {
                    assertEquals(0, specials)
                    assertEquals(0, t.turn)
                    assertTrue(t.steps.size in 3..8)
                }
                1 -> {
                    assertTrue(specials in 1..2)
                    assertEquals(0, t.turn)
                }
                else -> {
                    assertTrue(specials <= 1)
                    assertTrue(kotlin.math.abs(t.turn) in 1..2)
                }
            }
        }
    }

    @Test
    fun everyLightKeepsTheSameStandardPace() {
        for (mode in OrbitMode.values()) for (level in 1..10) for (round in
            0 until 24) assertEquals(Orbit.STEP_MS, Orbit.make(level, 7, round, mode).stepMs)
        assertEquals(3, Orbit.make(1, 0, 0).steps.size)
        assertEquals(5, Orbit.make(1, 0, 3).steps.size)
    }

    @Test
    fun pathsNeverRepeatANodeTwiceInARowAndAreReproducible() {
        for (t in all()) {
            assertTrue(t.steps.zipWithNext().none { (a, b) -> a.node == b.node })
            assertTrue(t.steps.all { it.node in 0 until t.nodes })
            assertEquals(t, Orbit.make(t.level, t.seed, t.round, t.mode))
        }
    }

    @Test
    fun magentaAsksForTheOppositeNodeAndMirrorReversesThePath() {
        val t = all().first { t -> t.steps.any { it.kind == Orbit.INVERSE } }
        t.steps.forEachIndexed { i, s ->
            assertEquals(if (s.kind == Orbit.INVERSE) (s.node + 4) % 8 else s.node, t.expected[i])
        }
        val m = t.copy(mode = OrbitMode.MIRROR)
        assertEquals(t.expected.reversed(), m.expected)
        assertEquals(t.doubles.reversed(), m.doubles)
        assertEquals(4, Orbit.opposite(0, 8))
        assertEquals(3, Orbit.opposite(0, 6))
        assertEquals(5, Orbit.opposite(0, 10))
    }

    @Test
    fun expandingOrbitGrowsFromSixToTenNodes() {
        assertEquals(
            listOf(6, 6, 6, 6, 8, 8, 8, 8, 10, 10, 10, 10),
            (0 until 12).map { Orbit.make(5, 3, it, OrbitMode.EXPAND).nodes },
        )
        assertTrue(all(OrbitMode.EXPAND).all { t -> t.expected.all { it in 0 until t.nodes } })
    }

    @Test
    fun decoysAppearFromRoundSevenOutsideTheRing() {
        for (t in all()) {
            assertEquals(if (t.round >= 9) 2 else if (t.round >= 6) 1 else 0, t.decoys.size)
            t.decoys.forEach { d ->
                val dx = d.x - .5f
                val dy = d.y - .5f
                assertTrue(dx * dx + dy * dy > .43f * .43f)
                assertTrue(d.atMs in 0 until t.showMs)
            }
        }
    }

    @Test
    fun singleAndDoubleTouches() {
        val t =
            OrbitTask(
                1,
                0,
                4,
                OrbitMode.CLASSIC,
                8,
                listOf(OrbitStep(1), OrbitStep(3, Orbit.DOUBLE), OrbitStep(6, Orbit.INVERSE)),
                800,
                0,
                emptyList(),
            )
        val ok = OrbitInput(t)
        assertEquals(OrbitTap.STEP, ok.tap(1, 100))
        assertEquals(OrbitTap.HALF, ok.tap(3, 500))
        assertEquals(OrbitTap.STEP, ok.tap(3, 800))
        assertEquals(OrbitTap.DONE, ok.tap(2, 1500)) // magenta 6 → opposite 2
        assertTrue(ok.finished)
        assertEquals(Orbit.OK, ok.error)

        val slow = OrbitInput(t)
        slow.tap(1, 0)
        slow.tap(3, 100)
        assertNull(slow.tick(500, 0))
        assertEquals(OrbitTap.WRONG, slow.tick(700, 0))
        assertEquals(Orbit.DOUBLE_MISSED, slow.error)
        assertEquals(3, slow.missed)

        val wrong = OrbitInput(t)
        assertEquals(OrbitTap.WRONG, wrong.tap(2, 0))
        assertEquals(Orbit.WRONG, wrong.error)
        assertEquals(1, wrong.missed)
        assertEquals(OrbitTap.IGNORED, wrong.tap(1, 10))

        val bounce = OrbitInput(t)
        bounce.tap(1, 0)
        assertEquals(OrbitTap.IGNORED, bounce.tap(1, 30))
        assertEquals(1, bounce.index)

        val late = OrbitInput(t)
        assertEquals(OrbitTap.WRONG, late.tick(t.answerMs + 1, 0))
        assertEquals(Orbit.TIME, late.error)
    }

    @Test
    fun focusBonusAddsAQuarterAndOnlyWhenRight() {
        val t = Orbit.make(3, 1, 9)
        assertEquals(150, t.points)
        assertEquals(187, Orbit.points(t, true, true))
        assertEquals(150, Orbit.points(t, true, false))
        assertEquals(0, Orbit.points(t, false, true))
    }

    @Test
    fun quadrantsFollowTheTurnedOrbit() {
        assertEquals(listOf(0, 0, 1, 1, 2, 2, 3, 3), (0 until 8).map { Orbit.quadrant(it, 8, 0) })
        assertEquals(listOf(0, 1, 1, 2, 2, 3, 3, 0), (0 until 8).map { Orbit.quadrant(it, 8, 1) })
        assertEquals(listOf(3, 0, 0, 1, 1, 2, 2, 3), (0 until 8).map { Orbit.quadrant(it, 8, -1) })
        assertEquals(
            listOf(12, 2, 3, 5, 6, 8, 9, 11),
            (0 until 8).map { Orbit.clock(Orbit.angle(it, 8, 0)) },
        )
        assertEquals(10, (0 until 10).map { Orbit.clock(Orbit.angle(it, 10, 0)) }.toSet().size)
    }

    @Test
    fun logRoundTripsAndFeedsGhostAndAttentionMap() {
        val a =
            OrbitLog(
                true,
                "mirror",
                1,
                8,
                5,
                Orbit.OK,
                true,
                156,
                listOf(0 to 640L, 2 to 910L, 2 to 1110L),
            )
        val b =
            OrbitLog(false, "mirror", 1, 8, 5, Orbit.WRONG, false, 0, listOf(1 to 700L), missQ = 3)
        assertEquals(a, OrbitLog.decode(a.encode()))
        assertEquals(b, OrbitLog.decode(b.encode()))
        assertEquals(
            OrbitLog(true, "", 0, 8, 3, 0, false, 100),
            OrbitLog.decode(OrbitLog(true, "", 0, 8, 3, 0, false, 100).encode()),
        )
        assertNull(OrbitLog.decode("ok=true"))
        assertNull(OrbitLog.decode(null))
        val details = listOf(a.encode(), b.encode(), a.encode())
        assertEquals(listOf(156, 156, 312), Orbit.cumulative(details))
        assertEquals(OrbitMode.MIRROR, Orbit.modeOf(details))
        val q = Orbit.attention(details)
        assertEquals(OrbitQuadrant(2, 640, 0), q[0])
        assertEquals(OrbitQuadrant(1, 700, 0), q[1])
        assertEquals(OrbitQuadrant(4, 1010, 0), q[2])
        assertEquals(OrbitQuadrant(0, null, 1), q[3])
    }

    @Test
    fun expandingRunsNeverMoveTheLevel() {
        val log = OrbitLog(true, "expand", 0, 6, 3, Orbit.OK, false, 100).encode()
        assertEquals(0 to 0, Formats.measure(Orbit.ID, 3, 12, List(3) { log } + List(9) { "" }))
        val classic = OrbitLog(true, "", 0, 8, 3, Orbit.OK, false, 100).encode()
        assertEquals(10 to 12, Formats.measure(Orbit.ID, 10, 12, List(12) { classic }))
        assertTrue(Formats.of(Orbit.ID).chained)
    }
}
