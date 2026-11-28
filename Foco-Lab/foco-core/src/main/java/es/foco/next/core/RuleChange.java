package es.foco.next.core;

import java.util.*;

/**
 * Modo estricto reforzado: con el candado activo, los bloqueos solo pueden volverse más estrictos.
 * Permitido: añadir bloqueos, añadir apps/sitios/palabras/secciones, bajar límites o pausas,
 * ampliar horarios. Rechazado: borrar o desactivar bloqueos, quitar apps o secciones, subir
 * límites, recortar horarios, cambiar el tipo.
 */
public final class RuleChange {
    private RuleChange() {}

    /** {@code old==null}: bloqueo nuevo. {@code neu==null}: borrar. */
    public static boolean isStricter(BlockRule old, BlockRule neu) {
        return reason(old, neu) == null;
    }

    /** null si el cambio es igual o más estricto; si no, el motivo en español. */
    public static String reason(BlockRule old, BlockRule neu) {
        if (neu == null)
            return old == null || !old.enabled ? null : "no se pueden eliminar bloqueos";
        if (old == null || !old.enabled)
            return null; // un bloqueo nuevo o desactivado no protegía nada
        if (!neu.enabled) return "no se pueden desactivar bloqueos";
        if (old.allowlist != neu.allowlist) return "no se puede cambiar el tipo de bloqueo";
        if (old.allowlist
                ? !old.packages.containsAll(neu.packages)
                : !neu.packages.containsAll(old.packages))
            return old.allowlist ? "no se pueden permitir apps nuevas" : "no se pueden quitar apps";
        if (neu.limitMinutes > old.limitMinutes) return "no se puede subir el límite";
        if (neu.period != old.period) return "no se puede cambiar el periodo";
        if (old.period == BlockRule.Period.WINDOW && neu.windowMinutes < old.windowMinutes)
            return "no se puede acortar la ventana";
        if (neu.maxPauses > old.maxPauses || neu.pauseMinutes > old.pauseMinutes)
            return "no se pueden añadir pausas";
        if (old.autoAddNew && !neu.autoAddNew) return "no se puede quitar «añadir apps nuevas»";
        if (old.blockUnsupportedBrowsers && !neu.blockUnsupportedBrowsers
                || old.blockPurchases && !neu.blockPurchases)
            return "no se pueden quitar protecciones";
        if (!neu.sites.containsAll(old.sites) || !neu.keywords.containsAll(old.keywords))
            return "no se pueden quitar sitios ni palabras";
        if (!neu.sections.containsAll(old.sections))
            return "no se pueden quitar secciones bloqueadas";
        if (!scheduleStricter(old, neu)) return "no se puede recortar el horario";
        return null;
    }

    private static boolean scheduleStricter(BlockRule old, BlockRule neu) {
        if (!old.scheduled())
            return !neu.scheduled(); // sin horario = siempre: añadir uno lo recorta
        if (!neu.scheduled())
            return old.scheduleMode
                    == BlockRule.ScheduleMode.LIMIT_IN_WINDOW; // límite siempre ⊇ límite en franja
        if (neu.scheduleMode != old.scheduleMode) return false;
        boolean[] a = coverage(old.schedules), b = coverage(neu.schedules);
        for (int i = 0; i < a.length; i++) if (a[i] && !b[i]) return false;
        return true;
    }

    /**
     * Minutos de la semana (lunes 00:00 = 0) cubiertos, con la misma semántica que {@link
     * Schedule#contains}.
     */
    static boolean[] coverage(List<Schedule> list) {
        boolean[] out = new boolean[7 * 1440];
        for (Schedule s : list)
            for (int day = 0; day < 7; day++)
                for (int m = 0; m < 1440; m++) {
                    boolean on = (s.days & (1 << day)) != 0,
                            prev = (s.days & (1 << ((day + 6) % 7))) != 0,
                            in;
                    if (s.startMinute == s.endMinute) in = on;
                    else if (s.startMinute < s.endMinute)
                        in = on && m >= s.startMinute && m < s.endMinute;
                    else in = (m >= s.startMinute && on) || (m < s.endMinute && prev);
                    if (in) out[day * 1440 + m] = true;
                }
        return out;
    }
}
