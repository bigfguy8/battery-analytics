# Building Battery Analytics on Windows 11

The project was developed on Android + Termux. It also builds cleanly on
Windows 11. This document covers the Windows path.

Source code, Gradle wrapper, and dependency versions are identical on both
platforms. Only three things differ:

1. `local.properties` stores an absolute SDK path and is gitignored — it
   must be regenerated on the new machine.
2. The Gradle wrapper is `.\gradlew.bat` on Windows, `./gradlew` on Unix.
3. Native binaries (aapt2) are x86_64 on Windows, so no override is needed.
   On Termux-aarch64 we set `android.aapt2FromMavenOverride`; on Windows
   the SDK binaries work directly.

---

## Path A — Android Studio (recommended)

Fastest route. About 10 minutes.

1. Download Android Studio from https://developer.android.com/studio
2. Run the installer, accept defaults. It bundles JDK 17 and the SDK.
3. Launch Android Studio → **Open** → select the `battery-analytics` folder.
4. Wait for Gradle sync (~2 minutes first run; downloads dependencies).
5. Press **▶ Run**. Connect a phone via USB (USB debugging enabled) or use
   the bundled emulator.

That is the whole path.

---

## Path B — One-click build (Windows, no Android Studio)

Prerequisites, installed once. Open **PowerShell as Administrator**:

    winget install --id EclipseAdoptium.Temurin.17.JDK
    winget install --id Git.Git

Then clone and run the bootstrapper. The whole thing in one line:

    git clone https://github.com/bigfguy8/battery-analytics.git && cd battery-analytics && powershell -ExecutionPolicy Bypass -File scripts\setup-windows.ps1

Or, if you received a zip instead of a clone:

1. Extract the zip.
2. Open the folder.
3. **Double-click `build-on-windows.cmd`.**

The `.cmd` file opens PowerShell, runs `scripts/setup-windows.ps1` with the
right execution policy, downloads the Android command-line tools, accepts
SDK licenses, installs `platform-tools`, `platforms;android-34` and
`build-tools;34.0.0`, writes `local.properties`, and prints the exact
build command. Takes 5–10 minutes on first run; skips everything on rerun.

Then build:

    .\gradlew.bat assembleDebug

APK at `app\build\outputs\apk\debug\app-debug.apk`.

Release APK (~950 KB, R8 full mode):

    .\gradlew.bat assembleRelease

APK at `app\build\outputs\apk\release\app-release.apk`.

Install on a USB-connected phone:

    .\gradlew.bat installDebug

---

## Path C — WSL2 (Linux on Windows)

If you want the same commands as Termux:

    wsl --install -d Ubuntu

Restart when prompted. Inside the Ubuntu shell:

    sudo apt update && sudo apt install -y openjdk-17-jdk unzip curl git
    git clone https://github.com/bigfguy8/battery-analytics.git
    cd battery-analytics
    mkdir -p ~/android-sdk/cmdline-tools
    curl -L "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o /tmp/tools.zip
    unzip -q /tmp/tools.zip -d ~/android-sdk/cmdline-tools
    mv ~/android-sdk/cmdline-tools/cmdline-tools ~/android-sdk/cmdline-tools/latest
    export ANDROID_HOME=$HOME/android-sdk
    yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
    ~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
    echo "sdk.dir=$HOME/android-sdk" > local.properties
    ./gradlew assembleRelease

---

## Running the tests

    .\gradlew.bat testDebugUnitTest

66 pure-JVM tests, no device required. Takes under a minute after the
first build.

---

## What does not change between Termux and Windows

- The source code, line for line
- Gradle wrapper version (8.7)
- Every dependency version
- Output APK byte size
- Test count and results

---

## Troubleshooting

**`local.properties` missing.**
It is gitignored and stores your SDK path. If you cloned without running
the setup script, create it manually:

    echo "sdk.dir=$env:USERPROFILE\android-sdk" > local.properties

**`aapt2` errors.**
Windows has native aapt2 in the SDK. If you copied `gradle.properties`
from Termux, remove any `android.aapt2FromMavenOverride` line.

**Gradle daemon crashes.**
Increase heap in `gradle.properties`:

    org.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=1024m

Windows machines have more RAM than phones; 3 GB heap is safe.

**Java version mismatch.**
This project requires JDK 17. Verify with `java -version`. If older,
install Temurin 17 and reopen PowerShell.

**`adb` does not see the phone.**
Install the OEM USB driver, enable USB debugging, accept the phone's
"Allow USB debugging?" prompt. If the phone shows as `unauthorized`,
revoke USB authorizations on the phone and reconnect.

---

## See also

- `HANDOFF.md` — architecture and reading order
- `docs/ACCEPTANCE.md` — what the app claims and how it is verified
- `docs/METHODS.md` — every estimate, formula, and failure mode
- `CHANGELOG.md` — version history
