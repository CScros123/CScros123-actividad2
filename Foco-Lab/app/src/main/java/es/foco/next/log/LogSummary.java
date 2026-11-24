package es.foco.next.log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Fase 4: per-day view model for the "Registro" screen. Pure Java. */
public final class LogSummary {
    private LogSummary() {}

    /** Result of {@code SELECT day,type,pkg,COUNT(*) ... GROUP BY day,type,pkg}. */
    public static final class Count {
        public final String day, type, pkg;
        public final int count;

        public Count(String day, String type, String pkg, int count) {
            this.day = day;
            this.type = type;
            this.pkg = pkg == null ? "" : pkg;
            this.count = count;
        }
    }

    /** One {@code usage_day} row. -1 means unknown (no permission or no data). */
    public static final class Usage {
        public final String day, pkg;
        public final long observedMs, usageStatsMs;

        public Usage(String day, String pkg, long observedMs, long usageStatsMs) {
            this.day = day;
            this.pkg = pkg;
            this.observedMs = observedMs;
            this.usageStatsMs = usageStatsMs;
        }
    }

    public static final class AppDay {
        public int attempts, blocks;
        public long observedMs = -1, usageStatsMs = -1;
    }

    public static final class Day {
        public final String day;
        public int blocks, attempts, practices, alternatives, homes;

        /** Sorted: most attempts+blocks first, then by package name. */
        public final Map<String, AppDay> apps = new LinkedHashMap<>();

        Day(String day) {
            this.day = day;
        }

        public boolean empty() {
            return blocks + attempts + practices + alternatives + homes == 0 && apps.isEmpty();
        }
    }

    /** Newest day first. Unknown event types (future versions) are ignored, never fatal. */
    public static List<Day> build(List<Count> counts, List<Usage> usage) {
        Map<String, Day> days = new TreeMap<>(Collections.reverseOrder());
        Map<String, Map<String, AppDay>> apps = new TreeMap<>();
        if (counts != null)
            for (Count c : counts) {
                BlockEvent.Type t = BlockEvent.Type.parse(c.type);
                if (t == null || c.day == null || c.day.isEmpty() || c.count <= 0) continue;
                Day d = days.computeIfAbsent(c.day, Day::new);
                if (t.isBlock()) d.blocks += c.count;
                else if (t.isAttempt()) d.attempts += c.count;
                else if (t.isPractice()) d.practices += c.count;
                else if (t == BlockEvent.Type.ALT_OPENED) d.alternatives += c.count;
                else if (t == BlockEvent.Type.HOME) d.homes += c.count;
                if ((t.isBlock() || t.isAttempt()) && !c.pkg.isEmpty()) {
                    AppDay a =
                            apps.computeIfAbsent(c.day, k -> new TreeMap<>())
                                    .computeIfAbsent(c.pkg, k -> new AppDay());
                    if (t.isBlock()) a.blocks += c.count;
                    else a.attempts += c.count;
                }
            }
        if (usage != null)
            for (Usage u : usage) {
                if (u.day == null || u.day.isEmpty() || u.pkg == null || u.pkg.isEmpty()) continue;
                if (u.observedMs <= 0 && u.usageStatsMs <= 0) continue;
                days.computeIfAbsent(u.day, Day::new);
                AppDay a =
                        apps.computeIfAbsent(u.day, k -> new TreeMap<>())
                                .computeIfAbsent(u.pkg, k -> new AppDay());
                a.observedMs = u.observedMs;
                a.usageStatsMs = u.usageStatsMs;
            }
        List<Day> out = new ArrayList<>();
        for (Day d : days.values()) {
            Map<String, AppDay> row = apps.get(d.day);
            if (row != null) {
                List<Map.Entry<String, AppDay>> sorted = new ArrayList<>(row.entrySet());
                sorted.sort(
                        (x, y) -> {
                            int a = x.getValue().attempts + x.getValue().blocks,
                                    b = y.getValue().attempts + y.getValue().blocks;
                            if (a != b) return Integer.compare(b, a);
                            long ux = Math.max(x.getValue().observedMs, x.getValue().usageStatsMs),
                                    uy =
                                            Math.max(
                                                    y.getValue().observedMs,
                                                    y.getValue().usageStatsMs);
                            if (ux != uy) return Long.compare(uy, ux);
                            return x.getKey().compareTo(y.getKey());
                        });
                for (Map.Entry<String, AppDay> e : sorted) d.apps.put(e.getKey(), e.getValue());
            }
            out.add(d);
        }
        return out;
    }
}
