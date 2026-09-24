# Capabilities detection

The app never assumes a metric is available. Every reading is produced by
attempting the sources below, in priority order, and either returning a value
with a source label or returning `UNAVAILABLE`.

## Sources

### ApiBatterySource
- `BatteryManager.getIntProperty(...)` for CAPACITY, CHARGE_COUNTER, CURRENT_NOW,
  CURRENT_AVERAGE. `Int.MIN_VALUE` => null.
- `BatteryManager.getLongProperty(ENERGY_COUNTER)`. `Long.MIN_VALUE` => null.
- Sticky `ACTION_BATTERY_CHANGED` broadcast for EXTRA_VOLTAGE (mV),
  EXTRA_TEMPERATURE (0.1 °C), EXTRA_STATUS, EXTRA_HEALTH, EXTRA_PLUGGED,
  EXTRA_TECHNOLOGY, and (API 34+) EXTRA_CYCLE_COUNT.

Unit guarantees from the API:
| Field | Unit |
|---|---|
| CAPACITY | percent |
| CHARGE_COUNTER | microampere-hours |
| CURRENT_NOW | microamperes |
| CURRENT_AVERAGE | microamperes |
| ENERGY_COUNTER | nanowatt-hours |
| EXTRA_VOLTAGE | millivolts |
| EXTRA_TEMPERATURE | tenths of °C |
| EXTRA_CYCLE_COUNT | count (API 34+, may be 0 or -1 if unsupported) |

API values are labelled `source = API`, `confidence = HIGH`.

### SysfsPowerSupplySource
Enumerates **every** node under `/sys/class/power_supply/`, does not assume
`battery/` exists. For each node, reads `type` and, if type is `Battery` or
`UPS`, reads these files if readable:


Raw first-line content is preserved and surfaced to the UI for long-press.

Unit detection: see `UnitNormalizer` and `docs/METHODS.md`. Sysfs values are
labelled `source = SYSFS`. Confidence is HIGH only when a cross-check against
the API value pinned the unit; otherwise MEDIUM (magnitude heuristic) or LOW
(no heuristic matched).

Read failures (permission denied, missing file, non-numeric content) return
null. Never zero.

## Priority

| Metric | Preferred source | Fallback |
|---|---|---|
| SoC | API CAPACITY | sysfs `capacity` |
| Voltage | API EXTRA_VOLTAGE | sysfs `voltage_now` |
| Current now | API CURRENT_NOW | sysfs `current_now` |
| Current avg | API CURRENT_AVERAGE | sysfs `current_avg` |
| Temperature | API EXTRA_TEMPERATURE | sysfs `temp` |
| Charge counter | API CHARGE_COUNTER | sysfs `charge_counter` |
| Charge full | sysfs `charge_full` | (none) |
| Charge full design | sysfs `charge_full_design` | (none) |
| Energy counter | API ENERGY_COUNTER | sysfs `energy_counter` |
| Energy full | sysfs `energy_full` | (none) |
| Energy full design | sysfs `energy_full_design` | (none) |
| Cycle count | API EXTRA_CYCLE_COUNT (34+) | sysfs `cycle_count` |
| Status / Health / Plugged / Technology | API | sysfs `status` / `health` / (n/a) / `technology` |
| Power | calculated V x I | (none) |

## What "Not available on this device" means

The row was probed against both sources and neither returned a value. It is
not displayed as `0`, `--`, or blank, because none of those are honest. It is
the literal string the spec requires.
