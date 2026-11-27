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
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.care.Rewards;
import es.foco.next.core.care.Routine;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v31: cupones, semana perfecta, progreso y avisos que inspiran (las reglas puras están en {@link
 * Rewards}). Preferencias "foco-premios". El cupón en uso abre UNA app hasta una hora; la decide el
 * servicio de Accesibilidad con {@link #grantActive}.
 */
public final class RewardStore {
    private RewardStore() {}

    private static final String PREFS = "foco-premios", CHANNEL = "foco-premios";
    private static final int NOTE_PROGRESS = 7401, NOTE_GRANT = 7402;

    /** Como mucho 3 avisos del búho al día por esto, nunca de noche ni con No molestar. */
    private static final int MAX_NOTES = 3;

    private static SharedPreferences prefs(Context c) {
        CareStore.install(c); // v32: horario de la noche siempre desde preferencias
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- Cupones ----------------

    public static List<Rewards.Coupon> coupons(Context c) {
        return Rewards.valid(
                Rewards.decode(prefs(c).getString("cupones", "")), RoutineStore.today());
    }

    private static void save(Context c, List<Rewards.Coupon> l) {
        prefs(c).edit().putString("cupones", Rewards.encode(l)).apply();
    }

    public static int usedToday(Context c) {
        SharedPreferences p = prefs(c);
        return RoutineStore.today().toString().equals(p.getString("usadoDia", ""))
                ? p.getInt("usados", 0)
                : 0;
    }

    private static boolean night(Context c) {
        return AbsoluteRules.night(LocalDateTime.now(), AppKinds.autoTime(c));
    }

    private static boolean morningPending(Context c) {
        if (!RoutineStore.enabled(c) || RoutineStore.current(c) != Routine.Kind.MORNING)
            return false;
        return Routine.remainingCount(
                        RoutineStore.tasks(c, Routine.Kind.MORNING),
                        RoutineStore.done(c, Routine.Kind.MORNING))
                > 0;
    }

    /** Motivo por el que ese tipo de cupón no se puede usar ahora, o null. */
    public static String whyNot(Context c, boolean week) {
        Rewards.Coupon k = Rewards.pick(coupons(c), week);
        boolean penalty = RoutineStore.activePenaltyUntil(c) > System.currentTimeMillis();
        return Rewards.whyNot(k, night(c), penalty, morningPending(c), usedToday(c));
    }

    /** Canjea un cupón para {@code pkg}. Devuelve null si va bien, o el motivo. */
    public static String redeem(Context c, String pkg, String label, boolean week) {
        String why = whyNot(c, week);
        if (why != null) return why;
        if (grantLeftMs(c) > 0) return "Ya tienes un cupón en uso.";
        List<Rewards.Coupon> l = coupons(c);
        Rewards.Coupon k = Rewards.pick(l, week);
        int min = Rewards.minutes(AbsoluteRules.social(pkg));
        long until = System.currentTimeMillis() + min * 60_000L;
        SharedPreferences p = prefs(c);
        String today = RoutineStore.today().toString();
        SharedPreferences.Editor e =
                p.edit()
                        .putString("cupones", Rewards.encode(Rewards.without(l, k.id)))
                        .putString("app", pkg)
                        .putString("appNombre", label)
                        .putBoolean(
                                "semana",
                                k.week) // v32 (R5): el de semana perfecta salta más reglas
                        .putLong("hasta", until);
        if (!k.week) e.putString("usadoDia", today).putInt("usados", usedToday(c) + 1);
        e.apply();
        es.foco.next.log.InspectorLog.event(
                c,
                es.foco.next.log.InspectorLog.INCIDENTE,
                pkg,
                min,
                (k.week ? "cupon_semana" : "cupon") + ":" + min + "min",
                "cupon|" + k.id);
        notifyGrant(c, label, until);
        FocusAccessibilityService.checkpoint();
        return null;
    }

    /** ¿El cupón en uso abre esta app ahora? */
    public static boolean grantActive(Context c, String pkg) {
        SharedPreferences p = prefs(c);
        return pkg != null
                && pkg.equals(p.getString("app", ""))
                && System.currentTimeMillis() < p.getLong("hasta", 0);
    }

    /** ¿El cupón en uso es de semana perfecta? */
    public static boolean grantWeek(Context c) {
        return grantLeftMs(c) > 0 && prefs(c).getBoolean("semana", false);
    }

    public static long grantLeftMs(Context c) {
        return Math.max(0, prefs(c).getLong("hasta", 0) - System.currentTimeMillis());
    }

    public static String grantApp(Context c) {
        return grantLeftMs(c) > 0 ? prefs(c).getString("appNombre", "") : "";
    }

    // ---------------- Días completos, racha y desbloqueos ----------------

    private static Set<String> completeDays(Context c) {
        return new HashSet<>(prefs(c).getStringSet("dias", new HashSet<>()));
    }

    public static Set<String> unlocked(Context c) {
        return new HashSet<>(prefs(c).getStringSet("desbloqueado", new HashSet<>()));
    }

    public static boolean has(Context c, String id) {
        return unlocked(c).contains(id);
    }

    /** Días seguidos al 100 %: hasta hoy si hoy está completo; si no, hasta ayer. */
    public static int streak(Context c) {
        Set<String> days = completeDays(c);
        LocalDate d = RoutineStore.today();
        if (!days.contains(d.toString())) d = d.minusDays(1);
        int n = 0;
        while (days.contains(d.toString()) && n < 400) {
            n++;
            d = d.minusDays(1);
        }
        return n;
    }

    /**
     * Tras marcar una tarea: si hoy quedó todo hecho, cupón, racha, desbloqueos y el búho lo
     * celebra (una vez al día).
     */
    public static void onProgress(Context c) {
        try {
            LocalDate today = RoutineStore.today();
            Set<String> days = completeDays(c);
            if (days.contains(today.toString()))
                return; // ya contado hoy (barato: se llama a menudo)
            // v32 (R2): cuenta aunque haya una pausa activa (la pausa solo anula la semana
            // perfecta).
            if (!RoutineStore.dayComplete(c, today)) return;
            days.add(today.toString());
            // Guarda como mucho 60 días.
            String limit = today.minusDays(60).toString();
            days.removeIf(s -> s.compareTo(limit) < 0);
            List<Rewards.Coupon> before = coupons(c);
            List<Rewards.Coupon> l = Rewards.earnDay(before, today, today);
            boolean earned = l.size() > Rewards.valid(before, today).size();
            prefs(c).edit().putStringSet("dias", days).apply();
            int streak = streak(c);
            if (Rewards.bonusAt(streak))
                l = Rewards.earnBonus(l, today); // cada 7 días seguidos, uno extra
            save(c, l);
            Set<String> have = unlocked(c);
            List<String> news = Rewards.newUnlocks(streak, have);
            if (!news.isEmpty()) {
                have.addAll(news);
                prefs(c).edit().putStringSet("desbloqueado", have).apply();
            }
            String text =
                    (earned ? "Cupón de 30 min ganado. " : "Ya tienes 3 cupones guardados. ")
                            + "Llevas "
                            + streak
                            + (streak == 1 ? " día" : " días")
                            + " al 100 %.";
            if (!news.isEmpty())
                text += " Desbloqueaste: " + Rewards.unlockName(news.get(news.size() - 1)) + ".";
            note(c, "Día completo ✓", text, true);
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- Semana perfecta ----------------

    /**
     * Intentos de evasión (Accesibilidad apagada, hora cambiada, DNS apagado a mano, otro inicio…).
     */
    public static void markEvasion(Context c, String detail) {
        try {
            if (detail == null) return;
            String d = detail.toLowerCase(java.util.Locale.ROOT);
            boolean evasive =
                    d.startsWith("accesibilidad_desactivada")
                            || d.startsWith("cambio_manual_hora")
                            || d.startsWith("hora_automatica_apagada")
                            || d.startsWith("launcher_cambiado")
                            || d.startsWith("vpn_activa")
                            || (d.startsWith("dns_apagado") && !dnsFallback(c));
            if (!evasive) return;
            String key = "evasion_" + RoutineStore.today();
            SharedPreferences p = prefs(c);
            p.edit().putInt(key, p.getInt(key, 0) + 1).apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** El respaldo de 10 min de NextDNS no cuenta como evasión. */
    private static boolean dnsFallback(Context c) {
        return c.getApplicationContext()
                .getSharedPreferences("foco-dns-auto", Context.MODE_PRIVATE)
                .getBoolean("fallback", false);
    }

    public static int evasions(Context c, LocalDate d) {
        return prefs(c).getInt("evasion_" + d, 0);
    }

    /** Fotos que faltan ese día: tareas hechas que pedían foto y no la tienen. */
    private static int photosMissing(Context c, LocalDate d) {
        int n = 0;
        java.io.File dir = RoutineStore.evidenceDir(c);
        for (String b : RoutineStore.BLOCKS) {
            List<Routine.Task> t = RoutineStore.blockTasks(c, b, d);
            Set<Integer> done = RoutineStore.blockDone(c, b, d);
            for (int i = 0; i < t.size(); i++)
                if (t.get(i).photo
                        && done.contains(i)
                        && !new java.io.File(dir, d + "_" + b + "_" + i + ".webp").exists()) n++;
        }
        return n;
    }

    private static Rewards.DayCheck dayCheck(Context c, LocalDate d) {
        int[] t = RoutineStore.totals(c, d);
        return new Rewards.DayCheck(
                t[0],
                t[1],
                RoutineStore.planned(c, d),
                photosMissing(c, d),
                RoutineStore.penalties(c, d),
                evasions(c, d));
    }

    /** Días perfectos de esta semana (lunes a hoy) para mostrar el camino a la semana perfecta. */
    public static int perfectDaysThisWeek(Context c) {
        LocalDate today = RoutineStore.today();
        LocalDate d = today.with(java.time.DayOfWeek.MONDAY);
        int n = 0;
        while (!d.isAfter(today)) {
            if (dayCheck(c, d).perfect()) n++;
            d = d.plusDays(1);
        }
        return n;
    }

    /** Revisa la semana que acaba de cerrar y da los 2 cupones si fue perfecta (una vez). */
    public static void checkWeek(Context c) {
        try {
            LocalDate today = RoutineStore.today();
            LocalDate monday = Rewards.lastMonday(today);
            SharedPreferences p = prefs(c);
            if (monday.toString().equals(p.getString("semanaRevisada", ""))) return;
            p.edit().putString("semanaRevisada", monday.toString()).apply();
            List<Rewards.DayCheck> days = new ArrayList<>();
            for (int i = 0; i < 7; i++) days.add(dayCheck(c, monday.plusDays(i)));
            if (!Rewards.perfectWeek(days)) return;
            save(c, Rewards.earnWeek(coupons(c), monday, today));
            note(
                    c,
                    "¡Semana perfecta! 🏆",
                    "Todas tus tareas, con sus fotos y sin atajos. Ganaste 2 cupones especiales: 1 h para lo que quieras esta semana.",
                    true);
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- Avisos que inspiran ----------------

    /** A media tarde, si falta poco para el cupón (una vez al día). */
    public static void nudge(Context c) {
        try {
            int h = LocalDateTime.now().getHour();
            if (h < 16 || h >= 20) return;
            SharedPreferences p = prefs(c);
            String today = RoutineStore.today().toString();
            if (today.equals(p.getString("animoDia", ""))) return;
            int[] t = RoutineStore.totals(c, RoutineStore.today());
            int left = t[0] - t[1];
            if (t[0] == 0 || left <= 0 || left > 3) return;
            p.edit().putString("animoDia", today).apply();
            note(
                    c,
                    left == 1 ? "Te falta 1 tarea" : "Te faltan " + left + " tareas",
                    "Estás cerca: termina y ganas tu cupón de 30 min. "
                            + (streak(c) > 0 ? "Tu racha va en " + streak(c) + "." : ""),
                    false);
        } catch (RuntimeException ignored) {
        }
    }

    private static void note(Context c, String title, String text, boolean important) {
        Context app = c.getApplicationContext();
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        if (AbsoluteRules.esNocheAbsoluta(LocalDateTime.now())) return;
        SharedPreferences p = prefs(c);
        String today = RoutineStore.today().toString();
        int sent = today.equals(p.getString("avisosDia", "")) ? p.getInt("avisos", 0) : 0;
        if (sent >= MAX_NOTES) return;
        if (!OwlBudget.take(app, false)) return; // v32 (R10): presupuesto común de avisos
        try {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm == null
                    || nm.getCurrentInterruptionFilter()
                            > NotificationManager.INTERRUPTION_FILTER_ALL) return;
            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL,
                            "Logros y ánimo",
                            important
                                    ? NotificationManager.IMPORTANCE_DEFAULT
                                    : NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            Intent open =
                    new Intent()
                            .setClassName(app, "es.foco.next.ui.HomeActivity")
                            .putExtra(RoutineStore.EXTRA_CORE, true)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tap =
                    PendingIntent.getActivity(
                            app,
                            NOTE_PROGRESS,
                            open,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            nm.notify(
                    NOTE_PROGRESS,
                    new Notification.Builder(app, CHANNEL)
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setContentTitle("🦉 " + title)
                            .setContentText(text)
                            .setStyle(new Notification.BigTextStyle().bigText(text))
                            .setContentIntent(tap)
                            .setAutoCancel(true)
                            .build());
            p.edit().putString("avisosDia", today).putInt("avisos", sent + 1).apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** Aviso fijo mientras dura el cupón (cuándo termina). */
    private static void notifyGrant(Context c, String label, long until) {
        Context app = c.getApplicationContext();
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm == null) return;
            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL + "-uso", "Cupón en uso", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
            String end =
                    java.time.Instant.ofEpochMilli(until)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalTime()
                            .withSecond(0)
                            .withNano(0)
                            .toString();
            nm.notify(
                    NOTE_GRANT,
                    new Notification.Builder(app, CHANNEL + "-uso")
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setContentTitle("🎟 Cupón: " + label)
                            .setContentText("Disfrútalo. Termina a las " + end + ".")
                            .setWhen(until)
                            .setUsesChronometer(true)
                            .setChronometerCountDown(true)
                            .setTimeoutAfter(Math.max(1, until - System.currentTimeMillis()))
                            .setOngoing(true)
                            .build());
        } catch (RuntimeException ignored) {
        }
    }
}
