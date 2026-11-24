package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import es.foco.next.core.AppConfig;
import es.foco.next.core.DnsGuard;
import es.foco.next.core.FocusEngine;

/**
 * Fase 3: vigila el DNS privado de Android con ACCESS_NETWORK_STATE (ya declarado). Solo lee
 * LinkProperties de la red por defecto; no abre conexiones ni consulta dominios. La decisión es de
 * {@link DnsGuard}. Cualquier error se trata como "sin dato" = no bloquear (nunca un falso
 * bloqueo). Preferencias propias en "foco-dns"; no se toca focus-lite-v1.json.
 */
public final class DnsMonitor {
    public static final String PREFS = "foco-dns";
    private static final String KEY_ARMED = "armed",
            KEY_PROVIDER = "provider",
            KEY_CHROME = "chrome_checked";
    private final Context context;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DnsGuard.Tracker tracker = new DnsGuard.Tracker();
    private ConnectivityManager.NetworkCallback callback;
    private volatile boolean network, active;
    private volatile String server = "";

    public DnsMonitor(Context context, Runnable changed) {
        this.context = context.getApplicationContext();
        this.changed = changed;
    }

    public void start() {
        read();
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            if (cm == null) return;
            callback =
                    new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network n) {
                            update(cm, n);
                        }

                        @Override
                        public void onLinkPropertiesChanged(Network n, LinkProperties p) {
                            apply(p);
                        }

