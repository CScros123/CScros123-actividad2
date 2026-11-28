package es.foco.next.core;

import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Lote 3: filtro web puro. Normaliza lo que muestra la barra de direcciones de un navegador y lo
 * compara con los sitios (dominio y subdominios, con prefijo de ruta opcional) y palabras clave
 * (sin mayúsculas ni tildes, palabra completa) de los bloqueos.
 */
public final class WebFilter {
    private WebFilter() {}

    public static final int MAX_SITES = 100, MAX_KEYWORDS = 50;

    /**
     * Navegadores compatibles y los ID de su barra de direcciones (el primero que exista manda).
     */
    public static final Map<String, List<String>> BROWSERS;

    static {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("com.android.chrome", List.of("com.android.chrome:id/url_bar"));
        m.put(
                "com.sec.android.app.sbrowser",
                List.of(
                        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                        "com.sec.android.app.sbrowser:id/url_bar"));
        m.put(
                "org.mozilla.firefox",
                List.of(
                        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                        "org.mozilla.firefox:id/url_bar_title"));
        m.put("com.brave.browser", List.of("com.brave.browser:id/url_bar"));
        m.put("com.microsoft.emmx", List.of("com.microsoft.emmx:id/url_bar"));
        m.put("com.opera.browser", List.of("com.opera.browser:id/url_field"));
        m.put(
                "com.duckduckgo.mobile.android",
                List.of("com.duckduckgo.mobile.android:id/omnibarTextInput"));
        BROWSERS = Collections.unmodifiableMap(m);
    }

    public static boolean supported(String pkg) {
        return pkg != null && BROWSERS.containsKey(pkg);
    }

