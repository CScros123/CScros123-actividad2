package es.foco.next.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import es.foco.next.R;
import es.foco.next.core.DnsGuard;
import es.foco.next.runtime.DnsMonitor;

/**
 * Fase 3: vista de la pausa cuando el DNS privado no está activo con el proveedor elegido. Sin
 * vídeo ni música.
 */
final class DnsPause {
    private DnsPause() {}

    static View build(Context service, String host, Runnable home) {
        android.content.res.Configuration dark =
                new android.content.res.Configuration(service.getResources().getConfiguration());
        dark.uiMode =
                (dark.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        | android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Context scene =
                new android.view.ContextThemeWrapper(
                        service.createConfigurationContext(dark), R.style.AppTheme);
        ScrollView scroll = new ScrollView(scene);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xF20B0F0D);
        LinearLayout box = Design.column(scene);
        box.setGravity(Gravity.CENTER);
        box.setPadding(
                Design.dp(scene, 28),
                Design.dp(scene, 56),
                Design.dp(scene, 28),
                Design.dp(scene, 32));
        scroll.addView(box);
        // Cambio de proveedor pedido con el candado: se dice cuándo se aplica, en vez de "apagado".
        String pending = es.foco.next.runtime.DnsMonitor.pendingProvider(service);
        if (!pending.isEmpty()) {
            String when =
                    java.time.Instant.ofEpochMilli(
                                    es.foco.next.runtime.DnsMonitor.pendingAt(service))
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(
                                    java.time.format.DateTimeFormatter.ofPattern(
                                            "EEEE H:mm", new java.util.Locale("es")));
            label(box, "Cambio de DNS en camino", 25, 0xFFFFE7A3);
            label(
                    box,
                    "Pediste "
                            + DnsGuard.providerLabel(pending)
                            + "; Foco lo adopta el "
                            + when
                            + ". Hasta entonces vigila "
                            + DnsGuard.providerLabel(host)
                            + " y pausa ocio y navegadores si el teléfono usa otro. Para seguir ya, vuelve a poner este:",
                    17,
                    0xFFE4D0A1);
        } else {
            label(box, "Tu filtro DNS está apagado", 25, 0xFFFFE7A3);
            label(
                    box,
                    "Mientras el DNS privado no esté activo con "
                            + DnsGuard.providerLabel(host)
                            + ", Foco pausa tus apps de ocio y los navegadores.",
                    17,
                    0xFFE4D0A1);
        }
        Design.gap(box, 12);
        label(
                box,
                "Ajustes › Conexiones › Más ajustes de conexión › DNS privado › Nombre de host del proveedor:",
                14,
                scene.getColor(R.color.text_secondary));
        label(
                box,
                host.isEmpty() ? "tu perfil de NextDNS (TU-ID.dns.nextdns.io)" : host,
                18,
                0xFFFFFFFF);
        box.addView(
                action(
                        scene,
                        "Abrir DNS privado",
                        true,
                        () -> {
                            DnsMonitor.copyHost(service, host);
                            Toast.makeText(scene, "Nombre copiado: " + host, Toast.LENGTH_SHORT)
                                    .show();
                            DnsMonitor.openPrivateDnsSettings(service);
                        }));
        label(
                box,
                "Android no ofrece un acceso directo garantizado al DNS privado: si se abre otra pantalla de Ajustes, busca \"DNS privado\". El nombre ya está copiado.",
                13,
                scene.getColor(R.color.text_secondary));
        box.addView(action(scene, "Descansar · ir a Inicio", false, home));
        label(
                box,
                "Ajustes, Teléfono, WhatsApp, bancos y Foco siguen disponibles. Al reactivar el DNS, esta pausa se quita sola.",
                13,
                scene.getColor(R.color.text_secondary));
        return scroll;
    }

    private static TextView label(LinearLayout box, String s, int size, int color) {
        TextView t = Design.text(box.getContext(), s, size, true);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        return t;
    }

    private static Button action(Context context, String text, boolean primary, Runnable run) {
        Button b = Design.button(context, text, primary, run);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? 0xED493517 : 0xDE121416);
        bg.setCornerRadius(Design.dp(context, 18));
        bg.setStroke(Design.dp(context, 1), primary ? 0xFFFFD783 : 0x997E683E);
        b.setBackground(bg);
        b.setTextColor(primary ? 0xFFFFE6AA : 0xFFE4D0A1);
        return b;
    }
}
