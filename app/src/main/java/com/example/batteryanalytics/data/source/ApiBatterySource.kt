package com.example.batteryanalytics.data.source

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

/**
 * Wraps BatteryManager and the sticky ACTION_BATTERY_CHANGED broadcast.
 * Every field is nullable. A missing field is null, NEVER zero.
 *
 * Unit notes:
 *  - BATTERY_PROPERTY_CAPACITY           -> percent
 *  - BATTERY_PROPERTY_CHARGE_COUNTER     -> microampere-hours (Int)
 *  - BATTERY_PROPERTY_CURRENT_NOW        -> microamperes (Int)
 *  - BATTERY_PROPERTY_CURRENT_AVERAGE    -> microamperes (Int)
 *  - BATTERY_PROPERTY_ENERGY_COUNTER     -> nanowatt-hours (Long)
 *  - EXTRA_CYCLE_COUNT                   -> count (Int, API 34+, from the broadcast)
 *  - EXTRA_VOLTAGE                       -> millivolts (Int)
 *  - EXTRA_TEMPERATURE                   -> tenths of degrees Celsius (Int)
 *
 * NOTE: BATTERY_PROPERTY_CYCLE_COUNT does NOT exist in the public SDK. The only
 * public cycle-count source is the EXTRA_CYCLE_COUNT extra on ACTION_BATTERY_CHANGED,
 * introduced in API 34.
 */
class ApiBatterySource(private val context: Context) {

    data class Readings(
        val socPercent: Int?,
        val chargeCounterUah: Long?,
        val currentNowUa: Int?,
        val currentAvgUa: Int?,
        val energyCounterNwh: Long?,
        val cycleCount: Int?,
        val voltageMv: Int?,
        val temperatureTenthsC: Int?,
        val statusCode: Int?,
        val healthCode: Int?,
        val plugCode: Int?,
        val technology: String?
    )

    fun read(): Readings {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        fun intProp(prop: Int): Int? {
            val v = bm?.getIntProperty(prop) ?: return null
            return if (v == Int.MIN_VALUE) null else v
        }
        fun longProp(prop: Int): Long? {
            val v = bm?.getLongProperty(prop) ?: return null
            return if (v == Long.MIN_VALUE) null else v
        }

        // Read the sticky broadcast first: cycle count comes from it, not from getIntProperty.
        val intent: Intent? = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Throwable) { null }

        val soc = intProp(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargeCounter = intProp(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.toLong()
        val currentNow = intProp(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAvg = intProp(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val energyCounter = longProp(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)

        // API 34+ only. AOSP returns 0 both when the device is new and when the field
        // is unsupported; -1 (our default) means the extra was not present. We keep
        // both 0 and >0 here and let SourceResolver label the ambiguity.
        val cycleCount: Int? = if (Build.VERSION.SDK_INT >= 34) {
            val v = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            if (v < 0) null else v
        } else null

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
            ?.takeIf { it > 0 }
        val tempTenthsC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
        val statusCode = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            ?.takeIf { it > 0 }
        val healthCode = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            ?.takeIf { it > 0 }
        val plugCode = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            ?.takeIf { it >= 0 }
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() }

        return Readings(
            socPercent = soc,
            chargeCounterUah = chargeCounter,
            currentNowUa = currentNow,
            currentAvgUa = currentAvg,
            energyCounterNwh = energyCounter,
            cycleCount = cycleCount,
            voltageMv = voltageMv,
            temperatureTenthsC = tempTenthsC,
            statusCode = statusCode,
            healthCode = healthCode,
            plugCode = plugCode,
            technology = technology
        )
    }
}
