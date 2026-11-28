package es.foco.next.core;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Predicate;

/**
 * Explicit identities. Store categories and partial words cannot turn a social feed into a book.
 */
public final class AlternativeCatalog {
    public static final int READING = 0, LEARNING = 1, CREATING = 2, PERSONAL = 3;
    private static final Map<String, Integer> PACKAGES = new HashMap<>(), LABELS = new HashMap<>();

    // --- Fase 5: catálogo de ocio (preselección en instalación nueva) ---
    /**
     * Ocio conocido, por paquete exacto (sin prefijos ni categorías de la tienda). Se compara en
     * minúsculas.
     */
    private static final Set<String> LEISURE = new LinkedHashSet<>();

    /** Comunicación que nunca se marca como ocio aunque alguien la añada al catálogo por error. */
    private static final Set<String> NEVER_LEISURE = new HashSet<>();

    static {
        addLeisure( // Vídeo y directos
                "com.google.android.youtube",
                "com.netflix.mediaclient",
                "tv.twitch.android.app",
                "com.kick.mobile",
                "com.disney.disneyplus",
                "com.amazon.avod.thirdpartyclient",
                "com.hbo.hbonow",
                "com.wbd.stream",
                "com.crunchyroll.crunchyroid",
                "tv.pluto.android",
                // Redes y vídeo corto
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.zhiliaoapp.musically.go",
                "com.instagram.android",
                "com.instagram.lite",
                "com.instagram.barcelona",
                "com.facebook.katana",
                "com.facebook.lite",
                "com.twitter.android",
                "com.snapchat.android",
                "com.reddit.frontpage",
                "com.pinterest",
                "com.tumblr",
                "com.ninegag.android.app",
                "com.kwai.video",
                "video.like",
                "com.bereal.ft",
                // Juegos comunes
                "com.roblox.client",
                "com.dts.freefireth",
                "com.dts.freefiremax",
                "com.supercell.clashroyale",
                "com.supercell.clashofclans",
                "com.supercell.brawlstars",
                "com.king.candycrushsaga",
                "com.king.candycrushsodasaga",
                "com.tencent.ig",
                "com.tencent.iglite",
                "com.miHoYo.GenshinImpact",
                "com.HoYoverse.hkrpgoversea",
                "com.mojang.minecraftpe",
                "com.kiloo.subwaysurf",
                "com.activision.callofduty.shooter",
                "com.innersloth.spacemafia",
                "com.mobile.legends",
                "com.miniclip.eightballpool",
                "com.nianticlabs.pokemongo",
                "com.kitkagames.fallbuddies",
                "com.dreamgames.royalmatch",
                "com.moonactive.coinmaster",
                "com.ea.gp.fifamobile",
                "com.epicgames.fortnite");
        // Música (Spotify, YouTube Music, Deezer...) NO es ocio por defecto: suele sonar de fondo
        // mientras se estudia o se trabaja.
        // El usuario puede añadirla a mano en "Apps de ocio".
        addNever(
                "com.whatsapp",
                "com.whatsapp.w4b",
                "com.facebook.orca",
                "com.facebook.mlite",
                "org.telegram.messenger",
                "org.thoughtcrime.securesms",
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging",
                "com.google.android.gm",
                "com.microsoft.teams",
                "us.zoom.videomeetings",
                "com.google.android.dialer",
                "com.samsung.android.dialer",
                "com.spotify.music",
                "com.apple.android.music"); // Música durante la pausa: nunca ocio
    }

    private static void addLeisure(String... values) {
        for (String v : values) LEISURE.add(v.toLowerCase(Locale.ROOT));
    }

    private static void addNever(String... values) {
        for (String v : values) NEVER_LEISURE.add(v.toLowerCase(Locale.ROOT));
    }

    /** Paquete de ocio del catálogo (minúsculas indiferentes). */
    public static boolean leisure(String pkg) {
        return pkg != null && LEISURE.contains(pkg.toLowerCase(Locale.ROOT));
    }

    public static Set<String> leisurePackages() {
        return Collections.unmodifiableSet(LEISURE);
    }

    /**
     * Guardia: esenciales (Ajustes, Teléfono, Foco), bancos, WhatsApp y mensajería nunca se marcan
     * como ocio.
     */
    public static boolean neverMark(AppConfig c, String pkg) {
        return pkg == null
                || pkg.isEmpty()
                || FocusEngine.essential(c, pkg)
                || NEVER_LEISURE.contains(pkg.toLowerCase(Locale.ROOT));
    }

