package es.foco.next.runtime;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.home.Companion;
import es.foco.next.core.home.OwlCare;
import es.foco.next.log.InspectorLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Los cuatro cuidados del búho que salieron del registro del 21/11 (reglas puras en {@link
 * OwlCare}):
 *
 * <ol>
 *   <li>Despertar: el primer desbloqueo de la mañana abre el ritual del búho en el inicio.
 *   <li>Inquietud: 6 aperturas de ocio en 2 min → "¿Todo bien?" con "Respirar 30 s". Sin bloqueo.
 *   <li>Foco apagado: si la Accesibilidad está apagada, el búho lo dice (como mucho cada 6 h, nunca
 *       de noche); al volver, cuenta el hueco con calma ("Estuve apagado de 5:00 a 10:07").
 *   <li>Inicio perdido: si Foco Home dejó de ser el inicio, un aviso al día para volver a ponerlo.
 * </ol>
 *
 * Es también el receptor de una alarma inexacta cada ~2 h (y al arrancar el teléfono): es lo único
 * que puede avisar cuando la Accesibilidad está apagada. Ningún método lanza.
 */
public final class OwlWatch extends BroadcastReceiver {
    static final String PREFS = "foco-owlwatch";
    private static final String CHANNEL = "foco-acompanamiento", ACTION_CHECK = "es.foco.next.action.OWL_CHECK";
    private static final int ID_OFF = 7410, ID_ROLE = 7411, ID_NIGHT = 7413;
    public static final String EXTRA_WAKE = "es.foco.next.extra.OWL_WAKE",
            EXTRA_ROLE = "es.foco.next.extra.OWL_ROLE";

