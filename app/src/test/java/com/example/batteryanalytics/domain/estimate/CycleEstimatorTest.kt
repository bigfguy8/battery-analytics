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

class CycleEstimatorTest {

    private val noSysfs = Metric.unavailable<Int>("no sysfs")
    private val noApi = Metric.unavailable<Int>("no API")
    private val noDesign = Metric.unavailable<Double>("no design")

    @Test fun sysfsWins() {
        val sysfs = Metric(250, Source.SYSFS, Confidence.HIGH, "sysfs/cycle_count", Unit.COUNT)
        val api = Metric(240, Source.API, Confidence.HIGH, "API", Unit.COUNT)
        val m = CycleEstimator.estimate(sysfs, api, emptyList(), noDesign)
        assertEquals(250, m.value)
        assertEquals(Source.SYSFS, m.source)
    }

    @Test fun apiUsedWhenNoSysfs() {
        val api = Metric(240, Source.API, Confidence.HIGH, "API", Unit.COUNT)
        val m = CycleEstimator.estimate(noSysfs, api, emptyList(), noDesign)
        assertEquals(240, m.value)
        assertEquals(Source.API, m.source)
    }

    @Test fun zeroApiFallsThrough() {
        val apiZero = Metric(0, Source.API, Confidence.LOW, "reported 0", Unit.COUNT)
        val m = CycleEstimator.estimate(noSysfs, apiZero, emptyList(), noDesign)
        assertFalse(m.isAvailable)
    }

    @Test fun historyEstimateNeedsFiveCycles() {
        val design = Metric(3.0, Source.SYSFS, Confidence.HIGH, "design", Unit.AMPHOUR)
        // 4 sessions of 3.0 Ah each → 12 Ah cumulative / 3 Ah design = 4 cycles → insufficient
        val sessions = (1..4).map { sessionRow(chargeAh = 3.0) }
        val m = CycleEstimator.estimate(noSysfs, noApi, sessions, design)
        assertFalse(m.isAvailable)
        assertTrue(m.method.contains("Insufficient data"))
    }

    @Test fun historyEstimateFiveCyclesPasses() {
        val design = Metric(3.0, Source.SYSFS, Confidence.HIGH, "design", Unit.AMPHOUR)
        val sessions = (1..5).map { sessionRow(chargeAh = 3.0) } // 15 Ah / 3 = 5 cycles
        val m = CycleEstimator.estimate(noSysfs, noApi, sessions, design)
        assertTrue(m.isAvailable)
        assertEquals(5, m.value)
        assertEquals(Confidence.LOW, m.confidence)
    }

    private fun sessionRow(chargeAh: Double) = SessionRow(
        id = 0, startTs = 0, endTs = 1_000_000,
        socStart = 40, socEnd = 80,
        chargeAh = chargeAh, energyWh = null,
        plugType = PlugType.USB,
        peakPowerW = null, avgPowerW = null,
        tempMinC = null, tempMeanC = null, tempMaxC = null,
        taperJson = null,
        quality = SessionQuality.CLEAN
    )
}
