#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

log()  { printf "\033[1;34m[setup]\033[0m %s\n" "$*"; }
warn() { printf "\033[1;33m[setup]\033[0m %s\n" "$*"; }
fail() { printf "\033[1;31m[setup] FAIL:\033[0m %s\n" "$*" >&2; exit 1; }

log "Updating package index"
pkg update -y >/dev/null 2>&1 || true

log "Installing base packages"
for p in openjdk-17 gradle git nano curl unzip zip; do
    if ! dpkg -s "$p" >/dev/null 2>&1; then
        pkg install -y "$p" || fail "pkg install $p failed"
    fi
done

log "Attempting aapt2 / zipalign / apksigner"
for p in aapt2 zipalign apksigner; do
    if pkg install -y "$p" >/dev/null 2>&1; then
        log "  + $p"
    else
        warn "  - $p not in repo"
    fi
done

if ! command -v aapt2 >/dev/null 2>&1; then
    warn "trying tur-repo for aapt2"
    if pkg install -y tur-repo >/dev/null 2>&1; then
        pkg update -y >/dev/null 2>&1 || true
        pkg install -y aapt2 >/dev/null 2>&1 && log "  + aapt2 via tur-repo" || true
    fi
fi

SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
log "SDK root: $SDK_ROOT"
mkdir -p "$SDK_ROOT"

CMDLINE_DIR="$SDK_ROOT/cmdline-tools/latest"
if [ ! -x "$CMDLINE_DIR/bin/sdkmanager" ]; then
    log "Downloading cmdline-tools"
    URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    TMPZIP="$SDK_ROOT/cmdline-tools.zip"
    curl -fL "$URL" -o "$TMPZIP" || fail "download failed"
    mkdir -p "$SDK_ROOT/cmdline-tools"
    unzip -q -o "$TMPZIP" -d "$SDK_ROOT/cmdline-tools"
    rm -f "$TMPZIP"
    if [ -d "$SDK_ROOT/cmdline-tools/cmdline-tools" ]; then
        mv "$SDK_ROOT/cmdline-tools/cmdline-tools" "$CMDLINE_DIR"
    fi
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
SDKMANAGER="$CMDLINE_DIR/bin/sdkmanager"
[ -x "$SDKMANAGER" ] || fail "sdkmanager not found"

log "Accepting licenses"
yes | "$SDKMANAGER" --sdk_root="$SDK_ROOT" --licenses >/dev/null 2>&1 || true

log "Installing platform-tools, android-34, build-tools 34.0.0"
"$SDKMANAGER" --sdk_root="$SDK_ROOT" --install \
    "platform-tools" "platforms;android-34" "build-tools;34.0.0" \
    || fail "sdkmanager install failed"

log "Writing local.properties"
printf "sdk.dir=%s\n" "$SDK_ROOT" > local.properties

if [ ! -x ./gradlew ]; then
    log "Generating Gradle wrapper 8.7"
    gradle wrapper --gradle-version 8.7 --distribution-type bin \
        || fail "gradle wrapper failed"
fi

log "Verifying aapt2"
SDK_AAPT2="$SDK_ROOT/build-tools/34.0.0/aapt2"
TERMUX_AAPT2="$(command -v aapt2 || true)"

AAPT2_BIN=""
if [ -n "$TERMUX_AAPT2" ] && "$TERMUX_AAPT2" version >/dev/null 2>&1; then
    AAPT2_BIN="$TERMUX_AAPT2"
    log "  using Termux aapt2: $AAPT2_BIN"
elif [ -x "$SDK_AAPT2" ] && "$SDK_AAPT2" version >/dev/null 2>&1; then
    AAPT2_BIN="$SDK_AAPT2"
    log "  using SDK aapt2: $AAPT2_BIN"
else
    fail "aapt2 present but will not execute on this device (likely x86_64 binary on aarch64). See docs/PHASE1.md."
fi
log "aapt2 version: $("$AAPT2_BIN" version)"

if [ "$AAPT2_BIN" != "$SDK_AAPT2" ]; then
    if ! grep -q "^android.aapt2FromMavenOverride=" gradle.properties 2>/dev/null; then
        printf "\nandroid.aapt2FromMavenOverride=%s\n" "$AAPT2_BIN" >> gradle.properties
    fi
fi

log "Phase 1 toolchain ready."
