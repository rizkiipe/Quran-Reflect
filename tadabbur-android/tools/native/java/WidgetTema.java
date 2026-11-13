package __APPID__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Logika bersama kedua widget: suasana (pagi/siang/malam, mengikuti waktu syuruq-dzuhur-maghrib) dan cuaca (cerah/berawan/hujan). */
public class WidgetTema {
    static final String PREFS = "tadabbur_widget";
    static final int CERAH = 0, BERAWAN = 1, HUJAN = 2;
    private static final int[][] LATAR = {
        {R.drawable.widget_bg_pagi, R.drawable.widget_bg_siang, R.drawable.widget_bg_malam},
        {R.drawable.widget_bg_berawan_pagi, R.drawable.widget_bg_berawan_siang, R.drawable.widget_bg_berawan_malam},
        {R.drawable.widget_bg_hujan_pagi, R.drawable.widget_bg_hujan_siang, R.drawable.widget_bg_hujan_malam}
    };

    static String kunci(Calendar c) {
        return String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /** Tujuh waktu (imsak, subuh, syuruq, dzuhur, ashar, maghrib, isya) dalam epoch ms untuk hari ke-offset, atau null bila belum ada data. */
    static long[] waktu(Context c, long now, int offset) {
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sholat", null);
            if (raw == null) return null;
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            cal.add(Calendar.DAY_OF_YEAR, offset);
            JSONArray a = new JSONObject(raw).getJSONObject("days").optJSONArray(kunci(cal));
            if (a == null || a.length() < 7) return null;
            long[] r = new long[7];
            for (int i = 0; i < 7; i++) r[i] = a.getLong(i);
            return r;
        } catch (Exception e) {
            return null;
        }
    }

