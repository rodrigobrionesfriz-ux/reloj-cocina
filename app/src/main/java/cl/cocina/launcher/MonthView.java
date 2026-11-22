package cl.cocina.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.Set;

/** Calendario mensual dibujado a mano. Semana parte el lunes. */
public class MonthView extends View {

    public interface OnDayClickListener {
        void onDayClick(int day);
    }

    private static final String[] HEADERS = {"L", "M", "M", "J", "V", "S", "D"};

    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int colText, colMuted, colAccent, colSunday, colBg;

    private int year, month;            // month 0-11
    private int todayYear = -1, todayMonth = -1, todayDay = -1;
    private int selectedDay = -1;
    private Set<Integer> eventDays = new HashSet<Integer>();
    private OnDayClickListener listener;

    public MonthView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        colText = getResources().getColor(R.color.text);
        colMuted = getResources().getColor(R.color.muted);
        colAccent = getResources().getColor(R.color.accent);
        colBg = getResources().getColor(R.color.panel);
        colSunday = 0xFFE58A7B;

        headerPaint.setTextAlign(Paint.Align.CENTER);
        headerPaint.setColor(colMuted);
        dayPaint.setTextAlign(Paint.Align.CENTER);
        dayPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        circlePaint.setColor(colAccent);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(colAccent);
        ringPaint.setStrokeWidth(dp(1.5f));
        dotPaint.setColor(colAccent);

        Calendar c = Calendar.getInstance();
        year = c.get(Calendar.YEAR);
        month = c.get(Calendar.MONTH);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    public void setMonth(int year, int month) {
        this.year = year;
        this.month = month;
        invalidate();
    }

    public void setToday(int y, int m, int d) {
        todayYear = y; todayMonth = m; todayDay = d;
        invalidate();
    }

    public void setSelectedDay(int d) {
        selectedDay = d;
        invalidate();
    }

    public void setEventDays(Set<Integer> days) {
        eventDays = days != null ? days : new HashSet<Integer>();
        invalidate();
    }

    public void setOnDayClickListener(OnDayClickListener l) {
        listener = l;
    }

    private int firstOffset() {
        Calendar cal = new GregorianCalendar(year, month, 1);
        int dow = cal.get(Calendar.DAY_OF_WEEK);   // Domingo = 1
        return (dow + 5) % 7;                       // Lunes = 0
    }

    private int daysInMonth() {
        return new GregorianCalendar(year, month, 1).getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float cw = w / 7f;
        float ch = h / 7f;   // 1 fila de encabezado + 6 semanas
        float textSize = Math.min(ch * 0.48f, cw * 0.45f);

        headerPaint.setTextSize(textSize * 0.8f);
        for (int i = 0; i < 7; i++) {
            headerPaint.setColor(i == 6 ? colSunday : colMuted);
            canvas.drawText(HEADERS[i], cw * i + cw / 2f, ch * 0.62f, headerPaint);
        }

        dayPaint.setTextSize(textSize);
        int offset = firstOffset();
        int days = daysInMonth();
        boolean isCurrentMonth = (year == todayYear && month == todayMonth);
        float radius = Math.min(cw, ch) * 0.44f;

        for (int d = 1; d <= days; d++) {
            int idx = offset + d - 1;
            int row = idx / 7 + 1;
            int col = idx % 7;
            float cx = cw * col + cw / 2f;
            float cy = ch * row + ch / 2f;
            float baseY = cy - (dayPaint.descent() + dayPaint.ascent()) / 2f;

            boolean isToday = isCurrentMonth && d == todayDay;
            if (isToday) {
                canvas.drawCircle(cx, cy, radius, circlePaint);
                dayPaint.setColor(colBg);
                dayPaint.setFakeBoldText(true);
            } else {
                dayPaint.setColor(col == 6 ? colSunday : colText);
                dayPaint.setFakeBoldText(false);
                if (d == selectedDay) canvas.drawCircle(cx, cy, radius, ringPaint);
            }
            canvas.drawText(String.valueOf(d), cx, baseY, dayPaint);

            if (eventDays.contains(d)) {
                dotPaint.setColor(isToday ? colBg : colAccent);
                canvas.drawCircle(cx, cy + radius * 0.68f, dp(2.2f), dotPaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) return true;
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float cw = getWidth() / 7f, ch = getHeight() / 7f;
            int row = (int) (e.getY() / ch);
            int col = (int) (e.getX() / cw);
            if (row >= 1 && col >= 0 && col < 7) {
                int d = (row - 1) * 7 + col - firstOffset() + 1;
                if (d >= 1 && d <= daysInMonth() && listener != null) {
                    listener.onDayClick(d);
                }
            }
            return true;
        }
        return super.onTouchEvent(e);
    }
}
