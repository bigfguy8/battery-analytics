@echo off
REM Battery Analytics -- double-click Windows bootstrapper.
REM
REM Extracts the project is already done; this file lives at the root of the
REM extracted zip or clone. Double-clicking runs setup-windows.ps1 with the
REM correct execution policy, then leaves the window open so you can read
REM the build commands it prints.

setlocal
cd /d "%~dp0"

echo ============================================================
echo   Battery Analytics -- Windows bootstrap
echo ============================================================
echo.
echo This will download the Android SDK command-line tools and
echo install the platform + build-tools this project targets.
echo First run takes 5-10 minutes. Re-runs are instant.
echo.

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "scripts\setup-windows.ps1"

echo.
echo ============================================================
echo   Done. Build commands are printed above.
echo ============================================================
pause
endlocal