    /**
     * "https://M.YouTube.com:443/Shorts/abc?x=1#y" → "youtube.com/shorts/abc". Null si no parece
     * una dirección (texto de búsqueda, vacío, con espacios o sin punto en el dominio).
     */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        int scheme = s.indexOf("://");
        if (scheme > 0 && scheme < 12 && s.substring(0, scheme).matches("[a-z][a-z0-9+.-]*"))
            s = s.substring(scheme + 3);
        int cut = firstOf(s, "?#");
        if (cut >= 0) s = s.substring(0, cut);
        int slash = s.indexOf('/');
        String host = slash < 0 ? s : s.substring(0, slash),
                path = slash < 0 ? "" : s.substring(slash);
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        int colon = host.indexOf(':');
        if (colon >= 0) host = host.substring(0, colon);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        while (host.startsWith("www.") || host.startsWith("m."))
            host = host.substring(host.indexOf('.') + 1);
        if (host.isEmpty()
                || host.indexOf('.') < 0
                || !host.matches("[a-z0-9.-]+")
                || host.startsWith(".")
                || host.contains("..")) return null;
        if (path.chars().anyMatch(Character::isWhitespace)) return null;
        while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return host + path;
    }

    /**
     * Normaliza una entrada escrita por el usuario ("youtube.com/shorts", "https://www.x.com/").
     * Null si no vale.
     */
    public static String site(String entry) {
        return normalize(entry);
    }

    static int firstOf(String s, String chars) {
        int best = -1;
        for (char c : chars.toCharArray()) {
            int i = s.indexOf(c);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    static String hostOf(String norm) {
        int i = norm.indexOf('/');
        return i < 0 ? norm : norm.substring(0, i);
    }

    static String pathOf(String norm) {
        int i = norm.indexOf('/');
        return i < 0 ? "" : norm.substring(i);
    }

    /**
     * La dirección cae en el sitio: mismo dominio o subdominio y, si el sitio tiene ruta, la ruta
     * empieza por ella (por segmentos).
     */
    public static boolean matchesSite(String url, String site) {
        String u = normalize(url), s = normalize(site);
        if (u == null || s == null) return false;
        String uh = hostOf(u), sh = hostOf(s);
        if (!(uh.equals(sh) || uh.endsWith("." + sh))) return false;
        String sp = pathOf(s);
        if (sp.isEmpty()) return true;
        String up = pathOf(u);
        return up.equals(sp) || up.startsWith(sp + "/");
    }

    /** Minúsculas y sin tildes ("PELÍCULA" → "pelicula"). */
    public static String fold(String text) {
        if (text == null) return "";
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }

    /** Palabra clave limpia o null (al menos 2 letras o cifras). */
    public static String keyword(String k) {
        String f = fold(k).trim().replaceAll("\\s+", " ");
        int alnum = 0;
        for (char c : f.toCharArray()) if (Character.isLetterOrDigit(c)) alnum++;
        return alnum < 2 || f.length() > 40 ? null : f;
    }

    /**
     * Palabra completa: no vale "tel" dentro de "hotel" ni de "telefono". Separadores de URL (. / -
     * _) cuentan como límite.
     */
    public static boolean matchesKeyword(String text, String keyword) {
        String k = keyword(keyword);
        if (k == null || text == null) return false;
        String t = fold(text);
        for (int i = t.indexOf(k); i >= 0; i = t.indexOf(k, i + 1)) {
            boolean before = i == 0 || !Character.isLetterOrDigit(t.charAt(i - 1));
            int end = i + k.length();
            boolean after = end >= t.length() || !Character.isLetterOrDigit(t.charAt(end));
            if (before && after) return true;
        }
        return false;
    }

    public static final class Hit {
        public final BlockRule rule;
        public final String what;

        Hit(BlockRule rule, String what) {
            this.rule = rule;
            this.what = what;
        }
    }

    /**
     * Lote 3: los sitios/palabras de la regla se aplican: activada, sin pausa y (sin horario o
     * dentro de su franja). Límites no cuentan.
     */
    public static boolean active(BlockRule r, RuleState s, ZonedDateTime now) {
        if (r == null || !r.enabled || !r.web()) return false;
        RuleState.Entry e = s.of(r.id);
        RuleEngine.roll(r, e, RuleEngine.millis(now), now.toLocalDate().toString());
        if (RuleEngine.paused(e, RuleEngine.millis(now))) return false;
        return RuleEngine.inWindow(r, now);
    }

    /**
     * Primera coincidencia entre las reglas activas: sitio en la dirección; si no, palabra clave en
     * la dirección o en los textos visibles. {@code what} es el dominio (o la palabra) que se
     * mostrará. Null si nada coincide.
     */
    public static Hit match(
            List<BlockRule> rules,
            RuleState s,
            ZonedDateTime now,
            String url,
            Collection<String> texts) {
        String u = normalize(url);
        for (BlockRule r : rules) {
            if (!active(r, s, now)) continue;
            if (u != null)
                for (String site : r.sites) if (matchesSite(u, site)) return new Hit(r, hostOf(u));
            for (String k : r.keywords) {
                if (url != null && matchesKeyword(url, k))
                    return new Hit(r, u != null ? hostOf(u) : "«" + k + "»");
                if (texts != null)
                    for (String t : texts)
                        if (matchesKeyword(t, k))
                            return new Hit(r, u != null ? hostOf(u) : "«" + k + "»");
            }
        }
        return null;
    }

    /** Alguna regla activa necesita leer textos de la página (tiene palabras clave). */
    public static boolean needsText(List<BlockRule> rules, RuleState s, ZonedDateTime now) {
        for (BlockRule r : rules) if (!r.keywords.isEmpty() && active(r, s, now)) return true;
        return false;
    }

    /** Alguna regla activa filtra sitios o palabras (para no leer nada si no hace falta). */
    public static boolean anyFilter(List<BlockRule> rules, RuleState s, ZonedDateTime now) {
        for (BlockRule r : rules)
            if ((!r.sites.isEmpty() || !r.keywords.isEmpty()) && active(r, s, now)) return true;
        return false;
    }

    /** Regla activa que bloquea navegadores no compatibles, o null. */
    public static BlockRule unsupportedBlocker(
            List<BlockRule> rules, RuleState s, ZonedDateTime now) {
        for (BlockRule r : rules) if (r.blockUnsupportedBrowsers && active(r, s, now)) return r;
        return null;
    }

    /** Limpia listas: normaliza, quita inválidos y duplicados, limita tamaño. */
    static void clean(List<String> sites, List<String> keywords) {
        LinkedHashSet<String> a = new LinkedHashSet<>();
        for (String x : sites) {
            String n = site(x);
            if (n != null) a.add(n);
        }
        sites.clear();
        for (String x : a) if (sites.size() < MAX_SITES) sites.add(x);
        LinkedHashSet<String> b = new LinkedHashSet<>();
        for (String x : keywords) {
            String n = keyword(x);
            if (n != null) b.add(n);
        }
        keywords.clear();
        for (String x : b) if (keywords.size() < MAX_KEYWORDS) keywords.add(x);
    }
}
