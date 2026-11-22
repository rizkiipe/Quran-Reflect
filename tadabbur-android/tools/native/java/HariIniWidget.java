package __APPID__;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.provider.AlarmClock;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.graphics.Typeface;
import android.view.View;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import org.json.JSONObject;

/** Widget 4x3: tanggal Hijriah, jam besar, ayat tadabbur, jadwal sholat, dan waktu terbit/terbenam. Latar mengikuti waktu dan cuaca. */
public class HariIniWidget extends AppWidgetProvider {
    private static final String ACTION_TICK = "TADABBUR_HARIINI_TICK";
    private static final String ACTION_REFRESH = "TADABBUR_HARIINI_REFRESH";
    /** Koreksi tanggal Hijriah dalam hari (+1 / -1) bila berbeda dari kalender yang dipakai di daerahmu. */
    private static final int KOREKSI_HIJRIAH = 0;
    /** Lama waktu yang ditampilkan di baris bawah (menit): Ishraq sesudah terbit, Istiwa sebelum dzuhur, Isfirar sebelum maghrib. */
    private static final int ISHRAQ_MENIT = 45, ISTIWA_MENIT = 10, ISFIRAR_MENIT = 45;

    private static final String[] BULAN = {"Muharram", "Safar", "Rabiul Awal", "Rabiul Akhir", "Jumadil Awal", "Jumadil Akhir",
        "Rajab", "Sya\u2019ban", "Ramadhan", "Syawal", "Dzulqa\u2019dah", "Dzulhijjah"};
    /** Enam waktu yang ditampilkan: nama dan indeks di data sholat (0 imsak, 1 subuh, 2 syuruq, 3 dzuhur, 4 ashar, 5 maghrib, 6 isya). */
    private static final String[] NAMA = {"Subuh", "Terbit", "Dzuhur", "Ashar", "Maghrib", "Isya"};
    private static final int[] IDX = {1, 2, 3, 4, 5, 6};
    private static final int[] ID_S = {R.id.hi_s0, R.id.hi_s1, R.id.hi_s2, R.id.hi_s3, R.id.hi_s4, R.id.hi_s5};

