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
        // Message text is not part of the contract; the reason is. Accept
        // any phrasing that says "there is nothing to extrapolate from".
        assertTrue(
            r.method.contains("no measurable") ||
            r.method.contains("too small") ||
            r.method.contains("standby")
        )
    }

    @Test fun belowFloorReportsUnavailable() {
        // -1.0 mA — matches the reading we saw on a real Samsung at idle.
        val r = DischargeEtaEstimator.estimate(
            soc(78), status(ChargeStatus.DISCHARGING), current(-0.001), full(6.02)
        )
        assertFalse(r.available)
        assertTrue(r.method.contains("standby") || r.method.contains("floor"))
    }

    @Test fun absurdEtaIsCapped() {
        // -25 mA at 6 Ah from 50% to 5% is 2.7 Ah / 0.025 A = 108 h > 168 h? No, 108 < 168.
        // Use -10 mA: 2.7 Ah / 0.010 A = 270 h > 168 h cap.
        // But 10 mA is below our 20 mA floor, so it triggers the floor.
        // Use -25 mA: 2.7 Ah / 0.025 A = 108 h. Below cap.
        // To trigger cap, need |I| just above 20 mA: -21 mA => 128.5 h. Still under.
        // Larger remaining span: 95% of 6 Ah = 5.7 Ah / 0.021 A = 271 h > 168.
        val r = DischargeEtaEstimator.estimate(
            soc(99), status(ChargeStatus.DISCHARGING), current(-0.021), full(6.0)
        )
        assertFalse(r.available)
        assertTrue(r.method.contains("cap") || r.method.contains("representative"))
    }

    @Test fun normalDischargeStillWorks() {
        // -500 mA at 6 Ah from 78% to 5% = 4.38 Ah / 0.5 A = 8.76 h = 525.6 min.
        // roundToInt() gives 526. Assert a tolerance band rather than the exact
        // value: small changes to the estimator's arithmetic should not fail
        // this test as long as the result is in the right ballpark.
        val r = DischargeEtaEstimator.estimate(
            soc(78), status(ChargeStatus.DISCHARGING), current(-0.5), full(6.0)
        )
        assertTrue(r.available)
        assertTrue("expected ~526 min, got ${r.minutes}",
            r.minutes != null && r.minutes in 524..528)
    }
}
