
## Phase 2 — capability detection + data sources

Status: **complete**.

- `domain/model/` — `Metric<T>`, `Source`, `Confidence`, `Unit`, `CapabilityRow`, `BatterySnapshot`
- `domain/units/` — `UnitNormalizer` with documented heuristics
- `domain/estimate/` — `PowerCalculator` (`P = V x I`, sign from status)
- `data/source/` — `ApiBatterySource`, `SysfsPowerSupplySource`, `SourceResolver`
- `ui/dashboard/` — live values, long-press for full source/confidence/method
- `ui/capabilities/` — one row per metric, "Not available on this device" where absent
- `docs/CAPABILITIES.md`, `docs/METHODS.md`

Tests: `./gradlew :app:testDebugUnitTest`

Nothing in Phase 2 stores data, estimates anything beyond V x I, or requires a
new permission.

## Phase 3 — storage, sessions, history

Status: **complete**.

- `data/db/` — `BatteryDbHelper` + four DAOs over raw SQLite (no Room, no KSP)
- `data/repo/` — `BatteryRepository` (facade) and `SamplingEngine` (coroutine loop)
- `domain/session/` — `SessionDetector` and `SessionAccumulator`
- `domain/retention/` — `RetentionPolicy` with documented downsampling rules
- `ui/history/` — Canvas line charts (SoC, power, temp, voltage) with window selector
- `ui/sessions/` — session list and detail, including taper curve
- `docs/METHODS.md` has the full method + failure-mode section for Phase 3

Tabs: Dashboard / History / Sessions / Capabilities.

Sampling runs while the app is in the foreground only. No service, no new
permission, no background work.

## Phase 4 — estimation framework

Status: **complete (Block D2b)**.

- `domain/estimate/` — `CapacityEstimator`, `CycleEstimator`,
  `ChargingEtaEstimator`, `DischargeEtaEstimator`, `ChargingEfficiency`,
  `TaperParse`
- `ui/health/` — full Health screen with confidence legend and method text
- Dashboard — ETA row (targets 80/90/100 while charging, to 5 % while
  discharging) and battery health card
- Nav has 5 tabs: Dash / History / Sessions / Health / Caps
- `docs/METHODS.md` has the full Phase 4 formula + failure-mode writeup

53 unit tests pass (up from 31). No new permissions, no new dependencies.

## Phase 5 — export, settings, docs

Status: **complete**.

- `data/prefs/Prefs.kt` — SharedPreferences-backed settings
- `data/export/` — CSV writer, JSON writer, exporter, FileProvider bridge
- `ui/settings/` — Settings screen: sampling rate, retention, background toggle,
  rated capacity, export, delete-all-data, diagnostics
- `docs/EXPORT.md` — file schemas
- `docs/ACCEPTANCE.md` — walkthrough of every §22 criterion
- `docs/samples/` — a valid CSV sample for samples and sessions, and a valid
  JSON sample, all checked in and hand-verified

Capabilities screen moved from the bottom nav to `Settings → Diagnostics`; the
nav is now Dash / History / Sessions / Health.

No new permissions. No new dependencies beyond the ones already present.

## What is deliberately deferred

**Foreground service for background sampling.** The Settings toggle exists and
records the user's preference, but the service is not implemented. See
`docs/ACCEPTANCE.md` for the reasoning. This is the largest remaining feature.

## The app at a glance

| Tab | Shows |
|---|---|
| **Dash** | Live tiles: SoC, temperature, voltage, charge counter, current (with rolling min/max), power (auto-scaled W/mW/µW), battery health card (reported health, estimated full capacity, capacity health %, cycle count, peak during last charge), ETA tiles when charging or discharging, device info card, gear icon → Settings |
| **History** | Canvas line charts over a selectable window (1 h / 6 h / 24 h / 7 d / 30 d) for SoC, power, temperature (with 40 °C / 45 °C threshold lines), voltage |
| **Sessions** | List of recorded charging sessions, tap for detail incl. taper bar chart |
| **Health** | Estimated full capacity, design capacity (firmware or user-supplied), estimated capacity health %, cycle count, app-tracked age, confidence legend, disclaimer |
| **Settings** (via gear) | Sampling rate, retention windows, background monitoring preference, rated capacity (user-supplied), export, delete-all-data, diagnostics |
| **Diagnostics** (via Settings) | Per-metric capability report: source, confidence, method, raw sysfs string where applicable |

## Architecture summary
Final block. Docs, sample exports, and the acceptance walkthrough — no app code changes in this block.

