package es.foco.next.core.backup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * v47: el resumen de un día de Foco (de 04:00 a 04:00) para el respaldo en Drive y la hoja. Se arma
 * a partir de las filas del registro (la app las convierte a {@link Row}) y de NextDNS. Lo bueno
 * primero: lo invertido va antes que el ocio. El ánimo solo entra si la persona lo activó.
 */
public final class DayReport {

    /** Versión del formato del JSON. Súbela si cambian o se quitan campos. */
    public static final int FORMAT = 1;

    /** Máximo de eventos por día en la pestaña Eventos (lo demás queda en el teléfono). */
    public static final int MAX_EVENTS = 200;

    // Tipos del registro (InspectorLog): nombres estables, se guardan y se exportan.
    static final String OCIO = "ocio",
            OCIO_PREMIO = "ocio_premio",
            OCIO_CUIDADO = "ocio_cuidado",
            OCIO_FUERA = "ocio_fuera",
            PRODUCTIVO = "uso_productivo",
            ALTERNATIVA = "alternativa_elegida",
            SIN_CLASIFICAR = "sin_clasificar",
            RETO = "reto_completado",
            GANADOS = "minutos_ganados",
            GASTADOS = "minutos_gastados",
            MODO = "modo_cambiado",
            ANIMO = "animo";

    static final List<String> BLOQUEOS =
            List.of("bloqueo_por_limite", "bloqueo_noche_absoluta", "bloqueo_web_red_social");
    static final List<String> INTENTOS = List.of("intento_bloqueo_total", "intento_panel_nextdns");
    static final List<String> INCIDENTES =
            List.of("incidente", "vpn_activa", "launcher_cambiado", "nextdns_cambiado");

    /** Una fila del registro, ya filtrada al día lógico. {@code hour} -1 = resumen sin hora. */
    public static final class Row {
        public final String type, pkg, detail;
        public final int hour;
        public final long ms, times, value;

        public Row(
                String type, String pkg, int hour, long ms, long times, long value, String detail) {
            this.type = type == null ? "" : type;
            this.pkg = pkg == null ? "" : pkg;
            this.hour = hour;
            this.ms = ms;
            this.times = times;
            this.value = value;
            this.detail = detail == null ? "" : detail;
        }
    }

    public static final class Item {
        public final String name;
        public final long value;

        /** Paquete de la app (para recuperar el historial); "" si no aplica. */
        public final String id;

        public Item(String name, long value) {
            this(name, value, "");
        }

        public Item(String name, long value, String id) {
            this.name = name;
            this.value = value;
            this.id = id == null ? "" : id;
        }
    }

    /** Lo de NextDNS en ese día (null en el informe si no hay clave o no respondió). */
    public static final class NextDns {
        public final long queries, blocked;
        public final List<Item> trackers;
        public final Map<String, Long> social;

        public NextDns(long queries, long blocked, List<Item> trackers, Map<String, Long> social) {
            this.queries = queries;
            this.blocked = blocked;
            this.trackers = trackers == null ? Collections.emptyList() : trackers;
            this.social = social == null ? Collections.emptyMap() : social;
        }

        public double pctBlocked() {
            return queries <= 0 ? 0 : Math.round(blocked * 1000.0 / queries) / 10.0;
        }
    }

    public static final class Event {
        public final int hour;
        public final String type, app, detail;
        public final long value;

        Event(int hour, String type, String app, long value, String detail) {
            this.hour = hour;
            this.type = type;
            this.app = app;
            this.value = value;
            this.detail = detail;
        }
    }

    public LocalDate day;
    public boolean complete;
    public String generated = "";
    public long investedMs, leisureMs;
    public int challenges, blocks, attempts, incidents, streak;
    public long earnedMin, spentMin;
    public final List<Item> productiveApps = new ArrayList<>();
    public final List<Item> leisureApps = new ArrayList<>();
    public final long[] leisureByHour = new long[24];
    public final Map<String, Double> modeHours = new LinkedHashMap<>();
    public final List<String[]> modeChanges = new ArrayList<>(); // {hora, de, a}
    public final List<Event> events = new ArrayList<>();
    public NextDns nextDns;
    public int moodCount;
    public double moodAverage;

    /** Modo con más horas ese día ("NORMAL" si no hay datos). */
    public String mainMode() {
        String best = "NORMAL";
        double h = -1;
        for (Map.Entry<String, Double> e : modeHours.entrySet())
            if (e.getValue() > h) {
                h = e.getValue();
                best = e.getKey();
            }
        return best;
    }

    public long investedMin() {
        return investedMs / 60_000;
    }

