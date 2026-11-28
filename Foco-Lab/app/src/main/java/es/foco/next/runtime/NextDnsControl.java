package es.foco.next.runtime;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import es.foco.next.core.AbsoluteRules;
import es.foco.next.core.NextDnsStats;
import es.foco.next.core.backup.NextDnsPlan;
import es.foco.next.core.care.CareMode;
import es.foco.next.log.InspectorLog;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * v47: Foco pone el perfil de NextDNS como toca según el modo y la hora (reglas en {@link
 * NextDnsPlan}): redes sociales por servicio en Enfoque y de noche; Telegram siempre en la lista de
 * bloqueo. Lo comprueba cada 30 min, al cambiar de modo y justo en cada cambio de horario (fin del
 * Enfoque, 21:00, 07:00). Sin internet lo reintenta WorkManager; el bloqueo por Accesibilidad sigue
 * igual. No hay "desbloquear ahora".
 */
public final class NextDnsControl {
    private NextDnsControl() {}

    private static final String PREFS = "foco-nextdns-control";
    static final String WORK_PERIODIC = "foco-nextdns-cada-30",
            WORK_NOW = "foco-nextdns-ya",
            WORK_EDGE = "foco-nextdns-horario";

    public enum Outcome {
        /** Sin clave o sin perfil: nada que hacer. */
        SKIP,
        OK,
        /** Sin red o NextDNS no respondió: se reintenta. */
        RETRY,
        /** La clave no vale para ese perfil: no tiene sentido reintentar. */
        DENIED
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static Constraints online() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    /** Comprobación cada 30 min (idempotente: se puede llamar en cada arranque). */
    public static void schedule(Context c) {
        try {
            if (!NextDnsSync.hasKey(c)) return;
            PeriodicWorkRequest w =
                    new PeriodicWorkRequest.Builder(
                                    NextDnsWorker.class,
                                    NextDnsPlan.CHECK_EVERY_MS,
                                    TimeUnit.MILLISECONDS)
                            .setConstraints(online())
                            .build();
            WorkManager.getInstance(c.getApplicationContext())
                    .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, w);
        } catch (RuntimeException ignored) {
            // WorkManager no disponible (pruebas): el bloqueo por Accesibilidad sigue igual.
        }
    }

