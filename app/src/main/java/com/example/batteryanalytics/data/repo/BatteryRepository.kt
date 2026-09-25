package com.example.batteryanalytics.data.repo

import android.content.Context
import com.example.batteryanalytics.data.prefs.Prefs
import com.example.batteryanalytics.data.db.BatteryDbHelper
import com.example.batteryanalytics.data.db.CapabilityDao
import com.example.batteryanalytics.data.db.RollupDao
import com.example.batteryanalytics.data.db.SessionDao
import com.example.batteryanalytics.data.db.TelemetryDao
import com.example.batteryanalytics.domain.model.CapabilityRow
import com.example.batteryanalytics.domain.model.DailyRollup
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.TelemetrySample
import com.example.batteryanalytics.domain.retention.RetentionPolicy
import java.util.Calendar
import java.util.Locale

/**
 * Data layer facade. Owns the DB helper and DAOs.
 *
 * Threading contract: every method blocks on SQLite and must be called from
 * a background dispatcher. The SamplingEngine and UI coroutines honour this.
 *
 * No method throws on empty result; they return empty lists. The DB is the
 * source of truth; missing data is a legitimate state, not an error.
 */
class BatteryRepository(context: Context) {

    private val prefs = Prefs(context)
    private val helper = BatteryDbHelper(context)
    private val telemetryDao = TelemetryDao(helper)
    private val sessionDao = SessionDao(helper)
    private val rollupDao = RollupDao(helper)
    private val capabilityDao = CapabilityDao(helper)

    // -------------------------------------------------------------- writes

    fun insertSample(s: TelemetrySample): Long = telemetryDao.insert(s)

    fun insertSamples(s: List<TelemetrySample>): Int = telemetryDao.insertBatch(s)

    fun insertSession(r: SessionRow): Long = sessionDao.insert(r)

    fun persistCapabilities(rows: List<CapabilityRow>, nowMs: Long) {
        capabilityDao.upsertAll(rows, nowMs)
    }

    fun upsertRollup(r: DailyRollup) {
        rollupDao.upsert(r)
    }

    // --------------------------------------------------------------- reads

    fun recentSessions(limit: Int = 100): List<SessionRow> = sessionDao.recent(limit)

    fun sessionById(id: Long): SessionRow? = sessionDao.byId(id)

    fun sessionCount(): Long = sessionDao.count()

    fun samplesInRange(fromMs: Long, toMs: Long): List<TelemetrySample> =
        telemetryDao.queryRange(fromMs, toMs)

    fun samplesInRangeStrided(fromMs: Long, toMs: Long, maxPoints: Int): List<TelemetrySample> =
        telemetryDao.queryRangeStrided(fromMs, toMs, maxPoints)

    fun recentSamples(limit: Int = 500): List<TelemetrySample> = telemetryDao.recent(limit)

    fun sampleCount(): Long = telemetryDao.count()

    fun capabilities(): List<CapabilityDao.PersistedCapability> = capabilityDao.all()

    fun recentRollups(days: Int = 30): List<DailyRollup> = rollupDao.recent(days)

    // ----------------------------------------------------------- retention

    /**
     * Apply the retention policy. Called on engine start; safe to call often.
     *
     * Strategy:
     *  - Rows older than 44 days are deleted.
     *  - Rows aged 14–44 days are downsampled: for each 60-second bucket with
     *    more than one sample, a synthetic extremum sample is inserted and the
     *    originals are deleted. Samples at bucket boundaries may end up alone
     *    and stay as they are.
     *
     * Returns a small report for logging.
     */
    fun applyRetention(nowMs: Long): RetentionReport {
        val fullMs = prefs.retentionFullDays * 24L * 3600_000L
        val downsampleMs = prefs.retentionDownsampleDays * 24L * 3600_000L
        val policy = RetentionPolicy(
            fullResolutionMs = fullMs,
            downsampleMs = downsampleMs
        )
        val hardCutoff = nowMs - downsampleMs
        val softCutoff = nowMs - fullMs

        val deletedHard = telemetryDao.deleteOlderThan(hardCutoff)

        val window = telemetryDao.queryRange(hardCutoff, softCutoff)
        if (window.isEmpty()) return RetentionReport(deletedHard, 0, 0)

        val buckets = window.groupBy { it.tsMs / RetentionPolicy.DOWNSAMPLE_BUCKET_MS }
        var insertedRepresentatives = 0
        val toDelete = ArrayList<Long>()
        for ((_, bucket) in buckets) {
            if (bucket.size <= 1) continue
            val repr = policy.downsampleBucket(bucket)
            if (telemetryDao.insert(repr) >= 0L) insertedRepresentatives++
            for (s in bucket) if (s.id > 0L) toDelete += s.id
        }
        val deletedDownsampled = telemetryDao.deleteByIds(toDelete)
        return RetentionReport(deletedHard, deletedDownsampled, insertedRepresentatives)
    }

