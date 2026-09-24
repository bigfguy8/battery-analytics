package com.example.batteryanalytics.domain.session

import com.example.batteryanalytics.domain.model.PlugType
import com.example.batteryanalytics.domain.model.QualityFlags
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.TelemetrySample

/**
 * Accumulates statistics for one charging session as samples arrive.
 *
 * The first sample is ingested by [open]; every subsequent sample must be
 * passed to [add]. Integration is trapezoidal between consecutive ingested
 * samples. The [close] call accepts an optional closing sample whose SoC
 * becomes the session's socEnd (this is the discharge-boundary sample that
 * does not itself belong to the accumulator's running stats).
 *
 * Never fabricates: any missing field stays null in the SessionRow.
 */
class SessionAccumulator private constructor(
    private val startTs: Long,
    private var socStart: Int?,
    private var plugType: PlugType?,
    private var qualityFlags: Int
) {
    companion object {
        fun open(first: TelemetrySample): SessionAccumulator {
            val acc = SessionAccumulator(
                startTs = first.tsMs,
                socStart = first.socPct,
                plugType = first.plugTxt?.let { parsePlug(it) },
                qualityFlags = first.qualityFlags
            )
            acc.ingest(first, integrate = false)
            return acc
        }

        private fun parsePlug(txt: String): PlugType = when (txt.lowercase()) {
            "none", "unplugged" -> PlugType.NONE
            "ac" -> PlugType.AC
            "usb" -> PlugType.USB
            "wireless" -> PlugType.WIRELESS
            "dock" -> PlugType.DOCK
            else -> PlugType.UNKNOWN
        }
    }

    private var lastSample: TelemetrySample? = null
    private var socEnd: Int? = socStart
    private var chargeAh: Double = 0.0
    private var energyWh: Double = 0.0
    private var peakPowerW: Double? = null
    private var sumPowerW: Double = 0.0
    private var powerCount: Int = 0
    private var tempMin: Double? = null
    private var tempMax: Double? = null
    private var tempSum: Double = 0.0
    private var tempCount: Int = 0
    private var sampleCount: Int = 0
    private val taperBandSum = HashMap<Int, Double>()
    private val taperBandCount = HashMap<Int, Int>()

    fun add(sample: TelemetrySample) = ingest(sample, integrate = true)

    private fun ingest(sample: TelemetrySample, integrate: Boolean) {
        val prev = lastSample
        if (integrate && prev != null) {
            val dtHours = (sample.tsMs - prev.tsMs).coerceAtLeast(0L) / 3_600_000.0
            val prevI = prev.currentA
            val nowI = sample.currentA
            if (prevI != null && nowI != null && dtHours > 0.0) {
                chargeAh += 0.5 * (prevI + nowI) * dtHours
            }
            val prevP = prev.powerW
            val nowP = sample.powerW
            if (prevP != null && nowP != null && dtHours > 0.0) {
                energyWh += 0.5 * (prevP + nowP) * dtHours
            }
        }

        sample.powerW?.let { p ->
            if (peakPowerW == null || p > peakPowerW!!) peakPowerW = p
            sumPowerW += p
            powerCount += 1
        }

        sample.tempC?.let { t ->
            if (tempMin == null || t < tempMin!!) tempMin = t
            if (tempMax == null || t > tempMax!!) tempMax = t
            tempSum += t
            tempCount += 1
        }

        sample.socPct?.let { soc ->
            val i = sample.currentA
            if (i != null && i > 0.0) {
                val band = (soc / 10) * 10
                taperBandSum[band] = (taperBandSum[band] ?: 0.0) + i
                taperBandCount[band] = (taperBandCount[band] ?: 0) + 1
            }
            socEnd = soc
        }

        qualityFlags = qualityFlags or sample.qualityFlags
        sample.plugTxt?.let { plugType = parsePlug(it) }
        lastSample = sample
        sampleCount += 1
    }

    fun close(
        endTs: Long,
        quality: SessionQuality,
        closingSample: TelemetrySample? = null
    ): SessionRow {
        closingSample?.socPct?.let { socEnd = it }

        val taper = if (taperBandCount.isEmpty()) null
        else buildString {
            append('{')
            var first = true
            for (band in taperBandCount.keys.sorted()) {
                val mean = taperBandSum[band]!! / taperBandCount[band]!!
                if (!first) append(',')
                append('"').append(band).append("\":")
                append(String.format(java.util.Locale.US, "%.4f", mean))
                first = false
            }
            append('}')
        }
        val avgP = if (powerCount > 0) sumPowerW / powerCount else null
        val meanT = if (tempCount > 0) tempSum / tempCount else null

        return SessionRow(
            startTs = startTs,
            endTs = endTs,
            socStart = socStart,
            socEnd = socEnd,
            chargeAh = if (sampleCount >= 2) chargeAh else null,
            energyWh = if (sampleCount >= 2) energyWh else null,
            plugType = plugType,
            peakPowerW = peakPowerW,
            avgPowerW = avgP,
            tempMinC = tempMin,
            tempMeanC = meanT,
            tempMaxC = tempMax,
            taperJson = taper,
            quality = quality
        )
    }

    fun lastTs(): Long = lastSample?.tsMs ?: startTs
    fun flagGapBefore() { qualityFlags = qualityFlags or QualityFlags.GAP_BEFORE }
    fun flagGapAfter()  { qualityFlags = qualityFlags or QualityFlags.GAP_AFTER }
    fun currentQualityFlags(): Int = qualityFlags
}
