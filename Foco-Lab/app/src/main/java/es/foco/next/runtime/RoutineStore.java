package es.foco.next.runtime;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.care.Routine;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rutinas obligatorias (mañana y noche): tareas, lo marcado hoy, cuándo empezó y el aviso fijo.
 * Activarlas es endurecer (al momento); desactivarlas afloja, así que vale desde mañana.
 */
public final class RoutineStore {
    private RoutineStore() {}

    private static final String PREFS = "foco-rutina", CHANNEL = "foco-rutina";
    private static final int ID = 7420;
    public static final String EXTRA_ROUTINE = "es.foco.next.extra.ROUTINE";
    private static volatile Runnable listener;
    private static String shownKey = "";

    private static SharedPreferences prefs(Context c) {
        CareStore.install(c); // v32: horario de la noche siempre desde preferencias
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void listen(Runnable r) {
        listener = r;
    }

    private static void changed() {
        Runnable r = listener;
        if (r != null) r.run();
    }

    // ---------------- Activar / desactivar ----------------

    public static boolean enabled(Context c) {
        SharedPreferences p = prefs(c);
        String off = p.getString("offDesde", "");
        if (!off.isEmpty() && LocalDate.now().toString().compareTo(off) >= 0) {
            p.edit().putBoolean("on", false).remove("offDesde").apply();
            return false;
        }
        return p.getBoolean("on", true);
    }

    /** "" si no hay nada pendiente; si no, el día desde el que se apagan. */
    public static String offPending(Context c) {
        return prefs(c).getString("offDesde", "");
    }

    public static void setEnabled(Context c, boolean on) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (on) e.putBoolean("on", true).remove("offDesde");
        else e.putString("offDesde", LocalDate.now().plusDays(1).toString());
        e.apply();
        changed();
    }

    // ---------------- Tareas ----------------

    /** Lista de hoy: la de la fecha, la del día de la semana o la general. */
    public static String text(Context c, Routine.Kind k) {
        return textFor(c, k, today());
    }

    public static String textFor(Context c, Routine.Kind k, LocalDate date) {
        SharedPreferences p = prefs(c);
        return Routine.resolve(k, date, key -> p.getString(key, null));
    }

