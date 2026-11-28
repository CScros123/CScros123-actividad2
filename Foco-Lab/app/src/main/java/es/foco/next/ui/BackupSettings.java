package es.foco.next.ui;

import android.widget.EditText;
import android.widget.LinearLayout;
import es.foco.next.core.NextDnsStats;
import es.foco.next.runtime.BackupStore;
import es.foco.next.runtime.DriveRestore;
import es.foco.next.runtime.GoogleDrive;
import es.foco.next.runtime.NextDnsControl;
import es.foco.next.runtime.NextDnsSync;

/**
 * v47: Ajustes de las conexiones (una sola vez): Google Drive para la copia de cada noche y la API
 * de NextDNS (clave cifrada y perfil). Sin botón de «desbloquear ahora»: el bloqueo de NextDNS lo
 * deciden el modo y el horario.
 */
final class BackupSettings {
    private BackupSettings() {}

    /** null = sin comprobar; se calcula en segundo plano (lee la base). */
    private static volatile Boolean restoreOffer;

    static void render(MainActivity a, LinearLayout body) {
        drive(a, body);
        nextDns(a, body);
    }

    private static String when(long ms) {
        return java.time.Instant.ofEpochMilli(ms)
                .atZone(java.time.ZoneId.systemDefault())
                .format(
                        java.time.format.DateTimeFormatter.ofPattern(
                                "EEEE d 'a las' H:mm", new java.util.Locale("es")));
    }

    private static void drive(MainActivity a, LinearLayout body) {
        LinearLayout card = Design.card(body, "Copia nocturna en Google Drive");
        boolean on = BackupStore.connected(a);
        if (!on) {
            Design.note(
                    card,
                    "Cada noche, sin que hagas nada, Foco guarda en tu Drive tu día (archivo, gráfico y hoja con tu historial) y todos tus ajustes. Si cambias de teléfono o reinstalas, lo recuperas con un botón. Solo en tu cuenta; Foco solo ve lo que él crea.");
            card.addView(
                    Design.button(
                            a,
                            "Conectar Google Drive",
                            true,
                            () -> GoogleDrive.connect(a, msg -> done(a, msg))));
            return;
        }
        String account = BackupStore.account(a);
        long sent = BackupStore.lastSentAt(a);
        Design.note(
                card,
                "Conectado"
                        + (account.isEmpty() ? "" : " como " + account)
                        + ". "
                        + (sent > 0
                                ? "Último envío: " + when(sent) + "."
                                : "Aún no hay envíos: el primero es esta noche."));
        if (BackupStore.needsReconnect(a)) {
            Design.note(card, "Vuelve a conectar Drive para seguir guardando tus reportes.");
            card.addView(
                    Design.button(
                            a,
                            "Volver a conectar Drive",
                            true,
                            () -> GoogleDrive.connect(a, msg -> done(a, msg))));
        }
        String sheet = BackupStore.sheetUrl(a);
        if (!sheet.isEmpty())
            card.addView(
                    Design.button(a, "Ver hoja «Foco · Historial»", false, () -> open(a, sheet)));
        card.addView(
                Design.button(
                        a,
                        "Guardar el día ahora",
                        false,
                        () -> {
                            BackupStore.sendNow(a);
                            a.message("Hecho. La copia se sube en cuanto haya internet.");
                        }));
        boolean mood = BackupStore.includeMood(a);
        card.addView(
                Design.button(
                        a,
                        mood ? "Ánimo en la copia: sí (quitar)" : "Ánimo en la copia: no (añadir)",
                        false,
                        () -> {
                            BackupStore.setIncludeMood(a, !mood);
                            a.render();
                        }));
        // Paso 6: recién instalado (base vacía) y con Drive, se ofrece recuperar.
        Boolean offer = restoreOffer;
        if (offer == null) {
            restoreOffer = Boolean.FALSE;
            new Thread(
                            () -> {
                                boolean show = DriveRestore.offer(a);
                                restoreOffer = show;
                                if (show) a.runOnUiThread(a::render);
                            },
                            "foco-recuperar-ver")
                    .start();
        } else if (offer) {
            Design.note(
                    card,
                    "Hay una copia tuya en Drive: plan, tareas, bloqueos, racha y cupones, noche, tema, sonido e historial. No incluye el candado ni la clave de NextDNS.");
            card.addView(
                    Design.button(
                            a,
                            "Recuperar todo desde Drive",
                            true,
                            () -> {
                                a.message("Recuperando tus ajustes e historial…");
                                new Thread(
                                                () -> {
                                                    String msg = DriveRestore.all(a);
                                                    restoreOffer = null;
                                                    a.runOnUiThread(
                                                            () -> {
                                                                a.message(msg);
                                                                a.render();
                                                            });
                                                },
                                                "foco-recuperar")
                                        .start();
                            }));
            if (BackupStore.restorePending(a))
                card.addView(
                        Design.button(
                                a,
                                "Empezar de cero (no recuperar)",
                                false,
                                () ->
                                        new android.app.AlertDialog.Builder(a)
                                                .setTitle("Empezar de cero")
                                                .setMessage(
                                                        "Esta noche Foco guardará los ajustes de este teléfono en lugar de tu copia anterior. ¿Seguro?")
                                                .setPositiveButton(
                                                        "Empezar de cero",
                                                        (d, w) -> {
                                                            BackupStore.startFresh(a);
                                                            restoreOffer = null;
                                                            a.render();
                                                        })
                                                .setNegativeButton("Cancelar", null)
                                                .show()));
        }
        card.addView(
                Design.button(
                        a,
                        "Desconectar Drive",
                        false,
                        () ->
                                new android.app.AlertDialog.Builder(a)
                                        .setTitle("Desconectar Drive")
                                        .setMessage(
                                                "Foco dejará de guardar tu día. Lo que ya está en tu Drive se queda ahí.")
                                        .setPositiveButton(
                                                "Desconectar",
                                                (d, w) -> {
                                                    BackupStore.disconnect(a);
                                                    a.render();
                                                })
                                        .setNegativeButton("Cancelar", null)
                                        .show()));
    }

