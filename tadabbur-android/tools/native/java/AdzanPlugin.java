package __APPID__;

import com.getcapacitor.JSArray;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Jembatan JavaScript untuk alarm adzan volume-alarm. */
@CapacitorPlugin(name = "TadabburAdzan")
public class AdzanPlugin extends Plugin {
    /** Ganti seluruh jadwal adzan. items: [{id, at, file, judul, isi}]; daftar kosong = batalkan semua. */
    @PluginMethod
    public void jadwal(PluginCall call) {
        JSArray items = call.getArray("items");
        AdzanAlarm.simpanDanAtur(getContext(), items == null ? "[]" : items.toString());
        call.resolve();
    }

    /** Putar adzan sekarang untuk menguji (file: adzan atau adzan_subuh). */
    @PluginMethod
    public void uji(PluginCall call) {
        AdzanService.mulai(getContext(), call.getString("file", "adzan"), "Uji adzan", "Adzan diputar lewat volume alarm.");
        call.resolve();
    }

    @PluginMethod
    public void berhenti(PluginCall call) {
        AdzanService.hentikan(getContext());
        call.resolve();
    }
}
