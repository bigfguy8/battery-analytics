package com.example.batteryanalytics.ui.components

import java.util.Locale

/**
 * Power formatter that auto-scales the unit to the magnitude.
 *
 * Rationale: battery-side power spans a huge range. Idle discharge with the
 * screen on is a few milliwatts; fast charge on a phone peaks at 15-25 W.
 * Showing both with the same "%.2f W" format makes the idle values look like
 * bugs. This picks the unit that keeps the significant digits visible.
 *
 * This is formatting only. It does not change what we measure.
 *
 * Charger-side power is not available to us and is not shown. Battery-side
 * power is always lower than wall-side power because of conversion losses and
 * the phone's own consumption.
 */
object PowerFormat {
    fun format(w: Double?): String {
        if (w == null) return "Not available on this device"
        val a = kotlin.math.abs(w)
        return when {
            a >= 1.0       -> String.format(Locale.US, "%.2f W", w)
            a >= 0.001     -> String.format(Locale.US, "%.1f mW", w * 1_000.0)
            a >= 0.000_001 -> String.format(Locale.US, "%.1f \u00B5W", w * 1_000_000.0)
            else           -> "0 W"
        }
    }

    /** Same scaling, but for the axis labels on charts. */
    fun shortForm(w: Double): String {
        val a = kotlin.math.abs(w)
        return when {
            a >= 1.0       -> String.format(Locale.US, "%.1f W", w)
            a >= 0.001     -> String.format(Locale.US, "%.0f mW", w * 1_000.0)
            else           -> String.format(Locale.US, "%.0f \u00B5W", w * 1_000_000.0)
        }
    }
}
