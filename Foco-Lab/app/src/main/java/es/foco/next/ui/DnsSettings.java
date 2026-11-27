package es.foco.next.ui;

import android.widget.LinearLayout;
import es.foco.next.core.DnsGuard;
import es.foco.next.runtime.DnsMonitor;
import es.foco.next.runtime.FocusAccessibilityService;

/**
 * Fase 3 y v26: tarjeta de Ajustes del filtro DNS. Solo NextDNS (tu perfil exacto). Asistente de
 * tres pasos: DNS privado de Android, "DNS seguro" de Chrome en el del sistema y vigilancia de
 * Foco. "Automático" (sin filtro) solo se puede pedir y llega a las 24 h; volver a NextDNS es al
 * momento.
 */
final class DnsSettings {
    private DnsSettings() {}

    static LinearLayout render(MainActivity a, LinearLayout body) {
        DnsGuard.State now = DnsMonitor.snapshot(a);
        String host = DnsMonitor.provider(a);
        boolean armed = DnsMonitor.armed(a);
        boolean dnsOk =
                DnsGuard.compliant(
                        new DnsGuard.State(
                                true, now.network, now.privateDnsActive, now.server, host));
        LinearLayout card = Design.card(body, "Filtro DNS: NextDNS");
        Design.note(card, status(now, host));
        Design.note(card, "Proveedor: " + DnsGuard.providerLabel(host) + ".");
        String pending = DnsMonitor.pendingProvider(a);
        if (DnsGuard.automatic(host)) {
            // Automático elegido por ti: Foco no exige DNS. Volver a NextDNS endurece: al momento.
            Design.note(
                    card,
                    "Elegiste automático tras un fallo de NextDNS: ahora no hay filtro DNS. Cuando NextDNS vuelva, regresa aquí.");
            card.addView(Design.button(a, "Volver a NextDNS", true, () -> backToNextDns(a)));
            return card;
        }
        // Sin perfil, o el teléfono usa otro perfil de NextDNS: se puede adoptar.
        if (now.privateDnsActive
                && DnsGuard.nextDns(now.server)
                && !DnsGuard.sameHost(now.server, host)) {
            String nd = DnsGuard.normalize(now.server);
            card.addView(
                    Design.button(
                            a,
                            "Usar mi NextDNS actual (" + DnsGuard.nextDnsProfile(nd) + ")",
                            true,
                            () -> {
                                if (!host.isEmpty() && lockedChange(a, nd)) return;
                                DnsMonitor.setProvider(a, nd);
                                FocusAccessibilityService.checkpoint();
                                a.render();
                                a.message(
                                        "Foco vigila ahora tu perfil exacto de NextDNS. Activa los filtros en my.nextdns.io: Foco no puede ver qué bloquea el perfil.");
                            }));
        }
        Design.note(
                card,
                "Foco comprueba que el teléfono usa este perfil exacto. Qué se bloquea (control parental, SafeSearch, bloquear VPN y proxies) se decide en my.nextdns.io.");
        if (!pending.isEmpty()) {
            String when =
                    java.time.Instant.ofEpochMilli(DnsMonitor.pendingAt(a))
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(
                                    java.time.format.DateTimeFormatter.ofPattern(
                                            "EEEE H:mm", new java.util.Locale("es")));
            Design.note(
                    card,
                    "Cambio pedido: "
                            + DnsGuard.providerLabel(pending)
                            + " desde el "
                            + when
                            + ".");
            card.addView(
                    Design.button(
                            a,
                            "Anular el cambio pedido",
                            false,
                            () -> {
                                DnsMonitor.cancelPending(a);
                                a.render();
                            }));
        }
        // Estadísticas automáticas: con la clave de API, Foco lee lo bloqueado hoy (cada 30 min).
        if (DnsGuard.nextDns(host) || DnsGuard.nextDns(pending)) {
            es.foco.next.runtime.NextDnsSync.Snapshot snap =
                    es.foco.next.runtime.NextDnsSync.snapshot(a);
            boolean key = es.foco.next.runtime.NextDnsSync.hasKey(a);
            if (key)
                Design.note(
                        card,
                        snap.error != null
                                ? snap.error
                                : snap.at > 0
                                        ? "Estadísticas de hoy: "
                                                + snap.blocked
                                                + " bloqueadas de "
                                                + snap.total
                                                + " consultas."
                                        : "Estadísticas: leyendo…");
            card.addView(
                    Design.button(
                            a,
                            key
                                    ? "Estadísticas automáticas · cambiar clave"
                                    : "Ver estadísticas automáticas",
                            !key,
                            () -> askApiKey(a, key)));
        }
        card.addView(
                Design.button(
                        a,
                        DnsGuard.nextDns(host)
                                ? "Cambiar de perfil de NextDNS"
                                : "Escribir mi perfil de NextDNS",
                        !DnsGuard.nextDns(host),
                        () -> askNextDns(a)));
        if (DnsGuard.nextDns(host) && !DnsGuard.automatic(pending))
            card.addView(
                    Design.button(
                            a,
                            "NextDNS falla: pasar a automático en 24 h",
                            false,
                            () -> askAutomatic(a)));
        Design.gap(card, 8);
        card.addView(Design.text(a, (dnsOk ? "✓ " : "1. ") + "DNS privado de Android", 17, true));
        Design.note(
                card,
                "Ajustes › Conexiones › Más ajustes de conexión › DNS privado › Nombre de host del proveedor. Pega:");
        String shown =
                host.isEmpty() ? "TU-ID.dns.nextdns.io (my.nextdns.io › Configuración)" : host;
        card.addView(Design.text(a, shown, 18, true));
        card.addView(
                Design.button(
                        a,
                        host.isEmpty() ? "Abrir DNS privado" : "Copiar y abrir DNS privado",
                        true,
                        () -> {
                            if (!host.isEmpty()) DnsMonitor.copyHost(a, host);
                            a.message(
                                    "Nombre copiado. Si se abre otra pantalla de Ajustes, busca \"DNS privado\".");
                            DnsMonitor.openPrivateDnsSettings(a);
                        }));
        Design.note(
                card,
                "Android no tiene un acceso público directo al DNS privado: Foco intenta abrirlo y, si el teléfono no lo permite, abre Redes o Ajustes.");

        boolean chrome = DnsMonitor.chromeChecked(a);
        card.addView(
                Design.text(
                        a, (chrome ? "✓ " : "2. ") + "Chrome: DNS seguro del sistema", 17, true));
        Design.note(
                card,
                "Chrome puede usar su propio DNS seguro y saltarse el filtro. En Chrome: Configuración › Privacidad y seguridad › Usar DNS seguro › elige el proveedor actual (el del sistema) o desactívalo. Samsung Internet y otros navegadores tienen un ajuste parecido.");
        card.addView(
                Design.button(
                        a,
                        chrome ? "Chrome revisado · deshacer" : "Ya lo revisé en Chrome",
                        false,
                        () -> {
                            DnsMonitor.setChromeChecked(a, !chrome);
                            a.render();
                        }));

        card.addView(Design.text(a, (armed ? "✓ " : "3. ") + "Vigilancia de Foco", 17, true));
        Design.note(
                card,
                armed
                        ? "Activada. Si el DNS privado se apaga o cambia de proveedor, tras 60 s Foco pausa tus apps de ocio y los navegadores conocidos hasta reactivarlo. Ajustes, Teléfono, WhatsApp, bancos y Foco siguen abiertos."
                        : "Desactivada. Se activa sola la primera vez que Foco vea el DNS privado con tu perfil de NextDNS.");
        card.addView(
                Design.button(
                        a,
                        armed ? "Desactivar vigilancia" : "Activar vigilancia",
                        !armed,
                        () -> {
                            if (!armed) {
                                DnsMonitor.setArmed(a, true);
                                FocusAccessibilityService.checkpoint();
                                a.render();
                                return;
                            }
                            FocusAccessibilityService.checkpoint();
                            // Fricción: no se apaga durante un bloqueo de ocio ni con el DNS mal
                            // (eso sería saltarse la pausa).
                            if (a.repository.locked()) {
                                a.message("Podrás cambiarlo al terminar esta ventana.");
                                return;
                            }
                            // Auditoría: con el candado del modo estricto la vigilancia solo puede
                            // encenderse.
                            if (es.foco.next.runtime.StrictAdmin.lockActive(a)) {
                                a.message(
                                        "Modo estricto: con el candado puesto no se puede desactivar la vigilancia del DNS.");
                                return;
                            }
                            DnsGuard.State s = DnsMonitor.snapshot(a);
                            if (!(s.network
                                    && DnsGuard.compliant(
                                            new DnsGuard.State(
                                                    true,
                                                    true,
                                                    s.privateDnsActive,
                                                    s.server,
                                                    host)))) {
                                a.message(
                                        "Primero vuelve a activar el DNS privado con "
                                                + DnsGuard.providerLabel(host)
                                                + ".");
                                return;
                            }
                            DnsMonitor.setArmed(a, false);
                            a.render();
                        }));
        Design.note(
                card,
                "Límites: el DNS no filtra el contenido dentro de apps ni de redes sociales abiertas en el navegador; una VPN activa cuenta como DNS apagado. Android permite apagar el DNS privado: Foco lo detecta y pausa ocio y navegadores. Foco no guarda consultas.");
        return card;
    }

