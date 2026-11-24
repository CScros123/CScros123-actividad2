package es.foco.next.runtime;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.care.Awareness;
import es.foco.next.log.InspectorLog;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v33: lo que se decide en la pregunta «¿Me aporta?» (preferencias "foco-conciencia"). Las reglas
 * están en {@link Awareness}; la ventana es {@code ui.AwarenessActivity}; quien pregunta es la
 * Accesibilidad. Las notas son tuyas: se quedan en el teléfono y salen en tu informe diario.
 */
public final class AwarenessStore {
    private AwarenessStore() {}

    private static final String PREFS = "foco-conciencia", CHANNEL = "foco-conciencia";
    private static final int ID_WORTH = 7431, ID_WEEK = 7432;
    public static final String ACTION_WORTH = "es.foco.next.action.WORTH";
    public static final String EXTRA_PKG = "pkg", EXTRA_ANSWER = "respuesta";
    private static final int KEEP_NOTES = 300;

    /** Dueño de la música tranquila mientras dura la ventana (y 3 min tras decir «no»). */
    public static final Object MUSIC = new Object();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Runnable STOP_MUSIC = () -> CalmAudio.leave(MUSIC);
    private static long lastOpen;

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String today() {
        return RoutineStore.today().toString();
    }

    // ---------------- La ventana ----------------

