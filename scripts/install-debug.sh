#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
APK="app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "build first"; exit 1; }
adb install -r "$APK"