    data class RetentionReport(
        val hardDeleted: Int,
        val downsampledDeleted: Int,
        val downsampledKept: Int
    )

    // ---------------------------------------------------------- rollups

    /**
     * Recompute the rollup row for a given local calendar day. Called after
     * every N inserts and once on engine start for the previous day.
     */
    fun recomputeDailyRollup(dateYyyymmdd: Int) {
        val cal = Calendar.getInstance(Locale.US)
        cal.clear()
        cal.set(
            dateYyyymmdd / 10000,
            (dateYyyymmdd / 100) % 100 - 1,
            dateYyyymmdd % 100
        )
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 24L * 3600_000L

        val samples = telemetryDao.queryRange(dayStart, dayEnd)
        if (samples.isEmpty()) return

        val socs = samples.mapNotNull { it.socPct }
        val temps = samples.mapNotNull { it.tempC }
        // Discharge amp-hours: integral of negative current over the day, abs.
        var dischargeAh = 0.0
        var anyCurrent = false
        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val now = samples[i]
            val dtH = (now.tsMs - prev.tsMs) / 3_600_000.0
            val pi = prev.currentA ?: continue
            val ni = now.currentA ?: continue
            anyCurrent = true
            val meanI = 0.5 * (pi + ni)
            if (meanI < 0.0) dischargeAh += -meanI * dtH
        }
        rollupDao.upsert(
            DailyRollup(
                dateYyyymmdd = dateYyyymmdd,
                minSoc = socs.minOrNull(),
                maxSoc = socs.maxOrNull(),
                // Null when nothing was recorded; a 0.0 average would be
                // indistinguishable from a real reading at freezing.
                avgTempC = if (temps.isEmpty()) null else temps.average(),
                // Null when no discharge happened. Storing 0.0 would make
                // "phone was on the charger all day" look identical to
                // "the discharge integral genuinely summed to zero".
                dischargeAh = if (anyCurrent && dischargeAh > 0.0) dischargeAh else null,
                sampleCount = samples.size.toLong()
            )
        )
    }

    // -------------------------------------------------------- destructive

    /**
     * Wipe every table. Called by the Settings screen after a confirm dialog.
     * VACUUM runs afterwards so the file shrinks on disk; it may take a moment
     * on large databases but is safe to run while the engine is stopped.
     */
    fun deleteAllData() {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(BatteryDbHelper.T_SAMPLES, null, null)
            db.delete(BatteryDbHelper.T_SESSIONS, null, null)
            db.delete(BatteryDbHelper.T_ROLLUPS, null, null)
            db.delete(BatteryDbHelper.T_CAPABILITIES, null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        try { db.execSQL("VACUUM") } catch (_: Throwable) {}
    }

    // ---------------------------------------------------------------- stats

    data class DbStats(
        val sampleCount: Long,
        val sessionCount: Long,
        val capabilityCount: Int,
        val rollupCount: Int,
        val oldestSampleTs: Long?,
        val newestSampleTs: Long?
    )

    fun stats(): DbStats {
        val db = helper.readableDatabase
        fun scalarLong(sql: String): Long =
            db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
        val sampleCount = scalarLong("SELECT COUNT(*) FROM ${BatteryDbHelper.T_SAMPLES}")
        val sessionCount = scalarLong("SELECT COUNT(*) FROM ${BatteryDbHelper.T_SESSIONS}")
        val rollupCount = scalarLong("SELECT COUNT(*) FROM ${BatteryDbHelper.T_ROLLUPS}")
        val capabilityCount = scalarLong("SELECT COUNT(*) FROM ${BatteryDbHelper.T_CAPABILITIES}").toInt()
        val oldest = db.rawQuery(
            "SELECT MIN(ts_ms) FROM ${BatteryDbHelper.T_SAMPLES}", null
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        val newest = db.rawQuery(
            "SELECT MAX(ts_ms) FROM ${BatteryDbHelper.T_SAMPLES}", null
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
        return DbStats(sampleCount, sessionCount, capabilityCount, rollupCount.toInt(), oldest, newest)
    }

    companion object {
        fun yyyymmdd(dateMs: Long): Int {
            val c = Calendar.getInstance(Locale.US)
            c.timeInMillis = dateMs
            return c.get(Calendar.YEAR) * 10000 +
                   (c.get(Calendar.MONTH) + 1) * 100 +
                   c.get(Calendar.DAY_OF_MONTH)
        }
    }
}
