package personal.qqq.braintraining.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WellbeingTest {
    private val zone = ZoneId.of("Europe/Madrid")

    @Test
    fun lowEnergyKeepsOnlyOneShortChallenge() {
        assertEquals(listOf("a"), Energy.LOW.trim(listOf("a", "b", "c")))
        assertFalse(Energy.LOW.timed)
        assertEquals(3, Energy.NORMAL.trim(listOf("a", "b", "c")).size)
        assertEquals(emptyList<String>(), Energy.LOW.trim(emptyList<String>()))
    }

    @Test
    fun dayClosesAtThreeNotAtMidnight() {
        fun ms(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        val d = LocalDate.of(2026, 5, 10)
        assertEquals(d, LabDay.of(ms(d.plusDays(1).atTime(2, 59)), zone))
        assertEquals(d.plusDays(1), LabDay.of(ms(d.plusDays(1).atTime(3, 0)), zone))
        assertEquals(d, LabDay.of(ms(d.atTime(23, 59)), zone))
    }

    @Test
    fun paceRisesWithHitsAndEasesAfterTwoMisses() {
        var p = SoftPace()
        repeat(8) { p = p.hit() }
        assertEquals(2, p.level)
        p = p.miss()
        assertEquals(2, p.level)
        p = p.miss()
        assertEquals(1, p.level)
        assertTrue(p.ms > SoftPace(level = 2).ms)
    }

    @Test
    fun paceNeverGoesBelowStartOrAboveCeiling() {
        var p = SoftPace()
        repeat(10) { p = p.miss() }
        assertEquals(0, p.level)
        repeat(200) { p = p.hit() }
        assertEquals(SoftPace.MAX_LEVEL, p.level)
        assertTrue(p.ms >= 450)
    }

    @Test
    fun aHitBreaksAMissRun() {
        val p = SoftPace(level = 3).miss().hit().miss()
        assertEquals(3, p.level)
    }
}
