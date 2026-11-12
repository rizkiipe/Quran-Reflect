#!/usr/bin/env python3
"""Jalankan SETELAH `npx cap add android`: menambahkan widget, plugin, ikon notifikasi, dan izin ke proyek Android."""
import json, os, re, shutil, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
app_id = json.load(open(os.path.join(root, 'capacitor.config.json'), encoding='utf-8'))['appId']
native = os.path.join(root, 'tools', 'native')
main = os.path.join(root, 'android', 'app', 'src', 'main')
if not os.path.isdir(main):
    sys.exit('Folder android belum ada. Jalankan dulu: npx cap add android')

# 1) Java: ganti MainActivity bawaan, tambahkan plugin dan widget
jdir = os.path.join(main, 'java', *app_id.split('.'))
os.makedirs(jdir, exist_ok=True)
for f in os.listdir(jdir):
    if f.startswith('MainActivity.'):
        os.remove(os.path.join(jdir, f))
for f in os.listdir(os.path.join(native, 'java')):
    src = open(os.path.join(native, 'java', f), encoding='utf-8').read().replace('__APPID__', app_id)
    open(os.path.join(jdir, f), 'w', encoding='utf-8').write(src)

# 2) resource: layout, xml, drawable, values
for sub in ('layout', 'xml', 'drawable', 'values'):
    dst = os.path.join(main, 'res', sub)
    os.makedirs(dst, exist_ok=True)
    for f in os.listdir(os.path.join(native, 'res', sub)):
        shutil.copy(os.path.join(native, 'res', sub, f), os.path.join(dst, f))

# 2b) suara adzan (opsional): taruh berkas pendek tools/native/res/raw/adzan.mp3 agar pilihan "Adzan" aktif
ADZAN = os.path.isfile(os.path.join(native, 'res', 'raw', 'adzan.mp3'))
if ADZAN:
    os.makedirs(os.path.join(main, 'res', 'raw'), exist_ok=True)
    shutil.copy(os.path.join(native, 'res', 'raw', 'adzan.mp3'), os.path.join(main, 'res', 'raw', 'adzan.mp3'))

# 3) AndroidManifest: izin notifikasi + receiver widget (aman dijalankan berulang)
mp = os.path.join(main, 'AndroidManifest.xml')
m = open(mp, encoding='utf-8').read()
for perm in ('android.permission.POST_NOTIFICATIONS', 'android.permission.RECEIVE_BOOT_COMPLETED', 'android.permission.REQUEST_INSTALL_PACKAGES',
             'android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION',
             'android.permission.SCHEDULE_EXACT_ALARM', 'android.permission.USE_EXACT_ALARM'):
    if perm not in m:
        m = m.replace('<application', '<uses-permission android:name="%s" />\n    <application' % perm, 1)
if 'AyatWidget' not in m:
    receiver = '''
        <receiver android:name=".AyatWidget" android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/ayat_widget_info" />
        </receiver>
'''
    m = re.sub(r'</application>', receiver + '    </application>', m, count=1)
open(mp, 'w', encoding='utf-8').write(m)
# 3b) FileProvider bawaan Capacitor harus mengizinkan folder cache (tempat APK pembaruan diunduh)
fp = os.path.join(main, 'res', 'xml', 'file_paths.xml')
if os.path.isfile(fp):
    x = open(fp, encoding='utf-8').read()
    if 'cache-path' not in x:
        x = x.replace('</paths>', '    <cache-path name="tadabbur_cache" path="." />\n</paths>')
        open(fp, 'w', encoding='utf-8').write(x)
if 'fileprovider' not in m.lower():
    print('PERINGATAN: FileProvider tidak ditemukan di AndroidManifest; pasang pembaruan dalam aplikasi mungkin gagal.')
# 4) Ikon aplikasi: pakai ikon terbesar yang tertulis di manifest.json web app (cadangan: icon-512.png)
def cari_ikon(root):
    repo = os.path.dirname(root)
    for d in (root, repo):
        mp = os.path.join(d, 'manifest.json')
        if os.path.isfile(mp):
            try:
                best, size = None, -1
                for ic in json.load(open(mp, encoding='utf-8')).get('icons', []):
                    if 'monochrome' in str(ic.get('purpose', '')):
                        continue
                    try:
                        w = int(str(ic.get('sizes', '0x0')).split()[0].split('x')[0])
                    except Exception:
                        w = 0
                    src = str(ic.get('src', ''))
                    for cand in (os.path.join(d, src.lstrip('./').lstrip('/')), os.path.join(d, os.path.basename(src))):
                        if os.path.isfile(cand) and w > size:
                            best, size = cand, w
                if best:
                    return best
            except Exception:
                pass
    for d in (root, repo):
        c = os.path.join(d, 'icon-512.png')
        if os.path.isfile(c):
            return c
    return None

