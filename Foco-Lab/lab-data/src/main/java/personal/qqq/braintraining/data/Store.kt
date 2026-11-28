package personal.qqq.braintraining.data

import android.content.Context
import androidx.room.*
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import personal.qqq.braintraining.core.*

@Entity(tableName = "results")
data class ResultRow(
    @PrimaryKey val id: String,
    val game: String,
    val ended: Long,
    val level: Int,
    val nextLevel: Int,
    val correct: Int,
    val total: Int,
    val medianMs: Long?,
    val activeMs: Long,
    val xp: Int,
    val trials: String,
    val days: String,
)

@Entity(tableName = "progress")
data class ProgressRow(
    @PrimaryKey val game: String,
    val level: Int = 1,
    val up: Int = 0,
    val down: Int = 0,
    val blocks: Int = 0,
)

@Entity(tableName = "draft") data class DraftRow(@PrimaryKey val id: Int = 1, val json: String)

@Entity(tableName = "preferences")
data class PreferenceRow(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "daily_goals")
data class GoalRow(@PrimaryKey val date: String, val goalMs: Long)

@Dao
interface BrainDao {
    @Query("SELECT * FROM results ORDER BY ended DESC") fun results(): Flow<List<ResultRow>>

    @Query("SELECT * FROM progress") fun progress(): Flow<List<ProgressRow>>

    @Query("SELECT * FROM preferences") fun preferences(): Flow<List<PreferenceRow>>

    @Query("SELECT * FROM daily_goals") fun goals(): Flow<List<GoalRow>>

    @Query("SELECT * FROM results ORDER BY ended DESC") suspend fun allResults(): List<ResultRow>

    @Query("SELECT * FROM progress") suspend fun allProgress(): List<ProgressRow>

    @Query("SELECT * FROM daily_goals") suspend fun allGoals(): List<GoalRow>

    @Query("SELECT * FROM preferences") suspend fun allPreferences(): List<PreferenceRow>

    @Query("SELECT * FROM progress WHERE game=:game")
    suspend fun getProgress(game: String): ProgressRow?

    @Query("SELECT * FROM draft WHERE id=1") suspend fun draft(): DraftRow?

    @Query("SELECT * FROM results WHERE id=:id") suspend fun result(id: String): ResultRow?

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addResult(row: ResultRow): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putProgress(row: ProgressRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDraft(row: DraftRow)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPreference(row: PreferenceRow)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addGoal(row: GoalRow)

    @Query("DELETE FROM draft") suspend fun deleteDraft()

    @Query("DELETE FROM results") suspend fun deleteResults()

    @Query("DELETE FROM progress") suspend fun deleteProgress()

    @Query("DELETE FROM daily_goals") suspend fun deleteGoals()

    @Query("SELECT * FROM preferences WHERE `key`=:key")
    suspend fun getPreference(key: String): PreferenceRow?

    @Query("DELETE FROM preferences WHERE `key`='eventsStart' OR `key` LIKE 'practice:%'")
    suspend fun deleteEvents()

    @Query("DELETE FROM preferences") suspend fun deletePreferences()

    @Query("DELETE FROM preferences WHERE `key`=:key") suspend fun deletePreference(key: String)
}

@Database(
    entities =
        [
            ResultRow::class,
            ProgressRow::class,
            DraftRow::class,
            PreferenceRow::class,
            GoalRow::class,
        ],
    version = 1,
    exportSchema = true,
)
abstract class BrainDatabase : RoomDatabase() {
    abstract fun dao(): BrainDao
}

/** Preference that records the level Ruta de paquetes inherited from Órbitas (Fase 3). */
const val MIGRATED_ROUTE = "migrated:route"

class Store(context: Context) {
    private val db =
        Room.databaseBuilder(
                context.applicationContext,
                BrainDatabase::class.java,
                "brain-training.db",
            )
            .build()
    val dao = db.dao()

    fun close() = db.close()

