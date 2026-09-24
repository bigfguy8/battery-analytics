package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.Unit
import java.util.Locale

/**
 * Capacity estimation. Three sources, priority-ordered:
 *
 *   1. sysfs charge_full              (direct read, HIGH)
 *   2. charge_counter / (SOC/100)     (single-point extrapolation, MEDIUM/LOW)
 *   3. Mean over clean sessions       (history-based, MEDIUM if ≥5, LOW if 1-4)
 *
 * Never fabricates. A value is produced only when its inputs are available.
 */
object CapacityEstimator {

    /**
     * Estimate current full-charge capacity in amp-hours from the present
     * charge_counter and SOC. Same math as the earlier seed, with the same
     * extrapolation-factor-based confidence rules.
     */
    fun estimateFullCapacityAh(
        chargeCounterAh: Metric<Double>,
        soc: Metric<Int>
    ): Metric<Double> {
        val counter = chargeCounterAh.value
        val socVal = soc.value
        if (counter == null || socVal == null) {
            return Metric.unavailable("needs both charge_counter and SOC")
        }
        if (counter <= 0.0) {
            return Metric.unavailable("charge_counter is zero or negative")
        }
        if (socVal < 10) {
            return Metric.unavailable("SOC below 10% — extrapolation factor > 10x")
        }
        val factor = 100.0 / socVal
        val estimated = counter * factor
        val confidence = if (socVal >= 25) Confidence.MEDIUM else Confidence.LOW
        val method = String.format(
            Locale.US,
            "charge_counter / (SOC/100) = %.3f / %.2f  (\u00D7%.1f extrapolation)",
            counter, socVal / 100.0, factor
        )
        return Metric(
            value = estimated,
            source = Source.CALCULATED,
            confidence = confidence,
            method = method,
            unit = Unit.AMPHOUR
        )
    }

    /**
     * Estimate full-charge capacity from accumulated clean charging sessions.
     * Each session that gained ≥ 30 % SOC and closed CLEAN contributes one
     * observation: charge_added_ah / (ΔSOC/100).
     */
    fun estimateFullCapacityFromSessions(sessions: List<SessionRow>): Metric<Double> {
        val clean = sessions.filter { s ->
            s.quality == SessionQuality.CLEAN &&
            s.chargeAh != null && s.chargeAh!! > 0.0 &&
            s.socStart != null && s.socEnd != null &&
            (s.socEnd!! - s.socStart!!) >= 30
        }
        if (clean.isEmpty()) {
            return Metric.unavailable(
                "no clean charging sessions with \u0394SOC \u2265 30%"
            )
        }
        val estimates = clean.map { s ->
            s.chargeAh!! / ((s.socEnd!! - s.socStart!!) / 100.0)
        }
        val mean = estimates.average()
        val confidence = when {
            clean.size >= 5 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val method = "mean(charge_ah / \u0394SOC) over ${clean.size} clean session${if (clean.size == 1) "" else "s"}"
        return Metric(mean, Source.ESTIMATED, confidence, method, Unit.AMPHOUR)
    }

    /**
     * Capacity health = estimated full / design capacity × 100.
     * Unavailable if either operand is missing. Never guessed.
     */
    fun healthPercent(
        estimatedFullAh: Metric<Double>,
        designCapacityAh: Metric<Double>
    ): Metric<Double> {
        if (!estimatedFullAh.isAvailable) {
            return Metric.unavailable("needs estimated full capacity")
        }
        if (!designCapacityAh.isAvailable) {
            return Metric.unavailable("device does not expose design capacity")
        }
        val design = designCapacityAh.value!!
        if (design <= 0.0) {
            return Metric.unavailable("design capacity is zero")
        }
        val pct = estimatedFullAh.value!! / design * 100.0
        return Metric(
            value = pct,
            source = Source.CALCULATED,
            confidence = Confidence.LOW,
            method = "estimated_full / design_capacity \u00D7 100",
            unit = Unit.PERCENT
        )
    }
}
