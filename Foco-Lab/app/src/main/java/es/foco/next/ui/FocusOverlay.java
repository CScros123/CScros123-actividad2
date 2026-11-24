package es.foco.next.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import es.foco.next.R;
import es.foco.next.core.AppConfig;

/** Touchable pause with a visible-only local scene; focus rules remain in the engine. */
public final class FocusOverlay {
    private final AccessibilityService service;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private View view;
    private TextView remaining;
    private boolean warning;
    private boolean dns; // Fase 3: la vista actual es la pausa por DNS privado
    private long shownMinutes = -1;
    private boolean shownNight;
    private boolean playLofi = true;
    private final QuietMusic music;
    private Button musicButton;
    private TextView musicCaption;
    private final Runnable hide = this::dismiss;

    public FocusOverlay(AccessibilityService s) {
        service = s;
        wm = s.getSystemService(WindowManager.class);
        music = new QuietMusic(s, this::refreshMusicControl, true);
    }

    public boolean visible() {
        return view != null;
    }

    public boolean warningVisible() {
        return warning && view != null;
    }

    /** Lote 1: datos de un bloqueo con nombre para la misma pausa. */
    public static final class RuleBlock {
        public String id = "", name = "";
        public long resetsIn;
        public int pausesLeft, pauseMinutes;
        public Runnable pause;

        /**
         * Lote 2: título de sesión ("Bloqueo rápido", "Pomodoro 2/4 · enfoque"); sin pausas. Null
         * en bloqueos con nombre.
         */
        public String session;

        /** Lote 2: bloqueado por su horario (o lista blanca en horario): "Termina en X". */
        public boolean schedule;

        /** Lote 3: sitio bloqueado (dominio o palabra) o navegador no compatible. */
        public String web;

        public boolean browser;

        /**
         * Sección bloqueada dentro de una app ("Shorts de YouTube"); {@code back} sale de ella
         * (botón «Volver»).
         */
        public String section;

        public Runnable back;

        String key() {
            return id
                    + "|"
                    + pausesLeft
                    + "|"
                    + session
                    + "|"
                    + schedule
                    + "|"
                    + web
                    + "|"
                    + browser
                    + "|"
                    + section;
        }

        String reason() {
            if (section != null) return "«" + name + "»: " + section + " bloqueados";
            if (browser) return "Navegador no compatible bloqueado por «" + name + "»";
            if (web != null) return "Sitio bloqueado por «" + name + "»: " + web;
            if (session != null) return session + " · termina en " + span(resetsIn);
            if (schedule) return "«" + name + "» · horario de bloqueo";
            return "Límite de «" + name + "» alcanzado.";
        }

        String time() {
            if (web != null || browser || section != null)
                return schedule
                        ? "Termina en " + span(resetsIn)
                        : "Mientras el bloqueo esté activado";
            return session != null
                    ? "Sin pausas hasta que termine"
                    : schedule ? "Termina en " + span(resetsIn) : until(resetsIn);
        }
    }

    private String shownRule;

    public void block(AppConfig c, long left) {
        block(c, left, null);
    }

    static String until(long ms) {
        if (ms <= 0) return "Bloqueo sin reinicio automático";
        return "Se reinicia en " + span(ms);
    }

    static String span(long ms) {
        long m = Math.max(1, (ms + 59_999) / 60_000);
        return m < 60 ? m + " min" : (m / 60) + " h" + (m % 60 == 0 ? "" : " " + (m % 60) + " min");
    }

    private TextView reasonView;

