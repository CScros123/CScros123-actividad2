package es.foco.next.core;

import java.util.*;

/**
 * Lote 1: uso por bloqueo y periodo, pausas del día y fin de la pausa actual. Reloj de pared en ms.
 */
public final class RuleState {
    public static final class Entry {
        /** Inicio de la ventana (WINDOW, -1 sin ventana) o día ISO (DAILY). */
        public long windowStart = -1;

        public String usageDay = "";
        public long usedMillis;
        public String pausesDay = "";
        public int pausesUsed;
        public long pausedUntil;

        public Entry copy() {
            Entry e = new Entry();
            e.windowStart = windowStart;
            e.usageDay = usageDay;
            e.usedMillis = usedMillis;
            e.pausesDay = pausesDay;
            e.pausesUsed = pausesUsed;
            e.pausedUntil = pausedUntil;
            return e;
        }
    }

    public final Map<String, Entry> entries = new HashMap<>();

    public Entry of(String id) {
        return entries.computeIfAbsent(id, k -> new Entry());
    }

    public void retain(Collection<String> ids) {
        entries.keySet().retainAll(new HashSet<>(ids));
    }
}