    public long leisureMin() {
        return leisureMs / 60_000;
    }

    /**
     * Arma el día.
     *
     * @param rows filas del registro ya filtradas al día lógico
     * @param systemTotal el total del ocio sale del sistema (Acceso de uso), como en el informe
     * @param label paquete → nombre visible
     * @param startMode modo al empezar el día si no hubo cambios registrados
     * @param endHour hasta qué hora lógica contar los modos (4..28; 28 = el día entero)
     * @param includeMood el ánimo solo se guarda si la persona lo activó
     */
    public static DayReport build(
            LocalDate day,
            List<Row> rows,
            boolean systemTotal,
            Function<String, String> label,
            String startMode,
            double endHour,
            boolean includeMood) {
        DayReport r = new DayReport();
        r.day = day;
        Map<String, Long> productive = new LinkedHashMap<>();
        Map<String, long[]> leisure = new LinkedHashMap<>(); // pkg → {ocio, resto}
        long[][] hours = new long[24][2];
        long moodSum = 0;
        List<int[]> changes = new ArrayList<>();
        List<String[]> changeModes = new ArrayList<>();
        for (Row x : rows) {
            String t = x.type;
            if (PRODUCTIVO.equals(t) || ALTERNATIVA.equals(t)) {
                r.investedMs += x.ms;
                if (!x.pkg.isEmpty() && x.ms > 0) productive.merge(x.pkg, x.ms, Long::sum);
            } else if (OCIO.equals(t)
                    || OCIO_PREMIO.equals(t)
                    || OCIO_CUIDADO.equals(t)
                    || OCIO_FUERA.equals(t)) {
                int k = OCIO.equals(t) ? 0 : 1;
                if (!x.pkg.isEmpty()) leisure.computeIfAbsent(x.pkg, p -> new long[2])[k] += x.ms;
                if (x.hour >= 0 && x.hour < 24) hours[x.hour][k] += x.ms;
            } else if (RETO.equals(t)) r.challenges++;
            else if (GANADOS.equals(t)) r.earnedMin += Math.max(0, x.value);
            else if (GASTADOS.equals(t)) r.spentMin += Math.max(0, x.value);
            else if (BLOQUEOS.contains(t)) r.blocks++;
            else if (INTENTOS.contains(t)) r.attempts++;
            else if (INCIDENTES.contains(t)) r.incidents++;
            else if (MODO.equals(t)) {
                String de = field(x.detail, "de"), a = field(x.detail, "a");
                if (!a.isEmpty() && x.hour >= 0) {
                    changes.add(new int[] {x.hour});
                    changeModes.add(new String[] {de, a});
                }
            } else if (ANIMO.equals(t) && x.value > 0) {
                r.moodCount++;
                moodSum += x.value;
            }
            if (isEvent(x) && r.events.size() < MAX_EVENTS && (includeMood || !ANIMO.equals(t)))
                r.events.add(
                        new Event(
                                x.hour,
                                t,
                                x.pkg.isEmpty() ? "" : label.apply(x.pkg),
                                x.value,
                                ANIMO.equals(t) ? "" : x.detail));
        }
        for (Map.Entry<String, long[]> e : leisure.entrySet()) {
            long[] v = e.getValue();
            long ms = systemTotal ? Math.max(v[0], v[1]) : v[0] + v[1];
            if (ms <= 0) continue;
            r.leisureApps.add(new Item(label.apply(e.getKey()), ms / 60_000, e.getKey()));
        }
        long[] sums = new long[2];
        for (int h = 0; h < 24; h++) {
            long ms = systemTotal ? Math.max(hours[h][0], hours[h][1]) : hours[h][0] + hours[h][1];
            r.leisureByHour[h] = Math.round(ms / 60_000.0);
            sums[0] += hours[h][0];
            sums[1] += hours[h][1];
        }
        // El total como el informe diario: con Acceso de uso manda el sistema.
        long total = systemTotal ? Math.max(sums[0], sums[1]) : sums[0] + sums[1];
        r.leisureMs = total;
        r.leisureApps.sort((p, q) -> Long.compare(q.value, p.value));
        for (Map.Entry<String, Long> e : productive.entrySet())
            r.productiveApps.add(
                    new Item(label.apply(e.getKey()), e.getValue() / 60_000, e.getKey()));
        r.productiveApps.sort((p, q) -> Long.compare(q.value, p.value));
        if (r.moodCount > 0) r.moodAverage = Math.round(moodSum * 10.0 / r.moodCount) / 10.0;
        modes(r, changes, changeModes, startMode, endHour);
        return r;
    }

