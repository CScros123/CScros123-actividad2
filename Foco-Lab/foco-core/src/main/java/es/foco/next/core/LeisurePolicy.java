package es.foco.next.core;

/**
 * v32 (R5): la única tabla de "qué excepción vale ahora y qué reglas salta". La usan el servicio de
 * Accesibilidad y el respaldo (GuardService), para que una misma situación reciba la misma
 * decisión. Java puro, sin estado y con pruebas.
 *
 * <p>Separación de responsabilidades: aquí solo se DECIDE. Observar (qué app está delante y cuánto
 * tiempo), ejecutar (pausa, aviso) y registrar quedan fuera.
 *
 * <p>La noche no es una capa: no la salta nada. Con noche no hay pase.
 */
public final class LeisurePolicy {
    private LeisurePolicy() {}

    /** Excepción activa para una app de ocio. */
    public enum Pass {
        /** Sin excepción: valen todas las reglas. */
        NONE,
        /** Hora libre de Foco Core (100 % hecho): sin el límite de ocio; las demás reglas siguen. */
        FREE_HOUR,
        /** Cupón normal para ESA app: salta límite, tope, rutina, Aduana y bloqueos con nombre. */
        COUPON,
        /** Cupón de semana perfecta: salta todo salvo la noche. */
        WEEK_COUPON,
        /** Modo Cuidado: el ocio no se limita (la rutina pendiente sigue). */
        CARE
    }

    /** Capas de reglas que una excepción puede saltar. */
    public enum Layer {
        /** 5 min por hora, y la puerta de Enfoque / Weekend Lock (sin hora libre, sin ocio). */
        BUDGET,
        /** Tope diario propio y franja estricta (Supervisor). */
        SUPERVISOR,
        /** Rutina de mañana o noche pendiente y la pausa por tarea vencida. */
        ROUTINE,
        /** Tiempo elegido en la Aduana. */
        ADUANA,
        /** Bloqueos con nombre. */
        NAMED_RULES,
        /** Sesión de foco (Pomodoro) que iniciaste. */
        POMODORO,
        /** Webs y secciones bloqueadas, navegadores no compatibles. */
        WEB,
        /** Pausa por DNS que no es NextDNS. */
        DNS
    }

    /**
     * El pase que vale ahora. El más específico gana: semana perfecta, cupón, hora libre, Cuidado.
     *
     * @param coupon hay un cupón en uso para ESTA app
     * @param weekCoupon ese cupón es de semana perfecta
     */
    public static Pass pass(boolean night, boolean care, boolean coupon, boolean weekCoupon, boolean freeHour) {
        if (night) return Pass.NONE;
        if (coupon) return weekCoupon ? Pass.WEEK_COUPON : Pass.COUPON;
        if (freeHour) return Pass.FREE_HOUR;
        if (care) return Pass.CARE;
        return Pass.NONE;
    }

    /** ¿Ese pase salta esa capa? */
    public static boolean skips(Pass p, Layer l) {
        if (p == null || l == null) return false;
        switch (p) {
            case WEEK_COUPON:
                return true;
            case COUPON:
                return l == Layer.BUDGET
                        || l == Layer.SUPERVISOR
                        || l == Layer.ROUTINE
                        || l == Layer.ADUANA
                        || l == Layer.NAMED_RULES;
            case FREE_HOUR:
                return l == Layer.BUDGET || l == Layer.ADUANA;
            case CARE:
                return l != Layer.ROUTINE;
            default:
                return false;
        }
    }

    /** ¿Es un premio ganado (hora libre o cupón)? */
    public static boolean reward(Pass p) {
        return p == Pass.FREE_HOUR || p == Pass.COUPON || p == Pass.WEEK_COUPON;
    }

    /**
     * La puerta de Foco Core: en Enfoque, y en el Weekend Lock salvo en Cuidado, el ocio solo se abre
     * con la hora libre o un cupón (v26; v32 R7: también en Normal durante el Weekend Lock).
     */
    public static boolean gated(es.foco.next.core.care.CareMode.Modo m, boolean weekend) {
        return m == es.foco.next.core.care.CareMode.Modo.ENFOQUE
                || (weekend && m != es.foco.next.core.care.CareMode.Modo.CUIDADO);
    }

    /** Por qué el respaldo (sin Accesibilidad) debe tapar una app de ocio; null si puede seguir. */
    public enum Reason { NIGHT, GATE, ROUTINE }

    /**
     * Versión del respaldo: solo las reglas para las que GuardService tiene datos (app delante,
     * hora, modo, Foco Core y premios). No puede ver webs, secciones ni el DNS, y no lo finge.
     */
    public static Reason backup(boolean leisure, boolean night, boolean gated, boolean routinePending, Pass p) {
        if (!leisure) return null;
        if (night) return Reason.NIGHT;
        if (routinePending && !skips(p, Layer.ROUTINE)) return Reason.ROUTINE;
        if (gated && !skips(p, Layer.BUDGET)) return Reason.GATE;
        return null;
    }
}
