package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.ChargeStatus
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.Unit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Charging-time estimation. Two modes:
 *
 *   1. Banded integration over historical taper curves (MEDIUM).
 *      Each 10-% SOC band uses the mean charge current observed for that band
 *      in recent clean sessions. Missing bands fall back to the current
 *      measured rate. This models the expected taper above 80 %.
 *
 *   2. Naive linear extrapolation (LOW).
 *      Used when there are no usable taper curves: remaining_ah / current.
 *      The method string says "naive extrapolation; taper not modelled" so
 *      the user knows the value will get worse near full charge.
 *
 * In both modes, unavailable inputs yield UNAVAILABLE — never a number.
 */
object ChargingEtaEstimator {

    data class EtaPoint(
        val targetPct: Int,
        val minutes: Int?,
        val source: Source,
        val confidence: Confidence,
        val method: String
    ) {
        val available: Boolean get() = minutes != null
    }

    /**
     * Minimum |current| that will be trusted for an ETA, in amperes.
     *
     * 50 mA is about the smallest current that produces a defensible multi-hour
     * extrapolation. Below this, small instantaneous fluctuations in the reading
     * dominate and the ETA explodes: on one reference device we observed 2.9 mA
     * during fast charging with the screen on, which extrapolates to hundreds of
     * hours. That is a real reading of net battery current, not a bug in the
     * source, but it is useless as a basis for time-to-full.
     *
     * Below the floor, the ETA is reported UNAVAILABLE with a method string
     * naming the measured rate. This is deliberate: §13 of the spec forbids
     * presenting an estimate we cannot defend.
     */
    const val MIN_TRUSTED_CURRENT_A: Double = 0.05

    /** Maximum ETA (in minutes) that will be reported without flagging. Above
     *  this, the extrapolation is treated as unreliable and returns UNAVAILABLE.
     *  A phone takes at most a few hours to charge from any SOC. */
    const val MAX_REASONABLE_ETA_MIN: Int = 24 * 60

    fun estimate(
        soc: Metric<Int>,
        status: Metric<ChargeStatus>,
        currentA: Metric<Double>,
        currentAvgA: Metric<Double>,
        estimatedFullAh: Metric<Double>,
        recentSessions: List<SessionRow>,
        targets: List<Int> = listOf(80, 90, 100)
    ): List<EtaPoint> {
        val charging = status.value == ChargeStatus.CHARGING ||
                       status.value == ChargeStatus.FULL
        if (!charging) {
            return targets.map {
                EtaPoint(it, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                    "not charging")
            }
        }
        val socVal = soc.value
        if (socVal == null) {
            return targets.map {
                EtaPoint(it, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                    "state of charge unavailable")
            }
        }
        val full = estimatedFullAh.value
        if (full == null || full <= 0.0) {
            return targets.map {
                EtaPoint(it, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                    "estimated full capacity unavailable")
            }
        }

        val taper = TaperParse.merge(recentSessions)

        // Prefer the larger magnitude of instantaneous and average current.
        // current_avg smooths momentary lulls; current_now catches the case
        // where current_avg has not yet caught up after plug-in.
        val nowMag = currentA.value?.let { kotlin.math.abs(it) } ?: 0.0
        val avgMag = currentAvgA.value?.let { kotlin.math.abs(it) } ?: 0.0
        val chosenMag = maxOf(nowMag, avgMag)
        val chosenSource = when {
            chosenMag == 0.0 -> "no current reading"
            chosenMag == nowMag -> "current_now"
            else -> "current_avg"
        }
        val currentRate: Double? = if (chosenMag >= MIN_TRUSTED_CURRENT_A) chosenMag else null

        // If charging and below the floor: report UNAVAILABLE for every target
        // with a specific reason, so the user sees what we measured.
        if (currentRate == null) {
            val reason = if (chosenMag == 0.0)
                "no usable current reading"
            else
                "measured current %.1f mA is below the %.0f mA floor for a trusted ETA"
                    .format(chosenMag * 1000.0, MIN_TRUSTED_CURRENT_A * 1000.0)
            return targets.map {
                EtaPoint(it, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE, reason)
            }
        }

        val useBanded = taper.isNotEmpty()

        return targets.map { target ->
            if (socVal >= target) {
                EtaPoint(target, 0, Source.CALCULATED, Confidence.HIGH,
                    "already at or above target")
            } else if (useBanded) {
                val minutes = bandedMinutes(socVal, target, full, taper, currentRate)
                if (minutes == null) {
                    EtaPoint(target, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                        "no rate available for this SOC range")
                } else if (minutes > MAX_REASONABLE_ETA_MIN) {
                    EtaPoint(target, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                        "computed ETA %.0f h exceeds %.0f h cap; measurement unreliable"
                            .format(minutes / 60.0, MAX_REASONABLE_ETA_MIN / 60.0))
                } else {
                    val bands = "${(socVal / 10 * 10)}..${(target - 1) / 10 * 10}"
                    EtaPoint(target, minutes, Source.ESTIMATED, Confidence.MEDIUM,
                        "banded taper integration over SOC $bands, rate from $chosenSource")
                }
            } else {
                val remainingAh = full * (target - socVal) / 100.0
                val hours = remainingAh / currentRate
                val minutes = (hours * 60.0).roundToInt()
                if (minutes > MAX_REASONABLE_ETA_MIN) {
                    EtaPoint(target, null, Source.UNAVAILABLE, Confidence.UNAVAILABLE,
                        "computed ETA %.0f h exceeds %.0f h cap; measurement unreliable"
                            .format(minutes / 60.0, MAX_REASONABLE_ETA_MIN / 60.0))
                } else {
                    EtaPoint(target, minutes,
                        Source.ESTIMATED, Confidence.LOW,
                        "naive extrapolation at %.3f A from $chosenSource; taper not modelled"
                            .format(currentRate))
                }
            }
        }
    }

    private fun bandedMinutes(
        socStart: Int,
        target: Int,
        fullAh: Double,
        taper: Map<Int, Double>,
        fallbackRate: Double
    ): Int? {
        var cursor = socStart.toDouble()
        var hours = 0.0
        while (cursor < target) {
            val bandStart = (cursor.toInt() / 10) * 10
            val bandEnd = (bandStart + 10).toDouble().coerceAtMost(target.toDouble())
            val socSpan = bandEnd - cursor
            val bandAh = fullAh * socSpan / 100.0
            val rate = taper[bandStart] ?: fallbackRate
            if (rate <= 0.0) return null
            hours += bandAh / rate
            cursor = bandEnd
        }
        return (hours * 60.0).roundToInt()
    }
}

private fun String.format(vararg args: Any): String =
    String.format(Locale.US, this, *args)
