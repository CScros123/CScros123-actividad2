package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import es.foco.next.core.WeekendLock;
import es.foco.next.data.Repository;
import es.foco.next.log.InspectorLog;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Foco 1.0, etapa 4: Weekend Lock en el teléfono. El estado sale de {@link WeekendLock#active};
 * aquí solo se guarda un ancla (hora del reloj + tiempo desde el arranque + fin calculado) para que
 * adelantar la hora no lo termine antes: mientras el tiempo real transcurrido no llegue al fin,
 * sigue activo y se registra un incidente. Tras un reinicio ya no se puede medir el tiempo
 * transcurrido: sigue activo si el reloj va por detrás de la última hora guardada.
 */
public final class Weekend {
    private Weekend() {}

    private static final String PREFS = "foco-weekend";

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Aviso silencioso de inicio y fin. Sale cuando Foco detecta el cambio (no usa alarmas
     * exactas); si no hay permiso, no pasa nada.
     */
    private static void announce(Context c, String title, String text) {
        try {
            android.app.NotificationManager nm =
                    c.getSystemService(android.app.NotificationManager.class);
            if (nm == null) return;
            android.app.NotificationChannel ch =
                    new android.app.NotificationChannel(
                            "foco-weekend",
                            "Weekend Lock",
                            android.app.NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            nm.notify(
                    7301,
                    new android.app.Notification.Builder(c, "foco-weekend")
                            .setSmallIcon(es.foco.next.R.drawable.leaf)
                            .setContentTitle(title)
                            .setContentText(text)
                            .setAutoCancel(true)
                            .build());
        } catch (RuntimeException ignored) {
        }
    }

    /** true durante el Weekend Lock. Nunca lanza: ante un fallo, decide el reloj. */
    public static boolean active(Context context) {
        long wall = WallClock.now();
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime local = Instant.ofEpochMilli(wall).atZone(zone).toLocalDateTime();
        boolean byClock = WeekendLock.active(local);
        try {
            Context c = context.getApplicationContext();
            SharedPreferences p = prefs(c);
            long elapsed = SystemClock.elapsedRealtime();
            int boot = Repository.boot(c);
            long end = p.getLong("end", 0), aw = p.getLong("wall", 0), ae = p.getLong("elapsed", 0);
            int ab = p.getInt("boot", -2);
            if (byClock) {
                long newEnd = WeekendLock.end(local).atZone(zone).toInstant().toEpochMilli();
                if (end == 0) {
                    InspectorLog.event(
                            c,
                            InspectorLog.WEEKEND_INICIO,
                            "",
                            0,
                            "hasta el domingo 23:59",
                            "wk|ini|" + WeekendLock.id(local));
                    announce(
                            c,
                            "Weekend Lock activo",
                            "Hasta el domingo a las 23:59, en Normal y Enfoque, el ocio se abre con tu hora libre de Foco Core o un cupón. En Cuidado queda abierto y se registra.");
                    end = newEnd;
                }
                // El ancla se refresca como mucho cada 5 minutos, y nunca hacia atrás.
                if (ab != boot || Math.abs(wall - aw) > 5 * 60_000L)
                    p.edit()
                            .putLong("end", end)
                            .putLong("wall", ab == boot ? Math.max(wall, aw) : wall)
                            .putLong("elapsed", elapsed)
                            .putInt("boot", boot)
                            .apply();
                return true;
            }
            if (end == 0) return false;
            // El reloj dice que ya terminó: se comprueba con el tiempo real transcurrido.
            boolean still;
            if (ab == boot && elapsed >= ae) still = aw + (elapsed - ae) < end;
            else still = wall < aw && aw < end;
            if (still) {
                InspectorLog.event(
                        c,
                        InspectorLog.INCIDENTE,
                        "",
                        0,
                        "hora_manipulada durante Weekend Lock",
                        "inc|hora|" + end);
                return true;
            }
            p.edit().remove("end").apply();
            InspectorLog.event(c, InspectorLog.WEEKEND_FIN, "", 0, "", "wk|fin|" + end);
            announce(c, "Weekend Lock terminado", "Vuelve el bloque gratis de lunes a viernes.");
            return false;
        } catch (RuntimeException e) {
            return byClock;
        }
    }
}
