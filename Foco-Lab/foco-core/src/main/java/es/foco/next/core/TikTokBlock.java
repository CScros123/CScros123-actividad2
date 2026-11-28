package es.foco.next.core;

import java.util.Arrays;
import java.util.List;

/**
 * Interruptor rápido «Bloquear TikTok por completo»: TikTok sin minutos de ocio ni pausas (límite
 * 0, siempre).
 */
public final class TikTokBlock {
    private TikTokBlock() {}

    public static final String RULE_NAME = "Sin TikTok";

    /** TikTok, TikTok (algunas regiones) y TikTok Lite. */
    public static final List<String> PACKAGES =
            Arrays.asList(
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill",
                    "com.zhiliaoapp.musically.go");

    public static boolean on(BlockRule r) {
        return r != null
                && r.enabled
                && !r.scheduled()
                && r.limitMinutes == 0
                && !r.allowlist
                && r.packages.containsAll(PACKAGES);
    }

    /**
     * Encender: bloqueo total sin horario y sin pausas. Apagar: lo desactiva (quien llama comprueba
     * el modo estricto con {@link RuleChange}).
     */
    public static BlockRule toggle(BlockRule existing, boolean on, String newId) {
        BlockRule r = existing == null ? new BlockRule() : existing.copy();
        if (existing == null) {
            r.id = newId;
            r.name = RULE_NAME;
        }
        if (on) {
            r.enabled = true;
            r.limitMinutes = 0;
            r.maxPauses = 0;
            r.allowlist = false;
            r.scheduleMode = BlockRule.ScheduleMode.ALWAYS_LIMIT;
            r.schedules.clear();
            r.packages.addAll(PACKAGES);
        } else r.enabled = false;
        r.clamp();
        return r;
    }
}
