package personal.qqq.braintraining.core

import org.junit.Assert.*
import org.junit.Test

class EvaluationTest {
    @Test
    fun separatesMistakesFromTimingFailures() {
        val errors =
            Evaluation.errors(
                listOf(
                    TrialEvidence(false, "wrong"),
                    TrialEvidence(false, "timeout"),
                    TrialEvidence(false, "early"),
                    TrialEvidence(false, "implausible"),
                    TrialEvidence(true, "ok"),
                )
            )
        assertEquals(ErrorProfile(1, 1, 1, 1), errors)
    }

    @Test
    fun excludesOtherLevelsAndModesAndSortsHistory() {
        val comparable =
            (1L..6L).map { PracticeSample(it, 2, "untimed", if (it <= 3) 6 else 9, 12) }
        val mixed =
            comparable +
                PracticeSample(4, 3, "untimed", 12, 12) +
                PracticeSample(5, 2, "timed", 0, 12)
        val result = Evaluation.compare(mixed.reversed())!!
        assertEquals(50.0, result.beforePercent, 0.001)
        assertEquals(75.0, result.afterPercent, 0.001)
        assertEquals(25.0, result.changePoints, 0.001)
    }

    @Test
    fun insufficientAndLegacyEvidenceDoesNotProduceComparison() {
        assertNull(Evaluation.compare((1L..5L).map { PracticeSample(it, 1, "timed", 12, 12) }))
        assertNull(Evaluation.compare((1L..6L).map { PracticeSample(it, 1, "unknown", 12, 12) }))
    }

    @Test
    fun latestModeDeterminesComparisonRatherThanAnOlderAvailableGroup() {
        val history =
            (1L..6L).map { PracticeSample(it, 1, "timed", 12, 12) } +
                PracticeSample(7, 1, "untimed", 12, 12)
        assertNull(Evaluation.compare(history))
    }

    @Test
    fun longPracticeIsNotTruncatedAtTwoMinutes() {
        assertEquals(300000L, 300000L.coerceIn(0, MAX_ROUND_ACTIVE_MS))
    }

    @Test
    fun falseStartsTakePriorityInAdvice() {
        assertTrue(
            Evaluation.advice("reaction", ErrorProfile(1, 0, 1, 0)).startsWith("Espera la señal")
        )
    }
}
