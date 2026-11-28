package es.foco.next.log;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

/**
 * Fase 4: separate database {@code foco-log.db}. Never touches {@code focus-lite-v1.json}. Plain
 * SQLiteOpenHelper (no Room/annotation processor in :app, see docs/FASE-4.md). Only used from the
 * BlockLog executor thread (never from the main thread).
 */
public final class LogDatabase extends SQLiteOpenHelper {
    public static final String NAME = "foco-log.db";
    static final int VERSION =
            4; // Lote 4: stats_day / stats_done · 1.9: stats_hour / stats_hour_done · Foco 1.0:
    // inspector_event / inspector_day
    private static LogDatabase instance;
    private static Context owner;

    /** One helper per application context (tests create a new application per test). */
    static synchronized LogDatabase get(Context c) {
        Context app = c.getApplicationContext() == null ? c : c.getApplicationContext();
        if (instance == null || owner != app) {
            if (instance != null)
                try {
                    instance.close();
                } catch (RuntimeException ignored) {
                }
            instance = new LogDatabase(app);
            owner = app;
        }
        return instance;
    }

    LogDatabase(Context c) {
        super(c, NAME, null, VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE block_event("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "ts INTEGER NOT NULL,"
                        + "zone TEXT NOT NULL,"
                        + "day TEXT NOT NULL,"
                        + "type TEXT NOT NULL,"
                        + "pkg TEXT NOT NULL DEFAULT '',"
                        + "budget_used_ms INTEGER NOT NULL DEFAULT -1,"
                        + "window_left_ms INTEGER NOT NULL DEFAULT -1,"
                        + "lock_ms INTEGER NOT NULL DEFAULT -1,"
                        + "action TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE INDEX block_event_day ON block_event(day)");
        db.execSQL(
                "CREATE TABLE usage_day("
                        + "day TEXT NOT NULL,"
                        + "pkg TEXT NOT NULL,"
                        + "observed_ms INTEGER NOT NULL DEFAULT -1,"
                        + "usage_stats_ms INTEGER NOT NULL DEFAULT -1,"
                        + "updated INTEGER NOT NULL DEFAULT 0,"
                        + "PRIMARY KEY(day,pkg))");
        createStats(db);
        createHours(db);
        createInspector(db);
    }

    /**
     * Foco 1.0, etapa 1: log del Inspector (detalle 30 días) y su resumen diario. Solo añade
     * tablas.
     */
    private static void createInspector(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS inspector_event(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER NOT NULL,day TEXT NOT NULL,hour INTEGER NOT NULL,type TEXT NOT NULL,pkg TEXT NOT NULL DEFAULT '',ms INTEGER NOT NULL DEFAULT 0,times INTEGER NOT NULL DEFAULT 0,value INTEGER NOT NULL DEFAULT 0,detail TEXT NOT NULL DEFAULT '',dedupe TEXT UNIQUE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS inspector_event_day ON inspector_event(day)");
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS inspector_day(day TEXT NOT NULL,type TEXT NOT NULL,pkg TEXT NOT NULL,ms INTEGER NOT NULL DEFAULT 0,times INTEGER NOT NULL DEFAULT 0,value INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(day,type,pkg))");
    }

    /**
     * Lote 4: cache of foreground time per app and day from UsageStatsManager (all apps, for
     * Estadísticas).
     */
    private static void createStats(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS stats_day(day TEXT NOT NULL,pkg TEXT NOT NULL,ms INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(day,pkg))");
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS stats_done(day TEXT PRIMARY KEY NOT NULL,updated INTEGER NOT NULL DEFAULT 0)");
    }

    /**
     * 1.9: foreground time per app, day and local hour (Sugerencias: horas punta). Only adds
     * tables.
     */
    private static void createHours(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS stats_hour(day TEXT NOT NULL,hour INTEGER NOT NULL,pkg TEXT NOT NULL,ms INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(day,hour,pkg))");
        db.execSQL(
                "CREATE TABLE IF NOT EXISTS stats_hour_done(day TEXT PRIMARY KEY NOT NULL,updated INTEGER NOT NULL DEFAULT 0)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int from, int to) {
        // Migrations only add tables; the log is never silently dropped.
        if (from < 2) createStats(db);
        if (from < 3) createHours(db);
        if (from < 4) createInspector(db);
    }

    public long insert(BlockEvent e) {
        ContentValues v = new ContentValues();
        v.put("ts", e.timestamp);
        v.put("zone", e.zone);
        v.put("day", e.day);
        v.put("type", e.type.name());
        v.put("pkg", e.pkg);
        v.put("budget_used_ms", e.budgetUsedMs);
        v.put("window_left_ms", e.windowLeftMs);
        v.put("lock_ms", e.lockMs);
        v.put("action", e.action);
        return getWritableDatabase().insertOrThrow("block_event", null, v);
    }

    /** Inserts or replaces the contrast of one app on one day. */
    public void putUsage(String day, String pkg, long observedMs, long usageStatsMs, long now) {
        ContentValues v = new ContentValues();
        v.put("day", day);
        v.put("pkg", pkg);
        v.put("observed_ms", observedMs);
        v.put("usage_stats_ms", usageStatsMs);
        v.put("updated", now);
        getWritableDatabase()
                .insertWithOnConflict("usage_day", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Deletes everything before {@code firstKeptDay}. Returns deleted rows. */
    public int prune(String firstKeptDay) {
        SQLiteDatabase db = getWritableDatabase();
        int n = db.delete("block_event", "day<?", new String[] {firstKeptDay});
        n += db.delete("usage_day", "day<?", new String[] {firstKeptDay});
        n += db.delete("stats_day", "day<?", new String[] {firstKeptDay});
        n += db.delete("stats_done", "day<?", new String[] {firstKeptDay});
        n += db.delete("stats_hour", "day<?", new String[] {firstKeptDay});
        n += db.delete("stats_hour_done", "day<?", new String[] {firstKeptDay});
        return n;
    }

    public void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("block_event", null, null);
        db.delete("usage_day", null, null);
        db.delete("stats_day", null, null);
        db.delete("stats_done", null, null);
        db.delete("stats_hour", null, null);
        db.delete("stats_hour_done", null, null);
        db.delete("inspector_event", null, null);
        db.delete("inspector_day", null, null);
    }

    // ---- Foco 1.0, etapa 1: log del Inspector ----
    /** Una fila del log del Inspector (detalle, o resumen diario con hour = −1). */
    public static final class InspectorRow {
        public final String day, type, pkg, detail;
        public final int hour;
        public long ms, times, value;

        public InspectorRow(
                String day,
                int hour,
                String type,
                String pkg,
                long ms,
                long times,
                long value,
                String detail) {
            this.day = day;
            this.hour = hour;
            this.type = type;
            this.pkg = pkg;
            this.ms = ms;
            this.times = times;
            this.value = value;
            this.detail = detail;
        }
    }

    /**
     * Guarda una fila. Con {@code dedupe} la fila es única: si {@code accumulate}, se suman tiempo
     * y veces a la existente (uso por hora); si no, la segunda vez no se guarda nada (un hecho se
     * registra una sola vez).
     */
    /**
     * v32: true si quedó guardado (fila nueva o sumada); false si ya existía ese {@code dedupe}.
     */
    public boolean addInspector(
            long ts,
            String day,
            int hour,
            String type,
            String pkg,
            long ms,
            long times,
            long value,
            String detail,
            String dedupe,
            boolean accumulate) {
        SQLiteDatabase db = getWritableDatabase();
        if (dedupe != null && accumulate) {
            db.execSQL(
                    "UPDATE inspector_event SET ms=ms+?,times=times+? WHERE dedupe=?",
                    new Object[] {ms, times, dedupe});
            try (Cursor c = db.rawQuery("SELECT changes()", null)) {
                if (c.moveToFirst() && c.getInt(0) > 0) return true;
            }
        }
        ContentValues v = new ContentValues();
        v.put("ts", ts);
        v.put("day", day);
        v.put("hour", hour);
        v.put("type", type);
        v.put("pkg", pkg);
        v.put("ms", ms);
        v.put("times", times);
        v.put("value", value);
        v.put("detail", detail);
        if (dedupe != null) v.put("dedupe", dedupe);
        return db.insertWithOnConflict("inspector_event", null, v, SQLiteDatabase.CONFLICT_IGNORE)
                != -1;
    }

    /** Detalle desde {@code fromDay}, en orden de día y hora. */
    public List<InspectorRow> inspectorRows(String fromDay) {
        List<InspectorRow> out = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,hour,type,pkg,ms,times,value,detail FROM inspector_event WHERE day>=? ORDER BY day,hour,ts,id",
                                new String[] {fromDay})) {
            while (c.moveToNext())
                out.add(
                        new InspectorRow(
                                c.getString(0),
                                c.getInt(1),
                                c.getString(2),
                                c.getString(3),
                                c.getLong(4),
                                c.getLong(5),
                                c.getLong(6),
                                c.getString(7)));
        }
        return out;
    }

    /** Resumen diario (días cuyo detalle ya se compactó) desde {@code fromDay}. */
    public List<InspectorRow> inspectorDays(String fromDay) {
        List<InspectorRow> out = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,type,pkg,ms,times,value FROM inspector_day WHERE day>=? ORDER BY day,type,pkg",
                                new String[] {fromDay})) {
            while (c.moveToNext())
                out.add(
                        new InspectorRow(
                                c.getString(0),
                                -1,
                                c.getString(1),
                                c.getString(2),
                                c.getLong(3),
                                c.getLong(4),
                                c.getLong(5),
                                "resumen_diario"));
        }
        return out;
    }

