package es.foco.next.runtime;

import android.content.Context;
import es.foco.next.core.backup.DayReport;
import es.foco.next.core.backup.Secrets;
import es.foco.next.core.backup.SheetLayout;
import es.foco.next.core.backup.SheetRows;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * v47: sube un día a Drive: «Foco/Reportes/AAAA-MM/AAAA-MM-DD.json» y «.png» (si ya existen, se
 * reemplazan) y sus filas en la hoja «Foco · Historial» (antes de escribir, se borran las filas que
 * ese día ya tenía: nada se duplica). La hoja se crea la primera vez, con su diseño; después solo
 * se añaden filas. Todo lo que sale pasa por {@link Secrets#redact}. Hilo de fondo.
 */
final class DriveBackup {
    private final Context c;
    private final String token, apiKey;
    private final boolean mood;
    private final Map<String, String> months = new HashMap<>();
    private String reports, sheet;
    private long lastWrite;

    DriveBackup(Context c, String token, boolean mood) {
        this.c = c.getApplicationContext();
        this.token = token;
        this.mood = mood;
        this.apiKey = NextDnsSync.key(c);
    }

    /** Carpetas y hoja (las crea si faltan o si se borraron). */
    void ensure() throws Exception {
        String foco = GoogleApi.folder(token, "Foco", "root");
        reports = GoogleApi.folder(token, "Reportes", foco);
        BackupStore.setId(c, "foco", foco);
        BackupStore.setId(c, "reportes", reports);
        sheet = BackupStore.id(c, "hoja");
        Set<String> tabs = null;
        if (!sheet.isEmpty())
            try {
                tabs = GoogleApi.tabs(token, sheet);
            } catch (GoogleApi.HttpError e) {
                if (e.code != 404 && e.code != 403) throw e;
                sheet = ""; // la borraron (o ya no es accesible): se crea otra
            }
        if (sheet.isEmpty()) {
            sheet = GoogleApi.createSheet(token, SheetLayout.createBody(zone(), mood));
            BackupStore.setId(c, "hoja", sheet);
            pace();
            GoogleApi.batchUpdate(token, sheet, SheetLayout.designBody(mood));
            pace();
            GoogleApi.valuesBatchUpdate(token, sheet, SheetLayout.headersAndPanelBody(mood));
            // La hoja nace en «Mi unidad»: se mueve a la carpeta Foco.
            try {
                GoogleApi.json(
                        "PATCH",
                        GoogleApi.DRIVE
                                + "files/"
                                + GoogleApi.enc(sheet)
                                + "?addParents="
                                + GoogleApi.enc(foco)
                                + "&removeParents=root&fields=id",
                        token,
                        "{}");
            } catch (GoogleApi.HttpError e) {
                if (e.code == 401) throw e;
            }
            BackupStore.prefs(c).edit().putInt("diseno", SheetLayout.DESIGN).apply();
        } else {
            boolean moodTab = tabs != null && tabs.contains(SheetLayout.ANIMO);
            if (mood && !moodTab) {
                pace();
                GoogleApi.batchUpdate(token, sheet, SheetLayout.addMoodBody());
                pace();
                GoogleApi.valuesBatchUpdate(token, sheet, SheetLayout.moodHeadersBody());
                moodTab = true;
            }
            // v47b: hoja creada con el diseño 1 (fórmulas que se movían al añadir filas y fecha
            // sin formato): se reescriben el Panel y los encabezados y se da formato a la fecha.
            if (BackupStore.prefs(c).getInt("diseno", 1) < SheetLayout.DESIGN) {
                pace();
                GoogleApi.valuesBatchUpdate(token, sheet, SheetLayout.headersAndPanelBody(moodTab));
                pace();
                GoogleApi.batchUpdate(token, sheet, SheetLayout.repairBody(moodTab));
                BackupStore.prefs(c).edit().putInt("diseno", SheetLayout.DESIGN).apply();
            }
        }
    }

    /**
     * v47b: Google quita las series de un gráfico si se creó sin datos. Tras subir los días, los
     * gráficos rotos o que falten se rehacen (si aún no hay datos, se vuelve a intentar mañana).
     */
    void fixCharts() throws Exception {
        List<Object[]> charts = GoogleApi.charts(token, sheet, SheetLayout.ID_PANEL);
        List<String> redo = new ArrayList<>();
        List<Integer> delete = new ArrayList<>();
        for (String title : SheetLayout.CHART_TITLES) {
            boolean ok = false;
            for (Object[] ch : charts)
                if (title.equals(ch[1])) {
                    if ((Integer) ch[2] > 0) ok = true;
                    else delete.add((Integer) ch[0]);
                }
            if (!ok) redo.add(title);
        }
        if (redo.isEmpty()) return;
        pace();
        GoogleApi.batchUpdate(token, sheet, SheetLayout.chartsBody(redo, delete));
    }

    /** Carpeta Foco/Ajustes (la crea si falta). */
    static String settingsFolder(String token) throws Exception {
        return GoogleApi.folder(token, "Ajustes", GoogleApi.folder(token, "Foco", "root"));
    }

    /**
     * v48: sube la copia de todos los ajustes (se reemplaza cada noche). Si en Drive ya hay una
     * copia y este teléfono aún no decidió recuperarla (recién instalado), no la pisa con ajustes
     * vacíos: Ajustes ofrece «Recuperar todo desde Drive».
     */
    void uploadSettings() throws Exception {
        String folder = settingsFolder(token);
        if (!BackupStore.settingsDecided(c)) {
            if (!GoogleApi.children(token, folder).containsValue(SettingsSnapshot.FILE)) {
                BackupStore.setSettingsDecided(c); // no había copia: este teléfono es el bueno
            } else {
                BackupStore.setRestorePending(c, true);
                return;
            }
        }
        String json = Secrets.redact(SettingsSnapshot.export(c), apiKey);
        if (Secrets.leaks(json, apiKey)) throw new IllegalStateException("secreto en los ajustes");
        GoogleApi.put(
                token,
                folder,
                SettingsSnapshot.FILE,
                "application/json",
                json.getBytes(StandardCharsets.UTF_8));
    }

    static String zone() {
        return java.util.TimeZone.getDefault().getID();
    }

    /** Sube un día. Devuelve el id del PNG (para «Ver gráfico»). */
    String upload(DayReport d, LocalDate today) throws Exception {
        String day = d.day.toString();
        String month = day.substring(0, 7);
        String folder = months.get(month);
        if (folder == null) {
            folder = GoogleApi.folder(token, month, reports);
            months.put(month, folder);
        }
        String json = Secrets.redact(d.toJson(mood), apiKey);
        if (Secrets.leaks(json, apiKey)) throw new IllegalStateException("secreto en el reporte");
        GoogleApi.put(
                token,
                folder,
                day + ".json",
                "application/json",
                json.getBytes(StandardCharsets.UTF_8));
        String png = GoogleApi.put(token, folder, day + ".png", "image/png", ReportChart.png(c, d));
        List<String> tabs = new ArrayList<>(java.util.Arrays.asList(SheetLayout.DATA_TABS));
        if (mood) tabs.add(SheetLayout.ANIMO);
        String keepFrom = today.minusDays(29).toString();
        for (String tab : tabs) {
            List<String> col = GoogleApi.columnA(token, sheet, tab);
            List<int[]> del = new ArrayList<>(SheetRows.rowsOf(col, day));
            if (SheetLayout.EVENTOS.equals(tab)) del.addAll(SheetRows.olderThan(col, keepFrom));
            del.sort((x, y) -> Integer.compare(y[0], x[0]));
            if (!del.isEmpty()) {
                pace();
                GoogleApi.batchUpdate(
                        token, sheet, SheetLayout.deleteRowsBody(SheetLayout.sheetId(tab), del));
            }
            List<List<Object>> rows = clean(SheetLayout.rowsFor(tab, d));
            if (rows.isEmpty()) continue;
            pace();
            GoogleApi.append(token, sheet, SheetLayout.a1(tab, "A1"), SheetLayout.appendBody(rows));
        }
        return png;
    }

    /** Ningún texto de la hoja lleva la clave ni un token. */
    private List<List<Object>> clean(List<List<Object>> rows) {
        List<List<Object>> out = new ArrayList<>();
        for (List<Object> r : rows) {
            List<Object> x = new ArrayList<>();
            for (Object v : r) x.add(v instanceof String ? Secrets.redact((String) v, apiKey) : v);
            out.add(x);
        }
        return out;
    }

    /** Sheets admite ~60 escrituras por minuto: una cada 1,1 s como mucho. */
    private void pace() throws InterruptedException {
        long wait = lastWrite + 1_100 - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastWrite = System.currentTimeMillis();
    }
}
