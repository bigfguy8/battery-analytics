package com.example.batteryanalytics.data.export

import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.TelemetrySample
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RFC 4180 CSV writer for samples and sessions. Every nullable field is
 * rendered as an empty cell; never as 0, never as a placeholder string.
 * Timestamps are ISO-8601 UTC strings plus the raw epoch millis, so the file
 * is both human-readable and lossless.
 *
 * Line terminator is "\n", which every spreadsheet accepts.
 */
object CsvWriter {

    private val ISO = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
    }

    fun writeSamples(w: Writer, samples: List<TelemetrySample>) {
        w.write(
            "id,ts_iso,ts_ms,soc_pct,voltage_v,current_a,power_w,temp_c," +
                "status,plug,source_flags,quality_flags\n"
        )
        for (s in samples) {
            w.write(s.id.toString()); w.write(",")
            w.write(ISO.get()!!.format(Date(s.tsMs))); w.write(",")
            w.write(s.tsMs.toString()); w.write(",")
            cell(w, s.socPct); w.write(",")
            cell(w, s.voltageV); w.write(",")
            cell(w, s.currentA); w.write(",")
            cell(w, s.powerW); w.write(",")
            cell(w, s.tempC); w.write(",")
            cell(w, s.statusTxt); w.write(",")
            cell(w, s.plugTxt); w.write(",")
            w.write(s.sourceFlags.toString()); w.write(",")
            w.write(s.qualityFlags.toString()); w.write("\n")
        }
    }

    fun writeSessions(w: Writer, sessions: List<SessionRow>) {
        w.write(
            "id,start_iso,start_ms,end_iso,end_ms,soc_start,soc_end," +
                "charge_ah,energy_wh,plug_type,peak_power_w,avg_power_w," +
                "temp_min_c,temp_mean_c,temp_max_c,quality,taper_json\n"
        )
        for (s in sessions) {
            w.write(s.id.toString()); w.write(",")
            w.write(ISO.get()!!.format(Date(s.startTs))); w.write(",")
            w.write(s.startTs.toString()); w.write(",")
            if (s.endTs != null) {
                w.write(ISO.get()!!.format(Date(s.endTs))); w.write(",")
                w.write(s.endTs.toString())
            } else {
                w.write(",")
            }
            w.write(",")
            cell(w, s.socStart); w.write(",")
            cell(w, s.socEnd); w.write(",")
            cell(w, s.chargeAh); w.write(",")
            cell(w, s.energyWh); w.write(",")
            cell(w, s.plugType?.name); w.write(",")
            cell(w, s.peakPowerW); w.write(",")
            cell(w, s.avgPowerW); w.write(",")
            cell(w, s.tempMinC); w.write(",")
            cell(w, s.tempMeanC); w.write(",")
            cell(w, s.tempMaxC); w.write(",")
            cell(w, s.quality.name); w.write(",")
            cell(w, s.taperJson)
            w.write("\n")
        }
    }

    /** Emit a cell value, quoting if it contains a comma, quote, or newline. */
    private fun cell(w: Writer, v: Any?) {
        if (v == null) return
        val s = v.toString()
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        if (!needsQuote) {
            w.write(s)
            return
        }
        w.write("\"")
        for (c in s) {
            if (c == '"') w.write("\"\"") else w.write(c.toString())
        }
        w.write("\"")
    }
}
