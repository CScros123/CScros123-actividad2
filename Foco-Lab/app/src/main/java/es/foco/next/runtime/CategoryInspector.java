package es.foco.next.runtime;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import es.foco.next.core.AppConfig;
import es.foco.next.core.Categories;
import es.foco.next.data.Repository;
import es.foco.next.log.BlockLog;
import es.foco.next.log.InspectorLog;
import es.foco.next.log.LogDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Foco 1.0, etapa 2: quién mueve las apps de categoría. Endurecer es automático y deja aviso;
 * aflojar solo se propone. Los datos salen del log del Inspector (últimos 7 días de las apps en
 * observación).
 */
public final class CategoryInspector {
    private CategoryInspector() {}

    private static final String PREFS = "foco-categorias";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Último aviso de endurecimiento ("" si no hay). */
    public static String notice(Context c) {
        return prefs(c).getString("notice", "");
    }

    /** Añade un aviso (del supervisor) al que ya hubiera, y lo muestra si Foco está en pantalla. */
    static void addNotice(Context c, String text) {
        String old = notice(c);
        prefs(c).edit().putString("notice", old.isEmpty() ? text : old + " " + text).apply();
        try {
            android.widget.Toast.makeText(c, text, android.widget.Toast.LENGTH_LONG).show();
        } catch (RuntimeException ignored) {
        }
    }

    public static void clearNotice(Context c) {
        prefs(c).edit().remove("notice").apply();
    }

    // Corrección A: apps nuevas en observación libre (se registran, no se limitan). Caché para
    // consultarla en cada cambio de app.
    private static volatile Set<String> watch;

    public static Set<String> watchlist(Context c) {
        Set<String> w = watch;
        if (w != null) return w;
        w = new HashSet<>(prefs(c).getStringSet("watch", new HashSet<>()));
        watch = w;
        return w;
    }

    static synchronized void watch(Context c, String pkg, boolean add) {
        Set<String> w = new HashSet<>(watchlist(c));
        if (add ? !w.add(pkg) : !w.remove(pkg)) return;
        prefs(c).edit().putStringSet("watch", w).apply();
        watch = w;
    }

    public static void reject(Context c, String pkg) {
        Set<String> r = new HashSet<>(prefs(c).getStringSet("rejected", new HashSet<>()));
        r.add(pkg);
        prefs(c).edit().putStringSet("rejected", r).apply();
    }

    public static Set<String> browsers(Context c) {
        Set<String> out = new HashSet<>();
        try {
            for (ResolveInfo r :
                    c.getPackageManager()
                            .queryIntentActivities(
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            android.net.Uri.parse("http://foco-check.invalid/")),
                                    android.content.pm.PackageManager.MATCH_ALL))
                if (r.activityInfo != null) out.add(r.activityInfo.packageName);
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    static boolean game(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationInfo(pkg, 0).category
                    == ApplicationInfo.CATEGORY_GAME;
        } catch (Exception e) {
            return false;
        }
    }

