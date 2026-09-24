package com.example.batteryanalytics.domain.units

import com.example.batteryanalytics.domain.model.Confidence
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitNormalizerTest {

    @Test fun microampsToAmps() {
        assertEquals(0.002, UnitNormalizer.currentToAmps(2000.0, CurrentUnit.MICROAMPS), 1e-12)
    }

    @Test fun milliampsToAmps() {
        assertEquals(2.0, UnitNormalizer.currentToAmps(2000.0, CurrentUnit.MILLIAMPS), 1e-12)
    }

    @Test fun tenthsCelsiusToCelsius() {
        assertEquals(32.5, UnitNormalizer.tempToCelsius(325.0, TempUnit.TENTHS_CELSIUS), 1e-12)
    }

    @Test fun voltageMicrovoltsToVolts() {
        assertEquals(4.2, UnitNormalizer.voltageToVolts(4_200_000.0, VoltageUnit.MICROVOLTS), 1e-9)
    }

    @Test fun chargeMicroampHoursToAmpHours() {
        assertEquals(3.5, UnitNormalizer.chargeToAmpHours(3_500_000.0, ChargeUnit.MICROAMP_HOURS), 1e-12)
    }

    @Test fun energyNanowattHoursToWattHours() {
        assertEquals(12.5, UnitNormalizer.energyToWattHours(12_500_000_000.0, EnergyUnit.NANOWATT_HOURS), 1e-9)
    }

    @Test fun guessCurrentWithApiCrossCheckMicroamps() {
        val (u, c) = UnitNormalizer.guessCurrentUnit(2_000_000.0, 2.0)
        assertEquals(CurrentUnit.MICROAMPS, u)
        assertEquals(Confidence.HIGH, c)
    }

    @Test fun guessCurrentWithApiCrossCheckMilliamps() {
        val (u, c) = UnitNormalizer.guessCurrentUnit(2_000.0, 2.0)
        assertEquals(CurrentUnit.MILLIAMPS, u)
        assertEquals(Confidence.HIGH, c)
    }

    @Test fun guessCurrentWithoutApiFallsBackToMagnitude() {
        val (u, c) = UnitNormalizer.guessCurrentUnit(2_000_000.0, null)
        assertEquals(CurrentUnit.MICROAMPS, u)
        assertEquals(Confidence.MEDIUM, c)
    }

    @Test fun guessTemperatureTenths() {
        val (u, _) = UnitNormalizer.guessTempUnit(325.0)
        assertEquals(TempUnit.TENTHS_CELSIUS, u)
    }

    @Test fun guessTemperatureCelsius() {
        val (u, _) = UnitNormalizer.guessTempUnit(32.5)
        assertEquals(TempUnit.CELSIUS, u)
    }

    @Test fun guessVoltageWithApiCrossCheck() {
        val (u, c) = UnitNormalizer.guessVoltageUnit(4_200_000.0, 4.2)
        assertEquals(VoltageUnit.MICROVOLTS, u)
        assertEquals(Confidence.HIGH, c)
    }

    @Test fun guessChargeUnitForQualcommStyleMicroAmpHours() {
        val (u, _) = UnitNormalizer.guessChargeUnit(3_500_000.0)
        assertEquals(ChargeUnit.MICROAMP_HOURS, u)
    }

    @Test fun guessEnergyUnitForNanowattHours() {
        val (u, _) = UnitNormalizer.guessEnergyUnit(12_500_000_000.0)
        assertEquals(EnergyUnit.NANOWATT_HOURS, u)
    }
}
