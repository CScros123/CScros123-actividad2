package es.foco.next.core;

import java.util.*;

/** Lote 1: un bloqueo con nombre. Independiente del Foco clásico (FocusEngine), que no cambia. */
public final class BlockRule {
    public enum Period {
        WINDOW,
        DAILY
    }

    /**
     * Lote 2: ALWAYS_LIMIT = Lote 1 (sin horario); BLOCK_IN_WINDOW = bloqueo total dentro del
     * horario; LIMIT_IN_WINDOW = el límite solo cuenta y bloquea dentro.
     */
    public enum ScheduleMode {
        ALWAYS_LIMIT,
        BLOCK_IN_WINDOW,
        LIMIT_IN_WINDOW
    }

    public static final int MAX_PAUSES = 5;
    public String id = "", name = "";
    public boolean enabled = true, autoAddNew;
    public final Set<String> packages = new LinkedHashSet<>();

    /** Minutos de uso permitidos por periodo. 0 = bloqueo total mientras esté activo. */
    public int limitMinutes = 15;

    public Period period = Period.DAILY;

    /** Duración de la ventana si period==WINDOW. */
    public int windowMinutes = 60;

    public int maxPauses = 1, pauseMinutes = 5;

    /** Lote 2: horario opcional. */
    public ScheduleMode scheduleMode = ScheduleMode.ALWAYS_LIMIT;

    public final List<Schedule> schedules = new ArrayList<>();

    /**
     * Lote 2: lista blanca. Bloquea todas las apps de lanzador salvo {@link #packages} y las
     * esenciales.
     */
    public boolean allowlist;

    /**
     * Lote 3: sitios ("youtube.com", "youtube.com/shorts") y palabras clave bloqueados en
     * navegadores compatibles.
     */
    public final List<String> sites = new ArrayList<>(), keywords = new ArrayList<>();

    /**
     * Lote 3: bloquear navegadores que Foco no sabe leer, mientras los sitios/palabras de esta
     * regla se aplican.
     */
    public boolean blockUnsupportedBrowsers;

    /**
     * Lote 5: cerrar la hoja de pago de Google Play mientras la regla está activada (y dentro de su
     * franja si tiene horario).
     */
    public boolean blockPurchases;

    /**
     * Secciones dentro de apps bloqueadas (ids de {@link InAppFilter#CATALOG}, por ejemplo
     * "youtube_shorts"). Como el filtro web: sin límites.
     */
    public final List<String> sections = new ArrayList<>();

    public boolean web() {
        return !sites.isEmpty() || !keywords.isEmpty() || blockUnsupportedBrowsers;
    }

    public boolean scheduled() {
        return scheduleMode != ScheduleMode.ALWAYS_LIMIT && !schedules.isEmpty();
    }

    /** Lote 5: copia con id nuevo, nombre «X» (copia) y desactivada. */
    public BlockRule duplicate(String newId) {
        BlockRule r = copy();
        r.id = newId;
        r.enabled = false;
        String base = name == null ? "" : name.trim();
        String suffix = " (copia)";
        if (base.length() + suffix.length() > 40)
            base = base.substring(0, 40 - suffix.length()).trim();
        r.name = base + suffix;
        return r;
    }

    public long limitMillis() {
        return Math.max(0, limitMinutes) * 60_000L;
    }

    public long windowMillis() {
        return Math.max(1, windowMinutes) * 60_000L;
    }

    public BlockRule copy() {
        BlockRule r = new BlockRule();
        r.id = id;
        r.name = name;
        r.enabled = enabled;
        r.autoAddNew = autoAddNew;
        r.packages.addAll(packages);
        r.limitMinutes = limitMinutes;
        r.period = period;
        r.windowMinutes = windowMinutes;
        r.maxPauses = maxPauses;
        r.pauseMinutes = pauseMinutes;
        r.scheduleMode = scheduleMode;
        r.allowlist = allowlist;
        for (Schedule x : schedules) r.schedules.add(x.copy());
        r.sites.addAll(sites);
        r.keywords.addAll(keywords);
        r.blockUnsupportedBrowsers = blockUnsupportedBrowsers;
        r.blockPurchases = blockPurchases;
        r.sections.addAll(sections);
        return r;
    }

    /** Normaliza valores fuera de rango (por ejemplo de un archivo editado a mano). */
    public void clamp() {
        if (name == null || name.trim().isEmpty()) name = "Bloqueo";
        name = name.trim();
        if (name.length() > 40) name = name.substring(0, 40);
        limitMinutes = Math.max(0, Math.min(24 * 60, limitMinutes));
        windowMinutes = Math.max(5, Math.min(24 * 60, windowMinutes));
        maxPauses = Math.max(0, Math.min(MAX_PAUSES, maxPauses));
        pauseMinutes = Math.max(1, Math.min(60, pauseMinutes));
        if (period == null) period = Period.DAILY;
        schedules.removeIf(x -> x == null);
        for (Schedule x : schedules) x.clamp();
        schedules.removeIf(x -> x.days == 0);
        while (schedules.size() > 7) schedules.remove(schedules.size() - 1);
        sites.removeIf(Objects::isNull);
        keywords.removeIf(Objects::isNull);
        WebFilter.clean(sites, keywords);
        InAppFilter.clean(sections);
        if (scheduleMode == null || schedules.isEmpty()) scheduleMode = ScheduleMode.ALWAYS_LIMIT;
    }
}
