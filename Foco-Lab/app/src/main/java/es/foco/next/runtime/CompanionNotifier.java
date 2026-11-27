package es.foco.next.runtime;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.home.Companion;
import es.foco.next.core.home.Companion.Trigger;
import es.foco.next.log.InspectorLog;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Etapa 6: muestra los mensajes de acompañamiento como notificación silenciosa con un botón. Los
 * límites y el texto los decide {@link Companion} (puro). Registra mensaje_mostrado, mensaje_tocado
 * (lo apunta HomeActivity al abrirse desde el mensaje) y mensaje_ignorado (al descartarla).
 *
 * <p>Sin trampolín: el toque abre directamente HomeActivity (Android 12+ no deja abrir actividades
 * desde un receptor tras tocar una notificación).
 */
public final class CompanionNotifier extends BroadcastReceiver {
    static final String CHANNEL = "foco-acompanamiento", PREFS = "foco-companion";
    public static final String EXTRA_ID = "es.foco.next.extra.MESSAGE_ID",
            EXTRA_TARGET = "es.foco.next.extra.MESSAGE_TARGET";
    static final String ACTION_IGNORED = "es.foco.next.action.MESSAGE_IGNORED";
    private static final int NOTIFICATION_ID = 7400;

    public static void trigger(Context c, Trigger t) {
        trigger(c, t, -1, null);
    }

    public static void trigger(Context c, Trigger t, long minutes) {
        trigger(c, t, minutes, null);
    }

