package es.foco.next.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import es.foco.next.R;
import es.foco.next.data.Repository;
import es.foco.next.log.BlockLog;
import es.foco.next.log.LogCsv;
import es.foco.next.log.LogRetention;
import es.foco.next.log.LogSummary;
import es.foco.next.log.ObservedSnapshot;
import es.foco.next.log.UsageContrast;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Fase 4: "Registro" screen. Per day: blocks, attempts and per-app detail, plus the Accessibility
 * vs UsageStats contrast. Reads foco-log.db on the log thread; never on the main thread. Shows
 * observed numbers only.
 */
public final class RegistroActivity extends Activity {
    static final int SHOWN_DAYS = 30, EXPORT_30 = 30, EXPORT_90 = 90, MAX_APPS = 6;
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es"));
    private LinearLayout body;
    private int generation;

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
        TextView title = new TextView(this);
        title.setText("Registro");
        title.setTextSize(34);
        title.setTextColor(getColor(R.color.text_primary));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(
                Design.dp(this, 20), Design.dp(this, 20), Design.dp(this, 20), Design.dp(this, 20));
        title.setAccessibilityHeading(true);
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
        render();
    }

    @Override
    protected void onDestroy() {
        generation++;
        super.onDestroy();
    }

    private void render() {
        body.removeAllViews();
        LinearLayout intro = Design.card(body, "Lo que ocurrió, día a día");
        Design.note(
                intro,
                "Bloqueos, intentos de abrir una app bloqueada y lo que elegiste en la pausa, con hora y app. Son números observados en este teléfono: no miden tu productividad ni prometen ahorro.");
        Design.note(
                intro,
                "Se guarda aparte del plan (foco-log.db), 90 días como máximo. Si el registro fallara, el bloqueo sigue igual.");
        intro.addView(
                Design.button(
                        this,
                        "Ver estadísticas de uso",
                        false,
                        () -> startActivity(new Intent(this, StatsActivity.class)))); // Lote 4
        LinearLayout days = Design.card(body, "Últimos " + SHOWN_DAYS + " días");
        Design.note(days, "Cargando el registro…");
        StabilityPermissions.card(body, this);
        LinearLayout export = Design.card(body, "Exportar o borrar");
        Design.note(
                export,
                "CSV con cada evento (fecha, hora, tipo, app) y el contraste diario de tiempos. Nada sale del teléfono salvo que tú guardes el archivo.");
        export.addView(
                Design.button(this, "Exportar 30 días · CSV", false, () -> create(EXPORT_30)));
        export.addView(
                Design.button(this, "Exportar 90 días · CSV", false, () -> create(EXPORT_90)));
        export.addView(Design.button(this, "Borrar el registro", false, this::confirmClear));

        ObservedSnapshot snapshot = null;
        try {
            Repository r = Repository.get(this);
            snapshot = ObservedSnapshot.of(r.state(), r.config());
        } catch (RuntimeException ignored) {
        }
        BlockLog.refreshNow(this, snapshot);
        int expected = ++generation;
        boolean access = UsageContrast.allowed(this);
        String from = LogRetention.firstDay(LocalDate.now(), SHOWN_DAYS);
        BlockLog.query(
                this,
                db -> LogSummary.build(db.counts(from), db.usage(from)),
                list -> {
                    if (expected == generation && !isFinishing()) fill(days, list, access);
                },
                () -> {
                    if (expected == generation && !isFinishing())
                        replace(days, "No se pudo leer el registro. El bloqueo no depende de él.");
                });
    }

    private void replace(LinearLayout card, String text) {
        while (card.getChildCount() > 2)
            card.removeViewAt(2); // keep heading + gap from Design.card
        Design.note(card, text);
    }

    private void fill(LinearLayout card, List<LogSummary.Day> list, boolean access) {
        while (card.getChildCount() > 2) card.removeViewAt(2);
        if (list.isEmpty()) {
            Design.note(
                    card,
                    "Aún no hay eventos. Aparecerán cuando Foco bloquee, cuando intentes abrir una app bloqueada o cuando elijas algo en la pausa.");
            return;
        }
        for (LogSummary.Day d : list) {
            String title;
            try {
                title = LocalDate.parse(d.day).format(DAY);
                title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
            } catch (RuntimeException e) {
                title = d.day;
            }
            card.addView(Design.text(this, title, 18, true));
            Design.note(card, "Bloqueos: " + d.blocks + " · Intentos: " + d.attempts);
            if (d.practices + d.alternatives + d.homes > 0)
                Design.note(
                        card,
                        "Prácticas: "
                                + d.practices
                                + " · Alternativas: "
                                + d.alternatives
                                + " · Ir a Inicio: "
                                + d.homes);
            int shown = 0;
            for (Map.Entry<String, LogSummary.AppDay> e : d.apps.entrySet()) {
                if (shown++ >= MAX_APPS) break;
                LogSummary.AppDay a = e.getValue();
                StringBuilder line = new StringBuilder(AppPicker.label(this, e.getKey()));
                if (a.attempts + a.blocks > 0)
                    line.append(" · ")
                            .append(a.attempts)
                            .append(a.attempts == 1 ? " intento" : " intentos")
                            .append(" · ")
                            .append(a.blocks)
                            .append(a.blocks == 1 ? " bloqueo" : " bloqueos");
                if (a.observedMs >= 0)
                    line.append("\nObservado por Foco ")
                            .append(Design.duration(this, a.observedMs));
                if (a.usageStatsMs >= 0)
                    line.append(" · Android ").append(Design.duration(this, a.usageStatsMs));
                else if (a.observedMs >= 0) line.append(" · Android: sin Acceso de uso");
                Design.note(card, line.toString());
            }
            Design.gap(card, 8);
        }
        Design.note(
                card,
                access
                        ? "Android cuenta como uso el tiempo en que la pausa de Foco cubre una app, por eso su cifra puede ser mayor. Foco no inventa tiempo si su servicio estuvo dormido: ahí Android puede mostrar más."
                        : "Concede Acceso de uso para comparar con el historial de Android.");
    }

    private void create(int days) {
        try {
            startActivityForResult(
                    new Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .setType("text/csv")
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .putExtra(
                                    Intent.EXTRA_TITLE,
                                    "Foco-registro-" + days + "d-" + LocalDate.now() + ".csv"),
                    days);
        } catch (RuntimeException e) {
            message(this, "No se pudo abrir el selector de archivos.");
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK
                || data == null
                || data.getData() == null
                || (request != EXPORT_30 && request != EXPORT_90)) return;
        Uri uri = data.getData();
        Context app = getApplicationContext();
        String from = LogRetention.firstDay(LocalDate.now(), request);
        BlockLog.query(
                this,
                db -> {
                    String csv =
                            LogCsv.build(
                                    db.events(from), db.usage(from), p -> AppPicker.label(app, p));
                    try (OutputStream out = app.getContentResolver().openOutputStream(uri, "wt")) {
                        if (out == null) throw new IOException("Sin destino");
                        out.write(csv.getBytes(StandardCharsets.UTF_8));
                    }
                    return Boolean.TRUE;
                },
                ok -> message(app, "Registro exportado. Puedes abrirlo en una hoja de cálculo."),
                () ->
                        message(
                                app,
                                "No se pudo exportar el registro. El bloqueo no se ve afectado."));
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setMessage(
                        "¿Borrar todo el registro de bloqueos? El plan y los tiempos de la semana no cambian.")
                .setPositiveButton(
                        "Borrar",
                        (d, w) ->
                                BlockLog.query(
                                        this,
                                        db -> {
                                            db.clear();
                                            return Boolean.TRUE;
                                        },
                                        ok -> {
                                            message(this, "Registro borrado.");
                                            if (!isFinishing()) render();
                                        },
                                        () -> message(this, "No se pudo borrar el registro.")))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void message(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_LONG).show();
    }
}
