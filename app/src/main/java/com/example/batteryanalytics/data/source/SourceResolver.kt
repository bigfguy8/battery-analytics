package com.example.batteryanalytics.data.source

import android.content.Context
import android.os.BatteryManager
import com.example.batteryanalytics.domain.estimate.PowerCalculator
import com.example.batteryanalytics.domain.model.BatteryHealth
import com.example.batteryanalytics.domain.model.BatterySnapshot
import com.example.batteryanalytics.domain.model.CapabilityRow
import com.example.batteryanalytics.domain.model.ChargeStatus
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.MetricKeys
import com.example.batteryanalytics.domain.model.PlugType
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.Unit
import com.example.batteryanalytics.domain.units.UnitNormalizer

/**
 * Merges ApiBatterySource and SysfsPowerSupplySource into a single BatterySnapshot
 * of well-labelled Metrics.
 *
 * Priority:
 *   SoC, voltage, temperature, current, charge counter, energy counter, status,
 *   health, plug, technology   ->  API (stable units, HIGH confidence)
 *   charge_full(_design), energy_full(_design), cycle_count when API lacks it
 *                              ->  sysfs (units guessed, MEDIUM confidence)
 *   power                      ->  CALCULATED from voltage x current
 *
 * Missing on both sources  ->  Metric.unavailable(...). Never zero.
 */
class SourceResolver(context: Context) {

    private val api = ApiBatterySource(context.applicationContext)
    private val sysfs = SysfsPowerSupplySource()

    fun read(): BatterySnapshot {
        val a = api.read()
        val s = sysfs.read()
        val batt = s.primaryBattery

        val status = buildStatus(a.statusCode, batt)
        val plug = buildPlugType(a.plugCode)
        val health = buildHealth(a.healthCode, batt)
        val soc = buildSoc(a.socPercent, batt)
        val voltage = buildVoltage(a.voltageMv, batt)
        val currentNow = buildCurrentNow(a.currentNowUa, batt)
        val currentAvg = buildCurrentAvg(a.currentAvgUa, batt)
        val power = buildPower(voltage, currentNow, status)
        val temp = buildTemperature(a.temperatureTenthsC, batt)
        val chargeCounter = buildChargeCounterAh(a.chargeCounterUah, batt)
        val chargeFull = buildChargeFullAh(batt)
        val chargeFullDesign = buildChargeFullDesignAh(batt)
        val energyCounter = buildEnergyCounterWh(a.energyCounterNwh, batt)
        val energyFull = buildEnergyFullWh(batt)
        val energyFullDesign = buildEnergyFullDesignWh(batt)
        val cycle = buildCycleCount(a.cycleCount, batt)
        val technology = buildTechnology(a.technology, batt)

        val rows = buildCapabilityRows(
            a, batt,
            soc, voltage, currentNow, currentAvg, power, temp,
            chargeCounter, chargeFull, chargeFullDesign,
            energyCounter, energyFull, energyFullDesign,
            cycle, status, health, plug, technology
        )

        return BatterySnapshot(
            timestampMs = System.currentTimeMillis(),
            soc = soc,
            voltageV = voltage,
            currentNowA = currentNow,
            currentAvgA = currentAvg,
            powerW = power,
            tempC = temp,
            chargeCounterAh = chargeCounter,
            chargeFullAh = chargeFull,
            chargeFullDesignAh = chargeFullDesign,
            energyCounterWh = energyCounter,
            energyFullWh = energyFull,
            energyFullDesignWh = energyFullDesign,
            cycleCount = cycle,
            status = status,
            health = health,
            plugType = plug,
            technology = technology,
            capabilityRows = rows
        )
    }

    // ---------------------------------------------------------------- helpers

