package __APPID__;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Pembaca teks (Text-to-Speech) bawaan HP untuk Asma'ul Husna, karena WebView Android tidak punya speechSynthesis. */
@CapacitorPlugin(name = "TadabburTts")
public class TadabburTts extends Plugin {
    private TextToSpeech tts;
    private boolean siap = false, gagal = false, memulai = false;
    private final ArrayList<Runnable> antrean = new ArrayList<Runnable>();
    private final ConcurrentHashMap<String, PluginCall> panggilan = new ConcurrentHashMap<String, PluginCall>();
    private int nomor = 0;

    /** Jalankan aksi setelah mesin TTS selesai disiapkan (atau gagal disiapkan). */
    private void siapkan(final Runnable aksi) {
        synchronized (this) {
            if (siap || gagal) {
                aksi.run();
                return;
            }
            antrean.add(aksi);
            if (memulai) return;
            memulai = true;
        }
        try {
            tts = new TextToSpeech(getContext().getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    ArrayList<Runnable> tugas;
                    synchronized (TadabburTts.this) {
                        if (status == TextToSpeech.SUCCESS) {
                            siap = true;
                            pasangPendengar();
                        } else {
                            gagal = true;
                        }
                        tugas = new ArrayList<Runnable>(antrean);
                        antrean.clear();
                    }
                    for (Runnable r : tugas) r.run();
                }
            });
        } catch (Exception e) {
            ArrayList<Runnable> tugas;
            synchronized (this) {
                gagal = true;
                tugas = new ArrayList<Runnable>(antrean);
                antrean.clear();
            }
            for (Runnable r : tugas) r.run();
        }
    }

    private void pasangPendengar() {
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { selesai(id, true, false); }
            @Override public void onError(String id) { selesai(id, false, false); }
            @Override public void onError(String id, int kode) { selesai(id, false, false); }
            @Override public void onStop(String id, boolean terputus) { selesai(id, false, true); }
        });
    }

    private void selesai(String id, boolean ok, boolean berhenti) {
        PluginCall c = panggilan.remove(id);
        if (c == null) return;
        JSObject r = new JSObject();
        r.put("ok", ok);
        if (berhenti) r.put("berhenti", true);
        c.resolve(r);
        try { getBridge().releaseCall(c); } catch (Exception ignored) { }
    }

    /** Mengembalikan {ada: mesin TTS tersedia, arab: data suara Arab terpasang}. */
    @PluginMethod
    public void cek(final PluginCall call) {
        siapkan(new Runnable() {
            @Override
            public void run() {
                boolean ada = siap && tts != null;
                boolean arab = false;
                if (ada) {
                    try {
                        arab = tts.isLanguageAvailable(new Locale("ar")) >= TextToSpeech.LANG_AVAILABLE;
                    } catch (Exception ignored) { }
                }
                JSObject r = new JSObject();
                r.put("ada", ada);
                r.put("arab", arab);
                call.resolve(r);
            }
        });
    }

    /** Membaca teks; janji selesai ketika bacaan habis. Hasil {ok, alasan?, berhenti?}. */
    @PluginMethod
    public void bicara(final PluginCall call) {
        final String teks = call.getString("teks", "");
        final String lang = call.getString("lang", "ar");
        final Float rate = call.getFloat("rate", 0.85f);
        siapkan(new Runnable() {
            @Override
            public void run() {
                JSObject r = new JSObject();
                if (!siap || tts == null) {
                    r.put("ok", false);
                    r.put("alasan", "mesin");
                    call.resolve(r);
                    return;
                }
                try {
                    int s = tts.setLanguage(new Locale(lang));
                    if (s == TextToSpeech.LANG_MISSING_DATA || s == TextToSpeech.LANG_NOT_SUPPORTED) {
                        r.put("ok", false);
                        r.put("alasan", "arab");
                        call.resolve(r);
                        return;
                    }
                    tts.setSpeechRate(rate == null ? 0.85f : rate.floatValue());
                    String id = "ah" + (nomor++);
                    getBridge().saveCall(call);
                    panggilan.put(id, call);
                    int h = tts.speak(teks, TextToSpeech.QUEUE_FLUSH, new Bundle(), id);
                    if (h != TextToSpeech.SUCCESS) {
                        panggilan.remove(id);
                        r.put("ok", false);
                        r.put("alasan", "mesin");
                        call.resolve(r);
                        try { getBridge().releaseCall(call); } catch (Exception ignored) { }
                    }
                } catch (Exception e) {
                    r.put("ok", false);
                    r.put("alasan", "mesin");
                    call.resolve(r);
                }
            }
        });
    }

    @PluginMethod
    public void berhenti(PluginCall call) {
        try { if (tts != null) tts.stop(); } catch (Exception ignored) { }
        call.resolve();
    }

    @Override
    protected void handleOnDestroy() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception ignored) { }
    }
}
