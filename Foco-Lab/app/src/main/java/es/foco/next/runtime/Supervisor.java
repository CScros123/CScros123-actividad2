package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import es.foco.next.core.AppConfig;
import es.foco.next.core.FocusEngine;
import es.foco.next.data.Repository;
import es.foco.next.log.BlockEvent;
import es.foco.next.log.BlockLog;
import es.foco.next.log.InspectorLog;
import es.foco.next.log.LogDatabase;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Foco 1.0 (docs/FOCO-1.0-DEFINICION.md §2 y §6): supervisor automático. Reglas fijas que cuentan y
 * comparan, sin IA ni internet. Todas endurecen, así que actúan solas y dejan aviso con la regla
 * que se cumplió.
 *
 * <ul>
 *   <li>Límite diario propio por app (TikTok: 10 min). Pasado el tope no se abre hasta mañana,
 *       aunque haya saldo.
 *   <li>Hora pico: si una hora concentra más del 30 % del ocio o de los bloqueos de 7 días, franja
 *       estricta de dos horas que empieza una hora antes; se quita cuando esa hora baja del 15 %.
 *   <li>App dominante: más del 40 % del ocio en 3 de 7 días baja su límite 2 minutos (mínimo 5);
 *       por debajo del 25 % lo recupera.
 *   <li>Redirección: al bloquear, propone una actividad que da minutos. Registro de VPN activa (no
 *       se bloquea).
 * </ul>
 */
public final class Supervisor {
    private Supervisor() {}

    private static final String PREFS = "foco-supervisor";
    static final int TIKTOK_LIMIT = 10, MIN_LIMIT = 5, STEP = 2;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean tiktok(String pkg) {
        return "com.zhiliaoapp.musically".equals(pkg) || "com.ss.android.ugc.trill".equals(pkg);
    }

    /** Límite diario propio en minutos (0 = no tiene). */
    public static int limit(Context c, String pkg) {
        return prefs(c).getInt("limit|" + pkg, tiktok(pkg) ? TIKTOK_LIMIT : 0);
    }

    /** Hora en que empieza la franja estricta (dura dos horas), o −1. */
    public static int franja(Context c) {
        return prefs(c).getInt("franja", -1);
    }

    /** Límites propios vigentes entre las apps con límite de 5 minutos, para mostrarlos. */
    public static Map<String, Integer> limits(Context c, AppConfig config) {
        Map<String, Integer> out = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(config.watchedApps);
        all.addAll(config.observedApps);
        for (String p : all) {
            int l = limit(c, p);
            if (l > 0) out.put(p, l);
        }
        return out;
    }

