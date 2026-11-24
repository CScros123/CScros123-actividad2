package es.foco.next.core;

import java.util.Locale;

/**
 * Fase 3: decide si el DNS privado de Android cumple el proveedor elegido. Desde la v26 el único
 * proveedor es NextDNS (tu perfil exacto); "automático" solo existe si lo pides y tarda 24 h. Java puro: no lee la
 * red, solo recibe lo que Android informó (LinkProperties) y el reloj. Nunca toca FocusEngine ni el
 * presupuesto.
 *
 * <p>Reglas anti falsos positivos: sin red activa = OK; sin vigilancia activada = OK; 60 s de
 * margen antes de bloquear; el nombre del servidor se compara sin mayúsculas, espacios ni punto
 * final.
 */
public final class DnsGuard {
    public static final long GRACE = 60_000L;
    /** Sin perfil todavía: vale cualquier perfil de NextDNS hasta que Foco adopta el primero que ve. */
    public static final String DEFAULT_PROVIDER = "";

    /**
     * DNS privado en automático (sin filtro), elegido por ti tras un fallo de NextDNS. Siempre
     * llega con 24 h de espera; volver a NextDNS es inmediato.
     */
    public static final String AUTOMATIC = "automatico";

    /**
     * NextDNS: cada perfil tiene su propio host ({@code <id>.dns.nextdns.io}, o
     * {@code <dispositivo>-<id>.dns.nextdns.io}). Se acepta el host EXACTO del perfil elegido, no
     * cualquier perfil de NextDNS: cambiar a otro perfil (quizá sin filtros) cuenta como DNS
     * incorrecto. Foco no puede ver qué filtra el perfil: eso se configura en my.nextdns.io.
     */
    public static final String NEXTDNS_SUFFIX = ".dns.nextdns.io";

    public static boolean nextDns(String host) {
        String h = normalize(host);
        if (!h.endsWith(NEXTDNS_SUFFIX)) return false;
        String label = h.substring(0, h.length() - NEXTDNS_SUFFIX.length());
        return label.matches("([a-z0-9-]{1,63}-)?[a-z0-9]{4,12}");
    }

    /** Id del perfil (lo que va tras el último guion), para mostrarlo. */
    public static String nextDnsProfile(String host) {
        String label = normalize(host).replace(NEXTDNS_SUFFIX, "");
        int cut = label.lastIndexOf('-');
        return cut >= 0 ? label.substring(cut + 1) : label;
    }

    /** Navegadores conocidos que se bloquean junto con el ocio cuando el DNS no cumple. */
    public static final String[] BROWSERS = {
        "com.android.chrome",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.duckduckgo.mobile.android"
    };

    public enum Verdict {
        OK,
        GRACE,
        BLOCK
    }

    /** Lo observado. {@code server} es null o vacío cuando Android usa el modo automático. */
    public static final class State {
        public boolean required, network, privateDnsActive;
        public String server = "", expected = DEFAULT_PROVIDER;

        public State() {}

        public State(
                boolean required,
                boolean network,
                boolean privateDnsActive,
                String server,
                String expected) {
            this.required = required;
            this.network = network;
            this.privateDnsActive = privateDnsActive;
            this.server = server;
            this.expected = expected;
        }
    }

    public static final class Decision {
        public final Verdict verdict;

        /**
         * Último instante en que se considera que el DNS estaba bien (el llamador lo guarda para la
         * siguiente llamada).
         */
        public final long lastOk;

        /** Milisegundos hasta volver a evaluar (solo en GRACE); 0 si no hace falta. */
        public final long recheck;

        Decision(Verdict verdict, long lastOk, long recheck) {
            this.verdict = verdict;
            this.lastOk = lastOk;
            this.recheck = recheck;
        }

        public boolean blocked() {
            return verdict == Verdict.BLOCK;
        }
    }

    private DnsGuard() {}

    public static String normalize(String host) {
        String h = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        return h;
    }

    /**
     * Solo un perfil de NextDNS (host exacto) o {@link #AUTOMATIC}; cualquier otro valor (también
     * los antiguos CleanBrowsing y AdGuard) queda "sin perfil".
     */
    public static String provider(String host) {
        String h = normalize(host);
        if (h.equals(AUTOMATIC)) return AUTOMATIC;
        if (nextDns(h)) return h;
        return DEFAULT_PROVIDER;
    }

    public static boolean automatic(String host) {
        return AUTOMATIC.equals(provider(host));
    }

    public static String providerLabel(String host) {
        String p = provider(host);
        if (nextDns(p)) return "NextDNS (perfil " + nextDnsProfile(p) + ")";
        return p.equals(AUTOMATIC) ? "Automático (sin filtro)" : "NextDNS (sin perfil todavía)";
    }

    public static boolean sameHost(String server, String expected) {
        String s = normalize(server), e = normalize(expected);
        return !s.isEmpty() && s.equals(e);
    }

    public static boolean browser(String pkg) {
        if (pkg == null) return false;
        for (String b : BROWSERS) if (b.equals(pkg)) return true;
        return false;
    }

    /**
     * Cumple si no se exige, si no hay red, o si el DNS privado está activo con el proveedor
     * elegido.
     */
    public static boolean compliant(State s) {
        if (s == null || !s.required || !s.network) return true;
        String e = provider(s.expected);
        if (e.equals(AUTOMATIC)) return true;
        if (!s.privateDnsActive) return false;
        // Sin perfil todavía: cualquier perfil de NextDNS (Foco adopta el primero que ve).
        if (e.isEmpty()) return nextDns(s.server);
        return sameHost(s.server, e);
    }

    /**
     * @param now reloj monotónico (ms)
     * @param lastOk último instante conocido con DNS correcto; negativo si se desconoce (empieza el
     *     margen ahora)
     */
    public static Decision decide(State s, long now, long lastOk) {
        if (compliant(s)) return new Decision(Verdict.OK, now, 0);
        long since = lastOk < 0 || lastOk > now ? now : lastOk;
        long bad = now - since;
        if (bad < GRACE) return new Decision(Verdict.GRACE, since, GRACE - bad);
        return new Decision(Verdict.BLOCK, since, 0);
    }

    /**
     * Memoria mínima entre llamadas. El margen empieza cuando se ve el primer estado incorrecto (no
     * cuando se vio el último correcto, que puede ser antiguo), y se reinicia al activar la
     * vigilancia o cambiar de proveedor para que haya tiempo de ajustar Android.
     */
    public static final class Tracker {
        private long lastOk = -1;
        private boolean wasCompliant = true, required;
        private String expected = "";

        public synchronized Decision decide(State s, long now) {
            boolean req = s != null && s.required;
            String exp = s == null ? "" : provider(s.expected);
            if (wasCompliant || now < lastOk || (req && (!required || !exp.equals(expected))))
                lastOk = now;
            required = req;
            expected = exp;
            Decision d = DnsGuard.decide(s, now, lastOk);
            lastOk = d.lastOk;
            wasCompliant = d.verdict == Verdict.OK;
            return d;
        }
    }
}