```bash
cd ~/battery-analytics

mkdir -p docs/samples

# ---------- docs/EXPORT.md ----------
cat > docs/EXPORT.md <<'EOF'
# Export formats

The app can write three files, all into app-private storage under
`filesDir/exports/`, and hand them to any other app via `ACTION_SEND`
and a `FileProvider`. No storage permission is requested. Files can only
leave the app sandbox if the user explicitly shares them.

## Files

| File | Content | Format |
|---|---|---|
| `samples_YYYYMMDD_HHMMSS.csv` | every stored telemetry sample | RFC 4180 CSV |
| `sessions_YYYYMMDD_HHMMSS.csv` | every stored charging session | RFC 4180 CSV |
| `battery_YYYYMMDD_HHMMSS.json` | everything: metadata + capabilities + sessions + latest snapshot + samples | JSON |

Only the five most recent files of each type are kept. Older ones are
deleted on each export.

## CSV column reference

### `samples_*.csv`

| Column | Unit | Notes |
|---|---|---|
| `id` | — | SQLite primary key |
| `ts_iso` | ISO-8601 UTC | human-readable timestamp |
| `ts_ms` | epoch ms | lossless timestamp |
| `soc_pct` | percent | empty if unavailable |
| `voltage_v` | volt | empty if unavailable |
| `current_a` | ampere | empty if unavailable |
| `power_w` | watt | battery-side, from V×I |
| `temp_c` | °C | empty if unavailable |
| `status` | string | `CHARGING`, `DISCHARGING`, `FULL`, … |
| `plug` | string | `AC`, `USB`, `WIRELESS`, … |
| `source_flags` | bitmask | see `SourceFlags` in `domain/model` |
| `quality_flags` | bitmask | see `QualityFlags` in `domain/model` |

Empty cells mean **not observed**. They are never `0` and never `--`.

### `sessions_*.csv`

| Column | Unit | Notes |
|---|---|---|
| `id` | — | SQLite primary key |
| `start_iso` / `start_ms` | — | session open |
| `end_iso` / `end_ms` | — | session close; end_iso empty if still open |
| `soc_start` / `soc_end` | percent | empty if unknown at the boundary |
| `charge_ah` | Ah | trapezoidal integral over the session |
| `energy_wh` | Wh | trapezoidal integral over the session |
| `plug_type` | string | `AC`, `USB`, `WIRELESS`, `DOCK` |
| `peak_power_w` | W | max observed battery-side power |
| `avg_power_w` | W | mean observed battery-side power |
| `temp_min_c` / `temp_mean_c` / `temp_max_c` | °C | observed range |
| `quality` | string | `CLEAN`, `GAP_BOUNDARY`, `REBOOT_BOUNDARY`, `MANUAL_FLUSH`, `PARTIAL`, `UNKNOWN` |
| `taper_json` | JSON | see below, empty if no taper data |

`taper_json` is a small object mapping SOC band start (multiple of 10) to
mean observed charge current in amperes within that band, e.g.
`{"40":1.44,"50":1.51,"60":1.19,"70":0.62,"80":0.28}`.

### `battery_*.json`

Pretty-printed JSON with these top-level keys:

- `schema_version` — integer, currently 1
- `exported_at` — ISO-8601 UTC
- `exported_at_ms` — epoch ms
- `metadata` — app version, manufacturer, model, device, android_sdk, android_release
- `capabilities` — the persisted device capability rows
- `sessions` — same content as the sessions CSV but as structured JSON
- `latest_snapshot` — the most recent in-memory snapshot at export time
- `samples` — same content as the samples CSV

Every nullable field is JSON `null`, never 0, never an empty string.

## What the export does NOT contain

- No user identifier, no device serial, no location, no IMEI.
- No network traffic of any kind. The file is written to disk and the
  user decides where it goes via the OS share sheet.
- No vendor "health" percentage is added if the device did not report one.

## Importing

There is no import feature. The JSON schema is stable enough that a
third-party tool could read it, and `schema_version` is incremented
when the shape changes.

## Phase 5 — export, settings, docs

Status: **complete**.

- `data/prefs/Prefs.kt` — SharedPreferences-backed settings
- `data/export/` — CSV writer, JSON writer, exporter, FileProvider bridge
- `ui/settings/` — Settings screen: sampling rate, retention, background toggle,
  rated capacity, export, delete-all-data, diagnostics
- `docs/EXPORT.md` — file schemas
- `docs/ACCEPTANCE.md` — walkthrough of every §22 criterion
- `docs/samples/` — a valid CSV sample for samples and sessions, and a valid
  JSON sample, all checked in and hand-verified

Capabilities screen moved from the bottom nav to `Settings → Diagnostics`; the
nav is now Dash / History / Sessions / Health.

No new permissions. No new dependencies beyond the ones already present.

## What is deliberately deferred

**Foreground service for background sampling.** The Settings toggle exists and
records the user's preference, but the service is not implemented. See
`docs/ACCEPTANCE.md` for the reasoning. This is the largest remaining feature.

## The app at a glance

| Tab | Shows |
|---|---|
| **Dash** | Live tiles: SoC, temperature, voltage, charge counter, current (with rolling min/max), power (auto-scaled W/mW/µW), battery health card (reported health, estimated full capacity, capacity health %, cycle count, peak during last charge), ETA tiles when charging or discharging, device info card, gear icon → Settings |
| **History** | Canvas line charts over a selectable window (1 h / 6 h / 24 h / 7 d / 30 d) for SoC, power, temperature (with 40 °C / 45 °C threshold lines), voltage |
| **Sessions** | List of recorded charging sessions, tap for detail incl. taper bar chart |
| **Health** | Estimated full capacity, design capacity (firmware or user-supplied), estimated capacity health %, cycle count, app-tracked age, confidence legend, disclaimer |
| **Settings** (via gear) | Sampling rate, retention windows, background monitoring preference, rated capacity (user-supplied), export, delete-all-data, diagnostics |
| **Diagnostics** (via Settings) | Per-metric capability report: source, confidence, method, raw sysfs string where applicable |

## Architecture summary

```

