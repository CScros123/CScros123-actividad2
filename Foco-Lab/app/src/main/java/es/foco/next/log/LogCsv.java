package es.foco.next.log;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;

/**
 * Fase 4: CSV of the log (events + daily usage contrast) in a single table, so it opens directly in
 * a spreadsheet. Same escaping as the weekly CSV (Repository.cell): quotes and formula-injection
 * guard. Pure Java.
 */
public final class LogCsv {
    private LogCsv() {}

    public static final String HEADER =
            "registro,fecha,hora,zona,tipo,tipo_es,paquete,app,presupuesto_usado_s,ventana_restante_s,duracion_bloqueo_s,accion,ms_observados_accesibilidad,ms_usagestats";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * @param label package → visible app name; may return the package itself. Null means "use the
     *     package".
     */
    public static String build(
            List<BlockEvent> events, List<LogSummary.Usage> usage, Function<String, String> label) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        if (events != null)
            for (BlockEvent e : events) {
                if (e == null) continue;
                csv.append(cell("evento"))
                        .append(',')
                        .append(cell(e.day))
                        .append(',')
                        .append(cell(time(e)))
                        .append(',')
                        .append(cell(e.zone))
                        .append(',')
                        .append(cell(e.type.name()))
                        .append(',')
                        .append(cell(e.type.label))
                        .append(',')
                        .append(cell(e.pkg))
                        .append(',')
                        .append(cell(name(e.pkg, label)))
                        .append(',')
                        .append(seconds(e.budgetUsedMs))
                        .append(',')
                        .append(seconds(e.windowLeftMs))
                        .append(',')
                        .append(seconds(e.lockMs))
                        .append(',')
                        .append(cell(e.action))
                        .append(",,")
                        .append("\r\n");
            }
        if (usage != null)
            for (LogSummary.Usage u : usage) {
                if (u == null) continue;
                csv.append(cell("uso_diario"))
                        .append(',')
                        .append(cell(u.day))
                        .append(",,,,,")
                        .append(cell(u.pkg))
                        .append(',')
                        .append(cell(name(u.pkg, label)))
                        .append(",,,,,")
                        .append(number(u.observedMs))
                        .append(',')
                        .append(number(u.usageStatsMs))
                        .append("\r\n");
            }
        return csv.toString();
    }

    static String time(BlockEvent e) {
        ZoneId zone;
        try {
            zone = e.zone == null || e.zone.isEmpty() ? ZoneId.of("UTC") : ZoneId.of(e.zone);
        } catch (DateTimeException ex) {
            zone = ZoneId.of("UTC");
        }
        return TIME.format(Instant.ofEpochMilli(e.timestamp).atZone(zone));
    }

    private static String name(String pkg, Function<String, String> label) {
        if (pkg == null || pkg.isEmpty()) return "";
        if (label == null) return pkg;
        try {
            String l = label.apply(pkg);
            return l == null ? pkg : l;
        } catch (RuntimeException ex) {
            return pkg;
        }
    }

    static String seconds(long ms) {
        return ms < 0 ? "" : Long.toString(ms / 1000);
    }

    static String number(long value) {
        return value < 0 ? "" : Long.toString(value);
    }

    /**
     * Quoted cell; a leading = + - @ tab or newline is neutralised with an apostrophe (CSV
     * injection).
     */
    public static String cell(String value) {
        String v = value == null ? "" : value;
        if (!v.isEmpty() && "=+-@\t\r\n".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
