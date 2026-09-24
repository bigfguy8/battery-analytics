package com.example.batteryanalytics.domain.model

data class BatterySnapshot(
    val timestampMs: Long,
    val soc: Metric<Int>,
    val voltageV: Metric<Double>,
    val currentNowA: Metric<Double>,
    val currentAvgA: Metric<Double>,
    val powerW: Metric<Double>,
    val tempC: Metric<Double>,
    val chargeCounterAh: Metric<Double>,
    val chargeFullAh: Metric<Double>,
    val chargeFullDesignAh: Metric<Double>,
    val energyCounterWh: Metric<Double>,
    val energyFullWh: Metric<Double>,
    val energyFullDesignWh: Metric<Double>,
    val cycleCount: Metric<Int>,
    val status: Metric<ChargeStatus>,
    val health: Metric<BatteryHealth>,
    val plugType: Metric<PlugType>,
    val technology: Metric<String>,
    val capabilityRows: List<CapabilityRow>
)
