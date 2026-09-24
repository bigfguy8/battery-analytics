package com.example.batteryanalytics.domain.session

import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.TelemetrySample

/**
 * State machine that turns a stream of telemetry samples into sessions.
 *
 * A session opens on the first sample where the status is CHARGING AND the plug
 * type is not NONE, OR where the sample's qualityFlags indicate we have just
 * resumed after a gap and are now charging.
 *
 * A session closes on:
 *   - the first sample where the status is DISCHARGING or NOT_CHARGING and the
 *     plug type is NONE (normal close), OR
 *   - the first sample arriving more than [gapMs] after the previous one (gap
 *     boundary; session is closed at the previous sample's timestamp), OR
 *   - an explicit [flush] call (manual close, reboot recovery, shutdown).
 *
 * The detector is deliberately not aware of Android; it is a pure function of
 * the sample stream. Tests feed it synthetic streams and assert on outputs.
 */
class SessionDetector(private val gapMs: Long = DEFAULT_GAP_MS) {

    companion object {
        const val DEFAULT_GAP_MS: Long = 5L * 60L * 1000L
    }

    sealed class Output {
        data class Opened(val startTs: Long) : Output()
        data class Closed(val session: SessionRow, val qualityFlags: Int) : Output()
    }

    private var open: SessionAccumulator? = null
    private var lastTs: Long = 0L

    fun onSample(sample: TelemetrySample): List<Output> {
        val out = mutableListOf<Output>()

        // Gap detection first: if the gap is bigger than the threshold AND a session
        // is open, close it at the previous sample's timestamp with GAP_AFTER flag.
        if (open != null && lastTs > 0L && (sample.tsMs - lastTs) > gapMs) {
            val acc = open!!
            acc.flagGapAfter()
            val row = acc.close(lastTs, SessionQuality.GAP_BOUNDARY)
            out += Output.Closed(row, acc.currentQualityFlags())
            open = null
        }

        val charging = isCharging(sample)
        val plugged  = isPlugged(sample)

        when {
            open == null && charging && plugged -> {
                open = SessionAccumulator.open(sample)
                if (lastTs > 0L && (sample.tsMs - lastTs) > gapMs) {
                    open!!.flagGapBefore()
                }
                out += Output.Opened(sample.tsMs)
            }
            open != null && !charging && !plugged -> {
                val acc = open!!
                // The closing sample's SoC is the session's socEnd, but its other
                // fields belong to the discharge that follows and must NOT enter
                // the accumulator's running stats. Pass it as closingSample only.
                val row = acc.close(sample.tsMs, SessionQuality.CLEAN, closingSample = sample)
                out += Output.Closed(row, acc.currentQualityFlags())
                open = null
            }
            open != null -> {
                open!!.add(sample)
            }
        }

        lastTs = sample.tsMs
        return out
    }

    /** Force-close any open session. Call on app shutdown, or when detecting a reboot. */
    fun flush(nowMs: Long): Output? {
        val acc = open ?: return null
        open = null
        val row = acc.close(nowMs, SessionQuality.MANUAL_FLUSH)
        return Output.Closed(row, acc.currentQualityFlags())
    }

    private fun isCharging(s: TelemetrySample): Boolean =
        s.statusTxt?.equals("CHARGING", ignoreCase = true) == true ||
        s.statusTxt?.equals("FULL", ignoreCase = true) == true

    private fun isPlugged(s: TelemetrySample): Boolean {
        val p = s.plugTxt ?: return false
        return !p.equals("NONE", ignoreCase = true) &&
               !p.equals("Unplugged", ignoreCase = true)
    }
}