    private fun buildSoc(api: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Int> {
        if (api != null) return Metric(
            value = api.coerceIn(0, 100),
            source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.BATTERY_PROPERTY_CAPACITY",
            unit = Unit.PERCENT
        )
        val raw = batt?.files?.get("capacity") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs capacity", unit = Unit.PERCENT
        )
        val v = raw.toIntOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs capacity not an integer", unit = Unit.PERCENT, rawString = raw
        )
        return Metric(v.coerceIn(0, 100), Source.SYSFS, Confidence.HIGH,
            "sysfs ${batt.nodePath}/capacity", Unit.PERCENT, raw)
    }

    private fun buildVoltage(apiMv: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        if (apiMv != null) return Metric(
            value = apiMv / 1000.0, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.EXTRA_VOLTAGE (mV)",
            unit = Unit.VOLT
        )
        val raw = batt?.files?.get("voltage_now") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs voltage_now", unit = Unit.VOLT
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs voltage_now not numeric", unit = Unit.VOLT, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessVoltageUnit(rd, null)
        return Metric(UnitNormalizer.voltageToVolts(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/voltage_now (guessed ${u.name.lowercase()})",
            Unit.VOLT, raw)
    }

    private fun buildCurrentNow(apiUa: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        if (apiUa != null) return Metric(
            value = apiUa / 1_000_000.0, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.BATTERY_PROPERTY_CURRENT_NOW (\u00B5A)",
            unit = Unit.AMP
        )
        val raw = batt?.files?.get("current_now") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs current_now", unit = Unit.AMP
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs current_now not numeric", unit = Unit.AMP, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessCurrentUnit(rd, null)
        return Metric(UnitNormalizer.currentToAmps(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/current_now (guessed ${u.name.lowercase()})",
            Unit.AMP, raw)
    }

    private fun buildCurrentAvg(apiUa: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        if (apiUa != null) return Metric(
            value = apiUa / 1_000_000.0, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE (\u00B5A)",
            unit = Unit.AMP
        )
        val raw = batt?.files?.get("current_avg") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs current_avg", unit = Unit.AMP
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs current_avg not numeric", unit = Unit.AMP, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessCurrentUnit(rd, null)
        return Metric(UnitNormalizer.currentToAmps(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/current_avg (guessed ${u.name.lowercase()})",
            Unit.AMP, raw)
    }

    private fun buildTemperature(apiTenthsC: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        if (apiTenthsC != null) return Metric(
            value = apiTenthsC / 10.0, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.EXTRA_TEMPERATURE (0.1 \u00B0C)",
            unit = Unit.CELSIUS
        )
        val raw = batt?.files?.get("temp") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs temp", unit = Unit.CELSIUS
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs temp not numeric", unit = Unit.CELSIUS, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessTempUnit(rd)
        return Metric(UnitNormalizer.tempToCelsius(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/temp (guessed ${u.name.lowercase()})",
            Unit.CELSIUS, raw)
    }

    private fun buildPower(v: Metric<Double>, i: Metric<Double>, status: Metric<ChargeStatus>): Metric<Double> {
        if (!v.isAvailable || !i.isAvailable) return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "requires both voltage and current", unit = Unit.WATT
        )
        val p = PowerCalculator.computePowerW(v.value!!, i.value!!, status.value)
        return Metric(p, Source.CALCULATED, Confidence.MEDIUM,
            "V x I, sign from ${status.method}", Unit.WATT)
    }

    private fun buildChargeCounterAh(apiUah: Long?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        if (apiUah != null) return Metric(
            value = apiUah / 1_000_000.0, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER (\u00B5Ah)",
            unit = Unit.AMPHOUR
        )
        val raw = batt?.files?.get("charge_counter") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs charge_counter", unit = Unit.AMPHOUR
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs charge_counter not numeric", unit = Unit.AMPHOUR, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessChargeUnit(rd)
        return Metric(UnitNormalizer.chargeToAmpHours(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/charge_counter (guessed ${u.name.lowercase()})",
            Unit.AMPHOUR, raw)
    }

    private fun buildChargeFullAh(batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        val raw = batt?.files?.get("charge_full") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no sysfs charge_full", unit = Unit.AMPHOUR
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs charge_full not numeric", unit = Unit.AMPHOUR, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessChargeUnit(rd)
        return Metric(UnitNormalizer.chargeToAmpHours(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/charge_full (guessed ${u.name.lowercase()})",
            Unit.AMPHOUR, raw)
    }

    private fun buildChargeFullDesignAh(batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        val raw = batt?.files?.get("charge_full_design") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no sysfs charge_full_design", unit = Unit.AMPHOUR
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs charge_full_design not numeric", unit = Unit.AMPHOUR, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessChargeUnit(rd)
        return Metric(UnitNormalizer.chargeToAmpHours(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/charge_full_design (guessed ${u.name.lowercase()})",
            Unit.AMPHOUR, raw)
    }

    private fun buildEnergyCounterWh(apiNwh: Long?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        if (apiNwh != null) return Metric(
            value = apiNwh / 1_000_000_000.0, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER (nWh)",
            unit = Unit.WATTHOUR
        )
        val raw = batt?.files?.get("energy_counter") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs energy_counter", unit = Unit.WATTHOUR
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs energy_counter not numeric", unit = Unit.WATTHOUR, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessEnergyUnit(rd)
        return Metric(UnitNormalizer.energyToWattHours(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/energy_counter (guessed ${u.name.lowercase()})",
            Unit.WATTHOUR, raw)
    }

    private fun buildEnergyFullWh(batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        val raw = batt?.files?.get("energy_full") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no sysfs energy_full", unit = Unit.WATTHOUR
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs energy_full not numeric", unit = Unit.WATTHOUR, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessEnergyUnit(rd)
        return Metric(UnitNormalizer.energyToWattHours(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/energy_full (guessed ${u.name.lowercase()})",
            Unit.WATTHOUR, raw)
    }

    private fun buildEnergyFullDesignWh(batt: SysfsPowerSupplySource.NodeReadings?): Metric<Double> {
        val raw = batt?.files?.get("energy_full_design") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no sysfs energy_full_design", unit = Unit.WATTHOUR
        )
        val rd = raw.toDoubleOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs energy_full_design not numeric", unit = Unit.WATTHOUR, rawString = raw
        )
        val (u, c) = UnitNormalizer.guessEnergyUnit(rd)
        return Metric(UnitNormalizer.energyToWattHours(rd, u), Source.SYSFS, c,
            "sysfs ${batt.nodePath}/energy_full_design (guessed ${u.name.lowercase()})",
            Unit.WATTHOUR, raw)
    }

    private fun buildCycleCount(apiCycle: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<Int> {
        if (apiCycle != null) {
            // API 34 EXTRA_CYCLE_COUNT returns 0 both when the device is new and when
            // the field is unsupported. Present 0 at LOW confidence and say so; only a
            // positive count can be treated as a real measurement.
            val ambiguousZero = apiCycle == 0
            val conf = if (ambiguousZero) Confidence.LOW else Confidence.HIGH
            val method = if (ambiguousZero)
                "BatteryManager.EXTRA_CYCLE_COUNT (API 34+): reported 0, which also means unsupported"
            else
                "BatteryManager.EXTRA_CYCLE_COUNT (API 34+)"
            return Metric(apiCycle, Source.API, conf, method, Unit.COUNT)
        }
        val raw = batt?.files?.get("cycle_count") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API 34+ cycle count and no sysfs cycle_count",
            unit = Unit.COUNT
        )
        val v = raw.toIntOrNull() ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "sysfs cycle_count not numeric", unit = Unit.COUNT, rawString = raw
        )
        return Metric(v, Source.SYSFS, Confidence.HIGH,
            "sysfs ${batt.nodePath}/cycle_count", Unit.COUNT, raw)
    }

    private fun buildStatus(apiCode: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<ChargeStatus> {
        val mapped = apiCode?.let { mapApiStatus(it) }
        if (mapped != null) return Metric(
            value = mapped, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.EXTRA_STATUS", unit = Unit.NONE
        )
        val raw = batt?.files?.get("status") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs status", unit = Unit.NONE
        )
        val m = mapSysfsStatus(raw)
        return Metric(m, Source.SYSFS, Confidence.HIGH,
            "sysfs ${batt.nodePath}/status", Unit.NONE, raw)
    }

    private fun buildHealth(apiCode: Int?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<BatteryHealth> {
        val mapped = apiCode?.let { mapApiHealth(it) }
        if (mapped != null) return Metric(
            value = mapped, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.EXTRA_HEALTH", unit = Unit.NONE
        )
        val raw = batt?.files?.get("health") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs health", unit = Unit.NONE
        )
        val m = mapSysfsHealth(raw)
        return Metric(m, Source.SYSFS, Confidence.HIGH,
            "sysfs ${batt.nodePath}/health", Unit.NONE, raw)
    }

    private fun buildPlugType(apiCode: Int?): Metric<PlugType> {
        if (apiCode == null) return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API EXTRA_PLUGGED", unit = Unit.NONE
        )
        val v = when (apiCode) {
            BatteryManager.BATTERY_PLUGGED_AC -> PlugType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> PlugType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> PlugType.WIRELESS
            BatteryManager.BATTERY_PLUGGED_DOCK -> PlugType.DOCK
            0 -> PlugType.NONE
            else -> PlugType.UNKNOWN
        }
        return Metric(v, Source.API, Confidence.HIGH,
            "BatteryManager.EXTRA_PLUGGED", Unit.NONE)
    }

    private fun buildTechnology(apiTech: String?, batt: SysfsPowerSupplySource.NodeReadings?): Metric<String> {
        if (!apiTech.isNullOrBlank()) return Metric(
            value = apiTech, source = Source.API, confidence = Confidence.HIGH,
            method = "BatteryManager.EXTRA_TECHNOLOGY", unit = Unit.NONE
        )
        val raw = batt?.files?.get("technology") ?: return Metric(
            value = null, source = Source.UNAVAILABLE, confidence = Confidence.UNAVAILABLE,
            method = "no API or sysfs technology", unit = Unit.NONE
        )
        return Metric(raw, Source.SYSFS, Confidence.HIGH,
            "sysfs ${batt.nodePath}/technology", Unit.NONE, raw)
    }

    // -------------------------------------------------------- API enum mapping

    private fun mapApiStatus(code: Int): ChargeStatus? = when (code) {
        BatteryManager.BATTERY_STATUS_CHARGING -> ChargeStatus.CHARGING
        BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargeStatus.DISCHARGING
        BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargeStatus.NOT_CHARGING
        BatteryManager.BATTERY_STATUS_FULL -> ChargeStatus.FULL
        BatteryManager.BATTERY_STATUS_UNKNOWN -> ChargeStatus.UNKNOWN
        else -> null
    }

    private fun mapApiHealth(code: Int): BatteryHealth? = when (code) {
        BatteryManager.BATTERY_HEALTH_GOOD -> BatteryHealth.GOOD
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> BatteryHealth.OVERHEAT
        BatteryManager.BATTERY_HEALTH_DEAD -> BatteryHealth.DEAD
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> BatteryHealth.OVER_VOLTAGE
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> BatteryHealth.UNSPECIFIED_FAILURE
        BatteryManager.BATTERY_HEALTH_COLD -> BatteryHealth.COLD
        BatteryManager.BATTERY_HEALTH_UNKNOWN -> BatteryHealth.UNKNOWN
        else -> null
    }

    private fun mapSysfsStatus(raw: String): ChargeStatus = when (raw.lowercase()) {
        "charging" -> ChargeStatus.CHARGING
        "discharging" -> ChargeStatus.DISCHARGING
        "not charging", "not-charging" -> ChargeStatus.NOT_CHARGING
        "full" -> ChargeStatus.FULL
        else -> ChargeStatus.UNKNOWN
    }

    private fun mapSysfsHealth(raw: String): BatteryHealth = when (raw.lowercase()) {
        "good" -> BatteryHealth.GOOD
        "overheat" -> BatteryHealth.OVERHEAT
        "dead" -> BatteryHealth.DEAD
        "over voltage" -> BatteryHealth.OVER_VOLTAGE
        "cold" -> BatteryHealth.COLD
        "unspecified failure" -> BatteryHealth.UNSPECIFIED_FAILURE
        else -> BatteryHealth.UNKNOWN
    }

    // ------------------------------------------------------ capability rows

    private fun buildCapabilityRows(
        a: ApiBatterySource.Readings,
        batt: SysfsPowerSupplySource.NodeReadings?,
        soc: Metric<Int>, voltage: Metric<Double>, currentNow: Metric<Double>,
        currentAvg: Metric<Double>, power: Metric<Double>, temp: Metric<Double>,
        chargeCounter: Metric<Double>, chargeFull: Metric<Double>,
        chargeFullDesign: Metric<Double>, energyCounter: Metric<Double>,
        energyFull: Metric<Double>, energyFullDesign: Metric<Double>,
        cycle: Metric<Int>, status: Metric<ChargeStatus>, health: Metric<BatteryHealth>,
        plug: Metric<PlugType>, technology: Metric<String>
    ): List<CapabilityRow> = listOf(
        row(MetricKeys.SOC, "State of charge", soc),
        row(MetricKeys.VOLTAGE, "Voltage", voltage),
        row(MetricKeys.CURRENT_NOW, "Current now", currentNow),
        row(MetricKeys.CURRENT_AVG, "Current average", currentAvg),
        row(MetricKeys.POWER_REPORTED, "Power (V x I)", power),
        row(MetricKeys.TEMPERATURE, "Temperature", temp),
        row(MetricKeys.CHARGE_COUNTER, "Charge counter", chargeCounter),
        row(MetricKeys.CHARGE_FULL, "Charge full", chargeFull),
        row(MetricKeys.CHARGE_FULL_DESIGN, "Charge full (design)", chargeFullDesign),
        row(MetricKeys.ENERGY_COUNTER, "Energy counter", energyCounter),
        row(MetricKeys.ENERGY_FULL, "Energy full", energyFull),
        row(MetricKeys.ENERGY_FULL_DESIGN, "Energy full (design)", energyFullDesign),
        row(MetricKeys.CYCLE_COUNT, "Cycle count", cycle),
        row(MetricKeys.STATUS, "Charging status", status),
        row(MetricKeys.HEALTH, "Reported health", health),
        row(MetricKeys.PLUG_TYPE, "Plugged type", plug),
        row(MetricKeys.TECHNOLOGY, "Battery technology", technology)
    )

    private fun row(key: String, label: String, m: Metric<*>): CapabilityRow = CapabilityRow(
        key = key, label = label,
        available = m.isAvailable,
        source = m.source, unit = m.unit, confidence = m.confidence,
        method = m.method, rawString = m.rawString
    )
}