    static String namaLokasi(Context c) {
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sholat", null);
            return raw == null ? "" : new JSONObject(raw).optString("nama", "");
        } catch (Exception e) {
            return "";
        }
    }

    /** 0 = pagi, 1 = siang, 2 = malam. Pagi mulai 30 menit sebelum syuruq, siang 30 menit sebelum dzuhur, malam 20 menit sebelum maghrib. */
    static int tema(Context c, long now) {
        long[] h = waktu(c, now, 0);
        if (h != null) {
            if (now >= h[2] - 30 * 60000L && now < h[3] - 30 * 60000L) return 0;
            if (now >= h[3] - 30 * 60000L && now < h[5] - 20 * 60000L) return 1;
            return 2;
        }
        Calendar n = Calendar.getInstance();
        n.setTimeInMillis(now);
        int jam = n.get(Calendar.HOUR_OF_DAY);
        if (jam < 5) return 2;
        if (jam < 11) return 0;
        if (jam < 18) return 1;
        return 2;
    }

    /** Saat pergantian suasana berikutnya. */
    static long batasBerikutnya(Context c, long now) {
        long terbaik = Long.MAX_VALUE;
        for (int off = 0; off <= 1; off++) {
            long[] h = waktu(c, now, off);
            if (h == null) continue;
            long[] b = {h[2] - 30 * 60000L, h[3] - 30 * 60000L, h[5] - 20 * 60000L};
            for (long x : b) {
                if (x > now && x < terbaik) terbaik = x;
            }
        }
        if (terbaik != Long.MAX_VALUE) return terbaik;
        Calendar n = Calendar.getInstance();
        n.setTimeInMillis(now);
        int[] jam = {5, 11, 18};
        for (int j : jam) {
            Calendar t = (Calendar) n.clone();
            t.set(Calendar.HOUR_OF_DAY, j);
            t.set(Calendar.MINUTE, 0);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            if (t.getTimeInMillis() > now) return t.getTimeInMillis();
        }
        Calendar t = (Calendar) n.clone();
        t.add(Calendar.DAY_OF_YEAR, 1);
        t.set(Calendar.HOUR_OF_DAY, 5);
        t.set(Calendar.MINUTE, 0);
        t.set(Calendar.SECOND, 0);
        t.set(Calendar.MILLISECOND, 0);
        return t.getTimeInMillis();
    }

    private static JSONObject dataCuaca(Context c) {
        try {
            String raw = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cuaca", null);
            if (raw == null) return null;
            JSONObject o = new JSONObject(raw);
            if (System.currentTimeMillis() - o.optLong("t") > 6 * 3600000L) return null;   // data cuaca lebih dari 6 jam dianggap usang
            return o;
        } catch (Exception e) {
            return null;
        }
    }

    static int cuaca(Context c) {
        JSONObject o = dataCuaca(c);
        if (o == null) return CERAH;
        int k = o.optInt("kode", 0);
        if (k <= 1) return CERAH;
        if (k <= 3 || k == 45 || k == 48) return BERAWAN;
        return HUJAN;
    }

    static String cuacaTeks(Context c) {
        JSONObject o = dataCuaca(c);
        if (o == null) return "";
        int cu = cuaca(c);
        String ikon = cu == CERAH ? "\u2600\uFE0F" : (cu == BERAWAN ? "\u2601\uFE0F" : "\uD83C\uDF27\uFE0F");
        String nama = cu == CERAH ? "Cerah" : (cu == BERAWAN ? "Berawan" : "Hujan");
        return ikon + " " + nama + " " + Math.round(o.optDouble("suhu", 0)) + "\u00B0";
    }

    static int latar(int cuaca, int tema) {
        return LATAR[cuaca][tema];
    }

    static boolean terang(int cuaca, int tema) {
        return tema == 1 && cuaca != HUJAN;
    }

    static int aksen(int cuaca, int tema) {
        if (terang(cuaca, tema)) return Color.parseColor("#07325F");
        if (tema == 0) return Color.parseColor("#FFE08A");
        return Color.parseColor("#E2AE45");
    }

    static int utama(int cuaca, int tema) {
        return terang(cuaca, tema) ? Color.parseColor("#06223F") : Color.WHITE;
    }

    static int kedua(int cuaca, int tema) {
        return terang(cuaca, tema) ? Color.parseColor("#0A2540") : Color.parseColor("#E6FFFFFF");
    }

    /** Ambil cuaca terbaru dari Open-Meteo (tanpa kunci API) bila data lebih tua dari 45 menit. Panggil dari thread latar belakang. */
    static boolean segarkanCuaca(Context c) {
        try {
            SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString("sholat", null);
            if (raw == null) return false;
            JSONObject s = new JSONObject(raw);
            if (!s.has("la") || !s.has("lo")) return false;
            String lama = sp.getString("cuaca", null);
            if (lama != null && System.currentTimeMillis() - new JSONObject(lama).optLong("t") < 45 * 60000L) return false;
            String u = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&timezone=auto",
                s.getDouble("la"), s.getDouble("lo"));
            HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
            h.setConnectTimeout(8000);
            h.setReadTimeout(8000);
            if (h.getResponseCode() != 200) return false;
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(h.getInputStream(), "UTF-8"));
            String ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            br.close();
            JSONObject cur = new JSONObject(sb.toString()).getJSONObject("current");
            JSONObject o = new JSONObject();
            o.put("kode", cur.optInt("weather_code", 0));
            o.put("suhu", cur.optDouble("temperature_2m", 0));
            o.put("t", System.currentTimeMillis());
            sp.edit().putString("cuaca", o.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Segarkan cuaca di thread terpisah lalu perbarui kedua widget. pr boleh null. */
    static void segarkanLalu(final BroadcastReceiver.PendingResult pr, final Context app) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (segarkanCuaca(app)) {
                        AyatWidget.updateAll(app);
                        SholatWidget.updateAll(app);
                    }
                } catch (Exception ignored) {
                    // gagal: widget memakai data cuaca terakhir
                } finally {
                    if (pr != null) pr.finish();
                }
            }
        }).start();
    }
}
