package es.foco.next.runtime;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import es.foco.next.core.StrictLock;
import es.foco.next.data.Repository;

/**
 * Modo estricto: administrador de dispositivo sin políticas. Solo impide desinstalar Foco
 * directamente.
 */
public final class StrictAdmin extends DeviceAdminReceiver {
    private static final String PREFS = "foco_strict", KEY = "on", LOCK = "lock";

    public static ComponentName component(Context c) {
        return new ComponentName(c, StrictAdmin.class);
    }

    public static boolean adminActive(Context c) {
        try {
            DevicePolicyManager d = c.getSystemService(DevicePolicyManager.class);
            return d != null && d.isAdminActive(component(c));
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static Intent activationIntent(Context c) {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(c))
                .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        "Modo estricto de Foco: mientras sea administrador, Android no deja desinstalar Foco directamente. No usa ninguna política: no bloquea, borra ni vigila el teléfono. Para quitarlo: espera a que termine el candado del modo estricto, apágalo en Foco y desactiva el administrador en Ajustes.");
    }

    public static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply();
    }

    // Modo estricto reforzado: candado de tiempo (StrictLock, en foco-core). Se observa el reloj en
    // cada lectura.
    /** Candado actual, con el reloj ya observado y guardado. */
    public static synchronized StrictLock lock(Context c) {
        StrictLock l =
                StrictLock.decode(
                        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LOCK, null));
        if (!l.active) return l;
        long pw = l.lastWall, pe = l.lastElapsed;
        int pb = l.lastBoot;
        long w = System.currentTimeMillis(), e = SystemClock.elapsedRealtime();
        int b = Repository.boot(c);
        // Auditoría: antes se guardaba en cada lectura (cada segundo con el respaldo activo). Ahora
        // solo cuando importa; sin
        // guardar, la siguiente lectura parte de la última observación guardada y el contador
        // avanza igual.
        if (l.observe(w, e, b) && mustPersist(pw, pe, pb, w, e, b)) save(c, l);
        return l;
    }

    /**
     * Guardar si hay reinicio, si el reloj de pared salta respecto al monótono (más de 5 s) o cada
     * minuto.
     */
    static boolean mustPersist(long pw, long pe, int pb, long w, long e, int b) {
        if (b != pb || e < pe || e - pe >= PERSIST_EVERY) return true;
        return Math.abs((w - pw) - (e - pe)) > CLOCK_JUMP;
    }

    private static final long PERSIST_EVERY = 60_000, CLOCK_JUMP = 5_000;

    /**
     * Cambio de hora o apagado: observar y escribir ya (commit síncrono), sin esperar a la próxima
     * lectura.
     */
    public static synchronized void persistNow(Context c) {
        StrictLock l =
                StrictLock.decode(
                        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LOCK, null));
        if (!l.active) return;
        l.observe(System.currentTimeMillis(), SystemClock.elapsedRealtime(), Repository.boot(c));
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(LOCK, l.encode())
                .commit();
    }

    public static synchronized void save(Context c, StrictLock l) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(LOCK, l.encode())
                .apply();
    }

    /** Modo estricto encendido y su candado de tiempo aún sin cumplir. */
    /**
     * Foco 1.0, etapa 4: durante el Weekend Lock la configuración queda protegida igual que con el
     * candado (solo se puede endurecer).
     */
    public static boolean lockActive(Context c) {
        if (Weekend.active(c)) return true;
        try {
            return enabled(c) && lock(c).locked();
        } catch (RuntimeException e) {
            return enabled(c);
        }
    }

    /** Enciende el modo estricto con candado: {@code millis<0} = "siempre". */
    public static synchronized void enableLocked(Context c, long millis) {
        StrictLock l = lock(c);
        long w = System.currentTimeMillis(), e = SystemClock.elapsedRealtime();
        int b = Repository.boot(c);
        if (millis < 0) l.lockAlways(w, e, b);
        else l.lockFor(millis, w, e, b);
        save(c, l);
        setEnabled(c, true);
        GuardService.ensure(c);
    }

    /** Apaga el modo estricto si el candado lo permite. */
    public static synchronized boolean disable(Context c) {
        StrictLock l = lock(c);
        if (!l.release()) return false;
        save(c, l);
        setEnabled(c, false);
        GuardService.ensure(c);
        return true;
    }

    public static synchronized boolean requestUnlock(Context c) {
        StrictLock l = lock(c);
        boolean ok =
                l.requestUnlock(
                        System.currentTimeMillis(),
                        SystemClock.elapsedRealtime(),
                        Repository.boot(c));
        if (ok) save(c, l);
        return ok;
    }

    public static synchronized boolean cancelRequest(Context c) {
        StrictLock l = lock(c);
        boolean ok = l.cancelRequest();
        if (ok) save(c, l);
        return ok;
    }

    @Override
    public CharSequence onDisableRequested(Context c, Intent i) {
        return "Foco está en modo estricto. Si lo quitas antes de que termine el candado, te estarás saltando tu propio compromiso. Para hacerlo bien: espera a que termine y apágalo en Foco.";
    }
}
