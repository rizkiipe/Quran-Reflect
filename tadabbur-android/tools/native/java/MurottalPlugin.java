package __APPID__;

import android.content.Context;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unduhan audio murottal untuk didengar tanpa internet, dan layanan latar agar pemutaran tidak dimatikan saat layar mati. */
@CapacitorPlugin(name = "TadabburMurottal")
public class MurottalPlugin extends Plugin {
    static MurottalPlugin INSTANCE;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private volatile boolean batal = false;

    @Override
    public void load() {
        INSTANCE = this;
    }

    @Override
    protected void handleOnDestroy() {
        if (INSTANCE == this) INSTANCE = null;
        batal = true;
        try { MurottalService.hentikan(getContext()); } catch (Exception ignored) { }
    }

    /** Dipanggil dari tombol di notifikasi: toggle, next, prev, stop. */
    static void kirim(String aksi) {
        MurottalPlugin p = INSTANCE;
        if (p == null) return;
        JSObject o = new JSObject();
        o.put("aksi", aksi);
        p.notifyListeners("aksi", o);
    }

    private File dasar() {
        return new File(getContext().getFilesDir(), "murottal");
    }

    private File dirEdisi(String edisi) {
        String bersih = edisi == null ? "" : edisi.replaceAll("[^A-Za-z0-9._-]", "");
        return new File(dasar(), bersih.isEmpty() ? "x" : bersih);
    }

    /** Daftar nomor ayat (nomor global 1-6236) yang sudah terunduh untuk satu qari. */
    @PluginMethod
    public void daftar(PluginCall call) {
        File d = dirEdisi(call.getString("edisi", ""));
        JSArray nomor = new JSArray();
        long bytes = 0;
        File[] fs = d.listFiles();
        if (fs != null) {
            for (File f : fs) {
                String nm = f.getName();
                if (!nm.endsWith(".mp3") || f.length() <= 0) continue;
                try {
                    nomor.put(Integer.parseInt(nm.substring(0, nm.length() - 4)));
                    bytes += f.length();
                } catch (NumberFormatException ignored) { }
            }
        }
        JSObject r = new JSObject();
        r.put("nomor", nomor);
        r.put("bytes", bytes);
        r.put("dir", dasar().getAbsolutePath());
        call.resolve(r);
    }

    @PluginMethod
    public void unduh(final PluginCall call) {
        final String edisi = call.getString("edisi", "");
        final JSArray nomor = call.getArray("nomor");
        final JSArray bitrate = call.getArray("bitrate");
        if (nomor == null || nomor.length() == 0) {
            call.reject("Tidak ada ayat untuk diunduh");
            return;
        }
        batal = false;
        getBridge().saveCall(call);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                File dir = dirEdisi(edisi);
                dir.mkdirs();
                int total = nomor.length(), selesai = 0;
                long bytes = 0;
                JSArray gagal = new JSArray();
                String ed = dir.getName();
                for (int i = 0; i < total && !batal; i++) {
                    int n;
                    try { n = nomor.getInt(i); } catch (Exception e) { continue; }
                    File f = new File(dir, n + ".mp3");
                    boolean ok = f.exists() && f.length() > 0;
                    if (!ok) {
                        int jb = bitrate == null ? 0 : bitrate.length();
                        for (int b = 0; b < jb && !ok && !batal; b++) {
                            int br;
                            try { br = bitrate.getInt(b); } catch (Exception e) { continue; }
                            ok = ambil("https://cdn.islamic.network/quran/audio/" + br + "/" + ed + "/" + n + ".mp3", f);
                        }
                    }
                    if (ok) {
                        selesai++;
                        bytes += f.length();
                    } else if (!batal) {
                        gagal.put(n);
                    }
                    JSObject p = new JSObject();
                    p.put("selesai", selesai);
                    p.put("total", total);
                    p.put("bytes", bytes);
                    notifyListeners("unduh", p);
                }
                JSObject r = new JSObject();
                r.put("selesai", selesai);
                r.put("total", total);
                r.put("bytes", bytes);
                r.put("gagal", gagal);
                r.put("batal", batal);
                call.resolve(r);
                try { getBridge().releaseCall(call); } catch (Exception ignored) { }
            }
        });
    }

    private boolean ambil(String alamat, File tujuan) {
        File tmp = new File(tujuan.getParentFile(), tujuan.getName() + ".tmp");
        HttpURLConnection h = null;
        try {
            h = (HttpURLConnection) new URL(alamat).openConnection();
            h.setConnectTimeout(10000);
            h.setReadTimeout(20000);
            if (h.getResponseCode() != 200) return false;
            InputStream in = h.getInputStream();
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[16384];
            int r;
            long total = 0;
            while ((r = in.read(buf)) != -1) {
                if (batal) {
                    out.close();
                    in.close();
                    tmp.delete();
                    return false;
                }
                out.write(buf, 0, r);
                total += r;
            }
            out.close();
            in.close();
            if (total < 1000) {
                tmp.delete();
                return false;
            }
            if (tujuan.exists()) tujuan.delete();
            return tmp.renameTo(tujuan);
        } catch (Exception e) {
            tmp.delete();
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }

    @PluginMethod
    public void batal(PluginCall call) {
        batal = true;
        call.resolve();
    }

    /** hapus {edisi, nomor:[...]} atau {edisi, semua:true}. */
    @PluginMethod
    public void hapus(PluginCall call) {
        try {
            File d = dirEdisi(call.getString("edisi", ""));
            Boolean semua = call.getBoolean("semua", false);
            if (semua != null && semua) {
                File[] fs = d.listFiles();
                if (fs != null) for (File f : fs) f.delete();
            } else {
                JSArray nomor = call.getArray("nomor");
                if (nomor != null) {
                    for (int i = 0; i < nomor.length(); i++) {
                        new File(d, nomor.getInt(i) + ".mp3").delete();
                    }
                }
            }
            call.resolve();
        } catch (Exception e) {
            call.reject("Gagal menghapus: " + e.getMessage());
        }
    }

    /** Nyalakan atau matikan layanan latar (notifikasi dengan tombol kontrol) selama murottal diputar. */
    @PluginMethod
    public void layanan(PluginCall call) {
        Context c = getContext();
        Boolean aktif = call.getBoolean("aktif", false);
        if (aktif != null && aktif) {
            Boolean jalan = call.getBoolean("jalan", true);
            MurottalService.mulai(c, call.getString("judul", "Murottal"), call.getString("isi", ""), jalan == null || jalan);
        } else {
            MurottalService.hentikan(c);
        }
        call.resolve();
    }
}
