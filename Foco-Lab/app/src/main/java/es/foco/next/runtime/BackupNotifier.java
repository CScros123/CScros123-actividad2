package es.foco.next.runtime;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import es.foco.next.core.backup.BackupLines;

/**
 * v47: el búho avisa de la copia nocturna. Bien: una frase cercana (8+ variantes, sin repetir en 3
 * días) con «Ver hoja» y «Ver gráfico». Mal: un solo aviso suave al día. Permiso caducado: «vuelve
 * a conectar Drive». Durante una llamada o en No molestar la notificación llega en silencio.
 */
final class BackupNotifier {
    private BackupNotifier() {}

    static final String CHANNEL = "foco-respaldo", CHANNEL_QUIET = "foco-respaldo-silencio";
    static final int ID = 7471;

    static void done(Context c) {
        int i = BackupLines.pick(BackupStore.recentLines(c), RoutineStore.today().getDayOfYear());
        BackupStore.usedLine(c, i);
        String sheet = BackupStore.sheetUrl(c), chart = BackupStore.chartUrl(c);
        Notification.Builder b = base(c, BackupLines.DONE[i]);
        if (!sheet.isEmpty()) {
            PendingIntent open = view(c, sheet, 1);
            b.setContentIntent(open)
                    .addAction(new Notification.Action.Builder(null, "Ver hoja", open).build());
        }
        if (!chart.isEmpty())
            b.addAction(
                    new Notification.Action.Builder(null, "Ver gráfico", view(c, chart, 2))
                            .build());
        show(c, b);
    }

    static void failed(Context c) {
        show(c, base(c, BackupLines.FAILED).setContentIntent(settings(c)));
    }

    static void reconnect(Context c) {
        Notification.Builder b = base(c, BackupLines.RECONNECT).setContentIntent(settings(c));
        b.addAction(new Notification.Action.Builder(null, "Abrir Ajustes", settings(c)).build());
        show(c, b);
    }

    private static Notification.Builder base(Context c, String text) {
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                .setLargeIcon(
                        android.graphics.drawable.Icon.createWithResource(
                                c, es.foco.next.R.drawable.owl_face))
                .setContentTitle("🦉 Búho · Tu copia")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true);
    }

    private static PendingIntent view(Context c, String url, int code) {
        Intent i =
                new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                c, ID + code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent settings(Context c) {
        Intent i =
                new Intent()
                        .setClassName(c, "es.foco.next.ui.MainActivity")
                        .putExtra("es.foco.next.extra.PAGE", 2)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                c, ID + 3, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** En llamada o con No molestar: silenciosa. */
    static boolean quiet(Context c) {
        try {
            AudioManager a = c.getSystemService(AudioManager.class);
            int m = a == null ? AudioManager.MODE_NORMAL : a.getMode();
            if (m == AudioManager.MODE_IN_CALL
                    || m == AudioManager.MODE_IN_COMMUNICATION
                    || m == AudioManager.MODE_RINGTONE) return true;
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            return nm != null
                    && nm.getCurrentInterruptionFilter()
                            != NotificationManager.INTERRUPTION_FILTER_ALL;
        } catch (RuntimeException e) {
            return true;
        }
    }

    private static void show(Context c, Notification.Builder b) {
        try {
            Context app = c.getApplicationContext();
            if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL,
                            "Copia nocturna en Drive",
                            NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
            // En llamada o No molestar: el mismo aviso por un canal silencioso (sin sonido ni
            // vibración). Notification.Builder no tiene setSilent; eso es de NotificationCompat.
            if (quiet(app)) {
                NotificationChannel calm =
                        new NotificationChannel(
                                CHANNEL_QUIET,
                                "Copia nocturna en Drive (en silencio)",
                                NotificationManager.IMPORTANCE_LOW);
                calm.setSound(null, null);
                calm.enableVibration(false);
                nm.createNotificationChannel(calm);
                b.setChannelId(CHANNEL_QUIET);
            }
            nm.notify(ID, b.build());
        } catch (RuntimeException ignored) {
        }
    }
}
