package es.foco.next.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import es.foco.next.R;

final class Design {
    private Design() {}

    static int dp(Context c, float n) {
        return Math.round(c.getResources().getDisplayMetrics().density * n);
    }

    static LinearLayout column(Context c) {
        LinearLayout v = new LinearLayout(c);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    static TextView text(Context c, String value, int sp, boolean strong) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(sp);
        v.setTextColor(c.getColor(strong ? R.color.text_primary : R.color.text_secondary));
        v.setPadding(0, dp(c, 4), 0, dp(c, 4));
        // v41: letra del modo juego (Rubik títulos; Lexend texto), igual que las pantallas Compose.
        v.setTypeface(
                strong && sp >= 23
                        ? font(c, R.font.rubik_black, "sans-serif-black")
                        : strong && sp >= 18
                                ? font(c, R.font.rubik_extrabold, "sans-serif-medium")
                                : strong
                                        ? font(c, R.font.lexend_semibold, "sans-serif-medium")
                                        : font(c, R.font.lexend_regular, "sans-serif"));
        v.setIncludeFontPadding(false);
        v.setLetterSpacing(strong && sp >= 20 ? -.02f : 0f);
        v.setLineSpacing(dp(c, 3), 1f);
        return v;
    }

    static void note(LinearLayout parent, String value) {
        parent.addView(text(parent.getContext(), value, 15, false));
    }

    static LinearLayout card(LinearLayout parent, String title) {
        Context c = parent.getContext();
        LinearLayout box = column(c);
        box.setBackgroundResource(R.drawable.card);
        box.getBackground().mutate().setAlpha(237);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.bottomMargin = dp(c, 16);

        parent.addView(box, p);

        if (!title.isEmpty()) {
            TextView heading = text(c, title, 20, true);
            if (android.os.Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
            box.addView(heading);
            gap(box, 8);
        }
        return box;
    }

    static Button button(Context c, String label, boolean primary, Runnable run) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setMinHeight(dp(c, 52));
        // v41: botón del modo juego: relieve abajo; el principal, verde «hacer».
        b.setTypeface(font(c, R.font.rubik_extrabold, "sans-serif-medium"));
        b.setTextColor(primary ? GO_INK : c.getColor(R.color.copper));
        b.setBackground(gameBackground(c, primary));
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(c, 8);
        b.setLayoutParams(p);
        b.setOnClickListener(v -> run.run());
        UiMotion.press(b);

        return b;
    }

    static void gap(LinearLayout parent, int dp) {
        parent.addView(
                new View(parent.getContext()),
                new LinearLayout.LayoutParams(1, dp(parent.getContext(), dp)));
    }

    static EditText field(Context c, String hint, String value, boolean number) {
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(value);
        e.setTextColor(c.getColor(R.color.text_primary));
        e.setHintTextColor(c.getColor(R.color.text_secondary));
        e.setMinHeight(dp(c, 52));
        e.setTypeface(font(c, R.font.lexend_regular, "sans-serif"));
        e.setTextSize(16);
        android.graphics.drawable.GradientDrawable fieldBackground =
                new android.graphics.drawable.GradientDrawable();
        fieldBackground.setColor(c.getColor(R.color.surface));
        fieldBackground.setCornerRadius(dp(c, 16));
        e.setBackground(fieldBackground);
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        if (number) e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    /**
     * Cuidado: medir sin vigilar. Minutos redondeados hacia arriba, sin segundos corriendo en
     * pantalla ("4 min", no "3 min 41 s").
     */
    // ---------------- v41: modo juego ----------------

    /** Verde «hacer» (cara, relieve, tinta): los mismos que Act.GO en Compose. */
    static final int GO_FACE = 0xFF7BD84F, GO_EDGE = 0xFF4F9E2C, GO_INK = 0xFF12280A;

    /** Letra de res/font; si no carga, la del sistema. */
    static Typeface font(Context c, int res, String fallback) {
        try {
            Typeface t = c.getResources().getFont(res);
            if (t != null) return t;
        } catch (RuntimeException ignored) {
        }
        return Typeface.create(fallback, Typeface.NORMAL);
    }

    /** Fondo con relieve: un borde inferior más oscuro y la cara encima (16 dp de radio). */
    static android.graphics.drawable.Drawable gameBackground(Context c, boolean primary) {
        float r = dp(c, 16);
        android.graphics.drawable.GradientDrawable edge = new android.graphics.drawable.GradientDrawable();
        edge.setCornerRadius(r);
        edge.setColor(primary ? GO_EDGE : 0x40000000);
        android.graphics.drawable.Drawable face;
        if (primary) {
            android.graphics.drawable.GradientDrawable f = new android.graphics.drawable.GradientDrawable();
            f.setCornerRadius(r);
            f.setColor(GO_FACE);
            face = f;
        } else face = c.getDrawable(R.drawable.button_secondary).mutate();
        android.graphics.drawable.LayerDrawable ld =
                new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {edge, face});
        int d = dp(c, 4);
        ld.setLayerInset(0, 0, d, 0, 0);
        ld.setLayerInset(1, 0, 0, 0, d);
        return ld;
    }

    static String duration(Context c, long ms) {
        long min = (Math.max(0, ms) + 59_999) / 60_000;
        return min + " min";
    }
}