    public static String label(Context c, String pkg) {
        try {
            return c.getPackageManager()
                    .getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Uso por app y día de los últimos 7 días, solo de las apps en observación. Hilo del log. */
    private static Map<String, Map<String, long[]>> load(
            Context c, AppConfig config, LogDatabase db, Set<String> observed) {
        Map<String, Map<String, long[]>> out = new LinkedHashMap<>();
        for (LogDatabase.InspectorRow r : InspectorLog.rows(c, db, LocalDate.now(), 7, config)) {
            if (!(InspectorLog.OCIO.equals(r.type) || InspectorLog.SIN_CLASIFICAR.equals(r.type))
                    || !observed.contains(r.pkg)) continue;
            long[] d =
                    out.computeIfAbsent(r.pkg, k -> new LinkedHashMap<>())
                            .computeIfAbsent(r.day, k -> new long[2]);
            d[0] += r.ms;
            d[1] += r.times;
        }
        return out;
    }

    private static List<Categories.DayUse> days(Map<String, long[]> byDay) {
        List<Categories.DayUse> out = new ArrayList<>();
        if (byDay != null)
            for (long[] d : byDay.values()) out.add(new Categories.DayUse(d[0], d[1]));
        return out;
    }

    /**
     * Una vez al día: pasa a ocio las apps en observación que cumplen la regla de endurecer, lo
     * registra y deja el aviso. Nunca lanza.
     */
    public static void daily(Context context) {
        try {
            Context c = context.getApplicationContext();
            String today = LocalDate.now().toString();
            if (today.equals(prefs(c).getString("checked", ""))) return;
            Repository repository = Repository.get(c);
            AppConfig config = repository.config();
            // Observación con límite (juego, vídeo o red social) y observación libre (corrección
            // A): la misma regla de endurecer.
            Set<String> free = new HashSet<>(watchlist(c));
            free.removeAll(config.watchedApps);
            free.removeAll(config.observedApps);
            free.removeAll(config.productiveApps);
            Set<String> observed = new HashSet<>(config.observedApps);
            observed.addAll(free);
            if (observed.isEmpty()) {
                prefs(c).edit().putString("checked", today).apply();
                return;
            }
            BlockLog.query(
                    c,
                    db -> load(c, config, db, observed),
                    use -> {
                        try {
                            List<String> moved = new ArrayList<>();
                            for (String pkg : observed) {
                                boolean limited = config.observedApps.contains(pkg);
                                String why =
                                        Categories.hardenReason(
                                                days(use.get(pkg)), limited && game(c, pkg));
                                if (why == null) continue;
                                // El cambio de categoría queda en el log desde Repository, con este
                                // origen.
                                repository.harden(
                                        "automatico;" + why,
                                        n -> {
                                            n.observedApps.remove(pkg);
                                            n.watchedApps.add(pkg);
                                        });
                                watch(c, pkg, false);
                                moved.add(label(c, pkg) + " (" + why + ")");
                            }
                            for (String pkg : free)
                                if (AppKinds.ignored(c, pkg)) watch(c, pkg, false);
                            SharedPreferences.Editor e =
                                    prefs(c).edit().putString("checked", today);
                            if (!moved.isEmpty()) {
                                String text = "Foco pasó a ocio: " + String.join("; ", moved) + ".";
                                e.putString("notice", text);
                                try {
                                    android.widget.Toast.makeText(
                                                    c, text, android.widget.Toast.LENGTH_LONG)
                                            .show();
                                } catch (RuntimeException ignored) {
                                }
                            }
                            e.apply();
                        } catch (RuntimeException ignored) {
                        }
                    },
                    null);
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Apps en observación que cumplen las condiciones para proponerse como productivas (sin las ya
     * rechazadas). Resultado en el hilo principal.
     */
    public static void proposals(Context context, Consumer<List<String>> done) {
        try {
            Context c = context.getApplicationContext();
            AppConfig config = Repository.get(c).config();
            Set<String> observed = config.observedApps;
            if (observed.isEmpty()) {
                done.accept(new ArrayList<>());
                return;
            }
            Set<String> rejected = prefs(c).getStringSet("rejected", new HashSet<>());
            Set<String> browsers = browsers(c);
            BlockLog.query(
                    c,
                    db -> load(c, config, db, observed),
                    use -> {
                        List<String> out = new ArrayList<>();
                        for (String pkg : observed)
                            if (!rejected.contains(pkg)
                                    && !Categories.neverProductive(pkg, browsers.contains(pkg))
                                    && !Repository.get(c).recentLeisure(pkg)
                                    && Categories.canApprove(days(use.get(pkg)), game(c, pkg)))
                                out.add(pkg);
                        done.accept(out);
                    },
                    null);
        } catch (RuntimeException ignored) {
        }
    }
}
