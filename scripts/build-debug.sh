#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
[ -f local.properties ] || { echo "run setup-termux.sh first"; exit 1; }
./gradlew :app:assembleDebug "$@"
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] && { echo; echo "OK  $APK"; ls -lh "$APK"; } || { echo "APK missing"; exit 1; }
