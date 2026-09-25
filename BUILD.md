# Build quick-start

Pick the path that matches your machine.

## Windows 11

Double-click `build-on-windows.cmd`, or run in PowerShell:

    git clone https://github.com/bigfguy8/battery-analytics.git
    cd battery-analytics
    powershell -ExecutionPolicy Bypass -File scripts\setup-windows.ps1

Then:

    .\gradlew.bat assembleRelease

APK: `app\build\outputs\apk\release\app-release.apk`

Full guide with troubleshooting: `docs/WINDOWS.md`.

## Android + Termux (no PC)

    git clone https://github.com/bigfguy8/battery-analytics.git
    cd battery-analytics
    bash scripts/setup-termux.sh
    bash scripts/build-release.sh

APK: `app/build/outputs/apk/release/app-release.apk`

Termux-specific notes: `docs/PHASE1.md`.

## Just want to install the app

Download the APK from:

    https://github.com/bigfguy8/battery-analytics/releases/latest

Tap the file in any file manager on an Android phone. Allow install from
unknown sources for the file manager, once.
