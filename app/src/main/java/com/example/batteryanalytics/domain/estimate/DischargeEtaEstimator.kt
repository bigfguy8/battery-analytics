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

    /**
     * Minimum |current| trusted for a discharge ETA, in amperes. 20 mA is
     * about the point below which the phone is effectively on standby: the
     * cell's self-discharge, the RTC, and radios in deep sleep dominate, and
     * extrapolating a multi-day ETA from a momentary reading produces garbage
     * (we observed a real 7071-hour reading at -1.0 mA).
     */
    const val MIN_TRUSTED_CURRENT_A: Double = 0.02

    /**
     * Maximum ETA reported, in minutes. 7 days is generous: below that
     * threshold the "time remaining" is still a meaningful statement.
     * Anything longer is not actionable, and the underlying reading is not
     * representative of real usage.
     */
    const val MAX_REASONABLE_ETA_MIN: Int = 7 * 24 * 60

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
        if (magnitude < MIN_TRUSTED_CURRENT_A) {
            val reason = if (magnitude < 1e-6)
                "no measurable discharge current"
            else
                "measured %.1f mA is below the %.0f mA floor; phone is on standby"
                    .format(magnitude * 1000.0, MIN_TRUSTED_CURRENT_A * 1000.0)
            return Result(targetPct, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE, reason)
        }
        val remainingAh = full * (socVal - targetPct) / 100.0
        val hours = remainingAh / magnitude
        val minutes = (hours * 60.0).roundToInt()
        if (minutes > MAX_REASONABLE_ETA_MIN) {
            return Result(
                targetPct, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                "computed ETA %.0f h exceeds %.0f h cap; reading not representative"
                    .format(minutes / 60.0, MAX_REASONABLE_ETA_MIN / 60.0)
            )
        }
        return Result(
            targetPct,
            minutes,
            Source.ESTIMATED,
            Confidence.MEDIUM,
            "remaining_ah / |current_avg|; assumes average discharge rate holds"
        )
    }
}

private fun String.format(vararg args: Any): String =
    java.lang.String.format(java.util.Locale.US, this, *args)
