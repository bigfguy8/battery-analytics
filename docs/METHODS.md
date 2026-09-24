# Methods — every value, its formula, and its failure modes

This document is normative. When code and docs disagree, one of them is a bug.

## Direct reads

Everything labelled `source = API` or `source = SYSFS` with `confidence = HIGH`
is a direct read of a documented field. The `method` string on the Metric
identifies the exact API constant or sysfs file path.

## Power — `P = V x I`

Sign convention: **positive = charging**, **negative = discharging**.

Vendor convention on `current_now` (both API and sysfs) is not standardized.
The magnitude of power is `|V x I|`. The sign is applied from the
`charge status` metric, not from the raw current sign.

- Charging or Full → `+ |V x I|`
- Discharging → `- |V x I|`
- Not charging / Unknown → raw `V x I` (preserves whatever sign is present)

Labelled `source = CALCULATED`, `confidence = MEDIUM`.

**This is battery-side power.** It is not charger output power. Wall-side
power is always greater, because of conversion losses in the charger and
because the phone's own consumption is drawn from the same rail. The UI
repeats this caveat.

Failure modes:
- If either V or I is UNAVAILABLE, power is UNAVAILABLE. Never zero.
- On devices where current sign disagrees with status, the magnitude is
  correct but the sign is authoritative from status. Rare, and tolerated.

## Sysfs unit detection — `UnitNormalizer`

Android and Linux disagree on units. Possibilities seen in the wild:
µA / mA / A, µV / mV / V, µAh / mAh / Ah, µWh / mWh / Wh / nWh, °C / tenths °C.

Where the API provides the same metric (current_now, voltage_now, temp,
charge_counter, energy_counter), the sysfs raw value is compared against the
API's SI value. A ratio near 1, 1e3, or 1e6 pins the unit and yields
`confidence = HIGH`.

Without a cross-check, magnitude heuristics are used and yield
`confidence = MEDIUM`:

| Domain | Range → guessed unit |
|---|---|
| current | |x| < 20 → A; |x| < 20000 → mA; else µA |
| voltage | |x| < 10 → V; |x| < 10000 → mV; else µV |
| temp | 100 ≤ |x| ≤ 1500 → 0.1 °C; |x| < 100 → °C; else LOW |
| charge | 0.5–15 → Ah; 500–15000 → mAh; 5e5–1.5e7 → µAh; else LOW |
| energy | 1–100 → Wh; 1e3–1e5 → mWh; 1e6–1e8 → µWh; 1e9–1e11 → nWh; else LOW |

Failure modes:
- A device reporting current_now in centi-amps (rare, non-standard) falls
  through to LOW. Caller prefers API value.
- Without a cross-check, a sysfs value of exactly 42 could be 42 A (absurd),
  42 mA (plausible), or 42 µA (plausible). Ambiguity is inherent; confidence
  reflects it.
- Temperature is the worst case: 325 could be 32.5 °C or 325 °C. The range
  check catches the common case; the LOW fallback catches the rest.

Every SYSFS metric carries its `rawString` so the UI can show the exact bytes
read. Users can verify guesses themselves.

## What this document does NOT yet cover

Phase 2 does not estimate anything beyond `P = V x I`. Capacity health,
equivalent cycles, charging-time ETA, and charging-efficiency are Phase 4
concerns and will be added here with their own sections, formulas, and failure
modes when they are implemented.

---

# Phase 3 — sessions, retention, and what is deliberately not claimed

## Session detection

A session opens when the sample stream transitions into
`status = CHARGING|FULL AND plug != NONE`. It closes on:

