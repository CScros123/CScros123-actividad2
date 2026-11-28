package es.foco.next.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import es.foco.next.R;

/** Lote 4: seven bars drawn with Canvas; the last one (today) in the accent colour. */
final class WeekBars extends View {
    private long[] values = new long[0];
    private String[] labels = new String[0];
    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG),
            today = new Paint(Paint.ANTI_ALIAS_FLAG),
            text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    WeekBars(Context c) {
        super(c);
        bar.setColor(c.getColor(R.color.outline));
        today.setColor(c.getColor(R.color.copper));
        text.setColor(c.getColor(R.color.text_secondary));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(Design.dp(c, 12));
    }

    void set(long[] values, String[] labels, String description) {
        this.values = values;
        this.labels = labels;
        setContentDescription(description);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int n = values.length;
        if (n == 0) return;
        long max = 1;
        for (long v : values) max = Math.max(max, v);
        float w = getWidth(),
                h = getHeight(),
                label = Design.dp(getContext(), 20),
                slot = w / n,
                gap = slot * 0.25f,
                radius = Design.dp(getContext(), 6);
        for (int i = 0; i < n; i++) {
            float top = (h - label) * (1f - (float) values[i] / max);
            r.set(
                    i * slot + gap / 2,
                    Math.min(top, h - label - Design.dp(getContext(), 2)),
                    (i + 1) * slot - gap / 2,
                    h - label);
            c.drawRoundRect(r, radius, radius, i == n - 1 ? today : bar);
            if (i < labels.length)
                c.drawText(labels[i], i * slot + slot / 2, h - Design.dp(getContext(), 4), text);
        }
    }
}
