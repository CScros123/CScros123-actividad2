package es.foco.next.core;

import static org.junit.Assert.*;

import java.time.*;
import java.util.*;
import org.junit.Test;

public class WebFilterTest {
    static ZonedDateTime at(int h) {
        return ZonedDateTime.of(2026, 11, 6, h, 0, 0, 0, ScheduleTest.MAD);
    }

    static BlockRule web(String id, List<String> sites, List<String> keywords) {
        BlockRule r = new BlockRule();
        r.id = id;
        r.name = "Web " + id;
        r.sites.addAll(sites);
        r.keywords.addAll(keywords);
        r.clamp();
        return r;
    }

    @Test
    public void normalizeStripsSchemeWwwMobilePortQueryAndCase() {
        assertEquals(
                "youtube.com/shorts/abc",
                WebFilter.normalize("https://M.YouTube.com:443/Shorts/abc/?x=1#t"));
        assertEquals("youtube.com", WebFilter.normalize("www.youtube.com/"));
        assertEquals(
                "news.example.org/a", WebFilter.normalize("  http://user@news.example.org./a  "));
        assertEquals("x.com", WebFilter.normalize("x.com"));
    }

    @Test
    public void normalizeRejectsSearchText() {
        assertNull(WebFilter.normalize("gatos graciosos"));
        assertNull(WebFilter.normalize("youtube"));
        assertNull(WebFilter.normalize(""));
        assertNull(WebFilter.normalize(null));
        assertNull(WebFilter.normalize("buscar en.la web"));
    }

    @Test
    public void siteMatchesDomainAndSubdomainsOnly() {
        assertTrue(WebFilter.matchesSite("https://www.youtube.com/watch?v=1", "youtube.com"));
        assertTrue(WebFilter.matchesSite("music.youtube.com", "https://youtube.com/"));
        assertTrue(WebFilter.matchesSite("m.youtube.com", "youtube.com"));
        assertFalse(WebFilter.matchesSite("notyoutube.com", "youtube.com"));
        assertFalse(WebFilter.matchesSite("youtube.com.evil.net", "youtube.com"));
        assertFalse(WebFilter.matchesSite("youtube.co", "youtube.com"));
        assertFalse(WebFilter.matchesSite("youtube.com", "music.youtube.com"));
    }

    @Test
    public void sitePathPrefixMatchesBySegment() {
        assertTrue(WebFilter.matchesSite("youtube.com/shorts/abc", "youtube.com/shorts"));
        assertTrue(WebFilter.matchesSite("https://m.youtube.com/shorts", "youtube.com/shorts/"));
        assertFalse(WebFilter.matchesSite("youtube.com/shortsfan", "youtube.com/shorts"));
        assertFalse(WebFilter.matchesSite("youtube.com/watch?v=shorts", "youtube.com/shorts"));
        assertFalse(WebFilter.matchesSite("youtube.com", "youtube.com/shorts"));
    }

    @Test
    public void keywordIsCaseAndAccentInsensitiveWholeWord() {
        assertTrue(WebFilter.matchesKeyword("Mejores PELÍCULAS del año", "películas"));
        assertTrue(WebFilter.matchesKeyword("mejores peliculas", "Películas"));
        assertTrue(WebFilter.matchesKeyword("casino-online.example/poker", "casino"));
        assertFalse(WebFilter.matchesKeyword("Reserva tu hotel", "tel"));
        assertFalse(WebFilter.matchesKeyword("teléfono de contacto", "tel"));
        assertTrue(WebFilter.matchesKeyword("llama al tel. 900", "tel"));
        assertFalse(WebFilter.matchesKeyword("anything", "a"));
    }

    @Test
    public void cleanNormalizesDeduplicatesAndDropsInvalid() {
        BlockRule r =
                web(
                        "a",
                        List.of(
                                "https://www.YouTube.com/",
                                "youtube.com",
                                "no es web",
                                "reddit.com/r/all/"),
                        List.of(" Casino ", "casino", "x", ""));
        assertEquals(List.of("youtube.com", "reddit.com/r/all"), r.sites);
        assertEquals(List.of("casino"), r.keywords);
        BlockRule c = r.copy();
        assertEquals(r.sites, c.sites);
        assertEquals(r.keywords, c.keywords);
    }

    @Test
    public void matchUsesSitesThenKeywordsInUrlAndText() {
        BlockRule r = web("a", List.of("youtube.com/shorts"), List.of("apuestas"));
        RuleState s = new RuleState();
        WebFilter.Hit h =
                WebFilter.match(List.of(r), s, at(12), "https://m.youtube.com/shorts/xyz", null);
        assertNotNull(h);
        assertEquals("youtube.com", h.what);
        assertSame(r, h.rule);
        assertNull(
                WebFilter.match(
                        List.of(r),
                        s,
                        at(12),
                        "youtube.com/watch?v=1",
                        List.of("Vídeo de cocina")));
        h =
                WebFilter.match(
                        List.of(r),
                        s,
                        at(12),
                        "deportes.example.com/hoy",
                        List.of("Las mejores APUESTAS"));
        assertNotNull(h);
        assertEquals("deportes.example.com", h.what);
        assertNull(WebFilter.match(List.of(r), s, at(12), "example.com", List.of("apuestasfacil")));
    }

    @Test
    public void disabledPausedOrOutsideScheduleDoNotFilterButLimitsDont() {
        BlockRule r = web("a", List.of("x.com"), List.of());
        r.limitMinutes = 60;
        RuleState s = new RuleState();
        assertTrue(
                WebFilter.active(r, s, at(12))); // con minutos libres los sitios siguen bloqueados
        assertTrue(WebFilter.anyFilter(List.of(r), s, at(12)));
        assertFalse(WebFilter.needsText(List.of(r), s, at(12)));
        r.enabled = false;
        assertNull(WebFilter.match(List.of(r), s, at(12), "x.com", null));
        r.enabled = true;
        assertTrue(
                RuleEngine.pause(r, s, RuleEngine.millis(at(12)), at(12).toLocalDate().toString()));
        assertNull(WebFilter.match(List.of(r), s, at(12), "x.com", null));
        assertNotNull(WebFilter.match(List.of(r), s, at(13), "x.com", null)); // la pausa terminó
        r.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
        r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60));
        assertNull(WebFilter.match(List.of(r), s, at(14), "x.com", null));
        assertNotNull(WebFilter.match(List.of(r), s, at(23), "x.com", null));
    }

    @Test
    public void unsupportedBrowsersAndSupportedList() {
        BlockRule r = web("a", List.of(), List.of());
        RuleState s = new RuleState();
        assertNull(WebFilter.unsupportedBlocker(List.of(r), s, at(12)));
        r.blockUnsupportedBrowsers = true;
        assertSame(r, WebFilter.unsupportedBlocker(List.of(r), s, at(12)));
        assertTrue(WebFilter.supported("com.android.chrome"));
        assertTrue(WebFilter.supported("com.duckduckgo.mobile.android"));
        assertFalse(WebFilter.supported("com.example.browser"));
        assertEquals(7, WebFilter.BROWSERS.size());
    }

    @Test
    public void webOnlyRuleDoesNotBlockApps() {
        BlockRule r = web("a", List.of("x.com"), List.of());
        RuleState s = new RuleState();
        assertFalse(RuleEngine.active(r, s, at(12)));
        assertFalse(
                RuleEngine.decide(List.of(r), s, "com.android.chrome", at(12), null, 60_000)
                        .blocked);
    }
}
