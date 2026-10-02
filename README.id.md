<div align="center">
  <h1>Tsugi</h1>

  <p>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge&logo=open-source-initiative&logoColor=white" alt="License: MIT"></a>
    <a href="https://developer.android.com"><img src="https://img.shields.io/badge/Android-API%2026%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK"></a>
    <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin"></a>
    <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"></a>
  </p>

  <p>
    <a href="README.md"><img src="https://img.shields.io/badge/EN-0078D4.svg?style=for-the-badge" alt="English"></a>
    <a href="README.id.md"><img src="https://img.shields.io/badge/ID-6e7681.svg?style=for-the-badge" alt="Bahasa Indonesia"></a>
  </p>
</div>

Tsugi adalah aplikasi Android native untuk menyambung screenshot menjadi satu gambar. Bagikan screenshot langsung dari galeri atau aplikasi screenshot kamu, susun dengan preset layout, pangkas bar yang terduplikasi secara otomatis, lalu export hasilnya sebagai JPEG, PNG, atau WebP — semuanya 100% di perangkat.

---

## Sorotan & Fitur

- **4 Preset Layout**: **Long** (satu strip screenshot vertikal), **Side** (strip horizontal), **Grid** dan **Photo** (contact sheet) — preset hanya mengatur layout tanpa mengubah daftar gambarmu.
- **Smart Trimming**: Otomatis memangkas status bar yang seragam dan bagian ekor yang terduplikasi supaya sambungan screenshot mulus.
- **Layout Engine Fleksibel**: Atur gap, padding, corner radius, separator, dan alignment horizontal/vertikal sesuai selera.
- **Backdrop Kustom**: Render semuanya di atas kanvas berwarna yang bersih.
- **Banyak Format Export**: Export sebagai JPEG, PNG, atau WebP (WebP lossy di Android 11+).
- **Integrasi Share-To**: Tsugi muncul di share sheet sistem — sambung screenshot yang baru kamu ambil langsung dari layar recents.
- **100% On-Device**: Tanpa akun, tanpa analitik, tanpa panggilan jaringan. Aplikasi ini bahkan tidak mendeklarasikan permission `INTERNET`.
- **UI Modern**: Dibangun dengan Jetpack Compose dan Material 3.

---

## Download & Instalasi

Belum ada rilis yang dipublikasikan — build sendiri:

```bash
git clone https://github.com/kouzen-neo/tsugi.git
cd tsugi
./gradlew assembleRelease
```

APK akan ada di `app/build/outputs/apk/release/`. Untuk build rilis bertanda tangan, isi detail keystore di `keystore.properties` (lihat konfigurasi signing di `app/build.gradle.kts`); tanpanya, build memakai debug keystore lokal sehingga `assembleRelease` selalu berhasil.

---

## Alur Stitch

```text
[ Screenshot dari share sheet / galeri ]
           │
           ▼
[ 1. Import ]    ──> Share sheet sistem (SEND / SEND_MULTIPLE)
           │
           ▼
[ 2. Trim ]      ──> Pangkas bar seragam & ekor terduplikasi
           │
           ▼
[ 3. Layout ]    ──> Preset: Long / Grid / Side / Photo
           │        + gap, padding, corner radius, alignment
           ▼
[ 4. Render ]    ──> Backdrop, separator, komposisi kanvas
           │
           ▼
[ 5. Export ]    ──> JPEG / PNG / WebP, tersimpan ke galeri
```

---

## Privasi & Keamanan

- **Sepenuhnya Offline**: Semua pemrosesan gambar terjadi di perangkatmu. Aplikasi tidak mendeklarasikan permission `INTERNET`, jadi screenshot-mu secara fisik tidak bisa keluar dari HP lewat aplikasi ini.
- **Tanpa Akun atau Pelacakan**: Tanpa sign-in, tanpa analitik, tanpa SDK pelaporan crash.

---

## Penghargaan Open-Source

Tsugi dimungkinkan oleh teknologi dan proyek open-source komunitas:

- [Jetpack Compose](https://developer.android.com/jetpack/compose) untuk UI deklaratif modern.
- [Material 3](https://m3.material.io/) untuk design system.
- Library [AndroidX](https://developer.android.com/jetpack/androidx) (Activity, Lifecycle, Core).

---

## Lisensi

Proyek ini dilisensikan di bawah [MIT License](LICENSE).
