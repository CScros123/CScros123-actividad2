package personal.qqq.braintraining.core

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ConsistencyTest {
    private val today = LocalDate.of(2026, 3, 30)

    private fun ago(vararg n: Long) = n.map { today.minusDays(it) }

    @Test
    fun restDayNeverErasesProgress() {
        // 29 días seguidos y descanso hoy y ayer: los 29 días siguen contando.
        val s = Consistency.summarize((2L..30L).map { today.minusDays(it) }, today)
        assertEquals(29, s.practicedDays)
    }

    @Test
    fun emptyHistoryIsNeutral() {
        val s = Consistency.summarize(emptyList(), today)
        assertEquals(0, s.practicedDays)
        assertNull(s.baselineDays)
        assertTrue(s.steady)
    }

    @Test
    fun firstWeekHasNoBaselineAndIsPositive() {
        val s = Consistency.summarize(ago(0, 2), today)
        assertEquals(2, s.recentDays)
        assertNull(s.baselineDays)
        assertTrue(s.steady)
    }

    @Test
    fun comparesAgainstOwnBaseline() {
        // 4 semanas previas con 3 días cada una; esta semana 2 días: dentro del margen.
        val before = (0 until 4).flatMap { w -> listOf(7L, 9L, 11L).map { it + w * 7 } }
        val s = Consistency.summarize(ago(*before.toLongArray()) + ago(0, 3), today)
        assertEquals(3.0, s.baselineDays!!, 1e-9)
        assertEquals(2, s.recentDays)
        assertTrue(s.steady)
        assertEquals("Mantienes tu ritmo habitual.", s.message())
    }

    @Test
    fun quietWeekIsDescribedKindly() {
        val before = (0 until 4).flatMap { w -> (7L..12L).map { it + w * 7 } }
        val s = Consistency.summarize(ago(*before.toLongArray()), today)
        assertFalse(s.steady)
        assertFalse(s.message().contains("fall", ignoreCase = true))
    }

    @Test
    fun shortHistoryAveragesOnlyExistingWeeks() {
        // Solo una semana previa con 4 días: la media es 4, no 1.
        val s = Consistency.summarize(ago(7, 8, 9, 10), today)
        assertEquals(4.0, s.baselineDays!!, 1e-9)
    }
}
