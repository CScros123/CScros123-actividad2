package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.care.CareMode;
import es.foco.next.core.care.Mood;
import es.foco.next.core.care.NightHours;
import es.foco.next.log.InspectorLog;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * De control a cuidado: modo actual (CUIDADO, NORMAL, ENFOQUE), horario de la noche, ánimo del día
 * y preferencias de "lo bueno primero".
 *
 * <p>Se guarda en SharedPreferences y no en DataStore a propósito: el servicio de Accesibilidad lee
 * el modo en cada evaluación, de forma síncrona y sin corrutinas, y el resto de Foco ya guarda así
 * su estado. DataStore sería una dependencia nueva sin ventaja aquí.
 *
 * <p>Todo cambio de modo queda en el log (modo_cambiado: de, a, motivo). El ánimo queda en el log
 * (animo) y solo en el teléfono: el texto exportado no lo incluye salvo que el usuario lo active.
 * Ningún método lanza.
 */
public final class CareStore {
    private CareStore() {}

    private static final String PREFS = "foco-cuidado";

    /** Cambió algo (modo, noche, ánimo): la Accesibilidad y el launcher vuelven a leer. */
    public interface Listener {
        void changed();
    }

    private static volatile Listener listener;

    public static void listen(Listener l) {
        listener = l;
    }

    private static void notifyChanged() {
        Listener l = listener;
        if (l != null)
            try {
                l.changed();
            } catch (RuntimeException ignored) {
            }
        FocusAccessibilityService.checkpoint();
    }

