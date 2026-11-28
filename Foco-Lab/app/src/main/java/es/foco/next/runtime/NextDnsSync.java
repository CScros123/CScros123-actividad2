package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.DnsGuard;
import es.foco.next.core.NextDnsStats;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Lee las estadísticas de hoy del perfil de NextDNS con la clave de API que la persona pega una
 * vez. Solo habla con api.nextdns.io, solo con clave guardada y como mucho cada 30 min. La clave se
 * guarda cifrada con el Android Keystore (v47, {@link SecretBox}) y no se exporta ni se registra.
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

    // v47: la clave va cifrada (SecretBox, Android Keystore). Se guarda en memoria descifrada para
    // no tocar el Keystore en cada vuelta al inicio.
    private static final String SECRET = "nextdns_key";
    private static volatile String cached;

    /** La clave de API (descifrada) o "". Nunca se escribe en el log. */
    static String key(Context c) {
        String k = cached;
        if (k != null) return k;
        SharedPreferences p = prefs(c);
        String plain = p.getString("key", "");
        // v47: antes se guardaba en texto plano; se pasa al Keystore y se borra la copia plana.
        if (!plain.isEmpty() && SecretBox.put(c, SECRET, plain.trim()))
            p.edit().remove("key").apply();
        k = SecretBox.get(c, SECRET);
        if (k.isEmpty() && !plain.isEmpty()) k = plain.trim();
        cached = k;
        return k;
    }

    public static boolean hasKey(Context c) {
        return NextDnsStats.validKey(key(c));
    }

    /** Guarda la clave cifrada (o la quita con null). false si el Keystore no pudo cifrarla. */
    public static boolean setKey(Context c, String key) {
        SharedPreferences.Editor e = prefs(c).edit().putLong("at", 0).remove("error").remove("key");
        boolean ok = true;
        if (key == null || key.trim().isEmpty()) {
            SecretBox.put(c, SECRET, null);
            e.remove("blocked").remove("total").remove("top").remove("day");
            cached = "";
        } else {
            ok = SecretBox.put(c, SECRET, key.trim());
            cached = ok ? key.trim() : null;
        }
        e.apply();
        if (ok) NextDnsControl.request(c);
        return ok;
    }

    /** v47: ID de perfil escrito a mano para la API (vacío = el del DNS privado vigilado). */
    public static String manualProfile(Context c) {
        return prefs(c).getString("perfil_api", "");
    }

    public static void setManualProfile(Context c, String id) {
        String v = id == null ? "" : id.trim().toLowerCase(java.util.Locale.ROOT);
        prefs(c).edit().putString("perfil_api", v).putLong("at", 0).apply();
        NextDnsControl.request(c);
    }

    /**
     * v47: «Probar conexión». Lee el perfil con la clave guardada y devuelve un texto para la
     * persona (sin la clave). Hilo de fondo.
     */
    public static String test(Context c) {
        String k = key(c), id = profile(c);
        if (!NextDnsStats.validKey(k)) return "Falta la clave de API.";
        if (id == null) return "Falta el ID de perfil (p. ej. 96916b).";
        try {
            NextDnsApi.get(NextDnsApi.BASE + id, k);
            return "Conexión correcta con el perfil " + id + ".";
        } catch (NextDnsApi.HttpError e) {
            return e.code == 401 || e.code == 403
                    ? "La clave de API no es válida o no tiene acceso a este perfil."
                    : e.code == 404
                            ? "NextDNS no encuentra el perfil " + id + "."
                            : "NextDNS respondió " + e.code + ".";
        } catch (Exception e) {
            return "Sin conexión con NextDNS. Prueba con internet.";
        }
    }

    /** ID del perfil a partir del DNS privado que Foco vigila; null si no es NextDNS. */
    public static String profile(Context c) {
        String manual = manualProfile(c);
        if (NextDnsStats.validProfile(manual)) return manual;
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
        String key = key(app);
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
                                week(p, id, key); // v50: la semana para Análisis
                            } catch (NextDnsApi.HttpError e) {
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

    /** v50: cada 6 h como mucho, lo bloqueado en 7 días y los intentos de cada red social. */
    static final long WEEK_REFRESH_MS = 6 * 60 * 60_000L;

    private static void week(SharedPreferences p, String id, String key) {
        if (System.currentTimeMillis() - p.getLong("sem_at", 0) < WEEK_REFRESH_MS) return;
        try {
            String base = NextDnsApi.BASE + id + "/analytics/";
            long[] st = NextDnsApi.status(get(base + "status?from=-7d", key));
            java.util.Map<String, Long> social =
                    es.foco.next.core.backup.NextDnsPlan.socialActivity(
                            NextDnsApi.domains(
                                    get(
                                            base
                                                    + "domains?status=blocked&root=true&limit=500&from=-7d",
                                            key)));
            StringBuilder b = new StringBuilder();
            for (java.util.Map.Entry<String, Long> e : social.entrySet())
                b.append(b.length() == 0 ? "" : ";").append(e.getKey()).append('|').append(e.getValue());
            p.edit()
                    .putLong("sem_at", System.currentTimeMillis())
                    .putLong("sem_total", st[0])
                    .putLong("sem_blocked", st[1])
                    .putString("sem_social", b.toString())
                    .apply();
        } catch (Exception ignored) {
            // Se reintenta en la próxima lectura.
        }
    }

    /** {total, bloqueadas} de los últimos 7 días (0 si aún no se leyó). */
    public static long[] week(Context c) {
        SharedPreferences p = prefs(c);
        return new long[] {p.getLong("sem_total", 0), p.getLong("sem_blocked", 0)};
    }

    /** Intentos bloqueados por red social en 7 días. */
    public static java.util.Map<String, Long> weekSocial(Context c) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        for (String part : prefs(c).getString("sem_social", "").split(";")) {
            int i = part.lastIndexOf('|');
            if (i <= 0) continue;
            try {
                out.put(part.substring(0, i), Long.parseLong(part.substring(i + 1)));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    private static String get(String url, String key) throws Exception {
        return NextDnsApi.get(url, key);
    }
}
