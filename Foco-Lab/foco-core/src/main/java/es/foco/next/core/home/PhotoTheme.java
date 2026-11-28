package es.foco.next.core.home;

/**
 * Temas fotográficos del inicio (prototipo "Foco S23 con 9 temas"). Cada tema tiene su foto (si el
 * usuario la dio), su paleta y cuándo aparece solo. Puro y testeable.
 *
 * <p>Cuidado: el prototipo cambiaba a "Fricción ígnea" con más de 60 min de ocio y a "Alerta roja"
 * con más de 120. Eso es castigo y rojo para el ocio; aquí no se hace. Con mucho ocio el que cambia
 * es el búho (preocupado y tierno). LAVA solo existe si el usuario la elige a mano.
 */
public enum PhotoTheme {
    CAFE(
            "Café del Alba",
            "05:00–08:00",
            "cafe",
            true,
            HomePalette.photo(
                    0xFFEFE3D3, 0xE0FAF6F2, 0xFF261C17, 0xFF6B584E, 0xFF5C3D2E, 0xD13B2B23, false)),
    HORIZONTE(
            "Horizonte del Alba",
            "Alba con un reto hecho",
            "horizonte",
            false,
            HomePalette.photo(
                    0xFF1A222D, 0xDB1A222D, 0xFFF5F2F0, 0xFFB8C0CC, 0xFFE87A64, 0xD6111720, false)),
    CASCADA(
            "Cascada Serena",
            "08:00–11:00 (días alternos)",
            "cascada",
            false,
            HomePalette.photo(
                    0xFF1B2621, 0xDB1B2621, 0xFFF2F6F3, 0xFFB3C4B9, 0xFF5FA37D, 0xD9131C18, false)),
    DUNAS(
            "Dunas de Enfoque",
            "08:00–11:00 (días alternos)",
            "dunas",
            false,
            HomePalette.photo(
                    0xFF291D12, 0xDB291D12, 0xFFFAF4ED, 0xFFD6C2AE, 0xFFE59A4C, 0xD91F150C, false)),
    RUTA(
            "Ruta de Lectura",
            "18:00–19:30",
            "ruta",
            false,
            HomePalette.photo(
                    0xFF211912, 0xE0211912, 0xFFF7F2EC, 0xFFC7B8A8, 0xFFD98A2B, 0xD9150F0A, false)),
    /** v38: fondo de mediodía (cielo azul, sol alto), generado con el mismo estilo. */
    PRADERA(
            "Pradera de Mediodía",
            "11:00–15:30",
            "pradera",
            false,
            HomePalette.photo(
                    0xFF19281F, 0xDB19281F, 0xFFF3F7F2, 0xFFB5C7BA, 0xFF6DBB7F, 0xD9111C16, false)),
    /** v38: fondo de tarde (luz dorada, sol a media altura). */
    TARDE(
            "Tarde Dorada",
            "15:30–18:00",
            "tarde",
            false,
            HomePalette.photo(
                    0xFF2A2014, 0xDB2A2014, 0xFFFBF5EC, 0xFFD9C6AC, 0xFFE9A54B, 0xD91C150C, false)),
    LUNA(
            "Luna de Ocaso",
            "19:30 hasta la noche",
            "luna",
            false,
            HomePalette.photo(
                    0xFF1C232E, 0xDB1C232E, 0xFFF5F1EE, 0xFFB5BCC7, 0xFFE8875E, 0xD9141A22, false)),
    CUMBRE(
            "Cumbre Nocturna",
            "Noche",
            "cumbre",
            false,
            HomePalette.photo(
                    0xFF141E2B, 0xE0141E2B, 0xFFF0F4F8, 0xFFA3B4C6, 0xFF8AB4D9, 0xE00D151F, true)),
    LAVA(
            "Fricción Ígnea",
            "Solo si la eliges",
            "lava",
            false,
            HomePalette.photo(
                    0xFF29120D, 0xE029120D, 0xFFFFF3ED, 0xFFD9B2A3, 0xFFFF8A3D, 0xE01A0A06, false)),
    /** v31: se desbloquea con 7 días seguidos al 100 %. Nunca automático: lo eliges tú. */
    AURORA(
            "Aurora",
            "Ganado con 7 días seguidos",
            "aurora",
            false,
            HomePalette.photo(
                    0xFF0E1D24, 0xE00E1D24, 0xFFEFF7F4, 0xFFA6C4BC, 0xFF6FD6B0, 0xE0081419, true)),
    /** v31: se desbloquea con 14 días seguidos al 100 %. */
    MAR(
            "Mar al alba",
            "Ganado con 14 días seguidos",
            "mar",
            false,
            HomePalette.photo(
                    0xFF1E2A3A, 0xDB1E2A3A, 0xFFF7F2EE, 0xFFC2C6D0, 0xFFF0A07A, 0xD9141C28, false));

