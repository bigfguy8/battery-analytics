package com.example.batteryanalytics.domain.retention

import com.example.batteryanalytics.domain.model.QualityFlags
import com.example.batteryanalytics.domain.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RetentionPolicyTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 3600_000L

    private val policy = RetentionPolicy()

    @Test fun youngSampleIsKeptRaw() {
        assertEquals(RetentionPolicy.Action.KEEP_RAW, policy.actionFor(now - 1L * day, now))
        assertEquals(RetentionPolicy.Action.KEEP_RAW, policy.actionFor(now - 13L * day, now))
    }

    @Test fun oldSampleIsDownsampled() {
        assertEquals(RetentionPolicy.Action.DOWNSAMPLE, policy.actionFor(now - 20L * day, now))
        assertEquals(RetentionPolicy.Action.DOWNSAMPLE, policy.actionFor(now - 43L * day, now))
    }

    @Test fun veryOldSampleIsDeleted() {
        assertEquals(RetentionPolicy.Action.DELETE, policy.actionFor(now - 45L * day, now))
        assertEquals(RetentionPolicy.Action.DELETE, policy.actionFor(now - 400L * day, now))
    }

    @Test fun downsampleKeepsExtrema() {
        fun mk(ts: Long, soc: Int, temp: Double) = TelemetrySample(
            tsMs = ts, socPct = soc, voltageV = 3.9, currentA = 0.5, powerW = 2.0,
            tempC = temp, statusTxt = "CHARGING", plugTxt = "USB",
            sourceFlags = 1, qualityFlags = QualityFlags.NONE
        )
        val bucket = listOf(
            mk(10_000L, 40, 30.0),
            mk(30_000L, 44, 32.5),
            mk(50_000L, 38, 31.0)
        )
        val out = policy.downsampleBucket(bucket)
        assertNotNull(out)
        assertEquals(38, out.socPct)
        assertEquals(32.5, out.tempC!!, 0.0)
    }
}
