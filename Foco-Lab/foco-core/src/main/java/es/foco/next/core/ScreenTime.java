package es.foco.next.core;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Lote 4: pure aggregation for "Estadísticas". Turns resume/pause marks into foreground time per
 * app and local day, then weekly sums and trend deltas. No Android types; the app feeds
 * UsageStatsManager events.
 *
 * <ul>
 *   <li>Only one app is in the foreground: a resume of another app closes the current one (missing
 *       pause).
 *   <li>An app stays open while any of its activities is resumed (pause of A1 after resume of A2
 *       keeps it open).
 *   <li>Repeated resumes of the same activity do not double count (overlap).
 *   <li>Intervals crossing local midnight are split between both days.
 * </ul>
 */
public final class ScreenTime {
    private ScreenTime() {}

    public static final int RESUME = 1, PAUSE = 2, CLOSE_ALL = 3;
    public static final long MIN = 60_000L, DEFAULT_TREND_MIN = 5 * MIN;

    public static final class Mark {
        public final long time;
        public final String pkg;
        public final String activity;
        public final int kind;

        public Mark(long time, String pkg, String activity, int kind) {
            this.time = time;
            this.pkg = pkg == null ? "" : pkg;
            this.activity = activity == null ? "" : activity;
            this.kind = kind;
        }
    }

    /**
     * Foreground ms per day (yyyy-MM-dd) and package, clipped to [start,end). Marks must be in time
     * order.
     */
    public static Map<String, Map<String, Long>> perDay(
            List<Mark> marks, long start, long end, ZoneId zone) {
        Map<String, Map<String, Long>> out = new TreeMap<>();
        walk(marks, start, end, (pkg, from, to) -> add(out, pkg, from, to, zone));
        return out;
    }

    /**
     * Foreground ms per day, package and local hour (index 0–23), same rules as {@link #perDay}.
     * Used for the peak window of "Sugerencias". A 25-hour DST day folds the repeated hour into the
     * same index.
     */
    public static Map<String, Map<String, long[]>> perHour(
            List<Mark> marks, long start, long end, ZoneId zone) {
        Map<String, Map<String, long[]>> out = new TreeMap<>();
        walk(
                marks,
                start,
                end,
                (pkg, from, to) -> {
                    while (from < to) {
                        java.time.ZonedDateTime z = Instant.ofEpochMilli(from).atZone(zone);
                        long next =
                                z.withMinute(0)
                                        .withSecond(0)
                                        .withNano(0)
                                        .plusHours(1)
                                        .toInstant()
                                        .toEpochMilli();
                        long cut = Math.min(to, next);
                        out.computeIfAbsent(z.toLocalDate().toString(), k -> new LinkedHashMap<>())
                                        .computeIfAbsent(pkg, k -> new long[24])[z.getHour()] +=
                                cut - from;
                        from = cut;
                    }
                });
        return out;
    }

    private interface Sink {
        void interval(String pkg, long from, long to);
    }

    private static void walk(List<Mark> marks, long start, long end, Sink out) {
        if (marks == null || end <= start) return;
        String current = "";
        long since = start;
        Set<String> open = new HashSet<>();
        for (Mark m : marks) {
            if (m == null) continue;
            long t = Math.max(start, Math.min(end, m.time));
            if (m.kind == RESUME && !m.pkg.isEmpty()) {
                if (!current.equals(m.pkg)) {
                    emit(out, current, since, t);
                    current = m.pkg;
                    since = t;
                    open.clear();
                }
                open.add(m.activity);
            } else if (m.kind == PAUSE) {
                if (current.equals(m.pkg)) {
                    open.remove(m.activity);
                    if (open.isEmpty()) {
                        emit(out, current, since, t);
                        current = "";
                    }
                }
            } else if (m.kind == CLOSE_ALL) {
                emit(out, current, since, t);
                current = "";
                open.clear();
            }
        }
        emit(out, current, since, end);
    }

    private static void emit(Sink out, String pkg, long from, long to) {
        if (!pkg.isEmpty() && to > from) out.interval(pkg, from, to);
    }

    private static void add(
            Map<String, Map<String, Long>> out, String pkg, long from, long to, ZoneId zone) {
        if (pkg.isEmpty() || to <= from) return;
        while (from < to) {
            LocalDate d = Instant.ofEpochMilli(from).atZone(zone).toLocalDate();
            long next = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long cut = Math.min(to, next);
            out.computeIfAbsent(d.toString(), k -> new LinkedHashMap<>())
                    .merge(pkg, cut - from, Long::sum);
            from = cut;
        }
    }

