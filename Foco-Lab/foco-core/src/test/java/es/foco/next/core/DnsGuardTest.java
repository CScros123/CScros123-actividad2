package es.foco.next.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class DnsGuardTest {
    // v26: solo NextDNS. CB y AG son ahora dos perfiles distintos de NextDNS.
    private static final String CB = "abc123.dns.nextdns.io", AG = "zzz999.dns.nextdns.io";

    private DnsGuard.State on(boolean active, String server) {
        return new DnsGuard.State(true, true, active, server, CB);
    }

    @Test
    public void noActiveNetworkIsOk() {
        DnsGuard.State s = new DnsGuard.State(true, false, false, null, CB);
        DnsGuard.Decision d = DnsGuard.decide(s, 500_000, 0);
        assertEquals(DnsGuard.Verdict.OK, d.verdict);
        assertEquals(500_000, d.lastOk);
    }

    @Test
    public void notRequiredIsOkEvenWithDnsOff() {
        assertEquals(
                DnsGuard.Verdict.OK,
                DnsGuard.decide(new DnsGuard.State(false, true, false, null, CB), 500_000, 0)
                        .verdict);
        assertTrue(DnsGuard.compliant(null));
    }

    @Test
    public void matchingProviderIsOk() {
        assertEquals(DnsGuard.Verdict.OK, DnsGuard.decide(on(true, CB), 1000, -1).verdict);
    }

    @Test
    public void graceOfSixtySecondsBeforeBlocking() {
        DnsGuard.State off = on(false, null);
        DnsGuard.Decision d = DnsGuard.decide(off, 100_000, 100_000);
        assertEquals(DnsGuard.Verdict.GRACE, d.verdict);
        assertEquals(60_000, d.recheck);
        d = DnsGuard.decide(off, 159_999, 100_000);
        assertEquals(DnsGuard.Verdict.GRACE, d.verdict);
        assertEquals(1, d.recheck);
        assertFalse(d.blocked());
        d = DnsGuard.decide(off, 160_000, 100_000);
        assertEquals(DnsGuard.Verdict.BLOCK, d.verdict);
        assertTrue(d.blocked());
        assertEquals(0, d.recheck);
    }

    @Test
    public void unknownOrFutureLastOkStartsGraceNow() {
        DnsGuard.Decision d = DnsGuard.decide(on(false, null), 10_000, -1);
        assertEquals(DnsGuard.Verdict.GRACE, d.verdict);
        assertEquals(10_000, d.lastOk);
        d = DnsGuard.decide(on(false, null), 10_000, 99_000);
        assertEquals(DnsGuard.Verdict.GRACE, d.verdict);
        assertEquals(10_000, d.lastOk);
    }

    @Test
    public void automaticModeOrOtherProviderIsNotCompliant() {
        assertFalse(DnsGuard.compliant(on(true, null)));
        assertFalse(DnsGuard.compliant(on(true, "")));
        assertFalse(DnsGuard.compliant(on(true, "dns.google")));
        assertFalse(DnsGuard.compliant(on(true, "family-filter-dns.cleanbrowsing.org")));
        assertFalse(DnsGuard.compliant(on(false, CB)));
    }

    @Test
    public void hostComparisonAvoidsFalsePositives() {
        assertTrue(DnsGuard.compliant(on(true, "ABC123.dns.NextDNS.io")));
        assertTrue(DnsGuard.compliant(on(true, " abc123.dns.nextdns.io. ")));
        assertTrue(
                DnsGuard.compliant(
                        new DnsGuard.State(true, true, true, AG, "ZZZ999.dns.nextdns.io.")));
        assertFalse(DnsGuard.compliant(on(true, "abc123.dns.nextdns.io.evil.example")));
    }

    @Test
    public void onlyNextDnsOrAutomatic() {
        assertEquals("", DnsGuard.provider("x.example"));
        assertEquals("", DnsGuard.provider(null));
        assertEquals("", DnsGuard.provider("family-filter-dns.cleanbrowsing.org"));
        assertEquals(DnsGuard.AUTOMATIC, DnsGuard.provider(" Automatico "));
        // Sin perfil: vale cualquier perfil de NextDNS, nada más.
        assertTrue(DnsGuard.compliant(new DnsGuard.State(true, true, true, CB, "otro")));
        assertFalse(DnsGuard.compliant(new DnsGuard.State(true, true, true, "dns.google", "")));
        assertFalse(DnsGuard.compliant(new DnsGuard.State(true, true, false, null, "")));
        // Automático (pedido con 24 h): no se exige DNS.
        assertTrue(
                DnsGuard.compliant(
                        new DnsGuard.State(true, true, false, null, DnsGuard.AUTOMATIC)));
        assertEquals("Automático (sin filtro)", DnsGuard.providerLabel(DnsGuard.AUTOMATIC));
    }

    @Test
    public void providerChangeIsDetected() {
        DnsGuard.State s = new DnsGuard.State(true, true, true, CB, AG);
        assertFalse(DnsGuard.compliant(s));
        DnsGuard.Tracker t = new DnsGuard.Tracker();
        assertEquals(
                DnsGuard.Verdict.OK,
                t.decide(new DnsGuard.State(true, true, true, CB, CB), 1_000).verdict);
        // Ten minutes later the user picks another NextDNS profile but Android still points to
        // the old one: full grace first.
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(s, 601_000).verdict);
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(s, 660_999).verdict);
        assertEquals(DnsGuard.Verdict.BLOCK, t.decide(s, 661_000).verdict);
        // Android updated: unblocks at once.
        assertEquals(
                DnsGuard.Verdict.OK,
                t.decide(new DnsGuard.State(true, true, true, AG, AG), 662_000).verdict);
    }

    @Test
    public void trackerGraceStartsWhenProblemIsFirstSeenNotAtOldOk() {
        DnsGuard.Tracker t = new DnsGuard.Tracker();
        assertEquals(DnsGuard.Verdict.OK, t.decide(on(true, CB), 0).verdict);
        // Network change after a long quiet period: Android may report private DNS inactive while
        // it validates.
        DnsGuard.Decision d = t.decide(on(false, null), 3_600_000);
        assertEquals(DnsGuard.Verdict.GRACE, d.verdict);
        assertEquals(60_000, d.recheck);
        assertEquals(DnsGuard.Verdict.OK, t.decide(on(true, CB), 3_605_000).verdict);
    }

    @Test
    public void trackerBlocksAndUnblocksWithoutGettingStuck() {
        DnsGuard.Tracker t = new DnsGuard.Tracker();
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(on(false, null), 1_000).verdict);
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(on(false, null), 30_000).verdict);
        assertEquals(DnsGuard.Verdict.BLOCK, t.decide(on(false, null), 61_000).verdict);
        assertEquals(DnsGuard.Verdict.BLOCK, t.decide(on(false, null), 900_000).verdict);
        assertEquals(DnsGuard.Verdict.OK, t.decide(on(true, CB), 900_500).verdict);
        // Off again: a new grace, not an instant block.
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(on(false, null), 901_000).verdict);
    }

    @Test
    public void losingNetworkIsOkAndReturningGivesGrace() {
        DnsGuard.Tracker t = new DnsGuard.Tracker();
        t.decide(on(false, null), 0);
        assertEquals(DnsGuard.Verdict.BLOCK, t.decide(on(false, null), 70_000).verdict);
        assertEquals(
                DnsGuard.Verdict.OK,
                t.decide(new DnsGuard.State(true, false, false, null, CB), 80_000).verdict);
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(on(false, null), 81_000).verdict);
    }

    @Test
    public void armingStartsAFullGrace() {
        DnsGuard.Tracker t = new DnsGuard.Tracker();
        assertEquals(
                DnsGuard.Verdict.OK,
                t.decide(new DnsGuard.State(false, true, false, null, CB), 0).verdict);
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(on(false, null), 500_000).verdict);
        assertEquals(DnsGuard.Verdict.BLOCK, t.decide(on(false, null), 560_000).verdict);
    }

    @Test
    public void clockGoingBackwardsNeverBlocksInstantly() {
        DnsGuard.Tracker t = new DnsGuard.Tracker();
        t.decide(on(false, null), 100_000);
        assertEquals(DnsGuard.Verdict.GRACE, t.decide(on(false, null), 5_000).verdict);
    }

    @Test
    public void knownBrowsersOnly() {
        for (String b :
                new String[] {
                    "com.android.chrome",
                    "com.sec.android.app.sbrowser",
                    "org.mozilla.firefox",
                    "com.brave.browser",
                    "com.microsoft.emmx",
                    "com.opera.browser",
                    "com.duckduckgo.mobile.android"
                }) assertTrue(b, DnsGuard.browser(b));
        assertFalse(DnsGuard.browser("com.android.settings"));
        assertFalse(DnsGuard.browser("com.whatsapp"));
        assertFalse(DnsGuard.browser(null));
        assertFalse(DnsGuard.browser("com.android.chrome.beta"));
    }

    @Test
    public void settingsPhoneAndFocoStayEssentialForRepair() {
        AppConfig c = new AppConfig();
        for (String p :
                new String[] {
                    "com.android.settings",
                    "com.android.phone",
                    "com.samsung.android.dialer",
                    "es.foco.next"
                }) {
            assertTrue(p, FocusEngine.essential(c, p));
            assertFalse(p, DnsGuard.browser(p));
        }
    }

    // NextDNS (escrito sin ejecutar, por indicación del usuario).
    @Test
    public void nextDnsProfileIsAcceptedExactly() {
        assertTrue(DnsGuard.nextDns("abc123.dns.nextdns.io"));
        assertTrue(DnsGuard.nextDns(" Galaxy-S23-abc123.dns.nextdns.io. "));
        assertFalse(DnsGuard.nextDns("dns.nextdns.io"));
        assertFalse(DnsGuard.nextDns("abc123.dns.nextdns.io.evil.com"));
        assertEquals("abc123.dns.nextdns.io", DnsGuard.provider("ABC123.dns.nextdns.io"));
        assertEquals("abc123", DnsGuard.nextDnsProfile("galaxy-abc123.dns.nextdns.io"));
        assertEquals("NextDNS (perfil abc123)", DnsGuard.providerLabel("abc123.dns.nextdns.io"));
        // Otro perfil de NextDNS no cuenta como el elegido.
        assertFalse(
                DnsGuard.sameHost(
                        "zzz999.dns.nextdns.io", DnsGuard.provider("abc123.dns.nextdns.io")));
        DnsGuard.State st =
                new DnsGuard.State(
                        true, true, true, "abc123.dns.nextdns.io", "abc123.dns.nextdns.io");
        assertTrue(DnsGuard.compliant(st));
        st = new DnsGuard.State(true, true, true, "zzz999.dns.nextdns.io", "abc123.dns.nextdns.io");
        assertFalse(DnsGuard.compliant(st));
    }
}
