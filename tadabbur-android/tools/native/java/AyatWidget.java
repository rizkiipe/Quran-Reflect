package __APPID__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Widget layar utama: ayat pagi (sebelum 11.00), siang (11.00-17.59), atau malam (mulai 18.00).
 * Latar widget mengikuti suasana langit: malam gelap (00.00-04.59 dan mulai 18.00),
 * pagi saat matahari baru terbit (05.00-10.59), siang terang benderang (11.00-17.59).
 */
public class AyatWidget extends AppWidgetProvider {
    public static final String PREFS = "tadabbur_widget";
    public static final String KEY = "ayat";
    private static final String[] NAMA = {"Tadabbur Pagi", "Tadabbur Siang", "Tadabbur Malam"};
    private static final String ACTION_TICK = "TADABBUR_WIDGET_TICK";
    // urutan tema: 0 = pagi (matahari terbit), 1 = siang (terang), 2 = malam (gelap)
    private static final int[] LATAR = {R.drawable.widget_bg_pagi, R.drawable.widget_bg_siang, R.drawable.widget_bg_malam};
    private static final int[] WARNA_AKSEN = {Color.parseColor("#FFE08A"), Color.parseColor("#07325F"), Color.parseColor("#E2AE45")};
    private static final int[] WARNA_ARAB = {Color.WHITE, Color.parseColor("#06223F"), Color.WHITE};
    private static final int[] WARNA_ARTI = {Color.parseColor("#F5FFFFFF"), Color.parseColor("#0A2540"), Color.parseColor("#E6FFFFFF")};

    private static int temaUntukJam(int jam) {
        if (jam < 5) return 2;
        if (jam < 11) return 0;
        if (jam < 18) return 1;
        return 2;
    }

    /** Pasang alarm hemat daya untuk pergantian suasana berikutnya (05.00, 11.00, 18.00) agar latar tidak telat berganti. */
    private static void jadwalkanBerikutnya(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Calendar n = Calendar.getInstance();
            int[] batas = {5, 11, 18};
            Calendar target = null;
            for (int b : batas) {
                Calendar c = (Calendar) n.clone();
                c.set(Calendar.HOUR_OF_DAY, b);
                c.set(Calendar.MINUTE, 0);
                c.set(Calendar.SECOND, 5);
                c.set(Calendar.MILLISECOND, 0);
                if (c.after(n)) { target = c; break; }
            }
            if (target == null) {
                target = (Calendar) n.clone();
                target.add(Calendar.DAY_OF_YEAR, 1);
                target.set(Calendar.HOUR_OF_DAY, 5);
                target.set(Calendar.MINUTE, 0);
                target.set(Calendar.SECOND, 5);
                target.set(Calendar.MILLISECOND, 0);
            }
            Intent i = new Intent(context, AyatWidget.class).setAction(ACTION_TICK);
            PendingIntent pi = PendingIntent.getBroadcast(context, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            am.set(AlarmManager.RTC, target.getTimeInMillis(), pi);   // tidak perlu izin alarm tepat waktu
        } catch (Exception ignored) {
            // alarm gagal: widget tetap diperbarui sistem sekitar tiap 30 menit
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
            Intent i = new Intent(context, AyatWidget.class).setAction(ACTION_TICK);
            PendingIntent pi = PendingIntent.getBroadcast(context, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            if (am != null) am.cancel(pi);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            update(context, manager, id);
        }
        jadwalkanBerikutnya(context);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, AyatWidget.class));
        for (int id : ids) {
            update(context, manager, id);
        }
        if (ids.length > 0) jadwalkanBerikutnya(context);
    }

    static void update(Context context, AppWidgetManager manager, int id) {
        Calendar cal = Calendar.getInstance();
        int jam = cal.get(Calendar.HOUR_OF_DAY);
        int slot = jam < 11 ? 0 : (jam < 18 ? 1 : 2);
        int tema = temaUntukJam(jam);

        String arab = "";
        String arti = "Buka aplikasi sekali agar ayat tersimpan untuk widget.";
        String ref = "";
        try {
            String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null);
            if (raw != null) {
                JSONObject hari = new JSONObject(raw).getJSONObject("days");
                String kunci = String.format(Locale.US, "%04d-%02d-%02d",
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
                if (hari.has(kunci)) {
                    JSONObject o = hari.getJSONObject(kunci).getJSONObject(String.valueOf(slot));
                    arab = o.optString("a");
                    arti = o.optString("t");
                    ref = o.optString("r");
                } else {
                    arti = "Data ayat hari ini belum ada. Buka aplikasi untuk memperbarui.";
                }
            }
        } catch (Exception ignored) {
            // data rusak: tampilkan pesan bawaan
        }

        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_ayat);
        v.setTextViewText(R.id.wg_slot, NAMA[slot]);
        v.setTextViewText(R.id.wg_ar, arab);
        v.setTextViewText(R.id.wg_tr, arti);
        v.setTextViewText(R.id.wg_ref, ref);

        // latar dan warna teks mengikuti suasana langit (pagi / siang / malam)
        v.setInt(R.id.wg_root, "setBackgroundResource", LATAR[tema]);
        v.setTextColor(R.id.wg_slot, WARNA_AKSEN[tema]);
        v.setTextColor(R.id.wg_ar, WARNA_ARAB[tema]);
        v.setTextColor(R.id.wg_tr, WARNA_ARTI[tema]);
        v.setTextColor(R.id.wg_ref, WARNA_AKSEN[tema]);

        Intent buka = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (buka != null) {
            PendingIntent pi = PendingIntent.getActivity(context, 0, buka,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.wg_root, pi);
        }
        manager.updateAppWidget(id, v);
    }
}
