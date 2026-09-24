package com.example.batteryanalytics.domain.retention

import com.example.batteryanalytics.domain.model.TelemetrySample

/**
 * Decides what to keep from raw telemetry over time.
 *
 * Policy (configurable in Phase 5, these are the defaults):
 *   - Full resolution: 14 days.
 *   - Downsampled:     30 additional days, one sample per minute, keeping the
 *                      extrema within each minute (min SoC, max SoC, max temp).
 *   - Older than 44 days: deleted from telemetry_samples.
 *
 * Rollups and sessions are not touched by this policy; they live forever.
 *
 * This class is pure. It takes a "now" timestamp and returns decisions.
 */
class RetentionPolicy(
    private val fullResolutionMs: Long = DEFAULT_FULL_MS,
    private val downsampleMs: Long = DEFAULT_DOWNSAMPLE_MS
) {
    companion object {
        const val DEFAULT_FULL_MS: Long = 14L * 24L * 3600_000L
        const val DEFAULT_DOWNSAMPLE_MS: Long = 44L * 24L * 3600_000L
        const val DOWNSAMPLE_BUCKET_MS: Long = 60_000L
    }

    enum class Action { KEEP_RAW, DOWNSAMPLE, DELETE }

    fun fullResolutionMsCompat(): Long = fullResolutionMs
    fun downsampleMsCompat(): Long = downsampleMs

    fun actionFor(tsMs: Long, nowMs: Long): Action {
        val age = nowMs - tsMs
        return when {
            age < fullResolutionMs -> Action.KEEP_RAW
            age < downsampleMs -> Action.DOWNSAMPLE
            else -> Action.DELETE
        }
    }

    /**
     * Downsample a set of samples that all fall in the same minute bucket.
     * Returns a single representative sample whose numeric fields are the
     * extrema (min or max) of the inputs by SoC, temp, current, power, voltage.
     *
     * The returned row keeps the extrema but not the mean: Phase 4's health
     * estimation only needs extrema, and this avoids an averaging bias. If a
     * future phase needs means it will be added to the rollup table instead.
     */
    fun downsampleBucket(samples: List<TelemetrySample>): TelemetrySample {
        require(samples.isNotEmpty())
        val first = samples.minByOrNull { it.tsMs }!!
        return TelemetrySample(
            tsMs = (first.tsMs / DOWNSAMPLE_BUCKET_MS) * DOWNSAMPLE_BUCKET_MS,
            socPct = samples.mapNotNull { it.socPct }.minOrNull(),
            voltageV = samples.mapNotNull { it.voltageV }.minOrNull(),
            currentA = samples.mapNotNull { it.currentA }.minOrNull(),
            powerW = samples.mapNotNull { it.powerW }.minOrNull(),
            tempC = samples.mapNotNull { it.tempC }.maxOrNull(),
            statusTxt = first.statusTxt,
            plugTxt = first.plugTxt,
            sourceFlags = samples.fold(0) { acc, s -> acc or s.sourceFlags },
            qualityFlags = samples.fold(0) { acc, s -> acc or s.qualityFlags }
        )
    }
}