    private static SharedPreferences prefs(Context c) {
        CareStore.install(c); // v32: horario de la noche siempre desde preferencias
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- Alarma y comprobación ----------------

    /** Programa la comprobación periódica (inexacta: no pide permiso de alarmas exactas). */
    public static void ensure(Context c) {
        try {
            Context app = c.getApplicationContext();
            AlarmManager am = app.getSystemService(AlarmManager.class);
            PendingIntent pi =
                    PendingIntent.getBroadcast(
                            app, 7412, new Intent(app, OwlWatch.class).setAction(ACTION_CHECK),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(
                    AlarmManager.RTC, System.currentTimeMillis() + 30 * 60_000L, 2 * AlarmManager.INTERVAL_HOUR, pi);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        // Foco Core: alarma de la próxima hora límite (aviso o fin del margen).
        if (i != null && ACTION_CORE.equals(i.getAction())) {
            RoutineStore.sweep(c, true);
            return;
        }
        ensure(c);
        check(c);
        RoutineStore.scheduleNext(c); // también tras reiniciar el teléfono o actualizar Foco
    }

    static final String ACTION_CORE = "es.foco.next.action.CORE_CHECK";

    /** ¿Accesibilidad de Foco activada en Ajustes? */
    public static boolean accessibilityEnabled(Context c) {
        try {
            if (FocusAccessibilityService.connected()) return true;
            String list = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return list != null && list.contains(c.getPackageName() + "/");
        } catch (RuntimeException e) {
            return true; // si no se puede saber, no se molesta
        }
    }

    /** Foco apagado e inicio perdido. Lo llaman la alarma, el arranque y el inicio. */
    public static void check(Context c) {
        try {
            Context app = c.getApplicationContext();
            long now = System.currentTimeMillis();
            CareStore.night(app);
            boolean night = AbsoluteRules.night(LocalDateTime.now(), AppKinds.autoTime(app));
            SharedPreferences p = prefs(app);
            if (!accessibilityEnabled(app)) {
                if (p.getLong("offSince", 0) <= 0) {
                    long alive = p.getLong("aliveAt", 0);
                    p.edit().putLong("offSince", alive > 0 ? alive : now).apply();
                }
                // v32 (R3): desactivada en los Ajustes de Android = la apagó alguien, no el sistema.
                // Una vez por hueco (onceKey con su inicio).
                InspectorLog.event(app, InspectorLog.INCIDENTE, "", 0, "accesibilidad_desactivada",
                        "inc|acc_off|" + p.getLong("offSince", now));
                if (OwlCare.offNoticeDue(p.getLong("offNoticeAt", -1), now, night)) {
                    p.edit().putLong("offNoticeAt", now).apply();
                    notify(app, ID_OFF, "Foco está en pausa", OwlCare.OFF_TEXT, "Encenderme",
                            new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            }
            roleCheck(app, HomeRole.held(app));
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- 3. Foco apagado: lo llama la Accesibilidad ----------------

    /** Latido (como mucho uno por minuto): sirve para estimar desde cuándo estuvo apagado. */
    private static long lastAlive;

    static void alive(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastAlive < 60_000) return;
        lastAlive = now;
        try {
            prefs(c).edit().putLong("aliveAt", now).apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** La Accesibilidad se desconecta (la apagaron o el sistema la paró). */
    static void serviceOff(Context c) {
        try {
            SharedPreferences p = prefs(c);
            if (p.getLong("offSince", 0) <= 0) p.edit().putLong("offSince", System.currentTimeMillis()).commit();
        } catch (RuntimeException ignored) {
        }
    }

    /** La Accesibilidad vuelve: si el hueco fue de 10 min o más, el búho lo cuenta al volver al inicio. */
    static void serviceOn(Context c) {
        try {
            Context app = c.getApplicationContext();
            SharedPreferences p = prefs(app);
            long off = p.getLong("offSince", 0), now = System.currentTimeMillis();
            // Con el teléfono apagado Foco no "estuvo apagado": solo cuenta desde que arrancó.
            long booted = now - android.os.SystemClock.elapsedRealtime();
            if (off > 0 && off < booted) off = booted;
            SharedPreferences.Editor e = p.edit().putLong("offSince", 0).putLong("aliveAt", now);
            if (OwlCare.gapWorthTelling(off, now)) {
                e.putString("gapText", OwlCare.gapText(off, now, ZoneId.systemDefault()))
                        .putLong("gapAt", now)
                        .putBoolean("gapClosed", false);
                InspectorLog.event(app, InspectorLog.INCIDENTE, "", now, now - off, 1, (now - off) / 60_000,
                        "foco_apagado_min=" + (now - off) / 60_000, "inc|apagado|" + off);
            }
            e.apply();
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(ID_OFF);
        } catch (RuntimeException ignored) {
        }
    }

    /** Texto del hueco para el inicio (6 h tras volver) o null. */
    public static String gapNotice(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("gapClosed", true)) return null;
        if (System.currentTimeMillis() - p.getLong("gapAt", 0) > 6 * 60 * 60_000L) return null;
        return p.getString("gapText", null);
    }

    public static void closeGap(Context c) {
        prefs(c).edit().putBoolean("gapClosed", true).apply();
    }

    // ---------------- 4. Inicio perdido ----------------

    /** Con el estado actual del inicio predeterminado (lo llama HomeRole.check y la alarma). */
    static void roleCheck(Context c, boolean heldNow) {
        try {
            Context app = c.getApplicationContext();
            SharedPreferences p = prefs(app);
            if (heldNow && !p.getBoolean("everHeld", false)) p.edit().putBoolean("everHeld", true).apply();
            String today = LocalDate.now().toString();
            if (!OwlCare.roleNoticeDue(p.getBoolean("everHeld", false), heldNow, p.getString("roleNoticeDay", ""), today)) {
                if (heldNow) {
                    NotificationManager nm = app.getSystemService(NotificationManager.class);
                    if (nm != null) nm.cancel(ID_ROLE);
                }
                return;
            }
            if (AbsoluteRules.night(LocalDateTime.now(), AppKinds.autoTime(app))) return;
            p.edit().putString("roleNoticeDay", today).apply();
            notify(app, ID_ROLE, "El inicio cambió", OwlCare.ROLE_TEXT, "Volver a Foco Home",
                    new Intent().setClassName(app, "es.foco.next.ui.HomeActivity").putExtra(EXTRA_ROLE, true));
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- 1. Despertar ----------------

    /** Primer desbloqueo de la mañana: el búho abre Foco Core en el inicio (una vez al día). */
    static void unlocked(Context c) {
        nightRest(c);
        try {
            Context app = c.getApplicationContext();
            SharedPreferences p = prefs(app);
            if (!OwlCare.morningDue(LocalDateTime.now(), p.getString("ritualDay", ""), true)) return;
            p.edit().putString("ritualDay", LocalDate.now().toString()).apply();
            // Un servicio de Accesibilidad puede abrir una actividad desde segundo plano.
            app.startActivity(
                    new Intent()
                            .setClassName(app, "es.foco.next.ui.HomeActivity")
                            .putExtra(EXTRA_WAKE, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- 2. Inquietud ----------------

    private static final ArrayDeque<Long> opens = new ArrayDeque<>();
    private static long restlessAt = -1;

    /** Una apertura de una app de ocio (la Accesibilidad, al cambiar la app de delante). */
    static synchronized void leisureOpened(Context c, String pkg) {
        nightRest(c);
        long now = System.currentTimeMillis();
        opens.addLast(now);
        while (!opens.isEmpty() && now - opens.peekFirst() > OwlCare.RESTLESS_WINDOW_MS) opens.pollFirst();
        if (!OwlCare.restless(new ArrayList<>(opens), now, restlessAt)) return;
        restlessAt = now;
        opens.clear();
        InspectorLog.event(c, InspectorLog.INQUIETUD, pkg, now, 0, 1, 0, "6_aperturas_2_min", null);
        CompanionNotifier.trigger(c, Companion.Trigger.RESTLESS, -1, pkg);
    }

    // ---------------- Descanso nocturno ----------------

    /**
     * De noche (horario configurado): desbloquear, tocar al búho o abrir ocio manda un aviso cálido,
     * como mucho uno cada 45 min y rotando los textos. Con No molestar activo no se envía nada.
     */
    public static synchronized void nightRest(Context c) {
        try {
            if (!AbsoluteRules.esNocheAbsoluta(LocalDateTime.now())) return;
            Context app = c.getApplicationContext();
            SharedPreferences p = prefs(app);
            long now = System.currentTimeMillis();
            if (!es.foco.next.core.home.OwlState.nightNoticeDue(p.getLong("nightAt", 0), now)) return;
            if (!OwlBudget.take(app, true)) return; // v32 (R10): presupuesto común de avisos
            int i = p.getInt("nightIdx", 0);
            p.edit().putLong("nightAt", now).putInt("nightIdx", i + 1).apply();
            notify(app, ID_NIGHT, "Buenas noches", es.foco.next.core.home.OwlState.nightText(i), "Vale",
                    new Intent().setClassName(app, "es.foco.next.ui.HomeActivity"));
        } catch (RuntimeException ignored) {
        }
    }

    // ---------------- Última app antes de volver al inicio (memoria de 4 min) ----------------

    private static es.foco.next.core.home.OwlState.Recent curCat = es.foco.next.core.home.OwlState.Recent.NONE;
    private static boolean curOver;

    /**
     * Lo llama la Accesibilidad en cada evaluación. Solo escribe al salir de una app de lectura o de
     * ocio (no en cada evento): categoría, hora de salida y si el ocio pasó del límite.
     */
    static synchronized void foreground(Context c, String pkg, boolean leisure, boolean over) {
        es.foco.next.core.home.OwlState.Recent cat =
                leisure ? es.foco.next.core.home.OwlState.Recent.OCIO
                        : es.foco.next.core.home.OwlState.reading(pkg, null) ? es.foco.next.core.home.OwlState.Recent.LECTURA
                        : es.foco.next.core.home.OwlState.Recent.NONE;
        if (cat == curCat) {
            if (leisure && over) curOver = true;
            return;
        }
        if (curCat != es.foco.next.core.home.OwlState.Recent.NONE)
            prefs(c.getApplicationContext()).edit()
                    .putString("recentCat", curCat.name())
                    .putLong("recentAt", System.currentTimeMillis())
                    .putBoolean("recentOver", curOver)
                    .apply();
        curCat = cat;
        curOver = leisure && over;
    }

    public static es.foco.next.core.home.OwlState.Recent recentCat(Context c) {
        try {
            return es.foco.next.core.home.OwlState.Recent.valueOf(prefs(c.getApplicationContext()).getString("recentCat", "NONE"));
        } catch (IllegalArgumentException e) {
            return es.foco.next.core.home.OwlState.Recent.NONE;
        }
    }

    public static long recentAt(Context c) {
        return prefs(c.getApplicationContext()).getLong("recentAt", 0);
    }

    public static boolean recentOver(Context c) {
        return prefs(c.getApplicationContext()).getBoolean("recentOver", false);
    }

    // ---------------- Notificación del búho ----------------

    private static void notify(Context app, int id, String title, String text, String button, Intent open) {
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        try {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm == null || nm.getCurrentInterruptionFilter() > NotificationManager.INTERRUPTION_FILTER_ALL) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Acompañamiento", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            PendingIntent tap =
                    PendingIntent.getActivity(
                            app, id, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            nm.notify(
                    id,
                    new Notification.Builder(app, CHANNEL)
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setLargeIcon(android.graphics.drawable.Icon.createWithResource(app, es.foco.next.R.drawable.owl_face))
                            .setContentTitle("🦉 Búho · " + title)
                            .setContentText(text)
                            .setStyle(new Notification.BigTextStyle().bigText(text))
                            .setContentIntent(tap)
                            .addAction(new Notification.Action.Builder(null, button, tap).build())
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(true)
                            .build());
        } catch (RuntimeException ignored) {
        }
    }
}
