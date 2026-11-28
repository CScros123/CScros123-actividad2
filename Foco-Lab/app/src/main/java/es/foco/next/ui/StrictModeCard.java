package es.foco.next.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.LinearLayout;
import es.foco.next.core.StrictLock;
import es.foco.next.log.UsageContrast;
import es.foco.next.runtime.FocusAccessibilityService;
import es.foco.next.runtime.GuardService;
import es.foco.next.runtime.StrictAdmin;
import java.time.*;

/**
 * Modo estricto reforzado: administrador de dispositivo + candado de tiempo + cierre de pantallas
 * peligrosas de Ajustes + respaldo sin Accesibilidad (GuardService).
 */
final class StrictModeCard {
    private StrictModeCard() {}

    static void add(MainActivity a, LinearLayout body) {
        boolean on = StrictAdmin.enabled(a),
                admin = StrictAdmin.adminActive(a),
                acc = FocusAccessibilityService.connected();
        boolean overlay = Settings.canDrawOverlays(a),
                usage = UsageContrast.allowed(a),
                battery = StabilityPermissions.batteryUnrestricted(a);
        StrictLock lock = StrictAdmin.lock(a);
        boolean locked = on && lock.locked();
        if (locked) GuardService.ensure(a);
        LinearLayout card = Design.card(body, "Modo estricto");
        Design.note(
                card,
                "Con el candado puesto, Foco cierra siempre las pantallas que lo desinstalarían o desactivarían (ficha de Foco, su Accesibilidad, administradores, diálogo de desinstalación) y los bloqueos solo pueden volverse más estrictos. Durante un bloqueo también cierra DNS privado, VPN y fecha y hora. Si apagas la Accesibilidad, un respaldo sigue bloqueando con Acceso de uso.");
        Design.note(card, (on ? "✓ Activado" : "Desactivado") + " · " + lockText(on, lock));
        Design.note(
                card,
                "Accesibilidad: "
                        + mark(acc)
                        + " · Administrador: "
                        + mark(admin)
                        + " · Aparecer encima: "
                        + mark(overlay)
                        + " · Acceso de uso: "
                        + mark(usage)
                        + " · Batería sin restricciones: "
                        + mark(battery));
        if (!on)
            card.addView(
                    Design.button(
                            a,
                            "Activar modo estricto",
                            true,
                            () -> {
                                if (!acc || !admin) {
                                    a.message(
                                            "Primero activa la Accesibilidad de Foco y el administrador de dispositivo.");
                                    return;
                                }
                                Runnable pick = () -> chooseDuration(a);
                                if (!overlay || !usage)
                                    new AlertDialog.Builder(a)
                                            .setTitle("Respaldo incompleto")
                                            .setMessage(
                                                    "Sin «Aparecer encima» y «Acceso de uso», si se desactiva la Accesibilidad Foco no podrá bloquear nada. Muy recomendable concederlos antes.")
                                            .setPositiveButton(
                                                    "Continuar igualmente", (d, w) -> pick.run())
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show();
                                else pick.run();
                            }));
        else if (locked && lock.always && !lock.requested())
            card.addView(
                    Design.button(
                            a,
                            "Solicitar desactivar (24 h de espera)",
                            false,
                            () -> {
                                if (StrictAdmin.requestUnlock(a))
                                    a.message(
                                            "Podrás apagarlo dentro de 24 h. Puedes cancelar la solicitud.");
                                a.render();
                            }));
        else if (locked && lock.requested())
            card.addView(
                    Design.button(
                            a,
                            "Cancelar la solicitud",
                            false,
                            () -> {
                                StrictAdmin.cancelRequest(a);
                                a.render();
                            }));
        else if (locked)
            card.addView(
                    Design.button(
                            a,
                            "Desactivar modo estricto",
                            false,
                            () ->
                                    a.message(
                                            "El candado sigue activo: "
                                                    + lockText(true, StrictAdmin.lock(a))
                                                    + ".")));
        else
            card.addView(
                    Design.button(
                            a,
                            "Desactivar modo estricto",
                            false,
                            () -> {
                                if (a.repository.locked()) {
                                    a.message("Podrás cambiarlo al terminar esta ventana.");
                                    return;
                                }
                                // Auditoría: también un bloqueo con nombre o una sesión de foco en
                                // curso (antes solo el Foco clásico).
                                if (es.foco.next.data.RuleStore.get(a).anyActive()) {
                                    a.message(
                                            "Hay un bloqueo activo. Podrás apagar el modo estricto cuando termine.");
                                    return;
                                }
                                if (!StrictAdmin.disable(a)) a.message("El candado sigue activo.");
                                a.render();
                            }));
        if (on && !locked)
            card.addView(
                    Design.button(
                            a,
                            "Poner candado de tiempo",
                            false,
                            () -> {
                                if (!acc || !admin) {
                                    a.message(
                                            "Primero activa la Accesibilidad de Foco y el administrador de dispositivo.");
                                    return;
                                }
                                chooseDuration(a);
                            }));
        if (!acc)
            card.addView(
                    Design.button(
                            a,
                            "Activar Accesibilidad",
                            false,
                            () -> open(a, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        if (!admin)
            card.addView(
                    Design.button(
                            a,
                            "Activar administrador de dispositivo",
                            false,
                            () -> {
                                try {
                                    a.startActivity(StrictAdmin.activationIntent(a));
                                } catch (ActivityNotFoundException | SecurityException e) {
                                    a.message(
                                            "Este teléfono no permitió abrir la pantalla de administrador.");
                                }
                            }));
        if (!overlay)
            card.addView(
                    Design.button(
                            a,
                            "Permitir «Aparecer encima»",
                            false,
                            () ->
                                    open(
                                            a,
                                            new Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:" + a.getPackageName())))));
        if (!usage)
            card.addView(
                    Design.button(
                            a,
                            "Permitir Acceso de uso",
                            false,
                            () -> StabilityPermissions.openUsageAccess(a)));
        if (!battery)
            card.addView(
                    Design.button(
                            a,
                            "Batería sin restricciones",
                            false,
                            () -> StabilityPermissions.requestBattery(a)));
        Design.note(
                card,
                "Límites honestos: el modo seguro, ADB o un restablecimiento de fábrica pueden saltárselo. Para desinstalar Foco: espera a que termine el candado, apaga el modo estricto → Ajustes › Seguridad › Apps de administración del dispositivo › quita Foco → desinstala.");
    }

    private static String mark(boolean b) {
        return b ? "sí" : "no";
    }

    private static String lockText(boolean on, StrictLock l) {
        if (!on || !l.active) return "sin candado";
        if (!l.locked()) return "candado terminado: ya puedes apagarlo";
        long r = l.remaining();
        if (r < 0) return "candado: siempre (solicita desactivar y espera 24 h)";
        return (l.always ? "desactivación solicitada · faltan " : "candado · faltan ")
                + FocusOverlay.span(r);
    }

    private static void open(MainActivity a, Intent i) {
        try {
            a.startActivity(i);
        } catch (RuntimeException e) {
            a.message("Abre este ajuste desde Ajustes del teléfono.");
        }
    }

    private static void chooseDuration(MainActivity a) {
        String[] items = {
            "1 hora",
            "4 horas",
            "8 horas",
            "Hasta mañana a las 07:00",
            "Hasta una fecha y hora",
            "Siempre (24 h de espera para desactivar)"
        };
        new AlertDialog.Builder(a)
                .setTitle("¿Durante cuánto tiempo no podrás apagarlo?")
                .setItems(
                        items,
                        (d, w) -> {
                            switch (w) {
                                case 0:
                                    enable(a, StrictLock.HOUR);
                                    break;
                                case 1:
                                    enable(a, 4 * StrictLock.HOUR);
                                    break;
                                case 2:
                                    enable(a, 8 * StrictLock.HOUR);
                                    break;
                                case 3:
                                    enable(a, StrictLock.untilSeven(ZonedDateTime.now()));
                                    break;
                                case 4:
                                    pickDate(a);
                                    break;
                                default:
                                    new AlertDialog.Builder(a)
                                            .setTitle("¿Siempre?")
                                            .setMessage(
                                                    "Para apagarlo tendrás que pulsar «Solicitar desactivar» y esperar 24 horas.")
                                            .setPositiveButton("Activar", (d2, w2) -> enable(a, -1))
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static void pickDate(MainActivity a) {
        LocalDate t = LocalDate.now();
        DatePickerDialog dp =
                new DatePickerDialog(
                        a,
                        (v, y, m, day) -> {
                            LocalTime now = LocalTime.now();
                            new TimePickerDialog(
                                            a,
                                            (tv, h, min) -> {
                                                long ms =
                                                        StrictLock.until(
                                                                ZonedDateTime.now(),
                                                                LocalDateTime.of(
                                                                        y, m + 1, day, h, min));
                                                if (ms <= 0) {
                                                    a.message("Esa fecha ya pasó.");
                                                    return;
                                                }
                                                enable(a, ms);
                                            },
                                            now.getHour(),
                                            now.getMinute(),
                                            true)
                                    .show();
                        },
                        t.getYear(),
                        t.getMonthValue() - 1,
                        t.getDayOfMonth());
        dp.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dp.show();
    }

    private static void enable(MainActivity a, long millis) {
        StrictAdmin.enableLocked(a, millis);
        a.message(
                millis < 0
                        ? "Modo estricto activado para siempre."
                        : "Modo estricto activado · " + FocusOverlay.span(millis));
        a.render();
    }
}