    /** Cambió el modo (o la clave): aplicar ya, en cuanto haya red. */
    public static void request(Context c) {
        try {
            if (!NextDnsSync.hasKey(c)) return;
            OneTimeWorkRequest w =
                    new OneTimeWorkRequest.Builder(NextDnsWorker.class)
                            .setConstraints(online())
                            .build();
            WorkManager.getInstance(c.getApplicationContext())
                    .enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, w);
            schedule(c);
        } catch (RuntimeException ignored) {
        }
    }

    /** Programa la próxima comprobación justo en el siguiente cambio de horario. */
    static void scheduleEdge(Context c) {
        try {
            CareStore.install(c);
            long now = System.currentTimeMillis();
            CareMode.State s = CareStore.state(c);
            long focusUntil = CareStore.mode(c) == CareMode.Modo.ENFOQUE ? s.enfoqueHasta : -1;
            long at =
                    NextDnsPlan.nextBoundary(
                            ZonedDateTime.now(),
                            focusUntil,
                            AbsoluteRules.nightStart(),
                            AbsoluteRules.nightEnd());
            OneTimeWorkRequest w =
                    new OneTimeWorkRequest.Builder(NextDnsWorker.class)
                            .setConstraints(online())
                            .setInitialDelay(Math.max(0, at - now) + 5_000, TimeUnit.MILLISECONDS)
                            .build();
            WorkManager.getInstance(c.getApplicationContext())
                    .enqueueUniqueWork(WORK_EDGE, ExistingWorkPolicy.REPLACE, w);
        } catch (RuntimeException ignored) {
        }
    }

    /** Lo que hay que bloquear ahora mismo por servicio. */
    static boolean expectedBlock(Context c) {
        CareStore.install(c);
        return NextDnsPlan.blockServices(
                CareStore.mode(c), AbsoluteRules.esNocheAbsoluta(LocalDateTime.now()));
    }

    /** Compara NextDNS con lo esperado y lo corrige. Hilo de fondo (WorkManager). */
    static synchronized Outcome apply(Context c) {
        String key = NextDnsSync.key(c), id = NextDnsSync.profile(c);
        if (!NextDnsStats.validKey(key) || id == null) return Outcome.SKIP;
        SharedPreferences p = prefs(c);
        boolean block = expectedBlock(c);
        String base = NextDnsApi.BASE + id + "/";
        try {
            java.util.Map<String, Boolean> services =
                    NextDnsApi.actives(NextDnsApi.get(base + NextDnsPlan.SERVICES_PATH, key));
            java.util.Map<String, Boolean> deny =
                    NextDnsApi.actives(NextDnsApi.get(base + NextDnsPlan.DENYLIST_PATH, key));
            Set<String> unsupported = new HashSet<>(p.getStringSet("no_admitidos", new HashSet<>()));
            List<NextDnsPlan.Op> ops = NextDnsPlan.diff(block, services, deny);
            ops.removeIf(
                    o ->
                            o.list.equals(NextDnsPlan.SERVICES_PATH)
                                    && unsupported.contains(o.id));
            boolean outside = NextDnsPlan.changedOutside(ops, p.getString("aplicado", ""), block);
            int done = 0;
            for (NextDnsPlan.Op o : ops) {
                try {
                    if (o.kind == NextDnsPlan.Op.Kind.ADD)
                        NextDnsApi.post(
                                base + o.list,
                                key,
                                "{\"id\":"
                                        + es.foco.next.core.backup.Json.quote(o.id)
                                        + ",\"active\":true}");
                    else
                        NextDnsApi.delete(
                                base + o.list + "/" + java.net.URLEncoder.encode(o.id, "UTF-8"),
                                key);
                    done++;
                } catch (NextDnsApi.HttpError e) {
                    if (e.code == 401 || e.code == 403) throw e;
                    // Un servicio que NextDNS no reconoce: no se vuelve a intentar cada 30 min.
                    if (e.code == 400 && o.list.equals(NextDnsPlan.SERVICES_PATH))
                        unsupported.add(o.id);
                }
            }
            p.edit()
                    .putString("aplicado", NextDnsPlan.signature(block))
                    .putLong("comprobado", System.currentTimeMillis())
                    .putStringSet("no_admitidos", unsupported)
                    .putString(
                            "estado",
                            (block ? "Redes bloqueadas por servicio" : "Redes sin bloqueo por servicio")
                                    + " · Telegram bloqueado")
                    .apply();
            // Alguien lo cambió desde la web: ya quedó como debe. Se apunta, sin culpa.
            if (outside && done > 0)
                InspectorLog.event(
                        c,
                        InspectorLog.NEXTDNS_CAMBIADO,
                        "",
                        done,
                        "restaurado:" + (block ? "enfoque_o_noche" : "normal"),
                        null);
            scheduleEdge(c);
            return Outcome.OK;
        } catch (NextDnsApi.HttpError e) {
            p.edit()
                    .putString(
                            "estado",
                            e.code == 401 || e.code == 403
                                    ? "La clave de API no tiene acceso a este perfil."
                                    : "NextDNS respondió " + e.code + "; lo intento más tarde.")
                    .apply();
            return e.code == 401 || e.code == 403 ? Outcome.DENIED : Outcome.RETRY;
        } catch (Exception e) {
            return Outcome.RETRY;
        }
    }

    /** Texto de estado para Ajustes (sin la clave). */
    public static String status(Context c) {
        SharedPreferences p = prefs(c);
        long at = p.getLong("comprobado", 0);
        String s = p.getString("estado", "");
        if (at <= 0) return s.isEmpty() ? "Aún sin comprobar." : s;
        String when =
                java.time.Instant.ofEpochMilli(at)
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern("H:mm"));
        Set<String> no = p.getStringSet("no_admitidos", new HashSet<>());
        return s
                + " (comprobado a las "
                + when
                + ")"
                + (no.isEmpty() ? "" : ". NextDNS no reconoce: " + String.join(", ", no) + ".");
    }
}
