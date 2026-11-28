package es.foco.next.runtime;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.PowerManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Auditoría: utilidades compartidas por FocusAccessibilityService y GuardService (antes duplicadas
 * en ambos). Solo lecturas del sistema; nunca lanzan.
 */
final class DeviceState {
    private DeviceState() {}

    /** Pantalla encendida y desbloqueada. */
    static boolean awake(Context c) {
        try {
            PowerManager p = c.getSystemService(PowerManager.class);
            KeyguardManager k = c.getSystemService(KeyguardManager.class);
            return p != null && p.isInteractive() && k != null && !k.isKeyguardLocked();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Paquetes con actividad de Inicio (launchers). */
    static Set<String> homes(Context c) {
        Set<String> out = new HashSet<>();
        try {
            for (ResolveInfo r :
                    c.getPackageManager()
                            .queryIntentActivities(
                                    new Intent(Intent.ACTION_MAIN)
                                            .addCategory(Intent.CATEGORY_HOME),
                                    0))
                if (r.activityInfo != null) out.add(r.activityInfo.packageName);
        } catch (RuntimeException ignored) {
        }
        return out;
    }

    /**
     * Caché de "tiene icono en el lanzador" (una consulta a PackageManager por paquete). Solo hilo
     * principal.
     */
    static final class Launchable {
        private static final int MAX = 500;
        private final Map<String, Boolean> cache = new HashMap<>();

        boolean has(Context c, String pkg) {
            Boolean l = cache.get(pkg);
            if (l == null) {
                try {
                    l = c.getPackageManager().getLaunchIntentForPackage(pkg) != null;
                } catch (RuntimeException e) {
                    l = false;
                }
                if (cache.size() > MAX) cache.clear();
                cache.put(pkg, l);
            }
            return l;
        }

        void forget(String pkg) {
            cache.remove(pkg);
        }
    }
}
