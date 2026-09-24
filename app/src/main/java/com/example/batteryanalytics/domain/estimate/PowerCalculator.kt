package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.ChargeStatus
import kotlin.math.abs

/**
 * Instantaneous power = V x I, with a documented sign convention:
 *
 *   Positive power  =  battery is charging
 *   Negative power  =  battery is discharging
 *
 * The sign of current_now in sysfs and in BATTERY_PROPERTY_CURRENT_NOW is
 * vendor-dependent. We therefore compute the magnitude |V x I| and then apply the
 * sign indicated by the reported charge status. That gives a stable, comparable
 * number across devices regardless of the underlying convention.
 *
 * This is BATTERY-SIDE power. It is NOT charger output power. Conversion losses
 * and the phone's own consumption mean battery-side power is always lower than
 * the wall-side figure. The UI repeats this caveat.
 */
object PowerCalculator {
    fun computePowerW(voltageV: Double, currentA: Double, status: ChargeStatus?): Double {
        val magnitude = abs(voltageV * currentA)
        return when (status) {
            ChargeStatus.CHARGING, ChargeStatus.FULL -> magnitude
            ChargeStatus.DISCHARGING -> -magnitude
            else -> voltageV * currentA
        }
    }
}
