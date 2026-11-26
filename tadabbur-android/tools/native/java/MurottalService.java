package __APPID__;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

/** Layanan latar tanpa suara sendiri: menahan proses aplikasi tetap hidup dan menampilkan tombol kontrol saat murottal diputar. */
public class MurottalService extends Service {
    static final String AKSI_TOGGLE = "__APPID__.MUR_TOGGLE", AKSI_NEXT = "__APPID__.MUR_NEXT", AKSI_PREV = "__APPID__.MUR_PREV", AKSI_STOP = "__APPID__.MUR_STOP";
    private static final String CH = "murottal";
    private static final int NOTIF = 71500;
    private PowerManager.WakeLock kunci;

    static void mulai(Context c, String judul, String isi, boolean jalan) {
        Intent i = new Intent(c, MurottalService.class).putExtra("judul", judul).putExtra("isi", isi).putExtra("jalan", jalan);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Exception ignored) { }
    }

    static void hentikan(Context c) {
        try { c.stopService(new Intent(c, MurottalService.class)); } catch (Exception ignored) { }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        String judul = in == null ? "Murottal" : in.getStringExtra("judul");
        String isi = in == null ? "" : in.getStringExtra("isi");
        boolean jalan = in == null || in.getBooleanExtra("jalan", true);
        if (judul == null) judul = "Murottal";
        if (isi == null) isi = "";
        buatSaluran();
        try {
            Notification n = notifikasi(judul, isi, jalan);
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIF, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            if (kunci == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                kunci = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tadabbur:murottal");
            }
            if (!kunci.isHeld()) kunci.acquire(4 * 60 * 60 * 1000L);
        } catch (Exception ignored) { }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        try { if (kunci != null && kunci.isHeld()) kunci.release(); } catch (Exception ignored) { }
        super.onDestroy();
    }

    private void buatSaluran() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CH, "Murottal", NotificationManager.IMPORTANCE_LOW);
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    private PendingIntent aksi(String a, int kode) {
        Intent i = new Intent(this, MurottalReceiver.class).setAction(a);
        return PendingIntent.getBroadcast(this, kode, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification notifikasi(String judul, String isi, boolean jalan) {
        int ic = getResources().getIdentifier("ic_stat_tadabbur", "drawable", getPackageName());
        if (ic == 0) ic = getApplicationInfo().icon;
        PendingIntent buka = null;
        Intent li = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (li != null) buka = PendingIntent.getActivity(this, 0, li, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CH)
            .setSmallIcon(ic)
            .setContentTitle(judul)
            .setContentText(isi)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(buka)
            .addAction(0, "Sebelumnya", aksi(AKSI_PREV, 21))
            .addAction(0, jalan ? "Jeda" : "Lanjut", aksi(AKSI_TOGGLE, 22))
            .addAction(0, "Berikutnya", aksi(AKSI_NEXT, 23))
            .addAction(0, "Berhenti", aksi(AKSI_STOP, 24))
            .build();
    }
}
