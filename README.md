# Kiosk Android

Versi Android dari [electron-kiosk](https://github.com/innovation-system/electron-kiosk).
Satu URL, fullscreen, landscape, tanpa chrome browser — tapi menggantikan hotkey
desktop dengan **edge-swipe gestures** supaya operator tetap bisa buka settings,
keyboard, dan toolbar meskipun tanpa keyboard fisik.

## Fitur

| Electron (desktop)                    | Android (project ini)                      |
|---------------------------------------|--------------------------------------------|
| `Ctrl+Shift+K` → kembali ke settings  | **Swipe dari tepi kiri** → Settings        |
| `Ctrl+Shift+L` → toggle kiosk mode    | **Swipe dari tepi bawah** → toolbar mini   |
| `Ctrl+Shift+Q` → quit                 | **Long-press 1.5s pojok kiri-atas** → exit |
| (keyboard fisik desktop)              | **Swipe dari tepi kanan** → soft keyboard  |
| `electron-store` settings             | `SharedPreferences` di `kiosk_settings`    |
| HTTP Basic/Digest auth dialog         | `AuthDialog` + `CredentialStore`           |
| Auto-reload every / at hour           | `AutoReloadScheduler` dengan `Handler`     |

Semua field settings `src/store.js` di Electron tersedia di `SettingsStore.kt`
(kecuali `multipleDisplays` / `displays` karena perangkat Android umumnya 1 layar).

## Konfigurasi

- **minSdk**: 29 (Android 10)
- **targetSdk**: 34
- **Orientasi**: `sensorLandscape` (dikunci di manifest)
- **Immersive**: status bar + nav bar auto-hide dengan transient swipe reveal

## Build

```bash
# Pastikan Android SDK terpasang dan ANDROID_HOME ter-set.
cd /home/st/.local/lib/kiosk-android

# Pertama kali: generate wrapper (butuh gradle di PATH, atau pakai Android Studio sync).
gradle wrapper --gradle-version 8.9

# Debug APK
./gradlew assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Release APK (unsigned — tambahkan signingConfigs di app/build.gradle.kts untuk production)
./gradlew assembleRelease
```

Atau buka folder ini di **Android Studio**, biarkan Gradle sync, lalu Run.

## Install ke device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.innovationsystem.kioskandroid.debug/com.innovationsystem.kioskandroid.MainActivity
```

## Jadikan benar-benar kiosk (opsional, direkomendasikan)

Ada tiga tingkatan "lockdown"; pilih sesuai kebutuhan:

### Tingkat 1 — Launcher default
Manifest sudah mendeklarasikan `category.HOME`, jadi app ini bisa dipilih sebagai
launcher. Setelah install:

1. Tekan tombol Home fisik/virtual.
2. Pilih **Kiosk** dan centang **Always**.
3. Sekarang Home selalu kembali ke kiosk.

### Tingkat 2 — Screen pinning (tanpa setup khusus)
Settings → Security → Screen pinning → ON. Setelah app terbuka, pin dari recents.
Keluar screen pinning butuh gesture dua tombol — bisa diubah per OEM.

### Tingkat 3 — Device Owner / COSU (paling kuat)
Jadikan app ini device-owner sekali via `adb` di device yang baru di-reset:

```bash
adb shell dpm set-device-owner com.innovationsystem.kioskandroid/.KioskDeviceAdminReceiver
```

> Catatan: `KioskDeviceAdminReceiver` belum ada di kode ini — tambahkan dulu kalau
> mau pakai mode ini. Untuk mayoritas use case, Tingkat 1 + 2 sudah cukup.

## Development notes

- `MainActivity.dispatchTouchEvent` memfilter MotionEvent sebelum WebView, jadi
  edge-swipe tidak bentrok dengan scroll konten web. Threshold diatur di
  `EdgeGestureDetector` (edge thickness 24dp, trigger distance 48dp).
- `AutoReloadScheduler` sengaja pakai `Handler` main-thread, bukan `WorkManager`,
  karena interval kiosk bisa serendah beberapa menit — di bawah minimum
  `WorkManager` (15 menit).
- `AuthDialog` menjaga kontrak `HttpAuthHandler`: setiap jalur dismissal
  memanggil `cancel()` sehingga koneksi tidak menggantung.
- Dark mode diterapkan di `KioskApp.onCreate` sebelum activity apa pun inflate
  view-nya, menghindari flash tema salah.

## Struktur file

```
kiosk-android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/innovationsystem/kioskandroid/
│       │   ├── KioskApp.kt              # Application + SettingsStore
│       │   ├── MainActivity.kt          # WebView host + gesture dispatch
│       │   ├── SettingsActivity.kt      # UI settings mirip App.vue
│       │   ├── EdgeGestureDetector.kt   # Pengganti hotkey → edge swipe
│       │   ├── AutoReloadScheduler.kt   # Reload every / at hour
│       │   ├── DurationParser.kt        # Pengganti npm parse-duration
│       │   ├── AuthDialog.kt            # HTTP Basic/Digest dialog
│       │   └── CredentialStore.kt       # Simpan credential per host|realm
│       └── res/
│           ├── layout/{activity_main,activity_settings,dialog_auth}.xml
│           ├── values/{strings,themes,colors,dimens}.xml
│           └── xml/data_extraction_rules.xml
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Belum diimplementasikan (roadmap)

- `KioskDeviceAdminReceiver` untuk COSU/device-owner mode.
- Signing config release (saat ini unsigned).
- Multi-display (`multipleDisplays` di Electron version) — Android jarang perlu.
- Auto-update dari URL generic (Electron punya ini via `electron-builder`).
- i18n lengkap selain `en` dan `id`.
