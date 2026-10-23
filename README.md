# WebView Android

Fullscreen Android WebView browser with customizable URL and auto-reload.

## Features

- **Fullscreen immersive** — hides status bar & navigation bar
- **Customizable URL** — configure via Settings (swipe from left edge)
- **Auto-reload** — automatic reload by interval or scheduled hour
- **Dark / Light mode** — toggle in Settings
- **HTTP Auth** — remembers credentials for basic authentication
- **Edge gestures:**
  - Swipe left edge → open Settings
  - Swipe right edge → toggle keyboard
  - Swipe bottom edge → mini toolbar (reload, back, forward)
  - Long-press top-left corner → exit app
- **Landscape only** — locked to landscape orientation
- **Keep screen on** — screen stays awake while the app is active

## Build (CI/CD)

No local Gradle installation required. Just push to GitHub:

| Trigger | What happens |
|---|---|
| Push to `main` | Auto build debug + release APK |
| Push tag `v*` | Auto build + publish to GitHub Releases |
| Manual | Trigger from the Actions tab |

```bash
# Create a new release:
git tag v0.1.0
git push origin v0.1.0
```

Download the APK from **Releases** or from **Actions → workflow run → Artifacts**.

## Project Structure

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
