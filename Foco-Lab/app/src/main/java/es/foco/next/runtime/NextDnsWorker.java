package es.foco.next.runtime;

import android.content.Context;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** v47: trabajo de WorkManager que pone NextDNS como toca ({@link NextDnsControl#apply}). */
public final class NextDnsWorker extends Worker {
    public NextDnsWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        switch (NextDnsControl.apply(getApplicationContext())) {
            case RETRY:
                // Sin red o NextDNS caído: WorkManager reintenta con espera creciente.
                return getRunAttemptCount() < 8 ? Result.retry() : Result.failure();
            case DENIED:
                return Result.failure();
            default:
                return Result.success();
        }
    }
}
