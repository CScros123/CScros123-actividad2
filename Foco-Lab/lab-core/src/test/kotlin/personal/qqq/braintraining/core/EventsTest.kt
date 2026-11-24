package personal.qqq.braintraining.core

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class EventsTest {
    private val zone = ZoneId.of("America/Panama")
    private val mon = LocalDate.of(2026, 10, 5)

    private fun session(id: String, day: LocalDate, game: String = "math", hour: Int = 12) =
        EventSession(id, game, day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli())

    private fun note(day: LocalDate) =
        AppliedPractice(
            day,
            "Comparé el precio por unidad de dos compras.",
            day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli(),
        )

    @Test
    fun periodsAreStableAndRotateWithoutErasingRewards() {
        val first = EventEngine.build(mon, mon, emptyList(), emptyList(), zone)
        assertEquals(first, EventEngine.build(mon, mon, emptyList(), emptyList(), zone))
        assertEquals(3, first.daily.size)
        assertEquals(3, first.weekly.size)
        val game = first.daily.first().game!!
        val played = listOf(session("a", mon, game))
        val done = EventEngine.build(mon, mon, played, emptyList(), zone)
        assertEquals(25, done.bonusXp)
        val tomorrow = EventEngine.build(mon.plusDays(1), mon, played, emptyList(), zone)
        assertEquals(25, tomorrow.bonusXp)
        assertFalse(tomorrow.daily.any { it.complete })
        assertEquals(done.journal, tomorrow.journal)
    }

    @Test
    fun repeatedEvidenceCannotDuplicateXp() {
        val first = EventEngine.build(mon, mon, emptyList(), emptyList(), zone)
        val row = session("same", mon, first.daily.first().game!!)
        val n = note(mon)
        val a = EventEngine.build(mon, mon, listOf(row), listOf(n), zone)
        val b = EventEngine.build(mon, mon, listOf(row, row, row), listOf(n, n), zone)
        assertEquals(a, b)
        assertEquals(a.journal.size, a.journal.map { it.id }.distinct().size)
    }

    @Test
    fun weeklyGoalsUseDistinctDaysAndCompletedSessions() {
        val rows =
            listOf(
                session("1", mon),
                session("2", mon),
                session("3", mon.plusDays(1)),
                session("4", mon.plusDays(2)),
            )
        val notes = listOf(note(mon), note(mon.plusDays(2)))
        val result = EventEngine.build(mon.plusDays(2), mon, rows, notes, zone)
        assertTrue(result.weekly.all { it.complete })
        assertEquals(3, result.badges.size)
        val nextWeek = EventEngine.build(mon.plusWeeks(1), mon, rows, notes, zone)
        assertTrue(nextWeek.weekly.none { it.complete })
        assertEquals(result.bonusXp, nextWeek.bonusXp)
        assertEquals(result.journal, nextWeek.journal)
    }

    @Test
    fun realApplicationIsNeededForItsReward() {
        val result =
            EventEngine.build(mon, mon, List(10) { session("$it", mon) }, emptyList(), zone)
        assertFalse(result.daily.last().complete)
        assertFalse(result.weekly.last().complete)
    }

    @Test
    fun timezoneAndFirstShortWeekAreCorrect() {
        val sunday = mon.minusDays(1)
        val first = EventEngine.build(sunday, sunday, emptyList(), listOf(note(sunday)), zone)
        assertEquals(1, first.weekly[1].target)
        assertTrue(first.weekly[1].complete)
        assertTrue(first.weekly[2].complete)
        val utcMidnight =
            EventSession("night", "math", Instant.parse("2026-10-05T02:00:00Z").toEpochMilli())
        val result = EventEngine.build(mon, sunday, listOf(utcMidnight), emptyList(), zone)
        assertFalse(result.daily.any { it.complete })
        assertEquals(EventEngine.monday(sunday), result.weekly.first().start.minusWeeks(1))
        assertEquals(mon, result.weekly.first().start)
    }
}