    /** v47: ¿la base no tiene nada del Inspector? (recién instalado o datos borrados). */
    public boolean inspectorEmpty() {
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT (SELECT COUNT(*) FROM inspector_event)+(SELECT COUNT(*) FROM inspector_day)",
                                null)) {
            return !c.moveToFirst() || c.getLong(0) == 0;
        }
    }

    /**
     * v47: recuperar desde Drive. Escribe resúmenes diarios (día, tipo, app) sin pisar lo que ya
     * hay. Devuelve cuántas filas entraron.
     */
    public int restoreSummary(List<InspectorRow> rows) {
        SQLiteDatabase db = getWritableDatabase();
        int n = 0;
        db.beginTransaction();
        try {
            for (InspectorRow r : rows) {
                ContentValues v = new ContentValues();
                v.put("day", r.day);
                v.put("type", r.type);
                v.put("pkg", r.pkg);
                v.put("ms", r.ms);
                v.put("times", r.times);
                v.put("value", r.value);
                if (db.insertWithOnConflict(
                                "inspector_day", null, v, SQLiteDatabase.CONFLICT_IGNORE)
                        != -1) n++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return n;
    }

    /**
     * El detalle anterior a {@code firstDetailDay} pasa al resumen diario y se borra; el resumen
     * anterior a {@code firstSummaryDay} se borra.
     */
    public void compactInspector(String firstDetailDay, String firstSummaryDay) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL(
                    "INSERT OR IGNORE INTO inspector_day(day,type,pkg,ms,times,value) SELECT day,type,pkg,SUM(ms),SUM(times),SUM(value) FROM inspector_event WHERE day<? GROUP BY day,type,pkg",
                    new Object[] {firstDetailDay});
            db.delete("inspector_event", "day<?", new String[] {firstDetailDay});
            db.delete("inspector_day", "day<?", new String[] {firstSummaryDay});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 1.9: replaces one day of the hourly cache (pkg → ms per local hour 0–23). */
    public void putHours(String day, java.util.Map<String, long[]> apps, long updated) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("stats_hour", "day=?", new String[] {day});
            for (java.util.Map.Entry<String, long[]> e : apps.entrySet()) {
                long[] h = e.getValue();
                for (int i = 0; i < 24 && i < h.length; i++)
                    if (h[i] > 0) {
                        ContentValues v = new ContentValues();
                        v.put("day", day);
                        v.put("hour", i);
                        v.put("pkg", e.getKey());
                        v.put("ms", h[i]);
                        db.insertWithOnConflict(
                                "stats_hour", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                    }
            }
            ContentValues d = new ContentValues();
            d.put("day", day);
            d.put("updated", updated);
            db.insertWithOnConflict("stats_hour_done", null, d, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** 1.9: day → last update time of the hourly cache since {@code fromDay}. */
    public java.util.Map<String, Long> hoursDone(String fromDay) {
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,updated FROM stats_hour_done WHERE day>=?",
                                new String[] {fromDay})) {
            while (c.moveToNext()) out.put(c.getString(0), c.getLong(1));
        }
        return out;
    }

    /** 1.9: cached day → (pkg → ms per hour) since {@code fromDay}. */
    public java.util.Map<String, java.util.Map<String, long[]>> hours(String fromDay) {
        java.util.Map<String, java.util.Map<String, long[]>> out = new java.util.TreeMap<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,hour,pkg,ms FROM stats_hour WHERE day>=?",
                                new String[] {fromDay})) {
            while (c.moveToNext()) {
                int h = c.getInt(1);
                if (h < 0 || h > 23) continue;
                out.computeIfAbsent(c.getString(0), k -> new java.util.LinkedHashMap<>())
                                .computeIfAbsent(c.getString(2), k -> new long[24])[h] +=
                        c.getLong(3);
            }
        }
        return out;
    }

    /**
     * Lote 4: replaces one day of the stats cache. {@code updated} after the day's end marks it
     * final.
     */
    public void putStats(String day, java.util.Map<String, Long> apps, long updated) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("stats_day", "day=?", new String[] {day});
            for (java.util.Map.Entry<String, Long> e : apps.entrySet()) {
                ContentValues v = new ContentValues();
                v.put("day", day);
                v.put("pkg", e.getKey());
                v.put("ms", e.getValue());
                db.insertWithOnConflict("stats_day", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            ContentValues d = new ContentValues();
            d.put("day", day);
            d.put("updated", updated);
            db.insertWithOnConflict("stats_done", null, d, SQLiteDatabase.CONFLICT_REPLACE);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Lote 4: day → last update time, for cached days since {@code fromDay}. */
    public java.util.Map<String, Long> statsDone(String fromDay) {
        java.util.Map<String, Long> out = new java.util.HashMap<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,updated FROM stats_done WHERE day>=?",
                                new String[] {fromDay})) {
            while (c.moveToNext()) out.put(c.getString(0), c.getLong(1));
        }
        return out;
    }

    /** Lote 4: cached day → (pkg → ms) since {@code fromDay}. */
    public java.util.Map<String, java.util.Map<String, Long>> stats(String fromDay) {
        java.util.Map<String, java.util.Map<String, Long>> out = new java.util.TreeMap<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,pkg,ms FROM stats_day WHERE day>=?",
                                new String[] {fromDay})) {
            while (c.moveToNext())
                out.computeIfAbsent(c.getString(0), k -> new java.util.LinkedHashMap<>())
                        .put(c.getString(1), c.getLong(2));
        }
        return out;
    }

    public List<LogSummary.Count> counts(String fromDay) {
        List<LogSummary.Count> out = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,type,pkg,COUNT(*) FROM block_event WHERE day>=? GROUP BY day,type,pkg",
                                new String[] {fromDay})) {
            while (c.moveToNext())
                out.add(
                        new LogSummary.Count(
                                c.getString(0), c.getString(1), c.getString(2), c.getInt(3)));
        }
        return out;
    }

    public List<LogSummary.Usage> usage(String fromDay) {
        List<LogSummary.Usage> out = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT day,pkg,observed_ms,usage_stats_ms FROM usage_day WHERE day>=? ORDER BY day,pkg",
                                new String[] {fromDay})) {
            while (c.moveToNext())
                out.add(
                        new LogSummary.Usage(
                                c.getString(0), c.getString(1), c.getLong(2), c.getLong(3)));
        }
        return out;
    }

    /** Events since {@code fromDay}, oldest first. Rows with unknown types are skipped. */
    public List<BlockEvent> events(String fromDay) {
        List<BlockEvent> out = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT id,ts,zone,day,type,pkg,budget_used_ms,window_left_ms,lock_ms,action FROM block_event WHERE day>=? ORDER BY ts,id",
                                new String[] {fromDay})) {
            while (c.moveToNext()) {
                BlockEvent.Type t = BlockEvent.Type.parse(c.getString(4));
                if (t == null) continue;
                BlockEvent e =
                        new BlockEvent(
                                c.getLong(0), t, c.getLong(1), c.getString(2), c.getString(3));
                e.pkg(c.getString(5));
                e.budgetUsedMs = c.getLong(6);
                e.windowLeftMs = c.getLong(7);
                e.lockMs = c.getLong(8);
                e.action(c.getString(9));
                out.add(e);
            }
        }
        return out;
    }
}
