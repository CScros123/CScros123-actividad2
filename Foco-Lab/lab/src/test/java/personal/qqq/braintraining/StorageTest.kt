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
import personal.qqq.braintraining.data.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class StorageTest {
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

    private fun row(): ResultRow {
        val t = JSONArray()
        repeat(12) {
            t.put(
                JSONObject()
                    .put("correct", true)
                    .put("ms", 400)
                    .put("active", 1000)
                    .put("ended", 1788820000000L + it * 1000)
                    .put("reason", "ok")
            )
        }
        return ResultRow(
            UUID.randomUUID().toString(),
            "grid",
            1788821000000,
            1,
            1,
            12,
            12,
            400,
            12000,
            0,
            t.toString(),
            "{\"2026-09-08\":12000}",
        )
    }

    @Test
    fun idempotent() = runBlocking {
        val r = row()
        store.dao.putDraft(DraftRow(json = "{}"))
        val a = store.finish(r, 0, 8)
        val b = store.finish(r, 0, 8)
        assertEquals(a, b)
        assertEquals(20, a.xp)
        assertEquals(1, store.dao.allResults().size)
        assertEquals(1, store.dao.getProgress("grid")!!.blocks)
        assertNull(store.dao.draft())
    }

    @Test
    fun restoreRoundTrip() = runBlocking {
        store.finish(row(), 0, 8)
        store.dao.putPreference(PreferenceRow("goal", "8"))
        val before = store.dao.allResults()
        val backup = store.export()
        store.reset()
        assertTrue(store.dao.allResults().isEmpty())
        store.restore(backup)
        assertEquals(before, store.dao.allResults())
        assertEquals(1, store.dao.getProgress("grid")!!.blocks)
    }

    /**
     * Fase 3: Órbitas progress becomes Ruta de paquetes progress (level − 2, at least 1), once, and
     * a backup keeps it coherent.
     */
    @Test
    fun orbitLevelMigratesToRouteAndSurvivesBackup() = runBlocking {
        store.migrateRoute()
        assertNull(store.dao.getProgress("route"))
        store.dao.putProgress(ProgressRow("orbit", 6))
        store.migrateRoute()
        assertEquals(4, store.dao.getProgress("route")!!.level)
        assertEquals("4", store.dao.getPreference(MIGRATED_ROUTE)!!.value)
        store.dao.putProgress(ProgressRow("orbit", 9))
        store.migrateRoute()
        assertEquals(4, store.dao.getProgress("route")!!.level)
        val r = store.finish(row().copy(game = "route", level = 4, nextLevel = 4), 0, 8)
        assertEquals(4, r.level)
        val backup = store.export()
        store.restore(backup)
        assertEquals(listOf(r), store.dao.allResults())
        assertEquals(r.nextLevel, store.dao.getProgress("route")!!.level)
        store.reset()
        assertNull(store.dao.getPreference(MIGRATED_ROUTE))
        store.dao.putProgress(ProgressRow("orbit", 2))
        store.migrateRoute()
        assertEquals(1, store.dao.getProgress("route")!!.level)
    }

    @Test
    fun newSkillsAndLearningSettingRoundTrip() = runBlocking {
        val ids =
            listOf(
                "orbit",
                "compass",
                "balance",
                "percent",
                "fraction",
                "equation",
                "ratio",
                "budget",
                "unitprice",
                "planning",
            )
        ids.forEach { store.finish(row().copy(game = it), 0, 8) }
        store.dao.putPreference(PreferenceRow("learning", "true"))
        val before = store.dao.allResults()
        val backup = store.export()
        store.reset()
        store.restore(backup)
        assertEquals(before, store.dao.allResults())
        assertTrue(store.dao.allPreferences().contains(PreferenceRow("learning", "true")))
    }

    @Test
    fun realApplicationsAreIdempotentAndSurviveBackup() = runBlocking {
        store.dao.putPreference(PreferenceRow("zone", "America/Panama"))
        val day = java.time.LocalDate.now(java.time.ZoneId.of("America/Panama"))
        assertTrue(store.applyPractice(day, "Comparé dos precios por unidad antes de comprar."))
        assertFalse(store.applyPractice(day, "Otro intento no debe duplicar la recompensa."))
        val saved = store.dao.getPreference("practice:$day")!!
        val backup = store.export()
        store.reset()
        assertNull(store.dao.getPreference("practice:$day"))
        store.restore(backup)
        assertEquals(saved, store.dao.getPreference("practice:$day"))
    }

    @Test
    fun malformedApplicationBackupPreservesData() = runBlocking {
        store.finish(row(), 0, 8)
        val before = store.dao.allResults()
        val backup = JSONObject(store.export())
        backup
            .getJSONArray("preferences")
            .put(JSONObject().put("key", "practice:2026-10-04").put("value", "{}"))
        try {
            store.restore(backup.toString())
            fail("Invalid application accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, store.dao.allResults())
    }

    @Test
    fun invalidBackupPreservesHistory() = runBlocking {
        store.finish(row(), 0, 8)
        val before = store.dao.allResults()
        val bad = JSONObject(store.export()).put("formatVersion", 999).toString()
        try {
            store.restore(bad)
            fail("Expected rejection")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, store.dao.allResults())
    }

    @Test
    fun longUntimedSessionSurvivesBackup() = runBlocking {
        val base = row()
        val trials = JSONArray(base.trials)
        repeat(12) {
            trials
                .getJSONObject(it)
                .put("active", 300000L)
                .put("ms", JSONObject.NULL)
                .put("responseMs", 300000L)
                .put("mode", "untimed")
        }
        val long =
            base.copy(
                game = "math",
                medianMs = null,
                activeMs = 3600000L,
                trials = trials.toString(),
                days = "{\"2026-09-08\":3600000}",
            )
        store.finish(long, 0, 8)
        val before = store.dao.allResults()
        val backup = store.export()
        store.reset()
        store.restore(backup)
        assertEquals(before, store.dao.allResults())
        assertEquals(3600000L, store.dao.allResults().single().activeMs)
    }

    @Test
    fun invalidNewEvidencePreservesHistory() = runBlocking {
        store.finish(row(), 0, 8)
        val before = store.dao.allResults()
        val backup = JSONObject(store.export())
        backup
            .getJSONArray("results")
            .getJSONObject(0)
            .getJSONArray("trials")
            .getJSONObject(0)
            .put("responseMs", -1)
        try {
            store.restore(backup.toString())
            fail("Negative response time accepted")
        } catch (_: IllegalArgumentException) {}
        assertEquals(before, store.dao.allResults())
    }

    @Test
    fun experienceSettingsRoundTripAndRejectInvalidValuesAtomically() = runBlocking {
        store.finish(row(), 0, 8)
        val settings = listOf("sound", "haptics", "motion").map { PreferenceRow(it, "false") }
        settings.forEach { store.dao.putPreference(it) }
        val backup = store.export()
        val before = store.dao.allResults()
        store.restore(backup)
        settings.forEach { assertEquals(it, store.dao.getPreference(it.key)) }
        for (key in listOf("sound", "haptics", "motion")) {
            val bad = JSONObject(backup)
            val prefs = bad.getJSONArray("preferences")
            for (i in 0 until prefs.length()) if (prefs.getJSONObject(i).getString("key") == key)
                prefs.getJSONObject(i).put("value", "invalid")
            try {
                store.restore(bad.toString())
                fail("Invalid $key accepted")
            } catch (_: IllegalArgumentException) {}
            assertEquals(before, store.dao.allResults())
            settings.forEach { assertEquals(it, store.dao.getPreference(it.key)) }
        }
    }
}
