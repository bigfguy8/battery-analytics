package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.ChargeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerCalculatorTest {

    @Test fun positiveWhenChargingRegardlessOfRawCurrentSign() {
        val p1 = PowerCalculator.computePowerW(4.2, -2.0, ChargeStatus.CHARGING)
        val p2 = PowerCalculator.computePowerW(4.2,  2.0, ChargeStatus.CHARGING)
        assertEquals(8.4, p1, 1e-9)
        assertEquals(8.4, p2, 1e-9)
        assertTrue(p1 > 0 && p2 > 0)
    }

    @Test fun negativeWhenDischargingRegardlessOfRawCurrentSign() {
        val p1 = PowerCalculator.computePowerW(3.8,  0.5, ChargeStatus.DISCHARGING)
        val p2 = PowerCalculator.computePowerW(3.8, -0.5, ChargeStatus.DISCHARGING)
        assertEquals(-1.9, p1, 1e-9)
        assertEquals(-1.9, p2, 1e-9)
        assertTrue(p1 < 0 && p2 < 0)
    }

    @Test fun fullIsTreatedAsCharging() {
        val p = PowerCalculator.computePowerW(4.35, 0.05, ChargeStatus.FULL)
        assertTrue(p >= 0)
    }

    @Test fun unknownPreservesRawSign() {
        val p = PowerCalculator.computePowerW(4.0, -0.5, ChargeStatus.UNKNOWN)
        assertEquals(-2.0, p, 1e-9)
    }
}