    /** Las horas de cada modo entre las 04:00 y {@code endHour} (hora lógica, 28 = 04:00). */
    static void modes(
            DayReport r,
            List<int[]> changes,
            List<String[]> modes,
            String startMode,
            double endHour) {
        String current =
                !modes.isEmpty() && !modes.get(0)[0].isEmpty()
                        ? modes.get(0)[0]
                        : startMode == null || startMode.isEmpty() ? "NORMAL" : startMode;
        double end = Math.max(4, Math.min(28, endHour));
        double at = 4;
        Map<String, Double> acc = new LinkedHashMap<>();
        for (int i = 0; i < changes.size(); i++) {
            int h = changes.get(i)[0];
            double logical = h < 4 ? h + 24 : h;
            logical = Math.max(at, Math.min(end, logical));
            acc.merge(current, logical - at, Double::sum);
            r.modeChanges.add(new String[] {String.valueOf(h), modes.get(i)[0], modes.get(i)[1]});
            current = modes.get(i)[1];
            at = logical;
        }
        acc.merge(current, end - at, Double::sum);
        for (Map.Entry<String, Double> e : acc.entrySet())
            if (e.getValue() > 0) r.modeHours.put(e.getKey(), Math.round(e.getValue() * 10) / 10.0);
        if (r.modeHours.isEmpty()) r.modeHours.put(current, 0.0);
    }

    /** Hechos puntuales (no las sumas de uso por hora). */
    static boolean isEvent(Row x) {
        String t = x.type;
        boolean tally =
                OCIO.equals(t)
                        || OCIO_PREMIO.equals(t)
                        || OCIO_CUIDADO.equals(t)
                        || OCIO_FUERA.equals(t)
                        || PRODUCTIVO.equals(t)
                        || ALTERNATIVA.equals(t)
                        || SIN_CLASIFICAR.equals(t);
        return !tally;
    }

    /** "de=NORMAL;a=ENFOQUE;motivo=x" → valor de la clave, o "". */
    static String field(String detail, String key) {
        if (detail == null) return "";
        for (String part : detail.split(";")) {
            int i = part.indexOf('=');
            if (i > 0 && part.substring(0, i).trim().equals(key))
                return part.substring(i + 1).trim();
        }
        return "";
    }

    private static Json.Arr items(List<Item> list, String key, String valueKey, int max) {
        Json.Arr a = new Json.Arr();
        for (int i = 0; i < list.size() && i < max; i++) {
            Json.Obj o = Json.obj().put(key, list.get(i).name).put(valueKey, list.get(i).value);
            if (!list.get(i).id.isEmpty()) o.put("paquete", list.get(i).id);
            a.add(o);
        }
        return a;
    }

    /** El JSON del día. Sin "animo" si {@code includeMood} es false. */
    public String toJson(boolean includeMood) {
        Json.Obj o =
                Json.obj()
                        .put("formato", FORMAT)
                        .put("fecha", day.toString())
                        .put("completo", complete)
                        .put("generado", generated)
                        .put("invertido_min", investedMin())
                        .put("retos", challenges)
                        .put("apps_productivas", items(productiveApps, "app", "min", 30));
        Json.Arr changes = new Json.Arr();
        for (String[] c : modeChanges)
            changes.add(Json.obj().put("hora", Integer.parseInt(c[0])).put("de", c[1]).put("a", c[2]));
        o.put(
                "modos",
                Json.obj()
                        .put("principal", mainMode())
                        .put("horas", modeHours)
                        .put("cambios", changes));
        o.put("ocio_min", leisureMin())
                .put("ocio_por_app", items(leisureApps, "app", "min", 30))
                .put("ocio_por_hora", leisureByHour.clone())
                .put("bloqueos", blocks)
                .put("intentos", attempts)
                .put("incidentes", incidents)
                .put("minutos_ganados", earnedMin)
                .put("minutos_gastados", spentMin)
                .put("racha", streak);
        if (nextDns == null) o.put("nextdns", null);
        else
            o.put(
                    "nextdns",
                    Json.obj()
                            .put("consultas", nextDns.queries)
                            .put("bloqueadas", nextDns.blocked)
                            .put("pct_bloqueado", nextDns.pctBlocked())
                            .put(
                                    "top_rastreadores",
                                    items(nextDns.trackers, "dominio", "bloqueos", 15))
                            .put("actividad_redes", nextDns.social));
        if (includeMood && moodCount > 0)
            o.put("animo", Json.obj().put("media", moodAverage).put("respuestas", moodCount));
        return o.toString();
    }
}
