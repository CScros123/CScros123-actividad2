package es.foco.next.ui;

import android.app.AlertDialog;
import android.widget.*;
import es.foco.next.R;
import es.foco.next.core.*;
import es.foco.next.data.RuleStore;
import es.foco.next.runtime.FocusAccessibilityService;
import es.foco.next.runtime.StrictAdmin;
import java.util.*;

/**
 * Lote 1: pantalla "Bloqueos" (desde Hoy). El Foco clásico aparece como tarjeta fija, no editable
 * aquí.
 */
final class BlocksScreen {
    private BlocksScreen() {}

    private static final int[] PAUSE_LENGTHS = {5, 10, 15};

    static void render(MainActivity a, LinearLayout body) {
        Design.note(
                body,
                "Cada bloqueo tiene sus apps, su límite y sus pausas. Si una app está en varios, basta con que uno la bloquee.");
        RuleStore store = RuleStore.get(a);
        if (store.unreadable())
            Design.note(
                    body,
                    "No se pudieron leer tus bloqueos anteriores. El archivo se conserva aparte en el teléfono; puedes restaurar una copia o crearlos de nuevo.");
        session(a, body, store);
        AppConfig c = a.repository.config();
        LinearLayout classic = Design.card(body, "Foco clásico");
        Design.note(
                classic,
                "5 minutos de ocio cada 60 minutos, compartidos entre "
                        + c.watchedApps.size()
                        + " apps. Sin pausas. Se ajusta en Mi plan y no se puede eliminar.");
        long left = a.repository.remaining();
        Design.note(
                classic,
                !c.enabled
                        ? "Desactivado"
                        : a.repository.locked()
                                ? "Límite alcanzado · "
                                        + FocusOverlay.until(left).toLowerCase(Locale.ROOT)
                                : "Activo");
        shortcuts(a, body, store);
        LinearLayout suggestions = Design.column(a);
        body.addView(suggestions, new LinearLayout.LayoutParams(-1, -2));
        SuggestionsCard.load(
                a, suggestions, a::render); // 1.9: «Sugerencias para ti», solo si hay alguna
        for (BlockRule r : store.rules()) {
            LinearLayout card = Design.card(body, r.name);
            Design.note(
                    card,
                    (r.allowlist
                                    ? "Lista blanca: todo salvo "
                                            + r.packages.size()
                                            + (r.packages.size() == 1 ? " app" : " apps")
                                    : r.packages.size()
                                            + (r.packages.size() == 1 ? " app" : " apps"))
                            + " · "
                            + describe(r));
            Design.note(card, state(store, r));
            card.addView(Design.button(a, "Editar", false, () -> edit(a, r)));
            card.addView(Design.button(a, "Duplicar", false, () -> duplicate(a, r)));
        }
        body.addView(Design.button(a, "Nuevo bloqueo", true, () -> edit(a, null)));
        backup(a, body, store);
        if (!FocusAccessibilityService.connected())
            Design.note(body, "Activa Accesibilidad para que los bloqueos midan y bloqueen.");
    }

    static String describe(BlockRule r) {
        String web =
                r.sites.size() + r.keywords.size() == 0
                        ? ""
                        : " · web: "
                                + r.sites.size()
                                + (r.sites.size() == 1 ? " sitio" : " sitios")
                                + ", "
                                + r.keywords.size()
                                + (r.keywords.size() == 1 ? " palabra" : " palabras");
        if (r.blockUnsupportedBrowsers) web += " · otros navegadores bloqueados";
        if (r.blockPurchases) web += " · compras bloqueadas";
        if (!r.sections.isEmpty()) {
            StringBuilder b = new StringBuilder();
            for (String id : r.sections) {
                if (b.length() > 0) b.append(", ");
                b.append(InAppFilter.label(id));
            }
            web += " · sin " + b;
        }
        return base(r) + web;
    }

