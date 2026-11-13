package __APPID__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Widget ayat: ayat pagi, siang, atau malam. Latar mengikuti waktu (pagi mulai sebelum syuruq,
 * siang menjelang dzuhur, malam menjelang maghrib) dan cuaca (cerah, berawan, hujan).
 */
public class AyatWidget extends AppWidgetProvider {
    public static final String PREFS = "tadabbur_widget";
    public static final String KEY = "ayat";
    private static final String[] NAMA = {"Tadabbur Pagi", "Tadabbur Siang", "Tadabbur Malam"};
    private static final String ACTION_TICK = "TADABBUR_WIDGET_TICK";

    private static PendingIntent tickIntent(Context context) {
        Intent i = new Intent(context, AyatWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(context, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Alarm hemat daya untuk pergantian suasana berikutnya agar latar tidak telat berganti. */
    private static void jadwalkanBerikutnya(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long target = WidgetTema.batasBerikutnya(context, System.currentTimeMillis()) + 5000L;
            am.set(AlarmManager.RTC, target, tickIntent(context));   // tidak perlu izin alarm tepat waktu
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
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, AyatWidget.class));
        for (int id : ids) {
            update(context, manager, id);
        }
        if (ids.length > 0) jadwalkanBerikutnya(context);
    }

    static void update(Context context, AppWidgetManager manager, int id) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        int tema = WidgetTema.tema(context, now);   // 0 pagi, 1 siang, 2 malam (juga menentukan ayat yang tampil)
        int cu = WidgetTema.cuaca(context);

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
                    JSONObject o = hari.getJSONObject(kunci).getJSONObject(String.valueOf(tema));
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

        String ct = WidgetTema.cuacaTeks(context);
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_ayat);
        v.setTextViewText(R.id.wg_slot, NAMA[tema] + (ct.isEmpty() ? "" : "  \u00B7  " + ct));
        v.setTextViewText(R.id.wg_ar, arab);
        v.setTextViewText(R.id.wg_tr, arti);
        v.setTextViewText(R.id.wg_ref, ref);

        v.setInt(R.id.wg_root, "setBackgroundResource", WidgetTema.latar(cu, tema));
        v.setTextColor(R.id.wg_slot, WidgetTema.aksen(cu, tema));
        v.setTextColor(R.id.wg_ar, WidgetTema.utama(cu, tema));
        v.setTextColor(R.id.wg_tr, WidgetTema.kedua(cu, tema));
        v.setTextColor(R.id.wg_ref, WidgetTema.aksen(cu, tema));

        Intent buka = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (buka != null) {
            PendingIntent pi = PendingIntent.getActivity(context, 0, buka,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.wg_root, pi);
        }
        manager.updateAppWidget(id, v);
    }
}
