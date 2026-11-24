package es.foco.next.log;

import es.foco.next.core.AppConfig;
import es.foco.next.core.FocusState;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fase 4: copy of what Accessibility observed (7 days kept by the engine) taken on the caller's
 * thread, so the log thread never touches Repository. Pure Java; reads FocusState/AppConfig, never
 * modifies them.
 */
public final class ObservedSnapshot {
    /** day → package → ms observed (leisure + chosen alternative). */
    public final Map<String, Map<String, Long>> byDay;

    /**
     * Packages whose UsageStats time is worth contrasting: leisure apps, chosen alternative and
     * observed apps.
     */
    public final Set<String> interest;

    public ObservedSnapshot(Map<String, Map<String, Long>> byDay, Set<String> interest) {
        this.byDay = byDay == null ? Collections.emptyMap() : byDay;
        this.interest = interest == null ? Collections.emptySet() : interest;
    }

    public static ObservedSnapshot of(FocusState s, AppConfig c) {
        Map<String, Map<String, Long>> days = new LinkedHashMap<>();
        Set<String> interest = new LinkedHashSet<>();
        if (s != null) {
            add(days, interest, s.weeklyOcio);
            add(days, interest, s.weeklyAlternative);
        }
        if (c != null) {
            interest.addAll(c.watchedApps);
            if (c.returnApp != null && !c.returnApp.isEmpty()) interest.add(c.returnApp);
        }
        return new ObservedSnapshot(days, interest);
    }

    public Map<String, Long> day(String day) {
        Map<String, Long> m = byDay.get(day);
        return m == null ? Collections.emptyMap() : m;
    }

    private static void add(
            Map<String, Map<String, Long>> days,
            Set<String> interest,
            Map<String, Map<String, Long>> source) {
        if (source == null) return;
        for (Map.Entry<String, Map<String, Long>> day : source.entrySet()) {
            if (day.getValue() == null) continue;
            Map<String, Long> row = days.computeIfAbsent(day.getKey(), k -> new LinkedHashMap<>());
            for (Map.Entry<String, Long> e : day.getValue().entrySet()) {
                if (e.getKey() == null || e.getValue() == null || e.getValue() < 0) continue;
                row.merge(e.getKey(), e.getValue(), Long::sum);
                interest.add(e.getKey());
            }
        }
    }
}
