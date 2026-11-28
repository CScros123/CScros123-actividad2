package es.foco.next.core.backup;

import es.foco.next.core.care.CareMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * v47: lo que Foco quiere en el perfil de NextDNS según el modo y la hora, y los cambios para
 * llegar a eso. Solo dos cosas: las redes sociales del control parental (en Enfoque y de noche) y
 * Telegram en la lista de bloqueo (siempre). No hay "desbloquear ahora": lo deciden el modo y el
 * horario. Si alguien lo cambia desde la web, Foco lo vuelve a poner como debe y lo apunta, sin
 * culpa.
 */
public final class NextDnsPlan {
    private NextDnsPlan() {}

    /** Cada cuánto se compara lo real con lo esperado. */
    public static final long CHECK_EVERY_MS = 30 * 60_000L;

    /** Servicios del control parental de NextDNS que se activan en Enfoque y de noche. */
    public static final List<String> SERVICES =
            List.of(
                    "tiktok",
                    "instagram",
                    "facebook",
                    "youtube",
                    "reddit",
                    "pinterest",
                    "snapchat",
                    "twitter");

    /** Telegram: en la lista de bloqueo siempre. */
    public static final List<String> TELEGRAM =
            List.of("telegram.org", "t.me", "telegram.me", "web.telegram.org");

    /** Un cambio en el perfil. ADD = POST {id, active:true}; REMOVE = DELETE …/id. */
    public static final class Op {
        public enum Kind {
            ADD,
            REMOVE
        }

        public final Kind kind;
        public final String list; // "parentalControl/services" o "denylist"
        public final String id;

        Op(Kind kind, String list, String id) {
            this.kind = kind;
            this.list = list;
            this.id = id;
        }

        @Override
        public String toString() {
            return kind + " " + list + "/" + id;
        }
    }

    public static final String SERVICES_PATH = "parentalControl/services";
    public static final String DENYLIST_PATH = "denylist";

    /**
     * v50: de noche, además de los 8 servicios, la categoría «redes sociales» del control parental
     * (Threads, Bluesky, Kwai… y los dominios de rastreo y avisos propios de cada red).
     */
    public static final String CATEGORIES_PATH = "parentalControl/categories";

    public static final String NIGHT_CATEGORY = "social-networks";

    /** WhatsApp es esencial: nunca lo corta la categoría de redes (la lista de permitidos manda). */
    public static final String ALLOWLIST_PATH = "allowlist";

    public static final List<String> ESSENTIAL_ALLOW =
            List.of("whatsapp.com", "whatsapp.net", "wa.me");

    /** ¿Se bloquean las redes por servicio? Solo en Enfoque o de noche. */
    public static boolean blockServices(CareMode.Modo mode, boolean night) {
        return night || mode == CareMode.Modo.ENFOQUE;
    }

    /** Firma de lo esperado (para saber si un desajuste vino de fuera o de un cambio de modo). */
    public static String signature(boolean block) {
        return block ? "servicios=si;telegram=si" : "servicios=no;telegram=si";
    }

    /** v50: con la categoría de redes de la noche. */
    public static String signature(boolean block, boolean night) {
        return signature(block) + (night ? ";noche=si" : "");
    }

    /**
     * Cambios para que el perfil quede como se espera.
     *
     * @param services lo que hay ahora en el control parental: id → activo
     * @param denylist lo que hay ahora en la lista de bloqueo: dominio → activo
     */
    public static List<Op> diff(
            boolean block, Map<String, Boolean> services, Map<String, Boolean> denylist) {
        List<Op> ops = new ArrayList<>();
        for (String id : SERVICES) {
            Boolean active = services == null ? null : services.get(id);
            if (block) {
                // Inactivo: se quita y se vuelve a poner activo (la API no necesita PATCH así).
                if (Boolean.FALSE.equals(active))
                    ops.add(new Op(Op.Kind.REMOVE, SERVICES_PATH, id));
                if (!Boolean.TRUE.equals(active)) ops.add(new Op(Op.Kind.ADD, SERVICES_PATH, id));
            } else if (Boolean.TRUE.equals(active))
                ops.add(new Op(Op.Kind.REMOVE, SERVICES_PATH, id));
        }
        for (String d : TELEGRAM) {
            Boolean active = denylist == null ? null : denylist.get(d);
            if (Boolean.FALSE.equals(active)) ops.add(new Op(Op.Kind.REMOVE, DENYLIST_PATH, d));
            if (!Boolean.TRUE.equals(active)) ops.add(new Op(Op.Kind.ADD, DENYLIST_PATH, d));
        }
        return ops;
    }

    /**
     * El perfil no estaba como Foco lo dejó: lo esperado no cambió desde la última vez que se
     * aplicó bien y, aun así, hay que corregir algo. Eso es un cambio hecho desde fuera.
     */
    public static boolean changedOutside(List<Op> ops, String lastApplied, boolean block) {
        return ops != null && !ops.isEmpty() && signature(block).equals(lastApplied);
    }

    public static boolean changedOutside(
            List<Op> ops, String lastApplied, boolean block, boolean night) {
        return ops != null && !ops.isEmpty() && signature(block, night).equals(lastApplied);
    }

