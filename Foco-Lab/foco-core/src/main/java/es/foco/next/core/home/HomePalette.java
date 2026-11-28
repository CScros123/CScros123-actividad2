package es.foco.next.core.home;

import es.foco.next.core.home.HomeState.Estado;

/**
 * Etapa 3: paletas del inicio por estado (ARGB). Sin negro puro ni rojo. Pura para poder
 * comprobarlo en pruebas.
 */
public final class HomePalette {
    /** fondo, tarjetas, texto, texto secundario, acento, sol, nubes, colina, texto sobre colina. */
    public final int background, card, text, muted, accent, sun, cloud, hill, onHill;

    public final boolean moon;

    private HomePalette(
            int background,
            int card,
            int text,
            int muted,
            int accent,
            int sun,
            int cloud,
            int hill,
            int onHill,
            boolean moon) {
        this.background = background;
        this.card = card;
        this.text = text;
        this.muted = muted;
        this.accent = accent;
        this.sun = sun;
        this.cloud = cloud;
        this.hill = hill;
        this.onHill = onHill;
        this.moon = moon;
    }

    // NEUTRO: la paleta del prototipo aprobado (salvia, colina oliva, sol pastel).
    public static final HomePalette NEUTRO =
            new HomePalette(
                    0xFFDCE4DC,
                    0xFFF6F8F4,
                    0xFF1C2920,
                    0xFF526356,
                    0xFF355C40,
                    0xFFF1D7A5,
                    0xFFE8EFE8,
                    0xFF667B66,
                    0xFFF5F7F2,
                    false);
    public static final HomePalette PRODUCTIVO =
            new HomePalette(
                    0xFFE6F2E1,
                    0xFFFBFDF8,
                    0xFF1F3A30,
                    0xFF4F6656,
                    0xFF3C7A52,
                    0xFFF3D27A,
                    0xFFF4F9F1,
                    0xFF5E8264,
                    0xFFF7FAF3,
                    false);
    public static final HomePalette DISTRAIDO =
            new HomePalette(
                    0xFFD5DBD6,
                    0xFFEEF1EE,
                    0xFF1F3A30,
                    0xFF56635A,
                    0xFF4E6358,
                    0xFFE3E0CF,
                    0xFFC2CAC4,
                    0xFF6E7A72,
                    0xFFF2F4F2,
                    false);
    public static final HomePalette NOCHE =
            new HomePalette(
                    0xFF1F3A3A,
                    0xFF2A4A48,
                    0xFFE8E2C8,
                    0xFFB8B6A0,
                    0xFFB9C9A8,
                    0xFFE8E2C8,
                    0xFF34524F,
                    0xFF17302F,
                    0xFFE8E2C8,
                    true);
    public static final HomePalette DESPERTAR =
            new HomePalette(
                    0xFFF6E3C6,
                    0xFFFDF7EC,
                    0xFF4A3420,
                    0xFF7A6048,
                    0xFF8A5A2B,
                    0xFFEDB57A,
                    0xFFFBEEDC,
                    0xFFA07A52,
                    0xFFFDF7EC,
                    false);

    /**
     * Temas fotográficos: paleta sobre foto (tarjetas translúcidas, "colina" = velo del dock). Sol
     * y nubes solo se usan si falta la foto (paisaje dibujado con los mismos colores).
     */
    public static HomePalette photo(
            int background, int card, int text, int muted, int accent, int scrim, boolean moon) {
        // El velo del dock siempre es oscuro: texto claro encima aunque el tema sea claro.
        int lum = (((text >> 16) & 255) * 3 + ((text >> 8) & 255) * 6 + (text & 255)) / 10;
        int onScrim = lum > 140 ? text : 0xFFFAF6F2;
        return new HomePalette(
                background,
                card,
                text,
                muted,
                accent,
                accent,
                blend(background, text, .12f),
                scrim,
                onScrim,
                moon);
    }

    public static HomePalette of(Estado e) {
        if (e == null) return NEUTRO;
        switch (e) {
            case PRODUCTIVO:
                return PRODUCTIVO;
            case DISTRAIDO:
                return DISTRAIDO;
            case NOCHE:
                return NOCHE;
            case DESPERTAR:
                return DESPERTAR;
            default:
                return NEUTRO;
        }
    }

    /** Colinas: dos tonos derivados del acento (lejana más clara). */
    public static int blend(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int ca = (a >>> 24), ra = (a >> 16) & 255, ga = (a >> 8) & 255, ba = a & 255;
        int cb = (b >>> 24), rb = (b >> 16) & 255, gb = (b >> 8) & 255, bb = b & 255;
        return (Math.round(ca + (cb - ca) * t) << 24)
                | (Math.round(ra + (rb - ra) * t) << 16)
                | (Math.round(ga + (gb - ga) * t) << 8)
                | Math.round(ba + (bb - ba) * t);
    }

    /** Planta: 5 tamaños según los minutos invertidos hoy (0, 10, 30, 60 y 90 o más). */
    public static final int[] PLANT_STEPS_MIN = {10, 30, 60, 90};

    public static int plantSize(long investedTodayMin) {
        int size = 0;
        for (int s : PLANT_STEPS_MIN) if (investedTodayMin >= s) size++;
        return size; // 0..4
    }

    /** Ningún color de la paleta es negro puro ni rojo (r alto con g y b bajos). */
    public static boolean safe(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        boolean black = r < 16 && g < 16 && b < 16;
        boolean red = r > 150 && g < 90 && b < 90;
        return !black && !red;
    }

    public int[] all() {
        return new int[] {background, card, text, muted, accent, sun, cloud, hill, onHill};
    }
}
