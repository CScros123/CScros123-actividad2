package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.DnsGuard;
import es.foco.next.core.NextDnsStats;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Lee las estadísticas de hoy del perfil de NextDNS con la clave de API que la persona pega una
 * vez. Solo habla con api.nextdns.io, solo con clave guardada y como mucho cada 30 min. La clave se
 * guarda en las preferencias privadas de Foco y no se exporta ni se registra.
 */
public final class NextDnsSync {
    private NextDnsSync() {}

    private static final String PREFS = "foco-nextdns";
    // Una sola actualización a la vez (comprobar y marcar de forma atómica).
    private static final java.util.concurrent.atomic.AtomicBoolean running =
            new java.util.concurrent.atomic.AtomicBoolean(false);
    private static final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public static final class Snapshot {
        public final long blocked, total, at;
        public final List<NextDnsStats.Hit> top;
        public final String error;

        Snapshot(long blocked, long total, long at, List<NextDnsStats.Hit> top, String error) {
            this.blocked = blocked;
            this.total = total;
            this.at = at;
            this.top = top;
            this.error = error;
        }
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean hasKey(Context c) {
        return NextDnsStats.validKey(prefs(c).getString("key", ""));
    }

    public static void setKey(Context c, String key) {
        SharedPreferences.Editor e = prefs(c).edit().putLong("at", 0).remove("error");
        if (key == null || key.trim().isEmpty())
            e.remove("key").remove("blocked").remove("total").remove("top").remove("day");
        else e.putString("key", key.trim());
        e.apply();
    }

    /** ID del perfil a partir del DNS privado que Foco vigila; null si no es NextDNS. */
    public static String profile(Context c) {
        String host = DnsMonitor.provider(c);
        // Con un cambio a NextDNS pedido (modo estricto, 24 h), las estadísticas ya pueden leerse.
        if (!DnsGuard.nextDns(host)) host = DnsMonitor.pendingProvider(c);
        if (!DnsGuard.nextDns(host)) return null;
        String id = DnsGuard.nextDnsProfile(host).toLowerCase(java.util.Locale.ROOT);
        return NextDnsStats.validProfile(id) ? id : null;
    }

    public static void addListener(Runnable r) {
        listeners.addIfAbsent(r);
    }

    public static void removeListener(Runnable r) {
        listeners.remove(r);
    }

    /** Lo último leído (de hoy). */
    public static Snapshot snapshot(Context c) {
        SharedPreferences p = prefs(c);
        boolean today = LocalDate.now().toString().equals(p.getString("day", ""));
        return new Snapshot(
                today ? p.getLong("blocked", 0) : 0,
                today ? p.getLong("total", 0) : 0,
                p.getLong("at", 0),
                today
                        ? NextDnsStats.decode(p.getString("top", ""))
                        : java.util.Collections.emptyList(),
                p.getString("error", null));
    }

    /** Lee en segundo plano si toca (o si {@code force}). Avisa al terminar. */
    public static void refresh(Context c, boolean force) {
        Context app = c.getApplicationContext();
        SharedPreferences p = prefs(app);
        String key = p.getString("key", "");
        String id = profile(app);
        if (!NextDnsStats.validKey(key) || id == null || running.get()) return;
        long now = System.currentTimeMillis();
        boolean newDay = !LocalDate.now().toString().equals(p.getString("day", ""));
        if (!force && !newDay && now - p.getLong("at", 0) < NextDnsStats.REFRESH_MS) return;
        if (!running.compareAndSet(false, true)) return;
        new Thread(
                        () -> {
                            try {
                                String from =
                                        LocalDate.now()
                                                .atStartOfDay(ZoneId.systemDefault())
                                                .toInstant()
                                                .toString();
                                String status = get(NextDnsStats.statusUrl(id, from), key);
                                String domains =
                                        get(NextDnsStats.blockedDomainsUrl(id, from, 5), key);
                                p.edit()
                                        .putString("day", LocalDate.now().toString())
                                        .putLong("at", System.currentTimeMillis())
                                        .putLong("blocked", NextDnsStats.queries(status, "blocked"))
                                        .putLong("total", NextDnsStats.total(status))
                                        .putString(
                                                "top",
                                                NextDnsStats.encode(
                                                        NextDnsStats.domains(domains, 5)))
                                        .remove("error")
                                        .apply();
                            } catch (HttpError e) {
                                p.edit()
                                        .putLong("at", System.currentTimeMillis())
                                        .putString(
                                                "error",
                                                e.code == 401 || e.code == 403
                                                        ? "La clave de API no es válida o no tiene acceso a este perfil."
                                                        : e.code == 404
                                                                ? "NextDNS no encuentra el perfil "
                                                                        + id
                                                                        + "."
                                                                : "NextDNS respondió "
                                                                        + e.code
                                                                        + ".")
                                        .apply();
                            } catch (Exception e) {
                                // Sin red: se reintenta en la próxima vuelta al inicio (sin marcar
                                // la hora).
                                p.edit().putString("error", "Sin conexión con NextDNS.").apply();
                            } finally {
                                running.set(false);
                                for (Runnable r : listeners) r.run();
                            }
                        },
                        "foco-nextdns")
                .start();
    }

    private static final class HttpError extends Exception {
        final int code;

        HttpError(int code) {
            this.code = code;
        }
    }

    private static String get(String url, String key) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        try {
            h.setConnectTimeout(8_000);
            h.setReadTimeout(8_000);
            h.setRequestProperty("X-Api-Key", key);
            h.setRequestProperty("Accept", "application/json");
            int code = h.getResponseCode();
            if (code != 200) throw new HttpError(code);
            try (InputStream in = h.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0 && out.size() < 256_000) out.write(b, 0, n);
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            h.disconnect();
        }
    }
}
