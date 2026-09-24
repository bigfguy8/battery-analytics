package com.example.batteryanalytics.data.db

import android.content.ContentValues
import android.database.Cursor
import com.example.batteryanalytics.domain.model.DailyRollup

class RollupDao(private val helper: BatteryDbHelper) {

    private val cols = arrayOf(
        "date_yyyymmdd", "min_soc", "max_soc", "avg_temp_c",
        "discharge_ah", "sample_count"
    )

    /** Upsert. A day's rollup is recomputed on every app open from raw samples. */
    fun upsert(row: DailyRollup) {
        val db = helper.writableDatabase
        db.insertWithOnConflict(
            BatteryDbHelper.T_ROLLUPS, null, toValues(row),
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun recent(days: Int): List<DailyRollup> {
        val db = helper.readableDatabase
        db.query(
            BatteryDbHelper.T_ROLLUPS, cols,
            null, null, null, null, "date_yyyymmdd DESC", days.toString()
        ).use { c ->
            val out = ArrayList<DailyRollup>()
            while (c.moveToNext()) out += fromCursor(c)
            return out
        }
    }

    private fun toValues(r: DailyRollup): ContentValues = ContentValues().apply {
        put("date_yyyymmdd", r.dateYyyymmdd)
        r.minSoc?.let { put("min_soc", it) }
        r.maxSoc?.let { put("max_soc", it) }
        r.avgTempC?.let { put("avg_temp_c", it) }
        r.dischargeAh?.let { put("discharge_ah", it) }
        put("sample_count", r.sampleCount)
    }

    private fun fromCursor(c: Cursor): DailyRollup = DailyRollup(
        dateYyyymmdd = c.getInt(c.getColumnIndexOrThrow("date_yyyymmdd")),
        minSoc = c.intOrNull("min_soc"),
        maxSoc = c.intOrNull("max_soc"),
        avgTempC = c.doubleOrNull("avg_temp_c"),
        dischargeAh = c.doubleOrNull("discharge_ah"),
        sampleCount = c.getLong(c.getColumnIndexOrThrow("sample_count"))
    )
}
