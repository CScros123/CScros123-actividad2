package personal.qqq.braintraining

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import personal.qqq.braintraining.data.ResultRow

/** Fase 5: resumen de solo lectura para la tarjeta "Tu práctica" de Foco. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PracticeSummaryTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 31)

    private fun row(game: String, day: LocalDate, xp: Int = 20) =
        ResultRow(
            java.util.UUID.randomUUID().toString(),
            game,
            day.atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            1,
            1,
            12,
            12,
            400,
            12000,
            xp,
            "[]",
            "{}",
        )

    @Test
    fun emptyHistoryIsEmptySummary() {
        val s = PracticeSummary.from(emptyList(), today, zone)
        assertTrue(s.isEmpty())
        assertEquals(0, s.sessionsToday)
        assertEquals(0, s.practicedDays)
        assertEquals(netsecRank(0).first, s.netsecRank)
    }

    @Test
    fun countsTodayStreakAndNetsecPoints() {
        val rows =
            listOf(
                row("subnet", today, 100),
                row("grid", today, 20),
                row("ports", today.minusDays(1), 20),
                row("logs", today.minusDays(2), 20),
                row("bits", today.minusDays(4), 20),
            )
        val s = PracticeSummary.from(rows, today, zone)
        assertEquals(2, s.sessionsToday)
        assertEquals(4, s.practicedDays)
        assertEquals(160, s.netsecPoints)
        assertEquals(netsecRank(160).first, s.netsecRank)
        assertEquals(5, s.totalSessions)
    }

    @Test
    fun restDaysNeverResetPracticedDays() {
        val s =
            PracticeSummary.from(
                listOf(row("grid", today.minusDays(1)), row("grid", today.minusDays(2))),
                today,
                zone,
            )
        assertEquals(0, s.sessionsToday)
        assertEquals(2, s.practicedDays)
    }

    @Test
    fun unknownGamesDoNotBreakTheSummary() {
        val s = PracticeSummary.from(listOf(row("not-a-game", today, 50)), today, zone)
        assertEquals(1, s.sessionsToday)
        assertEquals(0, s.netsecPoints)
    }

    @Test
    fun loadNeverCreatesTheLabDatabase() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        c.deleteDatabase(PracticeSummary.DATABASE)
        assertSame(PracticeSummary.EMPTY, PracticeSummary.load(c))
        assertFalse(c.getDatabasePath(PracticeSummary.DATABASE).exists())
    }
}
