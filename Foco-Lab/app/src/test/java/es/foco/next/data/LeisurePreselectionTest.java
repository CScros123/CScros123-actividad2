package es.foco.next.data;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.content.pm.*;
import android.provider.Settings;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;

/**
 * Fase 5: una instalación nueva queda con las apps de ocio instaladas marcadas, sin esenciales ni
 * bancos.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LeisurePreselectionTest {
    private Context c;

    private void install(String p, String label) {
        PackageInfo info = new PackageInfo();
        info.packageName = p;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.packageName = p;
        info.applicationInfo.nonLocalizedLabel = label;
        ActivityInfo activity = new ActivityInfo();
        activity.name = p + ".Main";
        activity.packageName = p;
        activity.applicationInfo = info.applicationInfo;
        activity.exported = activity.enabled = true;
        info.activities = new ActivityInfo[] {activity};
        Shadows.shadowOf(c.getPackageManager()).installPackage(info);
        ResolveInfo r = new ResolveInfo();
        r.activityInfo = activity;
        r.nonLocalizedLabel = label;
        Shadows.shadowOf(c.getPackageManager())
                .addResolveInfoForIntent(
                        new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), r);
    }

    @Before
    public void init() {
        c = RuntimeEnvironment.getApplication();
        Repository.resetForTests();
        for (String n : new String[] {"focus-lite-v1.json", "focus-state-v1.json"}) c.deleteFile(n);
        Settings.Global.putInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, 3);
    }

    @After
    public void clean() {
        Repository.resetForTests();
    }

    @Test
    public void freshInstallMarksInstalledLeisureOnly() {
        install("com.google.android.youtube", "YouTube");
        install("com.roblox.client", "Roblox");
        install("com.whatsapp", "WhatsApp");
        install("com.bgeneral.app", "Banca Móvil");
        install("com.spotify.music", "Spotify");
        install("org.readera", "ReadEra");
        var watched = Repository.get(c).config().watchedApps;
        assertTrue(watched.contains("com.google.android.youtube"));
        assertTrue(watched.contains("com.roblox.client"));
        assertFalse(watched.contains("com.whatsapp"));
        assertFalse(watched.contains("com.bgeneral.app"));
        assertFalse(watched.contains("com.spotify.music"));
        assertFalse(watched.contains("org.readera"));
        // Reglas absolutas: las 13 redes sociales están siempre en ocio, instaladas o no.
        var own = new java.util.HashSet<>(watched);
        own.removeAll(es.foco.next.core.AbsoluteRules.SOCIAL_PACKAGES);
        assertEquals(1, own.size());
    }

    @Test
    public void existingInstallIsNeverRewritten() {
        Repository r = Repository.get(c);
        var a = r.config();
        a.watchedApps.add("feed.app");
        r.saveConfig(a);
        Repository.resetForTests();
        install("com.roblox.client", "Roblox");
        var watched = Repository.get(c).config().watchedApps;
        assertTrue(watched.contains("feed.app"));
        assertFalse(watched.contains("com.roblox.client"));
    }
}
