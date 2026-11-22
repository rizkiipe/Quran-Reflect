package __APPID__;

import android.content.Intent;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Menerima perintah "buka menu tertentu" dari widget (intent extra tadabbur_tab) dan meneruskannya ke JavaScript. */
@CapacitorPlugin(name = "TadabburNav")
public class NavPlugin extends Plugin {
    static final String EXTRA = "tadabbur_tab";
    private String tertunda = null;

    @Override
    public void load() {
        try {
            Intent i = getActivity().getIntent();
            if (i != null) {
                tertunda = i.getStringExtra(EXTRA);
                i.removeExtra(EXTRA);
            }
        } catch (Exception ignored) {
        }
    }

    /** Aplikasi sudah berjalan lalu widget ditekan. */
    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        try {
            String t = intent == null ? null : intent.getStringExtra(EXTRA);
            if (t != null) {
                intent.removeExtra(EXTRA);
                tertunda = null;
                JSObject o = new JSObject();
                o.put("tab", t);
                notifyListeners("tab", o);
            }
        } catch (Exception ignored) {
        }
    }

    /** Dipanggil JavaScript saat aplikasi baru dibuka: menu yang diminta widget, atau kosong. */
    @PluginMethod
    public void ambil(PluginCall call) {
        JSObject o = new JSObject();
        o.put("tab", tertunda == null ? "" : tertunda);
        tertunda = null;
        call.resolve(o);
    }
}
