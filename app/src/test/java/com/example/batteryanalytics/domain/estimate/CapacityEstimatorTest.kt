package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.PlugType
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.Unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacityEstimatorTest {

    private fun counter(v: Double) = Metric(v, Source.API, Confidence.HIGH, "test", Unit.AMPHOUR)
    private fun soc(v: Int) = Metric(v, Source.API, Confidence.HIGH, "test", Unit.PERCENT)

    @Test fun extrapolationAt50Percent() {
        val m = CapacityEstimator.estimateFullCapacityAh(counter(3.0), soc(50))
        assertTrue(m.isAvailable)
        assertEquals(6.0, m.value!!, 1e-9)
        assertEquals(Confidence.MEDIUM, m.confidence)
    }

    @Test fun extrapolationAt20PercentIsLowConfidence() {
        val m = CapacityEstimator.estimateFullCapacityAh(counter(1.2), soc(20))
        assertTrue(m.isAvailable)
        assertEquals(6.0, m.value!!, 1e-9)
        assertEquals(Confidence.LOW, m.confidence)
    }

    @Test fun extrapolationBelow10PercentUnavailable() {
        val m = CapacityEstimator.estimateFullCapacityAh(counter(0.5), soc(5))
        assertFalse(m.isAvailable)
        assertEquals(Source.UNAVAILABLE, m.source)
    }

    @Test fun sessionEstimateNeedsDeltaSoc30() {
        val s = sessionRow(socStart = 50, socEnd = 70, chargeAh = 1.0) // Δ=20
        val m = CapacityEstimator.estimateFullCapacityFromSessions(listOf(s))
        assertFalse(m.isAvailable)
    }

    @Test fun sessionEstimateSingle() {
        val s = sessionRow(socStart = 40, socEnd = 80, chargeAh = 2.4) // Δ=40 → 6.0 Ah
        val m = CapacityEstimator.estimateFullCapacityFromSessions(listOf(s))
        assertTrue(m.isAvailable)
        assertEquals(6.0, m.value!!, 1e-9)
        assertEquals(Confidence.LOW, m.confidence)
    }

    @Test fun sessionEstimateFiveOrMoreIsMedium() {
        val sessions = (1..5).map { sessionRow(40, 80, 2.4) }
        val m = CapacityEstimator.estimateFullCapacityFromSessions(sessions)
        assertTrue(m.isAvailable)
        assertEquals(Confidence.MEDIUM, m.confidence)
    }

    @Test fun healthPercentUnavailableWhenDesignMissing() {
        val est = Metric(6.0, Source.CALCULATED, Confidence.MEDIUM, "test", Unit.AMPHOUR)
        val design = Metric.unavailable<Double>("no sysfs")
        val m = CapacityEstimator.healthPercent(est, design)
        assertFalse(m.isAvailable)
    }

    private fun sessionRow(socStart: Int, socEnd: Int, chargeAh: Double) = SessionRow(
        id = 0,
        startTs = 0, endTs = 1_000_000,
        socStart = socStart, socEnd = socEnd,
        chargeAh = chargeAh, energyWh = null,
        plugType = PlugType.USB,
        peakPowerW = null, avgPowerW = null,
        tempMinC = null, tempMeanC = null, tempMaxC = null,
        taperJson = null,
        quality = SessionQuality.CLEAN
    )
}
