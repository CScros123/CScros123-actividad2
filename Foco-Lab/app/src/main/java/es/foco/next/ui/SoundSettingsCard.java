package es.foco.next.ui;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import es.foco.next.R;
import es.foco.next.runtime.PauseMusic;
import es.foco.next.runtime.SoundSettings;

/**
 * Fase 6: tarjeta "Sonido" en Ajustes. Música y efectos por separado, "sin sonido" y pausa de otras
 * apps.
 */
final class SoundSettingsCard {
    private SoundSettingsCard() {}

    static void add(Context c, LinearLayout body) {
        LinearLayout box = Design.card(body, "Sonido");
        Switch silent = toggle(c, box, "Sin sonido", SoundSettings.silent(c));
        TextView musicLabel = Design.text(c, "", 15, false);
        box.addView(musicLabel);
        SeekBar music = slider(c, box, SoundSettings.musicVolume(c));
        TextView effectsLabel = Design.text(c, "", 15, false);
        box.addView(effectsLabel);
        SeekBar effects = slider(c, box, SoundSettings.effectsVolume(c));
        Runnable labels =
                () -> {
                    boolean off = SoundSettings.silent(c);
                    musicLabel.setText(
                            "Música · "
                                    + SoundSettings.musicVolume(c)
                                    + " %"
                                    + (off ? " (silenciada)" : ""));
                    effectsLabel.setText(
                            "Efectos de los juegos · "
                                    + SoundSettings.effectsVolume(c)
                                    + " %"
                                    + (off ? " (silenciados)" : ""));
                    music.setEnabled(!off);
                    effects.setEnabled(!off);
                };
        labels.run();
        silent.setOnCheckedChangeListener(
                (v, on) -> {
                    SoundSettings.setSilent(c, on);
                    labels.run();
                });
        music.setOnSeekBarChangeListener(
                listener(
                        p -> {
                            SoundSettings.setMusicVolume(c, p);
                            labels.run();
                        }));
        effects.setOnSeekBarChangeListener(
                listener(
                        p -> {
                            SoundSettings.setEffectsVolume(c, p);
                            labels.run();
                        }));
        Switch pause =
                toggle(c, box, "Pausar otras apps al bloquear", SoundSettings.pauseOthers(c));
        pause.setOnCheckedChangeListener((v, on) -> SoundSettings.setPauseOthers(c, on));
        // Música durante la pausa: lo-fi de Foco / Spotify / Apple Music / ninguna, y enlace
        // opcional de playlist.
        TextView musicTitle = Design.text(c, "Música durante la pausa", 15, false);
        box.addView(musicTitle);
        android.widget.RadioGroup group = new android.widget.RadioGroup(c);
        box.addView(group);
        String[] names = {"Lo-fi de Foco", "Spotify", "Apple Music", "Ninguna"};
        PauseMusic.Choice[] values = {
            PauseMusic.Choice.LOFI,
            PauseMusic.Choice.SPOTIFY,
            PauseMusic.Choice.APPLE,
            PauseMusic.Choice.NONE
        };
        for (int i = 0; i < names.length; i++) {
            android.widget.RadioButton r = new android.widget.RadioButton(c);
            r.setId(android.view.View.generateViewId());
            r.setText(names[i]);
            r.setTextColor(c.getColor(R.color.text_primary));
            r.setMinHeight(Design.dp(c, 48));
            group.addView(r);
            if (values[i] == SoundSettings.pauseMusic(c)) r.setChecked(true);
            PauseMusic.Choice v = values[i];
            // Auditoría: la app de música elegida nunca se bloquea; con el candado del modo
            // estricto no se puede elegir una nueva.
            if (PauseMusic.packageFor(v) != null
                    && values[i] != SoundSettings.pauseMusic(c)
                    && es.foco.next.runtime.StrictAdmin.lockActive(c)) r.setEnabled(false);
            r.setOnCheckedChangeListener(
                    (b, on) -> {
                        if (on) SoundSettings.setPauseMusic(c, v);
                    });
        }
        if (es.foco.next.runtime.StrictAdmin.lockActive(c))
            Design.note(
                    box,
                    "Modo estricto: mientras dure el candado no se puede elegir otra app de música (la elegida nunca se bloquea).");
        android.widget.EditText link = new android.widget.EditText(c);
        link.setHint("Enlace de tu playlist (opcional)");
        link.setSingleLine(true);
        link.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        link.setText(SoundSettings.playlistLink(c));
        link.setOnFocusChangeListener(
                (v, has) -> {
                    if (!has) SoundSettings.setPlaylistLink(c, link.getText().toString());
                });
        link.addTextChangedListener(
                new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int a, int b, int d) {}

                    @Override
                    public void onTextChanged(CharSequence s, int a, int b, int d) {}

                    @Override
                    public void afterTextChanged(android.text.Editable e) {
                        SoundSettings.setPlaylistLink(c, e.toString());
                    }
                });
        box.addView(link, new LinearLayout.LayoutParams(-1, -2));
        Design.note(
                box,
                "Con Spotify o Apple Music, Foco no pausa otras apps y abre tu playlist (o busca \"lofi\") una vez por bloqueo. Si la app no está instalada, suena el lo-fi de Foco. Enlaces: open.spotify.com, spotify:… o music.apple.com.");
        appleMusic(c, box);
        Design.note(
                box,
                "Los efectos de los juegos también callan con el modo silencio, vibración o No molestar. Al bloquear, Foco pide a la app que suena que se pause; algunas apps pueden ignorarlo.");
    }

    /**
     * v48: Apple Music directo. Usa el mismo enlace (music.apple.com). Suena al entrar en Enfoque
     * (si lo activas), en la pausa (si eliges Apple Music arriba) y con el botón del inicio.
     */
    private static void appleMusic(Context c, LinearLayout box) {
        Design.gap(box, 8);
        box.addView(Design.text(c, "Apple Music", 15, true));
        if (!es.foco.next.runtime.AppleMusic.installed(c)) {
            Design.note(box, "Apple Music no está instalada.");
            return;
        }
        Switch focus =
                toggle(c, box, "Poner mi playlist al entrar en Enfoque", SoundSettings.appleOnFocus(c));
        focus.setOnCheckedChangeListener((v, on) -> SoundSettings.setAppleOnFocus(c, on));
        boolean control = es.foco.next.runtime.AppleMusic.canControl(c);
        String now = control ? es.foco.next.runtime.AppleMusic.nowPlaying(c) : "";
        Design.note(
                box,
                control
                        ? (now.isEmpty() ? "Control activado." : "Suena: " + now)
                        : "Para que Foco vea qué suena y la ponga a sonar seguro, permite «Foco · música» en Acceso a notificaciones. Foco solo mira de qué app viene cada aviso, nunca lo que dice (de noche lo usa también para quitar los avisos de redes).");
        if (!control)
            box.addView(
                    Design.button(
                            c,
                            "Permitir control de la música",
                            false,
                            () -> {
                                try {
                                    c.startActivity(
                                            es.foco.next.runtime.AppleMusic.accessSettings());
                                } catch (RuntimeException ignored) {
                                }
                            }));
        box.addView(
                Design.button(
                        c,
                        "Poner mi playlist ahora",
                        true,
                        () ->
                                android.widget.Toast.makeText(
                                                c,
                                                es.foco.next.runtime.AppleMusic.play(c),
                                                android.widget.Toast.LENGTH_SHORT)
                                        .show()));
    }

    private static Switch toggle(Context c, LinearLayout box, String label, boolean checked) {
        Switch s = new Switch(c);
        s.setText(label);
        s.setTextColor(c.getColor(R.color.text_primary));
        s.setMinHeight(Design.dp(c, 52));
        s.setChecked(checked);
        box.addView(s);
        return s;
    }

    private static SeekBar slider(Context c, LinearLayout box, int value) {
        SeekBar bar = new SeekBar(c);
        bar.setMax(100);
        bar.setProgress(value);
        bar.setMinimumHeight(Design.dp(c, 44));
        box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        return bar;
    }

    private static SeekBar.OnSeekBarChangeListener listener(
            java.util.function.IntConsumer changed) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) changed.accept(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {}
        };
    }
}
