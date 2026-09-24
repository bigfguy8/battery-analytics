package com.example.batteryanalytics.domain.estimate

import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.Source

/**
 * Charging efficiency = battery-side power / input-side power.
 *
 * Android does not expose input-side power on most devices (there is no
 * public API for wall-side wattage). Without both sides, this cannot be
 * computed. We do not fake it, and we do not pass off internal consistency
 * checks as "efficiency".
 *
 * If a future device exposes an input-side channel, this object is the
 * single place that gets updated.
 */
object ChargingEfficiency {
    fun estimate(): Metric<Double> = Metric.unavailable(
        "device does not expose input-side power; charger efficiency cannot be computed"
    )
}
