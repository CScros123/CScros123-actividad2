package es.foco.next.runtime;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import es.foco.next.core.home.DnsFallback;
import es.foco.next.log.InspectorLog;

/**
 * Etapa 5.4: DNS automático de respaldo con WRITE_SECURE_SETTINGS (se concede por adb; Foco nunca
 * lo pide). Comprueba cada 15 min y al cambiar de red si el DNS privado funciona. Si falla 2 veces
 * seguidas, pone el DNS privado en automático 10 min, restaura el proveedor y vuelve a probar. No
 * tiene botón para quitarlo. Todo se registra (dns_automatico).
 *
 * <p>La prueba NO abre conexiones (Foco no tiene permiso INTERNET): usa lo que Android ya validó,
 * NET_CAPABILITY_VALIDATED y LinkProperties.isPrivateDnsActive().
 *
 * <p>Interacción con la Fase 3: durante los 10 min en automático, DnsGuard ve el DNS fuera del
 * proveedor y, pasados 60 s, pausa el ocio y los navegadores (esenciales intactos). Es lo esperado:
 * Foco solo endurece; el respaldo sirve para que WhatsApp, banco y llamadas sigan funcionando.
 */
public final class DnsAuto {
    private static final String MODE = "private_dns_mode", SPEC = "private_dns_specifier";
    private final Context c;
    private final Handler main = new Handler(Looper.getMainLooper());
    private DnsFallback state = DnsFallback.start();
    private ConnectivityManager.NetworkCallback callback;
    private boolean running;

    private final Runnable check = this::check;

    DnsAuto(Context c) {
        this.c = c.getApplicationContext();
    }

