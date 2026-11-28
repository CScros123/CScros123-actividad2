package es.foco.next.core.home;

import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.AppConfig;
import es.foco.next.core.Categories;
import es.foco.next.core.FocusEngine;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Etapa 2: en qué sección va cada app del cajón y qué puede ir en el dock. Pura. */
public final class HomeSections {
    private HomeSections() {}

    public enum Section {
        PRODUCTIVA("Productivas"),
        HERRAMIENTA("Herramientas"),
        OCIO("Ocio"),
        BLOQUEADA("Bloqueo total");
        public final String title;

        Section(String title) {
            this.title = title;
        }
    }

    public static final int DOCK_MIN = 4, DOCK_MAX = 6;

    public static Section of(AppConfig c, String pkg) {
        if (Categories.totalBlock(pkg)) return Section.BLOQUEADA; // Telegram, siempre
        if (AbsoluteRules.social(pkg)) return Section.OCIO; // Regla 1
        if (c != null && (c.watchedApps.contains(pkg) || c.observedApps.contains(pkg)))
            return Section.OCIO;
        if (c != null && c.productiveApps.contains(pkg)) return Section.PRODUCTIVA;
        return Section.HERRAMIENTA;
    }

    /** Ocio y bloqueo total NUNCA van al dock ni al inicio. */
    public static boolean dockAllowed(AppConfig c, String pkg) {
        Section s = of(c, pkg);
        return s == Section.PRODUCTIVA || s == Section.HERRAMIENTA;
    }

    /**
     * El dock que se muestra: solo las elegidas que siguen permitidas, sin repetir, como mucho
     * {@link #DOCK_MAX}. De noche, solo las esenciales.
     */
    public static List<String> dock(
            AppConfig c, Collection<String> chosen, Collection<String> installed, boolean night) {
        List<String> out = new ArrayList<>();
        if (chosen == null) return out;
        for (String p : chosen) {
            if (p == null || p.isEmpty() || out.contains(p)) continue;
            if (installed != null && !installed.contains(p)) continue;
            if (!dockAllowed(c, p)) continue;
            if (night && !FocusEngine.essential(c, p)) continue;
            out.add(p);
            if (out.size() == DOCK_MAX) break;
        }
        return out;
    }

    /** Minúsculas y sin tildes, para buscar "camara" y encontrar "Cámara". */
    public static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    /** Coincide si alguna palabra del nombre empieza por la consulta, o el nombre la contiene. */
    public static int score(String label, String query) {
        String l = fold(label), q = fold(query);
        if (q.isEmpty()) return 0;
        if (l.startsWith(q)) return 3;
        for (String w : l.split("\\s+")) if (w.startsWith(q)) return 2;
        return l.contains(q) ? 1 : -1;
    }

    /** Orden del cajón: productivas, herramientas, ocio (al final) y bloqueo total. */
    public static int order(Section s) {
        return s.ordinal();
    }
}
