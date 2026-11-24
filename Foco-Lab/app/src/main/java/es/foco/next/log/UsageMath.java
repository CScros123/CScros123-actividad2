package es.foco.next.log;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fase 4: foreground time per app from Android usage events (UsageStatsManager.queryEvents),
 * clipped to a range. Pure Java. One app in the foreground at a time; an app already open before
 * {@code start} is not counted (under-estimates rather than invents time, like the engine).
 */
public final class UsageMath {
    private UsageMath() {}

    public static final int START = 1, STOP = 2, CLOSE_ALL = 3;
    public static final long MAX_DAY = 24L * 60 * 60_000;

    public static final class Mark {
        public final long time;
        public final String pkg;
        public final int kind;

        public Mark(long time, String pkg, int kind) {
            this.time = time;
            this.pkg = pkg == null ? "" : pkg;
            this.kind = kind;
        }
    }

    /**
     * @param marks events in time order (as Android returns them)
     * @param only if non-null, only these packages are returned
     */
    public static Map<String, Long> foreground(
            List<Mark> marks, long start, long end, Set<String> only) {
        Map<String, Long> out = new LinkedHashMap<>();
        if (marks == null || end <= start) return out;
        String current = "";
        long since = start;
        for (Mark m : marks) {
            if (m == null) continue;
            long t = Math.max(start, Math.min(end, m.time));
            if (m.kind == START && !m.pkg.isEmpty()) {
                if (!current.equals(m.pkg)) {
                    add(out, current, since, t);
                    current = m.pkg;
                    since = t;
                }
            } else if (m.kind == STOP) {
                if (current.equals(m.pkg)) {
                    add(out, current, since, t);
                    current = "";
                }
            } else if (m.kind == CLOSE_ALL) {
                add(out, current, since, t);
                current = "";
            }
        }
        add(out, current, since, end);
        if (only != null) out.keySet().retainAll(only);
        return out;
    }

    private static void add(Map<String, Long> out, String pkg, long from, long to) {
        if (pkg.isEmpty() || to <= from) return;
        out.put(pkg, Math.min(MAX_DAY, out.getOrDefault(pkg, 0L) + (to - from)));
    }
}
