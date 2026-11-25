package __APPID__;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Memeriksa izin dan pengaturan HP yang memengaruhi alarm adzan dan notifikasi, serta membuka halaman pengaturan terkait. */
@CapacitorPlugin(name = "TadabburDiag")
public class DiagPlugin extends Plugin {
    @PluginMethod
    public void status(PluginCall call) {
        Context c = getContext();
        JSObject r = new JSObject();
        r.put("sdk", Build.VERSION.SDK_INT);
        r.put("pabrikan", Build.MANUFACTURER);
        r.put("model", Build.MODEL);
        boolean exact = true;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
                exact = am != null && am.canScheduleExactAlarms();
            }
        } catch (Exception ignored) { }
        r.put("exact", exact);
        boolean baterai = true;
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
                baterai = pm != null && pm.isIgnoringBatteryOptimizations(c.getPackageName());
            }
        } catch (Exception ignored) { }
        r.put("baterai", baterai);
        try {
            AudioManager a = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            r.put("volAlarm", a.getStreamVolume(AudioManager.STREAM_ALARM));
            r.put("volAlarmMax", a.getStreamMaxVolume(AudioManager.STREAM_ALARM));
            r.put("ringer", a.getRingerMode());   // 0 silent, 1 getar, 2 normal
        } catch (Exception ignored) { }
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            int f = Build.VERSION.SDK_INT >= 23 ? nm.getCurrentInterruptionFilter() : 1;   // 1 semua, 2 prioritas, 3 hening total, 4 hanya alarm
            boolean alarmOk = true;
            if (f == 3) {
                alarmOk = false;
            } else if (f == 2 && Build.VERSION.SDK_INT >= 28) {
                alarmOk = (nm.getNotificationPolicy().priorityCategories & NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) != 0;
            }
            r.put("dnd", f);
            r.put("dndAlarmOk", alarmOk);
        } catch (Exception ignored) { }
        call.resolve(r);
    }

    /** jenis: notif, alarm, baterai, suara, aplikasi. */
    @PluginMethod
    public void buka(PluginCall call) {
        String jenis = call.getString("jenis", "aplikasi");
        String pkg = getContext().getPackageName();
        Intent i;
        try {
            if ("notif".equals(jenis) && Build.VERSION.SDK_INT >= 26) {
                i = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg);
            } else if ("alarm".equals(jenis) && Build.VERSION.SDK_INT >= 31) {
                i = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + pkg));
            } else if ("baterai".equals(jenis) && Build.VERSION.SDK_INT >= 23) {
                i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            } else if ("suara".equals(jenis)) {
                i = new Intent(Settings.ACTION_SOUND_SETTINGS);
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            try {
                Intent j = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(j);
                call.resolve();
            } catch (Exception e2) {
                call.reject("Tidak bisa membuka pengaturan: " + e2.getMessage());
            }
        }
    }

    /** Atur volume alarm ke persen (0-100). */
    @PluginMethod
    public void aturVolumeAlarm(PluginCall call) {
        try {
            int persen = Math.max(0, Math.min(100, call.getInt("persen", 70)));
            AudioManager a = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            int mx = a.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            a.setStreamVolume(AudioManager.STREAM_ALARM, Math.round(mx * persen / 100f), 0);
            call.resolve();
        } catch (Exception e) {
            call.reject("Gagal mengatur volume alarm: " + e.getMessage());
        }
    }
}