    private static PendingIntent tickIntent(Context c) {
        Intent i = new Intent(c, HariIniWidget.class).setAction(ACTION_TICK);
        return PendingIntent.getBroadcast(c, 3, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Buka aplikasi langsung di menu tertentu (sholat atau hari). Tiap tujuan memakai kode permintaan sendiri. */
    private static PendingIntent bukaTab(Context c, String tab, int kode) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (i == null) return null;
        i.putExtra("tadabbur_tab", tab);
        return PendingIntent.getActivity(c, kode, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Buka aplikasi jam/alarm bawaan HP. */
    private static PendingIntent bukaJam(Context c) {
        Intent i = new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(c, 12, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static PendingIntent refreshIntent(Context c) {
        Intent i = new Intent(c, HariIniWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(c, 4, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Alarm berikutnya: waktu sholat atau pergantian suasana terdekat, paling lama satu jam (untuk menyegarkan cuaca). */
    private static void jadwalkanBerikutnya(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            long now = System.currentTimeMillis();
            long target = Math.min(WidgetTema.batasBerikutnya(c, now), now + 3600000L);
            for (int off = 0; off <= 1; off++) {
                long[] h = WidgetTema.waktu(c, now, off);
                if (h == null) continue;
                for (long x : h) {
                    if (x > now && x < target) target = x;
                }
            }
            am.set(AlarmManager.RTC, target + 5000L, tickIntent(c));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_TICK.equals(a)) {
            updateAll(c);
            WidgetTema.segarkanLalu(goAsync(), c.getApplicationContext(), false);
            return;
        }
        if (ACTION_REFRESH.equals(a)) {
            updateAll(c);
            WidgetTema.segarkanLalu(goAsync(), c.getApplicationContext(), true);
            return;
        }
        super.onReceive(c, intent);
    }

    @Override
    public void onDisabled(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(tickIntent(c));
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
        jadwalkanBerikutnya(c);
        WidgetTema.segarkanLalu(goAsync(), c.getApplicationContext(), false);
    }

    public static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, HariIniWidget.class));
        for (int id : ids) update(c, m, id);
        if (ids.length > 0) jadwalkanBerikutnya(c);
    }

    /** Konversi Masehi ke Hijriah (kalender sipil/tabular). Mengembalikan {hari, bulan 1-12, tahun}. */
    static int[] hijriah(Calendar g) {
        Calendar k = (Calendar) g.clone();
        k.add(Calendar.DAY_OF_MONTH, KOREKSI_HIJRIAH);
        int y = k.get(Calendar.YEAR), mo = k.get(Calendar.MONTH) + 1, d = k.get(Calendar.DAY_OF_MONTH);
        int a = (14 - mo) / 12, yy = y + 4800 - a, mm = mo + 12 * a - 3;
        long jd = d + (153 * mm + 2) / 5 + 365L * yy + yy / 4 - yy / 100 + yy / 400 - 32045;
        long l = jd - 1948440 + 10632;
        long n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        long j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int bln = (int) ((24 * l) / 709);
        int hr = (int) (l - (709L * bln) / 24);
        int th = (int) (30 * n + j - 30);
        return new int[] {hr, bln, th};
    }

    /** Ayat hari ini; data dan slot sama dengan widget Ayat (0 pagi, 1 siang, 2 malam menurut WidgetTema.tema). */
    private static JSONObject ayat(Context c, Calendar cal, int tema) {
        try {
            String raw = c.getSharedPreferences(AyatWidget.PREFS, Context.MODE_PRIVATE).getString(AyatWidget.KEY, null);
            if (raw == null) return null;
            JSONObject hari = new JSONObject(raw).getJSONObject("days").optJSONObject(WidgetTema.kunci(cal));
            return hari == null ? null : hari.optJSONObject(String.valueOf(tema));
        } catch (Exception e) {
            return null;
        }
    }

    /** Latar khusus widget 4x3: awan berawan/hujan diletakkan di bawah baris atas. Cerah memakai latar bawaan. */
    private static int latarHi(int cu, int tema) {
        if (cu == WidgetTema.BERAWAN) {
            return tema == 0 ? R.drawable.widget_bg_hi_berawan_pagi : (tema == 1 ? R.drawable.widget_bg_hi_berawan_siang : R.drawable.widget_bg_hi_berawan_malam);
        }
        if (cu == WidgetTema.HUJAN) {
            return tema == 0 ? R.drawable.widget_bg_hi_hujan_pagi : (tema == 1 ? R.drawable.widget_bg_hi_hujan_siang : R.drawable.widget_bg_hi_hujan_malam);
        }
        return WidgetTema.latar(cu, tema);
    }

    private static int redup(int warna) {
        return (warna & 0x00FFFFFF) | 0xB3000000;
    }

    static void update(Context c, AppWidgetManager m, int id) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        int tema = WidgetTema.tema(c, now);
        int cu = WidgetTema.cuaca(c);
        long[] h = WidgetTema.waktu(c, now, 0);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        int utama = WidgetTema.utama(cu, tema);
        int pudar = redup(utama);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_hariini);
        v.setInt(R.id.hi_root, "setBackgroundResource", latarHi(cu, tema));

        // tanggal Hijriah
        int[] hj = hijriah(cal);
        v.setTextViewText(R.id.hi_hijri, "\u263E " + hj[0] + " " + BULAN[Math.max(0, Math.min(11, hj[1] - 1))] + " " + hj[2] + " H");
        v.setTextColor(R.id.hi_hijri, pudar);

        // terbit atau terbenam matahari, paling kanan atas: simbol setengah lingkaran dengan panah, lalu jamnya
        String mat = "";
        boolean terbit = true;
        if (h != null) {
            if (now < h[2]) {
                mat = f.format(new Date(h[2]));
            } else if (now < h[5]) {
                mat = f.format(new Date(h[5]));
                terbit = false;
            } else {
                long[] b = WidgetTema.waktu(c, now, 1);
                mat = b != null ? f.format(new Date(b[2])) : "";
            }
        }
        v.setViewVisibility(R.id.hi_pill, mat.isEmpty() ? View.GONE : View.VISIBLE);
        v.setTextViewText(R.id.hi_matahari, mat);
        v.setTextColor(R.id.hi_matahari, utama);
        v.setImageViewResource(R.id.hi_ikon, terbit ? R.drawable.hi_ic_terbit : R.drawable.hi_ic_terbenam);
        v.setInt(R.id.hi_ikon, "setColorFilter", utama);
        // latar tipis di belakang jam matahari supaya tetap terbaca di atas gambar awan
        v.setInt(R.id.hi_pill, "setBackgroundResource", WidgetTema.terang(cu, tema) ? R.drawable.hi_chip_terang : R.drawable.hi_chip_gelap);

        // jam dan tanggal
        v.setTextColor(R.id.hi_jam, utama);
        v.setTextColor(R.id.hi_tgl, utama);

        // ayat
        JSONObject a = ayat(c, cal, tema);
        if (a != null) {
            v.setTextViewText(R.id.hi_arab, a.optString("a", ""));
            v.setTextViewText(R.id.hi_arti, a.optString("t", ""));
            v.setTextViewText(R.id.hi_ref, a.optString("r", ""));
        } else {
            v.setTextViewText(R.id.hi_arab, "");
            v.setTextViewText(R.id.hi_arti, "Buka aplikasi sekali agar ayat hari ini termuat.");
            v.setTextViewText(R.id.hi_ref, "");
        }
        v.setTextColor(R.id.hi_arab, utama);
        v.setTextColor(R.id.hi_arti, utama);
        v.setTextColor(R.id.hi_ref, pudar);

        // jadwal: waktu sholat yang akan datang dicetak tebal dan terang, yang lain redup
        int berikut = -1;
        if (h != null) {
            for (int i = 0; i < 6; i++) {
                if (h[IDX[i]] > now) {
                    berikut = i;
                    break;
                }
            }
            if (berikut < 0) berikut = 0;   // semua sudah lewat: berikutnya Subuh besok
        }
        for (int i = 0; i < 6; i++) {
            String t = NAMA[i] + " " + (h == null ? "--:--" : f.format(new Date(h[IDX[i]])));
            SpannableString sp = new SpannableString(t);
            if (i == berikut) sp.setSpan(new StyleSpan(Typeface.BOLD), 0, t.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            v.setTextViewText(ID_S[i], sp);
            v.setTextColor(ID_S[i], i == berikut ? utama : pudar);
        }

        // Ishraq, Istiwa, Isfirar
        String extra = "";
        if (h != null) {
            long mnt = 60000L;
            extra = "Ishraq " + f.format(new Date(h[2])) + "\u2013" + f.format(new Date(h[2] + ISHRAQ_MENIT * mnt))
                + " \u00B7 Istiwa " + f.format(new Date(h[3] - ISTIWA_MENIT * mnt)) + "\u2013" + f.format(new Date(h[3]))
                + " \u00B7 Isfirar " + f.format(new Date(h[5] - ISFIRAR_MENIT * mnt)) + "\u2013" + f.format(new Date(h[5]));
        } else {
            extra = "Buka aplikasi \u203A Sholat \u203A ambil lokasi";
        }
        v.setTextViewText(R.id.hi_extra, extra);
        v.setTextColor(R.id.hi_extra, utama);
        v.setTextColor(R.id.hi_refresh, utama);

        v.setOnClickPendingIntent(R.id.hi_refresh, refreshIntent(c));
        // jam -> aplikasi jam HP; ayat -> Tadabbur Harian; jadwal sholat -> menu Sholat; bagian lain -> beranda aplikasi
        v.setOnClickPendingIntent(R.id.hi_jam, bukaJam(c));
        PendingIntent keHari = bukaTab(c, "hari", 11), keSholat = bukaTab(c, "sholat", 10);
        if (keHari != null) v.setOnClickPendingIntent(R.id.hi_ayat, keHari);
        if (keSholat != null) {
            v.setOnClickPendingIntent(R.id.hi_baris1, keSholat);
            v.setOnClickPendingIntent(R.id.hi_baris2, keSholat);
            v.setOnClickPendingIntent(R.id.hi_extra, keSholat);
        }
        Intent buka = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
        if (buka != null) {
            PendingIntent pi = PendingIntent.getActivity(c, 0, buka, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            v.setOnClickPendingIntent(R.id.hi_root, pi);
        }
        m.updateAppWidget(id, v);
    }
}
