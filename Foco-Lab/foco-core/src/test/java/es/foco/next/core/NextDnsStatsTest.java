package es.foco.next.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class NextDnsStatsTest {
    private static final String STATUS =
            "{\"data\":[{\"status\":\"default\",\"queries\":900},{\"status\":\"blocked\",\"queries\":34},{\"status\":\"allowed\",\"queries\":6}],\"meta\":{}}";
    private static final String DOMAINS =
            "{\"data\":[{\"domain\":\"tiktok.com\",\"root\":\"tiktok.com\",\"queries\":20},{\"queries\":9,\"domain\":\"instagram.com\"}]}";

    @Test
    public void readsStatus() {
        assertEquals(34, NextDnsStats.queries(STATUS, "blocked"));
        assertEquals(940, NextDnsStats.total(STATUS));
    }

    @Test
    public void readsDomainsInEitherOrder() {
        List<NextDnsStats.Hit> d = NextDnsStats.domains(DOMAINS, 5);
        assertEquals(2, d.size());
        assertEquals("tiktok.com", d.get(0).domain);
        assertEquals(9, d.get(1).queries);
    }

    @Test
    public void encodeRoundTrip() {
        List<NextDnsStats.Hit> d = NextDnsStats.decode(NextDnsStats.encode(NextDnsStats.domains(DOMAINS, 5)));
        assertEquals("instagram.com", d.get(1).domain);
        assertEquals(20, d.get(0).queries);
    }

    @Test
    public void owlLineRespectsHiddenNumbers() {
        List<NextDnsStats.Hit> d = NextDnsStats.domains(DOMAINS, 5);
        assertEquals("🦉 NextDNS frenó 34 intentos hoy, sobre todo tiktok.com.", NextDnsStats.owlLine(34, d, true));
        assertFalse(NextDnsStats.owlLine(34, d, false).matches(".*\\d.*"));
        assertNull(NextDnsStats.owlLine(0, d, true));
    }

    @Test
    public void validation() {
        assertTrue(NextDnsStats.validKey("a1b2c3d4e5f6a7b8c9d0e1f2"));
        assertFalse(NextDnsStats.validKey("con espacios no"));
        assertTrue(NextDnsStats.validProfile("96916b"));
        assertFalse(NextDnsStats.validProfile("https://x"));
    }

    @Test
    public void urls() {
        assertEquals("https://api.nextdns.io/profiles/96916b/analytics/status?from=2026-11-22T05:00:00Z",
                NextDnsStats.statusUrl("96916b", "2026-11-22T05:00:00Z"));
    }
}
