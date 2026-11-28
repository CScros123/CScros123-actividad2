package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import es.foco.next.core.backup.ReportQueue;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * v47: estado del respaldo en Drive (preferencias privadas): si está conectado, la cuenta, los IDs
 * de carpetas y hoja, el último día subido y el último envío. Sin tokens ni claves.
 */
public final class BackupStore {
    private BackupStore() {}

    private static final String PREFS = "foco-respaldo";
    static final String WORK_NIGHTLY = "foco-respaldo-noche", WORK_NOW = "foco-respaldo-ya";

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean connected(Context c) {
        return prefs(c).getBoolean("conectado", false);
    }

    static void markConnected(Context c) {
        SharedPreferences p = prefs(c);
        SharedPreferences.Editor e = p.edit().putBoolean("conectado", true).remove("reconectar");
        if (p.getString("desde", "").isEmpty())
            e.putString("desde", RoutineStore.today().toString());
        e.apply();
    }

    /** Desconectar: Foco deja de subir. Lo que ya está en Drive sigue ahí. */
    public static void disconnect(Context c) {
        prefs(c).edit()
                .putBoolean("conectado", false)
                .remove("cuenta")
                .remove("reconectar")
                .apply();
        try {
            WorkManager w = WorkManager.getInstance(c.getApplicationContext());
            w.cancelUniqueWork(WORK_NIGHTLY);
            w.cancelUniqueWork(WORK_NOW);
        } catch (RuntimeException ignored) {
        }
    }

    static void setAccount(Context c, String email) {
        if (email != null && !email.isEmpty()) prefs(c).edit().putString("cuenta", email).apply();
    }

    public static String account(Context c) {
        return prefs(c).getString("cuenta", "");
    }

    /** El permiso caducó: Ajustes lo muestra y el búho avisa una vez. */
    public static boolean needsReconnect(Context c) {
        return prefs(c).getBoolean("reconectar", false);
    }

    static void setNeedsReconnect(Context c, boolean v) {
        prefs(c).edit().putBoolean("reconectar", v).apply();
    }

    /**
     * v48: este teléfono ya decidió (recuperó la copia de ajustes, empezó de cero o era el primero):
     * desde ahí su copia de ajustes se sube cada noche.
     */
    static boolean settingsDecided(Context c) {
        return prefs(c).getBoolean("ajustes_ok", false);
    }

    static void setSettingsDecided(Context c) {
        prefs(c).edit().putBoolean("ajustes_ok", true).putBoolean("ajustes_pendiente", false).apply();
    }

    /** Hay una copia de ajustes en Drive esperando a que decidas (recién instalado). */
    public static boolean restorePending(Context c) {
        return prefs(c).getBoolean("ajustes_pendiente", false);
    }

    static void setRestorePending(Context c, boolean v) {
        prefs(c).edit().putBoolean("ajustes_pendiente", v).apply();
    }

    /** «Empezar de cero»: no recuperar y, desde esta noche, subir los ajustes de este teléfono. */
    public static void startFresh(Context c) {
        setSettingsDecided(c);
    }

    /** El ánimo solo se sube si la persona lo activa (apagado por defecto). */
    public static boolean includeMood(Context c) {
        return prefs(c).getBoolean("animo", false);
    }

    public static void setIncludeMood(Context c, boolean v) {
        prefs(c).edit().putBoolean("animo", v).apply();
    }

    static LocalDate connectedDay(Context c) {
        return parse(prefs(c).getString("desde", ""));
    }

    static LocalDate lastComplete(Context c) {
        return parse(prefs(c).getString("ultimo_completo", ""));
    }

    static void setLastComplete(Context c, LocalDate d) {
        if (d != null) prefs(c).edit().putString("ultimo_completo", d.toString()).apply();
    }

