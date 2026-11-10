package __APPID__;

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

/** Widget layar utama: ayat pagi (sebelum 11.00), siang (11.00-17.59), atau malam (mulai 18.00). */
public class AyatWidget extends AppWidgetProvider {
    public static final String PREFS = "tadabbur_widget";
    public static final String KEY = "ayat";
    private static final String[] NAMA = {"Tadabbur Pagi", "Tadabbur Siang", "Tadabbur Malam"};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            update(context, manager, id);
        }
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, AyatWidget.class));
        for (int id : ids) {
            update(context, manager, id);
        }
    }

    static void update(Context context, AppWidgetManager manager, int id) {
        Calendar cal = Calendar.getInstance();
        int jam = cal.get(Calendar.HOUR_OF_DAY);
        int slot = jam < 11 ? 0 : (jam < 18 ? 1 : 2);

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

        Intent buka = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (buka != null) {
            PendingIntent pi = PendingIntent.getActivity(context, 0, buka,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.wg_root, pi);
        }
        manager.updateAppWidget(id, v);
    }
}
