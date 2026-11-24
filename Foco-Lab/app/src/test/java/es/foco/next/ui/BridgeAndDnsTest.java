package es.foco.next.ui;

import static org.junit.Assert.*;

import android.content.*;
import android.view.*;
import android.widget.*;
import es.foco.next.core.*;
import es.foco.next.data.Repository;
import es.foco.next.runtime.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;

/** Fases 2 y 3: botón de práctica en la pausa, pausa por DNS y qué apps puede pausar el DNS. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
public class BridgeAndDnsTest {
    @Before
    public void prepare() {
        Context c = RuntimeEnvironment.getApplication();
        ReflectionHelpers.callStaticMethod(Repository.class, "resetForTests");
        c.deleteFile("focus-lite-v1.json");
        c.deleteFile("focus-state-v1.json");
        c.getSharedPreferences(DnsMonitor.PREFS, 0).edit().clear().commit();
        ReflectionHelpers.callStaticMethod(CalmAudio.class, "resetForTests");
    }

    private static TextView find(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText()))
            return (TextView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = find(g.getChildAt(i), text);
                if (t != null) return t;
            }
        }
        return null;
    }

    @Test
    public void dnsTargetsLeisureAndBrowsersButNeverEssentials() {
        AppConfig c = new AppConfig();
        c.watchedApps.add("feed.app");
        assertTrue(DnsMonitor.target(c, "feed.app"));
        assertTrue(DnsMonitor.target(c, "com.android.chrome"));
        assertTrue(DnsMonitor.target(c, "com.sec.android.app.sbrowser"));
        for (String p :
                new String[] {
                    "com.android.settings",
                    "com.android.phone",
                    "com.samsung.android.dialer",
                    "com.whatsapp",
                    "es.foco.next",
                    "com.bgeneral.app",
                    "other.app",
                    "",
                    null
                }) assertFalse(String.valueOf(p), DnsMonitor.target(c, p));
        c.sensitiveApps.add("com.android.chrome");
        assertFalse(DnsMonitor.target(c, "com.android.chrome"));
        c.enabled = false;
        assertFalse(DnsMonitor.target(c, "feed.app"));
    }

    @Test
    public void providerIsOnlyNextDns() {
        Context c = RuntimeEnvironment.getApplication();
        assertEquals("", DnsMonitor.provider(c));
        assertFalse(DnsMonitor.armed(c));
        DnsMonitor.setProvider(c, "family-filter-dns.cleanbrowsing.org");
        assertEquals("", DnsMonitor.provider(c));
        DnsMonitor.setProvider(c, "abc123.dns.nextdns.io");
        assertEquals("abc123.dns.nextdns.io", DnsMonitor.provider(c));
        assertEquals("abc123.dns.nextdns.io", DnsMonitor.lastNextDns(c));
    }

    @Test
    public void unarmedMonitorNeverBlocks() {
        DnsMonitor m = new DnsMonitor(RuntimeEnvironment.getApplication(), () -> {});
        m.start();
        try {
            assertFalse(m.decide().blocked());
        } finally {
            m.stop();
        }
    }

    @Test
    public void dnsPauseIsShownOnceAndBudgetPauseReplacesIt() {
        var s = Robolectric.buildService(FocusAccessibilityService.class).create();
        FocusOverlay o = new FocusOverlay(s.get());
        try {
            o.dnsBlock("abc123.dns.nextdns.io");
            assertTrue(o.dnsVisible());
            View first = ReflectionHelpers.getField(o, "view");
            assertNotNull(find(first, "Abrir DNS privado"));
            assertNotNull(find(first, "abc123.dns.nextdns.io"));
            o.dnsBlock("abc123.dns.nextdns.io");
            assertSame(first, ReflectionHelpers.getField(o, "view"));
            o.block(Repository.get(s.get()).config(), 3300000);
            assertTrue(o.visible());
            assertFalse(o.dnsVisible());
            assertNotSame(first, ReflectionHelpers.getField(o, "view"));
            o.dismiss();
            assertFalse(o.visible());
            assertFalse(o.dnsVisible());
        } finally {
            o.dismiss();
            s.destroy();
        }
    }

    @Test
    public void pauseOffersShortPracticeInItsOwnTask() {
        var s = Robolectric.buildService(FocusAccessibilityService.class).create();
        FocusOverlay o = new FocusOverlay(s.get());
        try {
            o.block(Repository.get(s.get()).config(), 3300000);
            TextView practice = find(ReflectionHelpers.getField(o, "view"), "Practicar 3 min");
            assertNotNull(practice);
            long used = Repository.get(s.get()).state().usedMillis;
            practice.performClick();
            Intent started =
                    Shadows.shadowOf(RuntimeEnvironment.getApplication()).getNextStartedActivity();
            assertNotNull(started);
            assertEquals(
                    "personal.qqq.braintraining.LabActivity",
                    started.getComponent().getClassName());
            assertTrue(personal.qqq.braintraining.LabLaunch.isShort(started));
            assertTrue(personal.qqq.braintraining.LabLaunch.fromPause(started));
            assertTrue((started.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0);
            assertEquals(used, Repository.get(s.get()).state().usedMillis);
        } finally {
            o.dismiss();
            s.destroy();
        }
    }
}
