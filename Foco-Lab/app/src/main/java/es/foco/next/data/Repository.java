package es.foco.next.data;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;
import es.foco.next.core.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** One live state, atomic checkpoints, and an explicit one-time migration. */
public final class Repository {
    public interface Listener {
        void changed();
    }

    private static Repository instance;

    public static synchronized Repository get(Context c) {
        if (instance == null) instance = new Repository(c.getApplicationContext());
        return instance;
    }

    static synchronized void resetForTests() {
        if (instance != null) instance.file.flushPending();
        instance = null;
    }

    private final Context context;
    private final AtomicJsonFile file;
    private final FocusEngine engine = new FocusEngine();
    private AppConfig config = new AppConfig();
    private FocusState state = new FocusState();
    private final List<Listener> listeners =
            new java.util.concurrent
                    .CopyOnWriteArrayList<>(); // se añaden y recorren desde hilos distintos
    private static final java.util.regex.Pattern PACKAGE =
            java.util.regex.Pattern.compile("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+");
    private long lastSave;
    public boolean migrated, legacyVpn;

    private Repository(Context c) {
        context = c;
        file = new AtomicJsonFile(new File(c.getFilesDir(), "focus-lite-v1.json"), "los datos");
        try {
            if (file.exists())
                read(new JSONObject(new String(file.read(), StandardCharsets.UTF_8)));
            else {
                android.util.AtomicFile old =
                        new android.util.AtomicFile(
                                new File(c.getFilesDir(), "focus-state-v1.json"));
                if (old.getBaseFile().exists() || new File(old.getBaseFile() + ".bak").exists())
                    migrate(new JSONObject(new String(old.readFully(), StandardCharsets.UTF_8)));
                else preselectLeisure(c); // Fase 5: instalación nueva
                write();
            }
            FocusEngine.forget(state);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "No se pudieron abrir los datos. Los archivos originales se conservan.", e);
        }
        // Reglas absolutas sobre el plan guardado por una versión anterior: se aplican y cada
        // cambio de categoría queda en el log.
        try {
            AppConfig before = loaded != null ? loaded : copy(config);
            loaded = null;
            AbsoluteRules.enforce(config);
            config.observedApps.removeAll(AbsoluteRules.RELEASED_FROM_OBSERVATION);
            neutralize(config);
            if (!sameCategories(before, config)) write();
            logChanges(before, config, "version_nueva");
        } catch (RuntimeException ignored) {
        }
    }

    private AppConfig loaded;

    // ---- Reglas absolutas (core/AbsoluteRules): memoria del ocio y salidas de ocio pendientes
    // ----
    // Son datos, no reglas: viven en preferencias propias que la copia JSON ni ninguna pantalla
    // pueden tocar.
    private android.content.SharedPreferences absolute() {
        return context.getSharedPreferences("foco-absolutas", Context.MODE_PRIVATE);
    }

    private void rememberLeisure(Collection<String> pkgs) {
        if (pkgs.isEmpty()) return;
        String today = java.time.LocalDate.now().toString();
        android.content.SharedPreferences.Editor e = absolute().edit();
        for (String p : pkgs) e.putString("ocio|" + p, today);
        e.apply();
    }

    /** Regla 3: la app estuvo en ocio en los últimos 30 días. */
    public boolean recentLeisure(String pkg) {
        synchronized (this) {
            if (config.watchedApps.contains(pkg)) return true;
        }
        return AbsoluteRules.recentLeisure(
                absolute().getString("ocio|" + pkg, ""), java.time.LocalDate.now());
    }

    /** Regla 3: apps cuya salida de ocio está pedida y aún no cumple 24 horas. */
    public Set<String> pendingLeave() {
        Set<String> out = new LinkedHashSet<>();
        for (String k : absolute().getAll().keySet())
            if (k.startsWith("salida|")) out.add(k.substring(7));
        return out;
    }

    /**
     * Corrección B: lanzadores, barra del sistema, teclados, Foco y apps de audio no tienen
     * categoría.
     */
    private void neutralize(AppConfig c) {
        for (Set<String> set : Arrays.asList(c.watchedApps, c.observedApps, c.productiveApps))
            set.removeIf(p -> es.foco.next.runtime.AppKinds.ignored(context, p));
    }

    private static String kind(AppConfig c, String p) {
        return c.watchedApps.contains(p)
                ? "ocio"
                : c.observedApps.contains(p)
                        ? "observacion"
                        : c.productiveApps.contains(p) ? "productiva" : "sin_categoria";
    }

    private static boolean sameCategories(AppConfig a, AppConfig b) {
        return a.watchedApps.equals(b.watchedApps)
                && a.observedApps.equals(b.observedApps)
                && a.productiveApps.equals(b.productiveApps);
    }

    /**
     * Regla 3: todo cambio de categoría queda en el log del Inspector, con su origen. Nunca lanza.
     */
    private void logChanges(AppConfig old, AppConfig neu, String origin) {
        try {
            Set<String> all = new LinkedHashSet<>();
            for (AppConfig c : new AppConfig[] {old, neu}) {
                all.addAll(c.watchedApps);
                all.addAll(c.observedApps);
                all.addAll(c.productiveApps);
            }
            for (String p : all) {
                String a = kind(old, p), b = kind(neu, p);
                if (!a.equals(b))
                    es.foco.next.log.InspectorLog.event(
                            context,
                            es.foco.next.log.InspectorLog.CATEGORIA,
                            p,
                            0,
                            a + ">" + b + ";" + origin,
                            null);
            }
            rememberLeisure(old.watchedApps);
            rememberLeisure(neu.watchedApps);
        } catch (Throwable ignored) {
        }
    }

    private void incident(String detail) {
        try {
            es.foco.next.log.InspectorLog.event(
                    context,
                    es.foco.next.log.InspectorLog.INCIDENTE,
                    "",
                    0,
                    detail.length() > 400 ? detail.substring(0, 400) : detail,
                    null);
        } catch (Throwable ignored) {
        }
    }

    private String label(String pkg) {
        try {
            return context.getPackageManager()
                    .getApplicationLabel(context.getPackageManager().getApplicationInfo(pkg, 0))
                    .toString();
        } catch (Exception e) {
            return pkg;
        }
    }

    /**
     * Regla 3 al guardar el plan a mano. Aprobar como productiva una app con ocio en los últimos 30
     * días se rechaza. Sacar apps de ocio (salvo la Regla 1, que nunca salen) no se puede pedir de
     * noche ni en Weekend Lock; si se pide, siguen en ocio y salen a las 24 horas ({@link
     * #applyPending}). Devuelve el aviso para la pantalla, o null.
     */
    private String leaveRules(AppConfig old, AppConfig neu) {
        for (String p : neu.productiveApps)
            if (!old.productiveApps.contains(p)) {
                if (Categories.neverProductive(p, false))
                    throw new IllegalStateException(
                            label(p) + " no puede ser productiva: regla absoluta.");
                if (recentLeisure(p))
                    throw new IllegalStateException(
                            label(p)
                                    + " estuvo en ocio en los últimos 30 días: no puede aprobarse como productiva.");
            }
        List<String> leaving = new ArrayList<>();
        for (String p : old.watchedApps)
            if (!neu.watchedApps.contains(p)
                    && !AbsoluteRules.social(p)
                    && !es.foco.next.runtime.AppKinds.ignored(context, p)) leaving.add(p);
        if (leaving.isEmpty()) return null;
        if (!AbsoluteRules.canRequestLeave(
                java.time.LocalDateTime.now(),
                es.foco.next.runtime.AppKinds.autoTime(context),
                es.foco.next.runtime.Weekend.active(context)))
            throw new IllegalStateException(
                    "Sacar apps de ocio no se puede pedir de noche (21:00 a 07:00), con la hora automática apagada ni durante el Weekend Lock.");
        neu.watchedApps.addAll(leaving);
        neu.observedApps.removeAll(leaving);
        neu.productiveApps.removeAll(leaving);
        android.content.SharedPreferences p = absolute();
        android.content.SharedPreferences.Editor e = p.edit();
        long wall = System.currentTimeMillis(), el = SystemClock.elapsedRealtime();
        int b = boot(context);
        List<String> names = new ArrayList<>();
        for (String pkg : leaving) {
            names.add(label(pkg));
            String k = "salida|" + pkg;
            if (p.contains(k)) continue; // ya pedida: el plazo no vuelve a empezar
            e.putString(k, wall + "|" + el + "|" + b);
            try {
                es.foco.next.log.InspectorLog.event(
                        context,
                        es.foco.next.log.InspectorLog.CATEGORIA,
                        pkg,
                        0,
                        "ocio>salida_pedida;24h;manual",
                        null);
            } catch (Throwable ignored) {
            }
        }
        e.apply();
        return "Quitar de ocio tarda 24 horas: "
                + String.join(", ", names)
                + " sigue en ocio hasta entonces.";
    }

    /** Regla 3: aplica las salidas de ocio que ya cumplieron 24 horas. Nunca lanza. */
    public void applyPending() {
        boolean changed = false;
        try {
            synchronized (this) {
                android.content.SharedPreferences p = absolute();
                android.content.SharedPreferences.Editor e = p.edit();
                long wall = System.currentTimeMillis(), el = SystemClock.elapsedRealtime();
                int b = boot(context);
                boolean auto = es.foco.next.runtime.AppKinds.autoTime(context);
                AppConfig next = copy(config);
                boolean edited = false;
                for (Map.Entry<String, ?> en : p.getAll().entrySet()) {
                    String k = en.getKey();
                    if (!k.startsWith("salida|")) continue;
                    String pkg = k.substring(7);
                    if (!next.watchedApps.contains(pkg) || AbsoluteRules.social(pkg)) {
                        e.remove(k);
                        edited = true;
                        continue;
                    }
                    try {
                        String[] v = String.valueOf(en.getValue()).split("\\|");
                        if (AbsoluteRules.leaveDue(
                                Long.parseLong(v[0]),
                                Long.parseLong(v[1]),
                                Integer.parseInt(v[2]),
                                wall,
                                el,
                                b,
                                auto)) {
                            next.watchedApps.remove(pkg);
                            e.remove(k);
                            edited = true;
                            changed = true;
                        }
                    } catch (RuntimeException x) {
                        e.remove(k);
                        edited = true;
                    }
                }
                if (edited) e.apply();
                if (!changed) return;
                validate(next, state);
                neutralize(next);
                AppConfig old = config;
                config = next;
                try {
                    write();
                } catch (RuntimeException x) {
                    config = old;
                    return;
                }
                logChanges(old, next, "salida_24h_cumplida");
            }
        } catch (RuntimeException ignored) {
            return;
        }
        for (Listener l : new ArrayList<>(listeners)) l.changed();
    }

    /**
     * Copia defensiva. Auditoría: copia directa (antes, ida y vuelta por JSON en cada evento de
     * Accesibilidad).
     */
    public synchronized AppConfig config() {
        return copy(config);
    }

    /** Para el reloj de la pantalla: evita copiar el plan entero cada segundo. */
    public synchronized boolean enabled() {
        return config.enabled;
    }

    /**
     * Auditoría de producto: si el archivo del plan no se puede leer, se aparta (no se borra) para
     * poder empezar de nuevo. Solo actúa cuando no hay datos abiertos. Devuelve false si no había
     * nada que apartar o no se pudo.
     */
    public static synchronized boolean quarantine(Context c) {
        if (instance != null) return false;
        File dir = c.getApplicationContext().getFilesDir();
        String stamp = "-apartado-" + System.currentTimeMillis();
        boolean moved = false;
        for (String name :
                new String[] {
                    "focus-lite-v1.json",
                    "focus-lite-v1.json.bak",
                    "focus-lite-v1.json.new",
                    "focus-state-v1.json",
                    "focus-state-v1.json.bak"
                }) {
            File f = new File(dir, name);
            if (f.exists()) {
                if (!f.renameTo(new File(dir, name + stamp))) return false;
                moved = true;
            }
        }
        return moved;
    }

    static AppConfig copy(AppConfig c) {
        AppConfig o = new AppConfig();
        o.enabled = c.enabled;
        o.watchedApps = new LinkedHashSet<>(c.watchedApps);
        o.sensitiveApps = new LinkedHashSet<>(c.sensitiveApps);
        o.intention = c.intention;
        o.returnApp = c.returnApp;
        o.observedApps = new LinkedHashSet<>(c.observedApps);
        o.productiveApps = new LinkedHashSet<>(c.productiveApps);
        return o;
    }

    public synchronized FocusState state() {
        return decodeState(encodeState(state));
    }

    public synchronized boolean locked() {
        return liveUsed() >= FocusEngine.BUDGET && remaining() > 0;
    }

    public synchronized long remaining() {
        return FocusEngine.remaining(
                state, System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot(context));
    }

    public synchronized long liveUsed() {
        if (remaining() == 0) return 0;
        long delta =
                state.observedBoot == boot(context) && state.observedElapsed >= 0
                        ? SystemClock.elapsedRealtime() - state.observedElapsed
                        : 0;
        if (delta < 0 || delta > FocusEngine.CHECKPOINT + 5000) delta = 0;
        return Math.min(
                FocusEngine.BUDGET, state.usedMillis + (state.observedAlternative ? 0 : delta));
    }

    // Foco 1.0, etapa 1: alternativa abierta desde Foco que aún no se ha medido (paquete, cuándo se
    // eligió y desde cuándo está delante).
    private String chosen = "";
    private long chosenAt, chosenSince = -1;

    private static long total(Map<String, Map<String, Long>> history, String pkg) {
        long n = 0;
        for (Map<String, Long> row : history.values()) n += row.getOrDefault(pkg, 0L);
        return n;
    }

    public synchronized FocusEngine.Decision sample(FocusEngine.Input in, boolean checkpoint) {
        long used = state.usedMillis;
        String day = state.day, pkg = state.observedPackage;
        boolean present = state.window.present(), wasAlternative = state.observedAlternative;
        Map<String, Map<String, Long>> measured =
                wasAlternative ? state.weeklyAlternative : state.weeklyOcio;
        long before = pkg.isEmpty() ? 0 : total(measured, pkg);
        FocusEngine.Decision d = engine.step(config, state, in);
        inspect(in, pkg, wasAlternative, pkg.isEmpty() ? 0 : total(measured, pkg) - before);
        boolean threshold = used < FocusEngine.BUDGET && state.usedMillis >= FocusEngine.BUDGET;
        if (checkpoint
                || threshold
                || d.warning
                || present != state.window.present()
                || !day.equals(state.day)
                || !pkg.equals(state.observedPackage)
                || ((!pkg.isEmpty() || !state.observedPackage.isEmpty())
                        && in.elapsed - lastSave >= FocusEngine.CHECKPOINT)) {
            writeLater();
            lastSave = in.elapsed;
        }
        return d;
    }

    /**
     * Foco 1.0, etapa 1: lleva al log del Inspector lo que acaba de medir el motor. Tiempo: lo que
     * sumó la app observada, en la hora actual. Veces: una por cada vez que una app de ocio o la
     * alternativa pasa a estar delante. Y, si se abrió una alternativa desde Foco, cuánto tiempo
     * estuvo realmente delante. Nunca lanza.
     */
    private void inspect(FocusEngine.Input in, String before, boolean wasAlternative, long gained) {
        try {
            if (gained > 0 && gained <= 24 * FocusEngine.HOUR)
                es.foco.next.log.InspectorLog.usage(
                        context,
                        wasAlternative
                                ? es.foco.next.log.InspectorLog.ALTERNATIVA
                                : es.foco.next.log.InspectorLog.OCIO,
                        before,
                        in.wall,
                        gained,
                        0);
            String now = state.observedPackage;
            if (!now.isEmpty() && !now.equals(before))
                es.foco.next.log.InspectorLog.usage(
                        context,
                        state.observedAlternative
                                ? es.foco.next.log.InspectorLog.ALTERNATIVA
                                : es.foco.next.log.InspectorLog.OCIO,
                        now,
                        in.wall,
                        0,
                        1);
            productive(in);
            if (chosen.isEmpty()) return;
            boolean front = in.interactive && chosen.equals(in.pkg);
            if (front) {
                if (chosenSince < 0) chosenSince = in.elapsed;
                return;
            }
            if (chosenSince >= 0) {
                long ms = Math.max(0, Math.min(4 * FocusEngine.HOUR, in.elapsed - chosenSince));
                es.foco.next.log.InspectorLog.event(
                        context,
                        es.foco.next.log.InspectorLog.ALTERNATIVA,
                        chosen,
                        in.wall,
                        ms,
                        1,
                        0,
                        "abierta_desde_foco",
                        null);
                chosen = "";
                chosenSince = -1;
            } else if (in.elapsed - chosenAt > 60_000 || in.elapsed < chosenAt) {
                // Se eligió pero nunca llegó a estar delante: cuenta la apertura, con 0 segundos.
                es.foco.next.log.InspectorLog.event(
                        context,
                        es.foco.next.log.InspectorLog.ALTERNATIVA,
                        chosen,
                        in.wall,
                        0,
                        1,
                        0,
                        "abierta_desde_foco",
                        null);
                chosen = "";
                chosenSince = -1;
            }
        } catch (Throwable ignored) {
        }
    }

    // ---- Uso productivo: solo se mide (v26: ya no da minutos; el ocio lo abre Foco Core) ----
    public static final long IDLE_CUT = 2 * 60_000L, MIN_STRETCH = 60_000L;

    /**
     * Tiempo de hoy de una app con límite, según lo que mide el motor (para el tope diario propio).
     */
    public synchronized long usedToday(String pkg) {
        return java.time.LocalDate.now().toString().equals(state.day)
                ? state.dailyPackageMillis.getOrDefault(pkg, 0L)
                : 0;
    }

    // Uso real de una app productiva: delante, con la pantalla encendida y con actividad al menos
    // cada 2 minutos.
    private volatile String productivePkg = "";
    private long productiveSince = -1, productiveTouch;

    private void productive(FocusEngine.Input in) {
        boolean front =
                in.interactive
                        && config.productiveApps.contains(in.pkg)
                        && !es.foco.next.runtime.AppKinds.ignored(context, in.pkg);
        if (!productivePkg.isEmpty() && (!front || !productivePkg.equals(in.pkg)))
            closeProductive(in.elapsed);
        if (front && productivePkg.isEmpty()) {
            productivePkg = in.pkg;
            productiveSince = in.elapsed;
            productiveTouch = in.elapsed;
            es.foco.next.log.InspectorLog.usage(
                    context, es.foco.next.log.InspectorLog.USO_PRODUCTIVO, in.pkg, in.wall, 0, 1);
        }
    }

    /** Cierra el tramo en curso: solo cuentan tramos de 60 s o más, y quedan en el registro. */
    private void closeProductive(long elapsed) {
        String pkg = productivePkg;
        long ms = Math.min(elapsed, productiveTouch + IDLE_CUT) - productiveSince;
        productivePkg = "";
        productiveSince = -1;
        if (ms < MIN_STRETCH || ms > 6 * FocusEngine.HOUR) return;
        es.foco.next.log.InspectorLog.usage(
                context,
                es.foco.next.log.InspectorLog.USO_PRODUCTIVO,
                pkg,
                System.currentTimeMillis(),
                ms,
                0);
    }

    /**
     * Actividad en pantalla (scroll, toque, cambio de página) de la app que está delante. Barato si
     * no es productiva.
     */
    public void touch(String pkg, long elapsed) {
        if (productivePkg.isEmpty()) return;
        synchronized (this) {
            if (!productivePkg.equals(pkg)) return;
            if (elapsed - productiveTouch > IDLE_CUT) {
                closeProductive(elapsed);
                productivePkg = pkg;
                productiveSince = elapsed;
            }
            productiveTouch = elapsed;
        }
    }

    public synchronized void forget() {
        FocusEngine.forget(state);
        write();
    }

    /**
     * Guarda el plan editado a mano. Devuelve un aviso para la pantalla (Regla 3: salida de ocio en
     * 24 h) o null.
     */
    public String saveConfig(AppConfig next) {
        String note;
        synchronized (this) {
            if (locked())
                throw new IllegalStateException(
                        "Tu hora sigue en curso. El plan se puede cambiar al terminar.");
            AppConfig clean = decodeConfig(encodeConfig(next));
            note = leaveRules(config, clean);
            validate(clean, state);
            neutralize(clean);
            AppConfig old = config;
            config = clean;
            try {
                write();
            } catch (RuntimeException e) {
                config = old;
                throw e;
            }
            logChanges(old, clean, "manual");
        }
        for (Listener l : new ArrayList<>(listeners)) l.changed();
        return note;
    }

    /**
     * Foco 1.0, etapa 2: cambio que solo endurece (app nueva a observación, observación a ocio). A
     * diferencia de {@link #saveConfig}, se permite durante el bloqueo; si el cambio aflojara algo,
     * se rechaza.
     */
    public void harden(java.util.function.Consumer<AppConfig> action) {
        harden("automatico", action);
    }

    /** Igual, con el origen que queda en el log del Inspector junto a cada cambio de categoría. */
    public void harden(String origin, java.util.function.Consumer<AppConfig> action) {
        synchronized (this) {
            AppConfig next = copy(config);
            action.accept(next);
            String why = PlanChange.reason(config, next);
            if (why != null) throw new IllegalStateException(why);
            AppConfig clean = decodeConfig(encodeConfig(next));
            validate(clean, state);
            neutralize(clean);
            AppConfig old = config;
            config = clean;
            if (state.usedMillis >= FocusEngine.BUDGET) {
                state.lockedApps.addAll(clean.watchedApps);
                state.lockedApps.addAll(clean.observedApps);
            }
            try {
                write();
            } catch (RuntimeException e) {
                config = old;
                throw e;
            }
            logChanges(old, clean, origin);
        }
        for (Listener l : new ArrayList<>(listeners)) l.changed();
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public synchronized void recordChoice(String pkg) {
        checkPackage(pkg);
        String day = java.time.LocalDate.now().toString();
        FocusEngine.record(state.weeklyChoices, day, pkg, 1);
        FocusEngine.prune(state, day);
        write();
        // Etapa 1: tu alternativa elegida ya se mide por horas; las demás se cronometran desde aquí
        // hasta que dejan de estar delante.
        if (!pkg.equals(config.returnApp)) {
            chosen = pkg;
            chosenAt = SystemClock.elapsedRealtime();
            chosenSince = -1;
        }
    }

    public synchronized String exportWeek() {
        FocusEngine.prune(state, java.time.LocalDate.now().toString());
        StringBuilder csv =
                new StringBuilder("fecha,tipo,paquete,app,segundos_observados,veces\r\n");
        Map<String, Map<String, Map<String, Long>>> kinds = new LinkedHashMap<>();
        kinds.put("ocio", state.weeklyOcio);
        kinds.put("alternativa_elegida", state.weeklyAlternative);
        kinds.put("alternativa_abierta_desde_foco", state.weeklyChoices);
        for (var kind : kinds.entrySet())
            for (String day : new TreeSet<>(kind.getValue().keySet()))
                for (var e : kind.getValue().get(day).entrySet()) {
                    String label = e.getKey();
                    try {
                        label =
                                context.getPackageManager()
                                        .getApplicationLabel(
                                                context.getPackageManager()
                                                        .getApplicationInfo(label, 0))
                                        .toString();
                    } catch (Exception ignored) {
                    }
                    boolean choice = kind.getKey().equals("alternativa_abierta_desde_foco");
                    csv.append(cell(day))
                            .append(',')
                            .append(cell(kind.getKey()))
                            .append(',')
                            .append(cell(e.getKey()))
                            .append(',')
                            .append(cell(label))
                            .append(',')
                            .append(choice ? 0 : e.getValue() / 1000)
                            .append(',')
                            .append(choice ? e.getValue() : 0)
                            .append("\r\n");
                }
        for (String day : new TreeSet<>(state.weeklyBlocks.keySet()))
            csv.append(cell(day))
                    .append(",bloqueo_por_limite,,,0,")
                    .append(state.weeklyBlocks.get(day))
                    .append("\r\n");
        return csv.toString();
    }

    private static String cell(String value) {
        if (!value.isEmpty() && "=+-@\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public synchronized String exportBackup() {
        return document().toString();
    }

    public void importBackup(String raw) {
        synchronized (this) {
            if (locked())
                throw new IllegalStateException("No puedes sustituir el plan durante el bloqueo.");
            if (raw.length() > 1_048_576)
                throw new IllegalArgumentException("Copia demasiado grande");
            try {
                JSONObject j = new JSONObject(raw);
                if (j.getInt("format") != 51)
                    throw new IllegalArgumentException("Usa una copia de Foco Ligero");
                AppConfig c = decodeConfig(j.getJSONObject("config"));
                FocusState s = decodeState(j.getJSONObject("state"));
                // Reglas absolutas: lo que la copia intente cambiar de ellas se ignora y queda como
                // incidente.
                List<String> broken = AbsoluteRules.enforce(c);
                List<String> kept = new ArrayList<>();
                for (String p : config.watchedApps)
                    if (!c.watchedApps.contains(p)
                            && !es.foco.next.runtime.AppKinds.ignored(context, p)) {
                        c.watchedApps.add(p);
                        kept.add(p);
                    }
                for (String p : new ArrayList<>(c.productiveApps))
                    if (!config.productiveApps.contains(p)
                            && (Categories.neverProductive(p, false) || recentLeisure(p))) {
                        c.productiveApps.remove(p);
                        broken.add(p + ":productiva_con_ocio_reciente");
                    }
                validate(c, s);
                neutralize(c);
                // Regla 4: el banco no se restaura desde una copia (antes una copia editada podía
                // llenarlo).
                s.bankMs = state.bankMs;
                s.earnedTodayMs = state.earnedTodayMs;
                s.earnedDay = state.earnedDay;
                s.productiveCarryMs = state.productiveCarryMs;
                s.freeDay = state.freeDay;
                AppConfig oldConfig = config;
                FocusState oldState = state;
                config = c;
                state = s;
                try {
                    write();
                } catch (RuntimeException e) {
                    config = oldConfig;
                    state = oldState;
                    throw e;
                }
                if (!broken.isEmpty())
                    incident("import_reglas_absolutas;" + String.join(";", broken));
                if (!kept.isEmpty())
                    incident("import_quita_ocio;ignorado;" + String.join(";", kept));
                logChanges(oldConfig, c, "copia_importada");
            } catch (JSONException e) {
                throw new IllegalArgumentException("Copia no válida", e);
            }
        }
        for (Listener l : new ArrayList<>(listeners)) l.changed();
    }

    // --- Fase 5: instalación nueva → marca las apps de ocio del catálogo que están instaladas ---
    // Visibilidad (Android 11+): el <queries> de LAUNCHER ya declarado basta; no se usa
    // QUERY_ALL_PACKAGES.
    // Esenciales, bancos (paquete o etiqueta) y WhatsApp nunca se marcan. Si falla, se empieza sin
    // apps (como 0.58).
    private void preselectLeisure(Context c) {
        try {
            Set<String> installed = new LinkedHashSet<>();
            for (android.content.pm.ResolveInfo r :
                    c.getPackageManager()
                            .queryIntentActivities(
                                    new android.content.Intent(android.content.Intent.ACTION_MAIN)
                                            .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
                                    0))
                if (r.activityInfo != null) installed.add(r.activityInfo.packageName);
            es.foco.next.runtime.BankAppGuard banks = new es.foco.next.runtime.BankAppGuard(c);
            AppConfig base = config;
            config.watchedApps.addAll(
                    AlternativeCatalog.preselect(base, installed, p -> banks.hide(base, p)));
        } catch (RuntimeException ignored) {
            config.watchedApps.clear();
        }
    }

    // --- fin Fase 5 ---
    public static int boot(Context c) {
        try {
            return Settings.Global.getInt(c.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private void migrate(JSONObject j) throws JSONException {
        JSONObject c = j.getJSONObject("config"), s = j.getJSONObject("state");
        config.enabled = c.optBoolean("enabled", true);
        config.returnApp = c.optString("returnApp", "");
        if (AlternativeCatalog.social(config.returnApp)) config.returnApp = "";
        config.watchedApps = set(c.optJSONArray("watchedApps"));
        config.sensitiveApps = set(c.optJSONArray("sensitiveApps"));
        if (c.optBoolean("autoSocial", true))
            Collections.addAll(
                    config.watchedApps,
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill",
                    "com.instagram.android",
                    "com.facebook.katana",
                    "com.facebook.lite",
                    "com.google.android.youtube",
                    "com.twitter.android",
                    "com.reddit.frontpage",
                    "com.snapchat.android");
        config.watchedApps.removeAll(set(c.optJSONArray("excludedApps")));
        config.watchedApps.removeIf(
                p -> FocusEngine.essential(config, p) || p.equals(config.returnApp));
        state.day = s.optString("day", "");
        state.dailyPackageMillis = map(s.optJSONObject("dailyPackageMillis"));
        // Old 10-minute/one-hour visual cycles have different semantics. Archive, don't
        // reinterpret.
        if (!state.day.isEmpty())
            state.weeklyOcio.put(state.day, new LinkedHashMap<>(state.dailyPackageMillis));
        migrated = true;
        legacyVpn = c.optBoolean("dnsEnabled", false);
        validate(config, state);
    }

    private void read(JSONObject j) throws JSONException {
        if (j.getInt("format") != 51) throw new IllegalArgumentException("Formato desconocido");
        config = decodeConfig(j.getJSONObject("config"));
        loaded = copy(config);
        state = decodeState(j.getJSONObject("state"));
        migrated = j.optBoolean("migrated");
        legacyVpn = j.optBoolean("legacyVpn");
        validate(config, state);
    }

    private JSONObject document() {
        JSONObject j = new JSONObject();
        put(j, "format", 51);
        put(j, "config", encodeConfig(config));
        put(j, "state", encodeState(state));
        put(j, "migrated", migrated);
        put(j, "legacyVpn", legacyVpn);
        return j;
    }

    private void write() {
        file.write(document().toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Auditoría: puntos de control del servicio fuera del hilo principal (mismo contenido, mismo
     * archivo).
     */
    private void writeLater() {
        file.writeLater(document().toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JSONObject encodeConfig(AppConfig c) {
        JSONObject j = new JSONObject();
        put(j, "enabled", c.enabled);
        put(j, "watchedApps", new JSONArray(c.watchedApps));
        put(j, "sensitiveApps", new JSONArray(c.sensitiveApps));
        put(j, "intention", c.intention);
        put(j, "returnApp", c.returnApp);
        put(j, "observedApps", new JSONArray(c.observedApps));
        put(j, "productiveApps", new JSONArray(c.productiveApps));
        return j;
    }

    private static AppConfig decodeConfig(JSONObject j) {
        AppConfig c = new AppConfig();
        c.enabled = j.optBoolean("enabled", true);
        c.watchedApps = set(j.optJSONArray("watchedApps"));
        c.sensitiveApps = set(j.optJSONArray("sensitiveApps"));
        c.intention = j.optString("intention", c.intention);
        c.returnApp = j.optString("returnApp", "");
        if (AlternativeCatalog.social(c.returnApp)) c.returnApp = "";
        c.observedApps = set(j.optJSONArray("observedApps"));
        c.productiveApps = set(j.optJSONArray("productiveApps"));
        return c;
    }

    private static JSONObject encodeState(FocusState s) {
        JSONObject j = new JSONObject(), d = new JSONObject();
        put(d, "wall", s.window.wall);
        put(d, "elapsed", s.window.elapsed);
        put(d, "boot", s.window.boot);
        put(j, "window", d);
        put(j, "usedMillis", s.usedMillis);
        put(j, "warningSent", s.warningSent);
        put(j, "lockedApps", new JSONArray(s.lockedApps));
        put(j, "day", s.day);
        put(j, "dailyPackageMillis", new JSONObject(s.dailyPackageMillis));
        put(j, "weeklyOcio", nested(s.weeklyOcio));
        put(j, "weeklyAlternative", nested(s.weeklyAlternative));
        put(j, "weeklyChoices", nested(s.weeklyChoices));
        put(j, "weeklyBlocks", new JSONObject(s.weeklyBlocks));
        put(j, "bankMs", s.bankMs);
        put(j, "earnedTodayMs", s.earnedTodayMs);
        put(j, "productiveCarryMs", s.productiveCarryMs);
        put(j, "earnedDay", s.earnedDay);
        put(j, "freeDay", s.freeDay);
        return j;
    }

    private static FocusState decodeState(JSONObject j) {
        FocusState s = new FocusState();
        JSONObject d = j.optJSONObject("window");
        if (d != null) {
            s.window.wall = d.optLong("wall");
            s.window.elapsed = d.optLong("elapsed");
            s.window.boot = d.optInt("boot", -1);
        }
        s.usedMillis = j.optLong("usedMillis");
        s.warningSent = j.optBoolean("warningSent");
        s.lockedApps = set(j.optJSONArray("lockedApps"));
        s.day = j.optString("day", "");
        s.dailyPackageMillis = map(j.optJSONObject("dailyPackageMillis"));
        s.weeklyOcio = nestedMap(j.optJSONObject("weeklyOcio"));
        s.weeklyAlternative = nestedMap(j.optJSONObject("weeklyAlternative"));
        s.weeklyChoices = nestedMap(j.optJSONObject("weeklyChoices"));
        s.weeklyBlocks = map(j.optJSONObject("weeklyBlocks"));
        // v26: el banco de minutos ya no existe; un saldo guardado de antes no se lee.
        s.bankMs = 0;
        s.earnedTodayMs = 0;
        s.productiveCarryMs = 0;
        s.earnedDay = j.optString("earnedDay", "");
        s.freeDay = j.optString("freeDay", "");
        return s;
    }

    private static JSONObject nested(Map<String, Map<String, Long>> values) {
        JSONObject j = new JSONObject();
        for (var e : values.entrySet()) put(j, e.getKey(), new JSONObject(e.getValue()));
        return j;
    }

    private static Map<String, Map<String, Long>> nestedMap(JSONObject j) {
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        if (j == null) return out;
        if (j.length() > 7)
            throw new IllegalArgumentException("El registro contiene más de siete días");
        Iterator<String> keys = j.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            out.put(k, map(j.optJSONObject(k)));
        }
        return out;
    }

    private static Set<String> set(JSONArray a) {
        Set<String> s = new LinkedHashSet<>();
        if (a != null) {
            if (a.length() > 2000) throw new IllegalArgumentException("Lista demasiado grande");
            for (int i = 0; i < a.length(); i++) s.add(a.optString(i));
        }
        return s;
    }

    private static Map<String, Long> map(JSONObject j) {
        Map<String, Long> m = new LinkedHashMap<>();
        if (j != null) {
            if (j.length() > 2000) throw new IllegalArgumentException("Lista demasiado grande");
            Iterator<String> keys = j.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                m.put(k, j.optLong(k, -1));
            }
        }
        return m;
    }

    private static void validate(AppConfig c, FocusState s) {
        if (c.intention.length() > 120
                || s.usedMillis < 0
                || s.usedMillis > FocusEngine.BUDGET
                || s.window.wall < 0
                || s.window.elapsed < 0
                || s.window.boot < -1) throw new IllegalArgumentException("Valores no válidos");
        for (Set<String> pkgs :
                java.util.Arrays.asList(
                        c.watchedApps,
                        c.sensitiveApps,
                        s.lockedApps,
                        c.observedApps,
                        c.productiveApps)) for (String p : pkgs) checkPackage(p);
        // Foco 1.0, etapa 2: el bloqueo total no admite excepciones, y una app tiene una sola
        // categoría (gana la más estricta).
        c.sensitiveApps.removeIf(Categories::totalBlock);
        c.productiveApps.removeIf(Categories::totalBlock);
        c.watchedApps.removeIf(Categories::totalBlock);
        c.observedApps.removeIf(
                Categories::totalBlock); // Telegram solo en bloqueo total: no ensucia el conteo de
        // ocio
        if (Categories.totalBlock(c.returnApp))
            c.returnApp = ""; // antes quedaba dentro del comentario de la línea anterior y no se
        // ejecutaba
        // Reglas absolutas: Regla 1 (redes sociales siempre en ocio) y corrección A (herramientas
        // fuera de observación).
        AbsoluteRules.enforce(c);
        c.observedApps.removeAll(AbsoluteRules.RELEASED_FROM_OBSERVATION);
        // Regla 3: marcar una app de ocio como esencial era sacarla de ocio al instante. Primero
        // tiene que salir de ocio (24 h).
        c.sensitiveApps.removeAll(c.watchedApps);
        c.observedApps.removeAll(c.watchedApps);
        c.productiveApps.removeAll(c.watchedApps);
        c.productiveApps.removeAll(c.observedApps);
        if (!c.returnApp.isEmpty()) {
            checkPackage(c.returnApp);
            if (c.watchedApps.contains(c.returnApp))
                throw new IllegalArgumentException("La alternativa no puede estar en ocio");
        }
        for (var e : s.dailyPackageMillis.entrySet()) {
            checkPackage(e.getKey());
            if (e.getValue() < 0 || e.getValue() > 24 * FocusEngine.HOUR)
                throw new IllegalArgumentException("Tiempo no válido");
        }
        for (Map<String, Map<String, Long>> history :
                java.util.Arrays.asList(s.weeklyOcio, s.weeklyAlternative, s.weeklyChoices)) {
            if (history.size() > 7) throw new IllegalArgumentException("Registro demasiado grande");
            for (var row : history.entrySet()) {
                java.time.LocalDate.parse(row.getKey());
                for (var value : row.getValue().entrySet()) {
                    checkPackage(value.getKey());
                    if (value.getValue() < 0 || value.getValue() > 24 * FocusEngine.HOUR)
                        throw new IllegalArgumentException("Registro no válido");
                }
            }
        }
        if (s.weeklyBlocks.size() > 7)
            throw new IllegalArgumentException("Registro demasiado grande");
        for (var e : s.weeklyBlocks.entrySet()) {
            java.time.LocalDate.parse(e.getKey());
            if (e.getValue() < 0 || e.getValue() > 100000)
                throw new IllegalArgumentException("Registro no válido");
        }
        if (s.usedMillis >= FocusEngine.BUDGET && s.lockedApps.isEmpty()) {
            s.lockedApps.addAll(c.watchedApps);
            s.lockedApps.addAll(c.observedApps);
        }
    }

    private static void checkPackage(String p) {
        if (p.length() > 256 || !PACKAGE.matcher(p).matches())
            throw new IllegalArgumentException("Paquete no válido");
    }

    private static void put(JSONObject j, String k, Object v) {
        try {
            j.put(k, v);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
