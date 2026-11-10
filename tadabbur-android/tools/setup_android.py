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

# 3) AndroidManifest: izin notifikasi + receiver widget (aman dijalankan berulang)
mp = os.path.join(main, 'AndroidManifest.xml')
m = open(mp, encoding='utf-8').read()
for perm in ('android.permission.POST_NOTIFICATIONS', 'android.permission.RECEIVE_BOOT_COMPLETED'):
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
        import subprocess
        subprocess.check_call([sys.executable, '-m', 'pip', 'install', '--quiet', '--break-system-packages', 'pillow'])
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

buat_ikon(root, main)

print('Selesai. Paket aplikasi:', app_id)