    /**
     * Preselección de una instalación nueva: solo las apps instaladas que están en el catálogo de
     * ocio, conservando el nombre de paquete tal como lo da Android. {@code alsoProtected} añade la
     * detección por etiqueta (bancos) que solo existe en Android; puede ser null.
     */
    public static Set<String> preselect(
            AppConfig c, Collection<String> installed, Predicate<String> alsoProtected) {
        Set<String> out = new LinkedHashSet<>();
        if (installed == null) return out;
        for (String p : installed) {
            if (!leisure(p) || neverMark(c, p) || p.equals(c.returnApp)) continue;
            if (alsoProtected != null && alsoProtected.test(p)) continue;
            out.add(p);
        }
        return out;
    }

    // --- fin Fase 5 ---
    static {
        add(
                READING,
                "com.google.android.apps.books",
                "com.gtl.nextory",
                "com.syc.librototal.El_Libro_Total",
                "com.amazon.kindle",
                "org.readera",
                "com.flyersoft.moonreader",
                "com.flyersoft.moonreaderp",
                "com.foobnix.pdf.reader",
                "com.foobnix.pro.pdf.reader",
                "org.koreader.launcher",
                "org.geometerplus.zlibrary.ui.android",
                "com.faultexception.reader",
                "com.kobobooks.android",
                "com.sirma.mobile.bible.android");
        labels(
                READING,
                "Play Libros",
                "Google Play Libros",
                "Nextory",
                "El Libro Total",
                "Libro Total",
                "ReadEra",
                "Kindle",
                "Biblia",
                "Santa Biblia",
                "YouVersion Biblia");
        add(
                LEARNING,
                "com.duolingo",
                "org.khanacademy.android",
                "org.coursera.android",
                "com.ichi2.anki",
                "org.wikipedia",
                "org.edx.mobile",
                "com.udemy.android",
                "com.memrise.android.memrisecompanion",
                "com.busuu.android.enc",
                "com.sololearn",
                "com.microblink.photomath",
                "org.brilliant.android");
        labels(
                LEARNING,
                "Duolingo",
                "Khan Academy",
                "AnkiDroid",
                "Wikipedia",
                "Coursera",
                "Brilliant",
                "Photomath");
        add(
                CREATING,
                "com.google.android.keep",
                "com.samsung.android.app.notes",
                "com.samsung.android.calendar",
                "com.samsung.android.app.reminder",
                "com.google.android.calendar",
                "md.obsidian",
                "notion.id",
                "com.todoist",
                "com.microsoft.office.onenote",
                "com.microsoft.office.word",
                "com.microsoft.office.excel",
                "com.google.android.apps.docs.editors.docs",
                "com.google.android.apps.docs.editors.sheets",
                "com.openai.chatgpt",
                "com.anthropic.claude",
                "com.google.android.apps.bard");
        labels(
                CREATING,
                "Notas de Keep",
                "Google Keep",
                "Keep",
                "Samsung Notes",
                "Notes",
                "Samsung Reminder",
                "Reminder",
                "Calendario",
                "Calendar",
                "Obsidian",
                "Notion",
                "Todoist",
                "AppSheet",
                "Hojas de cálculo",
                "ChatGPT",
                "Claude",
                "Gemini",
                "Kimi");
    }

    private AlternativeCatalog() {}

    private static void add(int group, String... values) {
        for (String v : values) PACKAGES.put(v.toLowerCase(Locale.ROOT), group);
    }

    private static void labels(int group, String... values) {
        for (String v : values) LABELS.put(normalize(v), group);
    }

    private static String normalize(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    public static boolean social(String pkg) {
        String p = pkg == null ? "" : pkg.toLowerCase(Locale.ROOT);
        return p.equals("com.facebook")
                || p.startsWith("com.facebook.")
                || p.equals("com.instagram.android")
                || p.equals("com.zhiliaoapp.musically")
                || p.equals("com.ss.android.ugc.trill")
                || p.equals("com.reddit.frontpage")
                || p.equals("com.twitter.android")
                || p.equals("com.snapchat.android")
                || p.equals("com.pinterest")
                || LEISURE.contains(p);
    }

    public static int group(String pkg, String label, boolean chosen) {
        if (pkg == null || social(pkg)) return -1;
        Integer known = PACKAGES.get(pkg.toLowerCase(Locale.ROOT));
        if (known == null) known = LABELS.get(normalize(label));
        return known != null ? known : chosen ? PERSONAL : -1;
    }

    public static int priority(String pkg, String label) {
        String p = pkg.toLowerCase(Locale.ROOT);
        if (p.equals("com.google.android.keep")) return 0;
        if (p.equals("com.samsung.android.app.notes")) return 1;
        if (p.equals("com.samsung.android.calendar")) return 2;
        if (p.equals("com.samsung.android.app.reminder")) return 3;
        if (normalize(label).equals("appsheet")) return 4;
        if (p.equals("com.openai.chatgpt")) return 5;
        return 10;
    }
}
