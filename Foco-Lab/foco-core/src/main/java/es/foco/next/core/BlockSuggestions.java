package es.foco.next.core;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;

/**
 * «Sugerencias para ti»: bloqueos propuestos a partir del uso real de los últimos 14–28 días.
 * Lógica pura.
 *
 * <ul>
 *   <li><b>Noche</b>: las horas punta (2–3 h seguidas con más ocio) → bloqueo total en franja,
 *       todos los días, para tus apps de ocio con más tiempo. Sin datos por horas: 20:00–23:00.
 *   <li><b>Día flojo</b> (el día de la semana con más tiempo de media, si destaca): límite de 15
 *       min ese día entero.
 *   <li><b>Estados</b>: si WhatsApp pasa de 30 min al día de media, el atajo de Estados y canales.
 * </ul>
 *
 * Cada sugerencia tiene un id estable; «Descartar» la oculta 14 días ({@link #dismiss}).
 */
public final class BlockSuggestions {
    private BlockSuggestions() {}

    public static final long MIN = 60_000L, HOUR = 60 * MIN, DAY_MS = 24 * HOUR;
    public static final int MIN_DAYS = 14,
            MAX_DAYS = 28,
            DISMISS_DAYS = 14,
            MAX_APPS = 5,
            LIMIT_WEAK_DAY = 15;
    public static final int FALLBACK_START = 20 * 60, FALLBACK_END = 23 * 60;

    /** Una franja necesita al menos 1 h de ocio acumulada para fiarse de ella. */
    static final long MIN_PEAK_MS = HOUR;

    /** El día flojo debe superar la media diaria en un 20 % y 1 h. */
    static final double WEAK_RATIO = 1.2;

    /** WhatsApp con 30 min al día de media o más. */
    static final long WHATSAPP_DAILY = 30 * MIN;

