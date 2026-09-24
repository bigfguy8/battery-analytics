#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

if [ ! -f local.properties ]; then
    echo "local.properties missing — run scripts/setup-termux.sh first" >&2
    exit 1
fi

if [ -f keystore.properties ]; then
    echo "[release] signing with keystore.properties"
else
    echo "[release] no keystore.properties — signing with the debug key"
    echo "[release] (installable over existing debug installs; not Play-Store distributable)"
fi

./gradlew :app:assembleRelease "$@"

APK="app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ]; then
    echo
    echo "OK   $APK"
    ls -lh "$APK"
    echo
    echo "Debug APK (for comparison):"
    ls -lh app/build/outputs/apk/debug/app-debug.apk 2>/dev/null || echo "  (none built yet)"
else
    echo "APK not produced — see Gradle output above" >&2
    exit 1
fi