    static void done(MainActivity a, String msg) {
        restoreOffer = null; // recién conectado: volver a mirar si hay copia en Drive
        a.runOnUiThread(
                () -> {
                    a.message(msg);
                    a.render();
                });
    }

    private static void open(MainActivity a, String url) {
        try {
            a.startActivity(
                    new android.content.Intent(
                            android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (RuntimeException e) {
            a.message("No hay una app para abrir el enlace.");
        }
    }

    private static void nextDns(MainActivity a, LinearLayout body) {
        LinearLayout card = Design.card(body, "NextDNS: control por modo");
        Design.note(
                card,
                "Con tu clave de API, Foco bloquea en tu perfil las redes sociales durante Enfoque y de noche, y Telegram siempre. Lo comprueba cada 30 min: si se cambia desde la web, lo vuelve a poner. No hay botón para desbloquear.");
        boolean key = NextDnsSync.hasKey(a);
        Design.note(
                card, key ? "Clave de API: guardada y cifrada ✓" : "Clave de API: sin guardar.");
        card.addView(
                Design.button(
                        a,
                        key ? "Cambiar la clave de API" : "Escribir la clave de API",
                        !key,
                        () -> askKey(a)));
        String profile = NextDnsSync.profile(a);
        Design.note(
                card,
                profile == null
                        ? "ID de perfil: sin escribir."
                        : "ID de perfil: "
                                + profile
                                + (NextDnsSync.manualProfile(a).isEmpty()
                                        ? " (del DNS privado)"
                                        : ""));
        card.addView(Design.button(a, "Escribir el ID de perfil", false, () -> askProfile(a)));
        if (key) {
            Design.note(card, NextDnsControl.status(a));
            card.addView(
                    Design.button(
                            a,
                            "Probar conexión",
                            false,
                            () ->
                                    new Thread(
                                                    () -> {
                                                        String msg = NextDnsSync.test(a);
                                                        a.runOnUiThread(() -> a.message(msg));
                                                    },
                                                    "foco-nextdns-probar")
                                            .start()));
        }
    }

    private static void askKey(MainActivity a) {
        EditText in = new EditText(a);
        in.setHint("Clave de API");
        in.setSingleLine(true);
        in.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        new android.app.AlertDialog.Builder(a)
                .setTitle("Clave de API de NextDNS")
                .setMessage(
                        "En my.nextdns.io › Cuenta › API. Se guarda cifrada en el teléfono y no se vuelve a mostrar. Solo se envía a api.nextdns.io.")
                .setView(in)
                .setPositiveButton(
                        "Guardar",
                        (d, w) -> {
                            String k = in.getText().toString().trim();
                            if (!NextDnsStats.validKey(k)) {
                                a.message(
                                        "Esa clave no parece válida: solo letras y números, sin espacios.");
                                return;
                            }
                            a.message(
                                    NextDnsSync.setKey(a, k)
                                            ? "Clave guardada."
                                            : "No se pudo guardar la clave de forma segura.");
                            a.render();
                        })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void askProfile(MainActivity a) {
        EditText in = Design.field(a, "ID, p. ej. 96916b", NextDnsSync.manualProfile(a), false);
        new android.app.AlertDialog.Builder(a)
                .setTitle("ID de perfil de NextDNS")
                .setMessage(
                        "En my.nextdns.io › Configuración, arriba: «ID». Vacío = el del DNS privado que Foco vigila.")
                .setView(in)
                .setPositiveButton(
                        "Guardar",
                        (d, w) -> {
                            String id =
                                    in.getText()
                                            .toString()
                                            .trim()
                                            .toLowerCase(java.util.Locale.ROOT);
                            if (!id.isEmpty() && !NextDnsStats.validProfile(id)) {
                                a.message("Ese ID no parece válido. Ejemplo: 96916b");
                                return;
                            }
                            NextDnsSync.setManualProfile(a, id);
                            a.render();
                        })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
