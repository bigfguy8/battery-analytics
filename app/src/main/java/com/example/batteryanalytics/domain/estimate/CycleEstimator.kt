package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.Unit

/**
 * Cycle count. Only surfaces a number when it can defend it.
 *
 * Priority:
 *   1. sysfs cycle_count if > 0            -> HIGH
 *   2. API EXTRA_CYCLE_COUNT if > 0        -> HIGH
 *   3. Equivalent-full-cycles from history -> LOW  (needs ≥ 5 cycles of data)
 *   4. Otherwise                           -> UNAVAILABLE ("Insufficient data")
 *
 * The history-based estimate uses cumulative *charged* amp-hours divided by
 * design capacity. This is a defensible approximation of wear: over a long
 * period, the total charge passed through the cell in either direction
 * tracks closely with equivalent full cycles. It is labelled LOW and the
 * method string says so explicitly.
 *
 * Phase 3 records charging sessions only. If a future phase records discharge
 * sessions too, this estimator can be upgraded to use discharged amp-hours,
 * which is closer to the spec's preferred formula.
 */
object CycleEstimator {

    const val MIN_CYCLES_FOR_ESTIMATE = 5.0

    fun estimate(
        sysfsCycle: Metric<Int>,
        apiCycle: Metric<Int>,
        sessions: List<SessionRow>,
        designCapacityAh: Metric<Double>
    ): Metric<Int> {
        if (sysfsCycle.isAvailable && (sysfsCycle.value ?: 0) > 0) {
            return Metric(
                value = sysfsCycle.value,
                source = Source.SYSFS,
                confidence = Confidence.HIGH,
                method = sysfsCycle.method,
                unit = Unit.COUNT
            )
        }
        if (apiCycle.isAvailable && (apiCycle.value ?: 0) > 0) {
            return Metric(
                value = apiCycle.value,
                source = Source.API,
                confidence = Confidence.HIGH,
                method = apiCycle.method,
                unit = Unit.COUNT
            )
        }
        if (!designCapacityAh.isAvailable || (designCapacityAh.value ?: 0.0) <= 0.0) {
            return Metric.unavailable(
                "no cycle count exposed and no design capacity for a history-based estimate"
            )
        }
        val design = designCapacityAh.value!!
        val totalChargedAh = sessions.mapNotNull { it.chargeAh }.sum()
        val equivalentCycles = totalChargedAh / design
        if (equivalentCycles < MIN_CYCLES_FOR_ESTIMATE) {
            return Metric.unavailable(
                "Insufficient data: need \u2265 %.0f equivalent full cycles, have %.2f"
                    .format(MIN_CYCLES_FOR_ESTIMATE, equivalentCycles)
            )
        }
        val method = "sum(charge_ah) / design_capacity = %.2f / %.2f".format(
            totalChargedAh, design
        )
        return Metric(
            value = equivalentCycles.toInt(),
            source = Source.ESTIMATED,
            confidence = Confidence.LOW,
            method = method,
            unit = Unit.COUNT
        )
    }
}

private fun String.format(vararg args: Any): String =
    java.lang.String.format(java.util.Locale.US, this, *args)
