package es.foco.next.log;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fase 4: best-effort logging. Neither {@link #submit} nor {@link #run} ever throws, whatever the
 * executor or the sink do, so the caller (the blocking path) always continues. Pure Java for JVM
 * tests.
 */
public final class SafeLogger {
    public interface Sink {
        void write(BlockEvent event) throws Exception;
    }

    public interface Errors {
        void report(Throwable error);
    }

    private final Executor executor;
    private final Sink sink;
    private final Errors errors;
    private final AtomicInteger failures = new AtomicInteger();

    public SafeLogger(Executor executor, Sink sink, Errors errors) {
        this.executor = executor;
        this.sink = sink;
        this.errors = errors;
    }

    /** Queues the event. Returns false if it could not even be queued. Never throws. */
    public boolean submit(BlockEvent event) {
        if (event == null) return false;
        return run(
                () -> {
                    try {
                        sink.write(event);
                    } catch (Throwable t) {
                        fail(t);
                    }
                });
    }

    /** Queues any log task (pruning, usage contrast, reads). Never throws. */
    public boolean run(Runnable task) {
        if (task == null) return false;
        try {
            executor.execute(
                    () -> {
                        try {
                            task.run();
                        } catch (Throwable t) {
                            fail(t);
                        }
                    });
            return true;
        } catch (Throwable t) {
            fail(t);
            return false;
        }
    }

    public int failures() {
        return failures.get();
    }

    private void fail(Throwable t) {
        failures.incrementAndGet();
        try {
            if (errors != null) errors.report(t);
        } catch (Throwable ignored) {
        }
    }
}
