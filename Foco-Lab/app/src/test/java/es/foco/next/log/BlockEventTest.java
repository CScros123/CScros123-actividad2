package es.foco.next.log;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.ZoneId;
import org.junit.Test;

/** Fase 4: event model (pure JVM). */
public class BlockEventTest {
    private static final long T = Instant.parse("2026-10-31T03:30:00Z").toEpochMilli();

    @Test
    public void dayFollowsTheLocalZoneAtThatMoment() {
        assertEquals(
                "2026-10-30",
                new BlockEvent(BlockEvent.Type.BUDGET_LOCK, T, ZoneId.of("America/Panama")).day);
        assertEquals(
                "2026-10-31",
                new BlockEvent(BlockEvent.Type.BUDGET_LOCK, T, ZoneId.of("Europe/Madrid")).day);
        assertEquals(
                "America/Panama",
                new BlockEvent(BlockEvent.Type.HOME, T, ZoneId.of("America/Panama")).zone);
    }

    @Test
    public void storedTypeNamesAreStable() {
        String[] expected = {
            "BUDGET_LOCK",
            "DNS_OFF_LOCK",
            "ATTEMPT_WHILE_LOCKED",
            "PRACTICE_STARTED",
            "PRACTICE_FINISHED",
            "ALT_OPENED",
            "HOME",
            "RULE_LOCK"
        };
        assertEquals(expected.length, BlockEvent.Type.values().length);
        for (String name : expected) assertNotNull(name, BlockEvent.Type.parse(name));
        assertNull(BlockEvent.Type.parse("SOMETHING_NEW"));
        assertNull(BlockEvent.Type.parse(null));
    }

    @Test
    public void categories() {
        assertTrue(BlockEvent.Type.BUDGET_LOCK.isBlock());
        assertTrue(BlockEvent.Type.DNS_OFF_LOCK.isBlock());
        assertTrue(BlockEvent.Type.ATTEMPT_WHILE_LOCKED.isAttempt());
        assertFalse(BlockEvent.Type.ATTEMPT_WHILE_LOCKED.isBlock());
        assertTrue(BlockEvent.Type.PRACTICE_STARTED.isPractice());
        assertFalse(BlockEvent.Type.PRACTICE_FINISHED.isPractice());
    }

    @Test
    public void unknownNumbersAreMinusOneAndPackagesAreSanitised() {
        BlockEvent e =
                new BlockEvent(BlockEvent.Type.ATTEMPT_WHILE_LOCKED, T, ZoneId.of("UTC"))
                        .pkg(" com.example.feed ")
                        .action("pausa_inicio")
                        .budgetUsed(-5);
        assertEquals("com.example.feed", e.pkg);
        assertEquals("pausa_inicio", e.action);
        assertEquals(-1, e.budgetUsedMs);
        assertEquals(-1, e.windowLeftMs);
        assertEquals(-1, e.lockMs);
        assertEquals(
                "", new BlockEvent(BlockEvent.Type.HOME, T, null).pkg("=cmd|' /C calc'!A0").pkg);
        assertEquals("", new BlockEvent(BlockEvent.Type.HOME, T, null).pkg(null).pkg);
        assertEquals("", new BlockEvent(BlockEvent.Type.HOME, T, null).pkg("a".repeat(300)).pkg);
        assertEquals("UTC", new BlockEvent(BlockEvent.Type.HOME, T, null).zone);
    }

    @Test
    public void typeIsRequired() {
        assertThrows(
                IllegalArgumentException.class, () -> new BlockEvent(null, T, ZoneId.of("UTC")));
    }
}
