package com.example.batteryanalytics.domain.session

import com.example.batteryanalytics.domain.model.QualityFlags
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccumulatorTest {

    private fun mk(ts: Long, soc: Int, i: Double, p: Double, t: Double) = TelemetrySample(
        tsMs = ts, socPct = soc, voltageV = 4.0, currentA = i, powerW = p, tempC = t,
        statusTxt = "CHARGING", plugTxt = "USB",
        sourceFlags = 1, qualityFlags = QualityFlags.NONE
    )

    @Test fun taperBucketsBySocBand() {
        val acc = SessionAccumulator.open(mk(0L, 20, 1.5, 6.0, 30.0))
        acc.add(mk(600_000L, 25, 1.5, 6.0, 30.0))
        acc.add(mk(1_200_000L, 35, 1.0, 4.0, 31.0))
        acc.add(mk(1_800_000L, 45, 0.8, 3.2, 32.0))
        val row = acc.close(2_000_000L, SessionQuality.CLEAN)
        assertNotNull(row.taperJson)
        assertTrue(row.taperJson!!.contains("\"20\":"))
        assertTrue(row.taperJson!!.contains("\"30\":"))
        assertTrue(row.taperJson!!.contains("\"40\":"))
    }

    @Test fun tempMinMeanMax() {
        val acc = SessionAccumulator.open(mk(0L, 20, 1.0, 4.0, 28.0))
        acc.add(mk(600_000L, 25, 1.0, 4.0, 30.0))
        acc.add(mk(1_200_000L, 30, 1.0, 4.0, 35.0))
        acc.add(mk(1_800_000L, 35, 1.0, 4.0, 42.0))
        val row = acc.close(2_000_000L, SessionQuality.CLEAN)
        assertEquals(28.0, row.tempMinC!!, 0.0)
        assertEquals(42.0, row.tempMaxC!!, 0.0)
        assertEquals((28.0+30.0+35.0+42.0)/4.0, row.tempMeanC!!, 1e-9)
    }

    @Test fun missingSocKeepsNull() {
        val first = TelemetrySample(
            tsMs = 0L, socPct = null, voltageV = 4.0, currentA = 1.0, powerW = 4.0,
            tempC = 30.0, statusTxt = "CHARGING", plugTxt = "USB",
            sourceFlags = 1, qualityFlags = QualityFlags.NONE
        )
        val acc = SessionAccumulator.open(first)
        val row = acc.close(1000L, SessionQuality.CLEAN)
        assertNull(row.socStart)
    }
}
