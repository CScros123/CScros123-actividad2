package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

class ShortSessionTest {
    @Test
    fun endsAfterSixChallenges() {
        var s = ShortSession()
        repeat(5) {
            s = s.answer(it % 2 == 0)
            assertFalse(s.done)
        }
        s = s.answer(true)
        assertTrue(s.done)
        assertFalse(s.timeUp)
        assertEquals(6, s.answered)
        assertEquals(4, s.correct)
        assertEquals(s, s.answer(true))
        assertEquals(s, s.tick(500))
    }

    @Test
    fun endsAfterThreeMinutesOfActiveTime() {
        var s = ShortSession().answer(true)
        repeat(179) { s = s.tick(1000) }
        assertFalse(s.done)
        assertEquals(1000L, s.remainingMs)
        s = s.tick(1000)
        assertTrue(s.done)
        assertTrue(s.timeUp)
        assertEquals(0L, s.remainingMs)
        assertEquals(1, s.answer(true).answered)
    }

    @Test
    fun bigOrNegativeJumpsDoNotEndTheSession() {
        var s = ShortSession().tick(600_000)
        assertEquals(ShortSession.MAX_TICK_MS, s.activeMs)
        s = s.tick(-5_000).tick(0)
        assertEquals(ShortSession.MAX_TICK_MS, s.activeMs)
    }

    @Test
    fun limitsMatchThePlan() {
        assertEquals(6, ShortSession.MAX_CHALLENGES)
        assertEquals(180_000L, ShortSession.MAX_ACTIVE_MS)
    }

    @Test
    fun missionPrefersNeverPlayedThenLowestThenOldest() {
        val unlocked = listOf("bits", "subnet", "ports")
        assertEquals(
            "ports",
            ShortSession.mission(
                unlocked,
                mapOf("bits" to 10, "subnet" to 7),
                mapOf("bits" to 5L, "subnet" to 9L),
            ),
        )
        assertEquals(
            "subnet",
            ShortSession.mission(
                unlocked,
                mapOf("bits" to 10, "subnet" to 7, "ports" to 9),
                emptyMap(),
            ),
        )
        assertEquals(
            "bits",
            ShortSession.mission(
                unlocked,
                mapOf("bits" to 7, "subnet" to 7, "ports" to 9),
                mapOf("bits" to 1L, "subnet" to 2L),
            ),
        )
    }

    @Test
    fun missionWithoutDataIsBits() {
        assertEquals("bits", ShortSession.mission(emptyList(), emptyMap(), emptyMap()))
        assertEquals("bits", ShortSession.mission(listOf("orbit"), emptyMap(), emptyMap()))
    }

    @Test
    fun planStartsWithMissionAndStaysInNetsec() {
        assertEquals(
            listOf("subnet", "bits", "subnet", "bits", "subnet", "bits"),
            ShortSession.plan("subnet", listOf("bits", "subnet")),
        )
        assertEquals(List(6) { "bits" }, ShortSession.plan("bits", listOf("bits")))
        assertEquals(List(6) { "bits" }, ShortSession.plan("bits", listOf("orbit")))
        for (id in ShortSession.plan("logs", NetSec.ids)) assertTrue(id in NetSec.ids)
    }

    @Test
    fun everyPlannedChallengeIsAValidChoice() {
        for (id in NetSec.ids) for (l in 1..10) {
            val c = Generator.make(id, l, 42L + l)
            assertEquals(Kind.CHOICE, c.kind)
            assertTrue(c.answer in c.choices.indices)
        }
    }
}
