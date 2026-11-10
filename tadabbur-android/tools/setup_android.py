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
print('Selesai. Paket aplikasi:', app_id)
