package es.foco.next.runtime;

import android.content.Context;
import android.content.pm.PackageManager;
import es.foco.next.core.AppConfig;
import es.foco.next.core.NextDnsStats;
import es.foco.next.core.backup.DayReport;
import es.foco.next.core.backup.NextDnsPlan;
import es.foco.next.data.Repository;
import es.foco.next.log.InspectorLog;
import es.foco.next.log.LogDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * v47: junta los datos de un día de Foco (04:00 → 04:00) para el respaldo: el mismo registro que el
 * informe diario (con Acceso de uso, el tiempo sale del sistema) y lo de NextDNS en ese tramo. Hilo
 * de fondo.
 */
final class DayCollector {
    private DayCollector() {}

    static DayReport collect(Context c, LocalDate day, LocalDate today, boolean mood) {
        AppConfig config = Repository.get(c).config();
        InspectorLog.drainLab(c);
        PackageManager pm = c.getPackageManager();
        boolean systemTotal = es.foco.next.log.UsageContrast.allowed(c);
        List<LogDatabase.InspectorRow> rows =
                es.foco.next.log.BlockLog.await(
                        c, db -> InspectorLog.rows(c, db, day.plusDays(1), 2, config), 20_000);
        List<DayReport.Row> mine = new ArrayList<>();
        if (rows != null)
            for (LogDatabase.InspectorRow r : rows)
                if (InspectorLog.inLogicalDay(day, r.day, r.hour))
                    mine.add(
                            new DayReport.Row(
                                    r.type, r.pkg, r.hour, r.ms, r.times, r.value, r.detail));
        // Bloqueos e intentos: salen del registro de bloqueos (como el CSV del informe).
        List<es.foco.next.log.BlockEvent> blocks =
                es.foco.next.log.BlockLog.await(c, db -> db.events(day.toString()), 20_000);
        if (blocks != null)
            for (es.foco.next.log.BlockEvent e : blocks) {
                if (e.type == null || !(e.type.isBlock() || e.type.isAttempt())) continue;
                int hour;
                try {
                    hour =
                            java.time.Instant.ofEpochMilli(e.timestamp)
                                    .atZone(
                                            e.zone.isEmpty()
                                                    ? ZoneId.systemDefault()
                                                    : ZoneId.of(e.zone))
                                    .getHour();
                } catch (RuntimeException ex) {
                    continue;
                }
                if (!InspectorLog.inLogicalDay(day, e.day, hour)) continue;
                mine.add(
                        new DayReport.Row(
                                e.type.isBlock() ? "bloqueo_por_limite" : "intento_bloqueo_total",
                                e.pkg,
                                hour,
                                0,
                                1,
                                0,
                                e.type.name().toLowerCase(java.util.Locale.ROOT)));
            }
        boolean complete = es.foco.next.core.backup.ReportQueue.complete(day, today);
        LocalDateTime now = LocalDateTime.now();
        double endHour = 28;
        if (!complete) {
            double h = now.getHour() + now.getMinute() / 60.0;
            endHour = now.getHour() < 4 ? h + 24 : h;
        }
        DayReport d =
                DayReport.build(
                        day,
                        mine,
                        systemTotal,
                        p -> label(pm, p),
                        Inspector.firstMode(c, day),
                        endHour,
                        mood);
        d.complete = complete;
        d.generated = now.withNano(0).toString();
        try {
            d.streak = RewardStore.streakAt(c, day);
        } catch (RuntimeException ignored) {
        }
        d.nextDns = nextDns(c, day, complete);
        return d;
    }

    static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /** NextDNS de 04:00 a 04:00 (o hasta ahora). null si no hay clave, perfil o respuesta. */
    static DayReport.NextDns nextDns(Context c, LocalDate day, boolean complete) {
        String key = NextDnsSync.key(c), id = NextDnsSync.profile(c);
        if (!NextDnsStats.validKey(key) || id == null) return null;
        ZoneId z = ZoneId.systemDefault();
        String from = day.atTime(4, 0).atZone(z).toInstant().toString();
        String to =
                complete ? day.plusDays(1).atTime(4, 0).atZone(z).toInstant().toString() : "now";
        String base = NextDnsApi.BASE + id + "/analytics/";
        String range = "from=" + from + "&to=" + to;
        try {
            long[] st = NextDnsApi.status(NextDnsApi.get(base + "status?" + range, key));
            List<DayReport.Item> blocked =
                    NextDnsApi.domains(
                            NextDnsApi.get(
                                    base + "domains?status=blocked&root=true&limit=100&" + range,
                                    key));
            List<DayReport.Item> allowed =
                    NextDnsApi.domains(
                            NextDnsApi.get(
                                    base + "domains?status=default&root=true&limit=500&" + range,
                                    key));
            return new DayReport.NextDns(
                    st[0],
                    st[1],
                    NextDnsPlan.topRoots(blocked, 15),
                    NextDnsPlan.socialActivity(allowed));
        } catch (Exception e) {
            return null;
        }
    }
}
