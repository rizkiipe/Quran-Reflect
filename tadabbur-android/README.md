# Tadabbur Harian untuk Android (dengan widget layar utama)

Isi paket: aplikasi web Anda (`www/index.html`) dibungkus Capacitor, ditambah widget Android asli,
jembatan data widget, dan notifikasi ayat terjadwal. Belum pernah dibuild atau dijalankan di perangkat.

## Cara A: build di cloud (tanpa Android Studio), perlu komputer untuk mengunggah
1. Buat akun di github.com, lalu buat repositori baru (boleh private).
2. Unggah SEMUA isi folder ini, termasuk folder tersembunyi `.github`.
   Setelah itu pastikan file `.github/workflows/build-apk.yml` benar-benar ada di repositori.
3. Buka tab **Actions** → **Build APK** → **Run workflow**. Tunggu sekitar 5–15 menit sampai hijau.
4. Buka hasil build tadi, unduh **tadabbur-harian-apk** (berupa zip berisi `app-debug.apk`).
5. Kirim `app-debug.apk` ke HP, buka, lalu izinkan "Pasang dari sumber tidak dikenal" bila diminta.

## Cara B: build lokal (Android Studio)
Butuh Node.js 22+, JDK 21, dan Android Studio (Otter atau lebih baru).
```
npm install
npx cap add android
python3 tools/setup_android.py
npx cap sync android
npx cap open android      # lalu Run di Android Studio, atau Build > Build APK
```

## Memakai widget
1. Buka aplikasi dengan internet aktif. Dalam beberapa detik ayat 7 hari ke depan disimpan untuk widget.
2. Di kartu **Widget dan pengingat** (tab Hari ini) buka bagian **4**, lalu tekan **Pasang widget**.
   Cara lain: tekan lama layar utama → **Widget** → **Tadabbur Harian**.
3. **Jadwalkan notifikasi ayat** membuat notifikasi 7 hari ke depan sesuai jam pengingat.
   Buka aplikasi seminggu sekali agar data dan jadwal diperbarui.

## Hal yang perlu Anda ketahui
- APK debug ditandatangani kunci acak per build. Untuk memasang versi baru, hapus aplikasi lama dulu.
  Kalau ingin update tanpa hapus, perlu keystore tetap (bisa saya siapkan).
- Login Google/Firebase memakai pop-up dan kemungkinan tidak jalan di dalam APK, jadi sinkron favorit antar perangkat
  bisa tidak berfungsi di sana. Favorit tetap tersimpan lokal.
- Font, audio, dan data tajwid masih memerlukan internet.
- Widget diperbarui sistem Android sekitar tiap 30 menit, jadi pergantian pagi, siang, dan malam bisa terlambat sedikit.
- Ikon aplikasi masih bawaan Capacitor. Ikon sendiri bisa ditambahkan nanti.