    private static SharedPreferences prefs(Context c) {
        install(c);
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * v32 (R13): AbsoluteRules lee el horario de la noche de estas preferencias en cada consulta,
     * en cualquier proceso y desde cualquier punto de entrada (alarma, receptor, servicio). Barato
     * e idempotente.
     */
    public static void install(Context c) {
        if (c == null || AbsoluteRules.hasNightSource()) return;
        final Context app = c.getApplicationContext();
        AbsoluteRules.setNightSource(
                () -> {
                    NightHours n = NightHours.effective(nightSetting(app), LocalDate.now());
                    return new int[] {n.start, n.end};
                });
    }

    // ---------------- Modo ----------------

    public static CareMode.State state(Context c) {
        try {
            SharedPreferences p = prefs(c);
            return new CareMode.State(
                    parse(p.getString("modo", "NORMAL")),
                    parse(p.getString("previo", "NORMAL")),
                    p.getLong("hasta", -1),
                    p.getLong("cuidadoHasta", -1));
        } catch (RuntimeException e) {
            return CareMode.State.inicial();
        }
    }

    private static CareMode.Modo parse(String s) {
        try {
            return CareMode.Modo.valueOf(s);
        } catch (RuntimeException e) {
            return CareMode.Modo.NORMAL;
        }
    }

    /** Modo que vale ahora. Si un ENFOQUE venció, lo cierra y lo registra (fin_enfoque). */
    public static CareMode.Modo mode(Context c) {
        long now = System.currentTimeMillis();
        CareMode.State s = state(c);
        CareMode.Change back = CareMode.settle(s, now);
        if (back != null) {
            save(c, back);
            return back.a;
        }
        return CareMode.effective(s, now);
    }

    public static long focusMinutesLeft(Context c) {
        return CareMode.focusMinutesLeft(state(c), System.currentTimeMillis());
    }

    public static long careMinutesLeft(Context c) {
        return CareMode.careMinutesLeft(state(c), System.currentTimeMillis());
    }

    private static synchronized void save(Context c, CareMode.Change ch) {
        try {
            SharedPreferences p = prefs(c);
            long now = System.currentTimeMillis();
            SharedPreferences.Editor e = p.edit();
            // Tope diario de Cuidado: al salir de CUIDADO se suma lo usado (hasta su final, si
            // salió solo); al entrar se apunta la hora.
            if (ch.de == CareMode.Modo.CUIDADO && ch.a != CareMode.Modo.CUIDADO) {
                long from = p.getLong("cuidadoDesde", 0);
                long until = p.getLong("cuidadoHasta", -1);
                long end = until > 0 ? Math.min(now, until) : now;
                if (from > 0 && end > from) addCareUsed(p, e, from, end);
                e.putLong("cuidadoDesde", 0);
            }
            if (ch.a == CareMode.Modo.CUIDADO) {
                long from = p.getLong("cuidadoDesde", 0);
                if (ch.de == CareMode.Modo.CUIDADO && from > 0)
                    addCareUsed(p, e, from, now); // ampliar
                e.putLong("cuidadoDesde", now);
            }
            e.putString("modo", ch.next.modo.name())
                    .putString("previo", ch.next.previo.name())
                    .putLong("hasta", ch.next.enfoqueHasta)
                    .putLong("cuidadoHasta", ch.next.cuidadoHasta)
                    .putLong("cambiadoEn", now)
                    .apply();
            InspectorLog.event(c, InspectorLog.MODO_CAMBIADO, "", 0, ch.detail(), null);
        } catch (RuntimeException ignored) {
        }
        notifyChanged();
        // v47: NextDNS sigue al modo (Enfoque bloquea redes por servicio). Solo con clave.
        NextDnsControl.request(c);
    }

    private static void addCareUsed(
            SharedPreferences p, SharedPreferences.Editor e, long from, long end) {
        String day = LocalDate.now().toString();
        long acc = day.equals(p.getString("cuidadoDia", "")) ? p.getLong("cuidadoMs", 0) : 0;
        e.putString("cuidadoDia", day).putLong("cuidadoMs", acc + (end - from));
    }

    /** Tiempo en CUIDADO hoy (lo cerrado más el tramo en curso). */
    public static long careUsedToday(Context c) {
        SharedPreferences p = prefs(c);
        long acc =
                LocalDate.now().toString().equals(p.getString("cuidadoDia", ""))
                        ? p.getLong("cuidadoMs", 0)
                        : 0;
        long from = p.getLong("cuidadoDesde", 0), now = System.currentTimeMillis();
        if (from > 0 && mode(c) == CareMode.Modo.CUIDADO) {
            long until = p.getLong("cuidadoHasta", -1);
            acc += (until > 0 ? Math.min(now, until) : now) - from;
        }
        return Math.max(0, acc);
    }

    /** Lo que queda hoy de Cuidado (ms); sin tope si el ánimo de hoy fue 1 o 2. */
    public static long careBudgetLeft(Context c) {
        return CareMode.careBudgetLeftMs(careUsedToday(c), moodLowForCap(c));
    }

    /** v32 (R8): motivos de la entrada consciente en Cuidado {clave, texto}. */
    public static final String[][] EXIT_MOTIVES = {
        {"cansancio", "Estoy cansado"},
        {"animo_bajo", "Ánimo bajo"},
        {"imprevisto", "Un imprevisto"}
    };

    /**
     * v32 (R8): entrar en Cuidado durante un Enfoque o el Weekend Lock se permite, pero con una
     * acción consciente: el texto que lo explica, o null si no hace falta (ya no hay nada que se
     * abra). No es una evasión; el tiempo queda registrado (ocio_cuidado).
     */
    public static String exitNotice(Context c) {
        CareMode.Modo m = mode(c);
        if (m == CareMode.Modo.CUIDADO) return null;
        if (m == CareMode.Modo.ENFOQUE)
            return "Sales de Enfoque: en Cuidado el ocio queda abierto y todo se registra. ¿Por qué?";
        if (Weekend.active(c))
            return "Estás en el Weekend Lock: en Cuidado el ocio queda abierto y todo se registra. ¿Por qué?";
        return null;
    }

    /**
     * CUIDADO durante {@code minutes} (15, 60 o 180), recortado al tope diario. Devuelve los
     * minutos concedidos, o 0 si hoy ya no queda Cuidado.
     */
    public static long care(Context c, int minutes, String motivo) {
        long now = System.currentTimeMillis();
        CareMode.Change ch =
                CareMode.toCare(state(c), minutes, now, careUsedToday(c), moodLowForCap(c), motivo);
        if (ch == null) return 0;
        save(c, ch);
        return (ch.next.cuidadoHasta - now + 59_999) / 60_000;
    }

    // ---------------- Enfriamiento del modo Normal ----------------

    /**
     * 5 min y 30 min de enfriamiento en Normal. Afloja (más ocio posible por hora), así que se
     * aplica desde el día siguiente a instalar esta versión.
     */
    public static long normalCooldownMs(Context c) {
        try {
            SharedPreferences p = prefs(c);
            String from = p.getString("enfriamientoDesde", "");
            LocalDate today = LocalDate.now();
            if (from.isEmpty()) {
                p.edit().putString("enfriamientoDesde", today.plusDays(1).toString()).apply();
                return 0;
            }
            return today.isBefore(LocalDate.parse(from))
                    ? 0
                    : es.foco.next.core.FocusEngine.NORMAL_COOLDOWN;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** Día desde el que vale el enfriamiento (para mostrarlo), o "". */
    public static String cooldownFrom(Context c) {
        return prefs(c).getString("enfriamientoDesde", "");
    }

    /** ENFOQUE con duración. false si la duración no está disponible ahora. */
    public static boolean focus(Context c, CareMode.Duracion d) {
        CareMode.Change ch =
                CareMode.toFocus(state(c), d, ZonedDateTime.now(), AbsoluteRules.nightStart());
        if (ch == null) return false;
        save(c, ch);
        // v48: tu música de concentración al entrar en Enfoque (si lo activaste).
        if (es.foco.next.runtime.SoundSettings.appleOnFocus(c)) AppleMusic.play(c);
        return true;
    }

    /** CUIDADO (siempre) o NORMAL (no durante un ENFOQUE). false si no se permitió. */
    public static boolean choose(Context c, CareMode.Modo to, String motivo) {
        CareMode.Change ch = CareMode.to(state(c), to, System.currentTimeMillis(), motivo);
        if (ch == null) return false;
        save(c, ch);
        return true;
    }

    // ---------------- Noche ----------------

    public static NightHours.Setting nightSetting(Context c) {
        SharedPreferences p = prefs(c);
        NightHours active = new NightHours(p.getInt("nocheIni", 21), p.getInt("nocheFin", 7));
        if (!NightHours.valid(active.start, active.end)) active = NightHours.DEFAULT;
        String from = p.getString("nochePendDesde", "");
        NightHours pend =
                from.isEmpty()
                        ? null
                        : new NightHours(p.getInt("nochePendIni", 21), p.getInt("nochePendFin", 7));
        LocalDate d = null;
        try {
            if (!from.isEmpty()) d = LocalDate.parse(from);
        } catch (RuntimeException ignored) {
        }
        return new NightHours.Setting(active, pend, d);
    }

    /**
     * Horario de noche que vale hoy; lo deja puesto en AbsoluteRules. Barato: se llama a menudo.
     */
    public static NightHours night(Context c) {
        try {
            NightHours n = NightHours.effective(nightSetting(c), LocalDate.now());
            AbsoluteRules.useNight(n.start, n.end);
            return n;
        } catch (RuntimeException e) {
            return NightHours.DEFAULT;
        }
    }

    /**
     * Pide un horario de noche. Devuelve el texto para el usuario: si endurece, "desde ya"; si
     * afloja, "desde mañana".
     */
    public static String requestNight(Context c, int start, int end) {
        if (!NightHours.valid(start, end))
            return "Ese horario no protege el sueño. La noche empieza entre las 19 y las 23 h y acaba entre las 5 y las 9 h.";
        LocalDate today = LocalDate.now();
        NightHours.Setting before = NightHours.settle(nightSetting(c), today);
        NightHours.Setting after = NightHours.request(before, new NightHours(start, end), today);
        SharedPreferences.Editor e =
                prefs(c).edit()
                        .putInt("nocheIni", after.active.start)
                        .putInt("nocheFin", after.active.end);
        if (after.pending == null) e.putString("nochePendDesde", "");
        else
            e.putInt("nochePendIni", after.pending.start)
                    .putInt("nochePendFin", after.pending.end)
                    .putString("nochePendDesde", after.pendingFrom.toString());
        e.apply();
        InspectorLog.event(
                c,
                InspectorLog.CATEGORIA,
                "",
                0,
                "noche:"
                        + before.active.label()
                        + ">"
                        + start
                        + "-"
                        + end
                        + (after.pending == null ? ":ya" : ":manana"),
                null);
        night(c);
        notifyChanged();
        return after.pending == null
                ? "Listo: la noche va de " + after.active.label() + " desde ya."
                : "Hecho. Como suelta horas, la noche irá de "
                        + after.pending.label()
                        + " desde mañana. Hoy sigue de "
                        + after.active.label()
                        + ".";
    }

    // ---------------- Ánimo ----------------

    /** ¿Ya se preguntó hoy? (contestado o "ahora no"). */
    public static boolean moodAskedToday(Context c) {
        return LocalDate.now().toString().equals(prefs(c).getString("animoDia", ""));
    }

    /** "Ahora no": no vuelve a preguntar hoy (salvo al elegir CUIDADO). */
    public static void skipMood(Context c) {
        prefs(c).edit().putString("animoDia", LocalDate.now().toString()).apply();
    }

    /** Puntuación de hoy (1..5) o 0 si no contestó. */
    /** Ánimo que cuenta para el tope de Cuidado: la primera respuesta de hoy. */
    static boolean moodLowForCap(Context c) {
        SharedPreferences p = prefs(c);
        return LocalDate.now().toString().equals(p.getString("animoDia", ""))
                && Mood.suggestCare(p.getInt("animoPrimero", p.getInt("animo", 0)));
    }

    public static int moodToday(Context c) {
        SharedPreferences p = prefs(c);
        return LocalDate.now().toString().equals(p.getString("animoDia", ""))
                ? p.getInt("animo", 0)
                : 0;
    }

    /** Guarda el ánimo (solo en el teléfono) y devuelve si conviene sugerir CUIDADO. */
    public static boolean recordMood(Context c, int score, String word, String where) {
        if (!Mood.valid(score)) return false;
        String w = Mood.word(word);
        long now = System.currentTimeMillis();
        SharedPreferences p = prefs(c);
        String today = LocalDate.now().toString();
        SharedPreferences.Editor e = p.edit().putString("animoDia", today).putInt("animo", score);
        // Punto de fuga cerrado: la exención del tope de Cuidado la decide la PRIMERA respuesta del
        // día. Volver a contestar 1 o 2 después ya no abre Cuidado sin límite.
        if (!today.equals(p.getString("animoDia", ""))) e.putInt("animoPrimero", score);
        e.apply();
        InspectorLog.event(
                c,
                InspectorLog.ANIMO,
                "",
                now,
                0,
                1,
                score,
                (where == null ? "" : where) + (w.isEmpty() ? "" : ":" + w),
                "animo|" + now);
        notifyChanged();
        return Mood.suggestCare(score);
    }

    // ---------------- Lo bueno primero ----------------

    /** Números visibles en "Hoy": siempre, salvo en CUIDADO. */
    public static boolean showNumbers(Context c) {
        return CareMode.showNumbers(mode(c), false);
    }

    // ---------------- Sugerencias y recordatorios ----------------

    /**
     * CUIDADO: suma ocio desde la última sugerencia y dice si toca una (como mucho cada 2 h y tras
     * 30 min de ocio).
     */
    public static synchronized boolean careSuggestionDue(Context c, long ocioMs) {
        SharedPreferences p = prefs(c);
        long now = System.currentTimeMillis();
        long acc = p.getLong("cuidadoOcio", 0) + Math.max(0, ocioMs);
        boolean due = CareMode.careSuggestionDue(now, p.getLong("cuidadoUltima", -1), acc);
        if (due) p.edit().putLong("cuidadoUltima", now).putLong("cuidadoOcio", 0).apply();
        else p.edit().putLong("cuidadoOcio", acc).apply();
        return due;
    }
}
