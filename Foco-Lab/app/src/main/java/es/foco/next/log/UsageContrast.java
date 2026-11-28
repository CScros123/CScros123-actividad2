package es.foco.next.log;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fase 4: on-demand contrast between the time observed by Accessibility and Android's usage history
 * (UsageStatsManager). Called only from the log thread, when "Registro" opens and once a day. No
 * periodic polling. The blocking engine never reads this.
 */
public final class UsageContrast {
    private UsageContrast() {}

    static final int DAYS = 7;

    /** Usage access ("Acceso de uso") granted in Android settings. Never throws. */
    public static boolean allowed(Context c) {
        try {
            AppOpsManager ops = c.getSystemService(AppOpsManager.class);
            if (ops == null) return false;
            int mode =
                    ops.unsafeCheckOpNoThrow(
                            AppOpsManager.OPSTR_GET_USAGE_STATS,
                            Process.myUid(),
                            c.getPackageName());
            if (mode == AppOpsManager.MODE_DEFAULT)
                return c.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS)
                        == PackageManager.PERMISSION_GRANTED;
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Writes usage_day for the last 7 days (today included). Without permission, usage_stats_ms
     * stays -1.
     */
    static void refresh(
            Context c,
            LogDatabase db,
            ObservedSnapshot snapshot,
            LocalDate today,
            ZoneId zone,
            long now) {
        boolean access = allowed(c);
        for (int i = 0; i < DAYS; i++) {
            LocalDate date = today.minusDays(i);
            String day = date.toString();
            long start = date.atStartOfDay(zone).toInstant().toEpochMilli();
            long end =
                    Math.min(now, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli());
            Map<String, Long> observed = snapshot.day(day);
            Map<String, Long> stats =
                    access && end > start
                            ? foreground(c, start, end, snapshot.interest)
                            : Collections.emptyMap();
            Set<String> pkgs = new LinkedHashSet<>(observed.keySet());
            pkgs.addAll(stats.keySet());
            for (String pkg : pkgs)
                db.putUsage(
                        day,
                        pkg,
                        observed.getOrDefault(pkg, 0L),
                        access ? stats.getOrDefault(pkg, 0L) : -1,
                        now);
        }
    }

    static Map<String, Long> foreground(Context c, long start, long end, Set<String> only) {
        if (only.isEmpty()) return Collections.emptyMap();
        UsageStatsManager m = c.getSystemService(UsageStatsManager.class);
        if (m == null) return Collections.emptyMap();
        UsageEvents events = m.queryEvents(start, end);
        if (events == null) return Collections.emptyMap();
        List<UsageMath.Mark> marks = new ArrayList<>();
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            if (!events.getNextEvent(e)) break;
            int kind = kind(e.getEventType());
            if (kind != 0)
                marks.add(new UsageMath.Mark(e.getTimeStamp(), e.getPackageName(), kind));
        }
        return UsageMath.foreground(marks, start, end, only);
    }

    private static int kind(int type) {
        switch (type) {
            case UsageEvents.Event.ACTIVITY_RESUMED:
                return UsageMath.START;
            case UsageEvents.Event.ACTIVITY_PAUSED:
            case UsageEvents.Event.ACTIVITY_STOPPED:
                return UsageMath.STOP;
            case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
            case UsageEvents.Event.KEYGUARD_SHOWN:
            case UsageEvents.Event.DEVICE_SHUTDOWN:
                return UsageMath.CLOSE_ALL;
            default:
                return 0;
        }
    }
}
