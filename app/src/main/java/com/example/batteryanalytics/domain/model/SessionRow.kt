package com.example.batteryanalytics.domain.model

/**
 * A contiguous charging session. Fields are nullable because a session may be
 * closed on a boundary (reboot, telemetry gap) before every statistic is known.
 * Null means "unknown", never zero.
 *
 * [taperJson] is a small JSON object mapping SOC band start (as string, e.g. "40")
 * to mean charge current in amperes observed within that band, e.g.
 *   {"20":1.45,"30":1.52,"40":1.48,"50":1.20,"60":0.95,"70":0.60,"80":0.30}
 * It is stored as a string so the DB has no JSON dependency.
 */
data class SessionRow(
    val id: Long = 0L,
    val startTs: Long,
    val endTs: Long?,
    val socStart: Int?,
    val socEnd: Int?,
    val chargeAh: Double?,
    val energyWh: Double?,
    val plugType: PlugType?,
    val peakPowerW: Double?,
    val avgPowerW: Double?,
    val tempMinC: Double?,
    val tempMeanC: Double?,
    val tempMaxC: Double?,
    val taperJson: String?,
    val quality: SessionQuality
)

enum class SessionQuality(override val label: String) : Labelled {
    CLEAN("Clean"),
    GAP_BOUNDARY("Closed by telemetry gap"),
    REBOOT_BOUNDARY("Closed by device restart"),
    MANUAL_FLUSH("Closed by manual flush"),
    PARTIAL("Partial data"),
    UNKNOWN("Unknown")
}
