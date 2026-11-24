package es.foco.next.runtime;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import es.foco.next.core.AppConfig;
import es.foco.next.core.care.Money;
import es.foco.next.core.care.Routine;
import es.foco.next.data.Repository;
import es.foco.next.data.RuleStore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * v28: informe diario único de Foco (antes, Foco Inspector en una carpeta). v32 (R9): cada día es el
 * día lógico de Foco (de 04:00 a 04:00, como Foco Core, los premios y las evasiones); al cerrarse
 * (alarma de las 04:05, y al abrir Foco o reiniciar si se perdió) Foco guarda en
 * el teléfono un archivo de texto con todo lo del día, dentro del teléfono (plan, bloqueos, tiempos,
 * retos de Lab, DNS) y fuera (tareas de Foco Core, pausas, hora libre, fotos y dinero). Se guardan
 * 90 días en {@code files/informes}; desde Foco › Estado se exportan los últimos 30 en un solo
 * archivo para analizarlos. Sin internet y sin el ánimo.
 */
public final class Inspector {
    private static final String PREFS = "foco-inspector";
    private static final String ACTION = "es.foco.next.REPORT";
    private static final int REQUEST = 5301;
    private static final int KEEP_DAYS = 90;
    private static final ExecutorService IO =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "foco-informe");
                        t.setDaemon(true);
                        return t;
                    });

    private Inspector() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), "informes");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static File file(Context c, LocalDate day) {
        return new File(dir(c), day + ".txt");
    }

    /** Día del último informe guardado ("" si ninguno). */
    public static String lastDay(Context c) {
        return prefs(c).getString("lastDay", "");
    }

    /**
     * Guarda el informe de ayer si falta (y, con {@code force}, también el de hoy hasta ahora) y
     * programa el cierre de esta noche. En segundo plano; nunca lanza.
     */
    public static void maybeWrite(Context context, boolean force) {
        final Context c = context.getApplicationContext();
        try {
            scheduleMidnight(c);
            LocalDate yesterday = RoutineStore.today().minusDays(1); // v32 (R9): día lógico
            boolean missing = !file(c, yesterday).exists() && firstUse(c).isBefore(RoutineStore.today());
            if (!missing && !force) return;
            IO.execute(
                    () -> {
                        try {
                            if (!file(c, yesterday).exists()) save(c, yesterday);
                            if (force) save(c, RoutineStore.today());
                            prune(c);
                        } catch (Exception e) {
                            prefs(c).edit().putString("error", e.getClass().getSimpleName()).apply();
                            android.util.Log.w("Foco", "Informe: no se pudo guardar", e);
                        }
                    });
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- v32 (R9): fotografía del día ----------------

    private static String lastNote = "";

    /**
     * Apunta lo que hubo hoy (día lógico): modos usados y si hubo Weekend Lock. Lo llama la
     * Accesibilidad; solo escribe cuando cambia algo.
     */
    public static void noteDay(Context c, String mode, boolean weekend) {
        try {
            String day = RoutineStore.today().toString();
            String key = day + "|" + mode + "|" + weekend;
            if (key.equals(lastNote)) return;
            lastNote = key;
            SharedPreferences p = prefs(c);
            String modes = p.getString("modos_" + day, "");
            if (!("," + modes + ",").contains("," + mode + ","))
                modes = modes.isEmpty() ? mode : modes + "," + mode;
            SharedPreferences.Editor e = p.edit().putString("modos_" + day, modes);
            if (weekend) e.putBoolean("weekend_" + day, true);
            // Solo los últimos días (los informes guardan el resto).
            String old = RoutineStore.today().minusDays(10).toString();
            for (String k : p.getAll().keySet())
                if ((k.startsWith("modos_") || k.startsWith("weekend_"))
                        && k.substring(k.indexOf('_') + 1).compareTo(old) < 0) e.remove(k);
            e.apply();
        } catch (RuntimeException ignored) {
        }
    }

    private static String seen(Context c, LocalDate day, String what) {
        SharedPreferences p = prefs(c);
        if ("weekend".equals(what)) return Boolean.toString(p.getBoolean("weekend_" + day, false));
        String m = p.getString("modos_" + day, "");
        return m.isEmpty() ? "sin_dato" : m;
    }

    /**
     * v32 (R6): las cuatro clases del ocio del día. El total sale del sistema (Acceso de uso) si hay
     * permiso; si no, de lo que midió Foco. Premio, Cuidado y "fuera de condiciones" los mide la
     * Accesibilidad; el resto es ocio sin excepción (dentro del límite).
     */
    static String leisure(List<es.foco.next.log.LogDatabase.InspectorRow> rows, LocalDate day, boolean systemTotal) {
        long ocio = 0, premio = 0, cuidado = 0, fuera = 0;
        for (es.foco.next.log.LogDatabase.InspectorRow r : rows) {
            if (!es.foco.next.log.InspectorLog.inLogicalDay(day, r.day, r.hour)) continue;
            if (es.foco.next.log.InspectorLog.OCIO.equals(r.type)) ocio += r.ms;
            else if (es.foco.next.log.InspectorLog.OCIO_PREMIO.equals(r.type)) premio += r.ms;
            else if (es.foco.next.log.InspectorLog.OCIO_CUIDADO.equals(r.type)) cuidado += r.ms;
            else if (es.foco.next.log.InspectorLog.OCIO_FUERA.equals(r.type)) fuera += r.ms;
        }
        long total = systemTotal ? Math.max(ocio, premio + cuidado + fuera) : ocio + premio + cuidado + fuera;
        long rest = Math.max(0, total - premio - cuidado - fuera);
        return "\n== OCIO (cuatro clases) ==\n"
                + "fuente_del_total=" + (systemTotal ? "sistema" : "foco") + "\n"
                + "ocio_total_min=" + total / 60_000 + "\n"
                + "ocio_sin_excepcion_min=" + rest / 60_000 + "\n"
                + "ocio_por_premio_min=" + premio / 60_000 + "\n"
                + "ocio_en_cuidado_min=" + cuidado / 60_000 + "\n"
                + "ocio_fuera_de_condiciones_min=" + fuera / 60_000 + "\n";
    }

    /** El primer día con Foco instalado no tiene "ayer". */
    private static LocalDate firstUse(Context c) {
        SharedPreferences p = prefs(c);
        String f = p.getString("first", "");
        if (f.isEmpty()) {
            f = RoutineStore.today().toString();
            p.edit().putString("first", f).apply();
        }
        return LocalDate.parse(f);
    }

    /**
     * Alarma inexacta a las 04:05 (v32: cierre del día lógico) hacia {@link DailyReminder}, que llama
     * a {@link #maybeWrite}.
     */
    private static void scheduleMidnight(Context c) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            if (am == null) return;
            long at =
                    RoutineStore.today()
                            .plusDays(1)
                            .atTime(4, 5)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli();
            PendingIntent pi =
                    PendingIntent.getBroadcast(
                            c,
                            REQUEST,
                            new Intent(c, DailyReminder.class).setAction(ACTION),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setWindow(AlarmManager.RTC, at, 30 * 60_000L, pi);
        } catch (RuntimeException ignored) {
        }
    }

    private static synchronized void save(Context c, LocalDate day) throws Exception {
        byte[] bytes = report(c, day).getBytes(StandardCharsets.UTF_8);
        File out = file(c, day), tmp = new File(dir(c), day + ".tmp");
        try (java.io.FileOutputStream o = new java.io.FileOutputStream(tmp)) {
            o.write(bytes);
        }
        if (!tmp.renameTo(out)) throw new java.io.IOException("No se pudo guardar el informe");
        if (day.toString().compareTo(lastDay(c)) > 0)
            prefs(c).edit().putString("lastDay", day.toString()).remove("error").apply();
    }

    private static void prune(Context c) {
        String limit = LocalDate.now().minusDays(KEEP_DAYS).toString();
        File[] all = dir(c).listFiles();
        if (all == null) return;
        for (File f : all)
            if (f.getName().length() >= 10 && f.getName().substring(0, 10).compareTo(limit) < 0)
                //noinspection ResultOfMethodCallIgnored
                f.delete();
    }

    /** v28: los informes guardados, para la copia de seguridad única ({fecha: texto}). */
    public static org.json.JSONObject backup(Context c) {
        org.json.JSONObject o = new org.json.JSONObject();
        File[] all = dir(c).listFiles();
        if (all == null) return o;
        for (File f : all) {
            String n = f.getName();
            if (!n.endsWith(".txt") || n.length() != 14) continue;
            try {
                o.put(n.substring(0, 10), new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
        return o;
    }

    /** Restaura los informes que falten (nunca pisa uno que ya está en el teléfono). */
    public static void restore(Context c, org.json.JSONObject in) {
        if (in == null) return;
        java.util.Iterator<String> keys = in.keys();
        while (keys.hasNext()) {
            String day = keys.next();
            if (!day.matches("\\d{4}-\\d{2}-\\d{2}")) continue;
            File f = new File(dir(c), day + ".txt");
            if (f.exists()) continue;
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) {
                o.write(in.optString(day, "").getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
    }

    /** Los últimos {@code days} informes guardados, del más antiguo al más nuevo, en un texto. */
    public static String export(Context c, int days) {
        StringBuilder s = new StringBuilder();
        LocalDate today = RoutineStore.today();
        for (int i = days - 1; i >= 0; i--) {
            File f = file(c, today.minusDays(i));
            if (!f.exists()) continue;
            try {
                s.append(new String(java.nio.file.Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8))
                        .append("\n\n");
            } catch (Exception ignored) {
            }
        }
        return s.length() == 0 ? "Todavía no hay informes guardados.\n" : s.toString();
    }

    /** Informe de un día: dentro del teléfono y fuera, en texto plano. Hilo de fondo. */
    static String report(Context c, LocalDate day) {
        StringBuilder s = new StringBuilder();
        s.append("INFORME DE FOCO · ").append(day)
                .append(" (día de Foco: ").append(day).append(" 04:00 → ").append(day.plusDays(1)).append(" 04:00)\n");
        s.append("generado=").append(LocalDateTime.now().withNano(0)).append('\n');
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            s.append("version=").append(v).append('\n');
        } catch (Exception ignored) {
        }

        s.append("\n== FUERA DEL TELÉFONO (Foco Core) ==\n");
        try {
            if (!RoutineStore.hasData(c, day)) s.append("sin_registro_de_foco_core\n");
            else {
                int[] t = RoutineStore.totals(c, day);
                s.append("tareas_hechas=").append(t[1]).append(" de ").append(t[0]).append('\n');
                for (String b : RoutineStore.BLOCKS) {
                    List<Routine.Task> tasks = RoutineStore.blockTasks(c, b, day);
                    if (tasks.isEmpty()) continue;
                    Set<Integer> done = RoutineStore.blockDone(c, b, day);
                    Set<Integer> missed = RoutineStore.blockMissed(c, b, day);
                    s.append("[").append(b.toLowerCase()).append("]\n");
                    for (int i = 0; i < tasks.size(); i++) {
                        Routine.Task task = tasks.get(i);
                        String mark =
                                done.contains(i) && missed.contains(i)
                                        ? "hecha_tarde"
                                        : done.contains(i) ? "hecha" : missed.contains(i) ? "incumplida" : "pendiente";
                        s.append("- ").append(task.label).append(" · ").append(task.minutes).append(" min");
                        if (task.window >= 0) s.append(" · límite ").append(task.windowText());
                        s.append(" · ").append(mark).append('\n');
                    }
                }
                s.append("pausas_por_tareas=").append(RoutineStore.penalties(c, day)).append('\n');
                s.append("fotos_de_evidencia=").append(RoutineStore.photos(c, day).size()).append('\n');
            }
        } catch (RuntimeException e) {
            s.append("foco_core_no_disponible\n");
        }
        try {
            long budget = MoneyStore.budget(c);
            List<Money.Entry> entries = MoneyStore.entries(c);
            s.append("gasto_del_dia=").append(Money.fmt(Money.spent(entries, day, day))).append('\n');
            if (budget > 0) {
                s.append("permitido_del_dia=").append(Money.fmt(Money.dailyAllowance(budget, entries, day))).append('\n');
                s.append("gasto_de_la_semana=").append(Money.fmt(Money.spentWeek(entries, day)))
                        .append(" de ").append(Money.fmt(budget)).append('\n');
            }
            long debt = MoneyStore.debt(c);
            if (debt > 0) s.append("deuda=").append(Money.fmt(debt)).append('\n');
        } catch (RuntimeException e) {
            s.append("dinero_no_disponible\n");
        }

        try {
            s.append("racha_dias_100=").append(RewardStore.streak(c)).append('\n');
            s.append("cupones_guardados=").append(RewardStore.coupons(c).size()).append('\n');
            s.append("intentos_de_evasion=").append(RewardStore.evasions(c, day)).append('\n');
        } catch (RuntimeException ignored) {
        }

        s.append("\n== DENTRO DEL TELÉFONO ==\n");
        Repository r = Repository.get(c);
        AppConfig config = r.config();
        // v32 (R9): lo que hubo ese día (fotografía tomada durante el día), no solo el estado de ahora.
        s.append("modos_del_dia=").append(seen(c, day, "modos")).append('\n');
        s.append("weekend_lock_del_dia=").append(seen(c, day, "weekend")).append('\n');
        s.append("modo_al_cerrar=").append(es.foco.next.core.care.CareMode.label(CareStore.mode(c))).append('\n');
        s.append("plan_activado=").append(config.enabled).append('\n');
        s.append("accesibilidad_conectada=").append(FocusAccessibilityService.connected()).append('\n');
        s.append("apps_de_ocio=").append(config.watchedApps.size()).append('\n');
        try {
            RuleStore rules = RuleStore.get(c);
            s.append("bloqueos_con_nombre=").append(rules.rules().size()).append('\n');
        } catch (RuntimeException ignored) {
        }
        try {
            s.append("modo_estricto_candado=").append(StrictAdmin.lockActive(c)).append('\n');
        } catch (RuntimeException ignored) {
        }

        s.append("acceso_de_uso=").append(es.foco.next.log.UsageContrast.allowed(c)).append('\n');
        try {
            s.append("dns=").append(es.foco.next.core.DnsGuard.providerLabel(DnsMonitor.provider(c))).append('\n');
        } catch (RuntimeException ignored) {
        }
        try {
            NextDnsSync.Snapshot n = NextDnsSync.snapshot(c);
            if (n.at > 0
                    && java.time.Instant.ofEpochMilli(n.at).atZone(ZoneId.systemDefault()).toLocalDate().equals(day))
                s.append("nextdns_bloqueadas=").append(n.blocked).append(" de ").append(n.total).append('\n');
        } catch (RuntimeException ignored) {
        }
        // Retos de Lab y uso real por app: el mismo registro que "Exportar mis 7 días · CSV".
        es.foco.next.log.InspectorLog.drainLab(c);
        android.content.pm.PackageManager pm = c.getPackageManager();
        // v32 (R9): las horas naturales del día lógico (de 04:00 a 03:59 del día siguiente).
        boolean systemTotal = es.foco.next.log.UsageContrast.allowed(c);
        String csv =
                es.foco.next.log.BlockLog.await(
                        c,
                        db -> {
                            List<es.foco.next.log.LogDatabase.InspectorRow> rows =
                                    es.foco.next.log.InspectorLog.rows(c, db, day.plusDays(1), 2, config);
                            return leisure(rows, day, systemTotal)
                                    + "\nREGISTRO DEL DÍA (CSV: tiempos, bloqueos, retos de Lab)\n"
                                    + es.foco.next.log.InspectorLog.csv(
                                            db,
                                            day.toString(),
                                            p -> {
                                                try {
                                                    return pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString();
                                                } catch (Exception e) {
                                                    return p;
                                                }
                                            },
                                            rows,
                                            false,
                                            (d, h) -> es.foco.next.log.InspectorLog.inLogicalDay(day, d, h));
                        },
                        15_000);
        s.append(csv == null ? "\nREGISTRO DEL DÍA\nno_disponible\n" : csv);
        return s.toString();
    }
}
