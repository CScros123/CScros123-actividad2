package es.foco.next.core.home;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Etapa 6: acompañamiento. Sustituye la fricción por mensajes cortos, con datos reales, sin culpa.
 * Puro: disparadores, límites, rotación y banco de mensajes. Quien muestra el mensaje (Android)
 * solo pregunta {@link #allowed} y {@link #pick}.
 */
public final class Companion {
    private Companion() {}

    public enum Category {
        ANIMO,
        RECORDATORIO,
        PROPUESTA,
        PAUSA,
        CELEBRACION,
        REFLEXION,
        /** Modo CUIDADO: sugerencias suaves (caminar, música, escribir, escribirle a alguien). */
        CUIDADO,
        /** Inquietud: saltar entre apps de ocio. Pregunta y propone respirar; nunca bloquea. */
        INQUIETUD
    }

    public enum Trigger {
        /** Se acabaron los 5 min de ocio: propuesta con botón. */
        LEISURE_ENDED(Category.PROPUESTA, false),
        /** Sigue en ocio sin saldo: cada 5 min, máximo 3 por hora (cuenta como ocio). */
        LEISURE_NO_BALANCE(Category.RECORDATORIO, true),
        PRODUCTIVE_20(Category.ANIMO, false),
        PRODUCTIVE_50(Category.PAUSA, false),
        RETO_PASSED(Category.CELEBRACION, false),
        LEVEL_UP(Category.CELEBRACION, false),
        NEXT_STEP_IDLE(Category.RECORDATORIO, false),
        DAYS_MILESTONE(Category.CELEBRACION, false),
        /** Modo NORMAL: pasó de los 5 min. Recordatorio con propuesta, sin bloqueo (máx. 3/h). */
        LEISURE_OVER(Category.RECORDATORIO, true),
        /** Modo CUIDADO: una sugerencia suave como mucho cada 2 h (lo limita quien llama). */
        CARE_SUGGESTION(Category.CUIDADO, false),
        /**
         * 6 aperturas de ocio en 2 min (OwlCare.restless; el límite de 30 min lo pone quien llama).
         */
        RESTLESS(Category.INQUIETUD, false),
        /** Resumen de la noche: el único permitido de noche. */
        NIGHT_SUMMARY(Category.REFLEXION, false);
        public final Category category;
        public final boolean inLeisure;

        Trigger(Category category, boolean inLeisure) {
            this.category = category;
            this.inLeisure = inLeisure;
        }
    }

    public static final int DAY_MAX = 8;
    public static final long LEISURE_REMINDER_EVERY_MS = 5 * 60_000L;
    public static final int LEISURE_REMINDERS_PER_HOUR = 3;
    public static final long PRODUCTIVE_CHEER_MS = 20 * 60_000L, PRODUCTIVE_PAUSE_MS = 50 * 60_000L;
    public static final long NEXT_STEP_IDLE_MS = 3 * 60 * 60_000L;
    public static final int NO_REPEAT_DAYS = 3;

    /** Consecuencia suave (6.4): más de 15 minutos_extra en el día = sin bloque gratis mañana. */
    public static final long EXTRA_LIMIT_MS = 15 * 60_000L;

    public static boolean noFreeBlockTomorrow(long extraTodayMs) {
        return extraTodayMs > EXTRA_LIMIT_MS;
    }

    /** Lo que ya se mostró hoy y en la última hora. */
    public static final class Budget {
        public int shownToday; // fuera de ocio
        public long lastLeisureReminder = -1;
        public List<Long> leisureRemindersLastHour = new ArrayList<>();
    }

    /**
     * ¿Se puede mostrar ahora? Nunca durante llamadas ni en No molestar; de noche solo el resumen.
     */
    public static boolean allowed(
            Trigger t, Budget b, long now, boolean night, boolean inCall, boolean dnd) {
        if (inCall || dnd) return false;
        if (night) return t == Trigger.NIGHT_SUMMARY;
        if (t == Trigger.NIGHT_SUMMARY) return false;
        if (t.inLeisure) {
            if (b.lastLeisureReminder >= 0
                    && now - b.lastLeisureReminder < LEISURE_REMINDER_EVERY_MS) return false;
            int lastHour = 0;
            for (long at : b.leisureRemindersLastHour) if (now - at < 60 * 60_000L) lastHour++;
            return lastHour < LEISURE_REMINDERS_PER_HOUR;
        }
        return b.shownToday < DAY_MAX;
    }

    /**
     * Elige un mensaje de la categoría que no se haya mostrado en los últimos 3 días. Si todos se
     * mostraron, el más antiguo. {@code lastShown}: id → día ISO en que se mostró por última vez.
     */
    public static Message pick(
            Category c, Map<String, String> lastShown, LocalDate today, long seed) {
        List<Message> pool = of(c);
        List<Message> fresh = new ArrayList<>();
        for (Message m : pool) {
            String d = lastShown == null ? null : lastShown.get(m.id);
            if (d == null || !LocalDate.parse(d).isAfter(today.minusDays(NO_REPEAT_DAYS)))
                fresh.add(m);
        }
        if (fresh.isEmpty()) {
            Message oldest = pool.get(0);
            for (Message m : pool)
                if (lastShown.get(m.id).compareTo(lastShown.get(oldest.id)) < 0) oldest = m;
            return oldest;
        }
        return fresh.get((int) Math.floorMod(seed, (long) fresh.size()));
    }

    /** Rellena {min_invertidos}, {proximo_paso} y {juego}. */
    public static String fill(String text, long investedMin, String nextStep, String game) {
        String step =
                nextStep == null || nextStep.trim().isEmpty() ? "tu próximo paso" : nextStep.trim();
        String g = game == null || game.trim().isEmpty() ? "un reto de Cognitive Lab" : game.trim();
        return text.replace("{min_invertidos}", String.valueOf(Math.max(0, investedMin)))
                .replace("{proximo_paso}", step)
                .replace("{juego}", g);
    }

    public static final class Message {
        public final String id, text;
        public final Category category;

        Message(Category category, int n, String text) {
            this.category = category;
            this.id = category.name().toLowerCase() + n;
            this.text = text;
        }
    }

    public static List<Message> of(Category c) {
        List<String> texts;
        switch (c) {
            case ANIMO:
                texts = ANIMO;
                break;
            case RECORDATORIO:
                texts = RECORDATORIO;
                break;
            case PROPUESTA:
                texts = PROPUESTA;
                break;
            case PAUSA:
                texts = PAUSA;
                break;
            case CELEBRACION:
                texts = CELEBRACION;
                break;
            case CUIDADO:
                texts = CUIDADO;
                break;
            case INQUIETUD:
                texts = INQUIETUD;
                break;
            default:
                texts = REFLEXION;
        }
        List<Message> out = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) out.add(new Message(c, i, texts.get(i)));
        return Collections.unmodifiableList(out);
    }

    static final List<String> ANIMO =
            Arrays.asList(
                    "Llevas {min_invertidos} min seguidos en lo tuyo. Así se avanza.",
                    "{min_invertidos} minutos de foco. Buen ritmo.",
                    "Vas bien: {min_invertidos} min invertidos sin saltar a otra cosa.",
                    "Esto cuenta: {min_invertidos} min dedicados a {proximo_paso}.",
                    "Media hora larga se construye así, minuto a minuto. Llevas {min_invertidos}.",
                    "Sigue a tu paso. {min_invertidos} min ya son tuyos.",
                    "Tu atención está aquí desde hace {min_invertidos} min. Bien hecho.",
                    "{min_invertidos} min sin interrupciones. Se nota.",
                    "Lo difícil era empezar, y ya llevas {min_invertidos} min.",
                    "Un bloque sólido: {min_invertidos} min. Cuando quieras, sigues.",
                    "Hoy ya suman {min_invertidos} min de trabajo real.");

    static final List<String> RECORDATORIO =
            Arrays.asList(
                    "Tu próximo paso sigue ahí: {proximo_paso}.",
                    "¿Retomamos {proximo_paso}? Basta con cinco minutos.",
                    "{proximo_paso} sigue guardado para cuando quieras. Sin prisa.",
                    "Un recordatorio amable: {proximo_paso}.",
                    "Si te apetece volver a algo, {proximo_paso} te espera.",
                    "Pequeño empujón: abrir {proximo_paso} ya es empezar.",
                    "El ocio de esta hora ya se usó. ¿Qué tal {proximo_paso}?",
                    "Ya van tus 5 min de esta hora. Tú decides; si te apetece, {proximo_paso}.",
                    "Nada urgente: solo te recuerdo {proximo_paso}.",
                    "Cuando cierres esto, {proximo_paso} está a un toque.",
                    "Un reto corto de {juego} también vale para cambiar de chip.");

    static final List<String> PROPUESTA =
            Arrays.asList(
                    "Se acabaron los 5 min. ¿Seguimos con {proximo_paso}?",
                    "Tiempo de ocio cumplido. Propuesta: {proximo_paso}.",
                    "¿Un reto rápido de {juego}? Dura un par de minutos.",
                    "Ya tuviste tu pausa. Lo siguiente puede ser {proximo_paso}.",
                    "Cambio de plan suave: {juego}, un reto y listo.",
                    "Cinco minutos bien gastados. Ahora, {proximo_paso}.",
                    "Te propongo algo pequeño: {proximo_paso}, solo para empezar.",
                    "Hora de volver. {proximo_paso} está a un toque.",
                    "¿Y si pruebas {juego}? Dos minutos para agilizar la mente.",
                    "Terminó el bloque. Elige: {proximo_paso} o un reto de {juego}.",
                    "Cierra con calma y abre {proximo_paso}.");

    static final List<String> PAUSA =
            Arrays.asList(
                    "50 min de foco. Levántate cinco minutos: agua y estirar.",
                    "Buen tramo. Una pausa de 5 min ayuda a que lo siguiente cueste menos.",
                    "Tus ojos agradecerán mirar lejos un momento. 5 minutos.",
                    "Pausa corta: camina un poco y vuelve a {proximo_paso}.",
                    "Llevas casi una hora. Respira hondo tres veces y estira la espalda.",
                    "5 min sin pantalla. Luego sigues donde lo dejaste.",
                    "Has hecho {min_invertidos} min. Un vaso de agua y vuelves.",
                    "Descansar también es parte del trabajo. Cinco minutos.",
                    "Estira cuello y hombros. En 5 min, a por más.",
                    "Pausa ganada. Nada de redes: mejor ventana o agua.",
                    "Cierra los ojos un minuto. Lo demás puede esperar cinco.");

    static final List<String> CELEBRACION =
            Arrays.asList(
                    "Reto superado. Bien jugado.",
                    "¡Nivel nuevo! Lo has entrenado tú.",
                    "Lo conseguiste: {juego} superado.",
                    "Días cumplidos: un hito más. Gracias por volver a lo tuyo.",
                    "Eso ha salido muy bien.",
                    "Un paso más en {juego}. Se nota la práctica.",
                    "Hecho. Minutos ganados con cabeza.",
                    "Sumaste otro día cumplido. Constancia, no perfección.",
                    "Buen trabajo con {juego}.",
                    "Superado. Puedes seguir o parar aquí, las dos valen.",
                    "Nivel arriba. La próxima vez costará un poco más, y está bien.");

    static final List<String> REFLEXION =
            Arrays.asList(
                    "Hoy: {min_invertidos} min invertidos. Mañana, un poco de lo mismo basta.",
                    "Día cerrado. ¿Qué fue lo mejor de hoy?",
                    "Hoy invertiste {min_invertidos} min. Descansa, mañana sigue.",
                    "Mañana seguimos con calma. Buenas noches.",
                    "Antes de dormir: ¿cuál es tu primer paso de mañana?",
                    "Gracias por cuidar tu tiempo hoy. {min_invertidos} min bien usados.",
                    "Noche tranquila. El ocio se abre a las 7:00.",
                    "Un día más. No hace falta que sea perfecto.",
                    "Hoy llegaste a {min_invertidos} min. Suficiente para sentirte bien.",
                    "Apaga con calma. Mañana, {proximo_paso}.",
                    "El descanso de hoy es el foco de mañana.");

    static final List<String> CUIDADO =
            Arrays.asList(
                    "Llevas un buen rato con el móvil. ¿Una vuelta corta, aunque sea a la ventana?",
                    "¿Te pongo música? Una canción que te guste, sin pantalla, ya cambia el día.",
                    "Si algo te ronda la cabeza, escribirlo en una línea ayuda a soltarlo.",
                    "¿Hace cuánto no le escribes a alguien que te cae bien? Un mensaje corto vale.",
                    "Un vaso de agua y estirar la espalda. Dos minutos, nada más.",
                    "Hoy vamos suave. Si te apetece, sal a caminar diez minutos.",
                    "Respira hondo tres veces. Sin objetivo, solo por ti.",
                    "Un rato sin pantalla también es descanso. ¿Cinco minutos mirando lejos?",
                    "¿Y si llamas a alguien de tu familia? A veces es justo lo que hace falta.",
                    "Escribe tres cosas que salieron bien hoy, aunque sean pequeñas.");

    static final List<String> INQUIETUD =
            Arrays.asList(
                    "¿Todo bien? Parece que buscas algo y no lo encuentras. ¿Respiramos 30 segundos?",
                    "Saltas de una app a otra. No pasa nada; para un momento conmigo.",
                    "Oye, ¿cómo estás de verdad? Respira hondo tres veces y vemos.",
                    "A veces el móvil no tiene lo que buscamos. ¿Agua, ventana o escribirle a alguien?",
                    "Tu cabeza va rápido ahora. 30 segundos de respirar ayudan más que otro vídeo.",
                    "Se nota mucho movimiento. Deja el móvil un minuto; sigo aquí cuando vuelvas.",
                    "¿Aburrimiento, cansancio o algo que te preocupa? Ponerle nombre ya ayuda.",
                    "Pausa pequeña: hombros abajo, respira lento. Luego decides tú.",
                    "Si buscas descansar, descansa de verdad: cierra los ojos un momento.",
                    "Estoy contigo. Elige una sola cosa y quédate en ella un rato.");
}
