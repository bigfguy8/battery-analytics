package com.example.batteryanalytics.domain.model

import java.util.Locale

/**
 * A single observed or derived value.
 *
 * Invariant: if [value] is null, then [source] is UNAVAILABLE and [confidence] is UNAVAILABLE.
 * Invariant: [method] is always a short human-readable string naming the actual source
 *            (file path, API constant, or formula). No fabricated numbers, ever.
 */
data class Metric<T>(
    val value: T?,
    val source: Source,
    val confidence: Confidence,
    val method: String,
    val unit: Unit,
    /** For SYSFS-sourced metrics: the raw file content as read. UI shows this on long-press. */
    val rawString: String? = null
) {
    val isAvailable: Boolean
        get() = value != null && confidence != Confidence.UNAVAILABLE

    /** Human-readable, unit-suffixed. Returns the spec'd "Not available on this device"
     *  string (not "0", not "--") when the metric is missing. */
    fun display(): String {
        if (!isAvailable) return "Not available on this device"
        val v = value!!
        val formatted = when (v) {
            is Double -> formatDouble(v)
            is Float -> formatDouble(v.toDouble())
            is Labelled -> v.label
            else -> v.toString()
        }
        return if (unit.label.isEmpty()) formatted else "$formatted ${unit.label}"
    }

    companion object {
        private fun formatDouble(d: Double): String {
            if (d.isNaN() || d.isInfinite()) return d.toString()
            val rounded = when {
                kotlin.math.abs(d) >= 100.0 -> String.format(Locale.US, "%.1f", d)
                kotlin.math.abs(d) >= 1.0 -> String.format(Locale.US, "%.3f", d)
                else -> String.format(Locale.US, "%.4f", d)
            }
            return rounded.trimEnd('0').trimEnd('.').ifEmpty { "0" }
        }

        fun <T> unavailable(reason: String): Metric<T> = Metric(
            value = null,
            source = Source.UNAVAILABLE,
            confidence = Confidence.UNAVAILABLE,
            method = reason,
            unit = Unit.NONE,
            rawString = null
        )
    }
}
