package com.example.batteryanalytics.domain.model

/**
 * Aggregated statistics for a single calendar day (device local time).
 * dateYyyymmdd is a plain int, e.g. 20260923.
 * Every other field is nullable: a day with no data has nulls, not zeros.
 */
data class DailyRollup(
    val dateYyyymmdd: Int,
    val minSoc: Int?,
    val maxSoc: Int?,
    val avgTempC: Double?,
    val dischargeAh: Double?,
    val sampleCount: Long
)
