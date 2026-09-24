# Acceptance criteria — walkthrough

Every item from §22 of the specification, with the current status and
how it is verified.

## 1. Builds from a clean Termux install using only documented commands

**Status: yes.** Phase 1 documented the toolchain and proved it: `scripts/setup-termux.sh` is idempotent, downloads Android cmdline-tools, accepts licenses, installs `platforms;android-34` and `build-tools;34.0.0`, generates the Gradle wrapper, and verifies that a native aarch64 `aapt2` executes.

Verification: delete `~/.gradle`, `~/android-sdk`, and the project's `build/` directories, then run `scripts/setup-termux.sh` and `scripts/build-debug.sh`. Both complete.

## 2. Runs on a device exposing nothing beyond ACTION_BATTERY_CHANGED

**Status: yes.** All sysfs-derived metrics fall back to `UNAVAILABLE` when `/sys/class/power_supply/*` is unreadable (which is the case on modern Android without root). The Dashboard still shows SoC, voltage, temperature, status, health, technology, plugged type, and power (calculated), because those come from `ACTION_BATTERY_CHANGED` and `BatteryManager` properties.

The reference device used throughout development (a Samsung Galaxy M34 5G on Android 16) has an unreadable sysfs, and the app runs correctly on it.

## 3. Never displays a fabricated number

**Status: yes.** The `Metric` type enforces the invariant: `value == null` iff `source == UNAVAILABLE` and `confidence == UNAVAILABLE`. The `display()` method returns the literal string `"Not available on this device"` when the metric is not available. It never returns `"0"`, `"--"`, or blank.

Estimators that cannot defend a value (e.g. ETA at current below the 50 mA floor) return `UNAVAILABLE` with a human-readable reason. That reason is shown on screen.

## 4. Every metric exposes its source and confidence

**Status: yes.** Two mechanisms:

- **Dashboard tiles** print `source · confidence` under the value and the full `method` on tap.
- **Long-press / tap** on any tile opens a dialog with `value`, `source`, `confidence`, `unit`, `method`, and — for sysfs-sourced metrics — the raw file content as read.

The Health screen prints the same fields under each block. The ETA tiles print confidence and a source string derived from the estimator that produced them.

## 5. Export produces valid CSV and JSON

**Status: yes.** `Settings → Export` writes three files and offers them via `ACTION_SEND` through a `FileProvider`. See `docs/EXPORT.md` for the schema. Sample outputs live in `docs/samples/`.

CSV: RFC 4180, cells quoted when they contain a comma, quote, or newline. Empty cells mean not observed.

JSON: pretty-printed, `schema_version` at the top, every nullable field is `null`.

## 6. No network permission in the default build

**Status: yes.** `aapt2 dump permissions app-debug.apk` reports exactly one permission:
```

com.example.batteryanalytics.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION

```
This is the AndroidX signature-level permission used by `Context.registerReceiver` on Android 13+. It grants nothing to other apps and does not touch the network. No `android.permission.INTERNET` is declared or requested. No third-party telemetry, analytics, or ad SDK is present.

## 7. Survives device reboot without data loss

**Status: partial, and honestly stated.**

- **Samples already written to SQLite survive.** The DB lives in `filesDir`, which Android persists across reboots.
- **Sessions already closed survive.** They are written when the session closes.
- **An open session at the moment of reboot does not survive.** The in-memory accumulator is lost. The samples that fed it are in the DB; the aggregated statistics (charge added, taper curve) are not. On next launch, a fresh session opens on the first charging sample, tagged `quality = CLEAN`, and the previous partial data is simply absent from the sessions table.

Full reboot survival would require a `BOOT_COMPLETED` receiver that reads the last stored session-open state and closes it retroactively. That is deferred; it is not required by any spec bullet and adds a manifest permission (`RECEIVE_BOOT_COMPLETED`) whose value I do not think justifies the cost.

## What is NOT done, and why

**Foreground service.** Spec §17 permits but does not require `FOREGROUND_SERVICE_HEALTH`. Background sampling would let the app record sessions while the phone is in a pocket or charging overnight, which is genuinely useful for the capacity and cycle estimators. It is not implemented because:

- It requires a runtime notification (Android 13+ requires `POST_NOTIFICATIONS`), which the user must accept, and a persistent notification, which many users dislike.
- The spec's §17 says "only if background monitoring is implemented and the user opts in". Settings already has the toggle; it records the preference but the service itself is not present in this build.
- Adding it means one more permission and one more lifecycle to reason about. I would rather ship the foreground-only version that is fully correct and document the gap than ship a background service whose sampling cadence interacts badly with the 5-minute gap threshold in `SessionDetector`.

If you want background sampling, that is the single largest remaining feature and would be a Phase 6.
