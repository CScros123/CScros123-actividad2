package es.foco.next.core;

import java.text.Normalizer;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Bloquear secciones dentro de apps (Shorts, Reels, Spotlight...) sin bloquear la app entera.
 * Lógica pura.
 *
 * <p>Cada sección se reconoce por señales de la pantalla que el servicio de Accesibilidad le pasa:
 * identificadores de vista (resource-id) y descripciones de contenido. Para evitar falsos positivos
 * solo cuentan:
 *
 * <ul>
 *   <li>identificadores del reproductor o visor de la sección (nunca la pestaña ni la estantería
 *       del inicio), o
 *   <li>la pestaña de la sección <b>seleccionada</b> (su etiqueta existe siempre en la barra
 *       inferior; sola no basta).
 * </ul>
 *
 * Las apps cambian estos identificadores con sus actualizaciones: ver docs/SECCIONES.md para añadir
 * uno nuevo.
 */
public final class InAppFilter {
    private InAppFilter() {}

    public enum Mode {
        /** Igual (sin mayúsculas ni tildes). */
        EXACT,
        /** Igual, o la etiqueta seguida de coma o espacio: "Reels, pestaña 3 de 5". */
        LABEL,
        /** Contiene el texto como palabra completa. */
        WORD
    }

    /**
     * Marcador de descripción. {@code selectedOnly}: solo cuenta en un elemento seleccionado
     * (pestaña activa).
     */
    public static final class Marker {
        public final String text;
        public final Mode mode;
        public final boolean selectedOnly;

        Marker(String text, Mode mode, boolean selectedOnly) {
            this.text = fold(text);
            this.mode = mode;
            this.selectedOnly = selectedOnly;
        }

        boolean matches(String raw) {
            String s = fold(raw);
            if (s.isEmpty()) return false;
            switch (mode) {
                case EXACT:
                    return s.equals(text);
                case LABEL:
                    return s.equals(text) || s.startsWith(text + ",") || s.startsWith(text + " ");
                default:
                    return (" " + s.replaceAll("[^\\p{L}\\p{N}]+", " ") + " ")
                            .contains(" " + text + " ");
            }
        }
    }

    public static final class Section {
        public final String id, label, pkg;

        /** Paquetes donde vive la sección ({@link #pkg} y variantes como WhatsApp Business). */
        public final Set<String> pkgs;

        /** Identificadores sin prefijo ("reel_recycler"); se compara con la parte tras ":id/". */
        public final Set<String> viewIds;

        public final List<Marker> markers;

        /**
         * Si true hace falta un identificador Y un marcador (secciones con identificadores
         * genéricos).
         */
        public final boolean needsBoth;

        /**
         * Identificadores débiles: solos nunca cuentan. Si la sección tiene alguno, sus marcadores
         * solo cuentan junto a uno de ellos (pestaña «Novedades» seleccionada + lista de estados).
         * Vacío = comportamiento de siempre.
         */
        public final Set<String> weakIds;

        Section(
                String id,
                String label,
                String pkg,
                String[] viewIds,
                Marker[] markers,
                boolean needsBoth) {
            this(id, label, new String[] {pkg}, viewIds, new String[] {}, markers, needsBoth);
        }

