package es.foco.next.runtime;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import es.foco.next.core.backup.DayReport;
import es.foco.next.core.backup.ReportQueue;
import es.foco.next.log.InspectorLog;
import java.time.LocalDate;
import java.util.List;

/**
 * v47: la copia de cada noche. Sube en orden los días que falten (el de hoy, y los que quedaron sin
 * red), avisa con el búho y, si algo falla, reintenta con espera creciente y avisa una sola vez.
 */
public final class BackupWorker extends Worker {
    /** Días por vuelta (para no pasar del tiempo de un trabajo ni del límite de Sheets). */
    static final int MAX_PER_RUN = 7;

    static final String WORK_REST = "foco-respaldo-resto";

    public BackupWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (!BackupStore.connected(c)) return Result.success();
        String token;
        try {
            token = GoogleDrive.token(c);
        } catch (GoogleDrive.NeedsReconnect e) {
            BackupStore.setNeedsReconnect(c, true);
            if (BackupStore.reconnectNoticeDue(c)) BackupNotifier.reconnect(c);
            return Result.failure();
        } catch (Exception e) {
            return retry(c);
        }
        BackupStore.setNeedsReconnect(c, false);
        BackupStore.clearReconnectNotice(c);
        LocalDate today = RoutineStore.today();
        List<LocalDate> days =
                ReportQueue.pending(
                        BackupStore.lastComplete(c), today, BackupStore.connectedDay(c));
        boolean mood = BackupStore.includeMood(c);
        try {
            DriveBackup up = new DriveBackup(c, token, mood);
            up.ensure();
            LocalDate last = BackupStore.lastComplete(c), sent = null;
            String png = null;
            int n = 0;
            for (LocalDate day : days) {
                if (n++ >= MAX_PER_RUN) break;
                DayReport d = DayCollector.collect(c, day, today, mood);
                png = up.upload(d, today);
                last = ReportQueue.advance(last, day, today);
                BackupStore.setLastComplete(c, last);
                sent = day;
            }
            // v48: la copia de todos los ajustes (no frena la del día si falla).
            try {
                up.uploadSettings();
            } catch (Exception e) {
                if (e instanceof GoogleApi.HttpError && ((GoogleApi.HttpError) e).code == 401)
                    throw (GoogleApi.HttpError) e;
            }
            try {
                up.fixCharts();
            } catch (Exception e) {
                // Los datos ya subieron; un gráfico no frena la copia.
                if (e instanceof GoogleApi.HttpError && ((GoogleApi.HttpError) e).code == 401)
                    throw (GoogleApi.HttpError) e;
            }
            if (sent != null) {
                BackupStore.sent(c, sent, png);
                InspectorLog.event(
                        c, InspectorLog.RESPALDO, "", n, "ok:" + sent, "respaldo|" + sent + "|" + System.currentTimeMillis() / 3_600_000L);
            }
            if (days.size() > MAX_PER_RUN) more(c);
            else BackupNotifier.done(c);
            return Result.success();
        } catch (GoogleApi.HttpError e) {
            if (e.code == 401) GoogleDrive.forget(c, token);
            return retry(c);
        } catch (Exception e) {
            return retry(c);
        }
    }

    /** Quedan días: otra vuelta en cuanto termine esta. */
    private static void more(Context c) {
        try {
            WorkManager.getInstance(c)
                    .enqueueUniqueWork(
                            WORK_REST,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            new OneTimeWorkRequest.Builder(BackupWorker.class)
                                    .setConstraints(
                                            new Constraints.Builder()
                                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                                    .build())
                                    .build());
        } catch (RuntimeException ignored) {
        }
    }

    /** Reintento con espera creciente; tras el tercer fallo, un solo aviso suave al día. */
    private Result retry(Context c) {
        int attempt = getRunAttemptCount();
        if (attempt >= 2 && BackupStore.failureNoticeDue(c)) BackupNotifier.failed(c);
        return attempt < 6 ? Result.retry() : Result.failure();
    }
}
