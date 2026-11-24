package personal.qqq.braintraining

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.LocalDate
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
 * Lote C: the album, chests and missions survive backup/restore, and Lab never touches Foco's
 * rules.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class EngagementStorageTest {
    private lateinit var store: Store
    private val context
        get() = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        context.deleteDatabase("brain-training.db")
        store = Store(context)
    }

    @After
    fun close() {
        store.close()
    }

    @Test
    fun albumChestsAndMissionsSurviveBackup() = runBlocking {
        val day = LocalDate.of(2026, 11, 8)
        val wallet =
            Wallet()
                .earn(day)!!
                .earn(day)!!
                .open()!!
                .first
                .unlock("ports:22")
                .unlock(Album.cards.last().key)
        val missions = MissionProgress(mapOf("streak:subnet" to 5, "short" to 2), rewarded = true)
        val prefs =
            listOf(
                PreferenceRow("rewards", wallet.encode()),
                PreferenceRow("missions:$day", missions.encode()),
                PreferenceRow("classic", "true"),
                PreferenceRow(
                    "arcade:pulse",
                    JSONArray()
                        .put(JSONObject().put("score", 12).put("ended", 1L).put("activeMs", 60000))
                        .toString(),
                ),
                PreferenceRow(
                    "arcade:flash:subnet",
                    JSONArray()
                        .put(JSONObject().put("score", 90).put("curve", JSONArray(listOf(10, 20))))
                        .toString(),
                ),
                PreferenceRow("short_session:last", JSONObject().put("answered", 6).toString()),
            )
        prefs.forEach { store.dao.putPreference(it) }
        val backup = store.export()
        store.reset()
        prefs.forEach { store.dao.putPreference(PreferenceRow(it.key, "x")) }
        store.restore(backup)
        prefs.forEach { assertEquals(it, store.dao.getPreference(it.key)) }
        assertEquals(wallet, Wallet.decode(store.dao.getPreference("rewards")!!.value))
        assertEquals(
            missions,
            MissionProgress.decode(store.dao.getPreference("missions:$day")!!.value),
        )
    }

    @Test
    fun corruptRewardsAreRejectedOnRestore() = runBlocking {
        store.dao.putPreference(PreferenceRow("rewards", "v=1\nminutes=30"))
        val backup = store.export()
        assertTrue(runCatching { store.restore(backup) }.isFailure)
    }

    @Test
    fun chestSummaryIsACheapReadOnlyMirror() {
        assertFalse(ChestSummary.available(context))
        assertEquals(0, ChestSummary.pending(context))
        ChestSummary.write(context, 2)
        assertTrue(ChestSummary.available(context))
        assertEquals(2, ChestSummary.pending(context))
        ChestSummary.write(context, 0)
        assertFalse(ChestSummary.available(context))
    }

    @Test
    fun chestIntentOpensLabOnTheChest() {
        val i = LabLaunch.chest(context, true)
        assertTrue(LabLaunch.isChest(i))
        assertTrue(LabLaunch.fromPause(i))
        assertFalse(LabLaunch.isShort(i))
        assertTrue(i.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(LabLaunch.isChest(LabLaunch.full(context)))
    }

    /**
     * Acceptance: rewards never touch Foco's budget or rules. No Lab source refers to Foco's rule
     * store or repository.
     */
    @Test
    fun labNeverTouchesFocoRules() {
        val sources =
            listOf(File("src/main"), File("../lab-core/src/main"), File("../lab-data/src/main"))
                .flatMap { d ->
                    d.walkTopDown()
                        .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
                        .toList()
                }
        assertTrue(sources.size > 10)
        val forbidden =
            Regex(
                "import\\s+es\\.foco|es\\.foco\\.next\\.(data|core)\\.|RuleStore\\.|FocusEngine\\.|Repository\\.get\\("
            )
        sources.forEach { f -> assertFalse(f.path, forbidden.containsMatchIn(f.readText())) }
    }
}
