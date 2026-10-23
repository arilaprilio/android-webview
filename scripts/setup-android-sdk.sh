#!/usr/bin/env bash
# ============================================================================
# Headless setup: Gradle + Android cmdline-tools + SDK packages untuk webview-android.
#
# Target: Ubuntu 25.10 (atau turunan Debian) dengan JDK 17 sudah terpasang.
# Pemakaian:
#     bash scripts/setup-android-sdk.sh
#
# Yang di-install:
#   - SDKMAN (jika belum) → Gradle 8.9
#   - Android cmdline-tools (latest)
#   - platform-tools (adb)
#   - platforms;android-34 (compileSdk/targetSdk)
#   - build-tools;34.0.0
#   - Accept semua SDK license otomatis
#
# Yang TIDAK di-install:
#   - Android Studio (headless, kita bisa live tanpa IDE)
#   - Emulator images (biasanya pakai device fisik)
#
# Idempotent: aman dijalankan ulang.
# ============================================================================
set -euo pipefail

# ---- Konfigurasi (ubah di sini kalau mau versi lain) -----------------------
GRADLE_VERSION="8.9"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
CMDLINE_TOOLS_VERSION="11076708"   # Build 11.0, April 2024 — lihat https://developer.android.com/studio#command-line-tools-only
ANDROID_PLATFORM="android-34"
ANDROID_BUILD_TOOLS="34.0.0"

log() { printf '\033[1;34m[setup]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31m[err]\033[0m %s\n' "$*" >&2; exit 1; }

# ---- 1. Prasyarat sistem ---------------------------------------------------
log "Checking prerequisites…"
command -v java >/dev/null || die "java not found. Install: sudo apt install openjdk-17-jdk"
command -v curl >/dev/null || die "curl not found. Install: sudo apt install curl"
command -v unzip >/dev/null || { log "Installing unzip…"; sudo apt-get install -y unzip; }
command -v zip >/dev/null || { log "Installing zip (needed by SDKMAN)…"; sudo apt-get install -y zip; }

java_major=$(java -version 2>&1 | awk -F '"' '/version/ {split($2, a, "."); print a[1]}')
[[ "$java_major" -ge 17 ]] || die "JDK >= 17 required, found $java_major"
log "JDK $java_major OK"

# ---- 2. SDKMAN + Gradle ----------------------------------------------------
if [[ ! -d "$HOME/.sdkman" ]]; then
    log "Installing SDKMAN…"
    curl -s "https://get.sdkman.io?rcupdate=true" | bash
fi

# shellcheck disable=SC1091
source "$HOME/.sdkman/bin/sdkman-init.sh"

if ! sdk list gradle 2>/dev/null | grep -q "installed .*${GRADLE_VERSION}"; then
    log "Installing Gradle ${GRADLE_VERSION}…"
    sdk install gradle "$GRADLE_VERSION" <<< "y"
fi
sdk default gradle "$GRADLE_VERSION" || true
log "Gradle $(gradle --version | awk '/^Gradle/ {print $2}') ready"

# ---- 3. Android cmdline-tools ---------------------------------------------
mkdir -p "$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT ANDROID_HOME="$ANDROID_SDK_ROOT"

cmdline_dir="$ANDROID_SDK_ROOT/cmdline-tools/latest"
if [[ ! -x "$cmdline_dir/bin/sdkmanager" ]]; then
    log "Downloading Android cmdline-tools…"
    tmp=$(mktemp -d)
    cd "$tmp"
    curl -LO "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
    unzip -q "commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip"
    mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"
    # Google mem-package zip-nya sebagai `cmdline-tools/` di root — sdkmanager wajib
    # berada di `cmdline-tools/latest/` menurut docs resmi, jadi rename:
    rm -rf "$cmdline_dir"
    mv cmdline-tools "$cmdline_dir"
    cd - >/dev/null
    rm -rf "$tmp"
fi

export PATH="$cmdline_dir/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"
log "sdkmanager: $(sdkmanager --version 2>/dev/null || echo 'FAIL')"

# ---- 4. Install SDK packages ----------------------------------------------
log "Accepting licenses + installing SDK packages…"
yes | sdkmanager --licenses >/dev/null 2>&1 || true
sdkmanager \
    "platform-tools" \
    "platforms;${ANDROID_PLATFORM}" \
    "build-tools;${ANDROID_BUILD_TOOLS}" \
    >/dev/null

log "ADB: $(adb --version 2>/dev/null | head -1 || echo 'not in PATH yet')"

# ---- 5. Persist env di ~/.bashrc ------------------------------------------
marker="# >>> webview-android env >>>"
if ! grep -qF "$marker" "$HOME/.bashrc" 2>/dev/null; then
    log "Adding env exports to ~/.bashrc"
    cat >> "$HOME/.bashrc" <<EOF

$marker
export ANDROID_SDK_ROOT="$ANDROID_SDK_ROOT"
export ANDROID_HOME="\$ANDROID_SDK_ROOT"
export PATH="\$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:\$ANDROID_SDK_ROOT/platform-tools:\$PATH"
# <<< webview-android env <<<
EOF
fi

# ---- 6. local.properties untuk project ------------------------------------
project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "$project_root/settings.gradle.kts" ]]; then
    log "Writing $project_root/local.properties"
    printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > "$project_root/local.properties"
fi

# ---- 7. Generate gradle wrapper -------------------------------------------
if [[ -f "$project_root/settings.gradle.kts" && ! -f "$project_root/gradlew" ]]; then
    log "Generating Gradle wrapper in $project_root"
    (cd "$project_root" && gradle wrapper --gradle-version "$GRADLE_VERSION")
fi

cat <<MSG

✅  Setup complete.

Next steps:
    # Load env di shell saat ini (hanya untuk sesi terminal ini — sudah di-persist di ~/.bashrc)
    source ~/.bashrc

    cd $project_root
    ./gradlew assembleDebug

    # Install ke device
    adb install -r app/build/outputs/apk/debug/app-debug.apk

MSG