    /**
     * Tras un fallo de NextDNS: seguir esperando (no cambia nada) o pasar a automático, que llega a
     * las 24 h. También se abre desde el aviso del búho.
     */
    static void askAutomatic(MainActivity a) {
        if (DnsGuard.automatic(DnsMonitor.provider(a))
                || DnsGuard.automatic(DnsMonitor.pendingProvider(a))) {
            a.message("Automático ya está pedido o activo.");
            return;
        }
        new android.app.AlertDialog.Builder(a)
                .setTitle("NextDNS no responde")
                .setMessage(
                        "Cuando falla, Foco lo pausa 10 min y vuelve a intentarlo solo; mientras tanto, el ocio y los navegadores esperan. Puedes seguir así, o pasar a automático (sin filtro): Foco dejará de intentarlo dentro de 24 h, y puedes volver a NextDNS cuando quieras, al momento.")
                .setNegativeButton("Seguir esperando", null)
                .setPositiveButton(
                        "Automático en 24 h",
                        (d, w) -> {
                            DnsMonitor.requestAutomatic(a);
                            a.render();
                            a.message(
                                    "Pedido. Automático llega el "
                                            + whenText(a)
                                            + ". Puedes anularlo en Ajustes.");
                        })
                .show();
    }

    /** Salir de automático: endurece, así que es al momento. */
    private static void backToNextDns(MainActivity a) {
        String last = DnsMonitor.lastNextDns(a);
        if (!DnsGuard.nextDns(last)) {
            askNextDns(a);
            return;
        }
        DnsMonitor.cancelPending(a);
        DnsMonitor.setProvider(a, last);
        FocusAccessibilityService.checkpoint();
        a.render();
        a.message(
                "De vuelta a NextDNS ("
                        + DnsGuard.nextDnsProfile(last)
                        + "). Pon "
                        + last
                        + " en DNS privado: tienes 60 s.");
    }