    suspend fun finish(row: ResultRow, falseStarts: Int, goalMinutes: Int): ResultRow =
        db.withTransaction {
            dao.result(row.id)?.let {
                dao.deleteDraft()
                return@withTransaction it
            }
            val old = dao.getProgress(row.game) ?: ProgressRow(row.game)
            // Paso 2: each game says what Adaptive counts (packets for Turno anti-DDoS, units for
            // the rest).
            val (c, t) = Formats.measure(row.game, row.correct, row.total, details(row.trials))
            val p =
                Adaptive.next(
                    Progress(old.level, old.up, old.down, old.blocks),
                    row.game,
                    c,
                    t,
                    row.medianMs,
                    falseStarts,
                )
            val saved = row.copy(nextLevel = p.level, xp = Adaptive.xp(row.correct, row.total))
            check(dao.addResult(saved) != -1L)
            dao.putProgress(ProgressRow(row.game, p.level, p.up, p.down, p.blocks))
            JSONObject(row.days).keys().forEach { dao.addGoal(GoalRow(it, goalMinutes * 60000L)) }
            dao.deleteDraft()
            saved
        }

    suspend fun reset() =
        db.withTransaction {
            dao.deleteDraft()
            dao.deleteResults()
            dao.deleteProgress()
            dao.deleteGoals()
            dao.deleteEvents()
            dao.deletePreference(MIGRATED_ROUTE)
            ensureEvents()
        }

    /**
     * Fase 3 (docs/DISENO-JUEGOS-V2.md §4): Órbitas becomes Ruta de paquetes. Once, while Ruta has
     * neither progress nor results, it starts two levels below Órbitas ([Route.migratedLevel]). The
     * starting level is kept in [MIGRATED_ROUTE], so a backup restores the same difficulty history.
     */
    suspend fun migrateRoute() =
        db.withTransaction {
            if (dao.getProgress(Route.ID) != null) return@withTransaction
            val orbit = dao.getProgress(Route.OLD_ID) ?: return@withTransaction
            if (dao.allResults().any { it.game == Route.ID }) return@withTransaction
            val level = Route.migratedLevel(orbit.level)
            dao.putProgress(ProgressRow(Route.ID, level))
            dao.putPreference(PreferenceRow(MIGRATED_ROUTE, level.toString()))
        }

    suspend fun ensureEvents() {
        if (dao.getPreference("eventsStart") == null) {
            val zone =
                runCatching {
                        java.time.ZoneId.of(
                            dao.getPreference("zone")?.value ?: java.time.ZoneId.systemDefault().id
                        )
                    }
                    .getOrDefault(java.time.ZoneId.systemDefault())
            dao.putPreference(PreferenceRow("eventsStart", LocalDate.now(zone).toString()))
        }
    }

    suspend fun applyPractice(date: LocalDate, note: String): Boolean =
        db.withTransaction {
            val text = note.trim()
            require(text.length in 10..500)
            val zone =
                java.time.ZoneId.of(
                    dao.getPreference("zone")?.value ?: java.time.ZoneId.systemDefault().id
                )
            require(date == LocalDate.now(zone)) { "El día cambió. Abre el reto de hoy." }
            val key = "practice:$date"
            if (dao.getPreference(key) != null) return@withTransaction false
            ensureEvents()
            dao.putPreference(
                PreferenceRow(
                    key,
                    JSONObject()
                        .put("note", text)
                        .put("ended", System.currentTimeMillis())
                        .toString(),
                )
            )
            true
        }

    suspend fun export(): String =
        db.withTransaction {
            JSONObject()
                .put("formatVersion", 1)
                .put("project", "Brain Training Personal Edition")
                .put("author", "qqq")
                .put("version", "1.5.0")
                .put("results", JSONArray().apply { dao.allResults().forEach { put(it.json()) } })
                .put(
                    "goals",
                    JSONArray().apply {
                        dao.allGoals().forEach {
                            put(JSONObject().put("date", it.date).put("goalMs", it.goalMs))
                        }
                    },
                )
                .put(
                    "preferences",
                    JSONArray().apply {
                        dao.allPreferences().forEach {
                            put(JSONObject().put("key", it.key).put("value", it.value))
                        }
                    },
                )
                .toString(2)
        }