    public void block(AppConfig c, long left, RuleBlock rule) {
        if (rule != null) left = rule.resetsIn > 0 ? rule.resetsIn : 60 * 60_000L;
        long minutes = Math.max(1, (left + 59_999) / 60_000);
        String key = rule == null ? null : rule.key();
        if (view != null
                && !warning
                && !dns
                && shownNight == SceneTheme.night()
                && java.util.Objects.equals(shownRule, key)) {
            if (shownMinutes != minutes) {
                remaining.setText(
                        rule == null ? "Podrás volver en " + minutes + " min" : rule.time());
                if (rule != null && reasonView != null) reasonView.setText(rule.reason());
                shownMinutes = minutes;
            }
            return;
        }
        dismiss();
        shownRule = key;
        // Música durante la pausa: lo-fi (por defecto), ninguna, o Spotify/Apple Music (una vez por
        // bloqueo, sin pausar otras).
        es.foco.next.runtime.PauseMusic.Action pauseMusic =
                es.foco.next.runtime.PauseMusicLauncher.start(service, left);
        boolean external =
                pauseMusic == es.foco.next.runtime.PauseMusic.Action.OPEN_LINK
                        || pauseMusic == es.foco.next.runtime.PauseMusic.Action.SEARCH;
        playLofi = pauseMusic == es.foco.next.runtime.PauseMusic.Action.LOFI;
        if (!external)
            es.foco.next.runtime.MediaPause.pauseOthers(
                    service); // Fase 6: tecla PAUSE multimedia además del foco de audio
        android.content.res.Configuration dark =
                new android.content.res.Configuration(service.getResources().getConfiguration());
        dark.uiMode =
                (dark.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        | android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Context scene =
                new android.view.ContextThemeWrapper(
                        service.createConfigurationContext(dark), R.style.AppTheme);
        shownNight = SceneTheme.night();
        ScrollView scroll = new ScrollView(scene);
        scroll.setFillViewport(true);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(scene.getColor(R.color.surface));
        scroll.setBackground(bg);
        LinearLayout box = Design.column(scene);
        box.setGravity(Gravity.CENTER);
        box.setPadding(
                Design.dp(service, 28),
                Design.dp(service, 48),
                Design.dp(service, 28),
                Design.dp(service, 32));
        scroll.addView(box);
        // v28: pausa simple. La foto del tema de ahora con un velo, el búho, una frase y lo
        // siguiente de Foco Core. Sin vídeo, órbita, hojas ni frases que rotan solas.
        FrameLayout environment = new FrameLayout(scene);
        android.graphics.Bitmap photo = themePhoto();
        if (photo != null) {
            ImageView bgPhoto = new ImageView(scene);
            bgPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
            bgPhoto.setImageBitmap(photo);
            environment.addView(bgPhoto, new FrameLayout.LayoutParams(-1, -1));
            View veil = new View(scene);
            veil.setBackgroundColor(0xB8000000);
            environment.addView(veil, new FrameLayout.LayoutParams(-1, -1));
            scroll.setBackgroundColor(0);
        }
        ImageView owl = new ImageView(scene);
        owl.setImageResource(R.drawable.owl_face);
        owl.setContentDescription("Búho de Foco");
        box.addView(owl, new LinearLayout.LayoutParams(Design.dp(scene, 96), Design.dp(scene, 96)));
        Design.gap(box, 10);
        label(box, "Tu tiempo vuelve a ser tuyo", 26, 0xFFFFE7A3);
        reasonView =
                label(
                        box,
                        rule == null
                                ? "Ya usaste tus 5 minutos de ocio de esta hora."
                                : rule.reason(),
                        17,
                        0xFFE4D0A1);
        remaining =
                label(
                        box,
                        rule == null ? "Podrás volver en " + minutes + " min" : rule.time(),
                        18,
                        0xFFE5BF70);
        shownMinutes = minutes;
        Design.gap(box, 14);
        label(box, "«" + phrase() + "»", 17, 0xFFFFFFFF);
        Design.gap(box, 6);
        if (rule != null && rule.back != null)
            box.addView(
                    action(
                            scene, "Volver", true,
                            rule.back)); // secciones: salir de Shorts/Reels sin cerrar la app
        if (rule != null
                && rule.session
                        == null) { // Lote 1: pausa con límite diario, decidida por RuleEngine
            if (rule.pausesLeft > 0 && rule.pause != null)
                box.addView(
                        action(
                                scene,
                                "Pausar "
                                        + rule.pauseMinutes
                                        + " min (quedan "
                                        + rule.pausesLeft
                                        + " hoy)",
                                false,
                                rule.pause));
            else label(box, "No quedan pausas hoy para este bloqueo", 13, 0xFFE4D0A1);
        }
        Design.gap(box, 16);
        label(box, nextCoreTask(), 21, 0xFFFFFFFF);
        musicButton =
                action(
                        scene,
                        "Música tranquila",
                        false,
                        () -> {
                            music.toggle();
                            refreshMusicControl();
                        });
        box.addView(musicButton);
        musicCaption = label(box, music.caption(), 13, 0xFFE4D0A1);
        if (PracticeEntry.chestAvailable(service))
            box.addView(
                    action(
                            scene,
                            "Abrir tu cofre 🎁",
                            true,
                            () -> PracticeEntry.openChest(service))); // Lote C: solo si se ganó practicando
        box.addView(
                action(
                        scene,
                        "Practicar 3 min",
                        true,
                        () -> PracticeEntry.open(service, true))); // Fase 2: puente a Lab
        box.addView(
                action(
                        scene,
                        "Juegos",
                        false,
                        () -> PracticeEntry.openGames(service))); // Menú completo de juegos de Lab
        box.addView(
                action(
                        scene,
                        "Descansar · ir a Inicio",
                        false,
                        () -> {
                            es.foco.next.log.BlockLog.record(
                                    service,
                                    es.foco.next.log.BlockEvent.Type.HOME,
                                    "",
                                    "pausa_inicio"); /* Fase 4 */
                            home();
                        }));
        box.addView(
                action(
                        scene,
                        "Llamadas",
                        false,
                        () -> {
                            try {
                                service.startActivity(
                                        new Intent(Intent.ACTION_DIAL)
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                            } catch (RuntimeException e) {
                                home();
                            }
                        }));
        environment.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        view = environment;
        view.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(View v) {}

                    @Override
                    public void onViewDetachedFromWindow(View v) {
                        music.stop();
                    }
                });
        WindowManager.LayoutParams p =
                params(
                        -1,
                        -1,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        p.gravity = Gravity.FILL;
        wm.addView(view, p);
        if (playLofi) music.enter();
        refreshMusicControl();
    }

    /** Fase 3: DNS privado apagado o distinto. Solo ventana aquí; la vista está en DnsPause. */
    public void dnsBlock(String host) {
        if (view != null && dns) return;
        dismiss();
        dns = true;
        view = DnsPause.build(service, host, this::home);
        WindowManager.LayoutParams p =
                params(
                        -1,
                        -1,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        p.gravity = Gravity.FILL;
        wm.addView(view, p);
    }

    public boolean dnsVisible() {
        return dns && view != null;
    }

    public void warn() {
        dismiss();
        warning = true;
        TextView text = new TextView(service);
        text.setText("Foco · te queda 1 minuto de ocio. ¿Qué quieres hacer después?");
        text.setTextColor(0xFFF6F7FB);
        text.setTextSize(16);
        int pad = Design.dp(service, 16);
        text.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF284533);
        bg.setCornerRadius(pad);
        text.setBackground(bg);
        view = text;
        WindowManager.LayoutParams p =
                params(
                        -1,
                        -2,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        p.gravity = Gravity.TOP;
        p.y = Design.dp(service, 48);
        wm.addView(view, p);
        main.postDelayed(hide, 3500);
    }

    private Button action(Context context, String text, boolean primary, Runnable run) {
        Button b = Design.button(context, text, primary, run);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(primary ? 0xED493517 : 0xDE121416);
        bg.setCornerRadius(Design.dp(context, 18));
        bg.setStroke(Design.dp(context, 1), primary ? 0xFFFFD783 : 0x997E683E);
        b.setBackground(bg);
        b.setTextColor(primary ? 0xFFFFE6AA : 0xFFE4D0A1);
        return b;
    }

    private TextView label(LinearLayout box, String s, int size, int color) {
        TextView t = Design.text(box.getContext(), s, size, true);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        box.addView(t);
        return t;
    }

    private void refreshMusicControl() {
        if (musicButton != null) musicButton.setText(music.button());
        if (musicCaption != null) musicCaption.setText(music.caption());
    }

    private WindowManager.LayoutParams params(int w, int h, int flags) {
        return new WindowManager.LayoutParams(
                w,
                h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
    }

    private void home() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME);
    }

    public void dismiss() {
        playLofi = true;
        music.stop();
        musicButton = null;
        musicCaption = null;
        main.removeCallbacks(hide);
        if (view != null) {
            try {
                wm.removeViewImmediate(view);
            } catch (RuntimeException ignored) {
            }
            view = null;
        }
        warning = false;
        dns = false;
        shownRule = null;
        remaining = null;
        shownMinutes = -1;
    }

    /** v27: lo siguiente es tu próxima tarea de Foco Core. */
    private String nextCoreTask() {
        String t = es.foco.next.runtime.RoutineStore.nextTaskLabel(service);
        return t.isEmpty() ? "Anota tus tareas de hoy en Foco Core" : "Lo siguiente: " + t;
    }

    /** v28: una frase de ánimo por pausa (la siguiente cada vez, sin repetir seguidas). */
    private String phrase() {
        try {
            android.content.SharedPreferences p = service.getSharedPreferences("foco-pausa-frases", 0);
            long index = p.getLong("index", 0);
            p.edit().putLong("index", index + 1).apply();
            return es.foco.next.core.PauseMessages.next(
                            java.util.EnumSet.of(
                                    es.foco.next.core.PauseMessages.Category.ANIMO,
                                    es.foco.next.core.PauseMessages.Category.CUERPO,
                                    es.foco.next.core.PauseMessages.Category.FE),
                            java.time.LocalDate.now().toEpochDay(),
                            index)
                    .text;
        } catch (RuntimeException e) {
            return "Respira. Lo que importa sigue aquí.";
        }
    }

    /** v28: la foto del tema que muestra el inicio ahora (la guarda HomeViewModel), reducida. */
    private android.graphics.Bitmap themePhoto() {
        try {
            String key = service.getSharedPreferences("foco-home", 0).getString("theme_now", "");
            if (key == null || key.isEmpty()) return null;
            int res = service.getResources().getIdentifier("theme_" + key, "drawable", service.getPackageName());
            if (res == 0) return null;
            android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
            o.inSampleSize = 2;
            o.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeResource(service.getResources(), res, o);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }
}
