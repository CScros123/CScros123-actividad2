package es.foco.next.core.home;

import java.util.List;

/**
 * Etapa 5.8: qué pantalla de Ajustes está delante y qué hace Foco. Pura: recibe la clase de la
 * ventana y hasta unos cientos de textos visibles; no guarda nada de lo que lee.
 *
 * <p>Detección en dos niveles: primero por clase (estable entre versiones de AOSP), después por
 * texto. Las marcadas {@code ONE_UI} dependen de los textos de One UI (Samsung) y pueden cambiar
 * con una actualización; ver docs/FOCO-HOME.md.
 */
public final class SettingsWatch {
    private SettingsWatch() {}

    public enum Action {
        /** Solo se registra. */
        LOG,
        /** Aviso al usuario y registro. */
        WARN,
        /** Volver al inicio y registrar incidente. */
        HOME_INCIDENT
    }

    public enum Screen {
        WIFI(Action.LOG),
        BLUETOOTH(Action.LOG),
        BATTERY(Action.LOG),
        SOUND(Action.LOG),
        DATE_TIME(Action.WARN),
        PRIVATE_DNS(Action.HOME_INCIDENT),
        ACCESSIBILITY_FOCO(Action.HOME_INCIDENT),
        APP_INFO_FOCO(Action.HOME_INCIDENT),
        VPN(Action.LOG),
        OTHER(null);
        public final Action action;

        Screen(Action action) {
            this.action = action;
        }
    }

    public static final String SETTINGS_PKG = "com.android.settings";

    /**
     * @param className clase de la ventana (puede ser null o genérica: SubSettings)
     * @param texts textos visibles (en minúsculas o no; se pliegan)
     * @param appLabel nombre de Foco tal como lo muestra Android ("Foco")
     */
    public static Screen classify(String className, List<String> texts, String appLabel) {
        String cls = className == null ? "" : className;
        // 1) Por clase (AOSP y One UI suelen conservarlas).
        if (cls.contains("WifiSettings") || cls.contains("NetworkProviderSettings"))
            return Screen.WIFI;
        if (cls.contains("Bluetooth")) return Screen.BLUETOOTH;
        if (cls.contains("PowerUsage") || cls.contains("BatterySaver")) return Screen.BATTERY;
        if (cls.contains("SoundSettings")) return Screen.SOUND;
        if (cls.contains("DateTimeSettings")) return Screen.DATE_TIME;
        if (cls.contains("PrivateDns")) return Screen.PRIVATE_DNS;
        if (cls.contains("VpnSettings")) return Screen.VPN;
        // 2) Por texto (One UI: SubSettings genérica en casi todo).
        String foco = HomeSections.fold(appLabel == null || appLabel.isEmpty() ? "Foco" : appLabel);
        boolean hasFoco = false, dns = false, dnsChoice = false, access = false, appInfo = false;
        boolean dateTime = false;
        boolean wifi = false, bt = false, battery = false, sound = false, vpn = false;
        if (texts != null)
            for (String raw : texts) {
                String t = HomeSections.fold(raw);
                if (t.isEmpty()) continue;
                if (t.equals(foco)) hasFoco = true;
                if (t.contains("dns privado") || t.contains("private dns")) dns = true;
                // El diálogo del DNS privado (no la fila "DNS privado" de la lista de conexiones).
                if (t.contains("nombre de host")
                        || t.contains("hostname")
                        || t.equals("automatico")
                        || t.equals("automatic")) dnsChoice = true;
                // La página de Foco dentro de Accesibilidad (no la lista general de servicios).
                if (t.contains("usar foco") || t.contains("use foco")) access = true;
                if (t.contains("forzar detencion")
                        || t.contains("force stop")
                        || t.equals("desinstalar")
                        || t.equals("uninstall")
                        || t.contains("info. de la aplicacion")
                        || t.contains("informacion de la aplicacion")
                        || t.contains("app info")) appInfo = true;
                if (t.contains("fecha y hora") || t.contains("date & time")) dateTime = true;
                if (t.equals("wi-fi") || t.equals("wifi")) wifi = true;
                if (t.equals("bluetooth")) bt = true;
                if (t.contains("bateria") || t.equals("battery")) battery = true;
                if (t.contains("sonidos y vibracion") || t.equals("sonido") || t.equals("sound"))
                    sound = true;
                if (t.equals("vpn")) vpn = true;
            }
        if (dns && dnsChoice) return Screen.PRIVATE_DNS;
        if (hasFoco && appInfo) return Screen.APP_INFO_FOCO;
        if (hasFoco && access) return Screen.ACCESSIBILITY_FOCO;
        if (dateTime) return Screen.DATE_TIME;
        if (vpn) return Screen.VPN;
        if (wifi) return Screen.WIFI;
        if (bt) return Screen.BLUETOOTH;
        if (battery) return Screen.BATTERY;
        if (sound) return Screen.SOUND;
        return Screen.OTHER;
    }

    /**
     * El DNS privado solo se protege cuando YA está bien puesto: si está roto, el usuario tiene que
     * poder arreglarlo (si no, Foco lo dejaría sin internet y sin salida).
     */
    public static boolean enforce(Screen s, boolean dnsCompliant) {
        if (s == Screen.PRIVATE_DNS) return dnsCompliant;
        return s.action == Action.HOME_INCIDENT;
    }

    /** Pantallas cuya detección depende de textos de One UI (documentado). */
    public static boolean dependsOnOneUiText(Screen s) {
        return s == Screen.ACCESSIBILITY_FOCO || s == Screen.APP_INFO_FOCO;
    }
}
