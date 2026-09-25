package com.example.batteryanalytics.ui.components

import java.util.Locale

/**
 * Current formatter. Milliamps for anything below 1 A, amps above.
 * Presentation choice only, not a change in what we measure.
 */
object CurrentFormat {

    fun format(a: Double?): String {
        if (a == null) return "Not available on this device"
        val abs = kotlin.math.abs(a)
        return if (abs < 1.0) {
            String.format(Locale.US, "%.1f mA", a * 1000.0)
        } else {
            String.format(Locale.US, "%.3f A", a)
        }
    }

    /**
     * Compact min/max pair with one unit at the end, chosen from the larger
     * magnitude. Example outputs:
     *   "-1.20 / -0.20 mA"
     *   "0.05 / 3.44 A"
     */
    fun formatRange(min: Double, max: Double): String {
        val largest = maxOf(kotlin.math.abs(min), kotlin.math.abs(max))
        val (factor, unit) = if (largest >= 1.0) 1.0 to "A" else 1_000.0 to "mA"
        return "${scaled(min * factor)} / ${scaled(max * factor)} $unit"
    }

    private fun scaled(v: Double): String {
        val a = kotlin.math.abs(v)
        return when {
            a >= 100.0 -> String.format(Locale.US, "%.0f", v)
            a >= 10.0  -> String.format(Locale.US, "%.1f", v)
            else       -> String.format(Locale.US, "%.2f", v)
        }
    }
}
