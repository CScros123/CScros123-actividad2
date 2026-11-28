package es.foco.next.log;

import static org.junit.Assert.*;

import es.foco.next.core.AppConfig;
import es.foco.next.core.FocusState;
import java.util.*;
import org.junit.Test;

/**
 * Fase 4: snapshot of observed time for the UsageStats contrast (pure JVM; FocusState is only
 * read).
 */
public class ObservedSnapshotTest {
    @Test
    public void mergesLeisureAndAlternativeAndCollectsPackagesOfInterest() {
        FocusState s = new FocusState();
        s.weeklyOcio.put("2026-10-31", new LinkedHashMap<>(Map.of("feed.app", 120_000L)));
        s.weeklyAlternative.put("2026-10-31", new LinkedHashMap<>(Map.of("reader.app", 60_000L)));
        s.weeklyAlternative.put("2026-10-30", new LinkedHashMap<>(Map.of("feed.app", 5_000L)));
        AppConfig c = new AppConfig();
        c.watchedApps.add("video.app");
        c.returnApp = "notes.app";
        ObservedSnapshot o = ObservedSnapshot.of(s, c);
        assertEquals(Long.valueOf(120_000), o.day("2026-10-31").get("feed.app"));
        assertEquals(Long.valueOf(60_000), o.day("2026-10-31").get("reader.app"));
        assertEquals(Long.valueOf(5_000), o.day("2026-10-30").get("feed.app"));
        assertTrue(o.day("2026-01-01").isEmpty());
        assertEquals(
                new HashSet<>(Arrays.asList("feed.app", "reader.app", "video.app", "notes.app")),
                o.interest);
        assertEquals(Long.valueOf(120_000), s.weeklyOcio.get("2026-10-31").get("feed.app"));
    }

    @Test
    public void nullsAreEmpty() {
        ObservedSnapshot o = ObservedSnapshot.of(null, null);
        assertTrue(o.byDay.isEmpty());
        assertTrue(o.interest.isEmpty());
    }
}
