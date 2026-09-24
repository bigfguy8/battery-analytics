package com.example.batteryanalytics.domain.model

/**
 * One persisted observation. Every field except tsMs and the two flag columns
 * is nullable. Missing fields stay null; they never become zero.
 *
 * [sourceFlags] is a bitmask of SourceFlags.* describing which sources
 * contributed to this row.
 * [qualityFlags] is a bitmask of QualityFlags.* describing known problems.
 */
data class TelemetrySample(
    val id: Long = 0L,
    val tsMs: Long,
    val socPct: Int?,
    val voltageV: Double?,
    val currentA: Double?,
    val powerW: Double?,
    val tempC: Double?,
    val statusTxt: String?,
    val plugTxt: String?,
    val sourceFlags: Int,
    val qualityFlags: Int
)
