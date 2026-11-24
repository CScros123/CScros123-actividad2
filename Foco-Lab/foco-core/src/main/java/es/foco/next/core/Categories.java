package es.foco.next.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Foco 1.0, etapa 2 (docs/FOCO-1.0-DEFINICION.md §2): categorías de apps. Lógica pura y testeable.
 *
 * <ul>
 *   <li>Bloqueo total: Telegram y sus clientes, y sus dominios. Siempre, sin excepciones.
 *   <li>Esencial: {@link FocusEngine#essential}. Productiva aprobada: {@link
 *       AppConfig#productiveApps}, sin límite.
 *   <li>En observación ({@link AppConfig#observedApps}) y ocio ({@link AppConfig#watchedApps}):
 *       mismo límite de 5 minutos.
 * </ul>
 *
 * Regla asimétrica: endurecer puede ser automático ({@link #hardenReason}); aflojar solo se propone
 * ({@link #canApprove}).
 */
public final class Categories {
    private Categories() {}

    public static final Set<String> TOTAL_PACKAGES =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "org.telegram.messenger",
                                    "org.telegram.messenger.web",
                                    "org.telegram.messenger.beta",
                                    "org.thunderdog.challegram",
                                    "org.telegram.plus",
                                    "tw.nekomimi.nekogram")));
    public static final List<String> TOTAL_DOMAINS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "telegram.org",
                            "t.me",
                            "telegram.me",
                            "telegram.dog",
                            "telegra.ph",
                            "telesco.pe",
                            "tg.dev",
                            "cdn-telegram.org",
                            "telegram-cdn.org"));

    /**
     * Paquete en bloqueo total: está en la lista o su nombre contiene "telegram" (clientes nuevos
     * entran directos).
     */
    public static boolean totalBlock(String pkg) {
        if (pkg == null || pkg.isEmpty()) return false;
        return TOTAL_PACKAGES.contains(pkg) || pkg.toLowerCase(Locale.ROOT).contains("telegram");
    }

    /**
     * Dirección (como sale en la barra del navegador, con o sin https://) de un dominio en bloqueo
     * total o un subdominio suyo.
     */
    public static boolean totalDomain(String url) {
        return matches(url, TOTAL_DOMAINS);
    }

    /**
     * Host de una dirección de la barra del navegador (con o sin esquema), en minúsculas; "" si no
     * parece un dominio.
     */
    public static String host(String url) {
        if (url == null) return "";
        String u = url.trim().toLowerCase(Locale.ROOT);
        int scheme = u.indexOf("://");
        if (scheme >= 0) u = u.substring(scheme + 3);
        int end = u.length();
        for (int i = 0; i < u.length(); i++) {
            char ch = u.charAt(i);
            if (ch == '/' || ch == '?' || ch == '#' || ch == ':' || ch == ' ') {
                end = i;
                break;
            }
        }
        String host = u.substring(0, end);
        int at = host.lastIndexOf('@');
        if (at >= 0) host = host.substring(at + 1);
        return host.isEmpty() || host.indexOf('.') < 0 ? "" : host;
    }

    /** La dirección es uno de esos dominios o un subdominio suyo. */
    public static boolean matches(String url, List<String> domains) {
        String host = host(url);
        if (host.isEmpty()) return false;
        for (String d : domains) if (host.equals(d) || host.endsWith("." + d)) return true;
        return false;
    }

    /**
     * Con el mismo límite de 5 minutos: ocio o en observación (nunca esenciales ni la alternativa
     * elegida).
     */
    public static boolean limited(AppConfig c, String pkg) {
        return FocusEngine.watched(c, pkg);
    }

    /**
     * Nunca pueden aprobarse como productivas: bloqueo total, redes sociales, YouTube y navegadores
     * ({@code browser}).
     */
    public static boolean neverProductive(String pkg, boolean browser) {
        if (pkg == null || pkg.isEmpty()) return true;
        String p = pkg.toLowerCase(Locale.ROOT);
        return browser
                || totalBlock(pkg)
                || AbsoluteRules.social(pkg)
                || AlternativeCatalog.social(pkg)
                || p.contains("youtube")
                || p.contains("tiktok")
                || p.contains("reddit")
                || p.contains("pinterest")
                || p.contains("twitter")
                || p.contains("snapchat")
                || p.contains("instagram")
                || p.contains("facebook");
    }

    /** Uso de una app en un día: tiempo real y aperturas. */
    public static final class DayUse {
        public final long ms;
        public final long times;

        public DayUse(long ms, long times) {
            this.ms = ms;
            this.times = times;
        }
    }

    public static final long HARD_DAY_MS = 45 * 60_000L,
            GAME_DAY_MS = 20 * 60_000L,
            APPROVE_SESSION_MS = 3 * 60_000L;
    public static final int HARD_OPENS = 15, HARD_DAYS = 2, APPROVE_DAYS = 3;

    /**
     * Regla de endurecer sobre los últimos 7 días: motivo en español, o null si no se cumple
     * ninguna condición. Más de 45 min en un mismo día dos días o más; más de 15 aperturas en un
     * día; o un juego con 20 min o más en un día (aproximación de "sesiones de más de 20 minutos":
     * el log guarda tiempo por hora, no sesiones).
     */
    public static String hardenReason(List<DayUse> days, boolean game) {
        if (days == null) return null;
        int heavy = 0;
        for (DayUse d : days) {
            if (d == null) continue;
            if (d.times > HARD_OPENS) return "más de " + HARD_OPENS + " aperturas en un día";
            if (game && d.ms >= GAME_DAY_MS) return "es un juego con más de 20 minutos en un día";
            if (d.ms > HARD_DAY_MS) heavy++;
        }
        return heavy >= HARD_DAYS ? "más de 45 minutos al día en " + heavy + " días" : null;
    }

    /**
     * Condiciones para proponer pasar de observación a productiva: uso en 3 días o más, 3 min o más
     * por apertura de media y sin motivo para endurecer.
     */
    public static boolean canApprove(List<DayUse> days, boolean game) {
        if (days == null || hardenReason(days, game) != null) return false;
        int used = 0;
        long ms = 0, times = 0;
        for (DayUse d : days) {
            if (d == null || d.ms <= 0) continue;
            used++;
            ms += d.ms;
            times += Math.max(1, d.times);
        }
        return used >= APPROVE_DAYS && times > 0 && ms / times >= APPROVE_SESSION_MS;
    }
}
