package com.example.batteryanalytics.ui.components

import java.util.Locale

/**
 * Current formatter. Milliamps for anything below 1 A, amps above.
 * This is a presentation choice, not a change in what we measure.
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
}
