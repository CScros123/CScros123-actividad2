package es.foco.next.runtime;

import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import es.foco.next.R;
import es.foco.next.core.*;
import es.foco.next.data.Repository;
import es.foco.next.data.RuleStore;
import es.foco.next.log.UsageContrast;
import es.foco.next.ui.MainActivity;
import java.util.*;

/**
 * Modo estricto reforzado: respaldo del bloqueo si la Accesibilidad de Foco se desactiva o se cae.
 * Solo funciona mientras el candado del modo estricto está activo. Con Accesibilidad conectada,
 * espera (una mirada cada 30 s). Sin ella, consulta Acceso de uso cada segundo para saber qué app
 * está delante y, si un bloqueo la cubre, pone encima una pantalla completa (permiso "Aparecer
 * encima") con "Ir a Inicio". No lee textos de ninguna pantalla. Auditoría: con la pantalla apagada
 * o bloqueada no consulta nada (espera a SCREEN_ON / USER_PRESENT); respeta las mismas esenciales
 * que la Accesibilidad (teléfono, llamadas en curso, bancos, música de la pausa); y anota el reloj
 * del candado al cambiar la hora o al apagar el teléfono, para que adelantar la hora y reiniciar no
 * acorte el candado.
 */
public final class GuardService extends Service {
    private static final String CHANNEL = "foco_guard";
    private static final int ID = 4711;
    private static final long IDLE = 30_000, POLL = 1_000;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Repository repository;
    private RuleStore rules;
    private View overlay;
    private String overlayPkg = "";
    private String current = "";
    private long lastQuery, ruleSince = -1;
    private String rulePkg = "";
    private boolean wasConnected = true, started;
    private final Set<String> homes = new HashSet<>();
    private final DeviceState.Launchable launchable = new DeviceState.Launchable();
    private CallMonitor calls;
    private BankAppGuard bank;
    private boolean receiverOn;
    private final Runnable tick = this::tick;
    private final BroadcastReceiver events =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    String a = i == null ? null : i.getAction();
                    if (a == null) return;
                    try {
                        if (Intent.ACTION_SHUTDOWN.equals(a)
                                || Intent.ACTION_TIME_CHANGED.equals(a)
                                || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
                            StrictAdmin.persistNow(c);
                            return;
                        }
                        if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                            hide();
                            rulePkg = "";
                            ruleSince = -1;
                            return;
                        }
                        main.removeCallbacks(tick);
                        main.post(tick); // SCREEN_ON / USER_PRESENT: comprobar ya
                    } catch (RuntimeException e) {
                        android.util.Log.w("Foco", "Respaldo: evento no procesado", e);
                    }
                }
            };

    /** Arranca o para el respaldo según el candado. Nunca lanza. */
    public static void ensure(Context c) {
        try {
            if (!StrictAdmin.lockActive(c)) {
                c.stopService(new Intent(c, GuardService.class));
                return;
            }
            c.startForegroundService(new Intent(c, GuardService.class));
        } catch (RuntimeException e) {
            android.util.Log.w("Foco", "No se pudo iniciar el respaldo", e);
        }
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            NotificationChannel ch =
                    new NotificationChannel(
                            CHANNEL,
                            "Respaldo del modo estricto",
                            NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            startForeground(
                    ID,
                    notification(FocusAccessibilityService.connected()),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } catch (RuntimeException e) {
            android.util.Log.w("Foco", "Respaldo sin primer plano", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!StrictAdmin.lockActive(this)) {
            stop();
            return START_NOT_STICKY;
        }
        if (!started) {
            started = true;
            repository = Repository.get(this);
            rules = RuleStore.get(this);
            homes.addAll(DeviceState.homes(this));
            calls = new CallMonitor(this);
            bank = new BankAppGuard(this);
            try {
                IntentFilter f = new IntentFilter();
                f.addAction(Intent.ACTION_SCREEN_ON);
                f.addAction(Intent.ACTION_SCREEN_OFF);
                f.addAction(Intent.ACTION_USER_PRESENT);
                f.addAction(Intent.ACTION_TIME_CHANGED);
                f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
                f.addAction(Intent.ACTION_SHUTDOWN);
                registerReceiver(events, f, Context.RECEIVER_NOT_EXPORTED);
                receiverOn = true;
            } catch (RuntimeException e) {
                android.util.Log.w("Foco", "Respaldo sin eventos de pantalla", e);
            }
        }
        main.removeCallbacks(tick);
        main.post(tick);
        return START_STICKY;
    }

    private void stop() {
        main.removeCallbacks(tick);
        hide();
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (RuntimeException ignored) {
        }
        stopSelf();
    }

    private void tick() {
        try {
            if (!StrictAdmin.lockActive(this)) {
                stop();
                return;
            }
            boolean conn = FocusAccessibilityService.connected();
            if (conn != wasConnected) {
                wasConnected = conn;
                notifyState(conn);
            }
            if (conn) {
                hide();
                current = "";
                lastQuery = 0;
                ruleSince = -1;
                main.postDelayed(tick, IDLE);
                return;
            }
            // Auditoría: antes se consultaba Acceso de uso cada segundo también con la pantalla
            // apagada.
            if (!DeviceState.awake(this)) {
                hide();
                rulePkg = "";
                ruleSince = -1;
                main.postDelayed(tick, receiverOn ? IDLE : POLL);
                return;
            }
            String pkg = foreground();
            if (blocked(pkg)) show(pkg);
            else if (!pkg.equals(getPackageName())) hide();
        } catch (RuntimeException e) {
            android.util.Log.w("Foco", "Respaldo: fallo al comprobar", e);
        }
        main.postDelayed(tick, POLL);
    }

    /** Última app con ACTIVITY_RESUMED según Acceso de uso. "" si no hay permiso. */
    private String foreground() {
        if (!UsageContrast.allowed(this)) return "";
        UsageStatsManager u = getSystemService(UsageStatsManager.class);
        if (u == null) return current;
        long now = System.currentTimeMillis(),
                from = lastQuery > 0 && lastQuery <= now ? lastQuery - 2_000 : now - 60_000;
        UsageEvents ev = u.queryEvents(from, now);
        UsageEvents.Event e = new UsageEvents.Event();
        long best = -1;
        while (ev != null && ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED
                    && e.getTimeStamp() >= best
                    && e.getPackageName() != null) {
                best = e.getTimeStamp();
                current = e.getPackageName();
            }
        }
        lastQuery = now;
        return current;
    }

    /**
     * Mismas esenciales que FocusAccessibilityService.essential(): nunca se tapa el teléfono, un
     * banco ni la música elegida.
     */
    private boolean exempt(AppConfig c, String pkg) {
        return pkg.isEmpty()
                || pkg.equals(getPackageName())
                || StrictGuard.guardedPackage(pkg)
                || FocusEngine.essential(c, pkg)
                || homes.contains(pkg)
                || calls.packages.contains(pkg)
                || bank.hide(c, pkg)
                || PauseMusic.allowed(SoundSettings.pauseMusic(this), pkg)
                || !launchable.has(this, pkg);
    }

    /**
     * Misma decisión que la Accesibilidad, en versión reducida: bloqueo clásico ya agotado, sesión
     * de foco y bloqueos con nombre (RuleStore.decide; el tiempo delante cuenta igual). Ajustes no
     * se puede leer sin Accesibilidad: se deja pasar.
     */
    private boolean blocked(String pkg) {
        if (!DeviceState.awake(this)) {
            rulePkg = "";
            ruleSince = -1;
            return false;
        }
        AppConfig c = repository.config();
        if (exempt(c, pkg) || calls.active(pkg)) {
            rulePkg = "";
            ruleSince = -1;
            return false;
        }
        long wall = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        if (repository.locked() && (c.watchedApps.contains(pkg) || c.observedApps.contains(pkg)))
            return true;
        // v32 (R5): las reglas de ocio para las que el respaldo sí tiene datos (noche, puerta de
        // Foco Core en Enfoque y Weekend Lock, rutina y pausa por tareas), con la misma tabla de
        // excepciones que la Accesibilidad (LeisurePolicy). Webs, secciones y DNS no se pueden ver aquí.
        try {
            if (es.foco.next.core.FocusEngine.watched(c, pkg)
                    && (c.enabled || es.foco.next.core.AbsoluteRules.social(pkg))) {
                CareStore.install(this); // v32 (R13)
                boolean night =
                        es.foco.next.core.AbsoluteRules.night(java.time.LocalDateTime.now(), AppKinds.autoTime(this));
                es.foco.next.core.care.CareMode.Modo modo = CareStore.mode(this);
                boolean grant = RewardStore.grantActive(this, pkg);
                es.foco.next.core.LeisurePolicy.Pass pass =
                        es.foco.next.core.LeisurePolicy.pass(
                                night,
                                modo == es.foco.next.core.care.CareMode.Modo.CUIDADO,
                                grant,
                                grant && RewardStore.grantWeek(this),
                                RoutineStore.freeActive(this));
                if (es.foco.next.core.LeisurePolicy.backup(
                                true,
                                night,
                                es.foco.next.core.LeisurePolicy.gated(modo, Weekend.active(this)),
                                RoutineStore.gate(this) != null,
                                pass)
                        != null) return true;
            }
        } catch (RuntimeException ignored) {
        }
        if (rules.focusing(wall) && rules.sessionApps(c).contains(pkg)) return true;
        java.util.function.Predicate<String> ex = p -> exempt(c, p);
        long delta = pkg.equals(rulePkg) && ruleSince >= 0 ? elapsed - ruleSince : 0;
        RuleEngine.Decision d = rules.decide(pkg, wall, Math.max(0, delta), elapsed, ex);
        rulePkg = pkg;
        ruleSince = elapsed;
        return d != null && d.blocked;
    }

    private void show(String pkg) {
        if (!Settings.canDrawOverlays(this)) return;
        if (overlay != null) {
            overlayPkg = pkg;
            return;
        }
        WindowManager wm = getSystemService(WindowManager.class);
        if (wm == null) return;
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER);
        v.setPadding(64, 64, 64, 64);
        v.setBackgroundColor(getColor(R.color.surface));
        TextView t = new TextView(this);
        t.setText("Esta app está bloqueada ahora");
        t.setTextSize(24);
        t.setTextColor(getColor(R.color.text_primary));
        t.setGravity(Gravity.CENTER);
        v.addView(t);
        TextView n = new TextView(this);
        n.setText(
                "Modo estricto: la Accesibilidad de Foco está desactivada, así que este es el respaldo. Vuelve a activarla para la experiencia completa.");
        n.setTextSize(16);
        n.setTextColor(getColor(R.color.text_primary));
        n.setGravity(Gravity.CENTER);
        n.setPadding(0, 32, 0, 48);
        v.addView(n);
        Button home = new Button(this);
        home.setText("Ir a Inicio");
        home.setOnClickListener(x -> goHome());
        v.addView(home);
        WindowManager.LayoutParams lp =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.OPAQUE);
        try {
            wm.addView(v, lp);
            overlay = v;
            overlayPkg = pkg;
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private void hide() {
        if (overlay == null) return;
        try {
            getSystemService(WindowManager.class).removeView(overlay);
        } catch (RuntimeException ignored) {
        }
        overlay = null;
        overlayPkg = "";
    }

    private void goHome() {
        try {
            startActivity(
                    new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_HOME)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (RuntimeException ignored) {
        }
        main.postDelayed(
                () -> {
                    current = "";
                    hide();
                },
                400);
    }

    private void notifyState(boolean conn) {
        try {
            getSystemService(NotificationManager.class).notify(ID, notification(conn));
        } catch (RuntimeException ignored) {
        }
    }

    private Notification notification(boolean conn) {
        PendingIntent open =
                PendingIntent.getActivity(
                        this,
                        ID,
                        new Intent(this, MainActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b =
                new Notification.Builder(this, CHANNEL)
                        .setSmallIcon(R.drawable.leaf)
                        .setContentIntent(open)
                        .setOngoing(true)
                        .setOnlyAlertOnce(true);
        if (conn)
            return b.setContentTitle("Modo estricto activo")
                    .setContentText("Respaldo en espera: la Accesibilidad de Foco está conectada.")
                    .build();
        PendingIntent acc =
                PendingIntent.getActivity(
                        this,
                        ID + 1,
                        new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return b.setContentTitle("Foco necesita Accesibilidad para protegerte")
                .setContentText("El respaldo bloquea con Acceso de uso mientras tanto.")
                .addAction(
                        new Notification.Action.Builder(null, "Abrir Accesibilidad", acc).build())
                .build();
    }

    @Override
    public void onDestroy() {
        main.removeCallbacksAndMessages(null);
        hide();
        if (receiverOn)
            try {
                unregisterReceiver(events);
            } catch (RuntimeException ignored) {
            }
        receiverOn = false;
        super.onDestroy();
    }
}
