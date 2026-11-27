package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.home.Companion;
import es.foco.next.core.home.HomeState;
import es.foco.next.core.home.HomeState.Estado;
import es.foco.next.core.home.HomeState.Fact;
import es.foco.next.core.home.HomeState.Kind;
import es.foco.next.core.home.HomeState.Snapshot;
import es.foco.next.data.Repository;
import es.foco.next.log.BlockEvent;
import es.foco.next.log.InspectorLog;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Etapa 1.2: EstadoActual de Foco Home. Se recalcula cuando se escribe un evento en el log
 * (InspectorLog.usage/event y BlockLog.record llaman aquí) y al mostrarse el inicio; nunca con un
 * temporizador. La decisión es {@link HomeState#step} (pura, con pruebas).
 *
 * <p>Persistencia: SharedPreferences "foco-home-state" (el proyecto no usa DataStore; añadirlo
 * sería una dependencia nueva solo para 7 campos, y SharedPreferences ya es lo que usan Weekend,
 * DNS y el ambiente). Trabajo en un hilo propio; nunca lanza: si falla, el bloqueo sigue igual.
 */
public final class HomeHub {
    private HomeHub() {}

    /** EstadoActual: lo que lee el inicio. Inmutable. */
    public static final class EstadoActual {
        public final Estado estado;
        public final long minutosInvertidosHoy, ocioHoyMin, extraHoyMin;
        public final String proximoPaso, motivo;
        public final int rachaDias;
        public final long desde;
        public final boolean sinBloqueGratisHoy;

        /**
         * Para el compañero: retos superados hoy y descansos reales (pantalla apagada ≥ 30 min).
         */
        public int retosHoy, descansosHoy;

        public long ultimoDescansoMin;

        EstadoActual(
                Estado estado,
                String proximoPaso,
                long minutosInvertidosHoy,
                int rachaDias,
                long desde,
                String motivo,
                long ocioHoyMin,
                long extraHoyMin,
                boolean sinBloqueGratisHoy) {
            this.estado = estado;
            this.proximoPaso = proximoPaso;
            this.minutosInvertidosHoy = minutosInvertidosHoy;
            this.rachaDias = rachaDias;
            this.desde = desde;
            this.motivo = motivo;
            this.ocioHoyMin = ocioHoyMin;
            this.extraHoyMin = extraHoyMin;
            this.sinBloqueGratisHoy = sinBloqueGratisHoy;
        }
    }

    public interface Listener {
        void changed(EstadoActual e);
    }

    static final String PREFS = "foco-home-state";
    private static final ExecutorService IO =
            Executors.newSingleThreadExecutor(
                    r -> {
                        Thread t = new Thread(r, "foco-home");
                        t.setDaemon(true);
                        return t;
                    });
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private static volatile EstadoActual last;

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    /** Último estado calculado (o null antes del primero). */
    public static EstadoActual last() {
        return last;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------------- Entradas (las llama el log) ----------------

    /** InspectorLog.usage: uso real de una app (ocio, productivo, alternativa). */
    public static void onUsage(Context c, String type, String pkg, long wall, long ms) {
        Kind k =
                InspectorLog.OCIO.equals(type)
                        ? Kind.OCIO
                        : InspectorLog.USO_PRODUCTIVO.equals(type)
                                        || InspectorLog.ALTERNATIVA.equals(type)
                                ? Kind.PRODUCTIVE
                                : null;
        if (k == null || ms <= 0 || c == null) return;
        Context app = c.getApplicationContext();
        run(
                () -> {
                    SharedPreferences p = prefs(app);
                    SharedPreferences.Editor e = p.edit();
                    rollDay(p, e, wall);
                    if (k == Kind.OCIO) {
                        e.putLong("ocioMs", p.getLong("ocioMs", 0) + ms);
                        e.putLong("prodRunMs", 0)
                                .putBoolean("cheer20", false)
                                .putBoolean("pause50", false);
                        // 6.4: ocio medido con el ocio ya bloqueado = minutos_extra (fugas del
                        // bloqueo).
                        // Cuidado: solo en ENFOQUE. En NORMAL pasarse no es una fuga (no hay
                        // bloqueo) y en
                        // CUIDADO el ocio no se cuenta.
                        if (CareStore.mode(app) == es.foco.next.core.care.CareMode.Modo.ENFOQUE
                                && Repository.get(app).locked()) {
                            e.putLong("extraMs", p.getLong("extraMs", 0) + ms);
                            InspectorLog.event(
                                    app,
                                    InspectorLog.MINUTOS_EXTRA,
                                    pkg,
                                    wall,
                                    ms,
                                    0,
                                    ms / 60_000,
                                    "ocio_sin_saldo",
                                    null);
                            e.apply();
                            CompanionNotifier.trigger(app, Companion.Trigger.LEISURE_NO_BALANCE);
                        }
                    } else {
                        e.putLong("investedMs", p.getLong("investedMs", 0) + ms);
                        long lastProd = p.getLong("prodLastAt", -1);
                        long run =
                                lastProd >= 0 && wall - lastProd <= 3 * 60_000L
                                        ? p.getLong("prodRunMs", 0)
                                        : 0;
                        run += ms;
                        e.putLong("prodRunMs", run).putLong("prodLastAt", wall);
                        if (run >= Companion.PRODUCTIVE_CHEER_MS
                                && !p.getBoolean("cheer20", false)) {
                            e.putBoolean("cheer20", true).apply();
                            CompanionNotifier.trigger(
                                    app, Companion.Trigger.PRODUCTIVE_20, run / 60_000);
                        }
                        if (run >= Companion.PRODUCTIVE_PAUSE_MS
                                && !p.getBoolean("pause50", false)) {
                            e.putBoolean("pause50", true).apply();
                            CompanionNotifier.trigger(
                                    app, Companion.Trigger.PRODUCTIVE_50, run / 60_000);
                        }
                    }
                    e.apply();
                    addFact(app, new Fact(wall, k, ms));
                });
    }

    /**
     * Modo NORMAL: pasó de los 5 min y no se bloquea. Recordatorio con propuesta; los límites (cada
     * 5 min como mínimo, 3 por hora, nada en llamadas, No molestar ni de noche) los pone Companion.
     * Lo llama la Accesibilidad en cada revisión (como mucho una por minuto).
     */
    private static long overLimitAt = -1;

    public static synchronized void overLimit(Context c, String pkg) {
        if (c == null) return;
        // La Accesibilidad evalúa con cada evento: como mucho una consulta cada 30 s.
        long now = android.os.SystemClock.elapsedRealtime();
        if (overLimitAt >= 0 && now - overLimitAt < 30_000) return;
        overLimitAt = now;
        Context app = c.getApplicationContext();
        run(() -> CompanionNotifier.trigger(app, Companion.Trigger.LEISURE_OVER, -1, pkg));
    }

    private static long careLastElapsed = -1;

    /**
     * Modo CUIDADO: rato en ocio (sin contarlo para ningún límite). Como mucho una sugerencia suave
     * cada 2 horas y solo tras 30 min de ocio desde la anterior.
     */
    public static synchronized void careLeisure(Context c, String pkg) {
        if (c == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        long delta =
                careLastElapsed >= 0 && now - careLastElapsed <= 65_000 ? now - careLastElapsed : 0;
        careLastElapsed = now;
        if (delta <= 0) return;
        Context app = c.getApplicationContext();
        run(
                () -> {
                    if (CareStore.careSuggestionDue(app, delta))
                        CompanionNotifier.trigger(app, Companion.Trigger.CARE_SUGGESTION, -1, pkg);
                });
    }

    /** InspectorLog.event: retos y logros. */
    public static void onEvent(
            Context c, String type, String pkg, long wall, long value, String onceKey) {
        if (c == null) return;
        boolean reto = InspectorLog.RETO.equals(type) && value > 0;
        boolean logro = InspectorLog.LOGRO.equals(type);
        if (!reto && !logro) return;
        Context app = c.getApplicationContext();
        run(
                () -> {
                    SharedPreferences p = prefs(app);
                    // La cola de Lab puede leerse dos veces: el mismo hecho solo cuenta una.
                    if (onceKey != null && !onceKey.isEmpty()) {
                        String seen = p.getString("seenKeys", "");
                        if (("\n" + seen + "\n").contains("\n" + onceKey + "\n")) return;
                        String next = seen.isEmpty() ? onceKey : seen + "\n" + onceKey;
                        if (next.length() > 6000)
                            next = next.substring(next.indexOf('\n', next.length() - 4000) + 1);
                        p.edit().putString("seenKeys", next).apply();
                    }
                    SharedPreferences.Editor e = p.edit();
                    rollDay(p, e, wall);
                    if (reto) e.putInt("retos", p.getInt("retos", 0) + 1);
                    e.apply();
                    if (reto) addFact(app, new Fact(wall, Kind.RETO, 0));
                    CompanionNotifier.trigger(
                            app,
                            logro ? Companion.Trigger.LEVEL_UP : Companion.Trigger.RETO_PASSED,
                            -1,
                            pkg);
                    if (!reto) recompute(app);
                });
    }

    /** BlockLog.record: intentos y bloqueos. Fin de los 5 min = propuesta. */
    public static void onBlock(Context c, BlockEvent.Type type) {
        if (c == null || type == null) return;
        Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        run(
                () -> {
                    if (type == BlockEvent.Type.BUDGET_LOCK)
                        CompanionNotifier.trigger(app, Companion.Trigger.LEISURE_ENDED);
                    if (type.isAttempt() || type.isBlock())
                        addFact(app, new Fact(now, Kind.ATTEMPT, 0));
                });
    }

    /** ACTION_USER_PRESENT: primer desbloqueo después de las 05:00 (DESPERTAR). */
    public static void unlocked(Context c) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        run(
                () -> {
                    SharedPreferences p = prefs(app);
                    restCheck(p, now);
                    LocalDateTime t = LocalDateTime.now();
                    String today = t.toLocalDate().toString();
                    if (t.getHour() >= HomeState.WAKE_FROM_HOUR
                            && !today.equals(p.getString("unlockDay", ""))) {
                        p.edit().putString("unlockDay", today).putLong("unlockAt", now).apply();
                    }
                    nightSummaryIfDue(app);
                    recompute(app);
                });
    }

    /**
     * Descanso real: pantalla apagada al menos este tiempo. Solo premio visual (Regla 4 intacta).
     */
    public static final long REST_MS = 30 * 60_000L;

    /** ACTION_SCREEN_OFF. */
    public static void screenOff(Context c) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        long now = System.currentTimeMillis();
        run(() -> prefs(app).edit().putLong("offAt", now).apply());
    }

    /** Al desbloquear: ¿hubo descanso real? (lo llama {@link #unlocked}). */
    private static void restCheck(SharedPreferences p, long now) {
        long off = p.getLong("offAt", -1);
        if (off <= 0 || now - off < REST_MS) return;
        String today = LocalDate.now().toString();
        int n = today.equals(p.getString("restDay", "")) ? p.getInt("rests", 0) : 0;
        p.edit()
                .putString("restDay", today)
                .putInt("rests", n + 1)
                .putLong("lastRestMin", (now - off) / 60_000)
                .putLong("offAt", -1)
                .apply();
    }

    /** La rutina de la mañana está completa: el inicio deja el modo Despertar. */
    public static void wakeDone(Context c) {
        Context app = c.getApplicationContext();
        run(
                () -> {
                    prefs(app).edit().putString("wakeDoneDay", LocalDate.now().toString()).apply();
                    recompute(app);
                });
    }

    /** 6.4: ayer hubo más de 15 minutos_extra → hoy no hay bloque gratis. Lectura barata. */
    public static boolean noFreeBlockToday(Context c) {
        try {
            SharedPreferences p = prefs(c);
            String today = LocalDate.now().toString();
            if (today.equals(p.getString("day", ""))) return p.getBoolean("noFreeToday", false);
            // El día aún no rodó: lo que hay guardado es de ayer (o antes).
            String yesterday = LocalDate.now().minusDays(1).toString();
            return yesterday.equals(p.getString("day", ""))
                    && Companion.noFreeBlockTomorrow(p.getLong("extraMs", 0));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Recalcula ya (al mostrar el inicio). */
    public static void refresh(Context c) {
        Context app = c.getApplicationContext();
        run(() -> recompute(app));
    }

    // ---------------- Interno ----------------

    private static void run(Runnable r) {
        try {
            IO.execute(
                    () -> {
                        try {
                            r.run();
                        } catch (Throwable ignored) {
                        }
                    });
        } catch (RuntimeException ignored) {
        }
    }

    /** Cambio de día: cierra el anterior (días cumplidos, minutos_extra) y pone a cero. */
    private static void rollDay(SharedPreferences p, SharedPreferences.Editor e, long wall) {
        String day =
                Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).toLocalDate().toString();
        String old = p.getString("day", "");
        if (day.equals(old)) return;
        boolean noFree = Companion.noFreeBlockTomorrow(p.getLong("extraMs", 0)) && !old.isEmpty();
        e.putString("day", day)
                .putLong("investedMs", 0)
                .putLong("ocioMs", 0)
                .putLong("extraMs", 0)
                .putInt("retos", 0)
                .putBoolean("noFreeToday", noFree)
                .putLong("prodRunMs", 0)
                .putString("summaryDay", p.getString("summaryDay", ""));
    }

    private static void addFact(Context app, Fact f) {
        SharedPreferences p = prefs(app);
        List<Fact> facts = facts(p);
        facts.add(f);
        facts = HomeState.prune(facts, System.currentTimeMillis());
        JSONArray a = new JSONArray();
        for (Fact x : facts) a.put(new JSONArray().put(x.wall).put(x.kind.name()).put(x.ms));
        p.edit().putString("facts", a.toString()).apply();
        recompute(app);
    }

    private static List<Fact> facts(SharedPreferences p) {
        List<Fact> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString("facts", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONArray x = a.getJSONArray(i);
                out.add(new Fact(x.getLong(0), Kind.valueOf(x.getString(1)), x.getLong(2)));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static Snapshot snapshot(SharedPreferences p, long now) {
        try {
            String e = p.getString("estado", null);
            if (e == null) return Snapshot.initial(now);
            String cand = p.getString("cand", "");
            return new Snapshot(
                    Estado.valueOf(e),
                    p.getLong("since", now),
                    cand.isEmpty() ? null : Estado.valueOf(cand),
                    p.getLong("candSince", -1),
                    p.getLong("lastProd", -1),
                    p.getString("motivo", ""));
        } catch (RuntimeException ex) {
            return Snapshot.initial(now);
        }
    }

    static void recompute(Context app) {
        SharedPreferences p = prefs(app);
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = p.edit();
        rollDay(p, e, now);
        e.apply();
        String today = LocalDate.now().toString();
        long unlockAt = today.equals(p.getString("unlockDay", "")) ? p.getLong("unlockAt", -1) : -1;
        boolean wakeDone = today.equals(p.getString("wakeDoneDay", ""));
        Snapshot prev = snapshot(p, now);
        Snapshot s =
                HomeState.step(
                        prev,
                        facts(p),
                        now,
                        ZoneId.systemDefault(),
                        AppKinds.autoTime(app),
                        unlockAt,
                        wakeDone);
        // Días cumplidos (racha_dias): total, nunca baja.
        int total = p.getInt("daysTotal", 0);
        int after =
                HomeState.countDay(
                        total,
                        p.getString("daysLast", ""),
                        LocalDate.now(),
                        HomeState.dayDone(p.getLong("investedMs", 0), p.getInt("retos", 0)));
        SharedPreferences.Editor w =
                p.edit()
                        .putString("estado", s.estado.name())
                        .putLong("since", s.since)
                        .putString("cand", s.candidate == null ? "" : s.candidate.name())
                        .putLong("candSince", s.candidateSince)
                        .putLong("lastProd", s.lastProductiveAt)
                        .putString("motivo", s.motivo);
        if (after != total) w.putInt("daysTotal", after).putString("daysLast", today);
        w.apply();
        if (HomeState.milestone(total, after))
            CompanionNotifier.trigger(app, Companion.Trigger.DAYS_MILESTONE, after);
        Repository r = Repository.get(app);
        // v27: el próximo paso es la siguiente tarea de Foco Core (sus avisos ya van a su hora).
        String step = RoutineStore.nextTaskLabel(app);
        EstadoActual ea =
                new EstadoActual(
                        s.estado,
                        step == null ? "" : step,
                        p.getLong("investedMs", 0) / 60_000,
                        p.getInt("daysTotal", 0),
                        s.since,
                        s.motivo,
                        p.getLong("ocioMs", 0) / 60_000,
                        p.getLong("extraMs", 0) / 60_000,
                        noFreeBlockToday(app));
        ea.retosHoy = p.getInt("retos", 0);
        ea.descansosHoy = today.equals(p.getString("restDay", "")) ? p.getInt("rests", 0) : 0;
        ea.ultimoDescansoMin = p.getLong("lastRestMin", 0);
        last = ea;
        try {
            JSONObject j =
                    new JSONObject()
                            .put("estado", ea.estado.name())
                            .put("proximo_paso", ea.proximoPaso)
                            .put("minutos_invertidos_hoy", ea.minutosInvertidosHoy)
                            .put("racha_dias", ea.rachaDias)
                            .put("desde", ea.desde)
                            .put("motivo", ea.motivo);
            p.edit().putString("estado_actual", j.toString()).apply();
        } catch (Exception ignored) {
        }
        MAIN.post(
                () -> {
                    for (Listener l : listeners) {
                        try {
                            l.changed(ea);
                        } catch (RuntimeException ignored) {
                        }
                    }
                });
    }

    /** Resumen de la noche: el primer evento o desbloqueo después de las 21:00, una vez al día. */
    static void nightSummaryIfDue(Context app) {
        LocalDateTime t = LocalDateTime.now();
        if (t.getHour() < AbsoluteRules.nightStart()) return;
        SharedPreferences p = prefs(app);
        String today = t.toLocalDate().toString();
        if (today.equals(p.getString("summaryDay", ""))) return;
        p.edit().putString("summaryDay", today).apply();
        CompanionNotifier.trigger(
                app, Companion.Trigger.NIGHT_SUMMARY, p.getLong("investedMs", 0) / 60_000);
    }

    /** Minutos productivos seguidos ahora mismo (para que el búho proponga levantarse). */
    public static long productiveRunMin(Context c) {
        SharedPreferences p = prefs(c);
        long last = p.getLong("prodLastAt", -1);
        if (last < 0 || System.currentTimeMillis() - last > 3 * 60_000L) return 0;
        return p.getLong("prodRunMs", 0) / 60_000;
    }

    /** Minutos de ocio en la ventana de 30 min (para el búho). */
    public static long ocioWindowMin(Context c) {
        return HomeState.window(facts(prefs(c)), System.currentTimeMillis()).ocioMs / 60_000;
    }

    /** Datos para el resumen de la noche. */
    public static long extraTodayMin(Context c) {
        return prefs(c).getLong("extraMs", 0) / 60_000;
    }

    public static boolean tomorrowWithoutFreeBlock(Context c) {
        return Companion.noFreeBlockTomorrow(prefs(c).getLong("extraMs", 0));
    }
}
