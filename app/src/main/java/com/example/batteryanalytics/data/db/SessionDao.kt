package com.example.batteryanalytics.data.db

import android.content.ContentValues
import android.database.Cursor
import com.example.batteryanalytics.domain.model.PlugType
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.SessionRow

class SessionDao(private val helper: BatteryDbHelper) {

    private val cols = arrayOf(
        "id", "start_ts", "end_ts", "soc_start", "soc_end",
        "charge_ah", "energy_wh", "plug_type",
        "peak_power_w", "avg_power_w",
        "temp_min_c", "temp_mean_c", "temp_max_c",
        "taper_json", "quality"
    )

    fun insert(row: SessionRow): Long {
        val db = helper.writableDatabase
        return db.insert(BatteryDbHelper.T_SESSIONS, null, toValues(row))
    }

    fun recent(limit: Int): List<SessionRow> {
        val db = helper.readableDatabase
        db.query(
            BatteryDbHelper.T_SESSIONS, cols,
            null, null, null, null, "start_ts DESC", limit.toString()
        ).use { c ->
            val out = ArrayList<SessionRow>()
            while (c.moveToNext()) out += fromCursor(c)
            return out
        }
    }

    fun byId(id: Long): SessionRow? {
        val db = helper.readableDatabase
        db.query(
            BatteryDbHelper.T_SESSIONS, cols,
            "id = ?", arrayOf(id.toString()),
            null, null, null, "1"
        ).use { c ->
            return if (c.moveToFirst()) fromCursor(c) else null
        }
    }

    fun count(): Long {
        val db = helper.readableDatabase
        db.rawQuery("SELECT COUNT(*) FROM ${BatteryDbHelper.T_SESSIONS}", null).use {
            return if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    private fun toValues(s: SessionRow): ContentValues = ContentValues().apply {
        put("start_ts", s.startTs)
        s.endTs?.let { put("end_ts", it) }
        s.socStart?.let { put("soc_start", it) }
        s.socEnd?.let { put("soc_end", it) }
        s.chargeAh?.let { put("charge_ah", it) }
        s.energyWh?.let { put("energy_wh", it) }
        s.plugType?.let { put("plug_type", it.name) }
        s.peakPowerW?.let { put("peak_power_w", it) }
        s.avgPowerW?.let { put("avg_power_w", it) }
        s.tempMinC?.let { put("temp_min_c", it) }
        s.tempMeanC?.let { put("temp_mean_c", it) }
        s.tempMaxC?.let { put("temp_max_c", it) }
        s.taperJson?.let { put("taper_json", it) }
        put("quality", s.quality.name)
    }

    private fun fromCursor(c: Cursor): SessionRow {
        val plugRaw = c.stringOrNull("plug_type")
        val plug = plugRaw?.let { raw ->
            PlugType.entries.firstOrNull { it.name == raw } ?: PlugType.UNKNOWN
        }
        val qualityRaw = c.stringOrNull("quality") ?: SessionQuality.UNKNOWN.name
        val quality = SessionQuality.entries.firstOrNull { it.name == qualityRaw }
            ?: SessionQuality.UNKNOWN
        return SessionRow(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            startTs = c.getLong(c.getColumnIndexOrThrow("start_ts")),
            endTs = c.longOrNull("end_ts"),
            socStart = c.intOrNull("soc_start"),
            socEnd = c.intOrNull("soc_end"),
            chargeAh = c.doubleOrNull("charge_ah"),
            energyWh = c.doubleOrNull("energy_wh"),
            plugType = plug,
            peakPowerW = c.doubleOrNull("peak_power_w"),
            avgPowerW = c.doubleOrNull("avg_power_w"),
            tempMinC = c.doubleOrNull("temp_min_c"),
            tempMeanC = c.doubleOrNull("temp_mean_c"),
            tempMaxC = c.doubleOrNull("temp_max_c"),
            taperJson = c.stringOrNull("taper_json"),
            quality = quality
        )
    }
}
