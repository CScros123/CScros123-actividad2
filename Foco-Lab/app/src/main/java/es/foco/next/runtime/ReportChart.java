package es.foco.next.runtime;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import es.foco.next.core.backup.DayReport;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * v47: el gráfico del día (PNG de 1080 px de ancho) dibujado con Canvas, sin librerías. Colores de
 * Foco: verdes, nada de rojo. Lo invertido va primero. Cuatro bloques: invertido vs ocio, mapa de
 * calor de 24 horas del ocio, top 3 apps y top 5 rastreadores bloqueados.
 */
final class ReportChart {
    private ReportChart() {}

    static final int W = 1080, PAD = 60;
    static final int BG = 0xFFF7FAF6,
            CARD = 0xFFFFFFFF,
            DARK = 0xFF2F5D4A,
            MID = 0xFF5E9C6A,
            LIGHT = 0xFF9CCB86,
            PALE = 0xFFEAF3EC,
            INK = 0xFF1E3A2F,
            MUTED = 0xFF6B8577;

    static byte[] png(Context c, DayReport d) {
        Typeface bold = font(c, es.foco.next.R.font.rubik_bold, Typeface.DEFAULT_BOLD);
        Typeface text = font(c, es.foco.next.R.font.lexend_regular, Typeface.DEFAULT);
        int h = 1680;
        Bitmap b = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas k = new Canvas(b);
        k.drawColor(BG);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int y = PAD;
        // Título.
        p.setTypeface(bold);
        p.setColor(DARK);
        p.setTextSize(58);
        k.drawText("Tu día con Foco", PAD, y + 50, p);
        p.setTypeface(text);
        p.setColor(MUTED);
        p.setTextSize(32);
        k.drawText(
                d.day
                        + (d.complete || d.generated.length() < 16
                                ? ""
                                : " · hasta las " + d.generated.substring(11, 16)),
                PAD,
                y + 100,
                p);
        y += 140;

        // 1) Invertido vs ocio (lo invertido primero).
        int start = y;
        y = card(k, p, bold, "Invertido y ocio", y, 300);
        long max = Math.max(1, Math.max(d.investedMin(), d.leisureMin()));
        y = bar(k, p, text, bold, "Invertido", d.investedMin(), max, DARK, y, " min");
        bar(k, p, text, bold, "Ocio", d.leisureMin(), max, LIGHT, y, " min");
        y = start + 300 + 30;

        // 2) Mapa de calor del ocio por hora.
        start = y;
        y = card(k, p, bold, "Ocio por hora", y, 260);
        long top = 1;
        for (long v : d.leisureByHour) top = Math.max(top, v);
        float cell = (W - 2 * PAD - 40) / 24f;
        for (int i = 0; i < 24; i++) {
            float x = PAD + 20 + i * cell;
            float t = d.leisureByHour[i] / (float) top;
            p.setColor(d.leisureByHour[i] <= 0 ? PALE : mix(LIGHT, DARK, t));
            k.drawRoundRect(new RectF(x + 2, y, x + cell - 2, y + 90), 10, 10, p);
            if (i % 3 == 0) {
                p.setColor(MUTED);
                p.setTypeface(text);
                p.setTextSize(24);
                k.drawText((i < 10 ? "0" : "") + i, x + 4, y + 130, p);
            }
        }
        y = start + 260 + 30;

        // 3) Top 3 apps por tiempo.
        start = y;
        y = card(k, p, bold, "Apps con más tiempo", y, 300);
        list(k, p, text, bold, d.leisureApps, 3, MID, y, " min", "Sin ocio registrado hoy.");
        y = start + 300 + 30;

        // 4) Top 5 rastreadores bloqueados.
        y = card(k, p, bold, "Rastreadores bloqueados", y, 460);
        if (d.nextDns == null) note(k, p, text, "Sin datos de NextDNS para este día.", y);
        else list(k, p, text, bold, d.nextDns.trackers, 5, DARK, y, "", "Nada bloqueado hoy.");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, out);
        b.recycle();
        return out.toByteArray();
    }

    static Typeface font(Context c, int res, Typeface fallback) {
        try {
            return c.getResources().getFont(res);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Fondo blanco redondeado con título; devuelve la y del contenido. */
    private static int card(Canvas k, Paint p, Typeface bold, String title, int y, int height) {
        p.setColor(CARD);
        k.drawRoundRect(new RectF(PAD - 20, y, W - PAD + 20, y + height), 36, 36, p);
        p.setColor(INK);
        p.setTypeface(bold);
        p.setTextSize(38);
        k.drawText(title, PAD + 10, y + 60, p);
        return y + 100;
    }

    private static int bar(
            Canvas k,
            Paint p,
            Typeface text,
            Typeface bold,
            String label,
            long value,
            long max,
            int color,
            int y,
            String unit) {
        p.setTypeface(text);
        p.setColor(INK);
        p.setTextSize(30);
        k.drawText(ellipsize(label, 24), PAD + 10, y + 36, p);
        float x0 = PAD + 300, x1 = W - PAD - 150;
        p.setColor(PALE);
        k.drawRoundRect(new RectF(x0, y + 8, x1, y + 48), 20, 20, p);
        float w = (x1 - x0) * Math.min(1f, value / (float) Math.max(1, max));
        if (value > 0) {
            p.setColor(color);
            k.drawRoundRect(new RectF(x0, y + 8, x0 + Math.max(40, w), y + 48), 20, 20, p);
        }
        p.setTypeface(bold);
        p.setColor(DARK);
        k.drawText(value + unit, x1 + 20, y + 40, p);
        return y + 70;
    }

    private static int list(
            Canvas k,
            Paint p,
            Typeface text,
            Typeface bold,
            List<DayReport.Item> items,
            int n,
            int color,
            int y,
            String unit,
            String empty) {
        if (items == null || items.isEmpty()) {
            note(k, p, text, empty, y);
            return y + 70;
        }
        long max = 1;
        for (int i = 0; i < items.size() && i < n; i++) max = Math.max(max, items.get(i).value);
        for (int i = 0; i < items.size() && i < n; i++)
            y = bar(k, p, text, bold, items.get(i).name, items.get(i).value, max, color, y, unit);
        return y;
    }

    private static void note(Canvas k, Paint p, Typeface text, String s, int y) {
        p.setTypeface(text);
        p.setColor(MUTED);
        p.setTextSize(30);
        k.drawText(s, PAD + 10, y + 36, p);
    }

    static String ellipsize(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    static int mix(int a, int b, float t) {
        float u = Math.max(0, Math.min(1, t));
        return Color.rgb(
                (int) (Color.red(a) + (Color.red(b) - Color.red(a)) * u),
                (int) (Color.green(a) + (Color.green(b) - Color.green(a)) * u),
                (int) (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u));
    }
}
