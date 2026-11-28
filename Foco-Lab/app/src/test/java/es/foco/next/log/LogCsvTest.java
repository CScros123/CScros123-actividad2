package es.foco.next.log;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

/** Fase 4: CSV export formatting (pure JVM). */
public class LogCsvTest {
    private static int columns(String line) {
        int n = 1;
        boolean quoted = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') quoted = !quoted;
            else if (ch == ',' && !quoted) n++;
        }
        return n;
    }

    @Test
    public void everyRowHasTheHeaderColumns() {
        BlockEvent e =
                new BlockEvent(
                                BlockEvent.Type.BUDGET_LOCK,
                                Instant.parse("2026-10-31T15:04:05Z").toEpochMilli(),
                                ZoneId.of("America/Panama"))
                        .pkg("com.feed.app")
                        .budgetUsed(300_000)
                        .windowLeft(1_500_000)
                        .lock(1_500_000);
        String csv =
                LogCsv.build(
                        Collections.singletonList(e),
                        Collections.singletonList(
                                new LogSummary.Usage(
                                        "2026-10-31", "com.feed.app", 240_000, 300_000)),
                        p -> "Feed, la app");
        String[] lines = csv.split("\r\n");
        assertEquals(3, lines.length);
        assertEquals(LogCsv.HEADER, lines[0]);
        int header = columns(lines[0]);
        assertEquals(14, header);
        assertEquals(header, columns(lines[1]));
        assertEquals(header, columns(lines[2]));
        assertTrue(
                lines[1].startsWith(
                        "\"evento\",\"2026-10-31\",\"10:04:05\",\"America/Panama\",\"BUDGET_LOCK\",\"Bloqueo por límite\",\"com.feed.app\",\"Feed, la app\",300,1500,1500,\"\""));
        assertTrue(lines[2].startsWith("\"uso_diario\",\"2026-10-31\""));
        assertTrue(lines[2].endsWith(",240000,300000"));
    }

    @Test
    public void unknownValuesAreEmptyCells() {
        BlockEvent e = new BlockEvent(BlockEvent.Type.HOME, 0, ZoneId.of("UTC"));
        String row =
                LogCsv.build(
                                Collections.singletonList(e),
                                Collections.singletonList(
                                        new LogSummary.Usage("1970-01-01", "a.b", 10, -1)),
                                null)
                        .split("\r\n")[1];
        assertTrue(row, row.contains("\"\",\"\",,,,\"\",,"));
        String usage =
                LogCsv.build(
                                null,
                                Arrays.asList(new LogSummary.Usage("1970-01-01", "a.b", 10, -1)),
                                null)
                        .split("\r\n")[1];
        assertTrue(usage, usage.endsWith(",10,"));
    }

    @Test
    public void formulaInjectionAndQuotesAreNeutralised() {
        assertEquals("\"'=SUM(A1)\"", LogCsv.cell("=SUM(A1)"));
        assertEquals("\"'+1\"", LogCsv.cell("+1"));
        assertEquals("\"'-1\"", LogCsv.cell("-1"));
        assertEquals("\"'@x\"", LogCsv.cell("@x"));
        assertEquals("\"di \"\"hola\"\"\"", LogCsv.cell("di \"hola\""));
        assertEquals("\"\"", LogCsv.cell(null));
        String csv =
                LogCsv.build(
                        null,
                        Collections.singletonList(new LogSummary.Usage("2026-10-31", "a.b", 1, 1)),
                        p -> "=HYPERLINK(\"x\")");
        assertTrue(csv.contains("\"'=HYPERLINK(\"\"x\"\")\""));
    }

    @Test
    public void failingLabelFallsBackToPackage() {
        String csv =
                LogCsv.build(
                        null,
                        Collections.singletonList(new LogSummary.Usage("2026-10-31", "a.b", 1, 1)),
                        p -> {
                            throw new IllegalStateException("pm");
                        });
        assertTrue(csv.contains("\"a.b\",\"a.b\""));
    }

    @Test
    public void invalidZoneFallsBackToUtc() {
        BlockEvent e =
                new BlockEvent(
                        1,
                        BlockEvent.Type.HOME,
                        Instant.parse("2026-10-31T15:04:05Z").toEpochMilli(),
                        "Not/AZone",
                        "2026-10-31");
        assertEquals("15:04:05", LogCsv.time(e));
    }
}
