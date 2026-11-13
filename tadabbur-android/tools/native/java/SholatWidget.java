package __APPID__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Widget jadwal sholat: waktu berikutnya, hitung mundur, dan tujuh waktu hari ini. Latar mengikuti waktu dan cuaca. */
public class SholatWidget extends AppWidgetProvider {
    private static final String ACTION_TICK = "TADABBUR_SHOLAT_TICK";
    private static final String[] NAMA = {"Imsak", "Subuh", "Syuruq", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    private static final int[] ID_N = {R.id.sh_n0, R.id.sh_n1, R.id.sh_n2, R.id.sh_n3, R.id.sh_n4, R.id.sh_n5, R.id.sh_n6};
    private static final int[] ID_T = {R.id.sh_t0, R.id.sh_t1, R.id.sh_t2, R.id.sh_t3, R.id.sh_t4, R.id.sh_t5, R.id.sh_t6};

    private static PendingIntent tickIntent(Context context) {
        Intent i = new Intent(context, SholatWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, 2, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Alarm berikutnya: waktu sholat terdekat atau pergantian suasana, mana yang lebih dulu. */
    private static void jadwalkanBerikutnya(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long now = System.currentTimeMillis();
            long target = WidgetTema.batasBerikutnya(context, now);
            for (int off = 0; off <= 1; off++) {
                long[] h = WidgetTema.waktu(context, now, off);
                if (h == null) continue;
                for (long x : h) {
                    if (x > now && x < target) target = x;
                }
            }
            am.set(AlarmManager.RTC, target + 5000L, tickIntent(context));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_TICK.equals(intent.getAction())) {
            updateAll(context);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onDisabled(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(tickIntent(context));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            update(context, manager, id);
        }
        jadwalkanBerikutnya(context);
        WidgetTema.segarkanLalu(goAsync(), context.getApplicationContext());
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, SholatWidget.class));
        for (int id : ids) {
            update(context, manager, id);
        }
        if (ids.length > 0) jadwalkanBerikutnya(context);
    }

    private static String sisa(long ms) {
        long m = Math.max(0, Math.round(ms / 60000.0));
        long j = m / 60;
        return "dalam " + (j > 0 ? j + " j " : "") + (m % 60) + " m";
    }

    static void update(Context context, AppWidgetManager manager, int id) {
        long now = System.currentTimeMillis();
        int tema = WidgetTema.tema(context, now);
        int cu = WidgetTema.cuaca(context);
        long[] h = WidgetTema.waktu(context, now, 0);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);

        int nx = -1;
        if (h != null) {
            for (int i = 0; i < 7; i++) {
                if (h[i] > now) {
                    nx = i;
                    break;
                }
            }
        }
        String next;
        if (h == null) {
            next = "Buka aplikasi, tab Sholat, lalu ambil lokasi.";
        } else if (nx >= 0) {
            next = NAMA[nx] + " " + f.format(new Date(h[nx])) + "  \u00B7  " + sisa(h[nx] - now);
        } else {
            long[] b = WidgetTema.waktu(context, now, 1);
            next = b != null ? "Imsak besok " + f.format(new Date(b[0])) : "Semua waktu hari ini sudah lewat";
        }

        int aksen = WidgetTema.aksen(cu, tema);
        int utama = WidgetTema.utama(cu, tema);
        int kedua = WidgetTema.kedua(cu, tema);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_sholat);
        String nama = WidgetTema.namaLokasi(context);
        v.setTextViewText(R.id.sh_judul, "Jadwal Sholat" + (nama.isEmpty() ? "" : "  \u00B7  " + nama));
        v.setTextViewText(R.id.sh_cuaca, WidgetTema.cuacaTeks(context));
        v.setTextViewText(R.id.sh_next, next);
        v.setInt(R.id.sh_root, "setBackgroundResource", WidgetTema.latar(cu, tema));
        v.setTextColor(R.id.sh_judul, aksen);
        v.setTextColor(R.id.sh_cuaca, kedua);
        v.setTextColor(R.id.sh_next, utama);
        for (int i = 0; i < 7; i++) {
            boolean sorot = i == nx;
            v.setTextViewText(ID_N[i], NAMA[i]);
            v.setTextViewText(ID_T[i], h == null ? "--:--" : f.format(new Date(h[i])));
            v.setTextColor(ID_N[i], sorot ? aksen : kedua);
            v.setTextColor(ID_T[i], sorot ? aksen : utama);
        }
        Intent buka = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (buka != null) {
            PendingIntent pi = PendingIntent.getActivity(context, 0, buka,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.sh_root, pi);
        }
        manager.updateAppWidget(id, v);
    }
}