        Section(
                String id,
                String label,
                String[] pkgs,
                String[] viewIds,
                String[] weakIds,
                Marker[] markers,
                boolean needsBoth) {
            this.id = id;
            this.label = label;
            this.pkg = pkgs[0];
            this.pkgs = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(pkgs)));
            this.viewIds = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(viewIds)));
            this.weakIds = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(weakIds)));
            this.markers = Collections.unmodifiableList(Arrays.asList(markers));
            this.needsBoth = needsBoth;
        }

        public boolean in(String p) {
            return p != null && pkgs.contains(p);
        }

        boolean idSeen(Collection<String> ids) {
            return seen(viewIds, ids);
        }

        private static boolean seen(Set<String> want, Collection<String> ids) {
            if (ids != null && !want.isEmpty())
                for (String raw : ids) {
                    String s = shortId(raw);
                    if (s != null && want.contains(s)) return true;
                }
            return false;
        }

        boolean markerSeen(Collection<String> descs, Collection<String> selected) {
            for (Marker m : markers) {
                Collection<String> pool = m.selectedOnly ? selected : descs;
                if (pool != null) for (String d : pool) if (d != null && m.matches(d)) return true;
            }
            return false;
        }

        boolean matches(
                Collection<String> ids, Collection<String> descs, Collection<String> selected) {
            boolean id = idSeen(ids);
            if (needsBoth) return id && markerSeen(descs, selected);
            if (id) return true;
            if (!weakIds.isEmpty() && !seen(weakIds, ids)) return false;
            return markerSeen(descs, selected);
        }
    }

    public static final String YOUTUBE = "com.google.android.youtube",
            INSTAGRAM = "com.instagram.android",
            FACEBOOK = "com.facebook.katana",
            SNAPCHAT = "com.snapchat.android";
    public static final String YOUTUBE_SHORTS = "youtube_shorts",
            INSTAGRAM_REELS = "instagram_reels",
            FACEBOOK_REELS = "facebook_reels",
            SNAPCHAT_SPOTLIGHT = "snapchat_spotlight",
            YOUTUBE_COMMENTS = "youtube_comments";
    public static final String WHATSAPP = "com.whatsapp",
            WHATSAPP_BUSINESS = "com.whatsapp.w4b",
            WHATSAPP_STATUS = "whatsapp_status",
            WHATSAPP_CHANNELS = "whatsapp_channels";

    /** Las del interruptor rápido «Bloquear Estados y canales de WhatsApp». */
    public static final List<String> WHATSAPP_SECTIONS =
            Collections.unmodifiableList(Arrays.asList(WHATSAPP_STATUS, WHATSAPP_CHANNELS));

    public static final String QUICK_WHATSAPP_NAME = "Sin Estados ni canales";

    /**
     * Apps esenciales (nunca se bloquean enteras) en las que sí se aplican secciones: el servicio
     * salta el bloqueo normal pero sigue revisando la sección. Chats, llamadas y comunidades nunca
     * coinciden con ninguna sección.
     */
    public static final Set<String> ESSENTIAL_WITH_SECTIONS =
            Collections.unmodifiableSet(
                    new LinkedHashSet<>(Arrays.asList(WHATSAPP, WHATSAPP_BUSINESS)));

    public static boolean sectionsInEssential(String pkg) {
        return pkg != null && ESSENTIAL_WITH_SECTIONS.contains(pkg);
    }

    /** Las del interruptor rápido «Bloquear Shorts y Reels». */
    public static final List<String> SHORT_VIDEO =
            Collections.unmodifiableList(
                    Arrays.asList(YOUTUBE_SHORTS, INSTAGRAM_REELS, FACEBOOK_REELS));

    public static final String QUICK_RULE_NAME = "Sin Shorts ni Reels";

    private static Marker sel(String t) {
        return new Marker(t, Mode.LABEL, true);
    }

    /** TikTok no está: toda la app es el feed, se bloquea con un bloqueo normal de apps. */
    public static final List<Section> CATALOG =
            Collections.unmodifiableList(
                    Arrays.asList(
                            new Section(
                                    YOUTUBE_SHORTS,
                                    "Shorts de YouTube",
                                    YOUTUBE,
                                    new String[] {
                                        "reel_recycler",
                                        "reel_player_page_container",
                                        "reel_watch_player",
                                        "reel_watch_fragment_root",
                                        "shorts_container"
                                    },
                                    new Marker[] {sel("Shorts")},
                                    false),
                            new Section(
                                    INSTAGRAM_REELS,
                                    "Reels de Instagram",
                                    INSTAGRAM,
                                    new String[] {
                                        "clips_viewer_view_pager",
                                        "clips_video_container",
                                        "clips_viewer_container"
                                    },
                                    new Marker[] {sel("Reels")},
                                    false),
                            new Section(
                                    FACEBOOK_REELS,
                                    "Reels y Watch de Facebook",
                                    FACEBOOK,
                                    new String[] {},
                                    new Marker[] {
                                        sel("Reels"), sel("Watch"), sel("Video"), sel("Vídeo")
                                    },
                                    false),
                            new Section(
                                    SNAPCHAT_SPOTLIGHT,
                                    "Spotlight de Snapchat",
                                    SNAPCHAT,
                                    new String[] {},
                                    new Marker[] {sel("Spotlight")},
                                    false),
                            new Section(
                                    YOUTUBE_COMMENTS,
                                    "Comentarios de YouTube",
                                    YOUTUBE,
                                    new String[] {"engagement_panel"},
                                    new Marker[] {
                                        new Marker("Comentarios", Mode.LABEL, false),
                                        new Marker("Comments", Mode.LABEL, false)
                                    },
                                    true),
                            // WhatsApp: conservador. Solo el visor de estados (identificador
                            // propio) o la pestaña «Novedades» seleccionada
                            // junto a la lista de estados. «progress_bar» también existe en los
                            // chats (descargas): por eso es débil.
                            new Section(
                                    WHATSAPP_STATUS,
                                    "Estados de WhatsApp",
                                    new String[] {WHATSAPP, WHATSAPP_BUSINESS},
                                    new String[] {
                                        "status_playback",
                                        "status_playback_page",
                                        "status_playback_fragment",
                                        "status_playback_container",
                                        "status_viewer",
                                        "status_viewer_container"
                                    },
                                    new String[] {
                                        "progress_bar",
                                        "status_list",
                                        "statuses_list",
                                        "status_row",
                                        "my_status",
                                        "my_status_row",
                                        "status_tile"
                                    },
                                    new Marker[] {
                                        sel("Novedades"),
                                        sel("Updates"),
                                        sel("Estados"),
                                        sel("Status")
                                    },
                                    false),
                            // Canales (internamente «newsletter»): la conversación de un canal o el
                            // directorio, o «Novedades» con filas de canal.
                            new Section(
                                    WHATSAPP_CHANNELS,
                                    "Canales de WhatsApp",
                                    new String[] {WHATSAPP, WHATSAPP_BUSINESS},
                                    new String[] {
                                        "newsletter_directory",
                                        "newsletter_header",
                                        "newsletter_conversation",
                                        "newsletter_messages",
                                        "channel_directory",
                                        "channel_header"
                                    },
                                    new String[] {
                                        "newsletter_row",
                                        "newsletter_list",
                                        "channel_row",
                                        "channels_list",
                                        "channels_section"
                                    },
                                    new Marker[] {
                                        sel("Novedades"),
                                        sel("Updates"),
                                        sel("Canales"),
                                        sel("Channels")
                                    },
                                    false)));

    public static Section section(String id) {
        for (Section s : CATALOG) if (s.id.equals(id)) return s;
        return null;
    }

    public static boolean known(String id) {
        return section(id) != null;
    }

    public static String label(String id) {
        Section s = section(id);
        return s == null ? id : s.label;
    }

    /** Nombre corto para avisos: "Shorts", "Reels", "Spotlight"... */
    public static String noun(String id) {
        String l = label(id);
        int i = l.indexOf(" de ");
        return i > 0 ? l.substring(0, i) : l;
    }

    /** Hay alguna sección del catálogo en esta app. */
    public static boolean supported(String pkg) {
        if (pkg == null) return false;
        for (Section s : CATALOG) if (s.in(pkg)) return true;
        return false;
    }

    /**
     * {@link #detect(String,Collection,Collection,Collection,Collection)} con todas las secciones y
     * sin estado de selección.
     */
    public static String detect(
            String pkg, Collection<String> viewIds, Collection<String> descriptions) {
        return detect(pkg, viewIds, descriptions, null, null);
    }

    /**
     * Primera sección de {@code pkg} cuya señal aparece en pantalla, o null.
     *
     * @param selected descripciones de los elementos seleccionados (pestañas activas)
     * @param only secciones a considerar (null = todas)
     */
    public static String detect(
            String pkg,
            Collection<String> viewIds,
            Collection<String> descriptions,
            Collection<String> selected,
            Collection<String> only) {
        if (pkg == null) return null;
        for (Section s : CATALOG) {
            if (!s.in(pkg) || only != null && !only.contains(s.id)) continue;
            if (s.matches(viewIds, descriptions, selected)) return s.id;
        }
        return null;
    }

    /**
     * Las secciones de la regla se aplican: activada, sin pausa y (sin horario o dentro de su
     * franja). Los límites no cuentan.
     */
    public static boolean active(BlockRule r, RuleState s, ZonedDateTime now) {
        if (r == null || !r.enabled || r.sections.isEmpty()) return false;
        RuleState.Entry e = s.of(r.id);
        RuleEngine.roll(r, e, RuleEngine.millis(now), now.toLocalDate().toString());
        if (RuleEngine.paused(e, RuleEngine.millis(now))) return false;
        return RuleEngine.inWindow(r, now);
    }

    /** Secciones de {@code pkg} bloqueadas ahora por alguna regla activa (vacío = no leer nada). */
    public static Set<String> wanted(
            List<BlockRule> rules, RuleState s, ZonedDateTime now, String pkg) {
        Set<String> out = new LinkedHashSet<>();
        if (!supported(pkg)) return out;
        for (BlockRule r : rules)
            if (active(r, s, now))
                for (String id : r.sections) {
                    Section x = section(id);
                    if (x != null && x.in(pkg)) out.add(id);
                }
        return out;
    }

    /** Primera regla activa que bloquea esa sección, o null. */
    public static BlockRule blocker(
            List<BlockRule> rules, RuleState s, ZonedDateTime now, String sectionId) {
        for (BlockRule r : rules) if (r.sections.contains(sectionId) && active(r, s, now)) return r;
        return null;
    }

    /**
     * El interruptor rápido está encendido: la regla existe, está activada, sin horario y con las
     * tres secciones.
     */
    public static boolean quickOn(BlockRule r) {
        return quickOn(r, SHORT_VIDEO);
    }

    /** Igual con otro grupo de secciones (Estados y canales de WhatsApp). */
    public static boolean quickOn(BlockRule r, List<String> ids) {
        return r != null && r.enabled && !r.scheduled() && r.sections.containsAll(ids);
    }

    /**
     * Regla del interruptor rápido tras pulsarlo. Encender: crea «Sin Shorts ni Reels» (o completa
     * la existente) activada, sin horario y con Shorts, Reels de Instagram y Reels de Facebook.
     * Apagar: la desactiva (quien llama comprueba el modo estricto con {@link RuleChange}).
     */
    public static BlockRule quickToggle(BlockRule existing, boolean on, String newId) {
        return quickToggle(existing, on, newId, QUICK_RULE_NAME, SHORT_VIDEO);
    }

    /**
     * Interruptor rápido «Bloquear Estados y canales de WhatsApp»: bloqueo «Sin Estados ni
     * canales», sin apps ni horario.
     */
    public static BlockRule quickWhatsApp(BlockRule existing, boolean on, String newId) {
        return quickToggle(existing, on, newId, QUICK_WHATSAPP_NAME, WHATSAPP_SECTIONS);
    }

    static BlockRule quickToggle(
            BlockRule existing, boolean on, String newId, String name, List<String> ids) {
        BlockRule r = existing == null ? new BlockRule() : existing.copy();
        if (existing == null) {
            r.id = newId;
            r.name = name;
            r.limitMinutes = 0;
            r.maxPauses = 1;
            r.pauseMinutes = 5;
        }
        if (on) {
            r.enabled = true;
            r.scheduleMode = BlockRule.ScheduleMode.ALWAYS_LIMIT;
            r.schedules.clear();
            for (String id : ids) if (!r.sections.contains(id)) r.sections.add(id);
        } else r.enabled = false;
        r.clamp();
        return r;
    }

    /** Quita nulos, repetidos e identificadores desconocidos. */
    static void clean(List<String> sections) {
        LinkedHashSet<String> a = new LinkedHashSet<>();
        for (String x : sections) if (x != null && known(x.trim())) a.add(x.trim());
        sections.clear();
        sections.addAll(a);
    }

    /** "com.google.android.youtube:id/reel_recycler" → "reel_recycler". */
    static String shortId(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        int i = s.indexOf(":id/");
        if (i >= 0) s = s.substring(i + 4);
        return s.isEmpty() ? null : s;
    }

    static String fold(String s) {
        if (s == null) return "";
        String n =
                Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}+", "");
        return n.replaceAll("\\s+", " ");
    }
}
