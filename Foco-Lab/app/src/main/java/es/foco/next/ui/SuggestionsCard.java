package es.foco.next.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.widget.LinearLayout;
import android.widget.Toast;
import es.foco.next.core.*;
import es.foco.next.data.Repository;
import es.foco.next.data.RuleStore;
import es.foco.next.log.BlockLog;
import es.foco.next.log.LogDatabase;
import es.foco.next.log.UsageContrast;
import es.foco.next.log.UsageHistory;
import es.foco.next.runtime.FocusAccessibilityService;
import es.foco.next.runtime.StrictAdmin;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 1.9: tarjeta «Sugerencias para ti» (Bloqueos y Estadísticas › Tendencia). Los cálculos están en
 * foco-core ({@link BlockSuggestions}); aquí solo se leen los datos, se guardan los descartes (14
 * días) y se crean los bloqueos.
 */
final class SuggestionsCard {
    private SuggestionsCard() {}

    static final String PREFS = "foco-sugerencias";
    static final int DAYS = BlockSuggestions.MAX_DAYS;

    /**
     * Apps de ocio: vigiladas, de bloqueos (no lista blanca) y las de ocio por defecto instaladas.
     * Nunca esenciales.
     */
    static Set<String> leisure(Context c) {
        Set<String> out = new LinkedHashSet<>();
        AppConfig cfg = null;
        try {
            cfg = Repository.get(c).config();
            out.addAll(cfg.watchedApps);
        } catch (RuntimeException ignored) {
        }
        try {
            for (BlockRule r : RuleStore.get(c).rules()) if (!r.allowlist) out.addAll(r.packages);
        } catch (RuntimeException ignored) {
        }
        for (String p : BlockSuggestions.DEFAULT_LEISURE) {
            try {
                c.getPackageManager().getApplicationInfo(p, 0);
                out.add(p);
            } catch (Exception ignored) {
            }
        }
        AppConfig f = cfg == null ? new AppConfig() : cfg;
        out.removeIf(p -> FocusEngine.essential(f, p));
        return out;
    }

    /** Foco y los launchers no cuentan. */
    static Set<String> hidden(Context c) {
        Set<String> out = new HashSet<>();
        out.add(c.getPackageName());
        try {
            for (ResolveInfo i :
                    c.getPackageManager()
                            .queryIntentActivities(
                                    new Intent(Intent.ACTION_MAIN)
                                            .addCategory(Intent.CATEGORY_HOME),
                                    0))
                if (i.activityInfo != null) out.add(i.activityInfo.packageName);
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    /**
     * En el hilo del registro: sugerencias visibles (sin descartadas ni las que ya tienen su
     * bloqueo).
     */
    static List<BlockSuggestions.Suggestion> compute(
            Context app,
            Map<String, Map<String, Long>> daily,
            Map<String, Map<String, long[]>> hourly,
            LocalDate today) {
        List<BlockSuggestions.Suggestion> all =
                BlockSuggestions.build(
                        daily,
                        hourly,
                        today,
                        leisure(app),
                        hidden(app),
                        p -> AppPicker.label(app, p));
        List<BlockRule> rules;
        try {
            rules = RuleStore.get(app).rules();
        } catch (RuntimeException e) {
            rules = Collections.emptyList();
        }
        return BlockSuggestions.visible(all, rules, dismissed(app), System.currentTimeMillis());
    }

    /**
     * Bloqueos: lee 28 días en segundo plano y rellena {@code holder} si sigue en pantalla. Sin
     * Acceso de uso no muestra nada.
     */
    static void load(Activity a, LinearLayout holder, Runnable changed) {
        if (!UsageContrast.allowed(a)) return;
        Context app = a.getApplicationContext();
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        BlockLog.query(
                a,
                (LogDatabase db) ->
                        compute(
                                app,
                                UsageHistory.load(app, db, today, DAYS, zone, now),
                                UsageHistory.hours(db, today, DAYS),
                                today),
                list -> {
                    if (holder.getParent() != null && !a.isFinishing())
                        show(a, holder, list, changed);
                },
                () -> {});
    }

    /** La tarjeta, o nada si no hay sugerencias. */
    static void show(
            Activity a,
            LinearLayout parent,
            List<BlockSuggestions.Suggestion> list,
            Runnable changed) {
        if (list == null || list.isEmpty()) return;
        LinearLayout card = Design.card(parent, "Sugerencias para ti");
        Design.note(
                card,
                "Salen de tu uso de las últimas semanas. Crear un bloqueo lo activa ya; luego puedes editarlo o desactivarlo en Bloqueos.");
        for (BlockSuggestions.Suggestion s : list) {
            Design.gap(card, 8);
            card.addView(Design.text(a, s.title, 17, true));
            Design.note(card, s.detail);
            card.addView(
                    Design.button(
                            a,
                            "Crear bloqueo",
                            true,
                            () -> {
                                if (create(a, s)) changed.run();
                            }));
            card.addView(
                    Design.button(
                            a,
                            "Descartar",
                            false,
                            () -> {
                                dismiss(a, s.id);
                                changed.run();
                            }));
        }
    }

    /**
     * Crea el bloqueo de la sugerencia, activado. Con el candado del modo estricto solo si es más
     * estricto (crear lo es).
     */
    static boolean create(Activity a, BlockSuggestions.Suggestion s) {
        try {
            RuleStore store = RuleStore.get(a);
            String id = UUID.randomUUID().toString();
            BlockRule before =
                    s.kind == BlockSuggestions.Kind.WHATSAPP ? store.quickWhatsApp() : null;
            BlockRule r =
                    s.kind == BlockSuggestions.Kind.WHATSAPP
                            ? InAppFilter.quickWhatsApp(before, true, id)
                            : s.rule(id);
            AppConfig c = Repository.get(a).config();
            r.packages.removeIf(p -> FocusEngine.essential(c, p));
            if (StrictAdmin.lockActive(a)) {
                String why = RuleChange.reason(before, r);
                if (why != null) {
                    toast(a, "Modo estricto (solo más estricto): " + why + ".");
                    return false;
                }
            }
            store.save(r);
            FocusAccessibilityService.rulesChanged();
            toast(a, "Bloqueo «" + r.name + "» creado y activado. Puedes editarlo en Bloqueos.");
            return true;
        } catch (RuntimeException e) {
            toast(a, e.getMessage() == null ? "No se pudo guardar el cambio" : e.getMessage());
            return false;
        }
    }

    static void dismiss(Context c, String id) {
        try {
            prefs(c).edit()
                    .putLong(id, BlockSuggestions.dismiss(System.currentTimeMillis()))
                    .apply();
        } catch (RuntimeException ignored) {
        }
    }

    static Map<String, Long> dismissed(Context c) {
        Map<String, Long> out = new HashMap<>();
        try {
            for (Map.Entry<String, ?> e : prefs(c).getAll().entrySet())
                if (e.getValue() instanceof Long) out.put(e.getKey(), (Long) e.getValue());
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void toast(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_LONG).show();
    }
}
