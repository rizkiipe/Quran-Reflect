package __APPID__;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
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
}
