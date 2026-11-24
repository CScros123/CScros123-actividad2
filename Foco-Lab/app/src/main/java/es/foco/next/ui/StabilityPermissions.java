package es.foco.next.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.Toast;
import es.foco.next.log.UsageContrast;

/**
 * Fase 4: the two permissions that add real data and stability (plan 5A): usage access
 * (PACKAGE_USAGE_STATS, granted by the user in Android settings) and the battery-optimisation
 * exemption, plus the One UI guide. Nothing here is required for blocking.
 */
final class StabilityPermissions {
    private StabilityPermissions() {}

    static boolean batteryUnrestricted(Context c) {
        try {
            PowerManager p = c.getSystemService(PowerManager.class);
            return p != null && p.isIgnoringBatteryOptimizations(c.getPackageName());
        } catch (RuntimeException e) {
            return false;
        }
    }

    static void card(LinearLayout body, Activity a) {
        LinearLayout box = Design.card(body, "Permisos de estabilidad");
        boolean usage = UsageContrast.allowed(a);
        Design.note(box, usage ? "Acceso de uso · concedido" : "Acceso de uso · sin conceder");
        Design.note(
                box,
                "Sirve para contrastar en el Registro el tiempo que midió Accesibilidad con el historial de uso de Android, también si el servicio estuvo suspendido. El bloqueo sigue usando Accesibilidad. Foco lo consulta solo al abrir el Registro y una vez al día.");
        if (!usage)
            box.addView(Design.button(a, "Abrir Acceso de uso", false, () -> openUsageAccess(a)));
        boolean battery = batteryUnrestricted(a);
        Design.note(
                box, battery ? "Batería · sin restricciones" : "Batería · optimizada por Android");
        Design.note(
                box,
                "One UI puede dormir el servicio de Foco y entonces la pausa no aparece. En Samsung: Ajustes › Aplicaciones › Foco › Batería › Sin restricciones. Si Foco figura en Aplicaciones en suspensión o en suspensión profunda (Ajustes › Mantenimiento del dispositivo › Batería › Límites de uso en segundo plano), quítalo de esas listas.");
        if (!battery)
            box.addView(
                    Design.button(
                            a,
                            "Permitir sin restricciones de batería",
                            false,
                            () -> requestBattery(a)));
        box.addView(
                Design.button(
                        a,
                        "Abrir ajustes de Foco",
                        false,
                        () ->
                                open(
                                        a,
                                        new Intent(
                                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                                Uri.parse("package:" + a.getPackageName())))));
    }

    static void openUsageAccess(Activity a) {
        // Some versions open Foco's own page with the package URI; others only accept the plain
        // list.
        try {
            a.startActivity(
                    new Intent(
                            Settings.ACTION_USAGE_ACCESS_SETTINGS,
                            Uri.parse("package:" + a.getPackageName())));
        } catch (RuntimeException e) {
            open(a, new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }
    }

    /** Plan 5A: allowed exemption. The system shows its own confirmation dialog. */
    @SuppressLint("BatteryLife")
    static void requestBattery(Activity a) {
        try {
            a.startActivity(
                    new Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:" + a.getPackageName())));
        } catch (RuntimeException e) {
            open(a, new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private static void open(Activity a, Intent i) {
        try {
            a.startActivity(i);
        } catch (RuntimeException e) {
            Toast.makeText(a, "Abre este ajuste desde Ajustes del teléfono.", Toast.LENGTH_LONG)
                    .show();
        }
    }
}
