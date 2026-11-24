package es.foco.next.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import es.foco.next.R;
import es.foco.next.core.BlockRule;
import es.foco.next.core.BlockSuggestions;
import es.foco.next.core.ScreenTime;
import es.foco.next.core.UsageShift;
import es.foco.next.data.Repository;
import es.foco.next.data.RuleStore;
import es.foco.next.log.BlockEvent;
import es.foco.next.log.BlockLog;
import es.foco.next.log.UsageContrast;
import es.foco.next.log.UsageHistory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lote 4: "Estadísticas". Día / Semana / Tendencia from Android's usage history (Acceso de uso),
 * read on the log thread when the screen opens and cached per day. Shows observed time only; no
 * scores.
 */
public final class StatsActivity extends Activity {
    static final int DAY = 0,
            WEEK = 1,
            TREND = 2,
            DAYS = SuggestionsCard.DAYS,
            TOP = 8; // 1.9: 28 días para las sugerencias
    private static final String[] TABS = {"Día", "Semana", "Tendencia"};
    private LinearLayout body;
    private int tab = DAY, generation;
    private Data data;
    private Set<String> distracting = new HashSet<>(), hidden = new HashSet<>();

    static final class Data {
        Map<String, Map<String, Long>> days;
        int blocks, attempts, pauses;
        List<BlockSuggestions.Suggestion> suggestions = new java.util.ArrayList<>();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base.createConfigurationContext(SceneTheme.override(base)));
    }

    @Override
    public void onCreate(Bundle b) {
        setTheme(R.style.AppTheme);
        super.onCreate(b);
        LinearLayout root = Design.column(this);
        root.setFitsSystemWindows(true);
        TextView title = Design.text(this, "Estadísticas", 34, true);
        title.setAccessibilityHeading(true);
        title.setPadding(
                Design.dp(this, 20), Design.dp(this, 20), Design.dp(this, 20), Design.dp(this, 8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body = Design.column(this);
        body.setPadding(Design.dp(this, 20), 0, Design.dp(this, 20), Design.dp(this, 24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    @Override
    protected void onDestroy() {
        generation++;
        super.onDestroy();
    }

    private void load() {
        distracting = new HashSet<>();
        hidden = new HashSet<>();
        hidden.add(getPackageName());
        try {
            distracting.addAll(Repository.get(this).config().watchedApps);
        } catch (RuntimeException ignored) {
        }
        try {
            for (BlockRule r : RuleStore.get(this).rules())
                if (!r.allowlist) distracting.addAll(r.packages);
        } catch (RuntimeException ignored) {
        }
        try {
            for (ResolveInfo i :
                    getPackageManager()
                            .queryIntentActivities(
                                    new Intent(Intent.ACTION_MAIN)
                                            .addCategory(Intent.CATEGORY_HOME),
                                    0))
                if (i.activityInfo != null) hidden.add(i.activityInfo.packageName);
        } catch (RuntimeException ignored) {
        }
        if (!UsageContrast.allowed(this)) {
            data = null;
            render();
            return;
        }
        data = null;
        render();
        int expected = ++generation;
        Context app = getApplicationContext();
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        long now = System.currentTimeMillis();
        BlockLog.query(
                this,
                db -> {
                    Data d = new Data();
                    d.days = UsageHistory.load(app, db, today, DAYS, zone, now);
                    for (BlockEvent e : db.events(today.toString())) {
                        if (!e.day.equals(today.toString())) continue;
                        if (e.type.isBlock()) d.blocks++;
                        if (e.type.isAttempt()) d.attempts++;
                    }
                    try {
                        d.pauses = RuleStore.get(app).pausesToday();
                    } catch (RuntimeException ignored) {
                    } // Lote 5: pausas reales de los bloqueos
                    try {
                        d.suggestions =
                                SuggestionsCard.compute(
                                        app, d.days, UsageHistory.hours(db, today, DAYS), today);
                    } catch (RuntimeException ignored) {
                    } // 1.9
                    return d;
                },
                d -> {
                    if (expected == generation && !isFinishing()) {
                        data = d;
                        render();
                    }
                },
                () -> {
                    if (expected == generation && !isFinishing()) {
                        body.removeAllViews();
                        tabs();
                        Design.note(
                                Design.card(body, "Sin datos"),
                                "No se pudo leer el historial de uso. El bloqueo no depende de él.");
                    }
                });
    }

    private void tabs() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < TABS.length; i++) {
            int which = i;
            Button b =
                    Design.button(
                            this,
                            TABS[i],
                            i == tab,
                            () -> {
                                tab = which;
                                render();
                            });
            b.setSelected(i == tab);
            b.setContentDescription(TABS[i] + (i == tab ? ", seleccionado" : ""));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            p.topMargin = Design.dp(this, 8);
            if (i > 0) p.leftMargin = Design.dp(this, 6);
            row.addView(b, p);
        }
        body.addView(row, new LinearLayout.LayoutParams(-1, -2));
        // v28: un solo sitio para tiempos y bloqueos: el registro de bloqueos se abre desde aquí.
        Button log =
                Design.button(
                        this,
                        "Registro de bloqueos ›",
                        false,
                        () -> startActivity(new android.content.Intent(this, RegistroActivity.class)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Design.dp(this, 6);
        body.addView(log, lp);
    }

    private void render() {
        body.removeAllViews();
        if (!UsageContrast.allowed(this)) {
            LinearLayout box = Design.card(body, "Falta el Acceso de uso");
            Design.note(
                    box,
                    "Las estadísticas salen del historial de uso que Android ya guarda en este teléfono. Para leerlo, Foco necesita que actives Acceso de uso. No se envía nada fuera del teléfono y el bloqueo funciona igual sin él.");
            box.addView(
                    Design.button(
                            this,
                            "Conceder Acceso de uso",
                            true,
                            () -> StabilityPermissions.openUsageAccess(this)));
            return;
        }
        tabs();
        if (data == null) {
            Design.note(Design.card(body, TABS[tab]), "Leyendo el historial de uso…");
            return;
        }
        if (tab == DAY) day();
        else if (tab == WEEK) week();
        else trend();
        Design.note(
                body,
                "Tiempo en primer plano según Android. Foco, el launcher y la interfaz del sistema no aparecen en las listas.");
    }

    private void day() {
        String today = LocalDate.now().toString();
        Map<String, Long> apps = data.days.get(today);
        LinearLayout box = Design.card(body, "Hoy");
        box.addView(Design.text(this, hm(total(apps) - apps0(apps)), 36, true));
        Design.note(
                box,
                "Bloqueos hoy: "
                        + data.blocks
                        + " · Pausas hoy: "
                        + data.pauses
                        + " · Intentos durante bloqueo: "
                        + data.attempts);
        UsageShift.Shift shift = shift();
        if (shift != null)
            Design.note(
                    box,
                    UsageShift.text(shift, p -> AppPicker.label(this, p))
                            + " (7 días). Más en Tendencia.");
        apps(Design.card(body, "Apps más usadas hoy"), apps, "Aún no hay tiempo registrado hoy.");
    }

    private void week() {
        List<String> days = ScreenTime.days(LocalDate.now(), 7);
        long[] totals = ScreenTime.totals(data.days, days);
        long sum = 0;
        for (long t : totals) sum += t;
        LinearLayout box = Design.card(body, "Últimos 7 días");
        box.addView(Design.text(this, hm(sum / 7) + " al día de media", 22, true));
        String[] labels = new String[days.size()];
        StringBuilder desc = new StringBuilder("Tiempo por día: ");
        for (int i = 0; i < labels.length; i++) {
            LocalDate d = LocalDate.parse(days.get(i));
            labels[i] =
                    i == labels.length - 1
                            ? "Hoy"
                            : d.getDayOfWeek()
                                    .getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es"));
            desc.append(labels[i])
                    .append(' ')
                    .append(hm(totals[i]))
                    .append(i < labels.length - 1 ? ", " : ".");
        }
        WeekBars bars = new WeekBars(this);
        bars.set(totals, labels, desc.toString());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Design.dp(this, 160));
        p.topMargin = Design.dp(this, 12);
        box.addView(bars, p);
        apps(
                Design.card(body, "Apps más usadas esta semana"),
                ScreenTime.sum(data.days, days),
                "Sin tiempo registrado esta semana.");
    }

    /** 1.9: últimos 7 días frente a los 7 anteriores, solo con apps de ocio como «la que baja». */
    private UsageShift.Shift shift() {
        LocalDate today = LocalDate.now();
        return UsageShift.detect(
                ScreenTime.sum(data.days, ScreenTime.days(today.minusDays(7), 7)),
                ScreenTime.sum(data.days, ScreenTime.days(today, 7)),
                distracting,
                hidden);
    }

    private void trend() {
        LocalDate today = LocalDate.now();
        Map<String, Long> last = ScreenTime.sum(data.days, ScreenTime.days(today, 7)),
                before = ScreenTime.sum(data.days, ScreenTime.days(today.minusDays(7), 7));
        // 1.9: el desplazamiento va primero; las dos listas «creciendo / van a menos» se juntan en
        // una sola tarjeta.
        UsageShift.Shift shift = UsageShift.detect(before, last, distracting, hidden);
        if (shift != null) {
            LinearLayout box = Design.card(body, "Desplazamiento");
            TextView t =
                    Design.text(
                            this, UsageShift.text(shift, p -> AppPicker.label(this, p)), 17, true);
            box.addView(t);
            String up = shift.up.get(0).pkg;
            Design.note(
                    box,
                    "Lo que dejas en una app suele irse a otra. "
                            + (es.foco.next.core.InAppFilter.sectionsInEssential(up)
                                    ? "WhatsApp no se bloquea entero: en Bloqueos › Atajos puedes bloquear solo sus Estados y canales."
                                    : "Puedes añadir "
                                            + AppPicker.label(this, up)
                                            + " a un bloqueo o usar los Atajos de Bloqueos."));
        }
        ScreenTime.Trend t =
                ScreenTime.trend(before, last, hidden, ScreenTime.DEFAULT_TREND_MIN, 3);
        LinearLayout changes = Design.card(body, "Cambios por app");
        Design.note(
                changes,
                "Últimos 7 días frente a los 7 anteriores. Solo cambios de 5 minutos o más.");
        if (t.up.isEmpty() && t.down.isEmpty())
            Design.note(changes, "Ninguna app ha subido ni bajado de forma notable.");
        // Cuidado: sin rojo para el ocio; un tono neutro.
        for (ScreenTime.Delta d : t.up) delta(changes, d, "↑ +", 0xFF7A6A55);
        for (ScreenTime.Delta d : t.down) delta(changes, d, "↓ −", 0xFF2E7D32);
        if (ScreenTime.total(before) == 0)
            Design.note(
                    changes,
                    "Aún no hay datos de la semana anterior: Android solo guarda unos días de historial detallado y Foco empieza a conservarlo desde hoy.");
        SuggestionsCard.show(this, body, data.suggestions, this::load);
    }

    private void delta(LinearLayout box, ScreenTime.Delta d, String sign, int color) {
        LinearLayout row = row(box, d.pkg);
        TextView v = Design.text(this, sign + hm(Math.abs(d.change())), 16, true);
        v.setTextColor(color);
        row.addView(v);
        Design.note(box, hm(d.before) + " → " + hm(d.after));
    }

    private void apps(LinearLayout box, Map<String, Long> apps, String empty) {
        List<Map.Entry<String, Long>> top = ScreenTime.top(apps, hidden, TOP);
        if (top.isEmpty()) {
            Design.note(box, empty);
            return;
        }
        long max = top.get(0).getValue();
        for (Map.Entry<String, Long> e : top) {
            LinearLayout row = row(box, e.getKey());
            row.addView(Design.text(this, hm(e.getValue()), 15, false));
            ProgressBar bar =
                    new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(1000);
            bar.setProgress((int) (1000 * e.getValue() / Math.max(1, max)));
            bar.setImportantForAccessibility(ProgressBar.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, Design.dp(this, 8));
            p.bottomMargin = Design.dp(this, 6);
            box.addView(bar, p);
        }
    }

    /** Icon, label and "Distractivo" tag; the caller appends the value at the end of the row. */
    private LinearLayout row(LinearLayout box, String pkg) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Design.dp(this, 8), 0, Design.dp(this, 4));
        ImageView icon = new ImageView(this);
        try {
            Drawable d = getPackageManager().getApplicationIcon(pkg);
            icon.setImageDrawable(d);
        } catch (Exception ignored) {
        }
        icon.setImportantForAccessibility(ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int s = Design.dp(this, 32);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(s, s);
        ip.rightMargin = Design.dp(this, 12);
        row.addView(icon, ip);
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.addView(Design.text(this, AppPicker.label(this, pkg), 16, true));
        if (distracting.contains(pkg)) {
            TextView tag = Design.text(this, "Distractivo", 12, true);
            tag.setTextColor(getColor(R.color.copper_dark));
            names.addView(tag);
        }
        row.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private static long total(Map<String, Long> apps) {
        long sum = 0;
        if (apps != null)
            for (Map.Entry<String, Long> e : apps.entrySet())
                if (!ScreenTime.excluded(e.getKey(), null)) sum += Math.max(0, e.getValue());
        return sum;
    }

    /** Foco's own time does not count as screen time here. */
    private long apps0(Map<String, Long> apps) {
        return apps == null ? 0 : Math.max(0, apps.getOrDefault(getPackageName(), 0L));
    }

    static String hm(long ms) {
        long min = Math.max(0, ms) / 60_000L;
        if (min < 60) return min + " min";
        return (min / 60) + " h" + (min % 60 == 0 ? "" : " " + (min % 60) + " min");
    }
}
