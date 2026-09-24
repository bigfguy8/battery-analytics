package com.example.batteryanalytics.data.prefs

import android.content.Context

/**
 * Persistent user settings. Backed by SharedPreferences.
 *
 * Every getter has a documented default. No setting changes behaviour on the
 * fly: the sampling loop reads its interval on every tick, and retention is
 * re-evaluated on every engine start. That means changes take effect within
 * one sampling interval, without needing a restart.
 */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Sampling interval in milliseconds. Allowed: 1000 .. 60000. Default 5000. */
    var samplingIntervalMs: Long
        get() = sp.getLong(KEY_SAMPLING, 5_000L).coerceIn(1_000L, 60_000L)
        set(value) = sp.edit()
            .putLong(KEY_SAMPLING, value.coerceIn(1_000L, 60_000L))
            .apply()

    /** Full-resolution retention window in days. Default 14. */
    var retentionFullDays: Int
        get() = sp.getInt(KEY_RET_FULL, 14).coerceIn(1, 365)
        set(value) = sp.edit().putInt(KEY_RET_FULL, value.coerceIn(1, 365)).apply()

    /** Downsample-then-delete horizon in days. Must be >= retentionFullDays. Default 44. */
    var retentionDownsampleDays: Int
        get() = sp.getInt(KEY_RET_DOWNSAMPLE, 44)
            .coerceAtLeast(retentionFullDays + 1)
            .coerceAtMost(3_650)
        set(value) = sp.edit()
            .putInt(KEY_RET_DOWNSAMPLE, value.coerceIn(retentionFullDays + 1, 3_650))
            .apply()

    /** Whether background sampling is enabled (Phase 5 opt-in). Default false. */
    var backgroundMonitoringEnabled: Boolean
        get() = sp.getBoolean(KEY_BACKGROUND, false)
        set(value) = sp.edit().putBoolean(KEY_BACKGROUND, value).apply()

    /**
     * User-supplied rated battery capacity in mAh, or null if not set.
     *
     * This is NOT read from the device. Android does not expose a public API
     * for the manufacturer-rated capacity, and many vendors do not write it
     * to sysfs. If the user knows their phone's spec sheet, they can enter it
     * here; the app then displays it labelled as user-supplied and can use it
     * as a stand-in for design capacity in health calculations, with a clear
     * LOW confidence marker.
     */
    var ratedCapacityMah: Int?
        get() = sp.getInt(KEY_RATED_MAH, -1).takeIf { it > 0 }
        set(value) {
            val e = sp.edit()
            if (value == null || value <= 0) e.remove(KEY_RATED_MAH)
            else e.putInt(KEY_RATED_MAH, value.coerceIn(500, 30_000))
            e.apply()
        }

    /**
     * Timestamp (ms) at which the currently-open session started, or null if
     * no session is open. Written when a session opens and cleared when it
     * closes normally. If the process is killed mid-session, this value
     * survives in SharedPreferences; on next launch the engine reconstructs
     * the session from the samples already written to the DB and closes it
     * with quality = REBOOT_BOUNDARY.
     */
    var pendingSessionStartTs: Long?
        get() = sp.getLong(KEY_PENDING_SESSION, -1L).takeIf { it > 0 }
        set(value) {
            val e = sp.edit()
            if (value == null || value <= 0) e.remove(KEY_PENDING_SESSION)
            else e.putLong(KEY_PENDING_SESSION, value)
            e.apply()
        }

    /** Reserved: unit system for display. Only metric is implemented today. */
    var useImperialUnits: Boolean
        get() = sp.getBoolean(KEY_IMPERIAL, false)
        set(value) = sp.edit().putBoolean(KEY_IMPERIAL, value).apply()

    fun resetAll() {
        sp.edit().clear().apply()
    }

    companion object {
        const val NAME = "battery_analytics_prefs"
        private const val KEY_SAMPLING = "sampling_interval_ms"
        private const val KEY_RET_FULL = "retention_full_days"
        private const val KEY_RET_DOWNSAMPLE = "retention_downsample_days"
        private const val KEY_BACKGROUND = "background_monitoring"
        private const val KEY_IMPERIAL = "imperial_units"
        private const val KEY_RATED_MAH = "rated_capacity_mah"
        private const val KEY_PENDING_SESSION = "pending_session_start_ts"
    }
}
