package com.example.batteryanalytics.domain.session

import com.example.batteryanalytics.domain.model.QualityFlags
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.model.TelemetrySample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDetectorTest {

    private fun s(
        ts: Long,
        soc: Int? = 40,
        v: Double? = 4.0,
        i: Double? = null,
        p: Double? = null,
        t: Double? = 30.0,
        status: String? = null,
        plug: String? = null
    ) = TelemetrySample(
        tsMs = ts, socPct = soc, voltageV = v, currentA = i, powerW = p, tempC = t,
        statusTxt = status, plugTxt = plug,
        sourceFlags = 0, qualityFlags = QualityFlags.NONE
    )

    @Test fun opensOnFirstChargingSample() {
        val d = SessionDetector()
        val out = d.onSample(s(1000L, status = "CHARGING", plug = "USB"))
        assertEquals(1, out.size)
        assertTrue(out[0] is SessionDetector.Output.Opened)
    }

    @Test fun doesNotOpenWhenUnplugged() {
        val d = SessionDetector()
        val out = d.onSample(s(1000L, status = "DISCHARGING", plug = "NONE"))
        assertTrue(out.isEmpty())
    }

    @Test fun closesOnUnplug() {
        val d = SessionDetector()
        d.onSample(s(1000L, status = "CHARGING", plug = "USB", soc = 40, i = 1.0))
        d.onSample(s(2000L, status = "CHARGING", plug = "USB", soc = 45, i = 1.0))
        val out = d.onSample(s(3000L, status = "DISCHARGING", plug = "NONE", soc = 46, i = -0.5))
        assertEquals(1, out.size)
        val closed = out[0] as SessionDetector.Output.Closed
        assertEquals(SessionQuality.CLEAN, closed.session.quality)
        assertEquals(40, closed.session.socStart)
        assertEquals(46, closed.session.socEnd)
    }

    @Test fun gapBoundaryClosesAtPreviousTimestamp() {
        val d = SessionDetector(gapMs = 60_000L)
        d.onSample(s(1000L, status = "CHARGING", plug = "USB", soc = 40))
        d.onSample(s(2000L, status = "CHARGING", plug = "USB", soc = 41))
        // 10 minutes later -> should close the previous session with GAP_BOUNDARY
        val out = d.onSample(s(602_000L, status = "CHARGING", plug = "USB", soc = 42))
        assertEquals(2, out.size)
        val closed = out[0] as SessionDetector.Output.Closed
        assertEquals(SessionQuality.GAP_BOUNDARY, closed.session.quality)
        assertEquals(2000L, closed.session.endTs)
        assertTrue((closed.qualityFlags and QualityFlags.GAP_AFTER) != 0)
        assertTrue(out[1] is SessionDetector.Output.Opened)
    }

    @Test fun flushClosesOpenSession() {
        val d = SessionDetector()
        d.onSample(s(1000L, status = "CHARGING", plug = "USB", soc = 40))
        val out = d.flush(5000L)
        assertNotNull(out)
        assertEquals(SessionQuality.MANUAL_FLUSH, (out as SessionDetector.Output.Closed).session.quality)
        assertNull(d.flush(6000L))
    }

    @Test fun trapezoidalIntegralOfConstantCurrent() {
        // Isolate trapezoidal integration from gap splitting: this test uses 15-minute
        // steps, which is larger than the default 5-minute gap threshold. A gap of this
        // length is meaningless in a foreground-sampling context and must not fragment
        // the session under test. Disable gap splitting with an effectively infinite
        // threshold.
        val d = SessionDetector(gapMs = 100_000_000_000L)
        // 1 A constant for 1 hour in 0.25 h steps; 4 W constant too.
        val stepMs = 900_000L   // 15 min
        d.onSample(s(0L,        status = "CHARGING", plug = "USB", soc = 40, i = 1.0, p = 4.0))
        d.onSample(s(stepMs,    status = "CHARGING", plug = "USB", soc = 45, i = 1.0, p = 4.0))
        d.onSample(s(2*stepMs,  status = "CHARGING", plug = "USB", soc = 50, i = 1.0, p = 4.0))
        d.onSample(s(3*stepMs,  status = "CHARGING", plug = "USB", soc = 55, i = 1.0, p = 4.0))
        d.onSample(s(4*stepMs,  status = "CHARGING", plug = "USB", soc = 60, i = 1.0, p = 4.0))
        val out = d.onSample(s(4*stepMs + 1L, status = "DISCHARGING", plug = "NONE", soc = 60, i = -0.5))
        assertEquals("expected one Closed output", 1, out.size)
        val closed = out[0] as SessionDetector.Output.Closed
        val chAh = closed.session.chargeAh
        val enWh = closed.session.energyWh
        assertNotNull("chargeAh was null", chAh)
        assertNotNull("energyWh was null", enWh)
        assertEquals(1.0, chAh!!, 0.02)
        assertEquals(4.0, enWh!!, 0.1)
    }
}
