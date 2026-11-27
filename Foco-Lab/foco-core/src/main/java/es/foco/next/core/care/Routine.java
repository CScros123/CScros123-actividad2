package es.foco.next.core.care;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Rutinas obligatorias de mañana y de noche. Mientras la de ahora no esté hecha, el ocio queda
 * cerrado en todos los modos (la persona lo pidió así). Lógica pura para poder probarla.
 */
public final class Routine {
    private Routine() {}

    public enum Kind {
        MORNING("mañana"),
        NIGHT("noche");

        public final String word;

        Kind(String word) {
            this.word = word;
        }
    }

    public static final class Task {
        public final String label;
        public final int minutes;

        /** Hora límite en minutos del día (8:30 → 510), o -1 sin ventana. */
        public final int window;

        /** 0 sin indicar · 1 fácil · 2 media · 3 difícil. */
        public final int difficulty;

        /** v30: al marcarla se pide una foto de evidencia. */
        public final boolean photo;

        public Task(String label, int minutes) {
            this(label, minutes, -1, 0, false);
        }

        public Task(String label, int minutes, int window, int difficulty) {
            this(label, minutes, window, difficulty, false);
        }

        public Task(String label, int minutes, int window, int difficulty, boolean photo) {
            this.label = label;
            this.minutes = minutes;
            this.window = window;
            this.difficulty = difficulty;
            this.photo = photo;
        }

        public String windowText() {
            return window < 0
                    ? ""
                    : (window / 60)
                            + ":"
                            + String.format(java.util.Locale.ROOT, "%02d", window % 60);
        }
    }

    public static final String[] DIFFICULTY = {"", "Fácil", "Media", "Difícil"};

    private static int difficultyOf(String t) {
        String s = t.trim().toLowerCase(java.util.Locale.ROOT).replace("á", "a").replace("í", "i");
        if (s.equals("facil")) return 1;
        if (s.equals("media") || s.equals("medio")) return 2;
        if (s.equals("dificil")) return 3;
        return 0;
    }

    /** Día de Foco Core: empieza a las 04:00 (de 0:00 a 3:59 sigue siendo el día anterior). */
    public static java.time.LocalDate logicalDay(LocalDateTime now) {
        return now.minusHours(4).toLocalDate();
    }

    public static final String DEFAULT_MORNING =
            "Bañarte · 10\nOrdenar el cuarto · 5\nRevisar que llevas todo para el trabajo · 3\nMoverte o hacer ejercicio · 10";
    public static final String DEFAULT_NIGHT =
            "Hacer el lonche de mañana · 10\nLeer un poco · 5\nOrdenar el cuarto · 5\nBañarte · 10\nArreglar la barba · 5";

    public static final int MAX_TASKS = 12;

    /** La mañana se abre a esta hora y, si no se hizo, deja de cerrar el ocio a mediodía. */
    public static final int MORNING_FROM = 5, MORNING_UNTIL = 12;

    /** La de noche cierra el ocio desde 2 h antes de la noche (después la noche ya lo cierra). */
    public static final int NIGHT_LEAD_HOURS = 2;

