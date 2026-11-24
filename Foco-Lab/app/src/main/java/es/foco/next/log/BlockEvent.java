package es.foco.next.log;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Fase 4: one row of {@code block_event} in {@code foco-log.db}. Pure Java (no Android) so it can
 * be tested on the JVM. Unknown numbers are stored as -1 and exported as empty cells.
 */
public final class BlockEvent {
    /** Stable names: they are stored in the database and exported to CSV. Never rename. */
    public enum Type {
        BUDGET_LOCK("Bloqueo por límite"),
        DNS_OFF_LOCK("Bloqueo por DNS apagado"),
        ATTEMPT_WHILE_LOCKED("Intento durante el bloqueo"),
        PRACTICE_STARTED("Práctica iniciada"),
        PRACTICE_FINISHED("Práctica terminada"),
        ALT_OPENED("Alternativa abierta"),
        HOME("Ir a Inicio"),
        RULE_LOCK("Bloqueo con nombre"); // Lote 1
        public final String label;

        Type(String label) {
            this.label = label;
        }

        public boolean isBlock() {
            return this == BUDGET_LOCK || this == DNS_OFF_LOCK || this == RULE_LOCK;
        }

        public boolean isAttempt() {
            return this == ATTEMPT_WHILE_LOCKED;
        }

        public boolean isPractice() {
            return this == PRACTICE_STARTED;
        }

        /** Unknown names (for example from a newer version) return null instead of throwing. */
        public static Type parse(String name) {
            if (name == null) return null;
            for (Type t : values()) if (t.name().equals(name)) return t;
            return null;
        }
    }

    public static final int MAX_PACKAGE = 256, MAX_ACTION = 48;
    public static final long UNKNOWN = -1;

    public long id = UNKNOWN;
    public final long timestamp;
    public final String zone;
    public final String day;
    public final Type type;
    public String pkg = "";
    public long budgetUsedMs = UNKNOWN, windowLeftMs = UNKNOWN, lockMs = UNKNOWN;
    public String action = "";

    public BlockEvent(Type type, long timestamp, ZoneId zone) {
        if (type == null) throw new IllegalArgumentException("Tipo de evento vacío");
        ZoneId z = zone == null ? ZoneId.of("UTC") : zone;
        this.type = type;
        this.timestamp = timestamp;
        this.zone = z.getId();
        this.day = Instant.ofEpochMilli(timestamp).atZone(z).toLocalDate().toString();
    }

    /**
     * For rows read back from the database: keeps the stored day even if the zone rules changed.
     */
    public BlockEvent(long id, Type type, long timestamp, String zone, String day) {
        if (type == null) throw new IllegalArgumentException("Tipo de evento vacío");
        this.id = id;
        this.type = type;
        this.timestamp = timestamp;
        this.zone = zone == null ? "" : zone;
        this.day = day == null ? "" : day;
    }

    public static BlockEvent now(Type type) {
        return new BlockEvent(type, System.currentTimeMillis(), ZoneId.systemDefault());
    }

    public BlockEvent pkg(String value) {
        pkg = clean(value, MAX_PACKAGE);
        return this;
    }

    public BlockEvent action(String value) {
        action = clean(value, MAX_ACTION);
        return this;
    }

    public BlockEvent budgetUsed(long ms) {
        budgetUsedMs = ms < 0 ? UNKNOWN : ms;
        return this;
    }

    public BlockEvent windowLeft(long ms) {
        windowLeftMs = ms < 0 ? UNKNOWN : ms;
        return this;
    }

    public BlockEvent lock(long ms) {
        lockMs = ms < 0 ? UNKNOWN : ms;
        return this;
    }

    /** Only printable package/action-like text survives; anything else becomes empty. */
    static String clean(String value, int max) {
        if (value == null) return "";
        String v = value.trim();
        if (v.length() > max) return "";
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (!(ch == '.'
                    || ch == '_'
                    || ch == '-'
                    || (ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9'))) return "";
        }
        return v;
    }
}
