package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.ChargeStatus
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.Source
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Time remaining while discharging. Estimates minutes to [targetSoc], default 5.
 *
 * The target is 5 % rather than 0 % because phones shut down above 0 %. A
 * "time to 0 %" number would be a lie by design.
 *
 * Inputs are current_avg (a smoothed measure) and estimated full capacity.
 * A raw current_now value is too noisy for a multi-hour extrapolation.
 */
object DischargeEtaEstimator {

    data class Result(
        val targetPct: Int,
        val minutes: Int?,
        val source: Source,
        val confidence: Confidence,
        val method: String
    ) {
        val available: Boolean get() = minutes != null
    }

    fun estimate(
        soc: Metric<Int>,
        status: Metric<ChargeStatus>,
        currentAvgA: Metric<Double>,
        estimatedFullAh: Metric<Double>,
        targetPct: Int = 5
    ): Result {
        if (status.value != ChargeStatus.DISCHARGING) {
            return Result(targetPct, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                "not discharging")
        }
        val socVal = soc.value
            ?: return Result(targetPct, null, Source.UNAVAILABLE,
                Confidence.UNAVAILABLE, "state of charge unavailable")
        val full = estimatedFullAh.value
            ?: return Result(targetPct, null, Source.UNAVAILABLE,
                Confidence.UNAVAILABLE, "estimated full capacity unavailable")
        val current = currentAvgA.value
            ?: return Result(targetPct, null, Source.UNAVAILABLE,
                Confidence.UNAVAILABLE, "average current unavailable")

        if (socVal <= targetPct) {
            return Result(targetPct, 0, Source.CALCULATED, Confidence.HIGH,
                "at or below target")
        }
        val magnitude = abs(current)
        if (magnitude < 1e-6) {
            return Result(targetPct, null, Source.UNAVAILABLE,
                Confidence.UNAVAILABLE, "average current too small to extrapolate")
        }
        val remainingAh = full * (socVal - targetPct) / 100.0
        val hours = remainingAh / magnitude
        return Result(
            targetPct,
            (hours * 60.0).roundToInt(),
            Source.ESTIMATED,
            Confidence.MEDIUM,
            "remaining_ah / |current_avg|; assumes average discharge rate holds"
        )
    }
}
