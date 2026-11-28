package personal.qqq.braintraining

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import personal.qqq.braintraining.core.NetSec
import personal.qqq.braintraining.data.ProgressRow
import personal.qqq.braintraining.data.ResultRow

/** Fase 2: plan de la práctica corta y el intent con el que Foco la abre. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShortPracticeTest {
    private fun row(game: String, correct: Int, ended: Long) =
        ResultRow("$game-$ended", game, ended, 1, 1, correct, 12, null, 1000L, 0, "[]", "{}")

    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun planStartsWithMissionAtSavedLevel() {
        // Newest first, as the DAO returns them. Bits 10/12 unlocks Subnet; Subnet 9/12 unlocks
        // Ports (never played).
        val plan =
            shortPlan(
                listOf(row("subnet", 9, 20L), row("bits", 10, 10L)),
                listOf(ProgressRow("ports", 4), ProgressRow("bits", 3)),
            )
        assertEquals(6, plan.size)
        assertEquals(ShortRound("ports", 4), plan.first())
        assertEquals(
            listOf("ports", "bits", "subnet", "ports", "bits", "subnet"),
            plan.map { it.game },
        )
        assertEquals(ShortRound("bits", 3), plan[1])
        assertEquals(ShortRound("subnet", 1), plan[2])
        assertTrue(plan.all { it.game in NetSec.ids })
    }

    @Test
    fun newUserPracticesBitsAtLevelOne() {
        assertEquals(List(6) { ShortRound("bits", 1) }, shortPlan(emptyList(), emptyList()))
    }

    @Test
    fun savedLevelIsClampedToGeneratorRange() {
        assertEquals(10, shortPlan(emptyList(), listOf(ProgressRow("bits", 42))).first().level)
    }

    @Test
    fun fromPauseOpensOwnHiddenTask() {
        val i = LabLaunch.shortSession(context, true)
        assertTrue(LabLaunch.isShort(i))
        assertTrue(LabLaunch.fromPause(i))
        assertEquals(LabActivity::class.java.name, i.component?.className)
        assertTrue((i.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((i.flags and Intent.FLAG_ACTIVITY_MULTIPLE_TASK) != 0)
    }

    @Test
    fun fromTodayStaysInFocoTask() {
        val i = LabLaunch.shortSession(context, false)
        assertTrue(LabLaunch.isShort(i))
        assertFalse(LabLaunch.fromPause(i))
        assertEquals(0, i.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun normalLaunchIsNotShort() {
        assertFalse(LabLaunch.isShort(Intent()))
        assertFalse(LabLaunch.isShort(null))
        assertFalse(LabLaunch.fromPause(null))
    }
}
