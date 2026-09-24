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