    /** Guardado tal cual en una clave (null o vacío = quitar y volver a la lista de siempre). */
    public static void setKey(Context c, String key, String text) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (text == null || Routine.parse(text).isEmpty()) e.remove(key);
        else e.putString(key, Routine.encode(Routine.parse(text)));
        e.apply();
        changed();
        scheduleNext(c); // las horas límite pueden haber cambiado
    }

    /** Lo guardado en esa clave, o null si no hay nada propio. */
    public static String stored(Context c, String key) {
        return prefs(c).getString(key, null);
    }

    /**
     * ¿Cambiar esta clave toca la rutina de hoy que está en marcha con tareas pendientes? Entonces
     * no se edita (quitar tareas sería un atajo para abrir el ocio).
     */
    public static boolean locked(Context c, Routine.Kind k, String key) {
        if (current(c) != k) return false;
        if (Routine.remainingCount(tasks(c, k), done(c, k)) == 0) return false;
        LocalDate today = today();
        SharedPreferences p = prefs(c);
        String applies =
                p.contains(Routine.dateKey(k, today))
                        ? Routine.dateKey(k, today)
                        : p.contains(Routine.weekdayKey(k, today.getDayOfWeek()))
                                ? Routine.weekdayKey(k, today.getDayOfWeek())
                                : Routine.generalKey(k);
        // Crear una lista propia de hoy también cambia la de hoy.
        return key.equals(applies)
                || key.equals(Routine.dateKey(k, today))
                || key.equals(Routine.weekdayKey(k, today.getDayOfWeek()));
    }

    // ---------------- Planificar la semana (domingo) ----------------

    public static boolean planDue(Context c) {
        return enabled(c)
                && Routine.planDue(LocalDateTime.now(), prefs(c).getString("planDomingo", ""));
    }

    public static void planSeen(Context c) {
        prefs(c).edit().putString("planDomingo", LocalDate.now().toString()).apply();
        changed();
    }

    /**
     * Las tareas de hoy de esa rutina. v32 (R1): exactamente la misma lista que Foco Core ({@link
     * #blockTasks}, con la copia fijada de hoy), para que la puerta del ocio, el aviso fijo y el
     * fin de la mañana usen los mismos índices que lo que ves.
     */
    public static List<Routine.Task> tasks(Context c, Routine.Kind k) {
        return blockTasks(c, k.name(), today());
    }

    /** Editar: añadir tareas endurece; quitar no afecta a la rutina de hoy si ya está en marcha. */
    public static void setText(Context c, Routine.Kind k, String text) {
        setKey(c, Routine.generalKey(k), text);
    }

    // ---------------- Hoy ----------------

    /** Rutina de ahora (dentro de su ventana y activadas), o null. */
    public static Routine.Kind current(Context c) {
        if (!enabled(c)) return null;
        return Routine.window(LocalDateTime.now(), AbsoluteRules.nightStart());
    }

    public static Set<Integer> done(Context c, Routine.Kind k) {
        Set<Integer> out = new HashSet<>();
        String key = Routine.dayKey(k, LocalDateTime.now());
        for (String s : prefs(c).getStringSet("hecho_" + key, new HashSet<>()))
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        return out;
    }

    public static long started(Context c, Routine.Kind k) {
        return prefs(c).getLong("inicio_" + Routine.dayKey(k, LocalDateTime.now()), 0);
    }

    /** Marca el comienzo la primera vez que se ve (el reloj contra atajos cuenta desde aquí). */
    public static void touch(Context c, Routine.Kind k) {
        String key = "inicio_" + Routine.dayKey(k, LocalDateTime.now());
        SharedPreferences p = prefs(c);
        if (p.getLong(key, 0) == 0) p.edit().putLong(key, System.currentTimeMillis()).apply();
    }

    public static void check(Context c, Routine.Kind k, int index, boolean on) {
        touch(c, k);
        String key = "hecho_" + Routine.dayKey(k, LocalDateTime.now());
        Set<String> s = new HashSet<>(prefs(c).getStringSet(key, new HashSet<>()));
        if (on) s.add(String.valueOf(index));
        else s.remove(String.valueOf(index));
        prefs(c).edit().putStringSet(key, s).apply();
        if (on && Routine.remainingCount(tasks(c, k), done(c, k)) == 0) {
            es.foco.next.log.InspectorLog.event(
                    c,
                    "rutina",
                    "",
                    1,
                    "rutina_" + k.name().toLowerCase(),
                    "rutina|" + Routine.dayKey(k, LocalDateTime.now()));
            // La mañana termina con la rutina, no con un ritual aparte (auditoría v25).
            if (k == Routine.Kind.MORNING) HomeHub.wakeDone(c);
        }
        changed();
        sync(c);
        if (on) RewardStore.onProgress(c);
    }

    /** Texto de la pausa si la rutina de ahora cierra el ocio; null si no. */
    public static String gate(Context c) {
        String pen = penaltyText(c);
        if (pen != null) return pen;
        Routine.Kind k = current(c);
        if (k == null) return null;
        List<Routine.Task> t = tasks(c, k);
        Set<Integer> d = done(c, k);
        long now = System.currentTimeMillis();
        touch(c, k);
        long st = started(c, k);
        return Routine.blocks(true, t, d, st, now) ? Routine.gateText(k, t, d, st, now) : null;
    }

    // ---------------- Aviso fijo ----------------

    private static boolean visible(NotificationManager nm) {
        try {
            for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications())
                if (n.getId() == ID) return true;
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    /**
     * Notificación fija mientras falte la rutina de ahora (no se puede deslizar en la mayoría de
     * versiones; en Android 14+ el sistema permite apartarla, pero vuelve en la siguiente
     * comprobación). Se quita sola al terminar.
     */
    public static void sync(Context c) {
        Context app = c.getApplicationContext();
        NotificationManager nm = app.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Routine.Kind k = current(app);
        List<Routine.Task> t = k == null ? null : tasks(app, k);
        Set<Integer> d = k == null ? null : done(app, k);
        int left = t == null ? 0 : Routine.remainingCount(t, d);
        String key = k == null || left == 0 ? "" : k.name() + left;
        // Igual que lo mostrado y sigue visible: nada que hacer. Si se apartó, vuelve.
        if (key.equals(shownKey) && (key.isEmpty() || visible(nm))) return;
        shownKey = key;
        if (key.isEmpty()) {
            nm.cancel(ID);
            return;
        }
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        try {
            NotificationChannel ch =
                    new NotificationChannel(CHANNEL, "Rutinas", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            Intent open =
                    new Intent()
                            .setClassName(app, "es.foco.next.ui.HomeActivity")
                            .putExtra(EXTRA_ROUTINE, true)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tap =
                    PendingIntent.getActivity(
                            app,
                            ID,
                            open,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            String text =
                    (left == 1 ? "Falta 1" : "Faltan " + left)
                            + " · ~"
                            + Routine.remainingMinutes(t, d)
                            + " min. El ocio espera.";
            nm.notify(
                    ID,
                    new Notification.Builder(app, CHANNEL)
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setContentTitle("🦉 Rutina de la " + k.word)
                            .setContentText(text)
                            .setContentIntent(tap)
                            .setOngoing(true)
                            .setOnlyAlertOnce(true)
                            .setCategory(Notification.CATEGORY_REMINDER)
                            .build());
        } catch (RuntimeException ignored) {
        }
    }

    // =====================================================================================
    // Foco Core (2+2): eventos, tareas vencidas, penalización, hora libre, cronómetros, fotos.
    // =====================================================================================

    public static final String MORNING = "MORNING", NIGHT = "NIGHT", EVENT = "EVENT";
    public static final String[] BLOCKS = {MORNING, NIGHT, EVENT};
    private static final int ID_CORE = 7421;
    private static long lastSweep;

    /** Día de Foco Core (empieza a las 04:00). */
    public static LocalDate today() {
        return Routine.logicalDay(LocalDateTime.now());
    }

    private static String blockKey(String block, LocalDate d) {
        return block + ":" + d;
    }

    public static String eventsKey(LocalDate d) {
        return "eventos_" + d;
    }

    public static String eventsText(Context c, LocalDate d) {
        return prefs(c).getString(eventsKey(d), "");
    }

    /** Tareas de un bloque en una fecha. Los días pasados usan la copia guardada ese día. */
    public static List<Routine.Task> blockTasks(Context c, String block, LocalDate d) {
        // v30: si hoy se quitó una tarea de su lista, hoy se sigue viendo (quitar vale desde
        // mañana).
        if (d.equals(today())) {
            String fixed = prefs(c).getString(fixedKey(block, d), null);
            if (fixed != null) return Routine.parse(fixed);
        }
        String snap = prefs(c).getString("plan_" + blockKey(block, d), null);
        if (snap != null && !d.equals(today())) return Routine.parse(snap);
        if (EVENT.equals(block)) return Routine.parse(eventsText(c, d));
        return Routine.parse(textFor(c, Routine.Kind.valueOf(block), d));
    }

    private static Set<Integer> intSet(Context c, String key) {
        Set<Integer> out = new HashSet<>();
        for (String s : prefs(c).getStringSet(key, new HashSet<>()))
            try {
                out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        return out;
    }

    private static void putInt(Context c, String key, int i, boolean on) {
        Set<String> s = new HashSet<>(prefs(c).getStringSet(key, new HashSet<>()));
        if (on) s.add(String.valueOf(i));
        else s.remove(String.valueOf(i));
        prefs(c).edit().putStringSet(key, s).apply();
    }

    public static Set<Integer> blockDone(Context c, String block, LocalDate d) {
        return intSet(c, "hecho_" + blockKey(block, d));
    }

    public static Set<Integer> blockMissed(Context c, String block, LocalDate d) {
        return intSet(c, "fallo_" + blockKey(block, d));
    }

    private static void snapshot(Context c, String block) {
        LocalDate d = today();
        prefs(c).edit()
                .putString("plan_" + blockKey(block, d), Routine.encode(blockTasks(c, block, d)))
                .apply();
    }

    /** Marcar o desmarcar una tarea de hoy en cualquier bloque. */
    public static void checkBlock(Context c, String block, int i, boolean on) {
        snapshot(c, block);
        if (EVENT.equals(block)) {
            putInt(c, "hecho_" + blockKey(EVENT, today()), i, on);
            changed();
        } else check(c, Routine.Kind.valueOf(block), i, on);
        if (on && running(c, block, i)) toggleTimer(c, block, i);
        if (on) RewardStore.onProgress(c); // v31: día completo → cupón, racha y desbloqueos
        // v36: si ya no queda nada urgente, el aviso insistente se calla al momento.
        if (on) {
            nag(c, System.currentTimeMillis());
            scheduleNext(c);
        }
    }

    /**
     * v27: el "próximo paso" de Foco es la siguiente tarea de Foco Core (la que vence antes; sin
     * hora, la primera del primer bloque con pendientes). "" si no queda ninguna.
     */
    public static String nextTaskLabel(Context c) {
        try {
            LocalDate d = today();
            String label = "";
            int best = Integer.MAX_VALUE;
            for (String b : BLOCKS) {
                List<Routine.Task> t = blockTasks(c, b, d);
                int i = es.foco.next.core.care.FocoCore.next(t, blockDone(c, b, d));
                if (i < 0) continue;
                int w = t.get(i).window;
                int key = w >= 0 ? w : 10_000 + java.util.Arrays.asList(BLOCKS).indexOf(b);
                if (key < best) {
                    best = key;
                    label = t.get(i).label;
                }
            }
            return label;
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---------------- v30: editar tareas desde la hoja (sin escribir formatos) ----------------

    public static final int WHEN_TODAY = 0, WHEN_TOMORROW = 1, WHEN_ALWAYS = 2, WHEN_DAYS = 3;

    private static String fixedKey(String block, LocalDate d) {
        return "fijo_" + blockKey(block, d);
    }

    /** Clave de la lista que vale ese día para un bloque. */
    public static String sourceKey(Context c, String block, LocalDate d) {
        if (EVENT.equals(block)) return eventsKey(d);
        Routine.Kind k = Routine.Kind.valueOf(block);
        SharedPreferences p = prefs(c);
        if (p.contains(Routine.dateKey(k, d))) return Routine.dateKey(k, d);
        if (p.contains(Routine.weekdayKey(k, d.getDayOfWeek())))
            return Routine.weekdayKey(k, d.getDayOfWeek());
        return Routine.generalKey(k);
    }

    /**
     * Lo que hay en la clave; una lista por día sin contenido propio empieza desde la de siempre.
     */
    public static String keyText(Context c, String key, String block) {
        String v = prefs(c).getString(key, null);
        if (v != null) return v;
        if (EVENT.equals(block)) return "";
        Routine.Kind k = Routine.Kind.valueOf(block);
        String g = prefs(c).getString(Routine.generalKey(k), null);
        if (g != null) return g;
        return k == Routine.Kind.MORNING ? Routine.DEFAULT_MORNING : Routine.DEFAULT_NIGHT;
    }

    private static void putKey(Context c, String key, String text) {
        // Vacío se guarda vacío (no vuelve a la lista por defecto); los eventos vacíos se borran.
        String enc = Routine.encode(Routine.parse(text));
        if (key.startsWith("eventos_") && enc.isEmpty()) prefs(c).edit().remove(key).apply();
        else prefs(c).edit().putString(key, enc).apply();
    }

    private static void afterEdit(Context c) {
        changed();
        scheduleNext(c);
    }

    /** Añade la tarea a una clave y, si esa lista vale hoy y hoy está fijada, también a hoy. */
    public static void addToKey(Context c, String key, String block, Routine.Task t) {
        putKey(c, key, Routine.withAdded(keyText(c, key, block), t));
        LocalDate d = today();
        String fixed = prefs(c).getString(fixedKey(block, d), null);
        if (fixed != null && key.equals(sourceKey(c, block, d)))
            prefs(c).edit().putString(fixedKey(block, d), Routine.withAdded(fixed, t)).apply();
        afterEdit(c);
    }

    /**
     * Nueva tarea. Hoy / Mañana: evento de esa fecha. Siempre: la lista de siempre de la franja (y
     * las listas propias de días y fechas que ya existan, para que también salga en ellas). Días:
     * la lista de cada día de la semana elegido.
     */
    public static void addTask(
            Context c, int when, Routine.Kind kind, Set<java.time.DayOfWeek> days, Routine.Task t) {
        LocalDate d = today();
        if (when == WHEN_TODAY || when == WHEN_TOMORROW) {
            addToKey(c, eventsKey(when == WHEN_TODAY ? d : d.plusDays(1)), EVENT, t);
            return;
        }
        String block = kind.name();
        if (when == WHEN_ALWAYS) {
            addToKey(c, Routine.generalKey(kind), block, t);
            SharedPreferences p = prefs(c);
            for (java.time.DayOfWeek w : java.time.DayOfWeek.values())
                if (p.contains(Routine.weekdayKey(kind, w)))
                    addToKey(c, Routine.weekdayKey(kind, w), block, t);
            for (int i = 0; i < 8; i++)
                if (p.contains(Routine.dateKey(kind, d.plusDays(i))))
                    addToKey(c, Routine.dateKey(kind, d.plusDays(i)), block, t);
            return;
        }
        if (days != null)
            for (java.time.DayOfWeek w : days) addToKey(c, Routine.weekdayKey(kind, w), block, t);
    }

    /** Cambia la tarea {@code i} de una clave (y en la copia fijada de hoy, por nombre). */
    public static void replaceInKey(Context c, String key, String block, int i, Routine.Task t) {
        String old = keyText(c, key, block);
        List<Routine.Task> l = Routine.parse(old);
        if (i < 0 || i >= l.size()) return;
        String label = l.get(i).label;
        putKey(c, key, Routine.withReplaced(old, i, t));
        LocalDate d = today();
        String fixed = prefs(c).getString(fixedKey(block, d), null);
        if (fixed != null) {
            int j = Routine.indexOf(fixed, label);
            if (j >= 0)
                prefs(c).edit()
                        .putString(fixedKey(block, d), Routine.withReplaced(fixed, j, t))
                        .apply();
        }
        afterEdit(c);
    }

    /**
     * Quita la tarea {@code i} de una clave. Si esa lista vale hoy, hoy se fija antes tal como
     * está: quitar vale desde mañana (quitar no puede ser un atajo para abrir el ocio).
     */
    public static void removeFromKey(Context c, String key, String block, int i) {
        LocalDate d = today();
        if (key.equals(sourceKey(c, block, d)) && !prefs(c).contains(fixedKey(block, d)))
            prefs(c).edit()
                    .putString(fixedKey(block, d), Routine.encode(blockTasks(c, block, d)))
                    .apply();
        putKey(c, key, Routine.withRemoved(keyText(c, key, block), i));
        afterEdit(c);
    }

    /** Editar desde Hoy: la tarea {@code i} de hoy en su lista de origen. */
    public static void replaceToday(Context c, String block, int i, Routine.Task t) {
        LocalDate d = today();
        String fixed = prefs(c).getString(fixedKey(block, d), null);
        String key = sourceKey(c, block, d);
        if (fixed != null) {
            List<Routine.Task> l = Routine.parse(fixed);
            if (i < 0 || i >= l.size()) return;
            int j = Routine.indexOf(keyText(c, key, block), l.get(i).label);
            prefs(c).edit()
                    .putString(fixedKey(block, d), Routine.withReplaced(fixed, i, t))
                    .apply();
            if (j >= 0) putKey(c, key, Routine.withReplaced(keyText(c, key, block), j, t));
            afterEdit(c);
        } else replaceInKey(c, key, block, i, t);
    }

    /** Quitar desde Hoy: sale de su lista desde mañana; hoy sigue. */
    public static void removeToday(Context c, String block, int i) {
        LocalDate d = today();
        List<Routine.Task> l = blockTasks(c, block, d);
        if (i < 0 || i >= l.size()) return;
        String key = sourceKey(c, block, d);
        int j = Routine.indexOf(keyText(c, key, block), l.get(i).label);
        if (j >= 0) removeFromKey(c, key, block, j);
    }

    /** Las listas propias guardadas: {clave, título} para la pestaña Semana. */
    public static java.util.List<String[]> ownLists(Context c) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        SharedPreferences p = prefs(c);
        for (Routine.Kind k : Routine.Kind.values()) {
            String word = k == Routine.Kind.MORNING ? "Mañana" : "Tarde y noche";
            out.add(new String[] {Routine.generalKey(k), k.name(), word + " · todos los días"});
            for (java.time.DayOfWeek w : java.time.DayOfWeek.values())
                if (p.contains(Routine.weekdayKey(k, w)))
                    out.add(
                            new String[] {
                                Routine.weekdayKey(k, w),
                                k.name(),
                                word + " · cada " + Routine.dayName(w)
                            });
        }
        LocalDate d = today();
        for (int i = 0; i < 8; i++)
            if (p.contains(eventsKey(d.plusDays(i))))
                out.add(
                        new String[] {
                            eventsKey(d.plusDays(i)),
                            EVENT,
                            i == 0
                                    ? "Hoy"
                                    : i == 1
                                            ? "Mañana"
                                            : Routine.dayName(d.plusDays(i).getDayOfWeek())
                                                    + " "
                                                    + d.plusDays(i).getDayOfMonth()
                        });
        return out;
    }

    /** {total, hechas} de hoy, sumando los tres bloques. */
    public static int[] totals(Context c, LocalDate d) {
        int total = 0, done = 0;
        for (String b : BLOCKS) {
            List<Routine.Task> t = blockTasks(c, b, d);
            Set<Integer> h = blockDone(c, b, d);
            total += t.size();
            for (int i = 0; i < t.size(); i++) if (h.contains(i)) done++;
        }
        return new int[] {total, done};
    }

    public static int remainingMinutes(Context c, LocalDate d) {
        int n = 0;
        for (String b : BLOCKS)
            n += Routine.remainingMinutes(blockTasks(c, b, d), blockDone(c, b, d));
        return n;
    }

    /**
     * v32 (R2): ¿el día tiene todas sus tareas hechas (y plan)? Independiente de la pausa: un día
     * terminado durante una pausa por tarea vencida está completo (cupón diario y racha), aunque la
     * pausa siga anulando la semana perfecta.
     */
    public static boolean dayComplete(Context c, LocalDate d) {
        int[] t = totals(c, d);
        return t[0] > 0
                && t[1] >= t[0]
                && planned(c, d) >= es.foco.next.core.care.FocoCore.MIN_TASKS;
    }

    public static es.foco.next.core.care.FocoCore.State state(Context c) {
        LocalDate d = today();
        int[] t = totals(c, d);
        return es.foco.next.core.care.FocoCore.state(
                t[0], t[1], planned(c, d), activePenaltyUntil(c), System.currentTimeMillis());
    }

    /**
     * Tareas propias de un día: eventos y listas de esa fecha, de ese día de la semana o "Siempre".
     * v32 (R4): la lista "Siempre" cuenta si la guardaste tú (existe su clave; editar la
     * predeterminada es adoptarla entera). La predeterminada que nunca tocaste no cuenta.
     */
    public static int planned(Context c, LocalDate d) {
        SharedPreferences p = prefs(c);
        int n = Routine.parse(eventsText(c, d)).size();
        for (Routine.Kind k : Routine.Kind.values())
            if (p.contains(Routine.dateKey(k, d))
                    || p.contains(Routine.weekdayKey(k, d.getDayOfWeek()))
                    || p.contains(Routine.generalKey(k)))
                n += Routine.parse(textFor(c, k, d)).size();
        return n;
    }

    /** En Cuidado no hay pausa por horas límite (la tarea queda incumplida en el reporte). */
    private static boolean careMode(Context c) {
        return CareStore.mode(c) == es.foco.next.core.care.CareMode.Modo.CUIDADO;
    }

    /** La pausa vigente, o 0 en Cuidado. */
    public static long activePenaltyUntil(Context c) {
        return careMode(c) ? 0 : penaltyUntil(c);
    }

    // ---------------- Penalización por hora límite ----------------

    public static long penaltyUntil(Context c) {
        return prefs(c).getLong("castigoHasta", 0);
    }

    public static int penalties(Context c, LocalDate d) {
        return prefs(c).getInt("castigos_" + d, 0);
    }

    private static String penaltyText(Context c) {
        long left = activePenaltyUntil(c) - System.currentTimeMillis();
        if (left <= 0) return null;
        return "Se pasó la hora de "
                + prefs(c).getString("castigoTarea", "una tarea")
                + ". El ocio espera "
                + Math.max(1, (left + 59_999) / 60_000)
                + " min. Aprovecha para terminarla.";
    }

    /**
     * Revisa las horas límite de hoy (como mucho cada 30 s): aviso al llegar la hora y, pasado el
     * margen de 10 min, marca incumplida y abre 1 h de pausa del ocio (sin acumular, nunca de
     * noche).
     */
    public static void sweep(Context c) {
        sweep(c, false);
    }

    /**
     * {@code force}: desde la alarma (sin el límite de 30 s). Al terminar, programa la siguiente.
     */
    public static void sweep(Context c, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastSweep < 30_000) return;
        lastSweep = now;
        // v31: ánimo de media tarde y la semana perfecta (los dos, una vez; baratos si no toca).
        RewardStore.nudge(c);
        RewardStore.checkWeek(c);
        RewardStore.onProgress(c); // v32 (R2): por si el día se completó durante una pausa
        try {
            sweepNow(c, now);
        } finally {
            scheduleNext(c);
        }
    }

    private static void sweepNow(Context c, long now) {
        if (!enabled(c)) return;
        java.time.LocalTime t = java.time.LocalTime.now();
        LocalDate d = today();
        boolean night = AbsoluteRules.esNocheAbsoluta(LocalDateTime.now());
        for (String b : BLOCKS) {
            List<Routine.Task> tasks = blockTasks(c, b, d);
            Set<Integer> done = blockDone(c, b, d);
            Set<Integer> missed = blockMissed(c, b, d);
            Set<Integer> warned = intSet(c, "avisado_" + blockKey(b, d));
            for (int i = 0; i < tasks.size(); i++) {
                Routine.Task task = tasks.get(i);
                boolean h = done.contains(i);
                int lm = es.foco.next.core.care.FocoCore.logicalMinute(t); // v32 (R9)
                if (es.foco.next.core.care.FocoCore.missedAt(task, h, lm) && !missed.contains(i)) {
                    snapshot(c, b);
                    putInt(c, "fallo_" + blockKey(b, d), i, true);
                    // En Cuidado: se apunta como incumplida, sin pausa.
                    long until =
                            careMode(c)
                                    ? 0
                                    : es.foco.next.core.care.FocoCore.penaltyUntil(
                                            now, penaltyUntil(c), night);
                    if (until > 0) {
                        prefs(c).edit()
                                .putLong("castigoHasta", until)
                                .putString("castigoTarea", task.label)
                                .putInt("castigos_" + d, penalties(c, d) + 1)
                                .apply();
                        notifyCore(
                                c,
                                "Se pasó la hora de " + task.label,
                                "1 h sin ocio. Terminarla sigue contando para hoy.");
                    }
                    // Sin el nombre de la tarea: el registro se puede exportar y las tareas son
                    // tuyas.
                    es.foco.next.log.InspectorLog.event(
                            c,
                            "tarea_incumplida",
                            "",
                            1,
                            b.toLowerCase(),
                            "fallo|" + blockKey(b, d) + "|" + i);
                    changed();
                } else if (es.foco.next.core.care.FocoCore.warnDueAt(task, h, lm)
                        && !warned.contains(i)) {
                    putInt(c, "avisado_" + blockKey(b, d), i, true);
                    notifyCore(
                            c,
                            task.label + " vence a las " + task.windowText(),
                            "Te quedan 10 min de margen.");
                }
            }
        }
        nag(c, now);
    }

    /** v41: para el inicio (el búho apunta): {tarea, «Quedan 12 min (hasta las 9:00)…»} o null. */
    public static String[] urgentNow(Context c) {
        try {
            return enabled(c) ? urgent(c) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * v36: lo más urgente con hora límite cerca, o null. {título, texto, minutos hasta la hora}.
     */
    private static String[] urgent(Context c) {
        LocalDate d = today();
        int lm = es.foco.next.core.care.FocoCore.logicalMinute(java.time.LocalTime.now());
        String[] best = null;
        int bestDeadline = Integer.MAX_VALUE;
        for (String b : BLOCKS) {
            List<Routine.Task> tasks = blockTasks(c, b, d);
            if (tasks.isEmpty()) continue;
            Set<Integer> done = blockDone(c, b, d);
            // Cada tarea con su propia hora.
            for (int i = 0; i < tasks.size(); i++) {
                Routine.Task t = tasks.get(i);
                int dl = es.foco.next.core.care.FocoCore.logicalDeadline(t.window);
                if (es.foco.next.core.care.FocoCore.nagAt(dl, done.contains(i), lm)
                        && dl < bestDeadline) {
                    bestDeadline = dl;
                    best = new String[] {t.label, es.foco.next.core.care.FocoCore.nagText(dl, lm)};
                }
            }
            // La franja entera.
            int pending = 0;
            String first = null;
            for (int i = 0; i < tasks.size(); i++)
                if (!done.contains(i)) {
                    pending++;
                    if (first == null) first = tasks.get(i).label;
                }
            int dl = es.foco.next.core.care.FocoCore.logicalDeadline(blockDeadline(c, b));
            if (es.foco.next.core.care.FocoCore.nagAt(dl, pending == 0, lm) && dl < bestDeadline) {
                bestDeadline = dl;
                String word = MORNING.equals(b) ? "la mañana" : "la tarde y noche";
                best =
                        new String[] {
                            pending == 1 ? first : first + " y " + (pending - 1) + " más",
                            es.foco.next.core.care.FocoCore.nagText(dl, lm)
                                    + " para terminar "
                                    + word
                                    + "."
                        };
            }
        }
        return best;
    }

    private static final String CHANNEL_NAG = "foco-hora-limite";
    private static final int ID_NAG = 7423;

    /**
     * v36: aviso insistente cada 5 min desde 30 min antes de la hora límite hasta que la cumples (o
     * vence el margen). Lo pediste tú: no pasa por el presupuesto del búho y suena también de
     * noche; con No molestar, el sistema lo silencia.
     */
    private static void nag(Context c, long now) {
        try {
            String[] u = urgent(c);
            Context app = c.getApplicationContext();
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (u == null) {
                if (nm != null && prefs(c).getLong("ultimoInsistir", 0) > 0) {
                    nm.cancel(ID_NAG);
                    prefs(c).edit().remove("ultimoInsistir").apply();
                }
                return;
            }
            if (!es.foco.next.core.care.FocoCore.nagAgain(
                    prefs(c).getLong("ultimoInsistir", 0), now)) return;
            if (nm == null
                    || app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) return;
            prefs(c).edit().putLong("ultimoInsistir", now).apply();
            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL_NAG,
                            "Hora límite (cada 5 min)",
                            NotificationManager.IMPORTANCE_HIGH);
            ch.enableVibration(true);
            nm.createNotificationChannel(ch);
            Intent open =
                    new Intent()
                            .setClassName(app, "es.foco.next.ui.HomeActivity")
                            .putExtra(EXTRA_CORE, true)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tap =
                    PendingIntent.getActivity(
                            app,
                            ID_NAG,
                            open,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            nm.notify(
                    ID_NAG,
                    new Notification.Builder(app, CHANNEL_NAG)
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setContentTitle("🦉 " + u[0])
                            .setContentText(u[1])
                            .setStyle(
                                    new Notification.BigTextStyle()
                                            .bigText(u[1] + " Toca para marcarla."))
                            .setCategory(Notification.CATEGORY_REMINDER)
                            .setContentIntent(tap)
                            .setAutoCancel(true)
                            .build());
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * Alarma para la próxima hora límite (o el fin de su margen) de hoy: así el aviso y la pausa
     * llegan a su hora aunque no estés usando el teléfono. Inexacta (±5 min) para no gastar
     * batería.
     */
    public static void scheduleNext(Context c) {
        try {
            Context app = c.getApplicationContext();
            android.app.AlarmManager am = app.getSystemService(android.app.AlarmManager.class);
            if (am == null) return;
            PendingIntent pi =
                    PendingIntent.getBroadcast(
                            app,
                            7422,
                            new Intent(app, OwlWatch.class).setAction(OwlWatch.ACTION_CORE),
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long next = Long.MAX_VALUE;
            if (enabled(c)) {
                LocalDate d = today();
                java.time.ZonedDateTime base = d.atStartOfDay(java.time.ZoneId.systemDefault());
                long now = System.currentTimeMillis();
                for (String b : BLOCKS) {
                    List<Routine.Task> tasks = blockTasks(c, b, d);
                    Set<Integer> done = blockDone(c, b, d);
                    Set<Integer> missed = blockMissed(c, b, d);
                    for (int i = 0; i < tasks.size(); i++) {
                        Routine.Task t = tasks.get(i);
                        if (t.window < 0 || done.contains(i) || missed.contains(i)) continue;
                        long at = base.plusMinutes(t.window).toInstant().toEpochMilli();
                        for (long when :
                                new long[] {at, at + es.foco.next.core.care.FocoCore.GRACE_MS})
                            if (when > now && when < next) next = when;
                    }
                }
            }
            // v36: el aviso insistente. Durante el tramo, cada 5 min (ventana de 1 min); antes, al
            // empezar el tramo (30 min antes de cada hora límite).
            boolean nagging = false;
            if (enabled(c)) {
                long now = System.currentTimeMillis();
                if (urgent(c) != null) {
                    nagging = true;
                    long last = prefs(c).getLong("ultimoInsistir", 0);
                    long at =
                            Math.max(
                                    now + 60_000L,
                                    (last > 0 ? last : now)
                                            + es.foco.next.core.care.FocoCore.NAG_EVERY_MS);
                    if (at < next) next = at;
                } else {
                    LocalDate d = today();
                    java.time.ZonedDateTime base = d.atStartOfDay(java.time.ZoneId.systemDefault());
                    for (String b : BLOCKS) {
                        List<Routine.Task> tasks = blockTasks(c, b, d);
                        if (tasks.isEmpty()) continue;
                        Set<Integer> done = blockDone(c, b, d);
                        java.util.List<Integer> deadlines = new java.util.ArrayList<>();
                        if (done.size() < tasks.size())
                            deadlines.add(
                                    es.foco.next.core.care.FocoCore.logicalDeadline(
                                            blockDeadline(c, b)));
                        for (int i = 0; i < tasks.size(); i++)
                            if (!done.contains(i))
                                deadlines.add(
                                        es.foco.next.core.care.FocoCore.logicalDeadline(
                                                tasks.get(i).window));
                        for (int dl : deadlines) {
                            if (dl < 0) continue;
                            long start =
                                    base.plusMinutes(
                                                    dl
                                                            - es.foco.next.core.care.FocoCore
                                                                    .NAG_LEAD_MIN)
                                            .toInstant()
                                            .toEpochMilli();
                            if (start > now && start < next) next = start;
                        }
                    }
                }
            }
            if (next == Long.MAX_VALUE) am.cancel(pi);
            else
                am.setWindow(
                        android.app.AlarmManager.RTC_WAKEUP,
                        next,
                        nagging ? 60_000L : 5 * 60_000L,
                        pi);
        } catch (RuntimeException ignored) {
        }
    }

    /**
     * ¿Hay datos de Foco Core ese día? (para no pintar de rojo los días anteriores a instalarlo)
     */
    public static boolean hasData(Context c, LocalDate d) {
        if (d.equals(today())) return true;
        SharedPreferences p = prefs(c);
        for (String b : BLOCKS)
            if (p.contains("plan_" + blockKey(b, d))
                    || p.contains("hecho_" + blockKey(b, d))
                    || p.contains("fallo_" + blockKey(b, d))) return true;
        return p.getInt("castigos_" + d, 0) > 0;
    }

    // ---------------- Hora libre al completar el día ----------------

    public static long freeUntil(Context c) {
        return prefs(c).getLong("libreHasta", 0);
    }

    public static boolean freeUsedToday(Context c) {
        return today().toString().equals(prefs(c).getString("libreDia", ""));
    }

    /** Activa 1 h de ocio sin límite de 5 min/h (las reglas fijas y la noche siguen). */
    public static boolean startFree(Context c) {
        if (state(c) != es.foco.next.core.care.FocoCore.State.COMPLETE || freeUsedToday(c))
            return false;
        long until =
                es.foco.next.core.care.FocoCore.freeUntil(
                        LocalDateTime.now(),
                        System.currentTimeMillis(),
                        AbsoluteRules.nightStart());
        if (until <= 0) return false;
        prefs(c).edit()
                .putLong("libreHasta", until)
                .putString("libreDia", today().toString())
                .apply();
        es.foco.next.log.InspectorLog.event(c, "hora_libre", "", 1, "", "libre|" + today());
        changed();
        return true;
    }

    public static boolean freeActive(Context c) {
        return freeUntil(c) > System.currentTimeMillis()
                && !AbsoluteRules.esNocheAbsoluta(LocalDateTime.now());
    }

    // ---------------- Cronómetro por tarea ----------------

    private static String timerKey(String block, int i) {
        return blockKey(block, today()) + ":" + i;
    }

    public static boolean running(Context c, String block, int i) {
        return prefs(c).getLong("crono_" + timerKey(block, i), 0) > 0;
    }

    public static void toggleTimer(Context c, String block, int i) {
        String k = timerKey(block, i);
        SharedPreferences p = prefs(c);
        long start = p.getLong("crono_" + k, 0);
        if (start > 0)
            p.edit()
                    .putLong(
                            "cronoMs_" + k,
                            p.getLong("cronoMs_" + k, 0) + (System.currentTimeMillis() - start))
                    .remove("crono_" + k)
                    .apply();
        else p.edit().putLong("crono_" + k, System.currentTimeMillis()).apply();
        changed();
    }

    public static long timerMs(Context c, String block, int i) {
        String k = timerKey(block, i);
        SharedPreferences p = prefs(c);
        long start = p.getLong("crono_" + k, 0);
        return p.getLong("cronoMs_" + k, 0) + (start > 0 ? System.currentTimeMillis() - start : 0);
    }

    // ---------------- Evidencias (fotos privadas, WebP 480 px al 60 %) ----------------

    public static java.io.File evidenceDir(Context c) {
        java.io.File d = new java.io.File(c.getFilesDir(), "evidencias");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** Comprime la foto tomada a 480 px de ancho en WebP al 60 % (~35 KB) y borra la temporal. */
    public static boolean saveEvidence(Context c, java.io.File tmp, String block, int i) {
        try {
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(tmp.getPath(), o);
            if (o.outWidth <= 0) return false;
            o.inJustDecodeBounds = false;
            o.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, o.outWidth / 480)));
            android.graphics.Bitmap raw =
                    android.graphics.BitmapFactory.decodeFile(tmp.getPath(), o);
            if (raw == null) return false;
            raw = rotate(raw, tmp);
            int w = Math.min(480, raw.getWidth());
            int h = Math.round(raw.getHeight() * (w / (float) raw.getWidth()));
            android.graphics.Bitmap small =
                    android.graphics.Bitmap.createScaledBitmap(raw, w, h, true);
            java.io.File out =
                    new java.io.File(evidenceDir(c), today() + "_" + block + "_" + i + ".webp");
            try (java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
                small.compress(android.graphics.Bitmap.CompressFormat.WEBP_LOSSY, 60, fo);
            }
            if (small != raw) small.recycle();
            raw.recycle();
            prune(c);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    private static android.graphics.Bitmap rotate(android.graphics.Bitmap b, java.io.File f) {
        try {
            int o =
                    new android.media.ExifInterface(f.getPath())
                            .getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1);
            int deg = o == 6 ? 90 : o == 3 ? 180 : o == 8 ? 270 : 0;
            if (deg == 0) return b;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(deg);
            android.graphics.Bitmap r =
                    android.graphics.Bitmap.createBitmap(
                            b, 0, 0, b.getWidth(), b.getHeight(), m, true);
            if (r != b) b.recycle();
            return r;
        } catch (Exception e) {
            return b;
        }
    }

    /** Fotos de un día. */
    public static java.util.List<java.io.File> photos(Context c, LocalDate d) {
        java.util.List<java.io.File> out = new java.util.ArrayList<>();
        java.io.File[] all = evidenceDir(c).listFiles();
        if (all != null) for (java.io.File f : all) if (f.getName().startsWith(d + "_")) out.add(f);
        java.util.Collections.sort(out);
        return out;
    }

    public static boolean hasPhoto(Context c, String block, int i) {
        return hasPhoto(c, block, i, today());
    }

    /** v36: para el informe de un día. */
    public static boolean hasPhoto(Context c, String block, int i, LocalDate d) {
        return new java.io.File(evidenceDir(c), d + "_" + block + "_" + i + ".webp").exists();
    }

    // ---------------- v36: «¿Cómo lo hiciste?» ----------------

    /** Máximo de la nota de una tarea. */
    public static final int NOTE_MAX = 300;

    private static String noteKey(String block, int i, LocalDate d) {
        return "nota_" + blockKey(block, d) + "_" + i;
    }

    private static String lastNoteKey(String label) {
        return "ultimaNota|"
                + (label == null ? "" : label.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Guarda cómo hiciste una tarea de hoy (opcional; vacía la borra). Solo para el informe del
     * teléfono: nunca va al registro exportable. Se recuerda la última por nombre de tarea para
     * proponerla la próxima vez.
     */
    public static void saveNote(Context c, String block, int i, String label, String note) {
        String n = note == null ? "" : note.trim();
        if (n.length() > NOTE_MAX) n = n.substring(0, NOTE_MAX);
        SharedPreferences.Editor e = prefs(c).edit();
        if (n.isEmpty()) e.remove(noteKey(block, i, today()));
        else e.putString(noteKey(block, i, today()), n).putString(lastNoteKey(label), n);
        e.apply();
    }

    public static String note(Context c, String block, int i, LocalDate d) {
        return prefs(c).getString(noteKey(block, i, d), "");
    }

    /** La última nota que escribiste para una tarea con ese nombre ("" si ninguna). */
    public static String lastNote(Context c, String label) {
        return prefs(c).getString(lastNoteKey(label), "");
    }

    // ---------------- v36: hora límite de cada franja ----------------

    /**
     * Hora límite de la franja (minutos del día) o -1 sin hora. Mañana: 9:00; tarde y noche: 23:00
     * (se cambian tocando la franja). Las tareas «solo este día» no tienen.
     */
    public static int blockDeadline(Context c, String block) {
        if (MORNING.equals(block))
            return prefs(c).getInt(
                            "limite_" + MORNING, es.foco.next.core.care.FocoCore.MORNING_DEADLINE);
        if (NIGHT.equals(block))
            return prefs(c).getInt(
                            "limite_" + NIGHT, es.foco.next.core.care.FocoCore.NIGHT_DEADLINE);
        return -1;
    }

    /** {@code minute} -1 = sin hora. */
    public static void setBlockDeadline(Context c, String block, int minute) {
        if (!MORNING.equals(block) && !NIGHT.equals(block)) return;
        prefs(c).edit()
                .putInt("limite_" + block, minute < 0 ? -1 : Math.min(minute, 24 * 60 - 1))
                .apply();
        lastSweep = 0;
        scheduleNext(c);
    }

    /** Solo se guardan 8 semanas de fotos. */
    private static void prune(Context c) {
        String limit = today().minusWeeks(8).toString();
        java.io.File[] all = evidenceDir(c).listFiles();
        if (all == null) return;
        for (java.io.File f : all)
            if (f.getName().length() >= 10 && f.getName().substring(0, 10).compareTo(limit) < 0)
                //noinspection ResultOfMethodCallIgnored
                f.delete();
    }

    // ---------------- Avisos de Foco Core ----------------

    private static void notifyCore(Context c, String title, String text) {
        Context app = c.getApplicationContext();
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        // Como el resto del búho: nada de noche ni con No molestar.
        if (AbsoluteRules.esNocheAbsoluta(LocalDateTime.now())) return;
        try {
            NotificationManager nm = app.getSystemService(NotificationManager.class);
            if (nm == null
                    || nm.getCurrentInterruptionFilter()
                            > NotificationManager.INTERRUPTION_FILTER_ALL) return;
            NotificationChannel ch =
                    new NotificationChannel(CHANNEL, "Rutinas", NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            Intent open =
                    new Intent()
                            .setClassName(app, "es.foco.next.ui.HomeActivity")
                            .putExtra(EXTRA_CORE, true)
                            .addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK
                                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent tap =
                    PendingIntent.getActivity(
                            app,
                            ID_CORE,
                            open,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            nm.notify(
                    ID_CORE,
                    new Notification.Builder(app, CHANNEL)
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setContentTitle("🦉 " + title)
                            .setContentText(text)
                            .setContentIntent(tap)
                            .setAutoCancel(true)
                            .build());
        } catch (RuntimeException ignored) {
        }
    }

    public static final String EXTRA_CORE = "es.foco.next.extra.CORE";
}