    /** El desbloqueo que pide el tema, o null si está siempre disponible. */
    public String unlock() {
        return this == AURORA ? "fondo_aurora" : this == MAR ? "fondo_mar" : null;
    }

    public final String label, when;

    /**
     * Fondo digital (res/drawable-nodpi/theme_&lt;photo&gt;.webp), sintetizado en la v29 con los
     * colores de tus fotos. Desde entonces los 8 temas tienen el suyo (antes Horizonte, Luna y
     * Cumbre no, y de tarde y de noche el inicio se quedaba sin foto).
     */
    public final String photo;

    /** Cielo claro arriba: iconos oscuros en la barra de estado. */
    public final boolean light;

    public final HomePalette palette;

    PhotoTheme(String label, String when, String photo, boolean light, HomePalette palette) {
        this.label = label;
        this.when = when;
        this.photo = photo;
        this.light = light;
        this.palette = palette;
    }

    /** Lo que decide el tema automático. */
    public static final class Facts {
        public int hour, minute;
        public boolean night;

        /** v38: ya no cambian el fondo (lo expresa el búho); se conservan por compatibilidad. */
        public boolean productive, focus;

        public int retosHoy;

        /** v38: para alternar fondos un día sí y otro no. */
        public int dayOfYear;

        /** v38: fondos ganados (entran solos en la rotación). */
        public boolean aurora, mar;
    }

    /**
     * v38: automático solo por la hora, para que el cielo cuadre con el momento del día (nada de
     * luna a mediodía). Lo que haces lo expresa el búho, no el fondo. Nunca por el ocio.
     */
    public static PhotoTheme auto(Facts f) {
        boolean alt = (f.dayOfYear & 1) == 1;
        PhotoTheme night = f.aurora && alt ? AURORA : CUMBRE;
        if (f.night) return night;
        int m = f.hour * 60 + f.minute;
        if (m < 5 * 60) return night; // madrugada
        if (m < 8 * 60) return f.retosHoy > 0 ? HORIZONTE : CAFE; // alba
        if (m < 11 * 60) return alt ? CASCADA : DUNAS; // mañana
        if (m < 15 * 60 + 30) return PRADERA; // mediodía
        if (m < 18 * 60) return TARDE; // tarde
        if (m < 19 * 60 + 30) return f.mar && alt ? MAR : RUTA; // ocaso
        return LUNA; // anochecer
    }

    /** ¿El cielo de este tema es de noche? (estrellas o luna). */
    public boolean nightSky() {
        return this == CUMBRE || this == AURORA || this == LUNA;
    }

    /** "AUTO" o el nombre de un tema. */
    public static PhotoTheme choose(String choice, Facts f) {
        if (choice != null && !choice.isEmpty() && !"AUTO".equals(choice))
            try {
                return valueOf(choice);
            } catch (IllegalArgumentException ignored) {
            }
        return auto(f);
    }
}