                        @Override
                        public void onLost(Network n) {
                            network = false;
                            active = false;
                            server = "";
                            post();
                        }
                    };
            cm.registerDefaultNetworkCallback(callback, main);
        } catch (RuntimeException e) {
            callback = null;
            android.util.Log.w("Foco", "DNS privado sin vigilancia de red", e);
        }
    }

    public void stop() {
        main.removeCallbacksAndMessages(null);
        if (callback == null) return;
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            if (cm != null) cm.unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
        callback = null;
    }

    private void read() {
        try {
            ConnectivityManager cm = context.getSystemService(ConnectivityManager.class);
            Network n = cm == null ? null : cm.getActiveNetwork();
            if (n == null) {
                network = false;
                active = false;
                server = "";
                return;
            }
            network = true;
            LinkProperties p = cm.getLinkProperties(n);
            if (p != null) {
                active = p.isPrivateDnsActive();
                server = p.getPrivateDnsServerName() == null ? "" : p.getPrivateDnsServerName();
            }
        } catch (RuntimeException e) {
            network = false;
        }
    }

    private void update(ConnectivityManager cm, Network n) {
        network = true;
        try {
            LinkProperties p = cm.getLinkProperties(n);
            if (p != null) {
                apply(p);
                return;
            }
        } catch (RuntimeException ignored) {
        }
        post();
    }

    private void apply(LinkProperties p) {
        network = true;
        active = p.isPrivateDnsActive();
        server = p.getPrivateDnsServerName() == null ? "" : p.getPrivateDnsServerName();
        post();
    }

    private void post() {
        main.post(changed);
    }

    /** Nunca lanza: ante cualquier error devuelve OK. */
    public DnsGuard.Decision decide() {
        try {
            // Primera vez que Android usa el proveedor elegido: la vigilancia se activa sola, salvo
            // que el usuario la haya apagado.
            // v26: sin perfil todavía, el primer perfil de NextDNS que usa el teléfono es el tuyo.
            if (network && active && provider(context).isEmpty() && DnsGuard.nextDns(server))
                setProvider(context, server);
            if (network
                    && active
                    && DnsGuard.sameHost(server, provider(context))
                    && !prefs(context).contains(KEY_ARMED)) setArmed(context, true);
            DnsGuard.State s =
                    new DnsGuard.State(armed(context), network, active, server, provider(context));
            return tracker.decide(s, SystemClock.elapsedRealtime());
        } catch (RuntimeException e) {
            return DnsGuard.decide(null, SystemClock.elapsedRealtime(), -1);
        }
    }

    public String host() {
        return provider(context);
    }

    /**
     * Causa probable de que el DNS privado no esté como debe, para el incidente "dns_apagado":
     * vpn_activa, dns_privado_cambiado (otro servidor), dns_privado_automatico, dns_privado_apagado
     * u otra. Nunca lanza.
     */
    public static String cause(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            if (cm == null) return "otra";
            Network n = cm.getActiveNetwork();
            if (n == null) return "sin_red";
            android.net.NetworkCapabilities k = cm.getNetworkCapabilities(n);
            if (k != null && k.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN))
                return "vpn_activa";
            LinkProperties p = cm.getLinkProperties(n);
            if (p == null) return "otra";
            String server = p.getPrivateDnsServerName();
            String expected = provider(c);
            if (server != null
                    && !server.isEmpty()
                    && !(expected.isEmpty() ? DnsGuard.nextDns(server) : DnsGuard.sameHost(server, expected)))
                return "dns_privado_cambiado";
            if (p.isPrivateDnsActive() && (server == null || server.isEmpty()))
                return "dns_privado_automatico";
            if (!p.isPrivateDnsActive()) return "dns_privado_apagado";
            return "otra";
        } catch (RuntimeException e) {
            return "otra";
        }
    }

    /**
     * Apps que el DNS apagado bloquea: las de ocio del plan y los navegadores conocidos, nunca las
     * esenciales.
     */
    public static boolean target(AppConfig c, String pkg) {
        if (c == null
                || !c.enabled
                || pkg == null
                || pkg.isEmpty()
                || FocusEngine.essential(c, pkg)) return false;
        return FocusEngine.watched(c, pkg) || DnsGuard.browser(pkg);
    }

    // ----- Preferencias (Ajustes) -----
    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean armed(Context c) {
        return prefs(c).getBoolean(KEY_ARMED, false);
    }

    public static void setArmed(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_ARMED, on).apply();
    }

    public static String provider(Context c) {
        SharedPreferences p = prefs(c);
        // Cambio pedido con el candado del modo estricto: se aplica solo al cumplirse las 24 h.
        String pending = p.getString("pendingProvider", "");
        if (!pending.isEmpty() && System.currentTimeMillis() >= p.getLong("pendingAt", Long.MAX_VALUE)) {
            p.edit().putString(KEY_PROVIDER, DnsGuard.provider(pending)).remove("pendingProvider").remove("pendingAt").apply();
            es.foco.next.log.InspectorLog.event(
                    c, es.foco.next.log.InspectorLog.INCIDENTE, "", 0, "dns_proveedor_cambiado",
                    "inc|dnsprov|" + System.currentTimeMillis() / 60_000);
        }
        String current = DnsGuard.provider(p.getString(KEY_PROVIDER, DnsGuard.DEFAULT_PROVIDER));
        // v26: CleanBrowsing y AdGuard ya no existen. Si quedaba uno guardado y había pedido un
        // perfil de NextDNS, ese perfil se adopta ya (es el único filtro posible).
        if (current.isEmpty() && DnsGuard.nextDns(pending)) {
            setProvider(c, pending);
            p.edit().remove("pendingProvider").remove("pendingAt").apply();
            return DnsGuard.provider(pending);
        }
        return current;
    }

    /** Último perfil de NextDNS usado, para volver a él al salir de automático. */
    public static String lastNextDns(Context c) {
        return prefs(c).getString("lastNextDns", "");
    }

    /**
     * Pasar a automático (sin filtro) siempre tarda 24 h, con o sin candado: es la decisión que
     * pediste después de un fallo de NextDNS, no un atajo.
     */
    public static void requestAutomatic(Context c) {
        requestProvider(c, DnsGuard.AUTOMATIC);
    }

    /** Con el candado puesto: el proveedor cambia dentro de 24 h (aflojar va con calma). */
    public static final long LOCKED_CHANGE_MS = 24 * 60 * 60_000L;

    public static void requestProvider(Context c, String host) {
        // Pedir lo mismo otra vez no reinicia la espera (antes cada toque volvía a sumar 24 h).
        if (DnsGuard.sameHost(pendingProvider(c), DnsGuard.provider(host))) return;
        prefs(c).edit()
                .putString("pendingProvider", DnsGuard.provider(host))
                .putLong("pendingAt", System.currentTimeMillis() + LOCKED_CHANGE_MS)
                .apply();
    }

    public static void cancelPending(Context c) {
        prefs(c).edit().remove("pendingProvider").remove("pendingAt").apply();
    }

    /** Proveedor pedido y aún no aplicado ("" si no hay). */
    public static String pendingProvider(Context c) {
        return prefs(c).getString("pendingProvider", "");
    }

    public static long pendingAt(Context c) {
        return prefs(c).getLong("pendingAt", 0);
    }

    public static void setProvider(Context c, String host) {
        String h = DnsGuard.provider(host);
        SharedPreferences.Editor e = prefs(c).edit().putString(KEY_PROVIDER, h);
        if (DnsGuard.nextDns(h)) e.putString("lastNextDns", h);
        e.apply();
    }

    public static boolean chromeChecked(Context c) {
        return prefs(c).getBoolean(KEY_CHROME, false);
    }

    public static void setChromeChecked(Context c, boolean on) {
        prefs(c).edit().putBoolean(KEY_CHROME, on).apply();
    }

    /** Estado actual para mostrar en Ajustes (lectura puntual, sin vigilancia). */
    public static DnsGuard.State snapshot(Context c) {
        DnsGuard.State s = new DnsGuard.State(armed(c), false, false, "", provider(c));
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network n = cm == null ? null : cm.getActiveNetwork();
            if (n != null) {
                s.network = true;
                LinkProperties p = cm.getLinkProperties(n);
                if (p != null) {
                    s.privateDnsActive = p.isPrivateDnsActive();
                    s.server =
                            p.getPrivateDnsServerName() == null ? "" : p.getPrivateDnsServerName();
                }
            }
        } catch (RuntimeException ignored) {
            s.network = false;
        }
        return s;
    }

    /**
     * No existe un intent público directo al DNS privado: se prueba el de Ajustes y luego Redes.
     */
    public static void openPrivateDnsSettings(Context c) {
        int flags =
                c instanceof android.app.Activity
                        ? 0
                        : android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
        String[] actions = {
            "android.settings.PRIVATE_DNS_SETTINGS",
            android.provider.Settings.ACTION_WIRELESS_SETTINGS,
            android.provider.Settings.ACTION_SETTINGS
        };
        for (String a : actions) {
            try {
                c.startActivity(new android.content.Intent(a).addFlags(flags));
                return;
            } catch (RuntimeException ignored) {
            }
        }
    }

    public static void copyHost(Context c, String host) {
        try {
            android.content.ClipboardManager cb =
                    c.getSystemService(android.content.ClipboardManager.class);
            if (cb != null)
                cb.setPrimaryClip(android.content.ClipData.newPlainText("DNS privado", host));
        } catch (RuntimeException ignored) {
        }
    }
}