    private static String base(BlockRule r) {
        String limit = r.limitMinutes == 0 ? "bloqueo total" : r.limitMinutes + " min";
        if (r.scheduled() && r.scheduleMode == BlockRule.ScheduleMode.BLOCK_IN_WINDOW)
            return "bloqueo total "
                    + windows(r)
                    + " · "
                    + r.maxPauses
                    + (r.maxPauses == 1 ? " pausa" : " pausas")
                    + " de "
                    + r.pauseMinutes
                    + " min";
        if (r.scheduled()) limit = windows(r) + ": " + limit;
        return limit
                + (r.period == BlockRule.Period.WINDOW
                        ? " cada " + r.windowMinutes + " min"
                        : " al día")
                + " · "
                + r.maxPauses
                + (r.maxPauses == 1 ? " pausa" : " pausas")
                + " de "
                + r.pauseMinutes
                + " min";
    }

    private static String windows(BlockRule r) {
        StringBuilder b = new StringBuilder();
        for (Schedule x : r.schedules) {
            if (b.length() > 0) b.append(", ");
            b.append(x.describe());
        }
        return b.toString();
    }

    private static String state(RuleStore store, BlockRule r) {
        if (!r.enabled) return "Desactivado";
        RuleState.Entry e = store.entry(r.id);
        long now = System.currentTimeMillis();
        String pauses = " · pausas hoy " + e.pausesUsed + "/" + r.maxPauses;
        if (RuleEngine.paused(e, now))
            return "En pausa · " + ((e.pausedUntil - now + 59_999) / 60_000) + " min" + pauses;
        java.time.ZonedDateTime local = java.time.ZonedDateTime.now();
        if (r.scheduled() && !RuleEngine.inWindow(r, local)) return "Fuera de horario" + pauses;
        if (r.scheduled()
                && store.active(r.id)
                && r.scheduleMode == BlockRule.ScheduleMode.BLOCK_IN_WINDOW)
            return "En horario · termina en "
                    + FocusOverlay.span(RuleEngine.windowEndsIn(r, local))
                    + pauses;
        if (store.active(r.id)) {
            java.time.ZonedDateTime z = java.time.ZonedDateTime.now();
            long today = now - z.toLocalDate().atStartOfDay(z.getZone()).toInstant().toEpochMilli();
            return "Límite alcanzado · "
                    + FocusOverlay.until(RuleEngine.resetsIn(r, e, now, today))
                            .toLowerCase(Locale.ROOT)
                    + pauses;
        }
        long left = Math.max(0, r.limitMillis() - e.usedMillis);
        return "Activo · quedan " + ((left + 59_999) / 60_000) + " min" + pauses;
    }