    suspend fun restore(text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= 25 * 1024 * 1024) {
            "La copia supera los 25 MB."
        }
        val root = JSONObject(text)
        require(root.getInt("formatVersion") == 1) { "Versión de copia no compatible." }
        require(root.getString("project") == "Brain Training Personal Edition") {
            "La copia pertenece a otra aplicación."
        }
        val array = root.getJSONArray("results")
        require(array.length() <= 50000)
        val results = (0 until array.length()).map { decodeResult(array.getJSONObject(it)) }
        require(results.map { it.id }.distinct().size == results.size) {
            "Hay registros duplicados."
        }
        val goalsJson = root.getJSONArray("goals")
        val goals =
            (0 until goalsJson.length()).map {
                val o = goalsJson.getJSONObject(it)
                val d = o.getString("date")
                LocalDate.parse(d)
                val g = o.getLong("goalMs")
                require(g in 180000..1200000)
                GoalRow(d, g)
            }
        require(goals.map { it.date }.distinct().size == goals.size)
        val prefsJson = root.getJSONArray("preferences")
        val prefs =
            (0 until prefsJson.length()).map {
                val o = prefsJson.getJSONObject(it)
                PreferenceRow(o.getString("key"), o.getString("value"))
            }
        require(prefs.map { it.key }.distinct().size == prefs.size)
        require(
            prefs.all {
                when (it.key) {
                    "dark",
                    "learning",
                    "sound",
                    "haptics",
                    "motion",
                    "amoled",
                    "dynamic",
                    "classic" -> it.value in listOf("true", "false")
                    "goal" -> it.value.toIntOrNull() in 3..20
                    "meteor_best" -> (it.value.toIntOrNull() ?: -1) >= 0
                    MIGRATED_ROUTE -> it.value.toIntOrNull() in 1..10
                    "zone" -> runCatching { java.time.ZoneId.of(it.value) }.isSuccess
                    "eventsStart" ->
                        runCatching { LocalDate.parse(it.value).toString() == it.value }
                            .getOrDefault(false)
                    "rewards" -> runCatching { Wallet.decode(it.value) }.isSuccess
                    "short_session:last" -> runCatching { JSONObject(it.value) }.isSuccess
                    else ->
                        if (it.key.startsWith("arcade:"))
                            runCatching {
                                    val a = JSONArray(it.value)
                                    a.length() <= 200 &&
                                        (0 until a.length()).all { i ->
                                            a.getJSONObject(i).getInt("score") >= 0
                                        }
                                }
                                .getOrDefault(false)
                        else if (it.key.startsWith("missions:"))
                            runCatching {
                                    LocalDate.parse(it.key.removePrefix("missions:"))
                                    MissionProgress.decode(it.value)
                                }
                                .isSuccess
                        else if (it.key.startsWith("flash_best:"))
                            it.key.removePrefix("flash_best:") in NetSec.ids &&
                                (it.value.toIntOrNull() ?: -1) >= 0
                        else if (it.key.startsWith("practice:"))
                            runCatching {
                                    val d = LocalDate.parse(it.key.removePrefix("practice:"))
                                    val obj = JSONObject(it.value)
                                    val note = obj.getString("note")
                                    val ended = obj.getLong("ended")
                                    val zone =
                                        java.time.ZoneId.of(
                                            prefs.firstOrNull { p -> p.key == "zone" }?.value
                                                ?: java.time.ZoneId.systemDefault().id
                                        )
                                    note.trim().length in 10..500 &&
                                        ended > 0 &&
                                        java.time.Instant.ofEpochMilli(ended)
                                            .atZone(zone)
                                            .toLocalDate() == d
                                }
                                .getOrDefault(false)
                        else false
                }
            }
        )
        val rebuilt = mutableMapOf<String, ProgressRow>()
        val migrated = prefs.firstOrNull { it.key == MIGRATED_ROUTE }?.value?.toInt()
        results
            .sortedBy { it.ended }
            .forEach { row ->
                // Ruta de paquetes may start at the level migrated from Órbitas.
                val old =
                    rebuilt[row.game]
                        ?: if (row.game == Route.ID && migrated != null && row.level == migrated)
                            ProgressRow(row.game, migrated)
                        else ProgressRow(row.game)
                require(row.level == old.level) { "El historial de dificultad no es coherente." }
                val t = JSONArray(row.trials)
                val early =
                    (0 until t.length()).count {
                        t.getJSONObject(it).optString("reason") == "early"
                    }
                val (c, n) = Formats.measure(row.game, row.correct, row.total, details(row.trials))
                val p =
                    Adaptive.next(
                        Progress(old.level, old.up, old.down, old.blocks),
                        row.game,
                        c,
                        n,
                        row.medianMs,
                        early,
                    )
                require(row.nextLevel == p.level)
                rebuilt[row.game] = ProgressRow(row.game, p.level, p.up, p.down, p.blocks)
            }
        db.withTransaction {
            dao.deleteDraft()
            dao.deleteResults()
            dao.deleteProgress()
            dao.deleteGoals()
            dao.deletePreferences()
            results.forEach { dao.addResult(it) }
            rebuilt.values.forEach { dao.putProgress(it) }
            goals.forEach { dao.addGoal(it) }
            prefs.forEach { dao.putPreference(it) }
            ensureEvents()
        }
    }
}

