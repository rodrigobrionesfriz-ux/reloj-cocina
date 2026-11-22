package cl.cocina.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class MainActivity extends Activity {

    // ---- Ajustes rápidos ----
    private static final int NIGHT_FROM = 23;   // desde las 23:00
    private static final int NIGHT_TO = 6;      // hasta las 06:59
    private static final float NIGHT_BRIGHTNESS = 0.03f;
    private static final long WAKE_MS = 60 * 1000;

    private static final String PKG_YOUTUBE = "com.google.android.youtube";
    private static final String PKG_NETFLIX = "com.netflix.mediaclient";

    private static final Locale ES = new Locale("es", "CL");
    private static final String[] MONTHS = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};

    private TextView dateText, monthTitle, eventsText;
    private AnalogClockView analog;
    private View clockPanel;
    private MonthView monthView;
    private AppAdapter appAdapter;
    private SharedPreferences prefs;

    private final Handler handler = new Handler();
    private int shownYear, shownMonth, selectedDay;
    private int lastDayOfYear = -1;
    private boolean dimmed = false;
    private long wakeUntil = 0;
    private Map<Integer, List<Ev>> monthEvents = new HashMap<Integer, List<Ev>>();
    private ContentObserver calendarObserver;

    private static class Ev {
        long begin; boolean allDay; String title;
    }

    // =====================================================================
    // Ciclo de vida
    // =====================================================================

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("cocina", MODE_PRIVATE);

        clockPanel = findViewById(R.id.clockPanel);
        analog = (AnalogClockView) findViewById(R.id.analog);
        dateText = (TextView) findViewById(R.id.date);
        monthTitle = (TextView) findViewById(R.id.monthTitle);
        eventsText = (TextView) findViewById(R.id.events);
        monthView = (MonthView) findViewById(R.id.monthView);
        GridView grid = (GridView) findViewById(R.id.apps);

        clockPanel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAlarms(); }
        });

        // Calendario
        goToToday();
        findViewById(R.id.prevMonth).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveMonth(-1); }
        });
        findViewById(R.id.nextMonth).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveMonth(1); }
        });
        monthTitle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToToday(); }
        });
        monthView.setOnDayClickListener(new MonthView.OnDayClickListener() {
            @Override public void onDayClick(int day) {
                selectedDay = day;
                monthView.setSelectedDay(day);
                showDayEvents();
            }
        });
        eventsText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openCalendarApp(); }
        });

        // Caja de aplicaciones
        appAdapter = new AppAdapter(this);
        grid.setAdapter(appAdapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                onTileClick(appAdapter.getItem(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> p, View v, int pos, long id) {
                AppItem it = appAdapter.getItem(pos);
                if (it.type == AppItem.APP) confirmRemove(it);
                return true;
            }
        });

        calendarObserver = new ContentObserver(handler) {
            @Override public void onChange(boolean selfChange) { loadEvents(); }
        };

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        handler.removeCallbacks(tick);
        tick.run();
        reloadApps();
        loadEvents();
        try {
            getContentResolver().registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        try { getContentResolver().unregisterContentObserver(calendarObserver); } catch (Exception ignored) { }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        goToToday();   // botón Inicio: vuelve al mes actual
    }

    @Override
    public void onBackPressed() {
        // Es el launcher: atrás no hace nada
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        loadEvents();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            boolean wasDimmed = dimmed;
            wakeUntil = System.currentTimeMillis() + WAKE_MS;
            updateDim();
            if (wasDimmed) return true;   // primer toque de noche solo enciende
        }
        return super.dispatchTouchEvent(ev);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
    }

    private float dp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }

    // =====================================================================
    // Reloj
    // =====================================================================

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 1000 - (now % 1000) + 10);
        }
    };

    private void updateClock() {
        Calendar c = Calendar.getInstance();
        analog.setTime(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND));

        int doy = c.get(Calendar.DAY_OF_YEAR);
        if (doy != lastDayOfYear) {          // cambio de día (o primera vez)
            lastDayOfYear = doy;
            String d = new SimpleDateFormat("EEEE d 'de' MMMM", ES).format(c.getTime());
            dateText.setText(capitalize(d));
            goToToday();
        }
        updateDim();
    }

    private void updateDim() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        boolean night = hour >= NIGHT_FROM || hour < NIGHT_TO;
        boolean shouldDim = night && System.currentTimeMillis() > wakeUntil;
        if (shouldDim == dimmed) return;
        dimmed = shouldDim;
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = dimmed ? NIGHT_BRIGHTNESS : WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        getWindow().setAttributes(lp);
        analog.setDimmed(dimmed);
    }

    private void openAlarms() {
        Intent i = new Intent("android.intent.action.SHOW_ALARMS");
        if (tryStart(i)) return;
        String[] pkgs = {"com.android.deskclock", "com.google.android.deskclock"};
        for (String p : pkgs) {
            Intent li = getPackageManager().getLaunchIntentForPackage(p);
            if (li != null && tryStart(li)) return;
        }
        tryStart(new Intent(android.provider.AlarmClock.ACTION_SET_ALARM));
    }

    // =====================================================================
    // Calendario
    // =====================================================================

    private void goToToday() {
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH), d = c.get(Calendar.DAY_OF_MONTH);
        monthView.setToday(y, m, d);
        boolean changed = (y != shownYear || m != shownMonth);
        shownYear = y; shownMonth = m; selectedDay = d;
        monthView.setMonth(y, m);
        monthView.setSelectedDay(d);
        monthTitle.setText(MONTHS[m] + " " + y);
        if (changed) loadEvents(); else showDayEvents();
    }

    private void moveMonth(int delta) {
        Calendar c = new GregorianCalendar(shownYear, shownMonth, 1);
        c.add(Calendar.MONTH, delta);
        shownYear = c.get(Calendar.YEAR);
        shownMonth = c.get(Calendar.MONTH);
        Calendar now = Calendar.getInstance();
        selectedDay = (shownYear == now.get(Calendar.YEAR) && shownMonth == now.get(Calendar.MONTH))
                ? now.get(Calendar.DAY_OF_MONTH) : 1;
        monthView.setMonth(shownYear, shownMonth);
        monthView.setSelectedDay(selectedDay);
        monthTitle.setText(MONTHS[shownMonth] + " " + shownYear);
        loadEvents();
    }

    private boolean hasCalendarPermission() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadEvents() {
        final int y = shownYear, m = shownMonth;
        if (!hasCalendarPermission()) {
            monthEvents = new HashMap<Integer, List<Ev>>();
            monthView.setEventDays(null);
            showDayEvents();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Map<Integer, List<Ev>> map = queryMonth(y, m);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (y != shownYear || m != shownMonth) return;
                        monthEvents = map;
                        monthView.setEventDays(new HashSet<Integer>(map.keySet()));
                        showDayEvents();
                    }
                });
            }
        }).start();
    }

    private Map<Integer, List<Ev>> queryMonth(int y, int m) {
        Map<Integer, List<Ev>> map = new HashMap<Integer, List<Ev>>();
        Calendar start = new GregorianCalendar(y, m, 1);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        long begin = start.getTimeInMillis(), finish = end.getTimeInMillis();

        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(b, begin);
        ContentUris.appendId(b, finish);
        String[] proj = {CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY};
        Cursor cur = null;
        try {
            cur = getContentResolver().query(b.build(), proj, null, null,
                    CalendarContract.Instances.BEGIN + " ASC");
            if (cur == null) return map;
            TimeZone utc = TimeZone.getTimeZone("UTC");
            while (cur.moveToNext()) {
                Ev ev = new Ev();
                ev.begin = cur.getLong(0);
                long evEnd = cur.getLong(1);
                ev.title = cur.getString(2);
                ev.allDay = cur.getInt(3) == 1;
                if (TextUtils.isEmpty(ev.title)) ev.title = "(sin título)";

                // Marca cada día que toca el evento dentro del mes
                Calendar c = ev.allDay ? Calendar.getInstance(utc) : Calendar.getInstance();
                c.setTimeInMillis(ev.begin);
                long stop = Math.max(evEnd - 1, ev.begin);
                int guard = 0;
                while (c.getTimeInMillis() <= stop && guard++ < 62) {
                    if (c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m) {
                        int d = c.get(Calendar.DAY_OF_MONTH);
                        List<Ev> list = map.get(d);
                        if (list == null) { list = new ArrayList<Ev>(); map.put(d, list); }
                        list.add(ev);
                    }
                    c.add(Calendar.DAY_OF_MONTH, 1);
                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        return map;
    }

    private void showDayEvents() {
        Calendar now = Calendar.getInstance();
        boolean isToday = shownYear == now.get(Calendar.YEAR) && shownMonth == now.get(Calendar.MONTH)
                && selectedDay == now.get(Calendar.DAY_OF_MONTH);
        String head = isToday ? "Hoy" : (selectedDay + " de " + MONTHS[shownMonth].toLowerCase(ES));

        List<Ev> list = monthEvents.get(selectedDay);
        if (list == null || list.isEmpty()) {
            eventsText.setText(head + ": sin eventos");
            return;
        }
        SimpleDateFormat hf = new SimpleDateFormat(
                android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm a", ES);
        List<String> parts = new ArrayList<String>();
        for (Ev e : list) {
            parts.add(e.allDay ? e.title : hf.format(e.begin) + " " + e.title);
        }
        eventsText.setText(head + ": " + TextUtils.join("  ·  ", parts));
    }

    private void openCalendarApp() {
        long t = new GregorianCalendar(shownYear, shownMonth, selectedDay, 9, 0).getTimeInMillis();
        Uri.Builder b = CalendarContract.CONTENT_URI.buildUpon().appendPath("time");
        ContentUris.appendId(b, t);
        tryStart(new Intent(Intent.ACTION_VIEW).setData(b.build()));
    }

    // =====================================================================
    // Caja de aplicaciones
    // =====================================================================

    private List<String> getFavorites() {
        if (!prefs.getBoolean("init", false)) {
            // Primera vez: agrega navegador si existe
            List<String> def = new ArrayList<String>();
            for (ResolveInfo ri : getLaunchableApps()) {
                String p = ri.activityInfo.packageName;
                if (p.equals("com.android.chrome") || p.equals("com.android.browser")) {
                    def.add(keyOf(ri));
                    break;
                }
            }
            saveFavorites(def);
            prefs.edit().putBoolean("init", true).commit();
        }
        String raw = prefs.getString("favs", "");
        List<String> out = new ArrayList<String>();
        for (String s : raw.split("\n")) if (s.length() > 0) out.add(s);
        return out;
    }

    private void saveFavorites(List<String> favs) {
        prefs.edit().putString("favs", TextUtils.join("\n", favs)).commit();
    }

    private static String keyOf(ResolveInfo ri) {
        return ri.activityInfo.packageName + "/" + ri.activityInfo.name;
    }

    private List<ResolveInfo> getLaunchableApps() {
        final PackageManager pm = getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> all = pm.queryIntentActivities(main, 0);
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        for (ResolveInfo ri : all) {
            if (!ri.activityInfo.packageName.equals(getPackageName())) out.add(ri);
        }
        final Collator col = Collator.getInstance(ES);
        final Map<ResolveInfo, String> labels = new HashMap<ResolveInfo, String>();
        for (ResolveInfo ri : out) labels.put(ri, String.valueOf(ri.loadLabel(pm)));
        Collections.sort(out, new Comparator<ResolveInfo>() {
            @Override public int compare(ResolveInfo a, ResolveInfo b) {
                return col.compare(labels.get(a), labels.get(b));
            }
        });
        return out;
    }

    private void reloadApps() {
        PackageManager pm = getPackageManager();
        List<AppItem> items = new ArrayList<AppItem>();

        items.add(fixedTile(PKG_YOUTUBE, "YouTube", "https://m.youtube.com", 0xFFD93A2B, "Y"));
        items.add(fixedTile(PKG_NETFLIX, "Netflix", "https://www.netflix.com", 0xFF2A2A2A, "N"));

        Map<String, ResolveInfo> byKey = new HashMap<String, ResolveInfo>();
        for (ResolveInfo ri : getLaunchableApps()) byKey.put(keyOf(ri), ri);

        for (String k : getFavorites()) {
            ResolveInfo ri = byKey.get(k);
            if (ri == null) continue;   // app desinstalada
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(PKG_YOUTUBE) || pkg.equals(PKG_NETFLIX)) continue;
            items.add(new AppItem(AppItem.APP, k, String.valueOf(ri.loadLabel(pm)), ri.loadIcon(pm),
                    launchIntent(ri)));
        }

        items.add(new AppItem(AppItem.ADD, null, "Agregar", plusIcon(), null));
        items.add(new AppItem(AppItem.ALL, null, "Todas", gridIcon(), null));
        appAdapter.setItems(items);
    }

    private AppItem fixedTile(String pkg, String label, String url, int color, String letter) {
        PackageManager pm = getPackageManager();
        Intent li = pm.getLaunchIntentForPackage(pkg);
        if (li != null) {
            Drawable icon;
            try { icon = pm.getApplicationIcon(pkg); } catch (Exception e) { icon = letterIcon(letter, color); }
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return new AppItem(AppItem.FIXED, pkg, label, icon, li);
        }
        // No instalada: abre la versión web
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return new AppItem(AppItem.FIXED, pkg, label, letterIcon(letter, color), web);
    }

    private static Intent launchIntent(ResolveInfo ri) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.addCategory(Intent.CATEGORY_LAUNCHER);
        i.setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return i;
    }

    private void onTileClick(AppItem it) {
        switch (it.type) {
            case AppItem.ADD:
                showAppPicker(true);
                break;
            case AppItem.ALL:
                showAppPicker(false);
                break;
            default:
                if (!tryStart(it.intent)) Toast.makeText(this, "No se pudo abrir " + it.label, Toast.LENGTH_SHORT).show();
        }
    }

    /** addMode = true: elegir app para la caja. false: cajón con todas las apps. */
    private void showAppPicker(final boolean addMode) {
        final PackageManager pm = getPackageManager();
        final List<ResolveInfo> apps = getLaunchableApps();
        final List<AppItem> items = new ArrayList<AppItem>();
        if (!addMode) {
            items.add(new AppItem(AppItem.APP, null, "Ajustes", letterIcon("A", 0xFF4A5260),
                    new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        }
        for (ResolveInfo ri : apps) {
            items.add(new AppItem(AppItem.APP, keyOf(ri), String.valueOf(ri.loadLabel(pm)),
                    ri.loadIcon(pm), launchIntent(ri)));
        }

        GridView g = new GridView(this);
        g.setNumColumns(GridView.AUTO_FIT);
        g.setColumnWidth((int) dp(84));
        g.setStretchMode(GridView.STRETCH_SPACING_UNIFORM);
        g.setVerticalSpacing((int) dp(6));
        g.setPadding((int) dp(8), (int) dp(8), (int) dp(8), (int) dp(8));
        AppAdapter ad = new AppAdapter(this);
        ad.setItems(items);
        g.setAdapter(ad);

        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
                .setTitle(addMode ? "Elige una app para la caja" : "Todas las aplicaciones")
                .setView(g)
                .setNegativeButton("Cerrar", null)
                .create();

        g.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                AppItem it = items.get(pos);
                dlg.dismiss();
                if (addMode) {
                    List<String> favs = getFavorites();
                    if (!favs.contains(it.key)) {
                        favs.add(it.key);
                        saveFavorites(favs);
                        reloadApps();
                    }
                } else {
                    tryStart(it.intent);
                }
            }
        });
        dlg.show();
        dlg.getWindow().setLayout((int) (getResources().getDisplayMetrics().widthPixels * 0.9f),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.9f));
    }

    private void confirmRemove(final AppItem it) {
        new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
                .setTitle("Quitar " + it.label)
                .setMessage("¿Quitar de la caja? (no se desinstala)")
                .setPositiveButton("Quitar", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        List<String> favs = getFavorites();
                        favs.remove(it.key);
                        saveFavorites(favs);
                        reloadApps();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // =====================================================================
    // Utilidades
    // =====================================================================

    private boolean tryStart(Intent i) {
        if (i == null) return false;
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) return s;
        return s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }

    private Drawable letterIcon(String letter, int color) {
        int s = (int) dp(52);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        c.drawCircle(s / 2f, s / 2f, s / 2f, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextSize(s * 0.48f);
        c.drawText(letter, s / 2f, s / 2f - (p.descent() + p.ascent()) / 2f, p);
        return new BitmapDrawable(getResources(), bmp);
    }

    private Drawable plusIcon() {
        int s = (int) dp(52);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2));
        p.setColor(getResources().getColor(R.color.muted));
        c.drawCircle(s / 2f, s / 2f, s / 2f - dp(2), p);
        p.setStrokeWidth(dp(3));
        p.setStrokeCap(Paint.Cap.ROUND);
        c.drawLine(s * 0.32f, s / 2f, s * 0.68f, s / 2f, p);
        c.drawLine(s / 2f, s * 0.32f, s / 2f, s * 0.68f, p);
        return new BitmapDrawable(getResources(), bmp);
    }

    private Drawable gridIcon() {
        int s = (int) dp(52);
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(getResources().getColor(R.color.accent));
        float step = s / 4f, r = dp(4.5f);
        for (int i = 1; i <= 3; i++)
            for (int j = 1; j <= 3; j++)
                c.drawCircle(step * i, step * j, r, p);
        return new BitmapDrawable(getResources(), bmp);
    }
}
