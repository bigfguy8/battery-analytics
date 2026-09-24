package com.example.batteryanalytics.domain.units

import com.example.batteryanalytics.domain.model.Confidence
import kotlin.math.abs

/**
 * All conversions to SI units. Every heuristic that guesses a unit *also* returns a
 * Confidence so the caller can propagate it into the Metric. Never silently assume.
 *
 * See docs/METHODS.md for the reasoning behind each heuristic and its failure modes.
 */
object UnitNormalizer {

    fun currentToAmps(raw: Double, unit: CurrentUnit): Double = raw * unit.toAmps
    fun voltageToVolts(raw: Double, unit: VoltageUnit): Double = raw * unit.toVolts
    fun chargeToAmpHours(raw: Double, unit: ChargeUnit): Double = raw * unit.toAmpHours
    fun energyToWattHours(raw: Double, unit: EnergyUnit): Double = raw * unit.toWattHours

    fun tempToCelsius(raw: Double, unit: TempUnit): Double = when (unit) {
        TempUnit.CELSIUS -> raw
        TempUnit.TENTHS_CELSIUS -> raw / 10.0
        TempUnit.FAHRENHEIT -> (raw - 32.0) * 5.0 / 9.0
    }

    /**
     * Guess the unit of a raw sysfs current value.
     *
     * If [apiCrossCheckAmps] is non-null and non-zero, we compare the sysfs raw value
     * against the same metric read from BatteryManager (always µA on the API side).
     * A ratio near 1 / 1e3 / 1e6 pins the sysfs unit and yields HIGH confidence.
     * Otherwise we fall back to a magnitude heuristic at MEDIUM confidence.
     *
     * Failure modes: devices that report in a non-ratio'd unit (rare, non-standard)
     * fall through to LOW; the caller should then prefer the API value.
     */
    fun guessCurrentUnit(rawValue: Double, apiCrossCheckAmps: Double?): Pair<CurrentUnit, Confidence> {
        if (apiCrossCheckAmps != null && apiCrossCheckAmps != 0.0 && !apiCrossCheckAmps.isNaN()) {
            val ratio = rawValue / apiCrossCheckAmps
            return when {
                ratio in 0.9..1.1 -> CurrentUnit.AMPS to Confidence.HIGH
                ratio in 900.0..1100.0 -> CurrentUnit.MILLIAMPS to Confidence.HIGH
                ratio in 900_000.0..1_100_000.0 -> CurrentUnit.MICROAMPS to Confidence.HIGH
                else -> CurrentUnit.MICROAMPS to Confidence.LOW
            }
        }
        val a = abs(rawValue)
        return when {
            a < 20.0 -> CurrentUnit.AMPS to Confidence.MEDIUM
            a < 20_000.0 -> CurrentUnit.MILLIAMPS to Confidence.MEDIUM
            else -> CurrentUnit.MICROAMPS to Confidence.MEDIUM
        }
    }

    /** Same idea as [guessCurrentUnit], for voltage. */
    fun guessVoltageUnit(rawValue: Double, apiCrossCheckVolts: Double?): Pair<VoltageUnit, Confidence> {
        if (apiCrossCheckVolts != null && apiCrossCheckVolts != 0.0 && !apiCrossCheckVolts.isNaN()) {
            val ratio = rawValue / apiCrossCheckVolts
            return when {
                ratio in 0.9..1.1 -> VoltageUnit.VOLTS to Confidence.HIGH
                ratio in 900.0..1100.0 -> VoltageUnit.MILLIVOLTS to Confidence.HIGH
                ratio in 900_000.0..1_100_000.0 -> VoltageUnit.MICROVOLTS to Confidence.HIGH
                else -> VoltageUnit.MICROVOLTS to Confidence.LOW
            }
        }
        val a = abs(rawValue)
        return when {
            a < 10.0 -> VoltageUnit.VOLTS to Confidence.MEDIUM
            a < 10_000.0 -> VoltageUnit.MILLIVOLTS to Confidence.MEDIUM
            else -> VoltageUnit.MICROVOLTS to Confidence.MEDIUM
        }
    }

    /**
     * Guess the unit of a sysfs temperature.
     *
     * Android's own EXTRA_TEMPERATURE is always tenths of °C, so API reads are HIGH.
     * Sysfs is mixed. A phone battery operates roughly 15-50 °C. Values in [100, 1500]
     * are therefore far more likely to be tenths (10.0–150.0 °C) than degrees, and
     * values in [0, 100] are almost certainly degrees. Above 1500 is neither plausible
     * as °C nor as tenths, so we flag LOW.
     */
    fun guessTempUnit(rawValue: Double): Pair<TempUnit, Confidence> {
        val a = abs(rawValue)
        return when {
            a in 100.0..1500.0 -> TempUnit.TENTHS_CELSIUS to Confidence.MEDIUM
            a in 0.0..100.0 -> TempUnit.CELSIUS to Confidence.MEDIUM
            else -> TempUnit.CELSIUS to Confidence.LOW
        }
    }

    /**
     * Sysfs charge and energy values are typically µAh / µWh on Qualcomm, but can be
     * mAh / mWh. Detect by order of magnitude of a plausible phone battery:
     * 2000–8000 mAh = 2e6–8e6 µAh = 2000–8000 mAh.
     */
    fun guessChargeUnit(rawValue: Double): Pair<ChargeUnit, Confidence> {
        val a = abs(rawValue)
        return when {
            a in 0.5..15.0 -> ChargeUnit.AMP_HOURS to Confidence.MEDIUM
            a in 500.0..15_000.0 -> ChargeUnit.MILLIAMP_HOURS to Confidence.MEDIUM
            a in 500_000.0..15_000_000.0 -> ChargeUnit.MICROAMP_HOURS to Confidence.MEDIUM
            else -> ChargeUnit.MICROAMP_HOURS to Confidence.LOW
        }
    }

    fun guessEnergyUnit(rawValue: Double): Pair<EnergyUnit, Confidence> {
        val a = abs(rawValue)
        return when {
            a in 1.0..100.0 -> EnergyUnit.WATT_HOURS to Confidence.MEDIUM
            a in 1_000.0..100_000.0 -> EnergyUnit.MILLIWATT_HOURS to Confidence.MEDIUM
            a in 1_000_000.0..100_000_000.0 -> EnergyUnit.MICROWATT_HOURS to Confidence.MEDIUM
            a in 1_000_000_000.0..100_000_000_000.0 -> EnergyUnit.NANOWATT_HOURS to Confidence.MEDIUM
            else -> EnergyUnit.NANOWATT_HOURS to Confidence.LOW
        }
    }
}
