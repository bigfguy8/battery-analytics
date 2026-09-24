package com.example.batteryanalytics.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.batteryanalytics.domain.model.TelemetrySample

/**
 * CRUD for telemetry_samples. All methods are synchronous and expect to be
 * called from a background dispatcher (the repository owns that).
 *
 * insert/insertBatch take a SQLiteDatabase, not the helper: the caller decides
 * whether to wrap multiple inserts in a transaction.
 */
class TelemetryDao(private val helper: BatteryDbHelper) {

    private val cols = arrayOf(
        "id", "ts_ms", "soc_pct", "voltage_v", "current_a", "power_w",
        "temp_c", "status_txt", "plug_txt", "source_flags", "quality_flags"
    )

    fun insert(db: SQLiteDatabase, sample: TelemetrySample): Long =
        db.insert(BatteryDbHelper.T_SAMPLES, null, toValues(sample))

    fun insert(sample: TelemetrySample): Long {
        val db = helper.writableDatabase
        return insert(db, sample)
    }

    /** Wrap a batch in a single transaction. Returns the number inserted. */
    fun insertBatch(samples: List<TelemetrySample>): Int {
        if (samples.isEmpty()) return 0
        val db = helper.writableDatabase
        var n = 0
        db.beginTransaction()
        try {
            for (s in samples) {
                if (insert(db, s) >= 0L) n++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return n
    }

    fun queryRange(fromMs: Long, toMs: Long): List<TelemetrySample> {
        val db = helper.readableDatabase
        val cursor = db.query(
            BatteryDbHelper.T_SAMPLES, cols,
            "ts_ms >= ? AND ts_ms <= ?", arrayOf(fromMs.toString(), toMs.toString()),
            null, null, "ts_ms ASC"
        )
        val out = ArrayList<TelemetrySample>()
        cursor.use {
            while (it.moveToNext()) out += fromCursor(it)
        }
        return out
    }

    /**
     * Query a range with at most [maxPoints] rows by striding on id.
     *
     * Rows are inserted in ts order, and AUTOINCREMENT gives contiguous ids, so
     * id % stride = 0 approximates even sampling across the range. Retention
     * deletes can create gaps in id, which makes the effective stride uneven
     * but not empty. This is a deliberate trade-off: window functions
     * (ROW_NUMBER) need API 30+, and OFFSET is O(n) in SQLite.
     *
     * If the range contains [maxPoints] or fewer samples, no stride is applied.
     */
    fun queryRangeStrided(fromMs: Long, toMs: Long, maxPoints: Int): List<TelemetrySample> {
        require(maxPoints > 0)
        val db = helper.readableDatabase
        val total = db.rawQuery(
            "SELECT COUNT(*) FROM ${BatteryDbHelper.T_SAMPLES} WHERE ts_ms >= ? AND ts_ms <= ?",
            arrayOf(fromMs.toString(), toMs.toString())
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

        if (total <= maxPoints) return queryRange(fromMs, toMs)

        val stride = maxOf(1L, total / maxPoints)
        val cursor = db.query(
            BatteryDbHelper.T_SAMPLES, cols,
            "ts_ms >= ? AND ts_ms <= ? AND (id % ? = 0)",
            arrayOf(fromMs.toString(), toMs.toString(), stride.toString()),
            null, null, "ts_ms ASC"
        )
        val out = ArrayList<TelemetrySample>()
        cursor.use { while (it.moveToNext()) out += fromCursor(it) }
        return out
    }

    fun recent(limit: Int): List<TelemetrySample> {
        val db = helper.readableDatabase
        val cursor = db.query(
            BatteryDbHelper.T_SAMPLES, cols,
            null, null, null, null, "ts_ms DESC", limit.toString()
        )
        val out = ArrayList<TelemetrySample>()
        cursor.use {
            while (it.moveToNext()) out += fromCursor(it)
        }
        return out.asReversed()
    }

    fun count(): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM ${BatteryDbHelper.T_SAMPLES}", null).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun deleteOlderThan(tsMs: Long): Int {
        val db = helper.writableDatabase
        return db.delete(BatteryDbHelper.T_SAMPLES, "ts_ms < ?", arrayOf(tsMs.toString()))
    }

    /** Delete specific rows by primary key. Used by the retention downsample pass. */
    fun deleteByIds(ids: List<Long>): Int {
        if (ids.isEmpty()) return 0
        val db = helper.writableDatabase
        var n = 0
        db.beginTransaction()
        try {
            val batchSize = 900  // SQLite variable limit is 999 on older devices
            var i = 0
            while (i < ids.size) {
                val batch = ids.subList(i, minOf(i + batchSize, ids.size))
                val placeholders = batch.joinToString(",") { "?" }
                val args = batch.map { it.toString() }.toTypedArray()
                n += db.delete(BatteryDbHelper.T_SAMPLES, "id IN ($placeholders)", args)
                i += batchSize
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return n
    }

    private fun toValues(s: TelemetrySample): ContentValues = ContentValues().apply {
        put("ts_ms", s.tsMs)
        s.socPct?.let { put("soc_pct", it) }
        s.voltageV?.let { put("voltage_v", it) }
        s.currentA?.let { put("current_a", it) }
        s.powerW?.let { put("power_w", it) }
        s.tempC?.let { put("temp_c", it) }
        s.statusTxt?.let { put("status_txt", it) }
        s.plugTxt?.let { put("plug_txt", it) }
        put("source_flags", s.sourceFlags)
        put("quality_flags", s.qualityFlags)
    }

    private fun fromCursor(c: android.database.Cursor): TelemetrySample = TelemetrySample(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        tsMs = c.getLong(c.getColumnIndexOrThrow("ts_ms")),
        socPct = c.intOrNull("soc_pct"),
        voltageV = c.doubleOrNull("voltage_v"),
        currentA = c.doubleOrNull("current_a"),
        powerW = c.doubleOrNull("power_w"),
        tempC = c.doubleOrNull("temp_c"),
        statusTxt = c.stringOrNull("status_txt"),
        plugTxt = c.stringOrNull("plug_txt"),
        sourceFlags = c.getInt(c.getColumnIndexOrThrow("source_flags")),
        qualityFlags = c.getInt(c.getColumnIndexOrThrow("quality_flags"))
    )
}
