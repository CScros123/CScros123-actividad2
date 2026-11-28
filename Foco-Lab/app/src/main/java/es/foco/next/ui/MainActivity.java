package es.foco.next.ui;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import es.foco.next.R;
import es.foco.next.core.*;
import es.foco.next.data.Repository;
import es.foco.next.runtime.DailyReminder;
import es.foco.next.runtime.FocusAccessibilityService;
import es.foco.next.runtime.StrictAdmin;
import java.io.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/** Three destinations: your time, your intention, essential settings. No perpetual UI timer. */
public final class MainActivity extends Activity {
    Repository repository;
    LinearLayout body, navigation;
    private int page;
    private boolean resumed;
    private final android.graphics.Rect visibleCards = new android.graphics.Rect();
    private final Runnable revealCards =
            () -> {
                if (resumed && body != null) {
                    ScrollView scroll = findViewById(R.id.page_scroll);
                    visibleCards.set(
                            0,
                            scroll.getScrollY(),
                            body.getWidth(),
                            scroll.getScrollY() + scroll.getHeight());
                    UiMotion.reveal(body, visibleCards);
                }
            };
    private TextView budgetText, windowText;
    private ProgressBar progress;
    private QuietMusic music;
    private Button musicButton;
    private TextView musicCaption;
    private boolean appliedNight, sceneReceiverRegistered;
    private final Runnable sceneBoundary =
            () -> {
                if (resumed) recreate();
            };
    private final BroadcastReceiver sceneChanges =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context c, Intent i) {
                    if (resumed) {
                        if (appliedNight != SceneTheme.night()) recreate();
                        else {
                            render();
                            scheduleScene();
                        }
                    }
                }
            };

    /** v27: pestaña con la que abrir Foco (0 Estado, 1 Mi plan, 2 Ajustes, 3 Bloqueos). */
    public static final String EXTRA_PAGE = "es.foco.next.extra.PAGE";

    int pendingReminderMinute = -1;
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Foco Home, etapa 1.1: trabajo pesado de onResume, tras el primer dibujado. */
    private final Runnable deferred =
            () -> {
                if (!resumed || repository == null) return;
                es.foco.next.log.InspectorLog.drainLab(this); // retos y logros de Lab al registro
                es.foco.next.runtime.CategoryInspector.daily(this);
                es.foco.next.runtime.Inspector.maybeWrite(this, false);
                // v47: copia nocturna en Drive y comprobación de NextDNS (idempotente).
                es.foco.next.runtime.BackupStore.ensureScheduled(this);
            };

    private final Runnable update =
            new Runnable() {
                public void run() {
                    if (resumed && page == 0) {
                        updateTime();
                        main.postDelayed(this, 30_000); // minutos redondeados: sin tic por segundo
                    }
                }
            };

    @Override
    protected void attachBaseContext(Context base) {
        appliedNight = SceneTheme.night();
        super.attachBaseContext(base.createConfigurationContext(SceneTheme.override(base)));
    }

    @Override
    public void onCreate(Bundle b) {
        setTheme(R.style.AppTheme);
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        body = findViewById(R.id.page_body);
        navigation = findViewById(R.id.navigation);
        // v28: sin escena animada, hojas ni botón de ahorro (Foco completo es solo ajustes).
        body.addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) -> revealCards.run());
        ((ScrollView) findViewById(R.id.page_scroll))
                .setOnScrollChangeListener((v, x, y, ox, oy) -> revealCards.run());
        if (b != null) {
            page = Math.max(0, Math.min(3, b.getInt("page")));
            pendingReminderMinute = b.getInt("pendingReminder", -1);
        }
        try {
            // Old periodic DNS jobs/notifications belong to this package and have no role in Lite.
            if (getPreferences(MODE_PRIVATE).getInt("cleanup", 0) < 52) {
                getSystemService(android.app.job.JobScheduler.class).cancelAll();
                getSystemService(NotificationManager.class).cancelAll();
                getPreferences(MODE_PRIVATE).edit().putInt("cleanup", 52).apply();
            }
            repository = Repository.get(this);
            music = new QuietMusic(this, this::refreshMusicControl);
        } catch (RuntimeException e) {
            // Auditoría de producto: antes esta pantalla no tenía salida y Foco quedaba
            // inutilizable para siempre.
            body.addView(Design.text(this, "Tus datos se conservan", 26, true));
            Design.note(
                    body,
                    "No se pudieron abrir los datos del plan. No se ha borrado nada. Cierra Foco y vuelve a abrirlo; si sigue igual, puedes empezar con un plan nuevo: el archivo actual se guarda aparte en el teléfono y tus bloqueos, apuntes y registro no se tocan.");
            body.addView(
                    Design.button(
                            this,
                            "Empezar con un plan nuevo",
                            false,
                            () ->
                                    new AlertDialog.Builder(this)
                                            .setMessage(
                                                    "¿Apartar el archivo dañado y empezar con un plan nuevo? Después podrás restaurar una copia desde Ajustes.")
                                            .setPositiveButton(
                                                    "Empezar de nuevo",
                                                    (d, w) -> {
                                                        if (Repository.quarantine(this)) recreate();
                                                        else
                                                            message(
                                                                    "No se pudo apartar el archivo.");
                                                    })
                                            .setNegativeButton("Cancelar", null)
                                            .show()));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appliedNight != SceneTheme.night()) {
            recreate();
            return;
        }
        resumed = true;
        repository.applyPending();
        if (!sceneReceiverRegistered) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            if (Build.VERSION.SDK_INT >= 33)
                registerReceiver(sceneChanges, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(sceneChanges, filter);
            sceneReceiverRegistered = true;
        }
        FocusAccessibilityService.appOpened();
        Alternatives.invalidate();
        // Foco Home, etapa 1.1: arranque ligero. Lab (banco), el Inspector y la revisión diaria de
        // categorías van después del primer dibujado, en el hilo principal (drainLab avisa al
        // servicio, que toca la pausa: no puede ir a otro hilo).
        main.post(deferred);
        es.foco.next.runtime.HomeRole.check(this); // etapa 1.5: launcher_cambiado al volver
        if (repository != null) {
            DailyReminder.ensure(this);
            if (getIntent().getBooleanExtra("open_day", false)) page = 0;
            int asked = getIntent().getIntExtra(EXTRA_PAGE, -1);
            if (asked >= 0 && asked <= 3) {
                getIntent().removeExtra(EXTRA_PAGE);
                page = asked;
            }
            // v26: aviso del búho "NextDNS no responde" → Ajustes y la pregunta.
            boolean dnsChoice =
                    getIntent()
                            .getBooleanExtra(es.foco.next.runtime.DnsAuto.EXTRA_DNS_CHOICE, false);
            if (dnsChoice) {
                getIntent().removeExtra(es.foco.next.runtime.DnsAuto.EXTRA_DNS_CHOICE);
                page = 2;
            }
            render();
            if (dnsChoice) DnsSettings.askAutomatic(this);
            if (music != null) music.enter();
            scheduleScene();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        body.removeCallbacks(revealCards);
        UiMotion.stop(body);
        UiMotion.stop(navigation);
        if (music != null) music.stop();
        main.removeCallbacks(update);
        main.removeCallbacks(sceneBoundary);
        if (sceneReceiverRegistered) {
            unregisterReceiver(sceneChanges);
            sceneReceiverRegistered = false;
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putInt("page", page);
        b.putInt("pendingReminder", pendingReminderMinute);
    }

    void render() {
        UiMotion.stop(body);
        UiMotion.stop(navigation);
        body.removeCallbacks(revealCards);
        body.removeAllViews();
        navigation.removeAllViews();
        budgetText = null;
        windowText = null;
        progress = null;
        musicButton = null;
        musicCaption = null;
        // Auditoría de producto: Bloqueos es una pestaña más (antes solo se llegaba desde una
        // tarjeta al final de Hoy).
        String[] names = {"Estado", "Mi plan", "Ajustes", "Bloqueos"};
        int[] icons = {
            R.drawable.tab_home,
            R.drawable.tab_rules,
            R.drawable.tab_settings,
            R.drawable.tab_blocks
        };
        int[] order = {0, 1, 3, 2};
        ((TextView) findViewById(R.id.page_title)).setText(names[page]);
        for (int i : order) {
            final int p = i;
            Button b =
                    Design.button(
                            this,
                            names[i],
                            false,
                            () -> {
                                if (page == p) return;
                                page = p;
                                ((ScrollView) findViewById(R.id.page_scroll)).scrollTo(0, 0);
                                render();
                            });
            b.setTextSize(14);
            b.setPadding(0, Design.dp(this, 8), 0, Design.dp(this, 8));
            var icon = getDrawable(icons[i]).mutate();
            icon.setTint(getColor(i == page ? R.color.copper : R.color.text_secondary));
            icon.setBounds(0, 0, Design.dp(this, 22), Design.dp(this, 22));
            b.setCompoundDrawables(null, icon, null, null);
            b.setSelected(i == page);
            navigation.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (android.os.Build.VERSION.SDK_INT >= 28)
            ((TextView) findViewById(R.id.page_title)).setAccessibilityHeading(true);
        if (page == 0) today();
        else if (page == 1) plan();
        else if (page == 3) BlocksScreen.render(this, body);
        else settings(); // Lote 1
        body.post(revealCards);
        if (UiMotion.allowed(this)) {
            android.view.View selected =
                    navigation.getChildAt(page == 3 ? 2 : page == 2 ? 3 : page);
            selected.setScaleX(.94f);
            selected.setScaleY(.94f);
            selected.animate().scaleX(1).scaleY(1).setDuration(280).start();
        }
        main.removeCallbacks(update);
        if (resumed && page == 0) main.postDelayed(update, 1000);
    }

    private void scheduleScene() {
        main.removeCallbacks(sceneBoundary);
        if (resumed) main.postDelayed(sceneBoundary, SceneTheme.untilBoundary());
    }

    private void refreshMusicControl() {
        if (resumed && music != null) {
            if (musicButton != null) musicButton.setText(music.button());
            if (musicCaption != null) musicCaption.setText(music.caption());
        }
    }

    private void today() {
        AppConfig c = repository.config();
        careCard();
        Design.note(
                body,
                SceneTheme.night()
                        ? "Una noche para bajar el ritmo"
                        : "Un día para descubrir algo bueno");
        // Auditoría de producto: lo que impide que Foco funcione se ve lo primero, no dentro de la
        // quinta tarjeta.
        boolean connected = FocusAccessibilityService.connected(),
                usage = es.foco.next.log.UsageContrast.allowed(this);
        if (!connected || !c.enabled || c.watchedApps.isEmpty() || !usage) {
            LinearLayout setup = Design.card(body, "Foco todavía no te protege");
            if (!connected) {
                Design.note(
                        setup, "Falta activar Accesibilidad: sin ella Foco no mide ni bloquea.");
                setup.addView(
                        Design.button(
                                this,
                                "Activar medición y bloqueo",
                                true,
                                () -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
            }
            if (!usage) {
                Design.note(
                        setup,
                        "Falta el Acceso de uso: con él Foco mide el tiempo con la misma fuente que Bienestar digital. Sin él, el informe se queda corto.");
                setup.addView(
                        Design.button(
                                this,
                                "Dar Acceso de uso",
                                true,
                                () -> open(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))));
            }
            if (!c.enabled) {
                Design.note(setup, "Tu plan está desactivado.");
                setup.addView(
                        Design.button(
                                this,
                                "Activar mi plan",
                                true,
                                () -> change(n -> n.enabled = true)));
            }
            if (c.watchedApps.isEmpty()) {
                Design.note(setup, "No has elegido apps de ocio.");
                setup.addView(
                        Design.button(this, "Elegir mis apps de ocio", true, () -> showPage(1)));
            }
        }
        LinearLayout time = Design.card(body, "Tu tiempo de ocio");
        budgetText = Design.text(this, "", 36, true);
        time.addView(budgetText);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(300);
        time.addView(progress, new LinearLayout.LayoutParams(-1, Design.dp(this, 10)));
        windowText = Design.text(this, "", 15, false);
        time.addView(windowText);
        // v26: sin banco de minutos. En Enfoque y en el Weekend Lock el ocio se abre con la hora
        // libre de Foco Core (100 % de las tareas de hoy).
        if (es.foco.next.runtime.Weekend.active(this))
            Design.note(
                    time,
                    "Weekend Lock activo hasta el domingo a las 23:59: en Normal y Enfoque el ocio se abre con tu hora libre de Foco Core o un cupón (en Cuidado queda abierto y se registra) y la configuración solo puede endurecerse.");
        dailyReports(); // v28: informe diario único
        updateTime();
        // v27: lo demás de "Hoy" (próximo paso, escena, práctica, nota del día, lo observado) vive
        // en el inicio y en Foco Core. Aquí queda el estado, la música y los reportes.
        LinearLayout hero = Design.card(body, "Música");
        musicButton =
                Design.button(
                        this,
                        "Música tranquila",
                        false,
                        () -> {
                            if (music != null) {
                                music.toggle();
                                refreshMusicControl();
                            }
                        });
        hero.addView(musicButton);
        musicCaption = Design.text(this, "", 13, false);
        hero.addView(musicCaption);
        refreshMusicControl();
        Design.note(hero, "Sin conexión. La música acompaña Foco y la pausa; puedes silenciarla.");
        // Auditoría de producto: estadísticas, registro y exportación estaban en tres tarjetas y
        // dos pantallas; ahora en una.
        LinearLayout week = Design.card(body, "Tu actividad, sin juicios");
        Design.note(
                week,
                "Tiempo de pantalla, tu semana y los bloqueos, todo en este teléfono. Un registro para decidir, no para puntuarte.");
        week.addView(
                Design.button(
                        this,
                        "Ver estadísticas y bloqueos",
                        true,
                        () -> open(new Intent(this, StatsActivity.class)))); // Lote 4
        week.addView(
                Design.button(
                        this,
                        "Exportar mis 7 días · CSV",
                        false,
                        () ->
                                openForResult(
                                        new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                .setType("text/csv")
                                                .addCategory(Intent.CATEGORY_OPENABLE)
                                                .putExtra(
                                                        Intent.EXTRA_TITLE,
                                                        "Foco-semana-" + LocalDate.now() + ".csv"),
                                        12)));
    }

    /**
     * De control a cuidado: el modo del día, con cambio a un toque, y la pregunta de ánimo la
     * primera vez que se abre Foco en el día.
     */
    private void careCard() {
        es.foco.next.core.care.CareMode.Modo m = es.foco.next.runtime.CareStore.mode(this);
        long left = es.foco.next.runtime.CareStore.focusMinutesLeft(this);
        LinearLayout box =
                Design.card(
                        body,
                        "Modo "
                                + es.foco.next.core.care.CareMode.label(m)
                                + (m == es.foco.next.core.care.CareMode.Modo.ENFOQUE
                                        ? " · quedan " + left + " min"
                                        : ""));
        Design.note(box, es.foco.next.core.care.CareMode.description(m));
        box.addView(Design.button(this, "Cambiar de modo", false, this::chooseMode));
        if (!es.foco.next.runtime.CareStore.moodAskedToday(this)
                && java.time.LocalTime.now().getHour() >= 5) main.post(() -> askMood(false));
    }

    private void chooseMode() {
        es.foco.next.core.care.CareMode.Modo m = es.foco.next.runtime.CareStore.mode(this);
        boolean focus = m == es.foco.next.core.care.CareMode.Modo.ENFOQUE;
        long careLeftMs = es.foco.next.runtime.CareStore.careBudgetLeft(this);
        String careNote =
                careLeftMs == Long.MAX_VALUE
                        ? ""
                        : " · te quedan " + careLeftMs / 60_000 + " min hoy";
        String[] items = {
            "Cuidado 15 min" + careNote,
            "Cuidado 1 h",
            "Cuidado 3 h",
            focus
                    ? "Normal (disponible al terminar el Enfoque)"
                    : "Normal: 5 min y 30 min de pausa, con avisos",
            "Enfoque 1 h",
            "Enfoque 2 h",
            "Enfoque hasta las " + es.foco.next.core.AbsoluteRules.nightStart() + ":00"
        };
        new AlertDialog.Builder(this)
                .setTitle("¿Cómo quieres el día?")
                .setItems(
                        items,
                        (d, w) -> {
                            if (w <= 2) {
                                // Cuidado con tiempo: vuelve solo al modo de antes al acabar.
                                pendingCareMin = new int[] {15, 60, 180}[w];
                                askMood(true);
                            } else if (w == 3) {
                                if (!es.foco.next.runtime.CareStore.choose(
                                        this,
                                        es.foco.next.core.care.CareMode.Modo.NORMAL,
                                        "elegido"))
                                    message(
                                            "Normal estará disponible cuando termine tu Enfoque. A Cuidado sí puedes pasar ya.");
                            } else {
                                es.foco.next.core.care.CareMode.Duracion dur =
                                        w == 4
                                                ? es.foco.next.core.care.CareMode.Duracion.UNA_HORA
                                                : w == 5
                                                        ? es.foco.next.core.care.CareMode.Duracion
                                                                .DOS_HORAS
                                                        : es.foco.next.core.care.CareMode.Duracion
                                                                .HASTA_LA_NOCHE;
                                if (!es.foco.next.runtime.CareStore.focus(this, dur))
                                    message("Esa duración ya no está disponible hoy.");
                            }
                            render();
                        })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * "¿Cómo estás?" del 1 al 5 y una palabra opcional. {@code thenCare}: se eligió CUIDADO (el
     * cambio va igual aunque no conteste). Con 1 o 2 se ofrece CUIDADO; nunca se cambia solo.
     */
    private String moodOffered = "";

    /** Minutos de Cuidado elegidos (se aplican tras la pregunta de ánimo). */
    private int pendingCareMin = 60;

    /** v32 (R8): en Enfoque o en el Weekend Lock, Cuidado pide una acción consciente con motivo. */
    private void enterCare(String motivo) {
        String why = es.foco.next.runtime.CareStore.exitNotice(this);
        if (why == null) {
            applyCare(motivo);
            return;
        }
        String[][] motives = es.foco.next.runtime.CareStore.EXIT_MOTIVES;
        String[] labels = new String[motives.length];
        for (int i = 0; i < motives.length; i++) labels[i] = motives[i][1];
        new android.app.AlertDialog.Builder(this)
                .setTitle(why)
                .setItems(labels, (d, w) -> applyCare(motivo + ";motivo_" + motives[w][0]))
                .setNegativeButton("Seguir como estoy", null)
                .show();
    }

    private void applyCare(String motivo) {
        long got = es.foco.next.runtime.CareStore.care(this, pendingCareMin, motivo);
        message(
                got > 0
                        ? "Cuidado durante " + got + " min. Luego vuelves a tu modo de antes."
                        : "Hoy ya usaste tu tiempo de Cuidado (2 h; 3 h si el ánimo está bajo).");
        render();
    }

    private void askMood(boolean thenCare) {
        if (!resumed) return;
        // Una sola vez por día y por pantalla (render() puede repetirse).
        if (!thenCare) {
            String today = LocalDate.now().toString();
            if (today.equals(moodOffered)) return;
            moodOffered = today;
        }
        android.widget.EditText word = Design.field(this, "Una palabra, si quieres", "", false);
        String[] faces = new String[5];
        for (int i = 0; i < 5; i++)
            faces[i] = es.foco.next.core.care.Mood.face(i + 1) + "  " + (i + 1);
        final int[] pick = {0};
        new AlertDialog.Builder(this)
                .setTitle(thenCare ? "Antes de pasar a Cuidado: ¿cómo estás?" : "¿Cómo estás?")
                .setSingleChoiceItems(faces, -1, (d, w) -> pick[0] = w + 1)
                .setView(word)
                .setPositiveButton(
                        "Guardar",
                        (d, w) -> {
                            boolean low =
                                    pick[0] > 0
                                            && es.foco.next.runtime.CareStore.recordMood(
                                                    this,
                                                    pick[0],
                                                    word.getText().toString(),
                                                    thenCare ? "al_elegir_cuidado" : "foco");
                            if (pick[0] == 0) es.foco.next.runtime.CareStore.skipMood(this);
                            else message(es.foco.next.core.care.Mood.reply(pick[0]));
                            if (thenCare) {
                                enterCare("elegido");
                            } else if (low
                                    && es.foco.next.runtime.CareStore.mode(this)
                                            != es.foco.next.core.care.CareMode.Modo.CUIDADO)
                                new AlertDialog.Builder(this)
                                        .setMessage("¿Pasamos a modo Cuidado? Es tu decisión.")
                                        .setPositiveButton(
                                                "Sí, modo Cuidado",
                                                (x, y) -> {
                                                    pendingCareMin = 60;
                                                    enterCare("animo_bajo");
                                                })
                                        .setNegativeButton("Ahora no", null)
                                        .show();
                        })
                .setNegativeButton(
                        thenCare ? "Prefiero no decirlo" : "Ahora no",
                        (d, w) -> {
                            es.foco.next.runtime.CareStore.skipMood(this);
                            if (thenCare) {
                                enterCare("elegido");
                            }
                        })
                .show();
    }

    private void updateTime() {
        if (budgetText == null) return;
        long used = repository.liveUsed(), left = repository.remaining();
        // Etapa 0: la noche (Regla 2) se decide antes que la resta; ver LeisureStatus.
        es.foco.next.core.LeisureStatus s =
                es.foco.next.core.LeisureStatus.of(
                        java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(
                                        es.foco.next.runtime.WallClock.now()),
                                java.time.ZoneId.systemDefault()),
                        es.foco.next.runtime.AppKinds.autoTime(this),
                        repository.enabled(),
                        FocusAccessibilityService.connected(),
                        used,
                        repository.locked(),
                        left);
        budgetText.setText(Design.duration(this, s.availableMs) + " disponibles");
        progress.setProgress(
                (int) ((s.night ? FocusEngine.BUDGET : FocusEngine.BUDGET - s.availableMs) / 1000));
        windowText.setText(s.reason);
    }

    private void plan() {
        AppConfig c = repository.config();
        boolean locked = repository.locked();
        LinearLayout choose = Design.card(body, "Cinco minutos. Después, tú eliges algo más.");
        Design.note(
                choose,
                es.foco.next.runtime.CareStore.mode(this)
                                == es.foco.next.core.care.CareMode.Modo.ENFOQUE
                        ? "Enfoque: al agotar 5 minutos acumulados, tus apps de ocio se cierran hasta el final de la ventana de una hora."
                        : "Modo Normal: 5 minutos de ocio por hora. Si te pasas, no se cierra nada; te aviso con una idea (como mucho 3 veces por hora). En Cuidado, el ocio no se cuenta.");
        if (locked)
            Design.note(
                    choose,
                    "Tu plan está protegido hasta que termine esta ventana. Podrás editarlo después.");
        Set<String> pending = repository.pendingLeave();
        if (!pending.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String p : pending) names.add(AppPicker.label(this, p));
            Design.note(
                    choose,
                    "Saliendo de ocio (tarda 24 horas desde que lo pediste): "
                            + String.join(", ", names)
                            + ".");
        }
        // Regla 1: las redes sociales salen con candado y no se pueden desmarcar.
        choose.addView(
                Design.button(
                        this,
                        "Apps de ocio · " + c.watchedApps.size(),
                        true,
                        () ->
                                edit(
                                        () ->
                                                AppPicker.many(
                                                        this,
                                                        c.watchedApps,
                                                        AbsoluteRules.SOCIAL_PACKAGES,
                                                        null,
                                                        chosen ->
                                                                change(
                                                                        n -> {
                                                                            chosen.removeIf(
                                                                                    p ->
                                                                                            FocusEngine
                                                                                                    .essential(
                                                                                                            n,
                                                                                                            p));
                                                                            chosen.remove(
                                                                                    n.returnApp);
                                                                            n.watchedApps = chosen;
                                                                            n.observedApps
                                                                                    .removeAll(
                                                                                            chosen);
                                                                            n.productiveApps
                                                                                    .removeAll(
                                                                                            chosen);
                                                                        })))));
        absoluteRules();
        categories(c);
    }

    /**
     * v27: los ajustes del inicio viven aquí. Los interruptores escriben las mismas preferencias
     * que lee el inicio; los editores (plan, rutinas, tema, noche, ánimo) se abren en el inicio.
     */
    private void homeSettings() {
        LinearLayout box = Design.card(body, "Inicio (Foco Home)");
        android.content.SharedPreferences home = getSharedPreferences("foco-home", MODE_PRIVATE);
        android.content.SharedPreferences amb = getSharedPreferences("foco-ambient", MODE_PRIVATE);
        homeToggle(box, "Temas con tus fotos (cambian solos con la hora)", home, "themes_on", true);
        homeToggle(box, "Iconos integrados en el dock", home, "dock_blend", true);
        boolean still = amb.getBoolean("saving", false) || amb.getBoolean("reduce", false);
        box.addView(
                Design.button(
                        this,
                        still ? "Movimiento: todo quieto · cambiar" : "Movimiento: suave · cambiar",
                        false,
                        () -> {
                            amb.edit()
                                    .putBoolean("saving", !still)
                                    .putBoolean("reduce", !still)
                                    .apply();
                            render();
                        }));
        boolean routines = es.foco.next.runtime.RoutineStore.enabled(this);
        String off = es.foco.next.runtime.RoutineStore.offPending(this);
        box.addView(
                Design.button(
                        this,
                        routines && (off == null || off.isEmpty())
                                ? "Rutinas obligatorias: encendidas · apagar desde mañana"
                                : routines
                                        ? "Rutinas: se apagan mañana"
                                        : "Rutinas obligatorias: apagadas · encender",
                        false,
                        () -> {
                            es.foco.next.runtime.RoutineStore.setEnabled(
                                    this, !routines || (off != null && !off.isEmpty()));
                            render();
                        }));
        homeAction(box, "Planificar la semana", "plan");
        homeAction(box, "Rutina de la mañana", "rutina_manana");
        homeAction(box, "Rutina de la noche", "rutina_noche");
        homeAction(box, "Horario de la noche", "noche");
        homeAction(box, "Tema del inicio", "tema");
        homeAction(box, "¿Cómo estás hoy?", "animo");
        Design.note(box, "La foto del tema se pone también en la pantalla de bloqueo, sola.");
    }

    private void homeToggle(
            LinearLayout box,
            String label,
            android.content.SharedPreferences p,
            String key,
            boolean def) {
        boolean on = p.getBoolean(key, def);
        box.addView(
                Design.button(
                        this,
                        label + (on ? ": sí" : ": no"),
                        false,
                        () -> {
                            p.edit().putBoolean(key, !on).apply();
                            render();
                        }));
    }

    private void homeAction(LinearLayout box, String label, String action) {
        box.addView(
                Design.button(
                        this,
                        label + " ›",
                        false,
                        () ->
                                open(
                                        new Intent(this, HomeActivity.class)
                                                .putExtra(
                                                        HomeActivity.EXTRA_HOME_ACTION, action))));
    }

    private void settings() {
        AppConfig c = repository.config();
        homeSettings();
        LinearLayout essential = Design.card(body, "Solo lo necesario");
        Design.note(
                essential,
                FocusAccessibilityService.connected()
                        ? "Medición conectada"
                        : "Activa Accesibilidad para medir y bloquear");
        essential.addView(
                Design.button(
                        this,
                        "Accesibilidad",
                        true,
                        () -> open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        essential.addView(
                Design.button(
                        this,
                        c.enabled ? "Desactivar mi plan" : "Activar mi plan",
                        false,
                        () -> {
                            if (c.enabled && StrictAdmin.lockActive(this)) {
                                message(
                                        "Modo estricto: con el candado puesto no se puede desactivar el plan.");
                                return;
                            }
                            // Auditoría de producto: desactivar el plan quita toda la protección;
                            // un toque accidental ya no basta.
                            if (c.enabled)
                                new AlertDialog.Builder(this)
                                        .setTitle("¿Desactivar tu plan?")
                                        .setMessage(
                                                "Tus apps de ocio dejarán de medirse y bloquearse hasta que lo actives de nuevo. Tus bloqueos con nombre siguen funcionando.")
                                        .setPositiveButton(
                                                "Desactivar",
                                                (d, w) -> change(n -> n.enabled = false))
                                        .setNegativeButton("Seguir protegido", null)
                                        .show();
                            else change(n -> n.enabled = true);
                        }));
        // Auditoría v13: los textos largos van a una sola hoja a petición, no en la página.
        essential.addView(
                Design.button(this, "Cómo funciona y privacidad", false, this::showHowItWorks));
        essential.addView(
                Design.button(
                        this,
                        "Otras apps esenciales · " + c.sensitiveApps.size(),
                        false,
                        () ->
                                edit(
                                        () ->
                                                AppPicker.many(
                                                        this,
                                                        c.sensitiveApps,
                                                        p -> change(n -> n.sensitiveApps = p)))));
        StabilityPermissions.card(body, this); // Fase 4: Acceso de uso y batería
        StrictModeCard.add(this, body); // Modo estricto
        SoundSettingsCard.add(this, body); // Fase 6: volumen de música/efectos y "sin sonido"
        DnsSettings.render(this, body); // Fase 3: proveedor, vigilancia y paso de Chrome
        BackupSettings.render(this, body); // v47: Google Drive y API de NextDNS
        LinearLayout data = Design.card(body, "Tus datos");
        data.addView(
                Design.button(
                        this,
                        "Guardar copia de todo (plan, tareas, dinero e informes)",
                        false,
                        () ->
                                openForResult(
                                        new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                .setType("application/json")
                                                .addCategory(Intent.CATEGORY_OPENABLE)
                                                .putExtra(Intent.EXTRA_TITLE, "Foco-Ligero.json"),
                                        10)));
        data.addView(
                Design.button(
                        this,
                        "Restaurar copia",
                        false,
                        () ->
                                edit(
                                        () -> {
                                            if (strictRestoreBlocked()) return;
                                            openForResult(
                                                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                            .setType("application/json")
                                                            .addCategory(Intent.CATEGORY_OPENABLE),
                                                    11);
                                        })));
        Design.note(
                data,
                "Foco "
                        + versionName()
                        + ". Datos locales; sin cuenta ni telemetría. No se copian a la nube ni a otro teléfono.");
    }

    private void showHowItWorks() {
        new AlertDialog.Builder(this)
                .setTitle("Cómo funciona")
                .setMessage(
                        "Foco detecta cambios de app. No lee mensajes ni vídeos. Solo lee textos de pantalla, sin guardarlos, en tres casos: la dirección del navegador (para el bloqueo total de Telegram y si un bloqueo filtra webs), la hoja de pago de Google Play si un bloqueo frena compras, y Ajustes con el modo estricto. Sin servicio VPN. Acceso de uso se consulta al abrir Registro o Estadísticas, una vez al día y, solo con el candado del modo estricto, mientras la Accesibilidad esté desactivada."
                                + "\n\n"
                                + "Llamadas, WhatsApp, Inicio, Ajustes y bancos reconocidos siguen disponibles (de WhatsApp solo se pueden bloquear Estados y canales, con su atajo). Sin modo estricto, Android permite desactivar la Accesibilidad o desinstalar Foco; con él, Foco cierra esas pantallas mientras dure el candado."
                                + "\n\n"
                                + "Protección del plan"
                                + "\n\n"
                                + "Durante el bloqueo, Foco impide cambiar el plan o restaurar una copia para ampliar el tiempo. Con el candado del modo estricto, el plan y los bloqueos solo pueden volverse más estrictos."
                                + "\n\n"
                                + "Para proteger Accesibilidad, DNS privado y la desinstalación, usa el Modo estricto. Las llamadas y apps esenciales siguen disponibles siempre."
                                + "\n\n"
                                + "Música: Morning Routine · Ghostrifter Official; Colorful Flowers · Tokyo Music Walker. Archivos que proporcionaste, reproducidos localmente.")
                .setPositiveButton("Entendido", null)
                .show();
    }

    /**
     * Reglas absolutas: solo lectura. Viven en el código (core/AbsoluteRules); ninguna pantalla ni
     * copia las cambia.
     */
    private void absoluteRules() {
        LinearLayout box = Design.card(body, "Reglas fijas y modos");
        Design.note(
                box,
                "Modos: Cuidado (el ocio no se cuenta ni se limita), Normal (5 min por hora con avisos, sin cierres) y Enfoque (lo eliges tú, con duración: el ocio solo se abre con tu hora libre de Foco Core). Las reglas 1 a 3 valen en todos los modos.");
        Design.note(
                box,
                "1. Redes sociales siempre en ocio: TikTok, Instagram, Threads, Facebook, X, Reddit, Pinterest, Snapchat, YouTube, Kwai y Bluesky. No se pueden quitar, aprobar como productivas ni pasar a observación. Su web está bloqueada siempre, sin ventana de 5 minutos.");
        Design.note(
                box,
                "2. Noche (por defecto de 21:00 a 07:00; ahora "
                        + es.foco.next.runtime.CareStore.night(this).label()
                        + "), todos los días: todo el ocio cerrado, sin hora libre y sin pausas. Con la hora automática apagada, el ocio se trata como de noche. Lo esencial (llamadas, SMS, WhatsApp, mapas, cámara, banco) sigue disponible.");
        Design.note(
                box,
                "3. Una app que estuvo en ocio en los últimos 30 días no puede ser productiva. Sacar una app de ocio tarda 24 horas y no se puede pedir de noche ni en el fin de semana. Todo cambio de categoría queda en el registro.");
        Design.note(
                box,
                "4. Las tareas reales abren el ocio: con el 100 % de tus tareas de hoy en Foco Core ganas 1 h de ocio libre (una vez al día, nunca de noche). Los retos de Lab entrenan la mente y salen en tus reportes, pero no dan minutos.");
        Design.note(
                box,
                "El horario de la noche se ajusta en el inicio (panel Ajustes › Cuidado): proteger más se aplica al momento; soltar horas, desde mañana.");
    }

    /**
     * Foco 1.0, etapa 2: categorías. Endurecer se puede siempre; aprobar una productiva lo
     * confirmas tú.
     */
    private void categories(AppConfig c) {
        LinearLayout box = Design.card(body, "Categorías");
        Design.note(
                box,
                "Telegram y sus clientes: bloqueo total, siempre, también en el navegador. Las apps nuevas se registran sin límite; solo los juegos, vídeo y redes sociales entran en observación con los mismos 5 minutos que el ocio. Si una app pasa de 45 min en un día dos veces, o de 15 aperturas en un día, pasa a ocio.");
        String notice = es.foco.next.runtime.CategoryInspector.notice(this);
        if (!notice.isEmpty()) {
            Design.note(box, notice);
            box.addView(
                    Design.button(
                            this,
                            "Entendido",
                            false,
                            () -> {
                                es.foco.next.runtime.CategoryInspector.clearNotice(this);
                                render();
                            }));
        }
        box.addView(
                Design.button(
                        this,
                        "En observación · " + c.observedApps.size(),
                        false,
                        () ->
                                edit(
                                        () ->
                                                AppPicker.many(
                                                        this,
                                                        c.observedApps,
                                                        null,
                                                        AbsoluteRules.SOCIAL_PACKAGES,
                                                        chosen ->
                                                                change(
                                                                        n -> {
                                                                            chosen.removeIf(
                                                                                    p ->
                                                                                            FocusEngine
                                                                                                            .essential(
                                                                                                                    n,
                                                                                                                    p)
                                                                                                    || Categories
                                                                                                            .totalBlock(
                                                                                                                    p));
                                                                            chosen.remove(
                                                                                    n.returnApp);
                                                                            chosen.removeAll(
                                                                                    n.watchedApps);
                                                                            n.observedApps = chosen;
                                                                            n.productiveApps
                                                                                    .removeAll(
                                                                                            chosen);
                                                                        })))));
        box.addView(
                Design.button(
                        this,
                        "Productivas aprobadas · " + c.productiveApps.size(),
                        false,
                        () ->
                                edit(
                                        () -> {
                                            Set<String> browsers =
                                                    es.foco.next.runtime.CategoryInspector.browsers(
                                                            this);
                                            // Regla 1 y 3: no aparecen las redes sociales, lo que
                                            // nunca puede ser productivo ni lo que estuvo en ocio
                                            // en 30 días.
                                            Set<String> hidden = new HashSet<>();
                                            for (String p : AppPicker.installed(this))
                                                if (!c.productiveApps.contains(p)
                                                        && (Categories.neverProductive(
                                                                        p, browsers.contains(p))
                                                                || repository.recentLeisure(p)))
                                                    hidden.add(p);
                                            hidden.addAll(AbsoluteRules.SOCIAL_PACKAGES);
                                            AppPicker.many(
                                                    this,
                                                    c.productiveApps,
                                                    null,
                                                    hidden,
                                                    chosen ->
                                                            change(
                                                                    n -> {
                                                                        chosen.removeIf(
                                                                                p ->
                                                                                        Categories
                                                                                                .neverProductive(
                                                                                                        p,
                                                                                                        browsers
                                                                                                                .contains(
                                                                                                                        p)));
                                                                        chosen.removeAll(
                                                                                n.watchedApps);
                                                                        n.productiveApps = chosen;
                                                                        n.observedApps.removeAll(
                                                                                chosen);
                                                                    }));
                                        })));
        Design.note(
                box,
                "Productivas: sin límite. No pueden serlo navegadores, YouTube ni redes sociales, ni una app que estuvo en ocio en los últimos 30 días. Música, lanzadores y teclados no tienen categoría.");
        // Supervisor: límites diarios propios y franja estricta (los pone Foco solo; aquí se ven).
        Map<String, Integer> limits = es.foco.next.runtime.Supervisor.limits(this, c);
        if (!limits.isEmpty()) {
            StringBuilder text = new StringBuilder("Límite diario propio: ");
            boolean first = true;
            for (Map.Entry<String, Integer> e : limits.entrySet()) {
                if (!first) text.append(", ");
                first = false;
                text.append(AppPicker.label(this, e.getKey()))
                        .append(' ')
                        .append(e.getValue())
                        .append(" min");
            }
            Design.note(
                    box, text + ". Pasado ese tope no se abre hasta mañana, aunque haya saldo.");
        }
        int franja = es.foco.next.runtime.Supervisor.franja(this);
        if (franja >= 0)
            Design.note(
                    box,
                    "Franja estricta de "
                            + franja
                            + ":00 a "
                            + ((franja + 2) % 24)
                            + ":00 (tu hora pico): sin ocio en ese tramo.");
        LinearLayout proposals = Design.column(this);
        box.addView(proposals, new LinearLayout.LayoutParams(-1, -2));
        es.foco.next.runtime.CategoryInspector.proposals(
                this,
                list -> {
                    if (!resumed || page != 1) return;
                    for (String p : list) {
                        Design.note(
                                proposals,
                                "Propuesta: "
                                        + AppPicker.label(this, p)
                                        + " tuvo uso real en 3 días o más. ¿Pasarla a productiva?");
                        proposals.addView(
                                Design.button(
                                        this,
                                        "Aprobar " + AppPicker.label(this, p),
                                        false,
                                        () ->
                                                edit(
                                                        () ->
                                                                change(
                                                                        n -> {
                                                                            n.observedApps.remove(
                                                                                    p);
                                                                            n.productiveApps.add(p);
                                                                            es.foco.next.log
                                                                                    .InspectorLog
                                                                                    .event(
                                                                                            this,
                                                                                            es.foco
                                                                                                    .next
                                                                                                    .log
                                                                                                    .InspectorLog
                                                                                                    .CATEGORIA,
                                                                                            p,
                                                                                            0,
                                                                                            "observacion>productiva;confirmado",
                                                                                            "cat|ok|"
                                                                                                    + LocalDate
                                                                                                            .now()
                                                                                                    + "|"
                                                                                                    + p);
                                                                        }))));
                        proposals.addView(
                                Design.button(
                                        this,
                                        "Rechazar",
                                        false,
                                        () -> {
                                            es.foco.next.runtime.CategoryInspector.reject(this, p);
                                            render();
                                        }));
                    }
                });
    }

    /**
     * v28: el informe diario único. Foco lo guarda solo al cerrarse cada día (24:00), con todo lo
     * de dentro y fuera del teléfono; aquí se ve el último y se exportan los de 30 días.
     */
    private void dailyReports() {
        LinearLayout box = Design.card(body, "Informe diario");
        String last = es.foco.next.runtime.Inspector.lastDay(this);
        Design.note(
                box,
                "Cada día va de 5:00 a 24:00. Al cerrarse, Foco guarda un informe con tus tareas de Foco Core, el dinero, los tiempos, los bloqueos, los retos de Lab y el DNS. Se queda en el teléfono (90 días); el ánimo no se incluye.");
        Design.note(
                box,
                last.isEmpty()
                        ? "Todavía no hay informes: el primero llega mañana."
                        : "Último informe: " + last + ".");
        box.addView(
                Design.button(
                        this,
                        "Exportar los últimos 30 días",
                        true,
                        () ->
                                openForResult(
                                        new Intent(Intent.ACTION_CREATE_DOCUMENT)
                                                .setType("text/plain")
                                                .addCategory(Intent.CATEGORY_OPENABLE)
                                                .putExtra(
                                                        Intent.EXTRA_TITLE,
                                                        "Foco-informes-"
                                                                + LocalDate.now()
                                                                + ".txt"),
                                        18)));
    }

    /**
     * Auditoría: restaurar una copia sustituye el plan entero (y los tiempos); con el candado no se
     * permite.
     */
    private boolean strictRestoreBlocked() {
        if (!StrictAdmin.lockActive(this)) return false;
        message("Modo estricto: con el candado puesto no se puede restaurar una copia del plan.");
        return true;
    }

    private String versionName() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private void edit(Runnable action) {
        FocusAccessibilityService.checkpoint();
        if (repository.locked()) message("Tu plan se puede cambiar al terminar esta ventana.");
        else action.run();
    }

    void change(Consumer<AppConfig> action) {
        FocusAccessibilityService.checkpoint();
        try {
            AppConfig old = repository.config(), c = repository.config();
            action.accept(c);
            // Auditoría: con el candado del modo estricto el plan solo puede volverse más estricto
            // (antes se podían quitar apps de ocio o añadir esenciales).
            if (StrictAdmin.lockActive(this)) {
                String why = PlanChange.reason(old, c);
                if (why != null) {
                    message("Modo estricto (solo más estricto): " + why + ".");
                    render();
                    return;
                }
            }
            String note = repository.saveConfig(c);
            render();
            if (note != null) message(note);
        } catch (RuntimeException e) {
            message(e.getMessage());
        }
    }

    private void launch(String p) {
        Intent i = getPackageManager().getLaunchIntentForPackage(p);
        if (i != null) {
            repository.recordChoice(p);
            open(i);
        } else message("La app no está disponible.");
    }

    private void open(Intent i) {
        try {
            startActivity(i);
        } catch (RuntimeException e) {
            message("Abre este ajuste desde Ajustes del teléfono.");
        }
    }

    void openForResult(Intent i, int code) {
        try {
            startActivityForResult(i, code);
        } catch (RuntimeException e) {
            message("No se pudo abrir el selector de archivos.");
        }
    }

    void showPage(int p) {
        page = p;
        ((ScrollView) findViewById(R.id.page_scroll)).scrollTo(0, 0);
        render();
    }

    /**
     * Atrás vuelve a Hoy desde cualquier pestaña; solo desde Hoy sale de Foco (evita salidas
     * accidentales).
     */
    @Override
    public void onBackPressed() {
        if (page != 0 && repository != null) {
            showPage(0);
            return;
        }
        super.onBackPressed();
    }

    void message(String text) {
        Toast.makeText(
                        this,
                        text == null ? "No se pudo guardar el cambio" : text,
                        Toast.LENGTH_LONG)
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (repository != null && intent.getBooleanExtra("open_day", false)) {
            page = 0;
            render();
        }
        int asked = intent.getIntExtra(EXTRA_PAGE, -1);
        if (repository != null && asked >= 0 && asked <= 3) {
            intent.removeExtra(EXTRA_PAGE);
            page = asked;
            render();
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        // v47: vuelta del permiso de Google Drive (no trae Uri).
        if (request == es.foco.next.runtime.GoogleDrive.REQUEST_CODE) {
            if (result == RESULT_OK && data != null)
                es.foco.next.runtime.GoogleDrive.onResult(
                        this, data, msg -> BackupSettings.done(this, msg));
            else message("No se conectó Drive. Puedes intentarlo cuando quieras.");
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        if (request == 14 || request == 15) {
            BlocksScreen.onResult(this, request, data.getData());
            return;
        } // Lote 5
        if (request == 18) {
            final Uri target = data.getData();
            new Thread(
                            () -> {
                                String text = es.foco.next.runtime.Inspector.export(this, 30);
                                runOnUiThread(
                                        () -> {
                                            try {
                                                Documents.write(this, target, text);
                                                message(
                                                        "Informes exportados. Puedes analizarlos con Claude.");
                                            } catch (Exception e) {
                                                message("No se pudo escribir el archivo.");
                                            }
                                        });
                            },
                            "foco-exportar-informes")
                    .start();
            return;
        }
        try {
            if (request == 12) { // Foco 1.0, etapa 1: mismo botón, formato nuevo del Inspector (con
                // hora, tiempo y veces separados)
                FocusAccessibilityService.checkpoint();
                es.foco.next.log.InspectorLog.drainLab(this);
                Uri target = data.getData();
                String from = es.foco.next.log.InspectorLog.weekStart(LocalDate.now());
                AppConfig plan = repository.config();
                es.foco.next.log.BlockLog.query(
                        this,
                        db ->
                                es.foco.next.log.InspectorLog.csv(
                                        db,
                                        from,
                                        p -> AppPicker.label(this, p),
                                        es.foco.next.log.InspectorLog.rows(
                                                this, db, LocalDate.now(), 7, plan),
                                        false),
                        csv -> {
                            try {
                                Documents.write(this, target, csv);
                                message(
                                        "Registro exportado. Puedes abrirlo en una hoja de cálculo.");
                            } catch (Exception e) {
                                message("No se pudo escribir el archivo.");
                            }
                        },
                        () -> message("No se pudo leer el registro."));
            }
            if (request == 10) {
                FocusAccessibilityService.checkpoint();
                // Foco Core va dentro de la misma copia (clave "focoCore"); las versiones
                // anteriores la ignoran al restaurar.
                String backup = repository.exportBackup();
                try {
                    org.json.JSONObject j = new org.json.JSONObject(backup);
                    j.put(
                            es.foco.next.runtime.FocoCoreBackup.KEY,
                            es.foco.next.runtime.FocoCoreBackup.export(this));
                    j.put("informes", es.foco.next.runtime.Inspector.backup(this)); // v28
                    backup = j.toString();
                } catch (org.json.JSONException ignored) {
                }
                Documents.write(this, data.getData(), backup);
                message("Copia guardada (plan, tiempos, tareas, dinero e informes)");
            }
            if (request == 11) {
                String json = Documents.read(this, data.getData(), Documents.MAX_IMPORT);
                new AlertDialog.Builder(this)
                        .setMessage("¿Sustituir el plan y los tiempos por esta copia?")
                        .setPositiveButton(
                                "Restaurar",
                                (d, w) -> {
                                    if (strictRestoreBlocked()) return;
                                    FocusAccessibilityService.checkpoint();
                                    try {
                                        repository.importBackup(json);
                                        try {
                                            org.json.JSONObject core =
                                                    new org.json.JSONObject(json)
                                                            .optJSONObject(
                                                                    es.foco.next.runtime
                                                                            .FocoCoreBackup.KEY);
                                            if (core != null)
                                                es.foco.next.runtime.FocoCoreBackup.restore(
                                                        this, core);
                                            es.foco.next.runtime.Inspector.restore(
                                                    this,
                                                    new org.json.JSONObject(json)
                                                            .optJSONObject("informes"));
                                        } catch (org.json.JSONException ignored) {
                                        }
                                        render();
                                    } catch (RuntimeException e) {
                                        message(e.getMessage());
                                    }
                                })
                        .setNegativeButton("Cancelar", null)
                        .show();
            }
        } catch (Exception e) {
            message("No se pudo procesar la copia. Tus datos se conservan.");
        }
    }
}
