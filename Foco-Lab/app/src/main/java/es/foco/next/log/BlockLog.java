package es.foco.next.log;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Fase 4: static entry point of the block log ({@code foco-log.db}). Best effort:
 *
 * <ul>
 *   <li>Every public method returns immediately and never throws. If the log fails, blocking
 *       continues.
 *   <li>All database work runs on one background thread (bounded queue; extra events are dropped,
 *       never block).
 *   <li>90-day pruning and the UsageStats contrast run once a day (first decision of the day) and
 *       when "Registro" opens.
 * </ul>
 *
 * Other phases call {@link #record(Context, BlockEvent.Type, String, String)}, for example {@code
 * BlockLog.record(context, BlockEvent.Type.PRACTICE_STARTED, "", "pausa")}.
 */
public final class BlockLog {
    private BlockLog() {}

    private static final String TAG = "FocoLog", PREFS = "foco-log", MAINTAINED = "maintained";
    private static final Object LOCK = new Object();
    private static SafeLogger logger;
    private static volatile Context app;
    private static volatile String maintainedDay = "";

    /** Work run on the log thread with the open database. */
    public interface Work<T> {
        T run(LogDatabase db) throws Exception;
    }

    // ---- Writing
    // ---------------------------------------------------------------------------------------------

    /**
     * Logs an event now. {@code pkg} and {@code action} may be empty. Never throws, never blocks.
     */
    public static void record(Context c, BlockEvent.Type type, String pkg, String action) {
        try {
            if (type != null) record(c, BlockEvent.now(type).pkg(pkg).action(action));
        } catch (Throwable t) {
            report(t);
        }
    }

    /** Logs a prepared event. Never throws, never blocks. */
    public static void record(Context c, BlockEvent event) {
        try {
            if (c == null || event == null) return;
            remember(c);
            logger().submit(event);
            es.foco.next.runtime.HomeHub.onBlock(c, event.type); // Foco Home, etapa 1.2
        } catch (Throwable t) {
            report(t);
        }
    }

    /**
     * Hook of FocusAccessibilityService.evaluate(), called after the pause was shown or hidden.
     * Derives BUDGET_LOCK / ATTEMPT_WHILE_LOCKED and starts the daily maintenance. Never throws.
     *
     * @param snapshot called at most once a day, on the caller's thread
     */
    public static void afterDecision(
            Context c,
            LockTracker tracker,
            String pkg,
            boolean blocked,
            long remainingBudget,
            long left,
            boolean pauseShownBefore,
            long budget,
            Supplier<ObservedSnapshot> snapshot) {
        try {
            ZoneId zone = ZoneId.systemDefault();
            long wall = System.currentTimeMillis();
            BlockEvent e =
                    tracker == null
                            ? null
                            : tracker.next(
                                    pkg,
                                    blocked,
                                    remainingBudget,
                                    left,
                                    pauseShownBefore,
                                    budget,
                                    wall,
                                    zone);
            if (e != null) record(c, e);
            daily(c, LocalDate.now(zone), snapshot);
        } catch (Throwable t) {
            report(t);
        }
    }

    // ---- Maintenance
    // -----------------------------------------------------------------------------------------

    /**
     * Once a day per process (and per day across restarts via preferences): prune + usage contrast.
     */
    static void daily(Context c, LocalDate today, Supplier<ObservedSnapshot> snapshot) {
        String day = today.toString();
        if (day.equals(maintainedDay)) return;
        maintainedDay = day;
        ObservedSnapshot snap = null;
        try {
            if (snapshot != null) snap = snapshot.get();
        } catch (Throwable t) {
            report(t);
        }
        remember(c);
        Context ctx = app;
        ObservedSnapshot taken = snap;
        logger().run(
                        () -> {
                            SharedPreferences p =
                                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
                            if (day.equals(p.getString(MAINTAINED, ""))) return;
                            maintain(ctx, taken, today);
                            p.edit().putString(MAINTAINED, day).apply();
                        });
    }

    /** "Registro" opened: prune and contrast now (on the log thread). Never throws. */
    public static void refreshNow(Context c, ObservedSnapshot snapshot) {
        try {
            remember(c);
            Context ctx = app;
            LocalDate today = LocalDate.now();
            logger().run(() -> maintain(ctx, snapshot, today));
        } catch (Throwable t) {
            report(t);
        }
    }

