package es.foco.next.core;

/**
 * Auditoría: con el candado del modo estricto, el plan del Foco clásico solo puede volverse más
 * estricto (como {@link RuleChange} para los bloqueos con nombre). Lógica pura y testeable.
 * Permitido: añadir apps de ocio, quitar apps esenciales propias, cambiar la intención o la app
 * para empezar, activar el plan. Rechazado: desactivar el plan, quitar apps de ocio, añadir apps
 * esenciales (las esenciales nunca se bloquean).
 */
public final class PlanChange {
    private PlanChange() {}

    /** null si el cambio es igual o más estricto; si no, el motivo en español. */
    public static String reason(AppConfig old, AppConfig neu) {
        if (old == null || neu == null) return null;
        if (old.enabled && !neu.enabled) return "no se puede desactivar el plan";
        if (!neu.watchedApps.containsAll(old.watchedApps))
            return "no se pueden quitar apps de ocio";
        if (!old.sensitiveApps.containsAll(neu.sensitiveApps))
            return "no se pueden añadir apps esenciales";
        // Foco 1.0, etapa 2: sacar una app de observación solo vale si pasa a ocio; aprobar
        // productivas es aflojar.
        for (String p : old.observedApps)
            if (!neu.observedApps.contains(p) && !neu.watchedApps.contains(p))
                return "no se pueden sacar apps de observación";
        if (!old.productiveApps.containsAll(neu.productiveApps))
            return "no se pueden aprobar apps productivas";
        return null;
    }
}
