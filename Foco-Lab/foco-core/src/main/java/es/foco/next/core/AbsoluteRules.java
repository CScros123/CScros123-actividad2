package es.foco.next.core;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * REGLAS ABSOLUTAS. Viven solo aquí, como constantes: no se leen de preferencias, base de datos,
 * copia JSON ni pantalla alguna. Para cambiarlas hay que sacar una versión nueva de Foco. Lógica
 * pura y sin estado: tras un reinicio, el resultado es el mismo.
 *
 * <ol>
 *   <li>Redes sociales fijas en ocio (apps y dominios).
 *   <li>Noche absoluta de 21:00 a 07:00: sin ocio, sin bloque gratis, sin gastar ni ganar minutos.
 *   <li>Hueco de configuración: 30 días de memoria del ocio y 24 horas para sacar una app de ocio.
 *   <li>(v26) Sin banco de minutos: el ocio de Enfoque y del Weekend Lock lo abre Foco Core. Antes:
 *       retos según nivel, caída por repetición, misiones a 0, +10 solo por nivel nuevo.
 * </ol>
 */
public final class AbsoluteRules {
    private AbsoluteRules() {}

    // ---------- Regla 1: redes sociales ----------
    public static final Set<String> SOCIAL_PACKAGES =
            Collections.unmodifiableSet(
                    new LinkedHashSet<>(
                            Arrays.asList(
                                    "com.zhiliaoapp.musically",
                                    "com.ss.android.ugc.trill", // TikTok (global y Asia)
                                    "com.instagram.android",
                                    "com.instagram.barcelona", // Instagram y Threads
                                    "com.facebook.katana",
                                    "com.facebook.lite",
                                    "com.twitter.android", // X
                                    "com.reddit.frontpage",
                                    "com.pinterest",
                                    "com.snapchat.android",
                                    "com.google.android.youtube",
                                    "com.kwai.video",
                                    "xyz.blueskyweb.app")));
    public static final List<String> SOCIAL_DOMAINS =
            Collections.unmodifiableList(
                    Arrays.asList(
                            "tiktok.com",
                            "tiktokv.com",
                            "tiktokcdn.com",
                            "instagram.com",
                            "cdninstagram.com",
                            "facebook.com",
                            "fb.com",
                            "fbcdn.net",
                            "threads.net",
                            "threads.com",
                            "x.com",
                            "twitter.com",
                            "twimg.com",
                            "reddit.com",
                            "redd.it",
                            "pinterest.com",
                            "pinimg.com",
                            "snapchat.com",
                            "youtube.com",
                            "youtu.be",
                            "ytimg.com"));

    /** App de la Regla 1: siempre ocio, nunca esencial, productiva, en observación ni excluida. */
    public static boolean social(String pkg) {
        return pkg != null && SOCIAL_PACKAGES.contains(pkg);
    }

    /**
     * Dirección de la barra del navegador que es un dominio de la Regla 1 o un subdominio suyo:
     * bloqueada siempre.
     */
    public static boolean socialDomain(String url) {
        return Categories.matches(url, SOCIAL_DOMAINS);
    }

    /**
     * Aplica la Regla 1 sobre un plan: las redes sociales entran en ocio y salen de cualquier otra
     * lista. Devuelve qué había que corregir (vacío si el plan ya la cumplía), para registrar un
     * incidente cuando viene de una copia importada.
     */
    public static List<String> enforce(AppConfig c) {
        List<String> fixed = new ArrayList<>();
        for (String p : SOCIAL_PACKAGES) {
            if (c.watchedApps.add(p)) fixed.add(p + ":faltaba_en_ocio");
            if (c.observedApps.remove(p)) fixed.add(p + ":en_observacion");
            if (c.productiveApps.remove(p)) fixed.add(p + ":en_productivas");
            if (c.sensitiveApps.remove(p)) fixed.add(p + ":en_esenciales");
        }
        if (social(c.returnApp)) {
            fixed.add(c.returnApp + ":como_alternativa");
            c.returnApp = "";
        }
        return fixed;
    }

    // ---------- Regla 2: noche absoluta ----------
    public static final int NIGHT_START = 21, NIGHT_END = 7;

    /**
     * Modos de cuidado: el horario de la noche se puede ajustar
     * (es.foco.next.core.care.NightHours). Endurecer se aplica al instante y aflojar al día
     * siguiente; quien lo decide llama a {@link #useNight}. Por defecto, 21:00 a 07:00.
     */
    private static volatile int nightStart = NIGHT_START, nightEnd = NIGHT_END;

    public static void useNight(int start, int end) {
        if (!es.foco.next.core.care.NightHours.valid(start, end)) return;
        nightStart = start;
        nightEnd = end;
    }