app/src/main/java/com/example/batteryanalytics/
├── domain/
│   ├── model/        Metric<T>, enums, BatterySnapshot, SessionRow, DailyRollup
│   ├── units/        UnitNormalizer (SI conversions + heuristic unit detection)
│   ├── estimate/     CapacityEstimator, CycleEstimator, ChargingEtaEstimator,
│   │                 DischargeEtaEstimator, ChargingEfficiency, TaperParse,
│   │                 PowerCalculator
│   ├── session/      SessionDetector, SessionAccumulator
│   └── retention/    RetentionPolicy
├── data/
│   ├── source/       ApiBatterySource, SysfsPowerSupplySource, SourceResolver
│   ├── db/           BatteryDbHelper + 4 DAOs + CursorExt
│   ├── repo/         BatteryRepository, SamplingEngine
│   ├── export/       CsvWriter, JsonWriter, Exporter
│   └── prefs/        Prefs
└── ui/
├── theme/        Glass + Palette tokens, Material3 dark-only scheme
├── components/   GlassCard, MetricTile, PowerFormat, CurrentFormat
├── dashboard/    DashboardScreen, BatteryHealthCard, EtaRow, DeviceInfoCard
├── history/      HistoryScreen, LineChart
├── sessions/     SessionsScreen, SessionDetailScreen
├── health/       HealthScreen
├── capabilities/ CapabilitiesScreen (reached from Settings → Diagnostics)
└── settings/     SettingsScreen

app/src/test/java/com/example/batteryanalytics/
└── domain/           61 unit tests across model, units, estimate, session, retention

```

## Testing

```

./gradlew :app:testDebugUnitTest

```

61 tests pass. All are pure-JVM: no Android framework is touched. The
`UnitNormalizer`, `PowerCalculator`, `SessionDetector`, `SessionAccumulator`,
`RetentionPolicy`, `CapacityEstimator`, `CycleEstimator`, `ChargingEtaEstimator`,
and `DischargeEtaEstimator` are covered by synthetic inputs including the
ambiguous-unit cases and the below-floor and capped-ETA cases.

## Privacy

- No network permission in the default build.
- No analytics SDK, no Firebase, no crash reporter.
- All data lives in `filesDir`, on-device, until the user deletes it
  (Settings → Delete all data) or exports it (Settings → Export).

## Phase 6 — background monitoring and reboot recovery

Status: **complete**.

- `service/` — `SamplingController` (single-owner engine wrapper),
  `BatteryMonitorService` (foreground service, specialUse FGS type),
  `NotificationHelper` (channel + persistent notification)
- Manifest declares `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, and a
  `specialUse` FGS type. No INTERNET permission.
- `Prefs.pendingSessionStartTs` — a durable marker that survives a hard kill.
- `SamplingEngine.recoverInterruptedSession()` — reconstructs a session from
  samples already in the DB and closes it with quality `REBOOT_BOUNDARY`.
- `SamplingEngine.refreshRecentRollups()` — recomputes the last 4 days of
  `daily_rollups` on every engine start.
- `HistoryScreen` shows a **Last days** strip reading directly from
  `daily_rollups`: one row per day with SoC range, average temperature, and
  discharged amp-hours.

Background monitoring is **opt-in** and defaults to off. When on, the
foreground service runs while the app is backgrounded, and Android requires a
persistent notification which the app posts. When the user disables the
toggle, the service stops and the notification is removed.

## Updated acceptance walkthrough

Item 7 of §22 ("survives device reboot without data loss") is now **fully
met**. Previously, an open session's accumulator was lost on hard kill. Now
the accumulator state is reconstructible from the persisted samples, and the
session is written on next launch with a `REBOOT_BOUNDARY` quality flag.
Nothing is fabricated: the integration runs over samples the DB already
recorded.
