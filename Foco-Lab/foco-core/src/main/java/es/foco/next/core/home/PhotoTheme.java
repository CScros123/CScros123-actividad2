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
    CAFE("Café del Alba", "07:00–09:00", "cafe", true,
            HomePalette.photo(0xFFEFE3D3, 0xE0FAF6F2, 0xFF261C17, 0xFF6B584E, 0xFF5C3D2E, 0xD13B2B23, false)),
    HORIZONTE("Horizonte del Alba", "Mañana con un reto hecho", "horizonte", false,
            HomePalette.photo(0xFF1A222D, 0xDB1A222D, 0xFFF5F2F0, 0xFFB8C0CC, 0xFFE87A64, 0xD6111720, false)),
    CASCADA("Cascada Serena", "Día tranquilo", "cascada", false,
            HomePalette.photo(0xFF1B2621, 0xDB1B2621, 0xFFF2F6F3, 0xFFB3C4B9, 0xFF5FA37D, 0xD9131C18, false)),
    DUNAS("Dunas de Enfoque", "Modo Enfoque", "dunas", false,
            HomePalette.photo(0xFF291D12, 0xDB291D12, 0xFFFAF4ED, 0xFFD6C2AE, 0xFFE59A4C, 0xD91F150C, false)),
    RUTA("Ruta de Lectura", "En racha productiva", "ruta", false,
            HomePalette.photo(0xFF211912, 0xE0211912, 0xFFF7F2EC, 0xFFC7B8A8, 0xFFD98A2B, 0xD9150F0A, false)),
    LUNA("Luna de Ocaso", "17:30–20:30", "luna", false,
            HomePalette.photo(0xFF1C232E, 0xDB1C232E, 0xFFF5F1EE, 0xFFB5BCC7, 0xFFE8875E, 0xD9141A22, false)),
    CUMBRE("Cumbre Nocturna", "Noche", "cumbre", false,
            HomePalette.photo(0xFF141E2B, 0xE0141E2B, 0xFFF0F4F8, 0xFFA3B4C6, 0xFF8AB4D9, 0xE00D151F, true)),
    LAVA("Fricción Ígnea", "Solo si la eliges", "lava", false,
            HomePalette.photo(0xFF29120D, 0xE029120D, 0xFFFFF3ED, 0xFFD9B2A3, 0xFFFF8A3D, 0xE01A0A06, false)),
    /** v31: se desbloquea con 7 días seguidos al 100 %. Nunca automático: lo eliges tú. */
    AURORA("Aurora", "Ganado con 7 días seguidos", "aurora", false,
            HomePalette.photo(0xFF0E1D24, 0xE00E1D24, 0xFFEFF7F4, 0xFFA6C4BC, 0xFF6FD6B0, 0xE0081419, true)),
    /** v31: se desbloquea con 14 días seguidos al 100 %. */
    MAR("Mar al alba", "Ganado con 14 días seguidos", "mar", false,
            HomePalette.photo(0xFF1E2A3A, 0xDB1E2A3A, 0xFFF7F2EE, 0xFFC2C6D0, 0xFFF0A07A, 0xD9141C28, false));

    /** El desbloqueo que pide el tema, o null si está siempre disponible. */
    public String unlock() {
        return this == AURORA ? "fondo_aurora" : this == MAR ? "fondo_mar" : null;
    }

    public final String label, when;
    /**
     * Fondo digital (res/drawable-nodpi/theme_&lt;photo&gt;.webp), sintetizado en la v29 con los colores
     * de tus fotos. Desde entonces los 8 temas tienen el suyo (antes Horizonte, Luna y Cumbre no, y
     * de tarde y de noche el inicio se quedaba sin foto).
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
        public boolean night, productive, focus;
        public int retosHoy;
    }

    /** Automático por hora y por lo que haces. Nunca por el ocio. */
    public static PhotoTheme auto(Facts f) {
        if (f.night) return CUMBRE;
        if (f.focus || f.productive) return f.focus && !f.productive ? DUNAS : RUTA;
        int m = f.hour * 60 + f.minute;
        if (m >= 5 * 60 + 30 && m < 9 * 60) return f.retosHoy > 0 ? HORIZONTE : CAFE;
        if (m >= 17 * 60 + 30 && m < 20 * 60 + 30) return LUNA;
        return CASCADA;
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