def buat_ikon(root, main):
    src = cari_ikon(root)
    if not src:
        print('Ikon web app tidak ditemukan, memakai ikon bawaan Capacitor.')
        return
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        import importlib, subprocess
        subprocess.check_call([sys.executable, '-m', 'pip', 'install', '--quiet', '--break-system-packages', 'pillow'])
        import site
        # pip bisa memasang ke folder pengguna (~/.local) bila folder sistem tidak bisa ditulis;
        # folder itu belum ada di jalur impor proses yang sedang berjalan, jadi tambahkan secara manual
        site.addsitedir(site.getusersitepackages())
        importlib.invalidate_caches()
        from PIL import Image, ImageDraw
    img = Image.open(src).convert('RGBA')
    px = img.getpixel((2, 2))
    bg = px[:3] if px[3] > 250 else (11, 46, 49)          # warna latar: sudut ikon, atau hijau tua aplikasi
    hexbg = '#%02X%02X%02X' % bg
    res = os.path.join(main, 'res')
    dens = {'mdpi': 1.0, 'hdpi': 1.5, 'xhdpi': 2.0, 'xxhdpi': 3.0, 'xxxhdpi': 4.0}
    for d, f in dens.items():
        folder = os.path.join(res, 'mipmap-' + d)
        os.makedirs(folder, exist_ok=True)
        s48 = int(48 * f)
        legacy = img.resize((s48, s48), Image.LANCZOS)
        legacy.save(os.path.join(folder, 'ic_launcher.png'))
        mask = Image.new('L', (s48, s48), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, s48 - 1, s48 - 1), fill=255)
        rnd = legacy.copy()
        rnd.putalpha(mask)
        rnd.save(os.path.join(folder, 'ic_launcher_round.png'))
        s108 = int(108 * f)                                  # ikon adaptif: gambar di area aman (sekitar 2/3 bagian tengah)
        fg = Image.new('RGBA', (s108, s108), (0, 0, 0, 0))
        inner = int(s108 * 0.667)
        fg.paste(img.resize((inner, inner), Image.LANCZOS), ((s108 - inner) // 2, (s108 - inner) // 2))
        fg.save(os.path.join(folder, 'tadabbur_foreground.png'))
    os.makedirs(os.path.join(res, 'values'), exist_ok=True)
    open(os.path.join(res, 'values', 'tadabbur_icon.xml'), 'w', encoding='utf-8').write(
        '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="tadabbur_icon_bg">%s</color>\n</resources>\n' % hexbg)
    adaptif = ('<?xml version="1.0" encoding="utf-8"?>\n'
               '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
               '    <background android:drawable="@color/tadabbur_icon_bg" />\n'
               '    <foreground android:drawable="@mipmap/tadabbur_foreground" />\n'
               '</adaptive-icon>\n')
    os.makedirs(os.path.join(res, 'mipmap-anydpi-v26'), exist_ok=True)
    for n in ('ic_launcher.xml', 'ic_launcher_round.xml'):
        open(os.path.join(res, 'mipmap-anydpi-v26', n), 'w', encoding='utf-8').write(adaptif)
    # layar pembuka (splash): ganti gambar bawaan Capacitor dengan latar polos + ikon, ukuran sama dengan file lama
    ganti = 0
    for dp, _, fs in os.walk(res):
        for f in fs:
            if f == 'splash.png':
                pth = os.path.join(dp, f)
                try:
                    w, h = Image.open(pth).size
                    kanvas = Image.new('RGBA', (w, h), bg + (255,))
                    t = int(min(w, h) * 0.30)
                    kanvas.paste(img.resize((t, t), Image.LANCZOS), ((w - t) // 2, (h - t) // 2))
                    kanvas.convert('RGB').save(pth)
                    ganti += 1
                except Exception as e:
                    print('Lewati', pth, e)
    print('Ikon dari', os.path.relpath(src, os.path.dirname(root)), '| warna latar', hexbg, '| splash diganti:', ganti)

try:
    buat_ikon(root, main)
except Exception as e:                      # ikon gagal tidak boleh menggagalkan seluruh build
    print('PERINGATAN: ikon tidak dibuat, memakai ikon bawaan Capacitor:', e)

# 5) Versi aplikasi + tanda tangan tetap (supaya versi baru bisa dipasang di atas yang lama tanpa hapus aplikasi)
def versi_dari_file():
    try:
        nama = open(os.path.join(root, 'VERSION'), encoding='utf-8').read().strip()
    except Exception:
        nama = '1.0'
    angka = [int(x) if x.isdigit() else 0 for x in re.split(r'[.\-]', nama)[:3]]
    while len(angka) < 3:
        angka.append(0)
    return nama, angka[0] * 10000 + angka[1] * 100 + angka[2]    # 1.1 -> 10100, 1.2 -> 10200, 1.2.3 -> 10203

def pasang_versi_dan_ttd(main):
    nama, kode = versi_dari_file()
    gp = os.path.join(os.path.dirname(os.path.dirname(main)), 'build.gradle')   # android/app/build.gradle
    if not os.path.isfile(gp):
        print('PERINGATAN: android/app/build.gradle tidak ditemukan, versi dan tanda tangan tidak diatur.')
        return nama, kode
    g = open(gp, encoding='utf-8').read()
    g = g.split('// TADABBUR-VERSI-MULAI')[0].rstrip() + '\n'      # aman dijalankan berulang
    ada_gs = os.path.isfile(os.path.join(os.path.dirname(gp), 'google-services.json'))
    akar = os.path.join(os.path.dirname(os.path.dirname(gp)), 'build.gradle')   # android/build.gradle
    if ada_gs and os.path.isfile(akar):
        r = open(akar, encoding='utf-8').read()
        if 'com.google.gms:google-services' not in r:
            r2 = re.sub(r'(buildscript\s*\{.*?dependencies\s*\{)', r"\1\n        classpath 'com.google.gms:google-services:4.4.4'", r, count=1, flags=re.S)
            open(akar, 'w', encoding='utf-8').write(r2)
    plugin_gs = ''
    if ada_gs and 'com.google.gms.google-services' not in g:
        plugin_gs = "apply plugin: 'com.google.gms.google-services'\n"
    g += """
// TADABBUR-VERSI-MULAI
android {
    defaultConfig {
        versionCode %d
        versionName "%s"
    }
    if (System.getenv("KS_FILE")) {
        signingConfigs {
            release {
                storeFile file(System.getenv("KS_FILE"))
                storePassword System.getenv("KS_PASS")
                keyAlias System.getenv("KS_ALIAS")
                keyPassword System.getenv("KS_PASS")
            }
        }
        buildTypes {
            release {
                signingConfig signingConfigs.release
            }
        }
    }
}
// TADABBUR-VERSI-SELESAI
""" % (kode, nama) + plugin_gs
    open(gp, 'w', encoding='utf-8').write(g)
    return nama, kode

nama_versi, kode_versi = pasang_versi_dan_ttd(main)

# berkas versi untuk web app: dipakai fitur "Cek pembaruan" di dalam aplikasi
repo = os.environ.get('GITHUB_REPOSITORY', '')
if not repo:
    try:
        repo = re.search(r'APP_VERSION=\{[^}]*repo:"([^"]*)"', open(os.path.join(root, 'www', 'app-version.js'), encoding='utf-8').read()).group(1)
    except Exception:
        repo = ''
open(os.path.join(root, 'www', 'app-version.js'), 'w', encoding='utf-8').write(
    '/* Dibuat otomatis oleh tools/setup_android.py */\nwindow.APP_VERSION={v:"%s",code:%d,repo:"%s"};\nwindow.ADZAN_ADA=%s;\n' % (nama_versi, kode_versi, repo, 'true' if ADZAN else 'false'))
print('Versi aplikasi:', nama_versi, '(versionCode %d)' % kode_versi, '| repositori pembaruan:', repo or '(belum terisi)',
      '| suara adzan:', 'ya' if ADZAN else 'tidak (tools/native/res/raw/adzan.mp3 belum ada)', '| tanda tangan tetap:', 'ya' if os.environ.get('KS_FILE') else 'tidak (APK debug)',
      '| login Google native:', 'ya' if os.path.isfile(os.path.join(main, '..', '..', 'google-services.json')) else 'tidak (google-services.json belum ada)')

print('Selesai. Paket aplikasi:', app_id)
