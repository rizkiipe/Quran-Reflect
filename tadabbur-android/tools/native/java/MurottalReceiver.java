package __APPID__;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Meneruskan tombol di notifikasi murottal ke halaman aplikasi. Bila aplikasi sudah tertutup, layanan dimatikan. */
public class MurottalReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i == null ? null : i.getAction();
        if (a == null) return;
        if (MurottalPlugin.INSTANCE == null) {
            MurottalService.hentikan(c);
            return;
        }
        if (a.equals(MurottalService.AKSI_TOGGLE)) MurottalPlugin.kirim("toggle");
        else if (a.equals(MurottalService.AKSI_NEXT)) MurottalPlugin.kirim("next");
        else if (a.equals(MurottalService.AKSI_PREV)) MurottalPlugin.kirim("prev");
        else if (a.equals(MurottalService.AKSI_STOP)) {
            MurottalPlugin.kirim("stop");
            MurottalService.hentikan(c);
        }
    }
}