    /**
     * Una tarea por línea: "Bañarte · 10", "Bañarte 10", "Bañarte|10" o solo "Bañarte" (5 min).
     * Foco Core añade, opcionales y en cualquier orden: hora límite y dificultad: "Estudiar · 60 ·
     * 10:30 · difícil".
     */
    public static List<Task> parse(String text) {
        List<Task> out = new ArrayList<>();
        if (text == null) return out;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            // v30: "•" (que algunos teclados ponen en vez de "·") también separa; nunca queda en el
            // nombre.
            String[] parts = line.split("[·|•]");
            String label = parts[0].replace("•", "").trim();
            int minutes = -1, window = -1, difficulty = 0;
            boolean photo = false;
            for (int i = 1; i < parts.length; i++) {
                String p = parts[i].trim().replaceAll("\\s*min$", "");
                if (p.matches("\\d{1,2}:\\d{2}")) {
                    String[] hm = p.split(":");
                    int h = Integer.parseInt(hm[0]), m = Integer.parseInt(hm[1]);
                    if (h < 24 && m < 60) window = h * 60 + m;
                } else if (p.matches("\\d{1,3}")) minutes = Integer.parseInt(p);
                else if (difficultyOf(p) > 0) difficulty = difficultyOf(p);
                else if (p.equalsIgnoreCase("foto")) photo = true;
            }
            if (parts.length == 1 || minutes < 0) {
                java.util.regex.Matcher m =
                        java.util.regex.Pattern.compile("^(.*?)[\\s:-]*(\\d{1,3})\\s*(min)?$")
                                .matcher(label);
                if (m.matches() && !m.group(1).trim().isEmpty()) {
                    label = m.group(1).trim();
                    if (minutes < 0) minutes = Integer.parseInt(m.group(2));
                }
            }
            if (minutes < 0) minutes = 5;
            if (label.isEmpty()) continue;
            out.add(
                    new Task(
                            label.length() > 60 ? label.substring(0, 60) : label,
                            Math.max(1, Math.min(240, minutes)),
                            window,
                            difficulty,
                            photo));
            if (out.size() == MAX_TASKS) break;
        }
        return out;
    }

    public static String encode(List<Task> tasks) {
        StringBuilder b = new StringBuilder();
        for (Task t : tasks) {
            if (b.length() > 0) b.append('\n');
            b.append(t.label).append(" · ").append(t.minutes);
            if (t.window >= 0) b.append(" · ").append(t.windowText());
            if (t.difficulty > 0)
                b.append(" · ").append(DIFFICULTY[t.difficulty].toLowerCase(java.util.Locale.ROOT));
            if (t.photo) b.append(" · foto");
        }
        return b.toString();
    }

    // ---------------- v30: editar listas sin escribir formatos ----------------

    /** Nombre limpio para una tarea nueva: sin separadores ni saltos de línea, máximo 60. */
    public static String cleanLabel(String raw) {
        String s =
                raw == null
                        ? ""
                        : raw.replaceAll("[·|•\\n\\r]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    /** La lista con la tarea al final (si cabe). */
    public static String withAdded(String text, Task t) {
        List<Task> l = new ArrayList<>(parse(text));
        if (l.size() < MAX_TASKS) l.add(t);
        return encode(l);
    }

    /** La lista con la tarea {@code i} cambiada. */
    public static String withReplaced(String text, int i, Task t) {
        List<Task> l = new ArrayList<>(parse(text));
        if (i >= 0 && i < l.size()) l.set(i, t);
        return encode(l);
    }

    /** La lista sin la tarea {@code i}. */
    public static String withRemoved(String text, int i) {
        List<Task> l = new ArrayList<>(parse(text));
        if (i >= 0 && i < l.size()) l.remove(i);
        return encode(l);
    }

    /** Posición de la primera tarea con ese nombre, o -1. */
    public static int indexOf(String text, String label) {
        List<Task> l = parse(text);
        for (int i = 0; i < l.size(); i++) if (l.get(i).label.equalsIgnoreCase(label)) return i;
        return -1;
    }

    public static int totalMinutes(List<Task> tasks) {
        int n = 0;
        for (Task t : tasks) n += t.minutes;
        return n;
    }

    public static int remainingMinutes(List<Task> tasks, Set<Integer> done) {
        int n = 0;
        for (int i = 0; i < tasks.size(); i++) if (!done.contains(i)) n += tasks.get(i).minutes;
        return n;
    }

    public static int remainingCount(List<Task> tasks, Set<Integer> done) {
        int n = 0;
        for (int i = 0; i < tasks.size(); i++) if (!done.contains(i)) n++;
        return n;
    }

    /** Qué rutina toca ahora (sin mirar si está hecha), o null. */
    public static Kind window(LocalDateTime now, int nightStart) {
        int h = now.getHour();
        if (h >= MORNING_FROM && h < MORNING_UNTIL) return Kind.MORNING;
        int from = nightStart - NIGHT_LEAD_HOURS;
        if (h >= from && h < 24) return Kind.NIGHT;
        return null;
    }

    /** Clave del día de cada rutina (la de noche pasada la medianoche sigue siendo la de ayer). */
    public static String dayKey(Kind k, LocalDateTime now) {
        return k.name() + ":" + logicalDay(now);
    }

    /**
     * Para que marcar todo de golpe no sirva de atajo: al terminar, el ocio se abre cuando haya
     * pasado al menos la mitad del tiempo estimado (máximo 15 min) desde que empezó la rutina.
     */
    public static long unlockWaitMs(List<Task> tasks, long startedAt, long now) {
        long need = Math.min(15, totalMinutes(tasks) / 2) * 60_000L;
        if (startedAt <= 0) return need;
        return Math.max(0, startedAt + need - now);
    }

    /**
     * ¿Cierra el ocio ahora? Solo con rutinas activadas, con tareas, dentro de su ventana y si
     * falta algo (o si todo está marcado pero aún no pasó el tiempo mínimo).
     */
    public static boolean blocks(
            boolean enabled, List<Task> tasks, Set<Integer> done, long startedAt, long now) {
        if (!enabled || tasks.isEmpty()) return false;
        if (remainingCount(tasks, done) > 0) return true;
        return unlockWaitMs(tasks, startedAt, now) > 0;
    }

    public static String gateText(
            Kind k, List<Task> tasks, Set<Integer> done, long startedAt, long now) {
        int left = remainingCount(tasks, done);
        if (left == 0) {
            long w = (unlockWaitMs(tasks, startedAt, now) + 59_999) / 60_000;
            return "Rutina de la "
                    + k.word
                    + " marcada. El ocio se abre en "
                    + Math.max(1, w)
                    + " min.";
        }
        return "Primero tu rutina de la "
                + k.word
                + ": "
                + (left == 1 ? "falta 1" : "faltan " + left)
                + " (~"
                + remainingMinutes(tasks, done)
                + " min). Márcala en el inicio.";
    }

    // ---------------- Planificar: por día de la semana y por fecha ----------------

    public static final String[] DAY_NAMES = {
        "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"
    };

    public static String dayName(java.time.DayOfWeek d) {
        return DAY_NAMES[d.getValue() - 1];
    }

    /** Claves de preferencias: general, por día de la semana y por fecha concreta. */
    public static String generalKey(Kind k) {
        return "tareas_" + k.name();
    }

    public static String weekdayKey(Kind k, java.time.DayOfWeek d) {
        return "tareas_" + k.name() + "_" + d.name();
    }

    public static String dateKey(Kind k, java.time.LocalDate date) {
        return "tareas_" + k.name() + "_" + date;
    }

    /**
     * Qué lista vale para una fecha: la de esa fecha, si no la de su día de la semana, si no la
     * general.
     */
    public static String resolve(
            Kind k, java.time.LocalDate date, java.util.function.Function<String, String> stored) {
        String v = stored.apply(dateKey(k, date));
        if (v != null) return v;
        v = stored.apply(weekdayKey(k, date.getDayOfWeek()));
        if (v != null) return v;
        v = stored.apply(generalKey(k));
        if (v != null) return v;
        return k == Kind.MORNING ? DEFAULT_MORNING : DEFAULT_NIGHT;
    }

    /** El domingo (desde mediodía) el búho invita a planificar la semana, una vez. */
    public static boolean planDue(LocalDateTime now, String lastPlannedSunday) {
        return now.getDayOfWeek() == java.time.DayOfWeek.SUNDAY
                && now.getHour() >= 12
                && !now.toLocalDate().toString().equals(lastPlannedSunday);
    }

    public static Set<Integer> none() {
        return Collections.emptySet();
    }
}
