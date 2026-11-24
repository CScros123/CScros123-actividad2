package es.foco.next.core;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

/** Fase 5: catálogo de ocio y preselección de una instalación nueva. */
public class LeisureCatalogTest {
    private static final String[] REQUIRED = {
        "com.google.android.youtube",
        "com.netflix.mediaclient",
        "tv.twitch.android.app",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.instagram.android",
        "com.facebook.katana",
        "com.twitter.android",
        "com.snapchat.android",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.disney.disneyplus",
        "com.amazon.avod.thirdpartyclient",
        "com.wbd.stream",
        "com.hbo.hbonow",
        "com.kick.mobile",
        "com.roblox.client",
        "com.dts.freefireth",
        "com.supercell.clashroyale",
        "com.king.candycrushsaga",
        "com.tencent.ig",
        "com.miHoYo.GenshinImpact"
    };

    @Test
    public void catalogCoversTheAgreedLeisureApps() {
        for (String p : REQUIRED) {
            assertTrue(p, AlternativeCatalog.leisure(p));
            assertTrue(p, AlternativeCatalog.social(p));
        }
    }

    @Test
    public void musicIsNotLeisureByDefault() {
        for (String p :
                new String[] {
                    "com.spotify.music",
                    "com.google.android.apps.youtube.music",
                    "deezer.android.app"
                }) assertFalse(p, AlternativeCatalog.leisure(p));
    }

    @Test
    public void freshInstallMarksOnlyInstalledLeisureApps() {
        AppConfig c = new AppConfig();
        List<String> installed =
                Arrays.asList(
                        "com.google.android.youtube",
                        "com.miHoYo.GenshinImpact",
                        "org.readera",
                        "com.duolingo",
                        "com.spotify.music",
                        "com.example.unknown",
                        "com.roblox.client");
        Set<String> chosen = AlternativeCatalog.preselect(c, installed, null);
        assertEquals(
                new LinkedHashSet<>(
                        Arrays.asList(
                                "com.google.android.youtube",
                                "com.miHoYo.GenshinImpact",
                                "com.roblox.client")),
                chosen);
        // Se conserva el nombre exacto que da Android (mayúsculas incluidas).
        assertTrue(chosen.contains("com.miHoYo.GenshinImpact"));
    }

    @Test
    public void nothingInstalledMeansNothingMarked() {
        assertTrue(
                AlternativeCatalog.preselect(new AppConfig(), Collections.emptyList(), null)
                        .isEmpty());
        assertTrue(AlternativeCatalog.preselect(new AppConfig(), null, null).isEmpty());
    }

    @Test
    public void essentialsBanksAndWhatsAppAreNeverMarked() {
        AppConfig c = new AppConfig();
        List<String> installed =
                Arrays.asList(
                        "com.whatsapp",
                        "com.whatsapp.w4b",
                        "com.bgeneral.app",
                        "com.bbva.bbvacontigo",
                        "com.nu.production",
                        "com.android.settings",
                        "com.samsung.android.dialer",
                        "com.google.android.dialer",
                        "es.foco.next",
                        "com.facebook.orca",
                        "org.telegram.messenger",
                        "com.google.android.youtube");
        Set<String> chosen = AlternativeCatalog.preselect(c, installed, null);
        assertEquals(Collections.singleton("com.google.android.youtube"), chosen);
        for (String p : installed)
            if (!p.equals("com.google.android.youtube"))
                assertTrue(p, AlternativeCatalog.neverMark(c, p));
    }

    @Test
    public void catalogNeverContainsEssentialOrSensitivePackages() {
        AppConfig c = new AppConfig();
        for (String p : AlternativeCatalog.leisurePackages()) {
            assertFalse(
                    "Esencial o banco en el catálogo: " + p, AlternativeCatalog.neverMark(c, p));
            assertFalse(p, SensitiveApps.isSensitive(c, p));
        }
    }

    @Test
    public void userSensitiveListAndBankLabelsWinOverCatalog() {
        AppConfig c = new AppConfig();
        c.sensitiveApps.add("com.netflix.mediaclient");
        List<String> installed =
                Arrays.asList("com.netflix.mediaclient", "com.twitter.android", "com.pinterest");
        Set<String> chosen =
                AlternativeCatalog.preselect(c, installed, p -> p.equals("com.pinterest"));
        assertEquals(Collections.singleton("com.twitter.android"), chosen);
    }

    @Test
    public void returnAppIsNeverMarked() {
        AppConfig c = new AppConfig();
        c.returnApp = "com.pinterest";
        assertTrue(
                AlternativeCatalog.preselect(c, Collections.singletonList("com.pinterest"), null)
                        .isEmpty());
    }

    @Test
    public void leisureAppsAreNeverOfferedAsAlternatives() {
        for (String p : REQUIRED) assertEquals(p, -1, AlternativeCatalog.group(p, "Kindle", true));
    }
}
