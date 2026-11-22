package cl.cocina.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** Reloj analógico con 12, 3, 6 y 9 grandes. */
public class AnalogClockView extends View {

    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int colText, colMuted, colAccent, colBg;
    private int hour, minute, second;
    private boolean dimmed = false;

    public AnalogClockView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        colText = getResources().getColor(R.color.text);
        colMuted = getResources().getColor(R.color.muted);
        colAccent = getResources().getColor(R.color.accent);
        colBg = getResources().getColor(R.color.bg);

        facePaint.setColor(colBg);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        numPaint.setTextAlign(Paint.Align.CENTER);
        numPaint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        handPaint.setStrokeCap(Paint.Cap.ROUND);
        secPaint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setTime(int h, int m, int s) {
        hour = h; minute = m; second = s;
        invalidate();
    }

    public void setDimmed(boolean d) {
        dimmed = d;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) / 2f * 0.96f;
        if (r <= 0) return;

        int main = dimmed ? colMuted : colText;

        // Esfera
        c.drawCircle(cx, cy, r, facePaint);
        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setColor(colMuted);
        tickPaint.setStrokeWidth(r * 0.012f);
        c.drawCircle(cx, cy, r, tickPaint);
        tickPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        // Marcas de minutos y horas (no se dibujan donde van los números grandes)
        for (int i = 0; i < 60; i++) {
            if (i % 15 == 0) continue;
            boolean hourMark = i % 5 == 0;
            double a = Math.toRadians(i * 6);
            float outer = r * 0.93f;
            float inner = hourMark ? r * 0.80f : r * 0.88f;
            tickPaint.setColor(hourMark ? main : colMuted);
            tickPaint.setStrokeWidth(hourMark ? r * 0.025f : r * 0.009f);
            c.drawLine(cx + (float) Math.sin(a) * inner, cy - (float) Math.cos(a) * inner,
                    cx + (float) Math.sin(a) * outer, cy - (float) Math.cos(a) * outer, tickPaint);
        }

        // Números grandes 12, 3, 6, 9
        numPaint.setColor(main);
        numPaint.setTextSize(r * 0.30f);
        float off = -(numPaint.descent() + numPaint.ascent()) / 2f;
        float nr = r * 0.72f;
        c.drawText("12", cx, cy - nr + off, numPaint);
        c.drawText("3", cx + nr, cy + off, numPaint);
        c.drawText("6", cx, cy + nr + off, numPaint);
        c.drawText("9", cx - nr, cy + off, numPaint);

        // Agujas
        float hAngle = (hour % 12) * 30f + minute * 0.5f + second / 120f;
        float mAngle = minute * 6f + second * 0.1f;
        float sAngle = second * 6f;

        handPaint.setColor(main);
        handPaint.setStrokeWidth(r * 0.055f);
        drawHand(c, cx, cy, hAngle, r * 0.50f, r * 0.08f, handPaint);
        handPaint.setStrokeWidth(r * 0.035f);
        drawHand(c, cx, cy, mAngle, r * 0.78f, r * 0.10f, handPaint);

        if (!dimmed) {
            secPaint.setColor(colAccent);
            secPaint.setStrokeWidth(r * 0.012f);
            drawHand(c, cx, cy, sAngle, r * 0.86f, r * 0.16f, secPaint);
        }

        handPaint.setStyle(Paint.Style.FILL);
        handPaint.setColor(dimmed ? colMuted : colAccent);
        c.drawCircle(cx, cy, r * 0.04f, handPaint);
        handPaint.setColor(colBg);
        c.drawCircle(cx, cy, r * 0.015f, handPaint);
        handPaint.setStyle(Paint.Style.STROKE);
    }

    private static void drawHand(Canvas c, float cx, float cy, float deg, float len, float tail, Paint p) {
        double a = Math.toRadians(deg);
        float sx = (float) Math.sin(a), sy = (float) Math.cos(a);
        c.drawLine(cx - sx * tail, cy + sy * tail, cx + sx * len, cy - sy * len, p);
    }
}
