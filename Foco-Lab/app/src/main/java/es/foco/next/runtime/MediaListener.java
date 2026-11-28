package es.foco.next.runtime;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * v48: acceso a notificaciones para ver y controlar la música (Apple Music) con las sesiones
 * multimedia de Android. v50: además, de noche quita los avisos de las redes sociales ({@link
 * NightQuiet}). Solo mira el paquete de cada aviso: no lee, guarda ni envía su contenido.
 */
public final class MediaListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || !NightQuiet.shouldQuiet(this, sbn.getPackageName())) return;
            cancelNotification(sbn.getKey());
            NightQuiet.count(this);
        } catch (RuntimeException ignored) {
        }
    }
}
