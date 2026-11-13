package __APPID__;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Jembatan JavaScript ke widget: simpan data ayat, segarkan widget, dan minta pemasangan widget. */
@CapacitorPlugin(name = "TadabburWidget")
public class TadabburWidgetPlugin extends Plugin {

    @PluginMethod
    public void save(PluginCall call) {
        String json = call.getString("json");
        if (json == null || json.isEmpty()) {
            call.reject("Data ayat kosong");
            return;
        }
        Context c = getContext();
        c.getSharedPreferences(AyatWidget.PREFS, Context.MODE_PRIVATE)
            .edit().putString(AyatWidget.KEY, json).apply();
        AyatWidget.updateAll(c);
        call.resolve();
    }

    /** Simpan jadwal sholat 7 hari (dan lokasi) untuk widget, lalu segarkan widget dan ambil cuaca di latar belakang. */
    @PluginMethod
    public void saveSholat(PluginCall call) {
        String json = call.getString("json");
        if (json == null || json.isEmpty()) {
            call.reject("Data jadwal kosong");
            return;
        }
        Context c = getContext();
        c.getSharedPreferences(AyatWidget.PREFS, Context.MODE_PRIVATE)
            .edit().putString("sholat", json).apply();
        SholatWidget.updateAll(c);
        AyatWidget.updateAll(c);
        WidgetTema.segarkanLalu(null, c.getApplicationContext());
        call.resolve();
    }

    @PluginMethod
    public void refresh(PluginCall call) {
        AyatWidget.updateAll(getContext());
        call.resolve();
    }

    @PluginMethod
    public void pin(PluginCall call) {
        Context c = getContext();
        boolean ok = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            if (m.isRequestPinAppWidgetSupported()) {
                ok = m.requestPinAppWidget(new ComponentName(c, AyatWidget.class), null, null);
            }
        }
        JSObject r = new JSObject();
        r.put("ok", ok);
        call.resolve(r);
    }

    private static volatile int dlPct = 0;

    /** Kemajuan unduhan pembaruan (0-100), dibaca JavaScript secara berkala. */
    @PluginMethod
    public void dlProgress(PluginCall call) {
        JSObject r = new JSObject();
        r.put("pct", dlPct);
        call.resolve(r);
    }

    /** Unduh APK pembaruan ke cache aplikasi lalu buka layar pemasangan Android (pengguna tetap menekan Pasang). */
    @PluginMethod
    public void installApk(final PluginCall call) {
        final String url = call.getString("url");
        if (url == null || !url.startsWith("https://")) {
            call.reject("Alamat unduhan tidak valid");
            return;
        }
        final Context c = getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !c.getPackageManager().canRequestPackageInstalls()) {
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + c.getPackageName()));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(s);
            call.reject("Izinkan pemasangan dari aplikasi ini terlebih dahulu", "IZIN");
            return;
        }
        dlPct = 0;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File dir = new File(c.getCacheDir(), "apk");
                    dir.mkdirs();
                    File f = new File(dir, "update.apk");
                    if (f.exists()) f.delete();
                    HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                    h.setConnectTimeout(15000);
                    h.setReadTimeout(30000);
                    h.setInstanceFollowRedirects(true);
                    int code = h.getResponseCode();
                    if (code != 200) throw new IOException("Server membalas " + code);
                    long total = h.getContentLengthLong();
                    long got = 0;
                    try (InputStream in = h.getInputStream(); FileOutputStream out = new FileOutputStream(f)) {
                        byte[] b = new byte[16384];
                        int n;
                        while ((n = in.read(b)) > 0) {
                            out.write(b, 0, n);
                            got += n;
                            if (total > 0) dlPct = (int) (got * 100 / total);
                        }
                    }
                    if (total > 0 && got != total) throw new IOException("Unduhan tidak lengkap");
                    Uri u = FileProvider.getUriForFile(c, c.getPackageName() + ".fileprovider", f);
                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(u, "application/vnd.android.package-archive");
                    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(i);
                    dlPct = 100;
                    call.resolve();
                } catch (Exception e) {
                    call.reject(String.valueOf(e.getMessage()));
                }
            }
        }).start();
    }
}
