package es.foco.next.log;

import static org.junit.Assert.*;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Test;

/** Fase 4: the log never stops the caller (the blocking path), whatever fails. Pure JVM. */
public class SafeLoggerTest {
    private static BlockEvent event() {
        return new BlockEvent(BlockEvent.Type.BUDGET_LOCK, 0, ZoneId.of("UTC")).pkg("feed.app");
    }

    /** Stand-in for the service: log, then show the pause. */
    private static boolean blockWith(SafeLogger log) {
        log.submit(event());
        return true;
    }

    @Test
    public void failingSinkDoesNotPreventTheBlock() {
        List<Throwable> reported = new ArrayList<>();
        SafeLogger log =
                new SafeLogger(
                        Runnable::run,
                        e -> {
                            throw new IllegalStateException("disco lleno");
                        },
                        reported::add);
        assertTrue(blockWith(log));
        assertEquals(1, log.failures());
        assertEquals(1, reported.size());
    }

    @Test
    public void errorsThrownBySinkAreAlsoContained() {
        SafeLogger log =
                new SafeLogger(
                        Runnable::run,
                        e -> {
                            throw new LinkageError("sqlite ausente");
                        },
                        null);
        assertTrue(blockWith(log));
        assertEquals(1, log.failures());
    }

    @Test
    public void rejectingExecutorDoesNotPreventTheBlock() {
        SafeLogger log =
                new SafeLogger(
                        r -> {
                            throw new RejectedExecutionException("cola llena");
                        },
                        e -> fail("never written"),
                        null);
        assertFalse(log.submit(event()));
        assertTrue(blockWith(log));
        assertEquals(2, log.failures());
    }

    @Test
    public void failingErrorReporterIsIgnored() {
        SafeLogger log =
                new SafeLogger(
                        Runnable::run,
                        e -> {
                            throw new RuntimeException("x");
                        },
                        t -> {
                            throw new RuntimeException("y");
                        });
        assertTrue(blockWith(log));
        assertEquals(1, log.failures());
    }

    @Test
    public void tasksAndNullsAreSafe() {
        SafeLogger log = new SafeLogger(Runnable::run, e -> {}, null);
        assertFalse(log.submit(null));
        assertFalse(log.run(null));
        assertTrue(
                log.run(
                        () -> {
                            throw new IllegalStateException("poda");
                        }));
        assertEquals(1, log.failures());
    }

    @Test
    public void workingSinkReceivesEventsInOrder() {
        List<BlockEvent> seen = new ArrayList<>();
        SafeLogger log = new SafeLogger(Runnable::run, seen::add, null);
        BlockEvent a = event(), b = event();
        log.submit(a);
        log.submit(b);
        assertSame(a, seen.get(0));
        assertSame(b, seen.get(1));
        assertEquals(0, log.failures());
    }
}
