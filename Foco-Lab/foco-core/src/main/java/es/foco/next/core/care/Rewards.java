package es.foco.next.core.care;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v31: recompensas de Foco Core, puras y con pruebas.
 *
 * <ul>
 *   <li><b>Cupón</b>: 1 por cada día con todas las tareas hechas. Abre 30 min una sola app de ocio
 *       que eliges al canjearlo (15 min si es una red social, solo la app). Como mucho 3 guardados;
 *       caducan a los 7 días; 1 al día. Nunca de noche, ni con una pausa por tareas vencidas, ni sin
 *       la rutina de la mañana hecha.
 *   <li><b>Cupones de semana perfecta</b>: 2, si de lunes a domingo todos los días estuvieron al
 *       100 % (con al menos 2 tareas propias), las tareas que piden foto tienen su foto, sin pausas
 *       por tareas y sin intentos de evasión en el registro. Saltan el límite diario, el Weekend
 *       Lock, la rutina pendiente y la pausa (la persona lo pidió así: «las reglas no tienen sentido
 *       cuando se hace las cosas bien»). La noche sigue siendo la única excepción. Caducan el
 *       domingo siguiente.
 *   <li><b>Progreso</b>: días seguidos al 100 % que desbloquean cosas del inicio, que nunca se
 *       pierden.
 * </ul>
 */
public final class Rewards {
    private Rewards() {}

    public static final int COUPON_MIN = 30, SOCIAL_MIN = 15, MAX_SAVED = 3, EXPIRE_DAYS = 7, PER_DAY = 1;
    public static final int WEEK_COUPONS = 2;

    public static final class Coupon {
        public final String id;
        public final LocalDate earned, expires;
        /** Cupón de semana perfecta. */
        public final boolean week;

        public Coupon(String id, LocalDate earned, LocalDate expires, boolean week) {
            this.id = id;
            this.earned = earned;
            this.expires = expires;
            this.week = week;
        }
    }

    /** "id,ganado,caduca,semana;…" */
    public static String encode(List<Coupon> list) {
        StringBuilder b = new StringBuilder();
        for (Coupon c : list) {
            if (b.length() > 0) b.append(';');
            b.append(c.id).append(',').append(c.earned).append(',').append(c.expires).append(',').append(c.week ? 1 : 0);
        }
        return b.toString();
    }

