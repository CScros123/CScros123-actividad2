package personal.qqq.braintraining

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import personal.qqq.braintraining.core.*
import personal.qqq.braintraining.data.*

/**
 * Paso 2 (docs/AUDITORIA-JUEGOS-V3.md): the per-game format in storage. A Turno anti-DDoS partida
 * is saved with its 5 waves, Adaptive counts its packets, what the result screen shows is what was
 * saved, and backups (old 12-wave results included) restore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DdosFormatTest {
    private lateinit var store: Store

    @Before
    fun setup() {
        val c = ApplicationProvider.getApplicationContext<Context>()
        c.deleteDatabase("brain-training.db")
        store = Store(c)
    }

    @After
    fun close() {
        store.close()
    }

    private fun log(correct: Int, score: Int = correct, tier: Int = 2) =
        DdosLog(correct, 20, 0, 20 - correct, tier, false, score, 0)

    /** A saved partida: [waves] logs; a wave counts as correct when it reached its goal. */
    private fun row(waves: List<DdosLog>, level: Int = 1, ended: Long = 1788821000000): ResultRow {
        val t = JSONArray()
        waves.forEachIndexed { i, l ->
            t.put(
                JSONObject()
                    .put("correct", l.passed)
                    .put("ms", JSONObject.NULL)
                    .put("active", 30000)
                    .put("ended", ended - 60000 + i * 1000)
                    .put("reason", if (l.passed) "ok" else "wrong")
                    .put("detail", l.encode())
            )
        }
        val correct = waves.count { it.passed }
        return ResultRow(
            UUID.randomUUID().toString(),
            Ddos.ID,
            ended,
            level,
            level,
            correct,
            waves.size,
            null,
            30000L * waves.size,
            0,
            t.toString(),
            "{\"2026-09-08\":${30000L*waves.size}}",
        )
    }

    @Test
    fun adaptiveCountsThePacketsOfTheFiveWaves() = runBlocking {
        // 17 of 20 in every wave: 85 of 100 packets. Two partidas in a row go up.
        val good = List(5) { log(17) }
        val a = store.finish(row(good), 0, 8)
        assertEquals(1, a.nextLevel)
        assertEquals(5, a.total)
        assertEquals(1, store.dao.getProgress(Ddos.ID)!!.up)
        val b = store.finish(row(good, ended = 1788822000000), 0, 8)
        assertEquals(2, b.nextLevel)
        assertEquals(2, store.dao.getProgress(Ddos.ID)!!.level)
        // Four perfect waves and a bad one (84 of 100) is not enough, even with 4 of 5 waves
        // passed.
        val c = store.finish(row(List(4) { log(20) } + log(4), 2, 1788823000000), 0, 8)
        assertEquals(4, c.correct)
        assertEquals(0, store.dao.getProgress(Ddos.ID)!!.up)
        // What the result screen shows is what was saved.
        val h = headline(c)
        assertEquals(84, h.value)
        assertEquals(100, h.total)
        assertEquals("paquetes", h.noun)
        assertEquals(84, h.score)
        assertEquals(85, recordFor(c, store.dao.allResults()))
    }

    @Test
    fun partidasSurviveABackupTogetherWithOldTwelveWaveResults() = runBlocking {
        // A result from before paso 2: 12 waves, measured in waves (11 of 12 twice went up).
        val old = List(12) { log(if (it == 0) 10 else 20) }
        store.finish(row(old), 0, 8)
        store.finish(row(old, ended = 1788822000000), 0, 8)
        assertEquals(2, store.dao.getProgress(Ddos.ID)!!.level)
        store.finish(row(List(5) { log(18) }, 2, 1788823000000), 0, 8)
        store.finish(row(List(5) { log(19) }, 2, 1788824000000), 0, 8)
        assertEquals(3, store.dao.getProgress(Ddos.ID)!!.level)
        val before = store.dao.allResults()
        val progress = store.dao.getProgress(Ddos.ID)
        val backup = store.export()
        store.reset()
        store.restore(backup)
        assertEquals(before, store.dao.allResults())
        assertEquals(progress, store.dao.getProgress(Ddos.ID))
        // Old results keep their own headline and are not compared with the new ones.
        val h = headline(before.last())
        assertEquals(11, h.value)
        assertEquals(12, h.total)
        assertEquals("aciertos", h.noun)
        assertEquals(95, recordFor(before[1], before))
        // Any other number of trials is still rejected, and nothing is lost.
        val bad = JSONObject(backup)
        val first = bad.getJSONArray("results").getJSONObject(0)
        val trials = first.getJSONArray("trials")
        trials.remove(0)
        first.put("total", trials.length())
        try {
            store.restore(bad.toString())
            fail()
        } catch (e: Exception) {}
        assertEquals(before, store.dao.allResults())
    }
}