    static LocalDate parse(String s) {
        try {
            return s == null || s.isEmpty() ? null : LocalDate.parse(s);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String id(Context c, String name) {
        return prefs(c).getString("id_" + name, "");
    }

    static void setId(Context c, String name, String id) {
        prefs(c).edit().putString("id_" + name, id == null ? "" : id).apply();
    }

    /** Último envío correcto (ms) y su día; los IDs para abrir la hoja y el gráfico. */
    public static long lastSentAt(Context c) {
        return prefs(c).getLong("enviado_en", 0);
    }

    static void sent(Context c, LocalDate day, String pngId) {
        prefs(c).edit()
                .putLong("enviado_en", System.currentTimeMillis())
                .putString("enviado_dia", day.toString())
                .putString("ultimo_png", pngId == null ? "" : pngId)
                .apply();
    }

    public static String sheetUrl(Context c) {
        String id = id(c, "hoja");
        return id.isEmpty() ? "" : "https://docs.google.com/spreadsheets/d/" + id + "/edit";
    }

    public static String chartUrl(Context c) {
        String id = prefs(c).getString("ultimo_png", "");
        return id.isEmpty() ? "" : "https://drive.google.com/file/d/" + id + "/view";
    }

    /** Frases del búho de los últimos días (para no repetir en 3 días). */
    static List<Integer> recentLines(Context c) {
        List<Integer> out = new ArrayList<>();
        for (String s : prefs(c).getString("frases", "").split(","))
            try {
                if (!s.isEmpty()) out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        return out;
    }

    static void usedLine(Context c, int i) {
        List<Integer> l = recentLines(c);
        l.add(i);
        while (l.size() > 5) l.remove(0);
        StringBuilder b = new StringBuilder();
        for (Integer v : l) b.append(b.length() == 0 ? "" : ",").append(v);
        prefs(c).edit().putString("frases", b.toString()).apply();
    }

    /** Un solo aviso suave de fallo por día. true si hay que mostrarlo. */
    static boolean failureNoticeDue(Context c) {
        String today = RoutineStore.today().toString();
        SharedPreferences p = prefs(c);
        if (today.equals(p.getString("aviso_fallo", ""))) return false;
        p.edit().putString("aviso_fallo", today).apply();
        return true;
    }

    static boolean reconnectNoticeDue(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("aviso_reconectar", false)) return false;
        p.edit().putBoolean("aviso_reconectar", true).apply();
        return true;
    }

    static void clearReconnectNotice(Context c) {
        prefs(c).edit().remove("aviso_reconectar").apply();
    }

    private static Constraints constraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();
    }

    /**
     * Trabajo de cada noche (cada 24 h, empezando a las 21:00) con red y batería no baja, y
     * reintentos con espera creciente. Idempotente: se llama en cada arranque.
     */
    public static void schedule(Context c) {
        try {
            if (!connected(c)) return;
            long delay = ReportQueue.minutesUntilSend(LocalDateTime.now());
            PeriodicWorkRequest w =
                    new PeriodicWorkRequest.Builder(BackupWorker.class, 24, TimeUnit.HOURS)
                            .setConstraints(constraints())
                            .setInitialDelay(delay, TimeUnit.MINUTES)
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                            .build();
            // v49: si cambió la hora de envío (23:30 → 21:00), se vuelve a programar una vez.
            String at = ReportQueue.SEND_AT.toString();
            boolean moved = !at.equals(prefs(c).getString("hora_envio", ""));
            WorkManager.getInstance(c.getApplicationContext())
                    .enqueueUniquePeriodicWork(
                            WORK_NIGHTLY,
                            moved
                                    ? ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE
                                    : ExistingPeriodicWorkPolicy.KEEP,
                            w);
            if (moved) prefs(c).edit().putString("hora_envio", at).apply();
        } catch (RuntimeException ignored) {
        }
    }

    /** «Subir ahora» desde Ajustes (también reintenta lo pendiente). */
    public static void sendNow(Context c) {
        try {
            if (!connected(c)) return;
            OneTimeWorkRequest w =
                    new OneTimeWorkRequest.Builder(BackupWorker.class)
                            .setConstraints(
                                    new Constraints.Builder()
                                            .setRequiredNetworkType(NetworkType.CONNECTED)
                                            .build())
                            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                            .build();
            WorkManager.getInstance(c.getApplicationContext())
                    .enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.KEEP, w);
        } catch (RuntimeException ignored) {
        }
    }

    /** Arranque de Foco: respaldo nocturno y comprobación de NextDNS. */
    public static void ensureScheduled(Context c) {
        schedule(c);
        NextDnsControl.schedule(c);
    }
}