    private static void maintain(Context ctx, ObservedSnapshot snapshot, LocalDate today) {
        LogDatabase db = LogDatabase.get(ctx);
        db.prune(LogRetention.firstKeptDay(today));
        // Foco 1.0, etapa 1: detalle del Inspector 30 días; después, resumen diario (un año).
        db.compactInspector(
                today.minusDays(InspectorLog.DETAIL_DAYS).toString(),
                today.minusDays(365).toString());
        if (snapshot != null)
            UsageContrast.refresh(
                    ctx, db, snapshot, today, ZoneId.systemDefault(), System.currentTimeMillis());
    }

    // ---- Reading (Registro, export)
    // ----------------------------------------------------------------------------

    /**
     * Runs {@code work} on the log thread; {@code onMain} or {@code onFail} run on the main thread.
     * Never throws.
     */
    public static <T> void query(Context c, Work<T> work, Consumer<T> onMain, Runnable onFail) {
        try {
            remember(c);
            Context ctx = app;
            Handler main = new Handler(Looper.getMainLooper());
            boolean queued =
                    logger().run(
                                    () -> {
                                        T loaded;
                                        try {
                                            loaded = work.run(LogDatabase.get(ctx));
                                        } catch (Throwable t) {
                                            report(t);
                                            post(main, onFail);
                                            return;
                                        }
                                        final T result = loaded;
                                        post(
                                                main,
                                                () -> {
                                                    if (onMain != null) onMain.accept(result);
                                                });
                                    });
            if (!queued) post(main, onFail);
        } catch (Throwable t) {
            report(t);
        }
    }

    /**
     * Foco 1.0, etapa 1: ejecuta {@code work} en el hilo del log y espera el resultado (para
     * exportar desde otro hilo de fondo). Nunca desde el hilo principal. Devuelve null si falla o
     * no termina en {@code timeoutMs}.
     */
    public static <T> T await(Context c, Work<T> work, long timeoutMs) {
        try {
            remember(c);
            Context ctx = app;
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<T> out =
                    new java.util.concurrent.atomic.AtomicReference<>();
            boolean queued =
                    logger().run(
                                    () -> {
                                        try {
                                            out.set(work.run(LogDatabase.get(ctx)));
                                        } catch (Throwable t) {
                                            report(t);
                                        } finally {
                                            done.countDown();
                                        }
                                    });
            if (!queued || !done.await(timeoutMs, TimeUnit.MILLISECONDS)) return null;
            return out.get();
        } catch (Throwable t) {
            report(t);
            return null;
        }
    }

    // ---- Internals
    // ---------------------------------------------------------------------------------------------

    private static void remember(Context c) {
        Context a = c.getApplicationContext();
        app = a == null ? c : a;
    }

    private static SafeLogger logger() {
        synchronized (LOCK) {
            if (logger == null) {
                ThreadPoolExecutor executor =
                        new ThreadPoolExecutor(
                                1,
                                1,
                                0L,
                                TimeUnit.MILLISECONDS,
                                new ArrayBlockingQueue<>(256),
                                r -> {
                                    Thread t = new Thread(r, "foco-log");
                                    t.setDaemon(true);
                                    t.setPriority(Thread.MIN_PRIORITY);
                                    return t;
                                },
                                new ThreadPoolExecutor.DiscardPolicy());
                logger =
                        new SafeLogger(
                                executor, e -> LogDatabase.get(app).insert(e), BlockLog::report);
            }
            return logger;
        }
    }

    private static void post(Handler main, Runnable r) {
        if (r == null) return;
        try {
            main.post(
                    () -> {
                        try {
                            r.run();
                        } catch (RuntimeException t) {
                            report(t);
                        }
                    });
        } catch (Throwable t) {
            report(t);
        }
    }

    private static void report(Throwable t) {
        try {
            android.util.Log.w(TAG, "Registro no disponible; el bloqueo continúa", t);
        } catch (Throwable ignored) {
        }
    }

    // ---- Tests
    // ---------------------------------------------------------------------------------------------------

    /** Tests only: replaces the executor and the sink (for example, a sink that always throws). */
    public static void installForTests(Executor executor, SafeLogger.Sink sink) {
        synchronized (LOCK) {
            logger = new SafeLogger(executor, sink, BlockLog::report);
            maintainedDay = "";
        }
    }

    /** Tests only: back to the default background thread and database. */
    public static void resetForTests() {
        synchronized (LOCK) {
            logger = null;
            maintainedDay = "";
        }
    }

    /** Tests only: failures counted by the current logger. */
    public static int failuresForTests() {
        synchronized (LOCK) {
            return logger == null ? 0 : logger.failures();
        }
    }
}
