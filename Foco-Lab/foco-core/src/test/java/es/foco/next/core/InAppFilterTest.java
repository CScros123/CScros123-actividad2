package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.junit.Test;

public class InAppFilterTest {
    static final String YT = InAppFilter.YOUTUBE,
            IG = InAppFilter.INSTAGRAM,
            FB = InAppFilter.FACEBOOK,
            SC = InAppFilter.SNAPCHAT;

    static List<String> l(String... s) {
        return Arrays.asList(s);
    }

    static final List<String> NONE = Collections.emptyList();

    static ZonedDateTime at(int h) {
        return ZonedDateTime.of(2026, 11, 6, h, 0, 0, 0, ScheduleTest.MAD);
    }

    static BlockRule rule(String id, boolean scheduled, String... sections) {
        BlockRule r = new BlockRule();
        r.id = id;
        r.name = "Sin Shorts";
        r.sections.addAll(Arrays.asList(sections));
        if (scheduled) {
            r.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
            r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 9 * 60, 17 * 60));
        }
        r.clamp();
        return r;
    }

    @Test
    public void youtubeShortsPlayerIds() {
        assertEquals(
                "youtube_shorts",
                InAppFilter.detect(YT, l("com.google.android.youtube:id/reel_recycler"), NONE));
        assertEquals(
                "youtube_shorts",
                InAppFilter.detect(
                        YT,
                        l(
                                "com.google.android.youtube:id/toolbar",
                                "com.google.android.youtube:id/reel_player_page_container"),
                        NONE));
        assertEquals("youtube_shorts", InAppFilter.detect(YT, l("reel_watch_player"), NONE));
    }

    @Test
    public void youtubeHomeWithShortsTabAndShelfIsNotBlocked() {
        // La pestaña «Shorts» y la estantería de Shorts existen en el inicio: no bloquean.
        List<String> ids =
                l(
                        "com.google.android.youtube:id/pivot_bar",
                        "com.google.android.youtube:id/reel_shelf_item",
                        "com.google.android.youtube:id/results");
        List<String> descs = l("Inicio", "Shorts", "Suscripciones", "Tú", "Shorts", "Buscar");
        assertNull(InAppFilter.detect(YT, ids, descs));
        assertNull(InAppFilter.detect(YT, ids, descs, l("Inicio"), null));
        assertNull(
                InAppFilter.detect(
                        YT,
                        l("com.google.android.youtube:id/watch_player"),
                        l("Vídeo sobre Shorts de cocina")));
    }

    @Test
    public void selectedShortsTabBlocks() {
        assertEquals(
                "youtube_shorts",
                InAppFilter.detect(YT, NONE, l("Inicio", "Shorts"), l("Shorts"), null));
        assertEquals("youtube_shorts", InAppFilter.detect(YT, NONE, NONE, l("SHORTS"), null));
        assertNull(InAppFilter.detect(YT, NONE, NONE, l("Shortstop highlights"), null));
    }

    @Test
    public void instagramReels() {
        assertEquals(
                "instagram_reels",
                InAppFilter.detect(
                        IG, l("com.instagram.android:id/clips_viewer_view_pager"), NONE));
        assertEquals(
                "instagram_reels",
                InAppFilter.detect(IG, l("com.instagram.android:id/clips_video_container"), NONE));
        assertEquals("instagram_reels", InAppFilter.detect(IG, NONE, NONE, l("Reels"), null));
        // Inicio con la pestaña Reels sin seleccionar ni visor: nada.
        assertNull(
                InAppFilter.detect(
                        IG,
                        l(
                                "com.instagram.android:id/clips_tab",
                                "com.instagram.android:id/feed_tab"),
                        l("Inicio", "Reels", "Buscar"),
                        l("Inicio"),
                        null));
    }

    @Test
    public void facebookReelsAndWatch() {
        assertEquals(
                "facebook_reels",
                InAppFilter.detect(FB, NONE, NONE, l("Reels, pestaña 3 de 6"), null));
        assertEquals(
                "facebook_reels", InAppFilter.detect(FB, NONE, NONE, l("Watch, tab 4 of 6"), null));
        assertEquals("facebook_reels", InAppFilter.detect(FB, NONE, NONE, l("Video"), null));
        assertNull(
                InAppFilter.detect(
                        FB,
                        NONE,
                        l("Reels, pestaña 3 de 6", "Inicio"),
                        l("Inicio, pestaña 1 de 6"),
                        null));
        assertNull(InAppFilter.detect(FB, NONE, NONE, l("Videos de Ana"), null));
    }

    @Test
    public void snapchatSpotlight() {
        assertEquals(
                "snapchat_spotlight", InAppFilter.detect(SC, NONE, NONE, l("Spotlight"), null));
        assertNull(
                InAppFilter.detect(SC, NONE, l("Spotlight", "Chat", "Cámara"), l("Cámara"), null));
    }

    @Test
    public void youtubeCommentsNeedsPanelAndTitle() {
        assertEquals(
                "youtube_comments",
                InAppFilter.detect(
                        YT,
                        l("com.google.android.youtube:id/engagement_panel"),
                        l("Comentarios"),
                        null,
                        l("youtube_comments")));
        assertNull(
                InAppFilter.detect(
                        YT,
                        l("com.google.android.youtube:id/engagement_panel"),
                        l("Descripción"),
                        null,
                        null));
        assertNull(InAppFilter.detect(YT, NONE, l("Comentarios"), null, null));
    }

    @Test
    public void otherPackagesAndOnlyFilter() {
        assertNull(InAppFilter.detect("com.zhiliaoapp.musically", l("reel_recycler"), l("Shorts")));
        assertNull(InAppFilter.detect(IG, l("reel_recycler"), NONE)); // ids de otra app
        assertNull(InAppFilter.detect(null, l("reel_recycler"), NONE));
        assertNull(InAppFilter.detect(YT, l("reel_recycler"), NONE, null, l("youtube_comments")));
        assertNull(InAppFilter.detect(YT, null, null, null, null));
        assertFalse(InAppFilter.supported("com.zhiliaoapp.musically"));
        assertTrue(InAppFilter.supported(YT));
    }

    @Test
    public void activeLikeWebFilter() {
        RuleState s = new RuleState();
        BlockRule always = rule("a", false, "youtube_shorts");
        assertTrue(InAppFilter.active(always, s, at(3)));
        assertEquals(
                new LinkedHashSet<>(l("youtube_shorts")),
                InAppFilter.wanted(Collections.singletonList(always), s, at(3), YT));
        assertTrue(InAppFilter.wanted(Collections.singletonList(always), s, at(3), IG).isEmpty());
        BlockRule sch = rule("b", true, "instagram_reels");
        assertTrue(InAppFilter.active(sch, s, at(10)));
        assertFalse(InAppFilter.active(sch, s, at(20)));
        BlockRule off = rule("c", false, "youtube_shorts");
        off.enabled = false;
        assertFalse(InAppFilter.active(off, s, at(10)));
        assertFalse(InAppFilter.active(rule("d", false), s, at(10)));
        // Una pausa lo levanta.
        BlockRule p = rule("p", false, "youtube_shorts");
        long wall = at(10).toInstant().toEpochMilli();
        assertTrue(RuleEngine.pause(p, s, wall, at(10).toLocalDate().toString()));
        assertFalse(InAppFilter.active(p, s, at(10)));
        assertNull(InAppFilter.blocker(Collections.singletonList(p), s, at(10), "youtube_shorts"));
        assertSame(
                always, InAppFilter.blocker(Arrays.asList(p, always), s, at(10), "youtube_shorts"));
    }

    @Test
    public void sectionsDoNotBlockTheAppNorConsumeLimits() {
        RuleState s = new RuleState();
        BlockRule r = rule("a", false, "youtube_shorts");
        RuleEngine.Decision d =
                RuleEngine.decide(Collections.singletonList(r), s, YT, at(10), null, 60_000);
        assertFalse(d.blocked);
        assertEquals(0, s.of("a").usedMillis);
        assertFalse(RuleEngine.active(r, s, at(10)));
        // Sin horario no bloquea Ajustes en estricto; con horario y en franja sí (como web y
        // compras).
        assertFalse(RuleEngine.strictActive(Collections.singletonList(r), s, at(10)));
        assertTrue(
                RuleEngine.strictActive(
                        Collections.singletonList(rule("b", true, "youtube_shorts")), s, at(10)));
    }

    @Test
    public void cleanDropsUnknownAndDuplicates() {
        BlockRule r = new BlockRule();
        r.sections.addAll(
                Arrays.asList(
                        "youtube_shorts", "nope", null, " youtube_shorts", "instagram_reels"));
        r.clamp();
        assertEquals(l("youtube_shorts", "instagram_reels"), r.sections);
        BlockRule c = r.copy();
        assertEquals(r.sections, c.sections);
        c.sections.clear();
        assertEquals(2, r.sections.size());
        assertEquals(r.sections, r.duplicate("z").sections);
    }

    @Test
    public void quickToggle() {
        BlockRule on = InAppFilter.quickToggle(null, true, "q");
        assertEquals("q", on.id);
        assertEquals(InAppFilter.QUICK_RULE_NAME, on.name);
        assertTrue(on.enabled);
        assertTrue(on.packages.isEmpty());
        assertTrue(on.sections.containsAll(InAppFilter.SHORT_VIDEO));
        assertFalse(on.scheduled());
        assertTrue(InAppFilter.quickOn(on));
        BlockRule off = InAppFilter.quickToggle(on, false, "x");
        assertEquals("q", off.id);
        assertFalse(off.enabled);
        assertFalse(InAppFilter.quickOn(off));
        assertFalse(RuleChange.isStricter(on, off)); // con el candado no se puede apagar
        BlockRule partial = rule("q", false, "youtube_comments");
        BlockRule again = InAppFilter.quickToggle(partial, true, "x");
        assertTrue(again.sections.contains("youtube_comments"));
        assertTrue(InAppFilter.quickOn(again));
        assertTrue(RuleChange.isStricter(partial, again));
    }

    @Test
    public void labelsAndCatalog() {
        assertEquals("Shorts de YouTube", InAppFilter.label("youtube_shorts"));
        assertEquals("x", InAppFilter.label("x"));
        assertEquals("Shorts", InAppFilter.noun("youtube_shorts"));
        assertEquals("Reels", InAppFilter.noun("instagram_reels"));
        Set<String> ids = new HashSet<>();
        for (InAppFilter.Section s : InAppFilter.CATALOG) assertTrue(ids.add(s.id));
        for (InAppFilter.Section s : InAppFilter.CATALOG)
            assertNotEquals("com.zhiliaoapp.musically", s.pkg);
    }
}
