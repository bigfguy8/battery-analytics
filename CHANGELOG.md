# Changelog

All notable changes to this project. Format loosely follows Keep a Changelog (https://keepachangelog.com/). Versions are semver: 0.x.y.

## v0.6.3 - 2026-09-25

- Smoothed history charts: 5-point moving average on Power, 3-point on SoC and Temperature. Display-only; underlying data unchanged.
- Daily summary shows an em-dash instead of 0.00 Ah when a day had no discharge, and instead of 0.0 C (which is not a plausible battery temperature).
- Health screen cards are tappable and open the same source/confidence/method dialog used on the Dashboard.
- Discharge ETA floor: refuses to compute below 20 mA (previously -1.0 mA idle produced a 7071-hour value). Cap: refuses to report more than 7 days.
- Current tile shows a plain-word state (idle, light use, super fast) plus a percent-per-hour rate.
- Tab bar icons: dashboard, bar chart, battery+bolt, heart.
- Compact min/max range labels with a single unit at the end.
- Samsung voltage caveat added to the Voltage detail dialog.
- scripts/verify.sh: pre-push hygiene and wiring verification.

## v0.6.0 - 2026-09-25

First release.

- Dashboard, History, Sessions, Health, Settings, Diagnostics screens.
- Every metric carries value, source, confidence, and method. Unavailable metrics display the literal string "Not available on this device" - never 0, never a placeholder.
- CSV and JSON export via FileProvider.
- Optional foreground service for background monitoring.
- Reboot recovery for interrupted sessions.
- Retention: 14 days full resolution, then 1-min downsample to 44 days. Sessions and rollups kept forever.
- 63 pure-JVM unit tests.
- No INTERNET permission. No analytics. No telemetry.
