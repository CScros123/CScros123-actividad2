package es.foco.next.core;

import java.util.*;
import java.util.function.Function;

/**
 * Desplazamiento: cuando una app de ocio vigilada baja y otra sube a la vez (bloqueas TikTok y el
 * tiempo se va a Instagram o WhatsApp). Compara los últimos 7 días con los 7 anteriores por app.
 * Lógica pura.
 *
 * <ul>
 *   <li>Baja: una app vigilada (de ocio o de un bloqueo) pierde al menos el 30 % y 30 minutos.
 *   <li>Sube: cualquier otra app (incluidas esenciales como WhatsApp) gana al menos el 50 % y 20
 *       minutos.
 * </ul>
 */
public final class UsageShift {
    private UsageShift() {}

    public static final long MIN = 60_000L, MIN_DROP = 30 * MIN, MIN_RISE = 20 * MIN;
    public static final double DROP = 0.30, RISE = 0.50;

    /** Como mucho dos apps que suben en el texto. */
    public static final int MAX_RISES = 2;

    public static final class Change {
        public final String pkg;
        public final long before, after;

        Change(String pkg, long before, long after) {
            this.pkg = pkg;
            this.before = before;
            this.after = after;
        }

        public long delta() {
            return after - before;
        }

        /** Cambio en %, redondeado; {@link Integer#MAX_VALUE} si antes era 0. */
        public int percent() {
            return before <= 0
                    ? Integer.MAX_VALUE
                    : (int) Math.round(100.0 * (after - before) / before);
        }
    }

    public static final class Shift {
        public final Change down;
        public final List<Change> up;

        Shift(Change down, List<Change> up) {
            this.down = down;
            this.up = Collections.unmodifiableList(up);
        }
    }

    /**
     * El desplazamiento más claro (la vigilada que más baja y las que más suben), o null.
     *
     * @param watched apps de ocio vigiladas o con bloqueo
     * @param exclude apps que nunca cuentan (Foco, launchers); las del sistema se quitan siempre
     */
    public static Shift detect(
            Map<String, Long> before,
            Map<String, Long> after,
            Set<String> watched,
            Set<String> exclude) {
        if (watched == null || watched.isEmpty()) return null;
        Set<String> pkgs = new TreeSet<>();
        if (before != null) pkgs.addAll(before.keySet());
        if (after != null) pkgs.addAll(after.keySet());
        Change down = null;
        List<Change> up = new ArrayList<>();
        for (String p : pkgs) {
            if (ScreenTime.excluded(p, exclude)) continue;
            long b = ms(before, p), a = ms(after, p);
            Change c = new Change(p, b, a);
            if (watched.contains(p) && b > 0 && b - a >= MIN_DROP && (b - a) >= DROP * b) {
                if (down == null || c.delta() < down.delta()) down = c;
            } else if (a - b >= MIN_RISE && (b == 0 || (a - b) >= RISE * b)) up.add(c);
        }
        if (down == null || up.isEmpty()) return null;
        up.sort(
                (x, y) ->
                        x.delta() == y.delta()
                                ? x.pkg.compareTo(y.pkg)
                                : Long.compare(y.delta(), x.delta()));
        while (up.size() > MAX_RISES) up.remove(up.size() - 1);
        return new Shift(down, up);
    }

    /**
     * "Desplazamiento: bajaste TikTok (−43 %) pero Instagram subió (+120 %)"; con dos: "pero
     * subieron Instagram (+120 %) y WhatsApp (+60 %)".
     */
    public static String text(Shift s, Function<String, String> label) {
        if (s == null) return "";
        StringBuilder b =
                new StringBuilder("Desplazamiento: bajaste ")
                        .append(label.apply(s.down.pkg))
                        .append(" (")
                        .append(pct(s.down))
                        .append(") pero ");
        if (s.up.size() == 1)
            return b.append(label.apply(s.up.get(0).pkg))
                    .append(" subió (")
                    .append(pct(s.up.get(0)))
                    .append(")")
                    .toString();
        b.append("subieron ");
        for (int i = 0; i < s.up.size(); i++) {
            Change c = s.up.get(i);
            if (i > 0) b.append(i == s.up.size() - 1 ? " y " : ", ");
            b.append(label.apply(c.pkg)).append(" (").append(pct(c)).append(")");
        }
        return b.toString();
    }

    /** "−43 %", "+120 %" o, si antes era 0, "+45 min". */
    public static String pct(Change c) {
        if (c.before <= 0) return "+" + (c.after / MIN) + " min";
        int p = c.percent();
        return (p < 0 ? "−" : "+") + Math.abs(p) + " %";
    }

    private static long ms(Map<String, Long> m, String p) {
        return m == null ? 0 : Math.max(0, m.getOrDefault(p, 0L));
    }
}