    /**
     * Apps de ocio por defecto, si están instaladas: TikTok, Instagram, Facebook y Call of Duty.
     */
    public static final List<String> DEFAULT_LEISURE =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "com.zhiliaoapp.musically",
                            "com.ss.android.ugc.trill",
                            InAppFilter.INSTAGRAM,
                            InAppFilter.FACEBOOK,
                            "com.activision.callofduty.shooter",
                            "com.activision.callofduty.warzone"));

    public static final String NIGHT_NAME = "Noche sin ocio", STATUS_ID = "estados";

    public enum Kind {
        NIGHT,
        WEAK_DAY,
        WHATSAPP
    }

    public static final class Suggestion {
        public final String id, title, detail;
        public final Kind kind;

        /**
         * El bloqueo que crea «Crear bloqueo» (sin id: lo pone quien llama). Para WHATSAPP es el
         * atajo.
         */
        final BlockRule rule;

        Suggestion(String id, Kind kind, String title, String detail, BlockRule rule) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.detail = detail;
            this.rule = rule;
        }

        /** Copia del bloqueo con el id dado: activado, editable después en Bloqueos. */
        public BlockRule rule(String newId) {
            BlockRule r = rule.copy();
            r.id = newId;
            r.enabled = true;
            r.clamp();
            return r;
        }
    }

    /**
     * Franja punta: inicio y fin en minutos locales (fin puede ser 0 = medianoche) y si viene de
     * datos reales.
     */
    public static final class Window {
        public final int start, end;
        public final boolean measured;

        Window(int start, int end, boolean measured) {
            this.start = start;
            this.end = end;
            this.measured = measured;
        }

        public String text() {
            return Schedule.hhmm(start) + "–" + Schedule.hhmm(end);
        }
    }

    /**
     * Las 2–3 horas seguidas (pueden cruzar medianoche) con más tiempo de {@code apps} en los días
     * dados. Se queda con 2 h si recogen el 85 % de lo que recogerían 3. Sin datos suficientes:
     * 20:00–23:00.
     */
    public static Window peak(
            Map<String, Map<String, long[]>> hourly, Collection<String> days, Set<String> apps) {
        long[] h = new long[24];
        if (hourly != null && days != null)
            for (String d : days) {
                Map<String, long[]> m = hourly.get(d);
                if (m == null) continue;
                for (Map.Entry<String, long[]> e : m.entrySet()) {
                    if (apps != null && !apps.isEmpty() && !apps.contains(e.getKey())) continue;
                    if (ScreenTime.excluded(e.getKey(), null)) continue;
                    long[] v = e.getValue();
                    for (int i = 0; i < 24 && i < v.length; i++) h[i] += Math.max(0, v[i]);
                }
            }
        int best3 = -1;
        long sum3 = 0;
        for (int i = 0; i < 24; i++) {
            long s = h[i] + h[(i + 1) % 24] + h[(i + 2) % 24];
            if (s > sum3) {
                sum3 = s;
                best3 = i;
            }
        }
        if (best3 < 0 || sum3 < MIN_PEAK_MS) return new Window(FALLBACK_START, FALLBACK_END, false);
        int best2 = -1;
        long sum2 = 0;
        for (int i = 0; i < 24; i++) {
            long s = h[i] + h[(i + 1) % 24];
            if (s > sum2) {
                sum2 = s;
                best2 = i;
            }
        }
        if (sum2 * 100 >= sum3 * 85) return new Window(best2 * 60, ((best2 + 2) % 24) * 60, true);
        return new Window(best3 * 60, ((best3 + 3) % 24) * 60, true);
    }

    /**
     * Día de la semana con más tiempo de media (solo días con datos), o null si no destaca o hay
     * pocos días.
     */
    public static DayOfWeek weakDay(
            Map<String, Map<String, Long>> daily, Collection<String> days, Set<String> exclude) {
        long[] sum = new long[7];
        int[] n = new int[7];
        long all = 0;
        int count = 0;
        if (daily != null && days != null)
            for (String d : days) {
                Map<String, Long> m = daily.get(d);
                if (m == null) continue;
                long t = 0;
                for (Map.Entry<String, Long> e : m.entrySet())
                    if (!ScreenTime.excluded(e.getKey(), exclude)) t += Math.max(0, e.getValue());
                if (t <= 0) continue;
                int w = LocalDate.parse(d).getDayOfWeek().getValue() - 1;
                sum[w] += t;
                n[w]++;
                all += t;
                count++;
            }
        if (count < 7) return null;
        double mean = (double) all / count;
        int best = -1;
        double bestAvg = 0;
        for (int i = 0; i < 7; i++)
            if (n[i] > 0) {
                double avg = (double) sum[i] / n[i];
                if (avg > bestAvg) {
                    bestAvg = avg;
                    best = i;
                }
            }
        if (best < 0 || bestAvg < mean * WEAK_RATIO || bestAvg - mean < HOUR) return null;
        return DayOfWeek.of(best + 1);
    }

    /**
     * Apps de ocio con más tiempo (como mucho 5). Sin tiempo registrado: las de ocio por defecto
     * que haya.
     */
    public static List<String> topApps(
            Map<String, Map<String, Long>> daily, Collection<String> days, Set<String> leisure) {
        List<String> out = new ArrayList<>();
        if (leisure == null || leisure.isEmpty()) return out;
        Map<String, Long> sum = ScreenTime.sum(daily, days);
        for (Map.Entry<String, Long> e : ScreenTime.top(sum, null, Integer.MAX_VALUE))
            if (leisure.contains(e.getKey()) && e.getValue() >= MIN && out.size() < MAX_APPS)
                out.add(e.getKey());
        if (out.isEmpty())
            for (String p : DEFAULT_LEISURE)
                if (leisure.contains(p) && out.size() < MAX_APPS) out.add(p);
        return out;
    }

    /**
     * Sugerencias con los últimos {@link #MAX_DAYS} días hasta {@code today}. Hacen falta al menos
     * {@link #MIN_DAYS} días con datos. {@code leisure}: apps vigiladas, de bloqueos y las de ocio
     * por defecto instaladas (sin esenciales).
     */
    public static List<Suggestion> build(
            Map<String, Map<String, Long>> daily,
            Map<String, Map<String, long[]>> hourly,
            LocalDate today,
            Set<String> leisure,
            Set<String> exclude,
            Function<String, String> label) {
        List<Suggestion> out = new ArrayList<>();
        List<String> days = ScreenTime.days(today, MAX_DAYS);
        int withData = 0;
        for (String d : days) {
            Map<String, Long> m = daily == null ? null : daily.get(d);
            if (m != null && ScreenTime.total(m) > 0) withData++;
        }
        if (withData < MIN_DAYS) return out;
        List<String> apps = topApps(daily, days, leisure);
        if (!apps.isEmpty()) {
            Window w = peak(hourly, days, new HashSet<>(apps));
            BlockRule r = base(NIGHT_NAME, apps);
            r.scheduleMode = BlockRule.ScheduleMode.BLOCK_IN_WINDOW;
            r.limitMinutes = 0;
            r.schedules.add(Schedule.of(Schedule.ALL_DAYS, w.start, w.end));
            r.clamp();
            out.add(
                    new Suggestion(
                            "noche:" + w.start + "-" + w.end,
                            Kind.NIGHT,
                            "Noche: " + w.text(),
                            (w.measured
                                            ? "Tus horas con más ocio son " + w.text() + ". "
                                            : "Aún no hay datos por horas; propuesta habitual "
                                                    + w.text()
                                                    + ". ")
                                    + "Bloqueo total de "
                                    + names(apps, label)
                                    + " cada día en esa franja, con 1 pausa de 5 min.",
                            r));
            DayOfWeek weak = weakDay(daily, days, exclude);
            if (weak != null) {
                String day = dayName(weak);
                BlockRule s = base(day + " con límite", apps);
                s.scheduleMode = BlockRule.ScheduleMode.LIMIT_IN_WINDOW;
                s.limitMinutes = LIMIT_WEAK_DAY;
                s.period = BlockRule.Period.DAILY;
                s.schedules.add(Schedule.of(Schedule.bit(weak), 0, 0));
                s.clamp();
                out.add(
                        new Suggestion(
                                "dia:" + weak.getValue(),
                                Kind.WEAK_DAY,
                                day + ": tu día más largo",
                                "Los "
                                        + plural(day)
                                        + " usas el móvil bastante más que otros días. "
                                        + LIMIT_WEAK_DAY
                                        + " min en total para "
                                        + names(apps, label)
                                        + " ese día.",
                                s));
            }
        }
        long wa = 0;
        for (Map.Entry<String, Long> e : ScreenTime.sum(daily, days).entrySet())
            if (InAppFilter.sectionsInEssential(e.getKey())) wa += Math.max(0, e.getValue());
        if (wa / withData >= WHATSAPP_DAILY) {
            BlockRule q = InAppFilter.quickWhatsApp(null, true, "");
            out.add(
                    new Suggestion(
                            STATUS_ID,
                            Kind.WHATSAPP,
                            "Estados y canales de WhatsApp",
                            "WhatsApp: "
                                    + (wa / withData / MIN)
                                    + " min al día de media. Bloquea solo Estados y canales; los chats y las llamadas siguen igual.",
                            q));
        }
        return out;
    }

    /**
     * Quita las descartadas (hasta su fecha) y las que ya tienen su bloqueo (mismo nombre, o el
     * atajo ya encendido).
     */
    public static List<Suggestion> visible(
            List<Suggestion> all,
            List<BlockRule> rules,
            Map<String, Long> dismissedUntil,
            long now) {
        List<Suggestion> out = new ArrayList<>();
        for (Suggestion s : all) {
            Long until = dismissedUntil == null ? null : dismissedUntil.get(s.id);
            if (until != null && until > now) continue;
            boolean exists = false;
            if (rules != null)
                for (BlockRule r : rules) {
                    if (s.kind == Kind.WHATSAPP
                            ? InAppFilter.QUICK_WHATSAPP_NAME.equals(r.name) && r.enabled
                            : s.rule.name.equals(r.name)) {
                        exists = true;
                        break;
                    }
                }
            if (!exists) out.add(s);
        }
        return out;
    }

    /** Fecha hasta la que se oculta una sugerencia descartada ahora. */
    public static long dismiss(long now) {
        return now + DISMISS_DAYS * DAY_MS;
    }

    static String dayName(DayOfWeek d) {
        String n = d.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"));
        return n.substring(0, 1).toUpperCase(Locale.ROOT) + n.substring(1);
    }

    /** «sábados», «domingos»; lunes a viernes no cambian. */
    static String plural(String day) {
        String d = day.toLowerCase(Locale.ROOT);
        return d.endsWith("s") ? d : d + "s";
    }

    private static BlockRule base(String name, List<String> apps) {
        BlockRule r = new BlockRule();
        r.name = name;
        r.packages.addAll(apps);
        r.maxPauses = 1;
        r.pauseMinutes = 5;
        r.allowlist = false;
        return r;
    }

    private static String names(List<String> apps, Function<String, String> label) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < apps.size(); i++) {
            if (i > 0) b.append(i == apps.size() - 1 ? " y " : ", ");
            b.append(label == null ? apps.get(i) : label.apply(apps.get(i)));
        }
        return b.toString();
    }
}
