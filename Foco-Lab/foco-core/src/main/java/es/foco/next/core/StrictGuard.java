package es.foco.next.core;

import java.util.*;

/**
 * Modo estricto: decide, a partir de los textos visibles en Ajustes o el instalador, si la pantalla
 * permitiría desinstalar o desactivar Foco (o sus protecciones). Lógica pura y testeable. Si tu
 * versión de One UI usa otros textos, añádelos a las listas de abajo (en minúsculas).
 */
public final class StrictGuard {
    private StrictGuard() {}

    public static final String APP_LABEL = "foco";
    public static final Set<String> GUARDED_PACKAGES =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    "com.android.settings",
                                    "com.samsung.android.settings",
                                    "com.samsung.accessibility",
                                    "com.google.android.packageinstaller",
                                    "com.android.packageinstaller",
                                    "com.samsung.android.packageinstaller",
                                    "com.samsung.android.lool",
                                    "com.samsung.android.sm.devicesecurity")));

    /** Acciones de la ficha de una app: junto a "Foco" indican la ficha de Foco. */
    static final String[] APP_ACTIONS = {
        "desinstalar",
        "forzar detención",
        "forzar detencion",
        "uninstall",
        "force stop",
        "desactivar",
        "disable"
    };

    /** Pantallas peligrosas por sí mismas (sin necesitar el nombre de Foco). */
    static final String[] DANGEROUS_SCREENS = {
        "administradores de dispositivos",
        "apps de administración del dispositivo",
        "aplicaciones de administración del dispositivo",
        "device admin apps",
        "device administrators",
        "dns privado",
        "private dns",
        "fecha y hora",
        "date and time",
        "date & time"
    };

    /** Pantallas peligrosas solo si Foco aparece: accesibilidad, VPN, instalador. */
    static final String[] FOCO_SCREENS = {
        "accesibilidad",
        "accessibility",
        "apps instaladas",
        "aplicaciones instaladas",
        "installed apps",
        "servicios instalados",
        "installed services",
        "vpn",
        "¿quieres desinstalar",
        "do you want to uninstall",
        "desinstalar",
        "uninstall"
    };

    /**
     * Textos que solo aparecen en la página de Accesibilidad de Foco (One UI y Android puro), en su
     * diálogo de desactivación o en su propia descripción. Bastan por sí solos: no dependen de
     * detectar el título.
     */
    static final String[] FOCO_PAGE = {
        "atajo de foco",
        "foco shortcut",
        "¿desea desactivar foco",
        "¿quieres desactivar foco",
        "desactivar foco",
        "turn off foco",
        "stop foco",
        "¿detener foco",
        "foco usa cambios de ventana"
    };

    /**
     * Diálogo de confirmación de «Forzar detención» (no lleva el nombre de la app): cuenta si Foco
     * aparece en la misma pantalla (el servicio lee todas las ventanas de Ajustes, también la ficha
     * de debajo). Forzar la detención apaga la Accesibilidad de Foco.
     */
    static final String[] FORCE_STOP_DIALOG = {
        "¿forzar detención?",
        "¿forzar detencion?",
        "forzar detención?",
        "¿forzar la detención?",
        "force stop?",
        "force stop app?",
        "¿quieres forzar la detención",
        "¿desea forzar la detención",
        "si fuerzas la detención",
        "si fuerza la detención",
        "if you force stop an app"
    };

    public static boolean guardedPackage(String pkg) {
        return pkg != null && GUARDED_PACKAGES.contains(pkg);
    }

    /** true si la pantalla con estos textos debe cerrarse en modo estricto durante un bloqueo. */
    public static boolean dangerous(List<String> texts) {
        return check(texts, true);
    }

    /**
     * Modo estricto reforzado (con el candado de tiempo activo, aunque no haya bloqueo): solo las
     * pantallas propias de Foco (su ficha, su página de Accesibilidad, administradores, diálogo de
     * desinstalación). DNS privado, fecha y hora y VPN siguen protegidas solo durante un bloqueo
     * para no dejarte sin Ajustes normales.
     */
    public static boolean dangerousAlways(List<String> texts) {
        return check(texts, false);
    }

    /** Pantallas de red y reloj: solo se cierran durante un bloqueo. */
    static final String[] BLOCK_ONLY = {
        "dns privado", "private dns", "fecha y hora", "date and time", "date & time", "vpn"
    };

    private static boolean blockOnly(String k) {
        for (String b : BLOCK_ONLY) if (b.equals(k)) return true;
        return false;
    }

    private static boolean check(List<String> texts, boolean network) {
        if (texts == null || texts.isEmpty()) return false;
        boolean foco = false;
        List<String> low = new ArrayList<>(texts.size());
        for (String t : texts) {
            if (t == null) continue;
            String s = t.trim().toLowerCase(Locale.ROOT);
            if (s.isEmpty()) continue;
            low.add(s);
            if (s.equals(APP_LABEL)
                    || s.startsWith("foco ")
                    || s.contains("\"foco\"")
                    || s.contains("«foco»")
                    || s.contains("desinstalar foco")
                    || s.contains("uninstall foco")) foco = true;
        }
        for (String s : low) for (String k : FOCO_PAGE) if (s.contains(k)) return true;
        for (String s : low)
            for (String k : DANGEROUS_SCREENS)
                if ((network || !blockOnly(k)) && (s.equals(k) || s.startsWith(k))) return true;
        if (!foco) return false;
        for (String s : low) {
            for (String k : APP_ACTIONS) if (s.equals(k)) return true;
            for (String k : FORCE_STOP_DIALOG) if (s.startsWith(k)) return true;
            if (s.equals("información de la aplicación")
                    || s.equals("informacion de la aplicacion")
                    || s.equals("app info")) return true;
            for (String k : FOCO_SCREENS)
                if ((network || !blockOnly(k)) && (s.equals(k) || s.startsWith(k))) return true;
        }
        return false;
    }
}
