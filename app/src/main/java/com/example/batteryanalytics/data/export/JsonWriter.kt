package com.example.batteryanalytics.data.export

import android.os.Build
import com.example.batteryanalytics.data.db.CapabilityDao
import com.example.batteryanalytics.domain.model.BatterySnapshot
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.TelemetrySample
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Hand-rolled JSON writer. We deliberately avoid adding a JSON library: this
 * is the only place in the app that needs to produce JSON, the structure is
 * fixed, and adding moshi/gson/kotlinx-serialization costs build time on
 * aarch64 for no benefit here.
 *
 * The output is pretty-printed for human readability. Number formatting uses
 * Locale.US so decimal separators are always dots regardless of device locale.
 */
object JsonWriter {

    private fun iso(ts: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(ts))

    private fun q(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append(String.format(Locale.US, "\\u%04x", c.code))
                } else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun num(v: Double?): String = v?.let {
        String.format(Locale.US, "%.6f", it)
    } ?: "null"

    private fun num(v: Int?): String = v?.toString() ?: "null"
    private fun num(v: Long?): String = v?.toString() ?: "null"

    private fun writeKey(w: Writer, key: String, value: String, indent: Int, comma: Boolean) {
        w.write(" ".repeat(indent))
        w.write(q(key))
        w.write(": ")
        w.write(value)
        if (comma) w.write(",")
        w.write("\n")
    }

    /**
     * Full-fidelity export. Includes:
     *   - metadata: schema version, export timestamp, device info, app version
     *   - device capabilities (as persisted)
     *   - all sessions
     *   - all telemetry samples
     *   - the most recent live snapshot (so an importing tool has a starting point)
     */
    fun writeAll(
        w: Writer,
        samples: List<TelemetrySample>,
        sessions: List<SessionRow>,
        capabilities: List<CapabilityDao.PersistedCapability>,
        latest: BatterySnapshot?,
        appVersion: String,
        nowMs: Long
    ) {
        w.write("{\n")
        w.write("  \"schema_version\": 1,\n")
        w.write("  \"exported_at\": ${q(iso(nowMs))},\n")
        w.write("  \"exported_at_ms\": $nowMs,\n")

        // metadata
        w.write("  \"metadata\": {\n")
        w.write("    \"app_version\": ${q(appVersion)},\n")
        w.write("    \"manufacturer\": ${q(Build.MANUFACTURER ?: "unknown")},\n")
        w.write("    \"model\": ${q(Build.MODEL ?: "unknown")},\n")
        w.write("    \"device\": ${q(Build.DEVICE ?: "unknown")},\n")
        w.write("    \"android_sdk\": ${Build.VERSION.SDK_INT},\n")
        w.write("    \"android_release\": ${q(Build.VERSION.RELEASE ?: "unknown")}\n")
        w.write("  },\n")

        // capabilities
        w.write("  \"capabilities\": [\n")
        capabilities.forEachIndexed { i, c ->
            w.write("    {\n")
            w.write("      \"metric_key\": ${q(c.metricKey)},\n")
            w.write("      \"available\": ${c.available},\n")
            w.write("      \"source\": ${q(c.source)},\n")
            w.write("      \"unit\": ${q(c.unit)},\n")
            w.write("      \"confidence\": ${q(c.confidence)},\n")
            w.write("      \"method\": ${q(c.method)},\n")
            w.write("      \"first_seen_ms\": ${c.firstSeenTs},\n")
            w.write("      \"last_probed_ms\": ${c.lastProbedTs}\n")
            w.write("    }")
            if (i < capabilities.lastIndex) w.write(",")
            w.write("\n")
        }
        w.write("  ],\n")

        // sessions
        w.write("  \"sessions\": [\n")
        sessions.forEachIndexed { i, s ->
            w.write("    {\n")
            w.write("      \"id\": ${s.id},\n")
            w.write("      \"start_ms\": ${s.startTs},\n")
            w.write("      \"start_iso\": ${q(iso(s.startTs))},\n")
            w.write("      \"end_ms\": ${num(s.endTs)},\n")
            w.write("      \"end_iso\": ${s.endTs?.let { q(iso(it)) } ?: "null"},\n")
            w.write("      \"soc_start\": ${num(s.socStart)},\n")
            w.write("      \"soc_end\": ${num(s.socEnd)},\n")
            w.write("      \"charge_ah\": ${num(s.chargeAh)},\n")
            w.write("      \"energy_wh\": ${num(s.energyWh)},\n")
            w.write("      \"plug_type\": ${s.plugType?.let { q(it.name) } ?: "null"},\n")
            w.write("      \"peak_power_w\": ${num(s.peakPowerW)},\n")
            w.write("      \"avg_power_w\": ${num(s.avgPowerW)},\n")
            w.write("      \"temp_min_c\": ${num(s.tempMinC)},\n")
            w.write("      \"temp_mean_c\": ${num(s.tempMeanC)},\n")
            w.write("      \"temp_max_c\": ${num(s.tempMaxC)},\n")
            w.write("      \"quality\": ${q(s.quality.name)},\n")
            w.write("      \"taper_json\": ${s.taperJson?.let { q(it) } ?: "null"}\n")
            w.write("    }")
            if (i < sessions.lastIndex) w.write(",")
            w.write("\n")
        }
        w.write("  ],\n")

        // latest snapshot
        w.write("  \"latest_snapshot\": ")
        if (latest == null) {
            w.write("null\n")
        } else {
            w.write("{\n")
            writeKey(w, "ts_ms", latest.timestampMs.toString(), 4, true)
            writeKey(w, "soc_pct", num(latest.soc.value), 4, true)
            writeKey(w, "voltage_v", num(latest.voltageV.value), 4, true)
            writeKey(w, "current_now_a", num(latest.currentNowA.value), 4, true)
            writeKey(w, "current_avg_a", num(latest.currentAvgA.value), 4, true)
            writeKey(w, "power_w", num(latest.powerW.value), 4, true)
            writeKey(w, "temp_c", num(latest.tempC.value), 4, true)
            writeKey(w, "charge_counter_ah", num(latest.chargeCounterAh.value), 4, true)
            writeKey(w, "cycle_count", num(latest.cycleCount.value), 4, true)
            writeKey(w, "status", latest.status.value?.let { q(it.name) } ?: "null", 4, true)
            writeKey(w, "plug_type", latest.plugType.value?.let { q(it.name) } ?: "null", 4, false)
            w.write("    }\n")
        }
        w.write("  ,\n")

        // samples
        w.write("  \"samples\": [\n")
        samples.forEachIndexed { i, s ->
            w.write("    {\n")
            writeKey(w, "id", s.id.toString(), 6, true)
            writeKey(w, "ts_ms", s.tsMs.toString(), 6, true)
            writeKey(w, "soc_pct", num(s.socPct), 6, true)
            writeKey(w, "voltage_v", num(s.voltageV), 6, true)
            writeKey(w, "current_a", num(s.currentA), 6, true)
            writeKey(w, "power_w", num(s.powerW), 6, true)
            writeKey(w, "temp_c", num(s.tempC), 6, true)
            writeKey(w, "status", s.statusTxt?.let { q(it) } ?: "null", 6, true)
            writeKey(w, "plug", s.plugTxt?.let { q(it) } ?: "null", 6, true)
            writeKey(w, "source_flags", s.sourceFlags.toString(), 6, true)
            writeKey(w, "quality_flags", s.qualityFlags.toString(), 6, false)
            w.write("    }")
            if (i < samples.lastIndex) w.write(",")
            w.write("\n")
        }
        w.write("  ]\n")

        w.write("}\n")
    }
}
