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

Tsugi is a native Android app for stitching screenshots into a single image. Share screenshots straight from your gallery or screenshot app, arrange them with layout presets, trim duplicated bars automatically, and export the result as JPEG, PNG, or WebP — all 100% on-device.

---

## Highlights & Features

- **4 Layout Presets**: **Long** (one vertical screenshot strip), **Side** (horizontal strip), **Grid** and **Photo** (contact sheets) — presets set the layout without touching your image list.
- **Smart Trimming**: Automatically trims uniform status bars and duplicated tail regions so stitched screenshots join seamlessly.
- **Flexible Layout Engine**: Fine-tune gap, padding, corner radius, separators, and horizontal/vertical alignment.
- **Custom Backdrops**: Render everything on a clean color canvas.
- **Multiple Export Formats**: Export as JPEG, PNG, or WebP (lossy WebP on Android 11+).
- **Share-To Integration**: Tsugi appears in the system share sheet — stitch the screenshots you just captured, straight from the recents screen.
- **100% On-Device**: No accounts, no analytics, no network calls. The app doesn't even declare the `INTERNET` permission.
- **Modern UI**: Built with Jetpack Compose and Material 3.

---

## Download & Installation

No releases published yet — build it yourself:

```bash
git clone https://github.com/kouzen-neo/tsugi.git
cd tsugi
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. For a signed release build, put your keystore details in `keystore.properties` (see the signing config in `app/build.gradle.kts`); without it, the build falls back to the local debug keystore so `assembleRelease` always works.

---

## Stitch Pipeline

```text
[ Shared screenshots / gallery images ]
           │
           ▼
[ 1. Import ]    ──> System share sheet (SEND / SEND_MULTIPLE)
           │
           ▼
[ 2. Trim ]      ──> Trim uniform bars & duplicated tails
           │
           ▼
[ 3. Layout ]    ──> Preset: Long / Grid / Side / Photo
           │        + gap, padding, corner radius, alignment
           ▼
[ 4. Render ]    ──> Backdrop, separators, canvas composition
           │
           ▼
[ 5. Export ]    ──> JPEG / PNG / WebP, saved to gallery
```

---

## Privacy & Security

- **Fully Offline**: All image processing happens on your device. The app declares no `INTERNET` permission, so your screenshots physically cannot leave the phone through it.
- **No Accounts or Tracking**: No sign-in, no analytics, no crash-reporting SDKs.

---

## Open-Source Acknowledgments

Tsugi is made possible by open-source technologies and community projects:

- [Jetpack Compose](https://developer.android.com/jetpack/compose) for the modern declarative UI.
- [Material 3](https://m3.material.io/) for the design system.
- [AndroidX](https://developer.android.com/jetpack/androidx) libraries (Activity, Lifecycle, Core).

---

## License

This project is licensed under the [MIT License](LICENSE).
