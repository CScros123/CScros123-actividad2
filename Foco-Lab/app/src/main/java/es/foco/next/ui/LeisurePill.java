package es.foco.next.ui;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * Cronómetro de las apps vigiladas: píldora pequeña arriba a la derecha ("🦉 4 min"). Minutos,
 * nunca segundos; el último minuto en tono cálido (ámbar, sin rojo). Se puede arrastrar y recuerda
 * dónde la dejaste. No roba el foco a la app. La decide la Accesibilidad; aquí solo se dibuja.
 */
public final class LeisurePill {
    private final AccessibilityService service;
    private final WindowManager wm;
    private TextView view;
    private WindowManager.LayoutParams lp;
    private String shown = "";
    private boolean warm;

    public LeisurePill(AccessibilityService service) {
        this.service = service;
        this.wm = service.getSystemService(WindowManager.class);
    }

    private SharedPreferences prefs() {
        return service.getSharedPreferences("foco-pill", Context.MODE_PRIVATE);
    }

    /** {@code text} null = ocultar. */
    public void show(String text, boolean lastMinute) {
        if (text == null) {
            hide();
            return;
        }
        try {
            if (view == null) create();
            if (!text.equals(shown)) {
                view.setText(text);
                shown = text;
            }
            if (lastMinute != warm) {
                warm = lastMinute;
                paint();
            }
        } catch (RuntimeException e) {
            hide();
        }
    }

    public void hide() {
        if (view != null)
            try {
                wm.removeView(view);
            } catch (RuntimeException ignored) {
            }
        view = null;
        shown = "";
    }

    private void paint() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(
                warm ? 0xEE8A5A2B : 0xD91C2920); // ámbar tostado en el último minuto, nunca rojo
        bg.setCornerRadius(Design.dp(service, 999));
        bg.setStroke(Design.dp(service, 1), 0x33FFFFFF);
        view.setBackground(bg);
    }

    private void create() {
        view = new TextView(service);
        view.setTextColor(0xFFF6F7FB);
        view.setTextSize(13);
        view.setTypeface(
                Design.font(
                        view.getContext(),
                        es.foco.next.R.font.rubik_extrabold,
                        "sans-serif-medium")); // v41
        int h = Design.dp(service, 12), v = Design.dp(service, 6);
        view.setPadding(h, v, h, v);
        view.setContentDescription("Tiempo de ocio restante");
        warm = false;
        paint();
        lp =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.END;
        lp.x = prefs().getInt("x", Design.dp(service, 12));
        lp.y = prefs().getInt("y", Design.dp(service, 96));
        // Arrastrar para moverla (por si tapa algo de la app).
        view.setOnTouchListener(
                new android.view.View.OnTouchListener() {
                    float sx, sy;
                    int ox, oy;

                    @Override
                    public boolean onTouch(android.view.View v, MotionEvent e) {
                        switch (e.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                sx = e.getRawX();
                                sy = e.getRawY();
                                ox = lp.x;
                                oy = lp.y;
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                lp.x =
                                        Math.max(
                                                0,
                                                ox - (int) (e.getRawX() - sx)); // gravedad END: x
                                // crece hacia la
                                // izquierda
                                lp.y = Math.max(0, oy + (int) (e.getRawY() - sy));
                                try {
                                    wm.updateViewLayout(view, lp);
                                } catch (RuntimeException ignored) {
                                }
                                return true;
                            case MotionEvent.ACTION_UP:
                            case MotionEvent.ACTION_CANCEL:
                                prefs().edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                                v.performClick();
                                return true;
                            default:
                                return false;
                        }
                    }
                });
        wm.addView(view, lp);
    }
}