    private static String whenText(MainActivity a) {
        return java.time.Instant.ofEpochMilli(DnsMonitor.pendingAt(a))
                .atZone(java.time.ZoneId.systemDefault())
                .format(
                        java.time.format.DateTimeFormatter.ofPattern(
                                "EEEE H:mm", new java.util.Locale("es")));
    }

    /** Clave de API de NextDNS (my.nextdns.io › Cuenta › API). Solo se usa con api.nextdns.io. */
    private static void askApiKey(MainActivity a, boolean hasKey) {
        android.widget.EditText in = new android.widget.EditText(a);
        in.setHint("Clave de API");
        in.setSingleLine(true);
        in.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.app.AlertDialog.Builder b =
                new android.app.AlertDialog.Builder(a)
                        .setTitle("Estadísticas de NextDNS")
                        .setMessage(
                                "En my.nextdns.io › Cuenta, abajo del todo: API. Copia la clave y pégala aquí. Foco solo lee lo de hoy de tu perfil, cada 30 min, y la clave no sale del teléfono salvo hacia NextDNS.")
                        .setView(in)
                        .setPositiveButton(
                                "Guardar",
                                (d, w) -> {
                                    String k = in.getText().toString().trim();
                                    if (!es.foco.next.core.NextDnsStats.validKey(k)) {
                                        a.message(
                                                "Esa clave no parece válida: solo letras y números, sin espacios.");
                                        return;
                                    }
                                    es.foco.next.runtime.NextDnsSync.setKey(a, k);
                                    // Una sola vez: al llegar la primera lectura se repinta la
                                    // página.
                                    Runnable[] once = new Runnable[1];
                                    once[0] =
                                            () -> {
                                                es.foco.next.runtime.NextDnsSync.removeListener(
                                                        once[0]);
                                                a.runOnUiThread(a::render);
                                            };
                                    es.foco.next.runtime.NextDnsSync.addListener(once[0]);
                                    es.foco.next.runtime.NextDnsSync.refresh(a, true);
                                    a.render();
                                })
                        .setNegativeButton("Cancelar", null);
        if (hasKey)
            b.setNeutralButton(
                    "Quitar clave",
                    (d, w) -> {
                        es.foco.next.runtime.NextDnsSync.setKey(a, null);
                        a.render();
                    });
        b.show();
    }