    private static void edit(MainActivity a, BlockRule original) {
        RuleStore store = RuleStore.get(a);
        // Modo estricto reforzado: con el candado se puede editar siempre, pero solo para hacerlo
        // más estricto (RuleChange).
        boolean strictLock = StrictAdmin.lockActive(a);
        if (original != null
                && !strictLock
                && StrictAdmin.enabled(a)
                && store.active(original.id)) {
            a.message(
                    "Modo estricto: este bloqueo está activo. Podrás editarlo cuando se reinicie.");
            return;
        }
        if (original != null
                && !strictLock
                && StrictAdmin.enabled(a)
                && store.focusing(System.currentTimeMillis())) {
            a.message(
                    "Modo estricto: hay una sesión de foco en curso. Podrás editar los bloqueos cuando termine.");
            return;
        }
        BlockRule r = original == null ? new BlockRule() : original.copy();
        if (original == null) {
            r.name = "";
            r.limitMinutes = 15;
            r.period = BlockRule.Period.DAILY;
            r.maxPauses = 1;
            r.pauseMinutes = 5;
        }
        ScrollView scroll = new ScrollView(a);
        LinearLayout box = Design.column(a);
        int pad = Design.dp(a, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        scroll.addView(box);
        EditText name = Design.field(a, "Nombre (por ejemplo, Redes)", r.name, false);
        box.addView(name);
        Design.gap(box, 8);
        Switch allow = new Switch(a);
        allow.setText("Lista blanca: bloquear todas las apps salvo estas");
        allow.setChecked(r.allowlist);
        allow.setTextColor(a.getColor(R.color.text_primary));
        box.addView(allow);
        Button apps = Design.button(a, "", false, null);
        Runnable appsLabel =
                () ->
                        apps.setText(
                                (allow.isChecked() ? "Apps permitidas: " : "Apps: ")
                                        + r.packages.size()
                                        + " · elegir");
        appsLabel.run();
        allow.setOnCheckedChangeListener((v, on) -> appsLabel.run());
        apps.setOnClickListener(
                v ->
                        AppPicker.many(
                                a,
                                new LinkedHashSet<>(r.packages),
                                chosen -> {
                                    r.packages.clear();
                                    r.packages.addAll(chosen);
                                    appsLabel.run();
                                }));
        box.addView(apps);
        Design.note(box, "Minutos permitidos (0 = bloqueo total)");
        EditText limit = Design.field(a, "Minutos", String.valueOf(r.limitMinutes), true);
        box.addView(limit);
        Button period = Design.button(a, "", false, null);
        Runnable periodLabel =
                () ->
                        period.setText(
                                r.period == BlockRule.Period.WINDOW
                                        ? "Se reinicia cada 60 min"
                                        : "Se reinicia cada día");
        periodLabel.run();
        period.setOnClickListener(
                v -> {
                    r.period =
                            r.period == BlockRule.Period.WINDOW
                                    ? BlockRule.Period.DAILY
                                    : BlockRule.Period.WINDOW;
                    r.windowMinutes = 60;
                    periodLabel.run();
                });
        box.addView(period);
        Button pauses = Design.button(a, "", false, null);
        Runnable pausesLabel = () -> pauses.setText("Pausas al día: " + r.maxPauses);
        pausesLabel.run();
        pauses.setOnClickListener(
                v -> {
                    r.maxPauses = (r.maxPauses + 1) % (BlockRule.MAX_PAUSES + 1);
                    pausesLabel.run();
                });
        box.addView(pauses);
        Button length = Design.button(a, "", false, null);
        Runnable lengthLabel =
                () -> length.setText("Duración de cada pausa: " + r.pauseMinutes + " min");
        lengthLabel.run();
        length.setOnClickListener(
                v -> {
                    int i = 0;
                    while (i < PAUSE_LENGTHS.length && PAUSE_LENGTHS[i] <= r.pauseMinutes) i++;
                    r.pauseMinutes = PAUSE_LENGTHS[i % PAUSE_LENGTHS.length];
                    lengthLabel.run();
                });
        box.addView(length);
        schedule(a, box, r);
        Switch browsersSwitch = web(a, box, r);
        Switch buys = new Switch(a);
        buys.setText("Bloquear compras dentro de apps (Google Play)");
        buys.setChecked(r.blockPurchases);
        buys.setTextColor(a.getColor(R.color.text_primary));
        box.addView(buys);
        Design.note(
                box,
                "Cierra la hoja de pago de Google Play mientras el bloqueo esté activado (o en su horario). Es un mejor esfuerzo: no cubre pagos con tarjeta dentro de la propia app.");
        sections(a, box, r);
        Switch enabled = new Switch(a);
        enabled.setText("Bloqueo activado");
        enabled.setChecked(r.enabled);
        enabled.setTextColor(a.getColor(R.color.text_primary));
        box.addView(enabled);
        Switch auto = new Switch(a);
        auto.setText("Añadir apps nuevas automáticamente");
        auto.setChecked(r.autoAddNew);
        auto.setTextColor(a.getColor(R.color.text_primary));
        box.addView(auto);
        Design.note(
                box,
                "Las apps nuevas se añaden al instalarlas, salvo esenciales, bancos y mensajería. Las apps esenciales nunca se bloquean.");
        AlertDialog.Builder b =
                new AlertDialog.Builder(a)
                        .setTitle(original == null ? "Nuevo bloqueo" : "Editar bloqueo")
                        .setView(scroll)
                        .setPositiveButton(
                                R.string.save,
                                (d, w) -> {
                                    r.name = name.getText().toString();
                                    r.enabled = enabled.isChecked();
                                    r.autoAddNew = auto.isChecked();
                                    r.allowlist = allow.isChecked();
                                    r.blockUnsupportedBrowsers = browsersSwitch.isChecked();
                                    r.blockPurchases = buys.isChecked();
                                    if (r.scheduleMode == BlockRule.ScheduleMode.ALWAYS_LIMIT)
                                        r.schedules.clear();
                                    try {
                                        r.limitMinutes =
                                                Integer.parseInt(limit.getText().toString().trim());
                                    } catch (NumberFormatException e) {
                                    }
                                    AppConfig c = a.repository.config();
                                    r.packages.removeIf(p -> FocusEngine.essential(c, p));
                                    if (StrictAdmin.lockActive(a)) {
                                        String why = RuleChange.reason(original, r);
                                        if (why != null) {
                                            a.message(
                                                    "Modo estricto (solo más estricto): "
                                                            + why
                                                            + ".");
                                            return;
                                        }
                                    }
                                    try {
                                        store.save(r);
                                        FocusAccessibilityService.rulesChanged();
                                        a.render();
                                    } catch (RuntimeException e) {
                                        a.message(e.getMessage());
                                    }
                                })
                        .setNegativeButton(R.string.cancel, null);
        if (original != null)
            b.setNeutralButton(
                    "Eliminar",
                    (d, w) ->
                            new AlertDialog.Builder(a)
                                    .setTitle("¿Eliminar «" + original.name + "»?")
                                    .setPositiveButton(
                                            "Eliminar",
                                            (d2, w2) -> {
                                                if (StrictAdmin.lockActive(a)) {
                                                    String why = RuleChange.reason(original, null);
                                                    if (why != null) {
                                                        a.message(
                                                                "Modo estricto (solo más estricto): "
                                                                        + why
                                                                        + ".");
                                                        return;
                                                    }
                                                }
                                                try {
                                                    store.delete(original.id);
                                                    FocusAccessibilityService.rulesChanged();
                                                    a.render();
                                                } catch (RuntimeException e) {
                                                    a.message(e.getMessage());
                                                }
                                            })
                                    .setNegativeButton(R.string.cancel, null)
                                    .show());
        b.show();
    }

    // Secciones dentro de apps: casillas en el editor. Como el filtro web: activas siempre (o en su
    // horario), sin minutos.
    private static void sections(MainActivity a, LinearLayout box, BlockRule r) {
        Design.note(
                box,
                "Secciones dentro de apps: Foco vuelve atrás al entrar en ellas, sin bloquear la app entera. Se aplican siempre que el bloqueo esté activado, o solo en su horario; una pausa las levanta.");
        for (InAppFilter.Section x : InAppFilter.CATALOG) {
            CheckBox cb = new CheckBox(a);
            cb.setText(x.label);
            cb.setChecked(r.sections.contains(x.id));
            cb.setTextColor(a.getColor(R.color.text_primary));
            cb.setOnCheckedChangeListener(
                    (v, on) -> {
                        if (on) {
                            if (!r.sections.contains(x.id)) r.sections.add(x.id);
                        } else r.sections.remove(x.id);
                    });
            box.addView(cb);
        }
    }

    // 1.9: una sola tarjeta «Atajos» con los tres interruptores rápidos (antes: tarjetas «Shorts y
    // Reels» y «TikTok»).
    // Cada interruptor sigue creando o completando su propio bloqueo con nombre fijo; con el
    // candado solo se puede encender.
    private static void shortcuts(MainActivity a, LinearLayout body, RuleStore store) {
        LinearLayout card = Design.card(body, "Atajos");
        Design.note(
                card,
                "Un toque para lo que más engancha. Cada atajo es un bloqueo normal que luego aparece abajo.");
        quick(
                a,
                card,
                "Bloquear Shorts y Reels",
                InAppFilter.quickOn(store.quickSections()),
                "YouTube, Instagram y Facebook siguen abriéndose; Foco solo vuelve atrás al entrar en Shorts o Reels.",
                on -> {
                    BlockRule b = store.quickSections();
                    return new BlockRule[] {
                        b, InAppFilter.quickToggle(b, on, UUID.randomUUID().toString())
                    };
                },
                on -> on ? "Shorts y Reels bloqueados" : "Bloqueo de Shorts y Reels desactivado");
        quick(
                a,
                card,
                "Bloquear TikTok por completo",
                TikTokBlock.on(store.quickTikTok()),
                "Sin minutos de ocio ni pausas: TikTok queda bloqueado siempre. Las demás apps siguen con tus límites.",
                on -> {
                    BlockRule b = store.quickTikTok();
                    return new BlockRule[] {
                        b, TikTokBlock.toggle(b, on, UUID.randomUUID().toString())
                    };
                },
                on -> on ? "TikTok bloqueado por completo" : "Bloqueo total de TikTok desactivado");
        quick(
                a,
                card,
                "Bloquear Estados y canales de WhatsApp",
                InAppFilter.quickOn(store.quickWhatsApp(), InAppFilter.WHATSAPP_SECTIONS),
                "Chats, llamadas y comunidades siguen igual; Foco solo vuelve atrás al abrir un estado o un canal. Lee identificadores de pantalla, nunca tus mensajes, y no guarda nada.",
                on -> {
                    BlockRule b = store.quickWhatsApp();
                    return new BlockRule[] {
                        b, InAppFilter.quickWhatsApp(b, on, UUID.randomUUID().toString())
                    };
                },
                on ->
                        on
                                ? "Estados y canales de WhatsApp bloqueados"
                                : "Bloqueo de Estados y canales desactivado");
        Design.note(
                card,
                "Los identificadores de pantalla pueden cambiar con las actualizaciones de esas apps.");
    }

    private static void quick(
            MainActivity a,
            LinearLayout card,
            String label,
            boolean checked,
            String note,
            java.util.function.Function<Boolean, BlockRule[]> change,
            java.util.function.Function<Boolean, String> done) {
        Switch sw = new Switch(a);
        sw.setText(label);
        sw.setChecked(checked);
        sw.setTextColor(a.getColor(R.color.text_primary));
        card.addView(sw);
        Design.note(card, note);
        sw.setOnCheckedChangeListener(
                (v, on) -> {
                    BlockRule[] bn = change.apply(on);
                    BlockRule before = bn[0], next = bn[1];
                    if (StrictAdmin.lockActive(a)) {
                        String why = RuleChange.reason(before, next);
                        if (why != null) {
                            a.message("Modo estricto (solo más estricto): " + why + ".");
                            a.render();
                            return;
                        }
                    }
                    try {
                        RuleStore.get(a).save(next);
                        FocusAccessibilityService.rulesChanged();
                        a.render();
                        a.message(done.apply(on));
                    } catch (RuntimeException e) {
                        a.message(e.getMessage());
                    }
                });
    }

    // Lote 5: duplicar. La copia nace desactivada para no bloquear nada por sorpresa.
    private static void duplicate(MainActivity a, BlockRule r) {
        try {
            RuleStore.get(a).save(r.duplicate(UUID.randomUUID().toString()));
            FocusAccessibilityService.rulesChanged();
            a.render();
            a.message("Copia creada y desactivada: «" + r.name + "»");
        } catch (RuntimeException e) {
            a.message(e.getMessage());
        }
    }

    // Lote 5: copia de seguridad de los bloqueos (sin tiempos de uso, pausas ni sesión).
    private static void backup(MainActivity a, LinearLayout body, RuleStore store) {
        LinearLayout card = Design.card(body, "Copia de los bloqueos");
        Design.note(
                card,
                "Guarda tus bloqueos en un archivo JSON o recupéralos. No incluye tiempos de uso, pausas ni la sesión de foco.");
        card.addView(
                Design.button(
                        a,
                        "Guardar copia de los bloqueos",
                        false,
                        () ->
                                a.openForResult(
                                        new android.content.Intent(
                                                        android.content.Intent
                                                                .ACTION_CREATE_DOCUMENT)
                                                .setType("application/json")
                                                .addCategory(
                                                        android.content.Intent.CATEGORY_OPENABLE)
                                                .putExtra(
                                                        android.content.Intent.EXTRA_TITLE,
                                                        "Foco-bloqueos-"
                                                                + java.time.LocalDate.now()
                                                                + ".json"),
                                        14)));
        card.addView(
                Design.button(
                        a,
                        "Restaurar copia de los bloqueos",
                        false,
                        () -> {
                            if (locked(a, store)) {
                                a.message(
                                        "Modo estricto: hay un bloqueo activo. Podrás restaurar la copia cuando termine.");
                                return;
                            }
                            a.openForResult(
                                    new android.content.Intent(
                                                    android.content.Intent.ACTION_OPEN_DOCUMENT)
                                            .setType("application/json")
                                            .addCategory(android.content.Intent.CATEGORY_OPENABLE),
                                    15);
                        }));
    }

    private static boolean locked(MainActivity a, RuleStore store) {
        return StrictAdmin.lockActive(a)
                || StrictAdmin.enabled(a) && (store.anyActive() || a.repository.locked());
    }

    static void onResult(MainActivity a, int request, android.net.Uri uri) {
        RuleStore store = RuleStore.get(a);
        try {
            if (request == 14) {
                String json = RuleStore.exportJson(store.rules());
                Documents.write(a, uri, json);
                a.message("Copia de los bloqueos guardada");
                return;
            }
            String text = Documents.read(a, uri, Documents.MAX_IMPORT);
            List<BlockRule> in = RuleStore.parseBackup(text);
            new AlertDialog.Builder(a)
                    .setMessage("¿Sustituir tus bloqueos por los " + in.size() + " de esta copia?")
                    .setPositiveButton(
                            "Restaurar",
                            (d, w) -> {
                                if (locked(a, store)) {
                                    a.message(
                                            "Modo estricto: hay un bloqueo activo. Podrás restaurar la copia cuando termine.");
                                    return;
                                }
                                AppConfig c = a.repository.config();
                                for (BlockRule r : in)
                                    r.packages.removeIf(p -> FocusEngine.essential(c, p));
                                try {
                                    store.replaceAll(in);
                                    FocusAccessibilityService.rulesChanged();
                                    a.render();
                                    a.message("Bloqueos restaurados");
                                } catch (RuntimeException e) {
                                    a.message(e.getMessage());
                                }
                            })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        } catch (Exception e) {
            a.message("No se pudo procesar la copia. Tus bloqueos se conservan.");
        }
    }

    // Lote 3: filtro web. Se aplica mientras el bloqueo esté activado y (sin horario o dentro de su
    // franja); los minutos no cuentan.
    private static Switch web(MainActivity a, LinearLayout box, BlockRule r) {
        Design.note(
                box,
                "Filtro web (Chrome, Samsung Internet, Firefox, Brave, Edge, Opera y DuckDuckGo). Se aplica siempre que el bloqueo esté activado, o solo en su horario si tiene uno. Los minutos permitidos no cuentan; una pausa lo levanta.");
        Button sites = Design.button(a, "", false, null);
        Runnable sitesLabel = () -> sites.setText("Sitios web: " + r.sites.size() + " · editar");
        sitesLabel.run();
        sites.setOnClickListener(
                v ->
                        list(
                                a,
                                "Sitios web",
                                "youtube.com o youtube.com/shorts",
                                r.sites,
                                WebFilter::site,
                                "Escribe un dominio, por ejemplo reddit.com",
                                sitesLabel));
        box.addView(sites);
        Button words = Design.button(a, "", false, null);
        Runnable wordsLabel =
                () -> words.setText("Palabras clave: " + r.keywords.size() + " · editar");
        wordsLabel.run();
        words.setOnClickListener(
                v ->
                        list(
                                a,
                                "Palabras clave",
                                "Palabra completa, por ejemplo apuestas",
                                r.keywords,
                                WebFilter::keyword,
                                "Escribe al menos 2 letras",
                                wordsLabel));
        box.addView(words);
        Switch other = new Switch(a);
        other.setText("Bloquear navegadores no compatibles");
        other.setChecked(r.blockUnsupportedBrowsers);
        other.setTextColor(a.getColor(R.color.text_primary));
        box.addView(other);
        Design.note(
                box,
                "Las palabras clave se buscan en la dirección y en los textos visibles de la página, sin mayúsculas ni tildes y como palabra completa. Foco no guarda lo que lee.");
        return other;
    }

    private static void list(
            MainActivity a,
            String title,
            String hint,
            List<String> items,
            java.util.function.Function<String, String> clean,
            String invalid,
            Runnable changed) {
        LinearLayout box = Design.column(a);
        int pad = Design.dp(a, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        LinearLayout rows = Design.column(a);
        box.addView(rows);
        Runnable[] fill = new Runnable[1];
        fill[0] =
                () -> {
                    rows.removeAllViews();
                    if (items.isEmpty()) Design.note(rows, "Lista vacía");
                    for (String it : new ArrayList<>(items))
                        rows.addView(
                                Design.button(
                                        a,
                                        it + "  ·  quitar",
                                        false,
                                        () -> {
                                            items.remove(it);
                                            fill[0].run();
                                            changed.run();
                                        }));
                };
        fill[0].run();
        EditText field = Design.field(a, hint, "", false);
        box.addView(field);
        box.addView(
                Design.button(
                        a,
                        "Añadir",
                        true,
                        () -> {
                            String v = clean.apply(field.getText().toString());
                            if (v == null) {
                                a.message(invalid);
                                return;
                            }
                            if (!items.contains(v) && items.size() < WebFilter.MAX_SITES) {
                                items.add(v);
                                field.setText("");
                                fill[0].run();
                                changed.run();
                            }
                        }));
        ScrollView scroll = new ScrollView(a);
        scroll.addView(box);
        new AlertDialog.Builder(a)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(R.string.save, null)
                .show();
    }

    // Lote 2: horario (una franja desde la app; el archivo admite varias).
    private static final String[] DAY_NAMES = {
        "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };

    private static void schedule(MainActivity a, LinearLayout box, BlockRule r) {
        Button mode = Design.button(a, "", false, null),
                days = Design.button(a, "", false, null),
                from = Design.button(a, "", false, null),
                to = Design.button(a, "", false, null);
        Runnable labels =
                () -> {
                    boolean on = r.scheduleMode != BlockRule.ScheduleMode.ALWAYS_LIMIT;
                    mode.setText(
                            r.scheduleMode == BlockRule.ScheduleMode.BLOCK_IN_WINDOW
                                    ? "Horario: bloqueo total en la franja"
                                    : r.scheduleMode == BlockRule.ScheduleMode.LIMIT_IN_WINDOW
                                            ? "Horario: el límite solo cuenta en la franja"
                                            : "Horario: sin horario");
                    for (Button b : new Button[] {days, from, to})
                        b.setVisibility(on ? android.view.View.VISIBLE : android.view.View.GONE);
                    if (!on) return;
                    Schedule x = r.schedules.get(0);
                    days.setText("Días: " + x.daysText());
                    from.setText("Desde " + Schedule.hhmm(x.startMinute));
                    to.setText(
                            "Hasta "
                                    + Schedule.hhmm(x.endMinute)
                                    + (x.endMinute < x.startMinute ? " (día siguiente)" : ""));
                };
        mode.setOnClickListener(
                v -> {
                    BlockRule.ScheduleMode[] all = BlockRule.ScheduleMode.values();
                    r.scheduleMode = all[(r.scheduleMode.ordinal() + 1) % all.length];
                    if (r.schedules.isEmpty())
                        r.schedules.add(Schedule.of(Schedule.ALL_DAYS, 22 * 60, 7 * 60));
                    labels.run();
                });
        days.setOnClickListener(
                v -> {
                    Schedule x = r.schedules.get(0);
                    boolean[] checked = new boolean[7];
                    for (int i = 0; i < 7; i++) checked[i] = (x.days & (1 << i)) != 0;
                    new AlertDialog.Builder(a)
                            .setTitle("Días")
                            .setMultiChoiceItems(DAY_NAMES, checked, (d, i, on) -> checked[i] = on)
                            .setPositiveButton(
                                    R.string.save,
                                    (d, w) -> {
                                        int m = 0;
                                        for (int i = 0; i < 7; i++) if (checked[i]) m |= 1 << i;
                                        x.days = m == 0 ? Schedule.ALL_DAYS : m;
                                        labels.run();
                                    })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
        from.setOnClickListener(
                v -> {
                    Schedule x = r.schedules.get(0);
                    new android.app.TimePickerDialog(
                                    a,
                                    (t, h, m) -> {
                                        x.startMinute = h * 60 + m;
                                        labels.run();
                                    },
                                    x.startMinute / 60,
                                    x.startMinute % 60,
                                    true)
                            .show();
                });
        to.setOnClickListener(
                v -> {
                    Schedule x = r.schedules.get(0);
                    new android.app.TimePickerDialog(
                                    a,
                                    (t, h, m) -> {
                                        x.endMinute = h * 60 + m;
                                        labels.run();
                                    },
                                    x.endMinute / 60,
                                    x.endMinute % 60,
                                    true)
                            .show();
                });
        box.addView(mode);
        box.addView(days);
        box.addView(from);
        box.addView(to);
        labels.run();
        Design.note(
                box,
                "Si «Hasta» es antes que «Desde», la franja cruza la medianoche (por ejemplo 22:00–07:00). Las pausas también valen en horario.");
    }

    // Lote 2: bloqueo rápido y Pomodoro. Bloquean las apps de todos los bloqueos y del Foco
    // clásico, sin pausas.
    private static void session(MainActivity a, LinearLayout body, RuleStore store) {
        FocusSession s = store.session();
        long now = System.currentTimeMillis();
        LinearLayout card = Design.card(body, "Sesión de foco");
        if (s != null) {
            FocusSession.Phase p = s.phase(now);
            Design.note(card, s.title(now) + " · termina en " + FocusOverlay.span(p.endsAt - now));
            if (s.kind == FocusSession.Kind.POMODORO)
                Design.note(
                        card, "La sesión completa termina en " + FocusOverlay.span(s.end() - now));
            card.addView(
                    Design.button(
                            a,
                            "Terminar",
                            false,
                            () -> {
                                if (StrictAdmin.enabled(a)) {
                                    a.message(
                                            "Modo estricto: la sesión no se puede terminar antes de tiempo.");
                                    return;
                                }
                                try {
                                    store.endSession();
                                    FocusAccessibilityService.rulesChanged();
                                    a.render();
                                } catch (RuntimeException e) {
                                    a.message(e.getMessage());
                                }
                            }));
            return;
        }
        Design.note(
                card,
                "Bloquea a la vez las apps de todos tus bloqueos y las del Foco clásico. Sin pausas mientras dura.");
        card.addView(
                Design.button(
                        a,
                        "Bloqueo rápido",
                        true,
                        () -> {
                            String[] items = new String[FocusSession.QUICK_CHOICES.length];
                            for (int i = 0; i < items.length; i++)
                                items[i] =
                                        FocusOverlay.span(FocusSession.QUICK_CHOICES[i] * 60_000L);
                            new AlertDialog.Builder(a)
                                    .setTitle("Bloqueo rápido")
                                    .setItems(
                                            items,
                                            (d, i) ->
                                                    startSession(
                                                            a,
                                                            store,
                                                            FocusSession.quick(
                                                                    System.currentTimeMillis(),
                                                                    FocusSession.QUICK_CHOICES[i])))
                                    .setNegativeButton(R.string.cancel, null)
                                    .show();
                        }));
        card.addView(
                Design.button(
                        a,
                        "Pomodoro",
                        false,
                        () -> {
                            LinearLayout box = Design.column(a);
                            int pad = Design.dp(a, 20);
                            box.setPadding(pad, pad / 2, pad, 0);
                            Design.note(box, "Minutos de enfoque");
                            EditText focus = Design.field(a, "Enfoque", "25", true);
                            box.addView(focus);
                            Design.note(box, "Minutos de descanso");
                            EditText rest = Design.field(a, "Descanso", "5", true);
                            box.addView(rest);
                            Design.note(box, "Ciclos");
                            EditText cycles = Design.field(a, "Ciclos", "4", true);
                            box.addView(cycles);
                            new AlertDialog.Builder(a)
                                    .setTitle("Pomodoro")
                                    .setView(box)
                                    .setPositiveButton(
                                            "Empezar",
                                            (d, w) ->
                                                    startSession(
                                                            a,
                                                            store,
                                                            FocusSession.pomodoro(
                                                                    System.currentTimeMillis(),
                                                                    number(focus, 25),
                                                                    number(rest, 5),
                                                                    number(cycles, 4))))
                                    .setNegativeButton(R.string.cancel, null)
                                    .show();
                        }));
    }

    private static int number(EditText e, int fallback) {
        try {
            return Integer.parseInt(e.getText().toString().trim());
        } catch (NumberFormatException x) {
            return fallback;
        }
    }

    private static void startSession(MainActivity a, RuleStore store, FocusSession s) {
        try {
            store.start(s);
            FocusAccessibilityService.rulesChanged();
            a.render();
        } catch (RuntimeException e) {
            a.message(e.getMessage());
        }
    }
}
