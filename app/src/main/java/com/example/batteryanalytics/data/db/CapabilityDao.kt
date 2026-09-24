package com.example.batteryanalytics.data.db

import android.content.ContentValues
import android.database.Cursor
import com.example.batteryanalytics.domain.model.CapabilityRow
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.Unit

class CapabilityDao(private val helper: BatteryDbHelper) {

    private val cols = arrayOf(
        "metric_key", "available", "source", "unit", "confidence",
        "method", "first_seen_ts", "last_probed_ts"
    )

    data class PersistedCapability(
        val metricKey: String,
        val available: Boolean,
        val source: String,
        val unit: String,
        val confidence: String,
        val method: String,
        val firstSeenTs: Long,
        val lastProbedTs: Long
    )

    /** Upsert on metric_key. first_seen_ts is preserved on update. */
    fun upsertAll(rows: List<CapabilityRow>, nowMs: Long) {
        if (rows.isEmpty()) return
        val db = helper.writableDatabase
        val existingFirstSeen = HashMap<String, Long>()
        db.query(BatteryDbHelper.T_CAPABILITIES,
            arrayOf("metric_key", "first_seen_ts"),
            null, null, null, null, null
        ).use { c ->
            while (c.moveToNext()) {
                existingFirstSeen[c.getString(0)] = c.getLong(1)
            }
        }
        db.beginTransaction()
        try {
            for (r in rows) {
                val firstSeen = existingFirstSeen[r.key] ?: nowMs
                val values = ContentValues().apply {
                    put("metric_key", r.key)
                    put("available", if (r.available) 1 else 0)
                    put("source", r.source.name)
                    put("unit", r.unit.name)
                    put("confidence", r.confidence.name)
                    put("method", r.method)
                    put("first_seen_ts", firstSeen)
                    put("last_probed_ts", nowMs)
                }
                db.insertWithOnConflict(
                    BatteryDbHelper.T_CAPABILITIES, null, values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun all(): List<PersistedCapability> {
        val db = helper.readableDatabase
        db.query(
            BatteryDbHelper.T_CAPABILITIES, cols,
            null, null, null, null, "metric_key ASC"
        ).use { c ->
            val out = ArrayList<PersistedCapability>()
            while (c.moveToNext()) {
                out += PersistedCapability(
                    metricKey = c.getString(c.getColumnIndexOrThrow("metric_key")),
                    available = c.getInt(c.getColumnIndexOrThrow("available")) != 0,
                    source = c.getString(c.getColumnIndexOrThrow("source")),
                    unit = c.getString(c.getColumnIndexOrThrow("unit")),
                    confidence = c.getString(c.getColumnIndexOrThrow("confidence")),
                    method = c.getString(c.getColumnIndexOrThrow("method")),
                    firstSeenTs = c.getLong(c.getColumnIndexOrThrow("first_seen_ts")),
                    lastProbedTs = c.getLong(c.getColumnIndexOrThrow("last_probed_ts"))
                )
            }
            return out
        }
    }
}
