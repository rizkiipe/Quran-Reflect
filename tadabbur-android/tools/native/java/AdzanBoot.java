package __APPID__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Setelah HP restart atau aplikasi diperbarui, alarm adzan dipasang ulang dari data tersimpan. */
public class AdzanBoot extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        AdzanAlarm.atur(c);
    }
}
