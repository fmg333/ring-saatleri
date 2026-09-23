package tr.ring.saatleri;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Calendar;
import java.util.List;

/** Ana ekranda seçilen durağın yaklaşan ringlerini gösterir; dakika başı kendini yeniler. */
public class RingWidget extends AppWidgetProvider {

    static final String PREFS = "ring_widget";
    static final String ACTION_TICK = "tr.ring.saatleri.TICK";
    private static final int ROWS = 7;

    private static final int[] ROW = {R.id.row1, R.id.row2, R.id.row3, R.id.row4, R.id.row5, R.id.row6, R.id.row7};
    private static final int[] BADGE = {R.id.badge1, R.id.badge2, R.id.badge3, R.id.badge4, R.id.badge5, R.id.badge6, R.id.badge7};
    private static final int[] DIR = {R.id.dir1, R.id.dir2, R.id.dir3, R.id.dir4, R.id.dir5, R.id.dir6, R.id.dir7};
    private static final int[] CLOCK = {R.id.clock1, R.id.clock2, R.id.clock3, R.id.clock4, R.id.clock5, R.id.clock6, R.id.clock7};
    private static final int[] MIN = {R.id.min1, R.id.min2, R.id.min3, R.id.min4, R.id.min5, R.id.min6, R.id.min7};

    /* ---------- ayarlar ---------- */