    /**
     * Motivo por el que esta app de ocio no se abre ahora aunque haya saldo (tope propio o franja
     * estricta), o null. La consulta el servicio en cada decisión: solo lee preferencias ya
     * cargadas.
     */
    public static String stop(Context c, Repository r, String pkg, int hour) {
        try {
            int limit = limit(c, pkg);
            if (limit > 0 && r.usedToday(pkg) >= limit * 60_000L)
                return CategoryInspector.label(c, pkg)
                        + ": límite de "
                        + limit
                        + " min de hoy alcanzado. Vuelve mañana.";
            int f = franja(c);
            if (f >= 0 && (hour == f || hour == (f + 1) % 24))
                return "Franja estricta de "
                        + f
                        + ":00 a "
                        + ((f + 2) % 24)
                        + ":00, tu hora más difícil. El ocio vuelve después.";
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    /**
     * Propuesta concreta en lugar del ocio. Rota entre las productivas aprobadas y Cognitive Lab, y
     * queda en el log.
     */
    public static String proposal(Context c, AppConfig config) {
        try {
            List<String> options = new ArrayList<>(config.productiveApps);
            options.add("");
            SharedPreferences p = prefs(c);
            String day = LocalDate.now().toString();
            int turn = day.equals(p.getString("propDay", "")) ? p.getInt("propTurn", 0) : 0;
            long now = System.currentTimeMillis();
            // La misma propuesta se mantiene 10 minutos; después rota a la siguiente.
            if (now - p.getLong("propAt", 0) > 10 * 60_000L) {
                turn++;
                p.edit()
                        .putString("propDay", day)
                        .putInt("propTurn", turn)
                        .putLong("propAt", now)
                        .apply();
            }
            String target =
                    options.get(
                            Math.floorMod(LocalDate.now().getDayOfYear() + turn, options.size()));
            String name = target.isEmpty() ? "Cognitive Lab" : CategoryInspector.label(c, target);
            InspectorLog.event(
                    c,
                    InspectorLog.REDIRECCION,
                    target,
                    0,
                    "propuesta: " + name,
                    "red|" + day + "|" + turn + "|" + target);
            return target.isEmpty()
                    ? "En vez de esto: un reto superado de Cognitive Lab te da de 2 a 5 min, según el nivel."
                    : "En vez de esto: 20 min activos en " + name + " te dan 5 min.";
        } catch (RuntimeException e) {
            return "Un reto superado de Cognitive Lab te da minutos.";
        }
    }

    /** Registra cuándo empieza y cuándo termina una VPN activa. No bloquea nada. */
    public static void vpn(Context context) {
        try {
            Context c = context.getApplicationContext();
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            if (cm == null) return;
            NetworkCapabilities k = cm.getNetworkCapabilities(cm.getActiveNetwork());
            boolean on = k != null && k.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            SharedPreferences p = prefs(c);
            if (on == p.getBoolean("vpn", false)) return;
            p.edit().putBoolean("vpn", on).apply();
            InspectorLog.event(c, InspectorLog.VPN, "", 0, on ? "inicio" : "fin", null);
        } catch (RuntimeException ignored) {
        }
    }

    /** Una vez al día: hora pico, app dominante y saldo de cierre. Nunca lanza. */
    public static void daily(Context context) {
        try {
            Context c = context.getApplicationContext();
            LocalDate today = LocalDate.now();
            String day = today.toString();
            SharedPreferences p = prefs(c);
            if (day.equals(p.getString("checked", ""))) return;
            Repository repository = Repository.get(c);
            AppConfig config = repository.config();
            String from = today.minusDays(6).toString();
            BlockLog.query(
                    c,
                    db ->
                            new Object[] {
                                InspectorLog.rows(c, db, today, 7, config), db.events(from)
                            },
                    loaded -> {
                        try {
                            @SuppressWarnings("unchecked")
                            List<LogDatabase.InspectorRow> rows =
                                    (List<LogDatabase.InspectorRow>) loaded[0];
                            @SuppressWarnings("unchecked")
                            List<BlockEvent> events = (List<BlockEvent>) loaded[1];
                            long[] ocio = new long[24];
                            long[] blocks = new long[24];
                            long total = 0, blockTotal = 0;
                            Map<String, Map<String, Long>> perDay = new HashMap<>();
                            Map<String, Long> perApp = new HashMap<>();
                            for (LogDatabase.InspectorRow r : rows) {
                                if (!InspectorLog.OCIO.equals(r.type) || r.ms <= 0) continue;
                                total += r.ms;
                                if (r.hour >= 0 && r.hour < 24) ocio[r.hour] += r.ms;
                                perDay.computeIfAbsent(r.day, k -> new HashMap<>())
                                        .merge(r.pkg, r.ms, Long::sum);
                                perApp.merge(r.pkg, r.ms, Long::sum);
                            }
                            for (BlockEvent e : events) {
                                if (!(e.type.isBlock() || e.type.isAttempt())) continue;
                                try {
                                    blocks[
                                            Instant.ofEpochMilli(e.timestamp)
                                                    .atZone(
                                                            e.zone.isEmpty()
                                                                    ? ZoneId.systemDefault()
                                                                    : ZoneId.of(e.zone))
                                                    .getHour()]++;
                                    blockTotal++;
                                } catch (RuntimeException ignored) {
                                }
                            }
                            SharedPreferences.Editor ed = p.edit().putString("checked", day);
                            List<String> notes = new ArrayList<>();
                            // Hora pico → franja estricta.
                            int peak = -1;
                            long share = 0;
                            for (int h = 0; h < 24; h++) {
                                long s =
                                        Math.max(
                                                total >= 30 * 60_000L ? ocio[h] * 100 / total : 0,
                                                blockTotal >= 10
                                                        ? blocks[h] * 100 / blockTotal
                                                        : 0);
                                if (s > 30 && s > share) {
                                    share = s;
                                    peak = h;
                                }
                            }
                            int franja = p.getInt("franja", -1), oldPeak = p.getInt("peak", -1);
                            if (peak >= 0) {
                                int start = (peak + 23) % 24;
                                if (start != franja) {
                                    ed.putInt("franja", start).putInt("peak", peak);
                                    String why =
                                            "la hora de las "
                                                    + peak
                                                    + ":00 concentra el "
                                                    + share
                                                    + " % de tu ocio o de tus bloqueos";
                                    InspectorLog.event(
                                            c,
                                            InspectorLog.FRANJA,
                                            "",
                                            start,
                                            "inicio;" + why,
                                            "franja|" + day + "|" + start);
                                    notes.add(
                                            "Franja estricta de "
                                                    + start
                                                    + ":00 a "
                                                    + ((start + 2) % 24)
                                                    + ":00: "
                                                    + why
                                                    + ".");
                                }
                            } else if (franja >= 0 && oldPeak >= 0) {
                                long now =
                                        Math.max(
                                                total > 0 ? ocio[oldPeak] * 100 / total : 0,
                                                blockTotal > 0
                                                        ? blocks[oldPeak] * 100 / blockTotal
                                                        : 0);
                                if (now < 15) {
                                    ed.remove("franja").remove("peak");
                                    InspectorLog.event(
                                            c,
                                            InspectorLog.FRANJA,
                                            "",
                                            franja,
                                            "fin;la hora de las "
                                                    + oldPeak
                                                    + ":00 bajó al "
                                                    + now
                                                    + " %",
                                            "franja|fin|" + day);
                                    notes.add(
                                            "Se quitó la franja estricta: esa hora ya no es tu hora pico.");
                                }
                            }
                            // App dominante → su límite baja; si deja de dominar, lo recupera.
                            for (Map.Entry<String, Long> app : perApp.entrySet()) {
                                String pkg = app.getKey();
                                if (!FocusEngine.watched(config, pkg)) continue;
                                int days = 0;
                                for (Map<String, Long> d : perDay.values()) {
                                    long sum = 0;
                                    for (long v : d.values()) sum += v;
                                    if (sum >= 10 * 60_000L
                                            && d.getOrDefault(pkg, 0L) * 100 > sum * 40) days++;
                                }
                                int current = limit(c, pkg);
                                long weekShare = total > 0 ? app.getValue() * 100 / total : 0;
                                String last = p.getString("lowered|" + pkg, "");
                                boolean recently = !last.isEmpty() && last.compareTo(from) >= 0;
                                if (days >= 3 && !recently) {
                                    int base = current > 0 ? current : TIKTOK_LIMIT,
                                            next =
                                                    current > 0
                                                            ? Math.max(MIN_LIMIT, current - STEP)
                                                            : TIKTOK_LIMIT;
                                    if (next != current) {
                                        if (!p.contains("base|" + pkg))
                                            ed.putInt("base|" + pkg, base);
                                        ed.putInt("limit|" + pkg, next)
                                                .putString("lowered|" + pkg, day);
                                        InspectorLog.event(
                                                c,
                                                InspectorLog.CATEGORIA,
                                                pkg,
                                                next,
                                                "limite_propio "
                                                        + current
                                                        + ">"
                                                        + next
                                                        + ";automatico;app dominante en "
                                                        + days
                                                        + " dias",
                                                "lim|" + day + "|" + pkg);
                                        notes.add(
                                                CategoryInspector.label(c, pkg)
                                                        + ": límite diario de "
                                                        + next
                                                        + " min (fue más del 40 % de tu ocio en "
                                                        + days
                                                        + " días).");
                                    }
                                } else if (current > 0
                                        && weekShare < 25
                                        && p.contains("base|" + pkg)
                                        && current < p.getInt("base|" + pkg, current)
                                        && !recently) {
                                    int next =
                                            Math.min(
                                                    p.getInt("base|" + pkg, current),
                                                    current + STEP);
                                    ed.putInt("limit|" + pkg, next)
                                            .putString("lowered|" + pkg, day);
                                    InspectorLog.event(
                                            c,
                                            InspectorLog.CATEGORIA,
                                            pkg,
                                            next,
                                            "limite_propio "
                                                    + current
                                                    + ">"
                                                    + next
                                                    + ";automatico;ya no domina",
                                            "lim|" + day + "|" + pkg);
                                    notes.add(
                                            CategoryInspector.label(c, pkg)
                                                    + ": recupera "
                                                    + STEP
                                                    + " min de límite diario ("
                                                    + next
                                                    + " min).");
                                }
                            }
                            ed.apply();
                            if (!notes.isEmpty())
                                CategoryInspector.addNotice(c, String.join(" ", notes));
                        } catch (RuntimeException ignored) {
                        }
                    },
                    null);
        } catch (RuntimeException ignored) {
        }
    }
}