/** Board logs of a saved result, one per unit (empty strings for games without a board). */
fun details(trials: String): List<String> =
    runCatching {
            val a = JSONArray(trials)
            (0 until a.length()).map { a.getJSONObject(it).optString("detail", "") }
        }
        .getOrDefault(emptyList())

fun ResultRow.json() =
    JSONObject()
        .put("id", id)
        .put("game", game)
        .put("ended", ended)
        .put("level", level)
        .put("nextLevel", nextLevel)
        .put("correct", correct)
        .put("total", total)
        .put("medianMs", medianMs ?: JSONObject.NULL)
        .put("activeMs", activeMs)
        .put("xp", xp)
        .put("trials", JSONArray(trials))
        .put("days", JSONObject(days))

fun decodeResult(o: JSONObject): ResultRow {
    val id = o.getString("id")
    java.util.UUID.fromString(id)
    val game = o.getString("game")
    require(Games.all.any { it.id == game })
    val level = o.getInt("level")
    val next = o.getInt("nextLevel")
    require(level in 1..10 && next in 1..10)
    val t = o.getJSONArray("trials")
    require(Formats.valid(game, t.length()))
    var correct = 0
    var active = 0L
    val times = mutableListOf<Long>()
    for (i in 0 until t.length()) {
        val trial = t.getJSONObject(i)
        if (trial.getBoolean("correct")) {
            correct++
            if (!trial.isNull("ms")) times += trial.getLong("ms")
        }
        val a = trial.getLong("active")
        require(a in 0..MAX_ROUND_ACTIVE_MS)
        active += a
        val mode = trial.optString("mode", "unknown")
        require(mode in listOf("unknown", "timed", "untimed"))
        if (!trial.isNull("responseMs"))
            require(trial.getLong("responseMs") in 0..MAX_ROUND_ACTIVE_MS)
        require(
            trial.optString("reason") in listOf("ok", "wrong", "timeout", "early", "implausible")
        )
        if (!trial.isNull("ms")) require(trial.getLong("ms") in 0..120000)
    }
    val med = if (times.size >= 8) median(times) else null
    require(
        correct == o.getInt("correct") &&
            t.length() == o.getInt("total") &&
            active == o.getLong("activeMs")
    )
    require(o.getInt("xp") == Adaptive.xp(correct, t.length()))
    require((if (o.isNull("medianMs")) null else o.getLong("medianMs")) == med)
    val days = o.getJSONObject("days")
    var sum = 0L
    days.keys().forEach {
        LocalDate.parse(it)
        val a = days.getLong(it)
        require(a in 0..(12 * MAX_ROUND_ACTIVE_MS))
        sum += a
    }
    require(sum == active)
    val ended = o.getLong("ended")
    require(ended > 0)
    return ResultRow(
        id,
        game,
        ended,
        level,
        next,
        correct,
        t.length(),
        med,
        active,
        Adaptive.xp(correct, t.length()),
        t.toString(),
        days.toString(),
    )
}
