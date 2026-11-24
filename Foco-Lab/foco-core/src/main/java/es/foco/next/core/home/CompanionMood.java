package es.foco.next.core.home;

import es.foco.next.core.home.HomeState.Estado;

/**
 * Compañero vivo de la colina (puro): traduce el día en rasgos visuales, sin textos técnicos ni
 * castigos duros. Crece con el tiempo invertido; florece con retos superados; amanece con rocío
 * (mañana o tras un descanso real); descansa de noche; y si hubo ocio sin saldo pierde algunas
 * hojas, que vuelven al día siguiente (recuperable, nunca "muerto").
 */
public final class CompanionMood {
    public enum Variant {
        BONSAI("Bonsái zen"),
        FOGATA("Fogata de campamento"),
        PIEDRAS("Piedras en equilibrio");
        public final String label;

        Variant(String label) {
            this.label = label;
        }

        public static Variant of(String name) {
            for (Variant v : values()) if (v.name().equals(name)) return v;
            return BONSAI;
        }
    }

    /** 0..4: tamaño (tiempo invertido hoy). */
    public final int growth;
    /** 0..3: flores, chispas o musgo (retos superados hoy). */
    public final int blooms;
    /** 0..3: hojas caídas, ceniza o piedras sueltas (ocio sin saldo hoy). */
    public final int fallen;
    /** Rocío o vapor: por la mañana o tras un descanso real. */
    public final boolean dew;
    /** Noche: el banco deja de sumar y el compañero descansa. */
    public final boolean resting;
    /** Productivo: más luz. Distraído: se inquieta un poco (oscila más). */
    public final boolean bright, restless;

    private CompanionMood(int growth, int blooms, int fallen, boolean dew, boolean resting, boolean bright, boolean restless) {
        this.growth = growth;
        this.blooms = blooms;
        this.fallen = fallen;
        this.dew = dew;
        this.resting = resting;
        this.bright = bright;
        this.restless = restless;
    }

    public static CompanionMood of(Estado e, long investedMin, int retosHoy, long extraMin, int descansosHoy, int hour) {
        boolean night = e == Estado.NOCHE;
        int fallen = extraMin <= 0 ? 0 : (int) Math.min(3, 1 + extraMin / 5);
        boolean dew = !night && ((hour >= 5 && hour < 10) || descansosHoy > 0 || e == Estado.DESPERTAR);
        return new CompanionMood(
                HomePalette.plantSize(investedMin),
                Math.max(0, Math.min(3, retosHoy)),
                fallen,
                dew,
                night,
                e == Estado.PRODUCTIVO,
                e == Estado.DISTRAIDO);
    }

    /** Frase corta al tocarlo (sin números técnicos más allá de los minutos del día). */
    public String caption(Variant v, long investedMin) {
        String who = v == Variant.FOGATA ? "La fogata" : v == Variant.PIEDRAS ? "Las piedras" : "El bonsái";
        if (resting) return who + " descansa. El ocio vuelve a las 7:00.";
        if (fallen > 0) return who + " perdió algo hoy; mañana se recupera.";
        if (blooms > 0) return "Retos superados hoy: " + blooms + (blooms == 1 ? ". Se nota." : ". Se nota mucho.");
        if (dew) return "Rocío fresco: buen momento para empezar.";
        if (investedMin <= 0) return who + " espera tu primer paso del día.";
        return investedMin + " min invertidos hoy. " + who + (growth >= 3 ? " está en su mejor momento." : " sigue creciendo.");
    }
}