- status → `DISCHARGING|NOT_CHARGING` **and** plug → `NONE` (CLEAN close),
- a gap larger than `SessionDetector.DEFAULT_GAP_MS` (5 min) between two
  consecutive samples (GAP_BOUNDARY close, session end recorded at the
  previous sample's timestamp),
- an explicit `flush()` call on the engine (MANUAL_FLUSH close): used on
  `Activity.onStop` and on shutdown.

**Integrals use the trapezoidal rule** between consecutive ingested samples:
`delta = 0.5 * (x_prev + x_now) * dt_hours`. Charge in Ah and energy in Wh
are both computed this way. The first sample is ingested but not integrated.

**Taper curve** buckets samples by SOC band (`floor(soc/10)*10`) and stores
the mean observed *positive* current within each band as JSON. Bands with no
samples are absent from the JSON, not zero.

## Retention

- Full resolution: 14 days.
- Downsampled to 1 minute: 14–44 days. Downsampled row stores the *extrema*
  (min soc, min voltage, min current, min power, max temp), not means. This is
  a deliberate choice: Phase 4's capacity estimator only needs extrema.
- Older than 44 days: deleted from `telemetry_samples`. Sessions and daily
  rollups are kept indefinitely.

Downsampling uses one bucket per minute. The bucket's timestamp is floored
to the minute. Source and quality flags are OR-combined across the bucket.

## What is deliberately NOT claimed in Phase 3

**Sessions do not survive a hard kill.** If the process is killed by the OS
or the user (task swipe, low-memory kill) without `onStop` firing, an open
session's accumulated statistics are lost. Samples already written to the DB
survive. On next launch, the detector opens a fresh session on the first
charging sample. This is honest behavior; a `BOOT_COMPLETED` receiver and a
heartbeat file are Phase 5 concerns.

**The `id % stride = 0` downsample query is approximate.** It relies on
contiguous AUTOINCREMENT ids, which retention deletes can break. The effect
is that a strided window may sample slightly unevenly. Window functions
require API 30+; OFFSET is O(n) in SQLite. If Phase 4 needs statistically
even sampling, this query should be replaced with a proper ROW_NUMBER
windowed query gated on `Build.VERSION.SDK_INT >= 30` with the current
approach as fallback.

**The 5-minute gap threshold will need to be adaptive if background sampling
is ever added.** The foreground sampler ticks every 5 s, so a 5-minute gap
really does mean the process died. If Phase 5 adds opt-in background
sampling, OS throttling can stretch the sample interval to 10–15 minutes;
under the current threshold, every long charge would be fragmented into
~1-hour pieces tagged `GAP_BOUNDARY`. Before enabling background mode, the
threshold must become `max(5 min, 3 * observed sample interval)` or similar.

**Charging efficiency is still not computed.** Battery-side power is reported
and correctly labelled as battery-side. Wall-side power is not available on
this device (no input-side telemetry), so efficiency cannot be computed here
and is not shown.

---

# Phase 4 — estimation framework

Every estimator below obeys the confidence rules from §13:
`value / source / confidence / method`. Missing inputs yield UNAVAILABLE — never
zero, never a guess. All estimators are pure Kotlin and unit-tested in
`app/src/test/.../domain/estimate/`.

## Estimated full-charge capacity — `CapacityEstimator`

Three sources, in priority order.

**1. sysfs `charge_full`** — direct read, HIGH. Not present on many devices.

**2. Single-point extrapolation**:
Source = ESTIMATED. Confidence = MEDIUM if ≥ 5 such sessions, LOW if 1–4,
UNAVAILABLE if 0. The ΔSOC ≥ 30 filter excludes short top-up charges where
rounding in the SOC integer dominates.

## Estimated battery capacity health — `CapacityEstimator.healthPercent`

Source = CALCULATED, Confidence = LOW. Unavailable whenever design capacity is
not exposed — which is the case on the majority of Android phones, including
the reference device used to develop this app. We do **not** substitute a
vendor-reported "Good" string as a percentage.

## Cycle count — `CycleEstimator`

Priority:
1. sysfs `cycle_count` if > 0 — HIGH.
2. API `EXTRA_CYCLE_COUNT` if > 0 — HIGH.
3. History: `sum(charge_ah over clean sessions) / design_capacity_ah` — LOW,
   and only when the result is ≥ 5 equivalent full cycles. Otherwise
   UNAVAILABLE with an "Insufficient data" method string.

Failure mode: the API returns 0 both when the device is genuinely new and when
the field is not implemented. We treat any reported 0 as "unsupported or new,
cannot distinguish" and never claim it as a measurement.

## Charging-time ETA — `ChargingEtaEstimator`

Two modes, chosen automatically.

**Banded integration** (MEDIUM) — used when at least one clean taper curve from
recent sessions is available AND the current measured rate is > 0:
This models the expected taper above 80 %. Missing bands silently fall back to
the current rate; the method string says which bands were covered.

**Naive linear** (LOW) — used otherwise:
Source = ESTIMATED, Confidence = MEDIUM. Uses `current_avg` (smoothed) rather
than `current_now` (noisy) because the extrapolation span is hours. A |current|
below 1 µA yields UNAVAILABLE — we refuse to divide by effectively zero.

## Charging efficiency

Not computed. Android does not expose input-side power on most devices, so
`battery_side / input_side` has no inputs. `ChargingEfficiency.estimate()`
returns UNAVAILABLE with the reason and does not silently pass off an internal
consistency check as an efficiency figure.

## Age

`now − min(first_seen_ts) over device_capabilities`. Source = CALCULATED,
Confidence = HIGH. `first_seen_ts` is preserved on capability upsert (it is
never overwritten), so this measures "how long has the app been observing
this device", which is the only age we can honestly claim. A future phase
could refine this using full-cycle observations, at the cost of more
complexity.

---

# Reserved enum values

A few `enum` members exist in the code but are never assigned. They are
reserved for cases the app does not yet produce, and are documented here so
a future reader does not spend time hunting for their writer.

## `SessionQuality`

- `CLEAN` — assigned on normal plug/unplug close.
- `GAP_BOUNDARY` — assigned when a session is closed because the next sample
  arrived more than 5 minutes after the previous one.
- `MANUAL_FLUSH` — assigned on `Activity.onStop` and on `SamplingEngine.shutdown`.
- `REBOOT_BOUNDARY` — assigned by `SamplingEngine.recoverInterruptedSession()`
  when the pending-session marker was still set at launch.
- `PARTIAL` — **reserved, never assigned.** It would describe a session
  recovered from a few samples with most fields null. The current recovery
  path produces a `REBOOT_BOUNDARY` row instead, which is more specific.
- `UNKNOWN` — never assigned by our own code; used as a fallback when a value
  read back from the DB does not match any known member (i.e. after a manual
  edit or a future schema migration).

## `QualityFlags`

- `NONE` — no flags.
- `GAP_BEFORE` — set on the accumulator when a session opens immediately after
  a > 5-minute silence. Currently written to the session row's flags but not
  read by any UI. Reserved for a future session-detail refinement.
- `GAP_AFTER` — set on a session at gap-close time.
- `SIGN_INCONSISTENT` — set when current and status disagree in sign.
- `PARTIAL_DATA` — set when SoC or voltage is missing on a sample.
- `COUNTER_RESET` — **reserved, never assigned.** Would describe a fuel-gauge
  counter that reset mid-session. Not currently detected.

If any of these reserved values is ever needed, this document is the place
to change first.
