package es.foco.next.runtime;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.SharedPreferences;
import es.foco.next.log.InspectorLog;

/**
 * Etapa 1.5 y 2.1: ¿Foco es el inicio predeterminado? Si lo era y deja de serlo, se registra
 * "launcher_cambiado" la siguiente vez que se comprueba (al volver a Foco o al desbloquear).
 */
public final class HomeRole {
    private HomeRole() {}

    public static boolean held(Context c) {
        try {
            RoleManager r = c.getSystemService(RoleManager.class);
            return r != null && r.isRoleAvailable(RoleManager.ROLE_HOME) && r.isRoleHeld(RoleManager.ROLE_HOME);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Compara con la última vez y registra el cambio. Devuelve el estado actual. */
    public static boolean check(Context c) {
        boolean now = held(c);
        try {
            SharedPreferences p =
                    c.getApplicationContext().getSharedPreferences("foco-home-role", Context.MODE_PRIVATE);
            boolean was = p.getBoolean("held", false);
            if (was && !now)
                InspectorLog.event(c, InspectorLog.LAUNCHER_CAMBIADO, "", System.currentTimeMillis(),
                        0, 1, 0, "inicio_predeterminado_ya_no_es_foco", null);
            if (was != now) p.edit().putBoolean("held", now).apply();
        } catch (RuntimeException ignored) {
        }
        OwlWatch.roleCheck(c, now); // el búho avisa una vez al día si dejó de ser el inicio
        return now;
    }
}