    /** DNS privado activo con el proveedor elegido en Foco (misma comprobación que la Fase 3). */
    static boolean dnsCompliant(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network n = cm.getActiveNetwork();
            android.net.LinkProperties p = n == null ? null : cm.getLinkProperties(n);
            return p != null
                    && es.foco.next.core.DnsGuard.compliant(
                            new es.foco.next.core.DnsGuard.State(
                                    true,
                                    true,
                                    p.isPrivateDnsActive(),
                                    p.getPrivateDnsServerName(),
                                    DnsMonitor.provider(c)));
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean granted(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    void start() {
        if (running) return;
        running = true;
        if (!granted(c)) {
            log("sin_permiso_write_secure_settings");
            return; // sin permiso no hace nada (la Fase 3 sigue igual)
        }
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            callback =
                    new ConnectivityManager.NetworkCallback() {
                        @Override
                        public void onAvailable(Network n) {
                            main.removeCallbacks(check);
                            main.postDelayed(check, 20_000); // deja que Android valide la red
                        }
                    };
            cm.registerDefaultNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
        restoreIfStuck();
        main.postDelayed(check, 30_000);
    }

    void stop() {
        running = false;
        main.removeCallbacks(check);
        try {
            if (callback != null)
                c.getSystemService(ConnectivityManager.class).unregisterNetworkCallback(callback);
        } catch (RuntimeException ignored) {
        }
        callback = null;
    }

    private SharedPreferences prefs() {
        return c.getSharedPreferences("foco-dns-auto", Context.MODE_PRIVATE);
    }

    /** Si el servicio murió con el respaldo puesto, al volver se restaura el proveedor. */
    private void restoreIfStuck() {
        SharedPreferences p = prefs();
        String saved = p.getString("specifier", "");
        if (p.getBoolean("fallback", false) && !saved.isEmpty()) {
            write("hostname", saved);
            p.edit().putBoolean("fallback", false).apply();
            log("restaurado_tras_reinicio:" + saved);
        }
    }

    private void check() {
        if (!running) return;
        long now = System.currentTimeMillis();
        // v26: con "automático" elegido por ti, Foco deja de probar y de restaurar NextDNS.
        if (es.foco.next.core.DnsGuard.automatic(DnsMonitor.provider(c))) {
            main.removeCallbacks(check);
            main.postDelayed(check, 15 * 60_000L);
            return;
        }
        try {
            ContentResolver r = c.getContentResolver();
            String mode = Settings.Global.getString(r, MODE);
            if (state.mode == DnsFallback.Mode.AUTOMATIC) {
                DnsFallback next = state.tick(now);
                if (next.mode == DnsFallback.Mode.PROVIDER) {
                    String spec = prefs().getString("specifier", "");
                    if (!spec.isEmpty()) write("hostname", spec);
                    prefs().edit().putBoolean("fallback", false).apply();
                    log("restaurado:" + spec);
                }
                state = next;
            } else if ("hostname".equals(mode)) {
                Boolean ok = resolves();
                if (ok != null) {
                    DnsFallback next = state.probe(ok, now);
                    if (!ok) log("fallo_resolucion:" + next.fails);
                    if (next.mode == DnsFallback.Mode.AUTOMATIC) {
                        String spec = Settings.Global.getString(r, SPEC);
                        prefs().edit()
                                .putString("specifier", spec == null ? "" : spec)
                                .putBoolean("fallback", true)
                                .apply();
                        write("opportunistic", null);
                        log("automatico_10_min:" + spec);
                        askAboutAutomatic(now);
                    }
                    state = next;
                }
            }
        } catch (RuntimeException e) {
            log("error:" + e.getClass().getSimpleName());
        }
        main.removeCallbacks(check);
        main.postDelayed(check, state.nextCheckIn(now));
    }

    /**
     * true si Android validó la red con el DNS privado activo; null si no hay red o es un portal
     * cautivo (no cuenta). Con el DNS privado estricto roto, Android no valida la red.
     * (isPrivateDnsBroken() es API oculta del sistema: no se puede usar.)
     */
    private Boolean resolves() {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        Network n = cm.getActiveNetwork();
        NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
        if (caps == null
                || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
            return null;
        android.net.LinkProperties lp = cm.getLinkProperties(n);
        boolean dnsActive = lp != null && lp.isPrivateDnsActive();
        return dnsActive && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void write(String mode, String spec) {
        try {
            ContentResolver r = c.getContentResolver();
            if (spec != null) Settings.Global.putString(r, SPEC, spec);
            Settings.Global.putString(r, MODE, mode);
        } catch (SecurityException e) {
            log("sin_permiso_al_escribir");
        }
    }

    /**
     * v26: tras un fallo, el búho pregunta (como mucho cada 6 h, nunca de noche ni con No
     * molestar): seguir esperando a NextDNS, o pasar a automático dentro de 24 h. Tú decides.
     */
    private void askAboutAutomatic(long now) {
        SharedPreferences p = prefs();
        if (now - p.getLong("askedAt", 0) < 6 * 60 * 60_000L) return;
        if (!DnsMonitor.pendingProvider(c).isEmpty()) return;
        p.edit().putLong("askedAt", now).apply();
        try {
            if (c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
            if (es.foco.next.core.AbsoluteRules.esNocheAbsoluta(java.time.LocalDateTime.now()))
                return;
            android.app.NotificationManager nm =
                    c.getSystemService(android.app.NotificationManager.class);
            if (nm == null
                    || nm.getCurrentInterruptionFilter()
                            > android.app.NotificationManager.INTERRUPTION_FILTER_ALL) return;
            android.app.NotificationChannel ch =
                    new android.app.NotificationChannel(
                            "foco-dns", "DNS", android.app.NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null, null);
            ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            android.content.Intent open =
                    new android.content.Intent()
                            .setClassName(c, "es.foco.next.ui.MainActivity")
                            .putExtra(EXTRA_DNS_CHOICE, true)
                            .addFlags(
                                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                            | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
            android.app.PendingIntent tap =
                    android.app.PendingIntent.getActivity(
                            c,
                            7301,
                            open,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                    | android.app.PendingIntent.FLAG_IMMUTABLE);
            nm.notify(
                    7301,
                    new android.app.Notification.Builder(c, "foco-dns")
                            .setSmallIcon(es.foco.next.R.drawable.ic_owl)
                            .setContentTitle("🦉 NextDNS no responde")
                            .setContentText(
                                    "Lo pausé 10 min y vuelvo a intentarlo. Toca para decidir: seguir esperando o pasar a automático en 24 h.")
                            .setStyle(
                                    new android.app.Notification.BigTextStyle()
                                            .bigText(
                                                    "Lo pausé 10 min y vuelvo a intentarlo. Toca para decidir: seguir esperando o pasar a automático (sin filtro) dentro de 24 h."))
                            .setContentIntent(tap)
                            .setAutoCancel(true)
                            .build());
        } catch (RuntimeException ignored) {
        }
    }

    /** Extra de MainActivity: abrir la pregunta "seguir esperando o automático". */
    public static final String EXTRA_DNS_CHOICE = "es.foco.next.extra.DNS_CHOICE";

    private void log(String detail) {
        InspectorLog.event(
                c, InspectorLog.DNS_AUTO, "", System.currentTimeMillis(), 0, 1, 0, detail, null);
    }
}
