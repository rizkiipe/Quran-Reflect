# Quran Reflect untuk Android (dengan widget layar utama)

Isi paket: aplikasi web Anda (`www/index.html`) dibungkus Capacitor, ditambah widget Android asli,
jembatan data widget, dan notifikasi ayat terjadwal. Belum pernah dibuild atau dijalankan di perangkat.

## Cara A: build di cloud (tanpa Android Studio), perlu komputer untuk mengunggah
1. Buat akun di github.com, lalu buat repositori baru (boleh private).
2. Unggah SEMUA isi folder ini, termasuk folder tersembunyi `.github`.
   Setelah itu pastikan file `.github/workflows/build-apk.yml` benar-benar ada di repositori.
3. Buka tab **Actions** → **Build APK** → **Run workflow**. Tunggu sekitar 5–15 menit sampai hijau.
4. Buka hasil build tadi, unduh **quran-reflect-apk** (berupa zip berisi `app-debug.apk`).
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
   Cara lain: tekan lama layar utama → **Widget** → **Ayat Tadabbur**.
3. **Jadwalkan notifikasi ayat** membuat notifikasi 7 hari ke depan sesuai jam pengingat.
   Buka aplikasi seminggu sekali agar data dan jadwal diperbarui.

## Update versi tanpa hapus aplikasi (1.1 → 1.2 dst)
Supaya APK baru bisa dipasang di atas yang lama, semua versi harus ditandatangani kunci yang sama.
**Sekali saja:**
1. Di repositori GitHub: Settings → Secrets and variables → Actions → New repository secret.
2. Buat secret `KEYSTORE_PASSWORD` (isi: sandi dari file `KUNCI-RAHASIA-JANGAN-DIBAGIKAN.txt`).
3. Buat secret `KEYSTORE_BASE64` (isi: seluruh teks di `keystore_base64.txt`).
4. Jalankan Build APK. Hasilnya muncul di tab **Releases** sebagai `quran-reflect-v1.1.apk`.
5. Pemasangan pertama kali tetap harus hapus aplikasi lama (tanda tangannya beda). Setelah itu tidak perlu lagi.

**Setiap kali mau update:**
1. Ubah isi file `VERSION` (misalnya `1.1` jadi `1.2`) beserta perubahan lain, lalu unggah ke GitHub.
2. Build APK jalan otomatis dan menerbitkan rilis `v1.2`.
3. Di HP, aplikasi memeriksa rilis terbaru saat dibuka (maksimal tiap 12 jam) dan menampilkan kartu **Pembaruan tersedia**.
   Tekan **Unduh pembaruan**, buka APK-nya, lalu **Pasang**. Data favorit dan penanda tetap ada.
   Bisa juga dicek manual lewat tombol **Cek pembaruan** di bagian bawah aplikasi.

Catatan: repositori harus **public** agar aplikasi bisa membaca rilis terbaru. Jangan pernah mengunggah file `.jks` atau sandinya ke repositori.
Android tidak mengizinkan aplikasi memasang diri sendiri secara diam-diam, jadi langkah Pasang tetap ditekan manual.

## Login Google di aplikasi
Login Google lewat popup (cara untuk browser) tidak bisa selesai di dalam aplikasi Android; hasilnya halaman
"Unable to process request due to missing initial state". Aplikasi ini memakai login Google native (plugin
`@capacitor-firebase/authentication`), yang butuh pengaturan Firebase sekali saja:
1. Firebase Console → proyek `quranic-daily-life` → Authentication → Sign-in method → pastikan **Google** aktif.
2. Project settings (ikon roda gigi) → Your apps → **Add app → Android**. Package name: `id.tadabbur.harian`.
   Isi SHA-1 dan SHA-256 dari kunci tanda tangan tetap (lihat bagian Update di atas).
   Kalau aplikasi Android sudah pernah ditambahkan, buka app itu lalu tambahkan sidik jarinya di sana.
3. Unduh **google-services.json** (unduh ulang setelah menambah sidik jari).
4. Di GitHub: Settings → Secrets and variables → Actions → **Repository secrets** → New repository secret.
   Nama: `GOOGLE_SERVICES_JSON`, isi: seluruh isi berkas google-services.json.
5. Jalankan Build APK lagi dan pasang hasilnya. Tombol Masuk Google sekarang membuka pemilih akun Google bawaan HP.
Sidik jari hanya cocok untuk APK yang ditandatangani kunci tetap. APK debug (tanpa secret kunci) tidak bisa login Google.

## Geser untuk pindah menu
Geser ke kiri = menu berikutnya, geser ke kanan = menu sebelumnya (Hari ini, Sesuai harimu, Al-Qur'an, Doa, Tersimpan).
Geser tidak aktif saat sedang membaca satu surah, saat menyentuh kolom isian, atau pada bagian yang bisa digulir ke samping.

## Hal yang perlu Anda ketahui
- Login Google/Firebase memakai pop-up dan kemungkinan tidak jalan di dalam APK, jadi sinkron favorit antar perangkat
  bisa tidak berfungsi di sana. Favorit tetap tersimpan lokal.
- Font, audio, dan data tajwid masih memerlukan internet.
- Latar widget mengikuti waktu: pagi (05.00-10.59) langit dengan matahari baru terbit, siang (11.00-17.59) biru terang dengan matahari dan awan, malam (18.00-04.59) gelap dengan bulan dan bintang. Pergantian dipicu alarm hemat daya di jam 05.00, 11.00, dan 18.00, jadi bisa meleset beberapa menit. Latar ini mengikuti jam, bukan data cuaca asli.
- Ikon aplikasi dan layar pembuka diambil otomatis dari ikon terbesar di `manifest.json` web app (cadangan: `icon-512.png`). Bila repositori punya ikon di akar, skrip mencarinya satu folder di atas `tadabbur-android`.
