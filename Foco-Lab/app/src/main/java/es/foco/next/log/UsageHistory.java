package es.foco.next.log;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import es.foco.next.core.ScreenTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Lote 4: foreground time per app and day for "Estadísticas". Run only on the log thread
 * (BlockLog.query), on demand when the screen opens. Finished days are cached in stats_day so they
 * are read once from Android; today is always recomputed. Aggregation lives in foco-core
 * (ScreenTime).
 */
public final class UsageHistory {
    private UsageHistory() {}

    /** An app already open shortly before midnight still counts after it. */
    static final long LOOKBACK = 3 * 60 * 60_000L;

    public static Map<String, Map<String, Long>> load(
            Context c, LogDatabase db, LocalDate today, int days, ZoneId zone, long now) {
        LocalDate first = today.minusDays(days - 1);
        LocalDate missing = firstMissing(db.statsDone(first.toString()), first, days, zone);
        // 1.9: horas punta. Caché aparte (stats_hour) con su propia marca: los días ya guardados
        // por día no se reescriben.
        LocalDate missingHours = firstMissing(db.hoursDone(first.toString()), first, days, zone);
        if ((missing != null || missingHours != null) && UsageContrast.allowed(c)) {
            LocalDate from =
                    missing == null
                            ? missingHours
                            : missingHours == null
                                    ? missing
                                    : (missing.isBefore(missingHours) ? missing : missingHours);
            long start = from.atStartOfDay(zone).toInstant().toEpochMilli();
            List<ScreenTime.Mark> marks = marks(c, start - LOOKBACK, now);
            if (missing != null) {
                long s = missing.atStartOfDay(zone).toInstant().toEpochMilli();
                Map<String, Map<String, Long>> fresh = ScreenTime.perDay(marks, s, now, zone);
                for (LocalDate d = missing; !d.isAfter(today); d = d.plusDays(1)) {
                    Map<String, Long> apps = fresh.get(d.toString());
                    db.putStats(d.toString(), apps == null ? Collections.emptyMap() : apps, now);
                }
            }
            if (missingHours != null) {
                long s = missingHours.atStartOfDay(zone).toInstant().toEpochMilli();
                Map<String, Map<String, long[]>> fresh = ScreenTime.perHour(marks, s, now, zone);
                for (LocalDate d = missingHours; !d.isAfter(today); d = d.plusDays(1)) {
                    Map<String, long[]> apps = fresh.get(d.toString());
                    db.putHours(d.toString(), apps == null ? Collections.emptyMap() : apps, now);
                }
            }
        }
        return db.stats(first.toString());
    }

    /** 1.9: hourly cache since {@code days} days ago (filled by {@link #load}). */
    public static Map<String, Map<String, long[]>> hours(
            LogDatabase db, LocalDate today, int days) {
        return db.hours(today.minusDays(days - 1).toString());
    }

    private static LocalDate firstMissing(
            Map<String, Long> done, LocalDate first, int days, ZoneId zone) {
        for (int i = 0; i < days; i++) {
            LocalDate d = first.plusDays(i);
            long end = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            if (done.getOrDefault(d.toString(), 0L) < end) return d;
        }
        return null;
    }

    private static List<ScreenTime.Mark> marks(Context c, long start, long end) {
        List<ScreenTime.Mark> out = new ArrayList<>();
        UsageStatsManager m = c.getSystemService(UsageStatsManager.class);
        if (m == null || end <= start) return out;
        UsageEvents events = m.queryEvents(start, end);
        if (events == null) return out;
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(e)) break;
            int kind;
            switch (e.getEventType()) {
                case UsageEvents.Event.ACTIVITY_RESUMED:
                    kind = ScreenTime.RESUME;
                    break;
                case UsageEvents.Event.ACTIVITY_PAUSED:
                    kind = ScreenTime.PAUSE;
                    break;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                case UsageEvents.Event.KEYGUARD_SHOWN:
                case UsageEvents.Event.DEVICE_SHUTDOWN:
                    kind = ScreenTime.CLOSE_ALL;
                    break;
                default:
                    continue;
            }
            out.add(
                    new ScreenTime.Mark(
                            e.getTimeStamp(), e.getPackageName(), e.getClassName(), kind));
        }
        return out;
    }
}