    /**
     * Con el candado del modo estricto, cambiar de proveedor podría aflojar el filtro: no se
     * prohíbe, se aplaza 24 h (como todo lo que afloja). Devuelve true si quedó pedido.
     */
    private static boolean lockedChange(MainActivity a, String host) {
        if (!es.foco.next.runtime.StrictAdmin.lockActive(a)) return false;
        DnsMonitor.requestProvider(a, host);
        String when =
                java.time.Instant.ofEpochMilli(DnsMonitor.pendingAt(a))
                        .atZone(java.time.ZoneId.systemDefault())
                        .format(
                                java.time.format.DateTimeFormatter.ofPattern(
                                        "EEEE H:mm", new java.util.Locale("es")));
        a.render();
        a.message(
                "Modo estricto: el cambio a "
                        + DnsGuard.providerLabel(host)
                        + " se aplica el "
                        + when
                        + ". Hasta entonces deja el DNS privado como está; después pon "
                        + host
                        + ".");
        return true;
    }

    /** Pide el ID de NextDNS (o el host DNS-over-TLS) y lo guarda como proveedor exacto. */
    private static void askNextDns(MainActivity a) {
        android.widget.EditText in = Design.field(a, "ID o host, p. ej. 96916b", "", false);
        new android.app.AlertDialog.Builder(a)
                .setTitle("Perfil de NextDNS")
                .setMessage(
                        "En my.nextdns.io › Configuración: copia el ID o el host de DNS-over-TLS (termina en .dns.nextdns.io). No sirve la dirección https ni las IP.")
                .setView(in)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Usar",
                        (d, w) -> {
                            String h = nextDnsHost(in.getText().toString());
                            if (h == null) {
                                a.message(
                                        "No es un perfil de NextDNS válido. Ejemplo: 96916b o 96916b.dns.nextdns.io");
                                return;
                            }
                            String cur = DnsMonitor.provider(a);
                            if (DnsGuard.nextDns(cur) && lockedChange(a, h)) return;
                            DnsMonitor.setProvider(a, h);
                            FocusAccessibilityService.checkpoint();
                            a.render();
                            a.message(
                                    "Listo. Ahora pulsa «Copiar y abrir DNS privado» y pega "
                                            + h
                                            + ". Tienes 60 s.");
                        })
                .show();
    }

    /** "96916b", "96916b.dns.nextdns.io", "https://dns.nextdns.io/96916b" → host exacto, o null. */
    static String nextDnsHost(String raw) {
        String t = DnsGuard.normalize(raw);
        if (t.startsWith("https://dns.nextdns.io/"))
            t = t.substring("https://dns.nextdns.io/".length());
        if (t.startsWith("tls://")) t = t.substring(6);
        int slash = t.indexOf('/');
        if (slash >= 0) t = t.substring(0, slash);
        String h = t.endsWith(DnsGuard.NEXTDNS_SUFFIX) ? t : t + DnsGuard.NEXTDNS_SUFFIX;
        return DnsGuard.nextDns(h) ? DnsGuard.normalize(h) : null;
    }

    private static String status(DnsGuard.State s, String host) {
        if (!s.network)
            return "Sin red activa ahora: no se puede comprobar el DNS privado (Foco no bloquea sin red).";
        if (!s.privateDnsActive) return "DNS privado apagado o en automático en la red actual.";
        if (s.server == null || s.server.isEmpty())
            return "DNS privado en modo automático: no filtra.";
        return DnsGuard.compliant(new DnsGuard.State(true, true, true, s.server, host))
                ? "DNS privado activo · " + s.server
                : "DNS privado con otro proveedor · " + s.server;
    }
}
