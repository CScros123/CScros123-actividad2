package es.foco.next.log;

import static org.junit.Assert.*;

import android.content.Context;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

/** Fase 4: foco-log.db (separate from focus-lite-v1.json), pruning and upserts. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LogDatabaseTest {
    private Context c;
    private LogDatabase db;

    @Before
    public void init() {
        c = RuntimeEnvironment.getApplication();
        c.deleteDatabase(LogDatabase.NAME);
        db = LogDatabase.get(c);
    }

    @After
    public void close() {
        db.close();
    }

    private static BlockEvent at(String iso, BlockEvent.Type t, String pkg) {
        return new BlockEvent(t, Instant.parse(iso).toEpochMilli(), ZoneId.of("UTC")).pkg(pkg);
    }

    @Test
    public void separateFileAndEventsRoundTrip() {
        db.insert(
                at("2026-10-31T10:00:00Z", BlockEvent.Type.BUDGET_LOCK, "feed.app")
                        .budgetUsed(300_000)
                        .windowLeft(1_000)
                        .lock(1_000)
                        .action("x"));
        db.insert(at("2026-10-31T10:05:00Z", BlockEvent.Type.ATTEMPT_WHILE_LOCKED, "feed.app"));
        assertTrue(c.getDatabasePath(LogDatabase.NAME).exists());
        assertFalse(
                "never mixed with the plan file",
                c.getFileStreamPath("focus-lite-v1.json").exists());
        List<BlockEvent> events = db.events("2026-10-31");
        assertEquals(2, events.size());
        BlockEvent e = events.get(0);
        assertEquals(BlockEvent.Type.BUDGET_LOCK, e.type);
        assertEquals("feed.app", e.pkg);
        assertEquals("2026-10-31", e.day);
        assertEquals("UTC", e.zone);
        assertEquals(300_000, e.budgetUsedMs);
        assertEquals(1_000, e.windowLeftMs);
        assertEquals(1_000, e.lockMs);
        assertEquals("x", e.action);
        assertEquals(-1, events.get(1).budgetUsedMs);
        List<LogSummary.Day> days =
                LogSummary.build(db.counts("2026-10-01"), db.usage("2026-10-01"));
        assertEquals(1, days.get(0).blocks);
        assertEquals(1, days.get(0).attempts);
    }

    @Test
    public void pruneRemovesOnlyDaysBeforeTheCutoff() {
        db.insert(at("2026-08-02T23:00:00Z", BlockEvent.Type.HOME, ""));
        db.insert(at("2026-08-03T00:00:00Z", BlockEvent.Type.HOME, ""));
        db.putUsage("2026-08-02", "feed.app", 1, 2, 0);
        db.putUsage("2026-08-03", "feed.app", 1, 2, 0);
        assertEquals(
                2, db.prune(LogRetention.firstKeptDay(java.time.LocalDate.parse("2026-10-31"))));
        assertEquals(1, db.events("2000-01-01").size());
        assertEquals("2026-08-03", db.usage("2000-01-01").get(0).day);
    }

    @Test
    public void usageIsReplacedPerDayAndApp() {
        db.putUsage("2026-10-31", "feed.app", 1000, -1, 1);
        db.putUsage("2026-10-31", "feed.app", 2000, 2500, 2);
        List<LogSummary.Usage> u = db.usage("2026-10-31");
        assertEquals(1, u.size());
        assertEquals(2000, u.get(0).observedMs);
        assertEquals(2500, u.get(0).usageStatsMs);
    }

    @Test
    public void clearEmptiesBothTables() {
        db.insert(at("2026-10-31T10:00:00Z", BlockEvent.Type.HOME, ""));
        db.putUsage("2026-10-31", "a.b", 1, 1, 1);
        db.clear();
        assertTrue(db.events("2000-01-01").isEmpty());
        assertTrue(db.usage("2000-01-01").isEmpty());
    }

    @Test
    public void hourlyStatsRoundTripAndPrune() {
        long[] h = new long[24];
        h[20] = 30 * 60_000L;
        h[21] = 15 * 60_000L;
        db.putHours("2026-08-02", java.util.Collections.singletonMap("feed.app", h), 5);
        db.putHours("2026-10-31", java.util.Collections.singletonMap("feed.app", h), 7);
        assertEquals(7L, (long) db.hoursDone("2026-10-01").get("2026-10-31"));
        long[] back = db.hours("2026-10-01").get("2026-10-31").get("feed.app");
        assertEquals(30 * 60_000L, back[20]);
        assertEquals(15 * 60_000L, back[21]);
        assertEquals(0, back[3]);
        db.putHours("2026-10-31", java.util.Collections.emptyMap(), 9);
        assertNull(db.hours("2026-10-01").get("2026-10-31")); // se reemplaza el día
        assertEquals(
                3,
                db.prune(
                        LogRetention.firstKeptDay(
                                java.time.LocalDate.parse(
                                        "2026-10-31")))); // 2 filas + 1 marca del 2 de agosto
        assertTrue(db.hoursDone("2000-01-01").containsKey("2026-10-31"));
        assertFalse(db.hoursDone("2000-01-01").containsKey("2026-08-02"));
    }

    @Test
    public void upgradeFromVersion2OnlyAddsHourlyTables() {
        android.database.sqlite.SQLiteDatabase w = db.getWritableDatabase();
        w.execSQL("DROP TABLE stats_hour");
        w.execSQL("DROP TABLE stats_hour_done");
        db.putStats("2026-10-31", java.util.Collections.singletonMap("feed.app", 5L), 1);
        db.onUpgrade(w, 2, 3);
        assertEquals(5L, (long) db.stats("2026-10-01").get("2026-10-31").get("feed.app"));
        assertTrue(db.hours("2026-10-01").isEmpty());
    }
}