    /** Total of one day's map. */
    public static long total(Map<String, Long> day) {
        long sum = 0;
        if (day != null) for (long v : day.values()) sum += Math.max(0, v);
        return sum;
    }

    /** Per-app sum over the given days (missing days count as zero). */
    public static Map<String, Long> sum(
            Map<String, Map<String, Long>> days, Collection<String> which) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (days == null || which == null) return out;
        for (String d : which) {
            Map<String, Long> m = days.get(d);
            if (m != null)
                for (Map.Entry<String, Long> e : m.entrySet())
                    out.merge(e.getKey(), Math.max(0, e.getValue()), Long::sum);
        }
        return out;
    }

    /** The {@code n} consecutive days ending at {@code last}, oldest first. */
    public static List<String> days(LocalDate last, int n) {
        List<String> out = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) out.add(last.minusDays(i).toString());
        return out;
    }

    /** Totals per day for the given days, same order. */
    public static long[] totals(Map<String, Map<String, Long>> days, List<String> which) {
        long[] out = new long[which.size()];
        for (int i = 0; i < out.length; i++)
            out[i] = total(days == null ? null : days.get(which.get(i)));
        return out;
    }

    /** Apps sorted by time descending, without excluded/noise packages, at most {@code n}. */
    public static List<Map.Entry<String, Long>> top(
            Map<String, Long> apps, Set<String> exclude, int n) {
        List<Map.Entry<String, Long>> out = new ArrayList<>();
        if (apps == null) return out;
        for (Map.Entry<String, Long> e : apps.entrySet())
            if (e.getValue() > 0 && !excluded(e.getKey(), exclude))
                out.add(Map.entry(e.getKey(), e.getValue()));
        out.sort(
                (a, b) ->
                        a.getValue().equals(b.getValue())
                                ? a.getKey().compareTo(b.getKey())
                                : Long.compare(b.getValue(), a.getValue()));
        return out.size() > n ? new ArrayList<>(out.subList(0, Math.max(0, n))) : out;
    }

    /** System UI, the Android framework and common launchers never show up in top lists. */
    public static boolean excluded(String pkg, Set<String> exclude) {
        if (pkg == null || pkg.isEmpty()) return true;
        if (exclude != null && exclude.contains(pkg)) return true;
        return pkg.equals("android")
                || pkg.equals("com.android.systemui")
                || pkg.contains(".launcher")
                || pkg.equals("com.google.android.apps.nexuslauncher")
                || pkg.equals("com.miui.home")
                || pkg.equals("com.android.settings.intelligence")
                || pkg.endsWith(".inputmethod")
                || pkg.contains(".inputmethod.");
    }

    public static final class Delta {
        public final String pkg;
        public final long before, after;

        Delta(String pkg, long before, long after) {
            this.pkg = pkg;
            this.before = before;
            this.after = after;
        }

        public long change() {
            return after - before;
        }
    }

    public static final class Trend {
        public final List<Delta> up = new ArrayList<>(), down = new ArrayList<>();
    }

    /**
     * Compares two periods per app. Only changes of at least {@code minMs} count. Biggest change
     * first, at most {@code n} per direction.
     */
    public static Trend trend(
            Map<String, Long> before,
            Map<String, Long> after,
            Set<String> exclude,
            long minMs,
            int n) {
        Trend t = new Trend();
        Set<String> pkgs = new HashSet<>();
        if (before != null) pkgs.addAll(before.keySet());
        if (after != null) pkgs.addAll(after.keySet());
        for (String p : pkgs) {
            if (excluded(p, exclude)) continue;
            long b = before == null ? 0 : Math.max(0, before.getOrDefault(p, 0L)),
                    a = after == null ? 0 : Math.max(0, after.getOrDefault(p, 0L));
            Delta d = new Delta(p, b, a);
            if (d.change() >= minMs) t.up.add(d);
            else if (-d.change() >= minMs) t.down.add(d);
        }
        t.up.sort(
                (x, y) ->
                        x.change() == y.change()
                                ? x.pkg.compareTo(y.pkg)
                                : Long.compare(y.change(), x.change()));
        t.down.sort(
                (x, y) ->
                        x.change() == y.change()
                                ? x.pkg.compareTo(y.pkg)
                                : Long.compare(x.change(), y.change()));
        while (t.up.size() > n) t.up.remove(t.up.size() - 1);
        while (t.down.size() > n) t.down.remove(t.down.size() - 1);
        return t;
    }
}