    static void saveStop(Context c, int widgetId, String stop) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString("stop_" + widgetId, stop).apply();
    }

    static String stopOf(Context c, int widgetId) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return p.getString("stop_" + widgetId, null);
    }

    private static void clearStop(Context c, int widgetId) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().remove("stop_" + widgetId).apply();
    }

    /* ---------- çizim ---------- */

    private static int badgeRes(String ring) {
        if (ring == null) {
            return R.drawable.badge_tk;     // ek servis (TEK / FM)
        }
        if ("2".equals(ring)) {
            return R.drawable.badge_r2;
        }
        if ("3".equals(ring)) {
            return R.drawable.badge_r3;
        }
        return R.drawable.badge_r1;
    }

    private static String minuteText(int diff) {
        if (diff <= 0) {
            return "şimdi";
        }
        if (diff < 60) {
            return diff + " dk";
        }
        int h = diff / 60;
        int r = diff % 60;
        return r == 0 ? h + " sa" : h + " sa " + r;
    }

    /** Dar widget'ta saat sütunu gizlenir, yön kısaltılır. */
    private static boolean isWide(AppWidgetManager m, int widgetId) {
        try {
            Bundle o = m.getAppWidgetOptions(widgetId);
            return o != null && o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) >= 220;
        } catch (Exception e) {
            return false;
        }
    }

    /** Widget'ın boyuna kaç sefer satırı sığıyorsa o kadarını gösterir. */
    private static int rowCount(AppWidgetManager m, int widgetId) {
        int h = 0;
        try {
            Bundle o = m.getAppWidgetOptions(widgetId);
            if (o != null) {
                h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            }
        } catch (Exception e) {
            h = 0;
        }
        if (h <= 0) {
            return ROWS;
        }
        int n = (h - 48) / 24;              // başlık + iç boşluk ~48dp, her sefer satırı ~24dp
        return Math.max(1, Math.min(ROWS, n));
    }

    /** Dar widget'ta varış listesini ilk birkaç durakla sınırla. */
    private static String shortDest(String s, int n) {
        int idx = -1;
        for (int k = 0; k < n; k++) {
            idx = s.indexOf(',', idx + 1);
            if (idx < 0) {
                return s;
            }
        }
        return s.substring(0, idx) + "…";
    }

    static void update(Context c, AppWidgetManager m, int widgetId) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.ring_widget);
        String stop = stopOf(c, widgetId);
        boolean wide = isWide(m, widgetId);
        int rows = rowCount(m, widgetId);

        if (stop == null) {
            v.setTextViewText(R.id.stop, "Durak seçilmedi");
            v.setTextViewText(R.id.day, "");
            v.setViewVisibility(R.id.empty, View.VISIBLE);
            v.setTextViewText(R.id.empty, "Widget'a dokunup uygulamadan devam et.");
            for (int i = 0; i < ROWS; i++) {
                v.setViewVisibility(ROW[i], View.GONE);
            }
            v.setOnClickPendingIntent(R.id.root, openApp(c, widgetId, null));
            m.updateAppWidget(widgetId, v);
            return;
        }

        int now = Schedule.nowMinutes();
        List<Schedule.Dep> deps = Schedule.next(c, stop, rows);

        v.setTextViewText(R.id.stop, stop);
        v.setTextViewText(R.id.day, Schedule.dayLabel(Calendar.getInstance()));

        for (int i = 0; i < ROWS; i++) {
            if (i < rows && i < deps.size()) {
                Schedule.Dep d = deps.get(i);
                int diff = d.time - now + (d.tomorrow ? 24 * 60 : 0);
                v.setViewVisibility(ROW[i], View.VISIBLE);
                v.setTextViewText(BADGE[i], d.isExtra() ? d.label : "R" + d.ring);
                v.setInt(BADGE[i], "setBackgroundResource", badgeRes(d.ring));
                String dirText;
                if (d.isExtra()) {
                    // "G1 yönü" gibi hazır gelen metin olduğu gibi, durak listesi ise kısaltılır
                    dirText = d.dir.endsWith(" yönü") ? d.dir : "→ " + (wide ? d.dir : shortDest(d.dir, 2));
                } else {
                    dirText = wide ? d.dir + " yönü" : "→ " + d.dir;
                }
                if (!wide && d.tomorrow) {
                    dirText = dirText + " (yarın)";   // dar widget'ta saat sütunu yok
                }
                v.setTextViewText(DIR[i], dirText);
                v.setViewVisibility(CLOCK[i], wide ? View.VISIBLE : View.GONE);
                v.setTextViewText(CLOCK[i], d.tomorrow ? "yarın " + Schedule.hhmm(d.time) : Schedule.hhmm(d.time));
                v.setTextViewText(MIN[i], minuteText(diff));
            } else {
                v.setViewVisibility(ROW[i], View.GONE);
            }
        }

        if (deps.isEmpty()) {
            v.setViewVisibility(R.id.empty, View.VISIBLE);
            v.setTextViewText(R.id.empty, "Sefer bulunamadı.");
        } else {
            v.setViewVisibility(R.id.empty, View.GONE);
        }

        v.setOnClickPendingIntent(R.id.root, openApp(c, widgetId, stop));
        m.updateAppWidget(widgetId, v);
    }

    /** Widget'a dokununca uygulamayı, varsa o durak seçili olarak açar. */
    private static PendingIntent openApp(Context c, int widgetId, String stop) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (stop != null) {
            i.putExtra(MainActivity.EXTRA_STOP, stop);
            // her widget'ın PendingIntent'i farklı olsun diye veri alanı da ayrıştırılır
            i.setData(Uri.parse("ring://stop/" + Uri.encode(stop) + "/" + widgetId));
        }
        return PendingIntent.getActivity(c, widgetId, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, RingWidget.class));
        for (int i = 0; i < ids.length; i++) {
            update(c, m, ids[i]);
        }
    }

    /* ---------- dakika başı yenileme ---------- */

    private static PendingIntent tick(Context c) {
        Intent i = new Intent(c, RingWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long next = now + (60000 - now % 60000);   // sonraki dakika başı
        am.set(AlarmManager.RTC, next, tick(c));   // yaklaşık; ekran kapalıyken sistem erteleyebilir
    }

    private static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            am.cancel(tick(c));
        }
    }

    /* ---------- yaşam döngüsü ---------- */

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] widgetIds) {
        for (int i = 0; i < widgetIds.length; i++) {
            update(c, m, widgetIds[i]);
        }
        schedule(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int widgetId, Bundle newOptions) {
        update(c, m, widgetId);   // yeniden boyutlandırılınca düzeni tazele
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (ACTION_TICK.equals(intent.getAction())) {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, RingWidget.class));
            if (ids.length == 0) {
                cancel(c);
                return;
            }
            for (int i = 0; i < ids.length; i++) {
                update(c, m, ids[i]);
            }
            schedule(c);
        }
    }

    @Override
    public void onEnabled(Context c) {
        schedule(c);
    }

    @Override
    public void onDisabled(Context c) {
        cancel(c);
    }

    @Override
    public void onDeleted(Context c, int[] widgetIds) {
        for (int i = 0; i < widgetIds.length; i++) {
            clearStop(c, widgetIds[i]);
        }
    }
}
