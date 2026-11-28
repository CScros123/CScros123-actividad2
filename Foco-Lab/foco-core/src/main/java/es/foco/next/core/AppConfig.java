package es.foco.next.core;

import java.util.LinkedHashSet;
import java.util.Set;

/** Only choices that change the purpose of the app. The budget is fixed at 5 min/hour. */
public final class AppConfig {
    public boolean enabled = true;
    public Set<String> watchedApps = new LinkedHashSet<>(), sensitiveApps = new LinkedHashSet<>();

    /**
     * Foco 1.0, etapa 2: en observación (mismo límite que el ocio) y productivas aprobadas (sin
     * límite).
     */
    public Set<String> observedApps = new LinkedHashSet<>(), productiveApps = new LinkedHashSet<>();

    public String intention = "Leer cinco minutos", returnApp = "";
}