    /**
     * v50: lo de {@link #diff(boolean, Map, Map)} más la noche: la categoría «redes sociales»
     * activa de noche (y quitada de día) y WhatsApp en la lista de permitidos mientras tanto.
     */
    public static List<Op> diff(
            boolean block,
            boolean night,
            Map<String, Boolean> services,
            Map<String, Boolean> categories,
            Map<String, Boolean> denylist,
            Map<String, Boolean> allowlist) {
        List<Op> ops = diff(block, services, denylist);
        Boolean cat = categories == null ? null : categories.get(NIGHT_CATEGORY);
        if (night) {
            for (String d : ESSENTIAL_ALLOW) {
                Boolean a = allowlist == null ? null : allowlist.get(d);
                if (Boolean.FALSE.equals(a)) ops.add(new Op(Op.Kind.REMOVE, ALLOWLIST_PATH, d));
                if (!Boolean.TRUE.equals(a)) ops.add(new Op(Op.Kind.ADD, ALLOWLIST_PATH, d));
            }
            if (Boolean.FALSE.equals(cat))
                ops.add(new Op(Op.Kind.REMOVE, CATEGORIES_PATH, NIGHT_CATEGORY));
            if (!Boolean.TRUE.equals(cat))
                ops.add(new Op(Op.Kind.ADD, CATEGORIES_PATH, NIGHT_CATEGORY));
        } else if (Boolean.TRUE.equals(cat))
            ops.add(new Op(Op.Kind.REMOVE, CATEGORIES_PATH, NIGHT_CATEGORY));
        return ops;
    }

    /**
     * Próximo momento en que cambia lo esperado: el final del Enfoque, el inicio o el final de la
     * noche (horas enteras, como AbsoluteRules). En epoch ms.
     */
    public static long nextBoundary(
            ZonedDateTime now, long focusUntil, int nightStart, int nightEnd) {
        ZoneId z = now.getZone();
        LocalDateTime t = now.toLocalDateTime();
        LocalDateTime start = t.toLocalDate().atTime(nightStart, 0);
        LocalDateTime end = t.toLocalDate().atTime(nightEnd, 0);
        if (!start.isAfter(t)) start = start.plusDays(1);
        if (!end.isAfter(t)) end = end.plusDays(1);
        long best =
                Math.min(
                        start.atZone(z).toInstant().toEpochMilli(),
                        end.atZone(z).toInstant().toEpochMilli());
        long n = now.toInstant().toEpochMilli();
        if (focusUntil > n && focusUntil < best) best = focusUntil;
        return best;
    }

    /** ¿Es de noche a esa hora con ese horario? (misma regla que AbsoluteRules). */
    public static boolean night(long at, ZoneId z, int nightStart, int nightEnd) {
        int h = Instant.ofEpochMilli(at).atZone(z).getHour();
        return h >= nightStart || h < nightEnd;
    }

    // ---------------- Lectura del día para el reporte ----------------

    private static final String[][] SOCIAL = {
        {
            "tiktok",
            "tiktok.com",
            "tiktokv.com",
            "tiktokcdn.com",
            "byteoversea.com",
            "ibytedtos.com"
        },
        {"instagram", "instagram.com", "cdninstagram.com"},
        {"facebook", "facebook.com", "facebook.net", "fbcdn.net", "fb.com", "messenger.com"},
        {"youtube", "youtube.com", "youtu.be", "ytimg.com", "googlevideo.com"},
        {"reddit", "reddit.com", "redd.it", "redditmedia.com", "redditstatic.com"},
        {"pinterest", "pinterest.com", "pinimg.com"},
        {"snapchat", "snapchat.com", "sc-cdn.net", "snapkit.com"},
        {"twitter", "twitter.com", "x.com", "twimg.com", "t.co"},
    };

    /** Red social a la que pertenece un dominio, o null. */
    public static String network(String domain) {
        if (domain == null) return null;
        String d = domain.toLowerCase(Locale.ROOT);
        for (String[] s : SOCIAL)
            for (int i = 1; i < s.length; i++)
                if (d.equals(s[i]) || d.endsWith("." + s[i])) return s[0];
        return null;
    }

    /** Consultas permitidas por red social (todas las redes, con 0 si no hubo). */
    public static Map<String, Long> socialActivity(List<DayReport.Item> allowedRoots) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String[] s : SOCIAL) out.put(s[0], 0L);
        if (allowedRoots != null)
            for (DayReport.Item it : allowedRoots) {
                String n = network(it.name);
                if (n != null) out.merge(n, it.value, Long::sum);
            }
        return out;
    }

    // Sufijos de dos partes frecuentes (co.uk, com.mx…): el dominio raíz lleva una parte más.
    private static final List<String> TWO_PART =
            List.of(
                    "co.uk", "com.mx", "com.ar", "com.br", "com.co", "com.pe", "com.es", "co.jp",
                    "com.au", "co.in", "org.uk", "com.ve", "com.cl", "gob.mx", "com.ec");

    /** "a.b.tracker.com" → "tracker.com"; "x.bbc.co.uk" → "bbc.co.uk". */
    public static String rootDomain(String domain) {
        if (domain == null) return "";
        String d = domain.toLowerCase(Locale.ROOT).trim();
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        String[] p = d.split("\\.");
        if (p.length <= 2) return d;
        String last2 = p[p.length - 2] + "." + p[p.length - 1];
        int keep = TWO_PART.contains(last2) ? 3 : 2;
        StringBuilder b = new StringBuilder();
        for (int i = Math.max(0, p.length - keep); i < p.length; i++)
            b.append(b.length() == 0 ? "" : ".").append(p[i]);
        return b.toString();
    }

    /** Agrupa por dominio raíz y deja los {@code max} con más bloqueos. */
    public static List<DayReport.Item> topRoots(List<DayReport.Item> domains, int max) {
        Map<String, Long> acc = new LinkedHashMap<>();
        if (domains != null)
            for (DayReport.Item it : domains) {
                String r = rootDomain(it.name);
                if (!r.isEmpty()) acc.merge(r, it.value, Long::sum);
            }
        List<DayReport.Item> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : acc.entrySet())
            out.add(new DayReport.Item(e.getKey(), e.getValue()));
        out.sort((a, b) -> Long.compare(b.value, a.value));
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
