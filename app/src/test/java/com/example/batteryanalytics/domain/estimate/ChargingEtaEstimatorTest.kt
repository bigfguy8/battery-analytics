package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.ChargeStatus
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

class ChargingEtaEstimatorTest {

    private fun soc(v: Int) = Metric(v, Source.API, Confidence.HIGH, "test", Unit.PERCENT)
    private fun status(s: ChargeStatus) = Metric(s, Source.API, Confidence.HIGH, "test", Unit.NONE)
    private fun current(v: Double) = Metric(v, Source.API, Confidence.HIGH, "test", Unit.AMP)
    private fun full(v: Double) = Metric(v, Source.CALCULATED, Confidence.MEDIUM, "test", Unit.AMPHOUR)

    @Test fun unavailableWhenNotCharging() {
        val eta = ChargingEtaEstimator.estimate(
            soc(40), status(ChargeStatus.DISCHARGING), current(-0.5), Metric.unavailable("test"),
            full(3.0), emptyList(), targets = listOf(80)
        )
        assertEquals(1, eta.size)
        assertFalse(eta[0].available)
        assertEquals("not charging", eta[0].method)
    }

    @Test fun linearWhenNoHistory() {
        // 40→80 is 40% of 3.0 Ah = 1.2 Ah. At 1.5 A → 0.8 h → 48 min.
        val eta = ChargingEtaEstimator.estimate(
            soc(40), status(ChargeStatus.CHARGING), current(1.5), Metric.unavailable("test"),
            full(3.0), emptyList(), targets = listOf(80)
        )
        assertEquals(1, eta.size)
        assertTrue(eta[0].available)
        assertEquals(48, eta[0].minutes)
        assertEquals(Confidence.LOW, eta[0].confidence)
        assertTrue(eta[0].method.contains("naive"))
    }

    @Test fun bandedUsesTaper() {
        // Taper: 40%→1.5A, 50%→1.4A, 60%→1.2A, 70%→0.8A
        // Time = 3.0 * 0.10 * (1/1.5 + 1/1.4 + 1/1.2 + 1/0.8) hours
        //      = 0.3 * (0.667 + 0.714 + 0.833 + 1.25) = 0.3 * 3.464 = 1.039 h = 62.3 min
        val session = sessionRow(taperJson = """{"40":1.5,"50":1.4,"60":1.2,"70":0.8}""")
        val eta = ChargingEtaEstimator.estimate(
            soc(40), status(ChargeStatus.CHARGING), current(1.5), Metric.unavailable("test"),
            full(3.0), listOf(session), targets = listOf(80)
        )
        assertEquals(1, eta.size)
        assertTrue(eta[0].available)
        // Rounded to nearest int: 62
        assertEquals(62, eta[0].minutes)
        assertEquals(Confidence.MEDIUM, eta[0].confidence)
        assertTrue(eta[0].method.contains("banded"))
    }

    @Test fun atOrAboveTargetIsZero() {
        val eta = ChargingEtaEstimator.estimate(
            soc(85), status(ChargeStatus.CHARGING), current(1.0), Metric.unavailable("test"),
            full(3.0), emptyList(), targets = listOf(80)
        )
        assertEquals(0, eta[0].minutes)
    }

    @Test fun unavailableWhenFullCapacityMissing() {
        val eta = ChargingEtaEstimator.estimate(
            soc(40), status(ChargeStatus.CHARGING), current(1.5), Metric.unavailable("test"),
            Metric.unavailable<Double>("no capacity"), emptyList(), targets = listOf(80)
        )
        assertFalse(eta[0].available)
        assertTrue(eta[0].method.contains("estimated full capacity"))
    }

    private fun sessionRow(taperJson: String?) = SessionRow(
        id = 0, startTs = 0, endTs = 1_000_000,
        socStart = 40, socEnd = 80,
        chargeAh = 2.0, energyWh = null,
        plugType = PlugType.USB,
        peakPowerW = null, avgPowerW = null,
        tempMinC = null, tempMeanC = null, tempMaxC = null,
        taperJson = taperJson,
        quality = SessionQuality.CLEAN
    )

    @Test fun belowFloorReportsUnavailable() {
        // 2.9 mA — mimics a real Samsung reading during fast charge with screen on.
        val eta = ChargingEtaEstimator.estimate(
            soc(50), status(ChargeStatus.CHARGING), current(0.0029),
            Metric.unavailable("test"),
            full(6.0), emptyList(), targets = listOf(80, 90, 100)
        )
        assertEquals(3, eta.size)
        assertTrue(eta.all { !it.available })
        assertTrue(eta[0].method.contains("below the"))
        assertTrue(eta[0].method.contains("mA floor"))
    }

    @Test fun absurdEtaIsCapped() {
        // 60 mA through 3 Ah from 40% to 100% is 1.8 Ah / 0.06 A = 30 h > 24 h cap.
        val eta = ChargingEtaEstimator.estimate(
            soc(40), status(ChargeStatus.CHARGING), current(0.06),
            Metric.unavailable("test"),
            full(3.0), emptyList(), targets = listOf(100)
        )
        assertEquals(1, eta.size)
        assertFalse(eta[0].available)
        assertTrue(eta[0].method.contains("exceeds"))
    }
}
