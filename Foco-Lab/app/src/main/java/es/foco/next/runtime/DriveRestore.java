package es.foco.next.runtime;

import android.content.Context;
import es.foco.next.log.LogDatabase;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v47: «Recuperar historial desde Drive». Al reinstalar (base local vacía y Drive conectado) lee
 * los JSON de Foco/Reportes y reconstruye los resúmenes diarios del registro: tiempo invertido y de
 * ocio por app, retos y minutos. No pisa nada que ya exista. Hilo de fondo.
 */
public final class DriveRestore {
    private DriveRestore() {}

    /**
     * ¿Ofrecer «Recuperar todo»? Drive conectado y: hay una copia de ajustes esperando (v48) o el
     * registro local está vacío. Hilo de fondo (puede mirar Drive).
     */
    public static boolean offer(Context c) {
        if (!BackupStore.connected(c)) return false;
        if (BackupStore.restorePending(c)) return true;
        if (!BackupStore.settingsDecided(c))
            try {
                String token = GoogleDrive.token(c);
                if (GoogleApi.children(token, DriveBackup.settingsFolder(token))
                        .containsValue(SettingsSnapshot.FILE)) {
                    BackupStore.setRestorePending(c, true);
                    return true;
                }
            } catch (Exception ignored) {
            }
        Boolean empty =
                es.foco.next.log.BlockLog.await(c, LogDatabase::inspectorEmpty, 5_000);
        return Boolean.TRUE.equals(empty);
    }

    /**
     * v48: «Recuperar todo desde Drive»: primero los ajustes (plan, tareas, bloqueos, preferencias)
     * y después el historial de los JSON diarios. Desde ahí este teléfono sube su copia cada noche.
     */
    public static String all(Context c) {
        String settings;
        try {
            String token = GoogleDrive.token(c);
            String folder = DriveBackup.settingsFolder(token);
            String id = null;
            for (Map.Entry<String, String> f : GoogleApi.children(token, folder).entrySet())
                if (SettingsSnapshot.FILE.equals(f.getValue())) id = f.getKey();
            if (id == null) settings = "No hay copia de ajustes en tu Drive.";
            else {
                settings =
                        SettingsSnapshot.restore(
                                c,
                                new String(GoogleApi.download(token, id), StandardCharsets.UTF_8));
                if (!settings.startsWith("Modo estricto")) BackupStore.setSettingsDecided(c);
            }
        } catch (GoogleDrive.NeedsReconnect e) {
            return es.foco.next.core.backup.BackupLines.RECONNECT;
        } catch (Exception e) {
            return "No pude leer tu Drive ahora. Prueba más tarde con internet.";
        }
        Boolean empty =
                es.foco.next.log.BlockLog.await(c, LogDatabase::inspectorEmpty, 5_000);
        String history = run(c);
        return settings
                + (Boolean.FALSE.equals(empty) && history.startsWith("No encontré") ? "" : " " + history);
    }

    /** Recupera y devuelve el texto para la persona. Hilo de fondo. */
    public static String run(Context c) {
        try {
            String token = GoogleDrive.token(c);
            String foco = GoogleApi.folder(token, "Foco", "root");
            String reports = GoogleApi.folder(token, "Reportes", foco);
            List<LogDatabase.InspectorRow> rows = new ArrayList<>();
            int days = 0;
            for (String month : GoogleApi.subfolders(token, reports).keySet())
                for (Map.Entry<String, String> f : GoogleApi.children(token, month).entrySet()) {
                    if (!f.getValue().endsWith(".json")) continue;
                    try {
                        String text =
                                new String(
                                        GoogleApi.download(token, f.getKey()),
                                        StandardCharsets.UTF_8);
                        if (parse(new JSONObject(text), rows)) days++;
                    } catch (Exception ignored) {
                        // Un archivo dañado no frena el resto.
                    }
                }
            if (days == 0) return "No encontré reportes de Foco en tu Drive.";
            Integer n = es.foco.next.log.BlockLog.await(c, db -> db.restoreSummary(rows), 20_000);
            return "Listo: recuperé "
                    + days
                    + (days == 1 ? " día" : " días")
                    + " de tu historial"
                    + (n == null ? "." : " (" + n + " registros).");
        } catch (GoogleDrive.NeedsReconnect e) {
            return es.foco.next.core.backup.BackupLines.RECONNECT;
        } catch (Exception e) {
            return "No pude leer tu Drive ahora. Prueba más tarde con internet.";
        }
    }

    /** Un JSON de día → filas de resumen. false si no es un reporte de Foco. */
    static boolean parse(JSONObject o, List<LogDatabase.InspectorRow> out) {
        String day = o.optString("fecha", "");
        if (o.optInt("formato", 0) < 1 || !day.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        apps(o.optJSONArray("apps_productivas"), day, "uso_productivo", out);
        apps(o.optJSONArray("ocio_por_app"), day, "ocio", out);
        int retos = o.optInt("retos", 0);
        if (retos > 0)
            out.add(new LogDatabase.InspectorRow(day, -1, "reto_completado", "", 0, retos, 0, ""));
        long won = o.optLong("minutos_ganados", 0), spent = o.optLong("minutos_gastados", 0);
        if (won > 0)
            out.add(new LogDatabase.InspectorRow(day, -1, "minutos_ganados", "", 0, 0, won, ""));
        if (spent > 0)
            out.add(new LogDatabase.InspectorRow(day, -1, "minutos_gastados", "", 0, 0, spent, ""));
        return true;
    }

    private static void apps(
            JSONArray a, String day, String type, List<LogDatabase.InspectorRow> out) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONObject x = a.optJSONObject(i);
            if (x == null) continue;
            String pkg = x.optString("paquete", "");
            long min = x.optLong("min", 0);
            if (pkg.isEmpty() || min <= 0) continue;
            out.add(new LogDatabase.InspectorRow(day, -1, type, pkg, min * 60_000L, 0, 0, ""));
        }
    }
}
