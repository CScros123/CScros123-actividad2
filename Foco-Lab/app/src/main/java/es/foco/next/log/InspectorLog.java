package es.foco.next.log;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Foco 1.0, etapa 1: log del Inspector. Tabla {@code inspector_event} de {@code foco-log.db} (la
 * base que ya usa Foco).
 *
 * <ul>
 *   <li>{@code segundos} es solo uso real; {@code veces} es solo aperturas o intentos. Un intento
 *       nunca suma segundos.
 *   <li>El uso se guarda agregado por día, hora, tipo y app (una fila que se reutiliza); los hechos
 *       puntuales se guardan una sola vez gracias a su clave.
 *   <li>El detalle dura {@link #DETAIL_DAYS} días; después queda el resumen diario ({@code
 *       inspector_day}).
 * </ul>
 *
 * Como {@link BlockLog}: ningún método público lanza ni bloquea a quien llama (salvo {@link #csv},
 * que se llama desde el hilo del log).
 */
public final class InspectorLog {
    private InspectorLog() {}

    public static final String HEADER = "fecha,hora,tipo,paquete,app,segundos,veces,valor,detalle";
    public static final int DETAIL_DAYS = 30;
    // Tipos de evento (nombres estables: se guardan y se exportan).
    public static final String OCIO = "ocio",
            USO_PRODUCTIVO = "uso_productivo",
            ALTERNATIVA = "alternativa_elegida",
            BLOQUEO = "bloqueo_por_limite",
            INTENTO_TOTAL = "intento_bloqueo_total",
            RETO = "reto_completado",
            LOGRO = "logro_desbloqueado",
            GANADOS = "minutos_ganados",
            GASTADOS = "minutos_gastados",
            SALDO = "saldo_fin_dia",
            CATEGORIA = "cambio_categoria",
            WEEKEND_INICIO = "weekend_inicio",
            WEEKEND_FIN = "weekend_fin",
            INCIDENTE = "incidente",
            SIN_CLASIFICAR = "sin_clasificar",
            VPN = "vpn_activa",
            FRANJA = "franja_estricta",
            REDIRECCION = "redireccion",
            NOCHE = "bloqueo_noche_absoluta",
            WEB_SOCIAL = "bloqueo_web_red_social",
            // v37: intento de abrir la app o la web de NextDNS en este teléfono.
            INTENTO_NEXTDNS = "intento_panel_nextdns",
            // v47: el perfil de NextDNS no estaba como Foco lo dejó y se restauró (sin culpa).
            NEXTDNS_CAMBIADO = "nextdns_cambiado",
            // v47: copia nocturna en Drive (detalle: día y si salió bien).
            RESPALDO = "respaldo_drive",
            // Foco Home (etapas 1, 5 y 6).
            LAUNCHER_CAMBIADO = "launcher_cambiado",
            DNS_AUTO = "dns_automatico",
            AJUSTES = "ajustes_vigilados",
            MENSAJE_MOSTRADO = "mensaje_mostrado",
            MENSAJE_TOCADO = "mensaje_tocado",
            MENSAJE_IGNORADO = "mensaje_ignorado",
            MINUTOS_EXTRA = "minutos_extra",
            // De control a cuidado.
            MODO_CAMBIADO = "modo_cambiado",
            ANIMO = "animo",
            INQUIETUD = "inquietud",
            // v32 (R6): ocio que el motor de 5 min no mide. Con OCIO (dentro del límite, o el total
            // del sistema con Acceso de uso) forman las cuatro clases del ocio.
            OCIO_PREMIO = "ocio_premio",
            OCIO_CUIDADO = "ocio_cuidado",
            OCIO_FUERA = "ocio_fuera",
            // v33: «¿me aporta?» (detalle: no / si;tipo) y «¿valió la pena?» (si / mas_o_menos /
            // no).
            CONCIENCIA = "decision_consciente",
            VALIO = "valio_la_pena";

    /**
     * Contrato con Lab (personal.qqq.braintraining.InspectorQueue): cola persistente de retos y
     * logros.
     */
    static final String LAB_PREFS = "foco-inspector-lab", LAB_KEY = "queue";

    /**
     * Uso real y aperturas de una app en una hora: se suma a la fila de ese día, hora, tipo y app.
     */
    public static void usage(Context c, String type, String pkg, long wall, long ms, int times) {
        if (ms <= 0 && times <= 0) return;
        try {
            ZonedDateTime t = Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault());
            String day = t.toLocalDate().toString();
            int hour = t.getHour();
            String p = BlockEvent.clean(pkg, BlockEvent.MAX_PACKAGE);
            BlockLog.query(
                    c,
                    db -> {
                        db.addInspector(
                                wall,
                                day,
                                hour,
                                type,
                                p,
                                Math.max(0, ms),
                                Math.max(0, times),
                                0,
                                "",
                                "u|" + day + "|" + hour + "|" + type + "|" + p,
                                true);
                        return Boolean.TRUE;
                    },
                    null,
                    null);
        } catch (Throwable ignored) {
        }
        // Foco Home, etapa 1.2: el estado se recalcula al escribir en el log (nunca lanza).
        es.foco.next.runtime.HomeHub.onUsage(c, type, pkg, wall, ms);
    }

    /**
     * Un hecho puntual (reto, logro, minutos, incidente…). Con {@code onceKey} se guarda una sola
     * vez: si ya existe, no se repite. {@code ms} y {@code times} pueden ser 0.
     */
    public static void event(
            Context c,
            String type,
            String pkg,
            long wall,
            long ms,
            int times,
            long value,
            String detail,
            String onceKey) {
        try {
            ZonedDateTime t = Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault());
            String day = t.toLocalDate().toString();
            int hour = t.getHour();
            String p = BlockEvent.clean(pkg, BlockEvent.MAX_PACKAGE), d = text(detail);
            BlockLog.query(
                    c,
                    db -> {
                        boolean added =
                                db.addInspector(
                                        wall,
                                        day,
                                        hour,
                                        type,
                                        p,
                                        Math.max(0, ms),
                                        Math.max(0, times),
                                        value,
                                        d,
                                        onceKey,
                                        false);
                        // v32 (R3): un intento de evasión cuenta una vez por hecho (respeta
                        // onceKey).
                        if (added) evasion(c, type, detail);
                        return Boolean.TRUE;
                    },
                    null,
                    null);
        } catch (Throwable ignored) {
        }
        // Foco Home, etapa 1.2 (retos y logros; el resto se ignora allí).
        es.foco.next.runtime.HomeHub.onEvent(c, type, pkg, wall, value, onceKey);
    }

    /** v31: los intentos de evasión cuentan contra la semana perfecta (v32: solo si se guardó). */
    private static void evasion(Context c, String type, String detail) {
        if (INCIDENTE.equals(type)) es.foco.next.runtime.RewardStore.markEvasion(c, detail);
        else if (LAUNCHER_CAMBIADO.equals(type) || VPN.equals(type))
            es.foco.next.runtime.RewardStore.markEvasion(c, type);
    }

    public static void event(
            Context c, String type, String pkg, long value, String detail, String onceKey) {
        event(c, type, pkg, System.currentTimeMillis(), 0, 0, value, detail, onceKey);
    }

    /**
     * Pasa al log los retos y logros que Lab dejó en su cola. La clave de cada uno evita duplicados
     * si se lee dos veces.
     */
    /** Lo que recibe quien abona minutos por cada reto o logro leído de la cola de Lab. */
    public interface LabSink {
        void got(String type, String game, long value, String id);
    }

    public static void drainLab(Context c) {
        drainLab(c, null);
    }

    /**
     * Auditoría (cuidado, paso 0): antes cada reto se escribía con su propia tarea en la cola del
     * log (256 huecos que DESCARTAN en silencio si está llena) y la cola de Lab se borraba nada más
     * encolar, sin esperar a que se guardara. Con el log ocupado, un reto podía abonar minutos al
     * banco y no quedar nunca en el registro: el resumen semanal decía "0 retos". Ahora se escriben
     * todos en una sola tarea y la cola de Lab solo se vacía cuando esa escritura terminó. Si no se
     * llega a escribir, se vuelve a intentar en la próxima lectura (las claves evitan duplicados).
     */
    public static void drainLab(Context c, LabSink sink) {
        try {
            Context app = c.getApplicationContext();
            SharedPreferences p = app.getSharedPreferences(LAB_PREFS, Context.MODE_PRIVATE);
            String raw = p.getString(LAB_KEY, null);
            if (raw == null || raw.isEmpty()) return;
            JSONArray a = new JSONArray(raw);
            final java.util.List<JSONObject> items = new java.util.ArrayList<>();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                String type = o.optString("type");
                if (!RETO.equals(type) && !LOGRO.equals(type)) continue;
                items.add(o);
                if (sink != null)
                    try {
                        sink.got(type, o.optString("game"), o.optLong("value"), o.optString("id"));
                    } catch (RuntimeException ignored) {
                    }
            }
            if (items.isEmpty()) {
                if (raw.equals(p.getString(LAB_KEY, null))) p.edit().remove(LAB_KEY).apply();
                return;
            }
            BlockLog.query(
                    app,
                    db -> {
                        for (JSONObject o : items) {
                            String type = o.optString("type"), id = o.optString("id");
                            long wall = o.optLong("ts", System.currentTimeMillis());
                            ZonedDateTime t =
                                    Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault());
                            db.addInspector(
                                    wall,
                                    t.toLocalDate().toString(),
                                    t.getHour(),
                                    type,
                                    "",
                                    0,
                                    RETO.equals(type) ? 1 : 0,
                                    o.optLong("value"),
                                    text(o.optString("game") + " · " + o.optString("detail")),
                                    id.isEmpty() ? null : "lab|" + id,
                                    false);
                        }
                        return Boolean.TRUE;
                    },
                    ok -> {
                        // Solo se vacía si Lab no añadió nada mientras tanto; si añadió, se volverá
                        // a leer (sin duplicar).
                        if (raw.equals(p.getString(LAB_KEY, null)))
                            p.edit().remove(LAB_KEY).apply();
                        for (JSONObject o : items) {
                            String id = o.optString("id");
                            es.foco.next.runtime.HomeHub.onEvent(
                                    app,
                                    o.optString("type"),
                                    "",
                                    o.optLong("ts", System.currentTimeMillis()),
                                    o.optLong("value"),
                                    id.isEmpty() ? null : "lab|" + id);
                        }
                    },
                    null);
        } catch (Throwable ignored) {
        }
    }

    /** Texto libre del detalle: una línea, sin comas raras ni más de 160 caracteres. */
    static String text(String value) {
        if (value == null) return "";
        String v = value.replace('\r', ' ').replace('\n', ' ').trim();
        return v.length() > 160 ? v.substring(0, 160) : v;
    }

    /**
     * CSV completo desde {@code fromDay}: resumen diario de los días antiguos, detalle reciente y
     * los bloqueos e intentos del registro de bloqueos, con hora y app. Todas las filas con las
     * mismas 9 columnas entre comillas. Hilo del log.
     */
    /**
     * Definición 1.0 revisada, etapa 1: el tiempo sale de UsageStatsManager (la fuente de Bienestar
     * digital), no de lo que alcanza a ver la Accesibilidad, que se quedaba corta (no cuenta con el
     * servicio apagado ni los huecos de más de 65 s entre eventos). Devuelve el detalle de {@code
     * days} días: tiempo por día, hora y app desde la caché horaria ({@link UsageHistory}),
     * clasificado con el plan actual; de las filas de la Accesibilidad solo se conservan las veces.
     * Las apps que no están en ninguna lista salen como {@code sin_clasificar}. Sin permiso de
     * Acceso de uso se devuelven las filas de la Accesibilidad tal cual. Hilo del log.
     */
    public static List<LogDatabase.InspectorRow> rows(
            Context c,
            LogDatabase db,
            java.time.LocalDate today,
            int days,
            es.foco.next.core.AppConfig config) {
        String from = today.minusDays(days - 1).toString();
        List<LogDatabase.InspectorRow> stored = db.inspectorRows(from);
        boolean measured = false;
        try {
            if (c != null && config != null && UsageContrast.allowed(c)) {
                UsageHistory.load(
                        c, db, today, days, ZoneId.systemDefault(), System.currentTimeMillis());
                measured = true;
            }
        } catch (RuntimeException ignored) {
        }
        if (!measured) return stored;
        Map<String, LogDatabase.InspectorRow> usage = new LinkedHashMap<>();
        String own = c.getPackageName();
        for (Map.Entry<String, Map<String, long[]>> day : db.hours(from).entrySet())
            for (Map.Entry<String, long[]> app : day.getValue().entrySet()) {
                String pkg = app.getKey(), type;
                if (pkg.equals(own)
                        || es.foco.next.core.Categories.totalBlock(pkg)
                        || es.foco.next.runtime.AppKinds.ignored(c, pkg)) continue;
                if (es.foco.next.core.FocusEngine.watched(config, pkg)) type = OCIO;
                else if (pkg.equals(config.returnApp)) type = ALTERNATIVA;
                else if (config.productiveApps.contains(pkg)) type = USO_PRODUCTIVO;
                // Cuidado, paso 0: Duolingo, Biblia, Claude, lectura… cuentan como invertido en
                // los informes aunque no estén aprobadas (solo medir; no dan minutos ni aprueban).
                else if (es.foco.next.core.care.GoodApps.good(pkg)) type = USO_PRODUCTIVO;
                else if (es.foco.next.core.FocusEngine.essential(config, pkg)
                        || pkg.contains("launcher")
                        || pkg.startsWith("com.android.")
                        || pkg.startsWith("com.samsung.android.")
                        || pkg.startsWith("com.sec.android.")) continue;
                else type = SIN_CLASIFICAR;
                long[] h = app.getValue();
                for (int i = 0; i < 24 && i < h.length; i++) {
                    // Lo sin clasificar solo entra si pasó de un minuto en esa hora: es para
                    // decidir categorías, no para listar todo el teléfono.
                    if (h[i] <= 0 || (type.equals(SIN_CLASIFICAR) && h[i] < 60_000)) continue;
                    usage.put(
                            day.getKey() + "|" + i + "|" + type + "|" + pkg,
                            new LogDatabase.InspectorRow(
                                    day.getKey(), i, type, pkg, h[i], 0, 0, ""));
                }
            }
        List<LogDatabase.InspectorRow> out = new java.util.ArrayList<>();
        for (LogDatabase.InspectorRow r : stored) {
            if (!r.pkg.isEmpty()
                    && (r.type.equals(OCIO) || r.type.equals(USO_PRODUCTIVO))
                    && es.foco.next.runtime.AppKinds.ignored(c, r.pkg))
                continue; // corrección B: también lo registrado antes
            boolean tally =
                    r.detail.isEmpty()
                            && (r.type.equals(OCIO)
                                    || r.type.equals(ALTERNATIVA)
                                    || r.type.equals(USO_PRODUCTIVO));
            if (!tally) {
                out.add(r);
                continue;
            }
            String key = r.day + "|" + r.hour + "|" + r.type + "|" + r.pkg;
            LogDatabase.InspectorRow u = usage.get(key);
            if (u == null) {
                u = new LogDatabase.InspectorRow(r.day, r.hour, r.type, r.pkg, 0, 0, 0, "");
                usage.put(key, u);
            }
            u.times += r.times;
        }
        out.addAll(usage.values());
        out.sort(
                (x, y) -> {
                    int d = x.day.compareTo(y.day);
                    return d != 0 ? d : Integer.compare(x.hour, y.hour);
                });
        return out;
    }

    public static String csv(LogDatabase db, String fromDay, Function<String, String> label) {
        return csv(db, fromDay, label, db.inspectorRows(fromDay));
    }

    /**
     * Como {@link #csv(LogDatabase,String,Function)}, con el detalle ya medido por {@link #rows}.
     */
    public static String csv(
            LogDatabase db,
            String fromDay,
            Function<String, String> label,
            List<LogDatabase.InspectorRow> measured) {
        return csv(db, fromDay, label, measured, false);
    }

    /**
     * Con {@code includeMood} false (lo normal), el ánimo no sale del teléfono: las filas {@link
     * #ANIMO} no se exportan.
     */
    public static String csv(
            LogDatabase db,
            String fromDay,
            Function<String, String> label,
            List<LogDatabase.InspectorRow> measured,
            boolean includeMood) {
        return csv(db, fromDay, label, measured, includeMood, null);
    }

    /**
     * v32 (R9): ¿esa hora natural pertenece al día lógico {@code d}? El día de Foco va de las 04:00
     * de d a las 04:00 del día siguiente (como Foco Core, {@code Routine.logicalDay}). Las filas
     * sin hora cuentan en su fecha.
     */
    public static boolean inLogicalDay(java.time.LocalDate d, String day, int hour) {
        if (hour < 0) return d.toString().equals(day);
        return (d.toString().equals(day) && hour >= 4)
                || (d.plusDays(1).toString().equals(day) && hour < 4);
    }

    /** Con {@code keep} (día, hora) solo salen esas filas; null = todas. */
    public static String csv(
            LogDatabase db,
            String fromDay,
            Function<String, String> label,
            List<LogDatabase.InspectorRow> measured,
            boolean includeMood,
            java.util.function.BiPredicate<String, Integer> keep) {
        StringBuilder csv = new StringBuilder(HEADER).append("\r\n");
        for (LogDatabase.InspectorRow r : db.inspectorDays(fromDay))
            if ((includeMood || !ANIMO.equals(r.type))
                    && (keep == null || keep.test(r.day, r.hour))) row(csv, r, label);
        for (LogDatabase.InspectorRow r : measured)
            if ((includeMood || !ANIMO.equals(r.type))
                    && (keep == null || keep.test(r.day, r.hour))) row(csv, r, label);
        // Bloqueos e intentos: salen del registro de bloqueos (cada evento es una vez; nunca suman
        // segundos).
        Map<String, LogDatabase.InspectorRow> blocks = new LinkedHashMap<>();
        List<BlockEvent> events = db.events(fromDay);
        for (BlockEvent e : events) {
            String detail;
            switch (e.type) {
                case BUDGET_LOCK:
                    detail = "limite";
                    break;
                case RULE_LOCK:
                    detail = "bloqueo_con_nombre";
                    break;
                case DNS_OFF_LOCK:
                    detail = "dns_apagado";
                    break;
                case ATTEMPT_WHILE_LOCKED:
                    detail = "intento";
                    break;
                default:
                    continue;
            }
            int hour;
            try {
                hour =
                        Instant.ofEpochMilli(e.timestamp)
                                .atZone(
                                        e.zone.isEmpty()
                                                ? ZoneId.systemDefault()
                                                : ZoneId.of(e.zone))
                                .getHour();
            } catch (RuntimeException ex) {
                hour = -1;
            }
            String key = e.day + "|" + hour + "|" + e.pkg + "|" + detail;
            LogDatabase.InspectorRow r = blocks.get(key);
            if (r == null) {
                r = new LogDatabase.InspectorRow(e.day, hour, BLOQUEO, e.pkg, 0, 0, 0, detail);
                blocks.put(key, r);
            }
            r.times++;
        }
        for (LogDatabase.InspectorRow r : blocks.values())
            if (keep == null || keep.test(r.day, r.hour)) row(csv, r, label);
        return csv.toString();
    }

    private static void row(
            StringBuilder csv, LogDatabase.InspectorRow r, Function<String, String> label) {
        String app = r.pkg;
        if (!r.pkg.isEmpty() && label != null)
            try {
                String l = label.apply(r.pkg);
                if (l != null) app = l;
            } catch (RuntimeException ignored) {
            }
        csv.append(LogCsv.cell(r.day))
                .append(',')
                .append(LogCsv.cell(r.hour < 0 ? "" : Integer.toString(r.hour)))
                .append(',')
                .append(LogCsv.cell(r.type))
                .append(',')
                .append(LogCsv.cell(r.pkg))
                .append(',')
                .append(LogCsv.cell(app))
                .append(',')
                .append(LogCsv.cell(Long.toString(r.ms / 1000)))
                .append(',')
                .append(LogCsv.cell(Long.toString(r.times)))
                .append(',')
                .append(LogCsv.cell(Long.toString(r.value)))
                .append(',')
                .append(LogCsv.cell(r.detail))
                .append("\r\n");
    }

    /** Primer día que se exporta por defecto: los últimos 7, como el CSV semanal anterior. */
    public static String weekStart(LocalDate today) {
        return today.minusDays(6).toString();
    }
}
