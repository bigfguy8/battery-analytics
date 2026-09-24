package com.example.batteryanalytics.domain.units

enum class CurrentUnit(val toAmps: Double) {
    AMPS(1.0),
    MILLIAMPS(1e-3),
    MICROAMPS(1e-6)
}

enum class VoltageUnit(val toVolts: Double) {
    VOLTS(1.0),
    MILLIVOLTS(1e-3),
    MICROVOLTS(1e-6)
}

enum class ChargeUnit(val toAmpHours: Double) {
    AMP_HOURS(1.0),
    MILLIAMP_HOURS(1e-3),
    MICROAMP_HOURS(1e-6)
}

enum class EnergyUnit(val toWattHours: Double) {
    WATT_HOURS(1.0),
    MILLIWATT_HOURS(1e-3),
    MICROWATT_HOURS(1e-6),
    NANOWATT_HOURS(1e-9)
}

enum class TempUnit {
    CELSIUS,
    TENTHS_CELSIUS,
    FAHRENHEIT
}
