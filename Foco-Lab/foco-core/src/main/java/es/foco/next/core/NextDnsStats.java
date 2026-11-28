package es.foco.next.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estadísticas de NextDNS (API oficial, api.nextdns.io) sin librerías: URLs, lectura de las dos
 * respuestas que usa Foco y la frase del búho. Lógica pura para poder probarla.
 */
public final class NextDnsStats {
    private NextDnsStats() {}

    public static final String API = "https://api.nextdns.io/profiles/";

    /** Como mucho una lectura cada 30 min (la API tiene límite y no hace falta más). */
    public static final long REFRESH_MS = 30 * 60_000L;

    public static final class Hit {
        public final String domain;
        public final long queries;

        public Hit(String domain, long queries) {
            this.domain = domain;
            this.queries = queries;
        }
    }

    /** Clave de API de NextDNS: letras y números, sin espacios. */
    public static boolean validKey(String key) {
        return key != null && key.trim().matches("[A-Za-z0-9]{16,80}");
    }

    public static boolean validProfile(String id) {
        return id != null && id.matches("[a-z0-9]{4,12}");
    }

    /** Consultas por estado desde {@code fromIso} (p. ej. el inicio del día en UTC). */
    public static String statusUrl(String profile, String fromIso) {
        return API + profile + "/analytics/status?from=" + fromIso;
    }

    /** Dominios bloqueados más consultados. */
    public static String blockedDomainsUrl(String profile, String fromIso, int limit) {
        return API
                + profile
                + "/analytics/domains?status=blocked&from="
                + fromIso
                + "&limit="
                + limit;
    }

    private static final Pattern STATUS =
            Pattern.compile(
                    "\\{[^{}]*\"status\"\\s*:\\s*\"([a-z]+)\"[^{}]*\"queries\"\\s*:\\s*(\\d+)[^{}]*\\}|\\{[^{}]*\"queries\"\\s*:\\s*(\\d+)[^{}]*\"status\"\\s*:\\s*\"([a-z]+)\"[^{}]*\\}");
    private static final Pattern DOMAIN =
            Pattern.compile(
                    "\\{[^{}]*\"domain\"\\s*:\\s*\"([^\"]+)\"[^{}]*\"queries\"\\s*:\\s*(\\d+)[^{}]*\\}|\\{[^{}]*\"queries\"\\s*:\\s*(\\d+)[^{}]*\"domain\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}");

    /** Consultas con un estado ("blocked", "default", "allowed"...). */
    public static long queries(String statusJson, String status) {
        if (statusJson == null) return 0;
        long n = 0;
        Matcher m = STATUS.matcher(statusJson);
        while (m.find()) {
            String s = m.group(1) != null ? m.group(1) : m.group(4);
            String q = m.group(2) != null ? m.group(2) : m.group(3);
            if (status.equals(s)) n += Long.parseLong(q);
        }
        return n;
    }

    public static long total(String statusJson) {
        if (statusJson == null) return 0;
        long n = 0;
        Matcher m = STATUS.matcher(statusJson);
        while (m.find()) n += Long.parseLong(m.group(2) != null ? m.group(2) : m.group(3));
        return n;
    }

    public static List<Hit> domains(String json, int max) {
        List<Hit> out = new ArrayList<>();
        if (json == null) return out;
        Matcher m = DOMAIN.matcher(json);
        while (m.find() && out.size() < max) {
            String d = m.group(1) != null ? m.group(1) : m.group(4);
            String q = m.group(2) != null ? m.group(2) : m.group(3);
            out.add(new Hit(d, Long.parseLong(q)));
        }
        return out;
    }

    /** "tiktok.com|34;instagram.com|12" ↔ lista (para guardarlo en preferencias). */
    public static String encode(List<Hit> hits) {
        StringBuilder b = new StringBuilder();
        for (Hit h : hits) {
            if (b.length() > 0) b.append(';');
            b.append(h.domain.replace("|", "").replace(";", "")).append('|').append(h.queries);
        }
        return b.toString();
    }

    public static List<Hit> decode(String s) {
        List<Hit> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) {
            int i = part.lastIndexOf('|');
            if (i <= 0) continue;
            try {
                out.add(new Hit(part.substring(0, i), Long.parseLong(part.substring(i + 1))));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    /**
     * Lo que dice el búho. Sin números si la persona los ocultó. Null si no hay nada que contar.
     */
    public static String owlLine(long blocked, List<Hit> top, boolean numbers) {
        if (blocked <= 0) return null;
        String first =
                top == null || top.isEmpty() ? null : top.get(0).domain.toLowerCase(Locale.ROOT);
        if (numbers)
            return "🦉 NextDNS frenó "
                    + blocked
                    + (blocked == 1 ? " intento" : " intentos")
                    + " hoy"
                    + (first == null ? "." : ", sobre todo " + first + ".");
        return "🦉 NextDNS te cuidó hoy" + (first == null ? "." : ", sobre todo de " + first + ".");
    }
}
