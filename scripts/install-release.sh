#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || { echo "Build first: scripts/build-release.sh" >&2; exit 1; }
[ -d "$HOME/storage/downloads" ] || { echo "run: termux-setup-storage" >&2; exit 1; }

cp -f "$APK" "$HOME/storage/downloads/battery-analytics-release.apk"
echo "Copied to Downloads/battery-analytics-release.apk ($(du -h "$APK" | cut -f1))"
echo "Open Files -> Downloads -> battery-analytics-release.apk -> Install."
