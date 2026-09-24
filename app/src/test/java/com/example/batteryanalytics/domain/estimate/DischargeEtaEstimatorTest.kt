package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.ChargeStatus
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.Unit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DischargeEtaEstimatorTest {

    private fun soc(v: Int) = Metric(v, Source.API, Confidence.HIGH, "test", Unit.PERCENT)
    private fun status(s: ChargeStatus) = Metric(s, Source.API, Confidence.HIGH, "test", Unit.NONE)
    private fun current(v: Double) = Metric(v, Source.API, Confidence.HIGH, "test", Unit.AMP)
    private fun full(v: Double) = Metric(v, Source.CALCULATED, Confidence.MEDIUM, "test", Unit.AMPHOUR)

    @Test fun unavailableWhenCharging() {
        val r = DischargeEtaEstimator.estimate(
            soc(50), status(ChargeStatus.CHARGING), current(1.0), full(3.0)
        )
        assertFalse(r.available)
        assertEquals("not discharging", r.method)
    }

    @Test fun simpleExtrapolation() {
        // 50% → 5% is 45% of 3.0 Ah = 1.35 Ah. At 0.5 A → 2.7 h = 162 min.
        val r = DischargeEtaEstimator.estimate(
            soc(50), status(ChargeStatus.DISCHARGING), current(-0.5), full(3.0)
        )
        assertTrue(r.available)
        assertEquals(162, r.minutes)
        assertEquals(Confidence.MEDIUM, r.confidence)
    }

    @Test fun atOrBelowTargetIsZero() {
        val r = DischargeEtaEstimator.estimate(
            soc(4), status(ChargeStatus.DISCHARGING), current(-0.5), full(3.0)
        )
        assertEquals(0, r.minutes)
    }

    @Test fun unavailableWhenFullCapacityMissing() {
        val r = DischargeEtaEstimator.estimate(
            soc(50), status(ChargeStatus.DISCHARGING), current(-0.5),
            Metric.unavailable<Double>("no capacity")
        )
        assertFalse(r.available)
    }

    @Test fun zeroCurrentUnavailable() {
        val r = DischargeEtaEstimator.estimate(
            soc(50), status(ChargeStatus.DISCHARGING), current(0.0), full(3.0)
        )
        assertFalse(r.available)
        assertTrue(r.method.contains("too small"))
    }
}
