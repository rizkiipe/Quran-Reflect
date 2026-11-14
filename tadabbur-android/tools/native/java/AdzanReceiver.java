package __APPID__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Dipanggil AlarmManager tepat saat waktu sholat; memulai layanan pemutar adzan. */
public class AdzanReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        AdzanService.mulai(c, i.getStringExtra("file"), i.getStringExtra("judul"), i.getStringExtra("isi"));
    }
}