    /** Evita abrir la ventana en bucle (como mucho una vez cada 20 s). */
    public static synchronized boolean canOpen() {
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastOpen > 0 && now - lastOpen < 20_000) return false;
        lastOpen = now;
        return true;
    }

    /** Música tranquila mientras se decide (pide el foco de audio: el video suele pararse). */
    public static void musicOn(Context c) {
        MAIN.removeCallbacks(STOP_MUSIC);
        try {
            CalmAudio.enter(c, MUSIC, () -> {}, true);
        } catch (RuntimeException ignored) {
        }
    }

    /** Apaga la música ya, o dentro de {@code delayMs}. */
    public static void musicOff(long delayMs) {
        MAIN.removeCallbacks(STOP_MUSIC);
        if (delayMs <= 0) STOP_MUSIC.run();
        else MAIN.postDelayed(STOP_MUSIC, delayMs);
    }

    // ---------------- Sesión y 5 min extra ----------------

    /** Fin de los 5 min tras un «sí» para esa app (0 si no hay). */
    public static long extendedUntil(Context c, String pkg) {
        SharedPreferences p = prefs(c);
        return pkg != null && pkg.equals(p.getString("extApp", "")) ? p.getLong("extHasta", 0) : 0;
    }

    /** Sesión nueva: lo de la anterior no vale. */
    public static void newSession(Context c, String pkg) {
        SharedPreferences p = prefs(c);
        if (pkg != null && pkg.equals(p.getString("extApp", ""))) p.edit().remove("extApp").remove("extHasta").apply();
    }

    /** ¿Dijiste «no» a esta app hace poco? */
    public static boolean reopenedAfterNo(Context c, String pkg) {
        long at = prefs(c).getLong("no_" + pkg, 0);
        return at > 0 && System.currentTimeMillis() - at < Awareness.REOPEN_MS;
    }

    /** Minutos desde el último «no» a esta app (-1 si no hubo o fue hace mucho). */
    public static long minutesSinceNo(Context c, String pkg) {
        long at = prefs(c).getLong("no_" + pkg, 0);
        long d = System.currentTimeMillis() - at;
        return at > 0 && d < Awareness.REOPEN_MS ? d / Awareness.MIN : -1;
    }

    // ---------------- Respuestas ----------------

    /** «No»: se sale. Cuenta como decisión consciente. */
    public static int answerNo(Context c, String pkg, long sessionMs) {
        SharedPreferences p = prefs(c);
        String day = today();
        int n = p.getInt("no_dia_" + day, 0) + 1;
        p.edit()
                .putLong("no_" + pkg, System.currentTimeMillis())
                .putInt("no_dia_" + day, n)
                .remove("extApp")
                .remove("extHasta")
                .apply();
        InspectorLog.event(c, InspectorLog.CONCIENCIA, pkg, System.currentTimeMillis(), sessionMs, 1, 0, "no", null);
        return n;
    }

    /**
     * v34: ocio de hoy (día de Foco, desde las 04:00) en las apps que vigila Foco, según el sistema
     * (Acceso de uso). 0 si no hay permiso o falla: entonces la pregunta va a los 15 min de siempre.
     * Se llama una vez por sesión, no en cada evento.
     */
    public static long leisureTodayMs(Context c, es.foco.next.core.AppConfig config) {
        try {
            if (config == null || !es.foco.next.log.UsageContrast.allowed(c)) return 0;
            LocalDateTime now = LocalDateTime.now();
            LocalDate day = now.getHour() < 4 ? now.toLocalDate().minusDays(1) : now.toLocalDate();
            long start = day.atTime(4, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            android.app.usage.UsageStatsManager usm = c.getSystemService(android.app.usage.UsageStatsManager.class);
            if (usm == null) return 0;
            long sum = 0;
            for (java.util.Map.Entry<String, android.app.usage.UsageStats> e :
                    usm.queryAndAggregateUsageStats(start, System.currentTimeMillis()).entrySet()) {
                String pkg = e.getKey();
                if ((config.enabled || AbsoluteRules.social(pkg))
                        && es.foco.next.core.FocusEngine.watched(config, pkg))
                    sum += e.getValue().getTotalTimeInForeground();
            }
            return sum;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** «Sí» con su nota: 5 min más para esa app. */
    public static void answerYes(Context c, String pkg, String label, Awareness.Kind kind, String note, long sessionMs) {
        answerYes(c, pkg, label, kind, note, sessionMs, Awareness.EXTEND_MS);
    }

    /** v34: «Sí» con su nota; la ventana dura {@code extendMs} (3 min con 2 h o más de ocio hoy). */
    public static void answerYes(
            Context c, String pkg, String label, Awareness.Kind kind, String note, long sessionMs, long extendMs) {
        SharedPreferences p = prefs(c);
        String day = today();
        long now = System.currentTimeMillis();
        try {
            JSONArray all = new JSONArray(p.getString("notas", "[]"));
            JSONObject o = new JSONObject();
            o.put("d", day);
            o.put("t", LocalTime.now().withSecond(0).withNano(0).toString());
            o.put("app", label == null ? pkg : label);
            o.put("k", kind.name());
            o.put("n", note.trim());
            all.put(o);
            JSONArray keep = new JSONArray();
            for (int i = Math.max(0, all.length() - KEEP_NOTES); i < all.length(); i++) keep.put(all.get(i));
            p.edit()
                    .putString("notas", keep.toString())
                    .putInt("si_dia_" + day, p.getInt("si_dia_" + day, 0) + 1)
                    .putString("extApp", pkg)
                    .putLong("extHasta", now + extendMs)
                    .apply();
        } catch (Exception e) {
            p.edit().putString("extApp", pkg).putLong("extHasta", now + extendMs).apply();
        }
        // El texto de la nota no va al registro exportable: solo el tipo.
        InspectorLog.event(c, InspectorLog.CONCIENCIA, pkg, now, sessionMs, 1, 0, "si;" + kind.name().toLowerCase(), null);
    }

    public static int noCount(Context c, String day) {
        return prefs(c).getInt("no_dia_" + day, 0);
    }

    public static int yesCount(Context c, String day) {
        return prefs(c).getInt("si_dia_" + day, 0);
    }

    /** Notas de ese día: {hora, app, tipo, texto}. */
    public static List<String[]> notes(Context c, String day) {
        List<String[]> out = new ArrayList<>();
        try {
            JSONArray all = new JSONArray(prefs(c).getString("notas", "[]"));
            for (int i = 0; i < all.length(); i++) {
                JSONObject o = all.getJSONObject(i);
                if (!day.equals(o.optString("d"))) continue;
                out.add(new String[] {o.optString("t"), o.optString("app"), Awareness.Kind.parse(o.optString("k")).label, o.optString("n")});
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    // ---------------- «¿Valió la pena?» al salir ----------------

    /** Aviso con tres botones (un toque). Nunca de noche ni con No molestar. */
    public static void askWorth(Context c, String pkg, long sessionMs) {
        Context app = c.getApplicationContext();
        SharedPreferences p = prefs(app);
        String day = today();
        int asked = p.getInt("vale_pregunta_" + day, 0);
        if (!Awareness.askWorth(sessionMs, false, asked)) return;
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (AbsoluteRules.esNocheAbsoluta(LocalDateTime.now())) return;
        try {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm == null || nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL, "¿Me aporta?", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            String label = label(app, pkg);
            Notification.Builder b =
                    new Notification.Builder(app, CHANNEL)
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setContentTitle("🦉 " + Awareness.duration(sessionMs) + " en " + label)
                            .setContentText("¿Valió la pena?")
                            .setAutoCancel(true)
                            .setTimeoutAfter(60 * Awareness.MIN);
            int i = 0;
            for (Awareness.Worth w : Awareness.Worth.values()) {
                Intent it = new Intent(app, OwlWatch.class).setAction(ACTION_WORTH).putExtra(EXTRA_PKG, pkg).putExtra(EXTRA_ANSWER, w.name());
                PendingIntent pi = PendingIntent.getBroadcast(app, ID_WORTH * 10 + i++, it, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                String text = w == Awareness.Worth.SI ? "Sí" : w == Awareness.Worth.NO ? "No" : "Más o menos";
                b.addAction(new Notification.Action.Builder(null, text, pi).build());
            }
            nm.notify(ID_WORTH, b.build());
            p.edit().putInt("vale_pregunta_" + day, asked + 1).apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** Respuesta desde el aviso (OwlWatch la recibe). */
    public static void answerWorth(Context c, String pkg, String answer) {
        Awareness.Worth w = Awareness.Worth.parse(answer);
        if (w == null || pkg == null) return;
        SharedPreferences p = prefs(c);
        String day = today();
        String key = "vale_" + day + "_" + w.name();
        p.edit().putInt(key, p.getInt(key, 0) + 1).putString("vale_app_" + day + "_" + w.name(), add(p.getString("vale_app_" + day + "_" + w.name(), ""), pkg)).apply();
        InspectorLog.event(c, InspectorLog.VALIO, pkg, System.currentTimeMillis(), 0, 1, 0, w.name().toLowerCase(), null);
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(ID_WORTH);
        } catch (RuntimeException ignored) {
        }
    }

    private static String add(String list, String pkg) {
        return list.isEmpty() ? pkg : list + "," + pkg;
    }

    /** {sí, más o menos, no} de ese día. */
    public static int[] worth(Context c, String day) {
        SharedPreferences p = prefs(c);
        return new int[] {p.getInt("vale_" + day + "_SI", 0), p.getInt("vale_" + day + "_MAS_O_MENOS", 0), p.getInt("vale_" + day + "_NO", 0)};
    }

    /** Apps que respondiste con esa respuesta ese día (paquetes, con repetición). */
    public static String worthApps(Context c, String day, Awareness.Worth w) {
        return prefs(c).getString("vale_app_" + day + "_" + w.name(), "");
    }

    // ---------------- Resumen del domingo ----------------

    /** Domingo desde las 18:00: tu semana en una frase, una vez (presupuesto común del búho). */
    public static void weekly(Context c) {
        try {
            Context app = c.getApplicationContext();
            LocalDateTime now = LocalDateTime.now();
            if (now.getDayOfWeek() != DayOfWeek.SUNDAY || now.getHour() < 18) return;
            SharedPreferences p = prefs(app);
            String week = RoutineStore.today().with(DayOfWeek.MONDAY).toString();
            if (week.equals(p.getString("semanaAvisada", ""))) return;
            int no = 0, si = 0;
            String last = "";
            LocalDate d = RoutineStore.today().with(DayOfWeek.MONDAY);
            for (int i = 0; i < 7; i++, d = d.plusDays(1)) {
                no += noCount(app, d.toString());
                si += yesCount(app, d.toString());
                for (String[] n : notes(app, d.toString())) last = n[3];
            }
            if (no + si == 0) return;
            if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            if (!OwlBudget.take(app, false)) return;
            p.edit().putString("semanaAvisada", week).apply();
            String text = "Esta semana dijiste «no» " + no + (no == 1 ? " vez" : " veces") + " y escribiste " + si + (si == 1 ? " nota" : " notas") + ".";
            if (!last.isEmpty()) text += " Lo último que ganaste: «" + (last.length() > 120 ? last.substring(0, 120) + "…" : last) + "»";
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "¿Me aporta?", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
            Intent open = new Intent().setClassName(app, "es.foco.next.ui.HomeActivity").putExtra(RoutineStore.EXTRA_CORE, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            nm.notify(ID_WEEK, new Notification.Builder(app, CHANNEL)
                    .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                    .setContentTitle("🦉 Tu semana consciente")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(PendingIntent.getActivity(app, ID_WEEK, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .setAutoCancel(true)
                    .build());
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- Utilidades ----------------

    public static String label(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** Una app productiva instalada al azar (tus apps productivas y las conocidas), o null. */
    public static String randomProductive(Context c, String except) {
        try {
            PackageManager pm = c.getPackageManager();
            java.util.Set<String> pool = new java.util.LinkedHashSet<>(es.foco.next.core.care.GoodApps.KNOWN);
            try {
                pool.addAll(es.foco.next.data.Repository.get(c).config().productiveApps);
            } catch (RuntimeException ignored) {
            }
            List<String> ok = new ArrayList<>();
            for (String p : pool)
                if (!p.equals(except) && !p.equals(c.getPackageName()) && pm.getLaunchIntentForPackage(p) != null) ok.add(p);
            if (ok.isEmpty()) return null;
            return ok.get(new java.util.Random().nextInt(ok.size()));
        } catch (RuntimeException e) {
            return null;
        }
    }
}
