#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
APK="$(cd "$(dirname "$0")/.." && pwd)/app/build/outputs/apk/debug/app-debug.apk"
[ -f "$APK" ] || { echo "build first: scripts/build-debug.sh" >&2; exit 1; }
[ -d "$HOME/storage/downloads" ] || { echo "run: termux-setup-storage" >&2; exit 1; }
cp -f "$APK" "$HOME/storage/downloads/app-debug.apk"
echo "Copied to Downloads/app-debug.apk ($(du -h "$APK" | cut -f1))"
echo "Now open Files -> Downloads -> app-debug.apk -> Install."