    /**
     * v32 (R13): de dónde sale el horario de la noche que vale hoy. La app instala una fuente que
     * lo lee de sus preferencias guardadas en cada consulta; así nadie depende de que antes alguien
     * haya llamado a {@link #useNight} en este proceso. Sin fuente (pruebas), vale lo de useNight.
     */
    public interface NightSource {
        /** {inicio, fin} de la noche de hoy. */
        int[] hours();
    }

    private static volatile NightSource source;

    public static void setNightSource(NightSource s) {
        source = s;
    }

    public static boolean hasNightSource() {
        return source != null;
    }

    private static int[] hours() {
        NightSource s = source;
        if (s != null)
            try {
                int[] h = s.hours();
                if (h != null
                        && h.length == 2
                        && es.foco.next.core.care.NightHours.valid(h[0], h[1])) return h;
            } catch (RuntimeException ignored) {
            }
        return new int[] {nightStart, nightEnd};
    }

    public static int nightStart() {
        return hours()[0];
    }

    public static int nightEnd() {
        return hours()[1];
    }

    /** De la hora de inicio (incluida) a la de fin (excluida), todos los días. */
    public static boolean esNocheAbsoluta(LocalDateTime ahora) {
        int[] n = hours();
        int h = ahora.getHour();
        return h >= n[0] || h < n[1];
    }

    /**
     * Con la hora automática del sistema apagada la hora no es fiable: para el ocio, siempre es de
     * noche.
     */
    public static boolean night(LocalDateTime ahora, boolean autoTime) {
        return !autoTime || esNocheAbsoluta(ahora);
    }

    // ---------- Regla 3: hueco de configuración ----------
    public static final int LEISURE_MEMORY_DAYS = 30;
    public static final long LEAVE_DELAY_MS = 24 * 60 * 60_000L;

    /**
     * La app estuvo en ocio en los últimos 30 días ({@code lastDay}: último día en ocio, ISO; vacío
     * si nunca).
     */
    public static boolean recentLeisure(String lastDay, LocalDate today) {
        if (lastDay == null || lastDay.isEmpty()) return false;
        try {
            return !LocalDate.parse(lastDay).isBefore(today.minusDays(LEISURE_MEMORY_DAYS));
        } catch (RuntimeException e) {
            return true;
        }
    }

    /** Pedir que una app salga de ocio: nunca de noche ni durante el Weekend Lock. */
    public static boolean canRequestLeave(
            LocalDateTime ahora, boolean autoTime, boolean weekendLock) {
        return !night(ahora, autoTime) && !weekendLock;
    }

    /**
     * Pasaron 24 horas desde la petición. En el mismo arranque cuenta el tiempo real transcurrido
     * (adelantar el reloj no sirve); tras un reinicio, el reloj, y solo si la hora automática está
     * encendida.
     */
    public static boolean leaveDue(
            long askedWall,
            long askedElapsed,
            int askedBoot,
            long wall,
            long elapsed,
            int boot,
            boolean autoTime) {
        if (askedBoot >= 0 && askedBoot == boot && elapsed >= askedElapsed)
            return elapsed - askedElapsed >= LEAVE_DELAY_MS;
        return autoTime && wall - askedWall >= LEAVE_DELAY_MS;
    }

    // ---------- Corrección A: observación ----------
    /**
     * Valores de android.content.pm.ApplicationInfo.CATEGORY_* (copiados para que el núcleo no
     * dependa de Android).
     */
    public static final int CATEGORY_GAME = 0,
            CATEGORY_AUDIO = 1,
            CATEGORY_VIDEO = 2,
            CATEGORY_SOCIAL = 4;

    /**
     * Una app nueva se limita desde el primer día solo si Android la declara juego, vídeo o red
     * social.
     */
    public static boolean limitInObservation(int category) {
        return category == CATEGORY_GAME
                || category == CATEGORY_VIDEO
                || category == CATEGORY_SOCIAL;
    }

    /** Corrección B: música y audio son neutrales (ni ocio ni productivas, no ganan minutos). */
    public static boolean neutral(int category) {
        return category == CATEGORY_AUDIO;
    }

    /** Herramientas que se limitaron por error el 17/11: salen de observación. */
    public static final Set<String> RELEASED_FROM_OBSERVATION =
            Collections.unmodifiableSet(
                    new LinkedHashSet<>(
                            Arrays.asList(
                                    "com.google.android.apps.docs.editors.docs",
                                    "com.google.android.apps.nbu.files",
                                    "org.localsend.localsend_app",
                                    "com.xstudios.cleanx")));
}
