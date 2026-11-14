package __APPID__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;

/** Menyimpan dan memasang alarm adzan (tepat waktu, tetap jalan saat Doze). Item: {id, at(ms), file, judul, isi}. */
class AdzanAlarm {
    static final String PREFS = "tadabbur_adzan";
    static final String AKSI = "__APPID__.ADZAN_BUNYI";

    private static PendingIntent buat(Context c, int id, JSONObject o, int flags) throws Exception {
        Intent i = new Intent(c, AdzanReceiver.class).setAction(AKSI);
        if (o != null) {
            i.putExtra("file", o.optString("file", "adzan"));
            i.putExtra("judul", o.optString("judul", "Waktu sholat"));
            i.putExtra("isi", o.optString("isi", ""));
        }
        return PendingIntent.getBroadcast(c, id, i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Batalkan alarm yang tersimpan lalu ganti dengan daftar baru. */
    static synchronized void simpanDanAtur(Context c, String json) {
        batalSemua(c);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("items", json).apply();
        atur(c);
    }

    static void batalSemua(Context c) {
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", null);
            if (raw == null) return;
            JSONArray a = new JSONArray(raw);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            for (int k = 0; k < a.length(); k++) {
                int id = a.getJSONObject(k).getInt("id");
                PendingIntent p = PendingIntent.getBroadcast(c, id, new Intent(c, AdzanReceiver.class).setAction(AKSI),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (p != null) {
                    am.cancel(p);
                    p.cancel();
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Pasang semua alarm masa depan dari data tersimpan (juga dipakai setelah HP restart). */
    static void atur(Context c) {
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("items", null);
            if (raw == null) return;
            JSONArray a = new JSONArray(raw);
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            long now = System.currentTimeMillis();
            for (int k = 0; k < a.length(); k++) {
                JSONObject o = a.getJSONObject(k);
                long at = o.getLong("at");
                if (at <= now) continue;
                PendingIntent p = buat(c, o.getInt("id"), o, PendingIntent.FLAG_UPDATE_CURRENT);
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                } else if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, at, p);
                }
            }
        } catch (Exception ignored) {
        }
    }
}
