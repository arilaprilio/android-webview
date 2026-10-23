# WebView Android

Fullscreen Android WebView app dengan URL yang bisa di-custom via Settings.

## Fitur

- **Fullscreen immersive** — status bar & navigation bar tersembunyi
- **URL bisa di-custom** — atur lewat Settings (swipe dari tepi kiri)
- **Auto-reload** — reload otomatis berdasarkan interval atau jam tertentu
- **Dark/Light mode** — toggle lewat Settings
- **HTTP Auth** — simpan credential untuk basic auth
- **Edge gestures:**
  - Swipe kiri → buka Settings
  - Swipe kanan → toggle keyboard
  - Swipe bawah → toolbar mini (reload, back, forward)
  - Long-press pojok kiri atas → keluar app
- **Landscape only** — dikunci orientasi landscape
- **Keep screen on** — layar tidak mati selama app aktif

## Build (CI/CD)

Project ini tidak perlu Gradle di lokal. Cukup push ke GitHub:

- **Push ke `main`** → auto build debug + release APK
- **Push tag `v*`** → auto build + publish ke GitHub Releases
- **Manual** → trigger dari tab Actions

```bash
# Release baru:
git tag v0.1.0
git push origin v0.1.0
```

## Struktur

```
webview-android/
├── .github/workflows/build.yml    # CI/CD: build & release
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/id/arilaprilio/webviewandroid/
│       │   ├── WebViewApp.kt            # Application + SettingsStore
│       │   ├── MainActivity.kt          # Fullscreen WebView host
│       │   ├── SettingsActivity.kt      # URL & preference editor
│       │   ├── EdgeGestureDetector.kt   # Left/right/bottom swipe detection
│       │   ├── AutoReloadScheduler.kt   # Periodic / hourly reload
│       │   ├── AuthDialog.kt            # HTTP Basic Auth prompt
│       │   ├── CredentialStore.kt       # Encrypted credential storage
│       │   └── DurationParser.kt        # "1h30m" parser
│       └── res/
│           ├── layout/                  # activity_main, activity_settings, dialog_auth
│           ├── values/                  # colors, strings, themes, dimens
│           └── xml/                     # data extraction rules
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Requirements

- Android 10+ (minSdk 29)
- compileSdk 34
- JDK 17
