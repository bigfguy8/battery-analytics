package com.example.batteryanalytics.domain.session

import com.example.batteryanalytics.domain.model.QualityFlags
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reconstructing a session from already-persisted samples is what makes the
 * REBOOT_BOUNDARY path work. The engine's recoverInterruptedSession() is
 * Android-coupled, so this test exercises the pure accumulation path that
 * method relies on: feed the same samples in the same order, close, verify
 * the resulting row is a valid SessionRow with plausible integrals and the
 * REBOOT_BOUNDARY quality.
 */
class RecoveryReconstructionTest {

    private fun sample(ts: Long, soc: Int, i: Double, p: Double, t: Double) = TelemetrySample(
        tsMs = ts, socPct = soc, voltageV = 4.0, currentA = i, powerW = p, tempC = t,
        statusTxt = "CHARGING", plugTxt = "USB",
        sourceFlags = 1, qualityFlags = QualityFlags.NONE
    )

    @Test fun reconstructsFromPersistedSamples() {
        // Four samples 15 min apart, constant 1 A and 4 W, over 1 hour.
        val stepMs = 900_000L
        val samples = listOf(
            sample(0L,        40, 1.0, 4.0, 30.0),
            sample(stepMs,    45, 1.0, 4.0, 31.0),
            sample(2*stepMs,  50, 1.0, 4.0, 32.0),
            sample(3*stepMs,  55, 1.0, 4.0, 33.0),
            sample(4*stepMs,  60, 1.0, 4.0, 34.0)
        )

        val acc = SessionAccumulator.open(samples.first())
        for (i in 1 until samples.size) acc.add(samples[i])
        val row = acc.close(
            endTs = samples.last().tsMs,
            quality = SessionQuality.REBOOT_BOUNDARY
        )

        assertEquals(SessionQuality.REBOOT_BOUNDARY, row.quality)
        assertEquals(0L, row.startTs)
        assertEquals(4 * stepMs, row.endTs)
        assertEquals(40, row.socStart)
        assertEquals(60, row.socEnd)
        assertNotNull(row.chargeAh)
        assertNotNull(row.energyWh)
        // ~1 A for ~1 h ≈ 1 Ah; ~4 W for ~1 h ≈ 4 Wh
        assertEquals(1.0, row.chargeAh!!, 0.02)
        assertEquals(4.0, row.energyWh!!, 0.1)
        // Temperature range preserved
        assertEquals(30.0, row.tempMinC!!, 0.001)
        assertEquals(34.0, row.tempMaxC!!, 0.001)
        // Taper curve exists (all bands got the same rate here)
        assertNotNull(row.taperJson)
        assertTrue(row.taperJson!!.contains("\"40\":"))
    }

    @Test fun singleSampleReconstructionProducesValidRow() {
        // If we only have one sample (crash happened almost immediately after
        // charge started), the accumulator still produces a valid row with
        // null integral fields — not zeros, not garbage.
        val only = sample(0L, 50, 1.0, 4.0, 30.0)
        val acc = SessionAccumulator.open(only)
        val row = acc.close(endTs = only.tsMs, quality = SessionQuality.REBOOT_BOUNDARY)

        assertEquals(SessionQuality.REBOOT_BOUNDARY, row.quality)
        assertEquals(50, row.socStart)
        assertEquals(50, row.socEnd)
        // With only one sample, sampleCount == 1, so charge/energy are null
        // (there is no interval to integrate over).
        org.junit.Assert.assertNull(row.chargeAh)
        org.junit.Assert.assertNull(row.energyWh)
    }
}
