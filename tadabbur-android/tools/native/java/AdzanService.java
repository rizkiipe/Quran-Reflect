package __APPID__;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

/** Memutar adzan lewat jalur volume ALARM, sehingga tetap berbunyi saat HP mode silent atau getar. */
public class AdzanService extends Service {
    static final String AKSI_STOP = "__APPID__.ADZAN_STOP";
    private static final String CH = "adzan_alarm";
    private static final String CH_INFO = "adzan_info";
    private static final int NOTIF = 71000;

    private MediaPlayer mp;
    private AudioManager audio;
    private AudioManager.OnAudioFocusChangeListener fokus = new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int perubahan) { }
    };
    private PowerManager.WakeLock kunci;
    private String judulAkhir, isiAkhir;

    static void mulai(Context c, String file, String judul, String isi) {
        Intent s = new Intent(c, AdzanService.class).putExtra("file", file).putExtra("judul", judul).putExtra("isi", isi);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception ignored) {
        }
    }

    static void hentikan(Context c) {
        try { c.stopService(new Intent(c, AdzanService.class)); } catch (Exception ignored) { }
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent in, int flags, int startId) {
        if (in != null && AKSI_STOP.equals(in.getAction())) {
            selesai(false);
            return START_NOT_STICKY;
        }
        String file = in == null ? "adzan" : in.getStringExtra("file");
        if (!"adzan_subuh".equals(file)) file = "adzan";
        String judul = in == null ? null : in.getStringExtra("judul");
        String isi = in == null ? null : in.getStringExtra("isi");
        if (judul == null) judul = "Waktu sholat";
        if (isi == null) isi = "";
        berhentiPemutar();
        buatSaluran();
        judulAkhir = judul;
        isiAkhir = isi;
        try {
            Notification n = notifikasi(judul, isi, true);
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
            else startForeground(NOTIF, n);
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        mulaiPutar(file);
        return START_NOT_STICKY;
    }

    private void mulaiPutar(String file) {
        try {
            int res = getResources().getIdentifier(file, "raw", getPackageName());
            if (res == 0) {
                selesai(true);
                return;
            }
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            kunci = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tadabbur:adzan");
            kunci.acquire(10 * 60 * 1000L);
            audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            audio.requestAudioFocus(fokus, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build());
            AssetFileDescriptor afd = getResources().openRawResourceFd(res);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override public void onCompletion(MediaPlayer m) { selesai(true); }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override public boolean onError(MediaPlayer m, int what, int extra) { selesai(true); return true; }
            });
            mp.prepare();
            mp.start();
        } catch (Exception e) {
            selesai(true);
        }
    }

    private void berhentiPemutar() {
        try {
            if (mp != null) {
                try { mp.stop(); } catch (Exception ignored) { }
                mp.release();
            }
        } catch (Exception ignored) { }
        mp = null;
        try { if (audio != null) audio.abandonAudioFocus(fokus); } catch (Exception ignored) { }
        try { if (kunci != null && kunci.isHeld()) kunci.release(); } catch (Exception ignored) { }
        kunci = null;
    }

    /** Menutup layanan. Bila adzan selesai sendiri (info=true), tinggalkan notifikasi biasa tanpa suara sebagai catatan. */
    private void selesai(boolean info) {
        berhentiPemutar();
        try { stopForeground(true); } catch (Exception ignored) { }
        if (info && judulAkhir != null) {
            try {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.notify(NOTIF + 1, notifikasiInfo(judulAkhir, isiAkhir));
            } catch (Exception ignored) { }
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        berhentiPemutar();
        super.onDestroy();
    }

    private void buatSaluran() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel a = new NotificationChannel(CH, "Adzan (volume alarm)", NotificationManager.IMPORTANCE_HIGH);
        a.setDescription("Adzan diputar lewat volume alarm sehingga tetap berbunyi saat HP silent atau getar");
        a.setSound(null, null);
        a.enableVibration(false);
        a.setShowBadge(false);
        nm.createNotificationChannel(a);
        NotificationChannel b = new NotificationChannel(CH_INFO, "Catatan adzan", NotificationManager.IMPORTANCE_LOW);
        b.setSound(null, null);
        b.enableVibration(false);
        nm.createNotificationChannel(b);
    }

    private int ikon() {
        int ic = getResources().getIdentifier("ic_stat_tadabbur", "drawable", getPackageName());
        return ic != 0 ? ic : getApplicationInfo().icon;
    }

    private PendingIntent bukaApp() {
        Intent li = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (li == null) return null;
        return PendingIntent.getActivity(this, 0, li, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification notifikasi(String judul, String isi, boolean berlangsung) {
        PendingIntent henti = PendingIntent.getService(this, 1, new Intent(this, AdzanService.class).setAction(AKSI_STOP),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CH)
            .setSmallIcon(ikon())
            .setContentTitle(judul)
            .setContentText(isi)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(berlangsung)
            .setOnlyAlertOnce(true)
            .setContentIntent(bukaApp());
        if (berlangsung) b.addAction(0, "Hentikan", henti);
        return b.build();
    }

    private Notification notifikasiInfo(String judul, String isi) {
        return new NotificationCompat.Builder(this, CH_INFO)
            .setSmallIcon(ikon())
            .setContentTitle(judul)
            .setContentText(isi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(bukaApp())
            .build();
    }
}