    public static List<Coupon> decode(String s) {
        List<Coupon> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String part : s.split(";")) {
            String[] f = part.split(",");
            if (f.length < 4) continue;
            try {
                out.add(new Coupon(f[0], LocalDate.parse(f[1]), LocalDate.parse(f[2]), "1".equals(f[3])));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    /** Sin los caducados. */
    public static List<Coupon> valid(List<Coupon> list, LocalDate today) {
        List<Coupon> out = new ArrayList<>();
        for (Coupon c : list) if (!c.expires.isBefore(today)) out.add(c);
        return out;
    }

    public static int normalCount(List<Coupon> list) {
        int n = 0;
        for (Coupon c : list) if (!c.week) n++;
        return n;
    }

    /** Un cupón por el día completado (si no se ganó ya ese día y caben). */
    public static List<Coupon> earnDay(List<Coupon> list, LocalDate day, LocalDate today) {
        List<Coupon> out = valid(list, today);
        String id = "d" + day;
        for (Coupon c : list) if (c.id.equals(id)) return out;
        if (normalCount(out) >= MAX_SAVED) return out;
        out.add(new Coupon(id, day, day.plusDays(EXPIRE_DAYS), false));
        return out;
    }

    /** Cupón extra por cada 7 días seguidos (si cabe). */
    public static List<Coupon> earnBonus(List<Coupon> list, LocalDate today) {
        List<Coupon> out = valid(list, today);
        String id = "b" + today;
        for (Coupon c : list) if (c.id.equals(id)) return out;
        if (normalCount(out) >= MAX_SAVED) return out;
        out.add(new Coupon(id, today, today.plusDays(EXPIRE_DAYS), false));
        return out;
    }

    /** Los 2 cupones de una semana perfecta (lunes {@code monday}); valen hasta el domingo siguiente. */
    public static List<Coupon> earnWeek(List<Coupon> list, LocalDate monday, LocalDate today) {
        List<Coupon> out = valid(list, today);
        for (Coupon c : list) if (c.id.startsWith("s" + monday)) return out;
        LocalDate until = monday.plusDays(13); // domingo de la semana siguiente
        for (int i = 0; i < WEEK_COUPONS; i++) out.add(new Coupon("s" + monday + "-" + i, monday.plusDays(7), until, true));
        return out;
    }

    /** Minutos que abre un cupón en esa app. */
    public static int minutes(boolean social) {
        return social ? SOCIAL_MIN : COUPON_MIN;
    }

    /**
     * ¿Se puede canjear ahora? null si sí; si no, el motivo para la persona.
     *
     * @param usedToday cupones normales ya canjeados hoy
     */
    public static String whyNot(Coupon c, boolean night, boolean penalty, boolean morningPending, int usedToday) {
        if (c == null) return "No tienes cupones.";
        if (night) return "De noche no: el sueño va primero.";
        if (c.week) return null;
        if (penalty) return "Con una pausa por una tarea vencida, el cupón espera.";
        if (morningPending) return "Primero tu rutina de la mañana.";
        if (usedToday >= PER_DAY) return "Hoy ya usaste un cupón. Mañana, otro.";
        return null;
    }

    /** El que conviene usar: los normales antes (caducan antes), y de ellos el más antiguo. */
    public static Coupon pick(List<Coupon> list, boolean week) {
        Coupon best = null;
        for (Coupon c : list)
            if (c.week == week && (best == null || c.expires.isBefore(best.expires))) best = c;
        return best;
    }

    public static List<Coupon> without(List<Coupon> list, String id) {
        List<Coupon> out = new ArrayList<>();
        for (Coupon c : list) if (!c.id.equals(id)) out.add(c);
        return out;
    }

    // ---------------- Semana perfecta ----------------

    /** Un día visto para la semana perfecta. */
    public static final class DayCheck {
        public final int total, done, planned, photosMissing, penalties, evasions;

        public DayCheck(int total, int done, int planned, int photosMissing, int penalties, int evasions) {
            this.total = total;
            this.done = done;
            this.planned = planned;
            this.photosMissing = photosMissing;
            this.penalties = penalties;
            this.evasions = evasions;
        }

        public boolean perfect() {
            return total > 0 && done == total && planned >= FocoCore.MIN_TASKS && photosMissing == 0 && penalties == 0 && evasions == 0;
        }
    }

    /** Los 7 días (de lunes a domingo) perfectos. */
    public static boolean perfectWeek(List<DayCheck> days) {
        if (days == null || days.size() != 7) return false;
        for (DayCheck d : days) if (!d.perfect()) return false;
        return true;
    }

    /** Lunes de la semana que acaba de cerrar (la anterior a la de {@code today}). */
    public static LocalDate lastMonday(LocalDate today) {
        return today.with(DayOfWeek.MONDAY).minusDays(7);
    }

    // ---------------- Progreso ----------------

    /** Días seguidos al 100 % que terminan en el último día de la lista (antiguo → reciente). */
    public static int streak(List<Boolean> complete) {
        int n = 0;
        for (int i = complete.size() - 1; i >= 0; i--) {
            if (!complete.get(i)) break;
            n++;
        }
        return n;
    }

    /** Lo que se desbloquea, por días seguidos. */
    public static final int[] MILESTONES = {3, 7, 14, 30};

    public static final String[] UNLOCK_IDS = {"buho_bufanda", "fondo_aurora", "fondo_mar", "dock_dorado"};

    public static final String[] UNLOCK_NAMES = {
        "Bufanda para el búho", "Fondo Aurora", "Fondo Mar al alba", "Dock dorado"
    };

    /** Desbloqueos nuevos al llegar a {@code streak} (los que aún no se tenían). */
    public static List<String> newUnlocks(int streak, java.util.Set<String> have) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < MILESTONES.length; i++)
            if (streak >= MILESTONES[i] && !have.contains(UNLOCK_IDS[i])) out.add(UNLOCK_IDS[i]);
        return out;
    }

    /** Próximo hito {días, índice} o null si ya están todos. */
    public static int[] nextMilestone(java.util.Set<String> have) {
        for (int i = 0; i < MILESTONES.length; i++) if (!have.contains(UNLOCK_IDS[i])) return new int[] {MILESTONES[i], i};
        return null;
    }

    public static String unlockName(String id) {
        for (int i = 0; i < UNLOCK_IDS.length; i++) if (UNLOCK_IDS[i].equals(id)) return UNLOCK_NAMES[i];
        return id;
    }

    /** Cada 7 días seguidos, un cupón extra. */
    public static boolean bonusAt(int streak) {
        return streak > 0 && streak % 7 == 0;
    }

    public static List<Coupon> none() {
        return Collections.emptyList();
    }
}
