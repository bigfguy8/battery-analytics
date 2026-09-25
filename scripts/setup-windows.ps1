# Battery Analytics -- Windows 11 bootstrap
#
# Downloads the Android SDK command-line tools, accepts licenses, installs
# platform-tools + platforms;android-34 + build-tools;34.0.0, writes
# local.properties, and prints the build command.
#
# Idempotent: safe to run repeatedly. Skips anything already present.
#
# Prerequisites (installed separately, one-time):
#   winget install --id EclipseAdoptium.Temurin.17.JDK
#   winget install --id Git.Git

$ErrorActionPreference = "Stop"

function Info($m) { Write-Host "[setup] $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host "[setup] $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "[setup] FAIL: $m" -ForegroundColor Red; exit 1 }

# Locate project root: this script lives in scripts\, parent is the root.
$projectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $projectRoot
Info "Project root: $projectRoot"

# JDK check (warn only; the actual build will fail loudly if wrong).
try {
    $javaOutput = (& java -version 2>&1 | Out-String)
    if ($javaOutput -notmatch 'version "1[78]' -and $javaOutput -notmatch 'version "2[0-9]') {
        Warn "Java 17+ recommended. Detected:"
        Write-Host $javaOutput
    } else {
        Info "Java 17+ detected."
    }
} catch {
    Fail "Java not found. Run: winget install --id EclipseAdoptium.Temurin.17.JDK"
}

# SDK root: honor existing env, else default.
$sdkRoot = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
           elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME }
           else { Join-Path $env:USERPROFILE "android-sdk" }
Info "SDK root: $sdkRoot"
New-Item -ItemType Directory -Force -Path $sdkRoot | Out-Null

# Command-line tools
$cmdlineDir = Join-Path $sdkRoot "cmdline-tools\latest"
$sdkmanager = Join-Path $cmdlineDir "bin\sdkmanager.bat"
if (-not (Test-Path $sdkmanager)) {
    Info "Downloading Android command-line tools..."
    $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    $zip = Join-Path $sdkRoot "cmdline-tools.zip"
    Invoke-WebRequest -Uri $url -OutFile $zip

    $tmp = Join-Path $sdkRoot "cmdline-tools-tmp"
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    Expand-Archive -Path $zip -DestinationPath $tmp -Force

    $inner = Join-Path $tmp "cmdline-tools"
    if (Test-Path $inner) {
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $cmdlineDir) | Out-Null
        if (Test-Path $cmdlineDir) { Remove-Item -Recurse -Force $cmdlineDir }
        Move-Item $inner $cmdlineDir
    }
    Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
    Remove-Item -Force $zip -ErrorAction SilentlyContinue
}
if (-not (Test-Path $sdkmanager)) { Fail "sdkmanager not found at $sdkmanager" }
Info "sdkmanager present."

# Licenses
Info "Accepting SDK licenses..."
$yes = ("y`n" * 30)
$yes | & $sdkmanager --sdk_root=$sdkRoot --licenses 2>&1 | Out-Null

# Packages
Info "Installing platform-tools, platforms;android-34, build-tools;34.0.0..."
& $sdkmanager --sdk_root=$sdkRoot --install "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# local.properties (Windows path needs backslashes escaped for Java props)
$localProps = Join-Path $projectRoot "local.properties"
$escaped = $sdkRoot -replace '\\', '\\'
Set-Content -Path $localProps -Value "sdk.dir=$escaped" -Encoding ASCII
Info "Wrote $localProps"

# Env hints
if (-not $env:ANDROID_HOME) {
    Warn "ANDROID_HOME not set. The build will work via local.properties."
    Warn "To use adb from any shell, add to your user environment:"
    Write-Host "    ANDROID_HOME = $sdkRoot"
    Write-Host "    PATH        += $sdkRoot\platform-tools"
}

# aapt2 sanity
$aapt2 = Join-Path $sdkRoot "build-tools\34.0.0\aapt2.exe"
if (Test-Path $aapt2) {
    Info "aapt2 verified."
} else {
    Fail "aapt2 missing at $aapt2"
}

Write-Host ""
Info "Setup complete."
Write-Host ""
Write-Host "Next commands (run from $projectRoot):"
Write-Host "    .\gradlew.bat testDebugUnitTest    # 66 unit tests"
Write-Host "    .\gradlew.bat assembleRelease      # release APK (~950 KB)"
Write-Host "    .\gradlew.bat installDebug         # install on USB phone"
Write-Host ""