    /** Nunca lanza. {@code minutes} -1 = usar los invertidos de hoy. */
    public static synchronized void trigger(Context c, Trigger t, long minutes, String pkg) {
        try {
            Context app = c.getApplicationContext();
            SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String today = LocalDate.now().toString();
            long now = System.currentTimeMillis();
            Companion.Budget b = new Companion.Budget();
            b.shownToday = today.equals(p.getString("day", "")) ? p.getInt("shown", 0) : 0;
            b.lastLeisureReminder = p.getLong("leisureAt", -1);
            JSONArray hour = new JSONArray(p.getString("leisureHour", "[]"));
            for (int i = 0; i < hour.length(); i++) b.leisureRemindersLastHour.add(hour.getLong(i));
            // Cuidado: en modo CUIDADO baja la presión. Nada de recordatorios ni propuestas; solo
            // la sugerencia suave, celebraciones, ánimo y el resumen de la noche.
            if (CareStore.mode(app) == es.foco.next.core.care.CareMode.Modo.CUIDADO
                    && (t.category == Companion.Category.RECORDATORIO
                            || t.category == Companion.Category.PROPUESTA)) return;
            if (t == Trigger.CARE_SUGGESTION
                    && CareStore.mode(app) != es.foco.next.core.care.CareMode.Modo.CUIDADO) return;
            CareStore.night(app);
            boolean night = AbsoluteRules.night(LocalDateTime.now(), AppKinds.autoTime(app));
            if (!Companion.allowed(t, b, now, night, inCall(app), dnd(app))) return;
            if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
            if (!OwlBudget.take(app, false)) return; // v32 (R10): presupuesto común de avisos
            Map<String, String> shown = new HashMap<>();
            JSONObject hist = new JSONObject(p.getString("history", "{}"));
            for (java.util.Iterator<String> it = hist.keys(); it.hasNext(); ) {
                String k = it.next();
                shown.put(k, hist.getString(k));
            }
            Companion.Message m = Companion.pick(t.category, shown, LocalDate.now(), now / 60_000);
            HomeHub.EstadoActual ea = HomeHub.last();
            long min = minutes >= 0 ? minutes : ea == null ? 0 : ea.minutosInvertidosHoy;
            String step = ea == null ? "" : ea.proximoPaso;
            String game = QuickGame.title(app);
            String text = Companion.fill(m.text, min, step, game);
            if (t == Trigger.NIGHT_SUMMARY) text = nightSummary(app, text);
            // Destino del botón: próximo paso (inicio), reto (Lab) o la app productiva.
            String target =
                    t == Trigger.RESTLESS
                            ? "respirar"
                            : t == Trigger.CARE_SUGGESTION
                                    ? "cuidado"
                                    : t.category == Companion.Category.PROPUESTA
                                                    && (now / 60_000) % 2 == 0
                                            ? "reto"
                                            : "inicio";
            Intent open =
                    new Intent()
                            .setClassName(app, "es.foco.next.ui.HomeActivity")
                            .putExtra(EXTRA_ID, m.id)
                            .putExtra(EXTRA_TARGET, target)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent tap =
                    PendingIntent.getActivity(
                            app,
                            m.id.hashCode(),
                            open,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent ignored =
                    PendingIntent.getBroadcast(
                            app,
                            m.id.hashCode(),
                            new Intent(app, CompanionNotifier.class)
                                    .setAction(ACTION_IGNORED)
                                    .putExtra(EXTRA_ID, m.id),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL, "Acompañamiento", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            String button = "reto".equals(target) ? "Hacer el reto" : "Ir a mi próximo paso";
            if (t == Trigger.CARE_SUGGESTION) button = "Abrir Foco";
            if (t == Trigger.RESTLESS) button = "Respirar 30 s";
            nm.notify(
                    NOTIFICATION_ID,
                    new Notification.Builder(app, CHANNEL)
                            // El búho es la voz de Foco: su icono y su cara, no un aviso frío.
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setLargeIcon(
                                    android.graphics.drawable.Icon.createWithResource(
                                            app, es.foco.next.R.drawable.owl_face))
                            .setContentTitle("🦉 Búho · " + title(t))
                            .setContentText(text)
                            .setStyle(new Notification.BigTextStyle().bigText(text))
                            .setContentIntent(tap)
                            .setDeleteIntent(ignored)
                            .addAction(new Notification.Action.Builder(null, button, tap).build())
                            .setAutoCancel(true)
                            .setOnlyAlertOnce(true)
                            .setVisibility(Notification.VISIBILITY_PRIVATE)
                            .build());
            hist.put(m.id, LocalDate.now().toString());
            // Para el banner del búho en el inicio (lo dice también al volver a casa).
            p.edit()
                    .putString("lastText", text)
                    .putString("lastTitle", title(t))
                    .putString("lastTarget", target)
                    .putString("lastId", m.id)
                    .putLong("lastAt", now)
                    .apply();
            SharedPreferences.Editor e =
                    p.edit().putString("history", hist.toString()).putString("day", today);
            if (t.inLeisure) {
                JSONArray kept = new JSONArray();
                for (long at : b.leisureRemindersLastHour)
                    if (now - at < 60 * 60_000L) kept.put(at);
                kept.put(now);
                e.putLong("leisureAt", now).putString("leisureHour", kept.toString());
            } else e.putInt("shown", b.shownToday + 1);
            e.apply();
            InspectorLog.event(
                    app,
                    InspectorLog.MENSAJE_MOSTRADO,
                    pkg == null ? "" : pkg,
                    now,
                    0,
                    1,
                    0,
                    t.name().toLowerCase() + ":" + m.id,
                    null);
        } catch (Exception ignored) {
        }
    }

    /** Último mensaje del búho si es reciente (30 min) y no se cerró; si no, null. */
    public static String[] lastForBanner(Context c) {
        SharedPreferences p =
                c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long at = p.getLong("lastAt", -1);
        String id = p.getString("lastId", "");
        if (at < 0 || System.currentTimeMillis() - at > 30 * 60_000L) return null;
        if (id.equals(p.getString("bannerClosed", ""))) return null;
        return new String[] {
            p.getString("lastTitle", ""),
            p.getString("lastText", ""),
            p.getString("lastTarget", "inicio"),
            id
        };
    }

    public static void closeBanner(Context c, String id) {
        c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString("bannerClosed", id)
                .apply();
    }

    private static String title(Trigger t) {
        switch (t.category) {
            case ANIMO:
                return "Vas bien";
            case PAUSA:
                return "Una pausa corta";
            case CELEBRACION:
                return "¡Bien hecho!";
            case PROPUESTA:
                return "¿Y ahora?";
            case REFLEXION:
                return "Resumen de hoy";
            case CUIDADO:
                return "Una idea, sin prisa";
            case INQUIETUD:
                return "¿Todo bien?";
            default:
                return "Un recordatorio";
        }
    }

    private static String nightSummary(Context c, String text) {
        HomeHub.EstadoActual ea = HomeHub.last();
        StringBuilder s = new StringBuilder(text);
        if (ea != null)
            s.append("\nInvertido: ")
                    .append(ea.minutosInvertidosHoy)
                    .append(" min · ocio: ")
                    .append(ea.ocioHoyMin)
                    .append(" min.");
        s.append("\nOcio cerrado hasta las 7:00.");
        return s.toString();
    }

    static boolean inCall(Context c) {
        try {
            AudioManager a = c.getSystemService(AudioManager.class);
            int mode = a == null ? AudioManager.MODE_NORMAL : a.getMode();
            return mode == AudioManager.MODE_IN_CALL
                    || mode == AudioManager.MODE_IN_COMMUNICATION
                    || mode == AudioManager.MODE_RINGTONE;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean dnd(Context c) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            return nm != null
                    && nm.getCurrentInterruptionFilter()
                            != NotificationManager.INTERRUPTION_FILTER_ALL;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** mensaje_tocado: lo llama HomeActivity al abrirse desde la notificación. */
    public static void tapped(Context c, String id) {
        if (id == null || id.isEmpty()) return;
        InspectorLog.event(
                c, InspectorLog.MENSAJE_TOCADO, "", System.currentTimeMillis(), 0, 1, 0, id, null);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (i != null && ACTION_IGNORED.equals(i.getAction()))
            InspectorLog.event(
                    c,
                    InspectorLog.MENSAJE_IGNORADO,
                    "",
                    System.currentTimeMillis(),
                    0,
                    1,
                    0,
                    String.valueOf(i.getStringExtra(EXTRA_ID)),
                    null);
    }
}
