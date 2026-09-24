package com.example.batteryanalytics.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.domain.estimate.CapacityEstimator
import com.example.batteryanalytics.domain.model.BatteryHealth
import com.example.batteryanalytics.domain.model.BatterySnapshot
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.GlassShapeSmall
import com.example.batteryanalytics.ui.theme.Palette
import com.example.batteryanalytics.ui.theme.colorfulGlass
import java.util.Locale

/**
 * Battery Health card for the Dashboard.
 *
 * Shows:
 *   - Reported health status (from BatteryManager.EXTRA_HEALTH)
 *   - Estimated full-charge capacity (extrapolated from charge_counter and SOC)
 *   - Capacity health % if both sides are derivable (usually not on Android)
 *   - Cycle count status, honest about the "0 means new OR unsupported" ambiguity
 *
 * Every value carries its own source + confidence badge. Tapping a sub-tile
 * opens the same detail dialog used by every other metric on this screen.
 */
@Composable
fun BatteryHealthCard(
    snapshot: BatterySnapshot,
    recentSessions: List<com.example.batteryanalytics.domain.model.SessionRow>,
    onMetricClick: (String, Metric<*>) -> Unit
) {
    val fullCap = remember(snapshot.timestampMs) {
        CapacityEstimator.estimateFullCapacityAh(
            snapshot.chargeCounterAh,
            snapshot.soc
        )
    }
    val healthPct = remember(snapshot.timestampMs) {
        CapacityEstimator.healthPercent(fullCap, snapshot.chargeFullDesignAh)
    }

    val reportedAccent = accentForHealth(snapshot.health.value)

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(reportedAccent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Battery Health",
                style = MaterialTheme.typography.labelLarge,
                color = GlassColors.TextSecondary
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HealthSubTile(
                label = "Reported",
                value = snapshot.health.display(),
                source = snapshot.health.source,
                confidence = snapshot.health.confidence,
                accent = reportedAccent,
                modifier = Modifier.weight(1f)
            ) { onMetricClick("Reported health", snapshot.health) }

            HealthSubTile(
                label = "Est. full capacity",
                value = fullCapDisplay(fullCap),
                source = fullCap.source,
                confidence = fullCap.confidence,
                accent = Palette.ChargeCounter,
                modifier = Modifier.weight(1f)
            ) { onMetricClick("Est. full capacity", fullCap) }
        }

        Spacer(Modifier.height(10.dp))

        // Capacity health %
        if (healthPct.isAvailable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Capacity health: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextTertiary
                )
                Text(
                    healthPct.display(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = GlassColors.TextPrimary
                )
            }
        } else {
            Text(
                "Capacity health: not available",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
            Text(
                healthPct.method,
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }

        Spacer(Modifier.height(4.dp))

        // Peak battery-side power observed during the most recent completed session.
        // This is the number users usually mean by "my phone charged at X watts".
        // It is battery-side, so lower than the charger's rated output.
        val lastPeak = recentSessions
            .firstOrNull { it.endTs != null && it.peakPowerW != null }
            ?.peakPowerW
        if (lastPeak != null && lastPeak >= 1.0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Peak during last charge: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextTertiary
                )
                Text(
                    "%.1f W".format(lastPeak),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = GlassColors.TextPrimary
                )
                Text(
                    "  (battery-side; lower than charger rating)",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextTertiary
                )
            }
            Spacer(Modifier.height(2.dp))
        }

        // Cycle count line
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Cycle count: ",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
            Text(
                cycleLabel(snapshot.cycleCount),
                style = MaterialTheme.typography.labelSmall,
                color = if (snapshot.cycleCount.isAvailable)
                    GlassColors.TextSecondary else GlassColors.TextTertiary
            )
        }
    }
}

@Composable
private fun HealthSubTile(
    label: String,
    value: String,
    source: Source,
    confidence: Confidence,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .colorfulGlass(accent = accent, shape = GlassShapeSmall, tintAlpha = 0.12f)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextSecondary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.TextPrimary
        )
        Text(
            "${source.short()} · ${confidence.short()}",
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.85f)
        )
    }
}

private fun accentForHealth(h: BatteryHealth?): Color = when (h) {
    BatteryHealth.GOOD -> Palette.ChipGreen
    BatteryHealth.OVERHEAT,
    BatteryHealth.OVER_VOLTAGE,
    BatteryHealth.UNSPECIFIED_FAILURE,
    BatteryHealth.DEAD -> Palette.ChipRose
    BatteryHealth.COLD -> Palette.ChipBlue
    else -> GlassColors.TextTertiary
}

private fun fullCapDisplay(m: Metric<Double>): String {
    if (!m.isAvailable || m.value == null) return m.display()
    return String.format(Locale.US, "~%.2f Ah", m.value)
}

private fun cycleLabel(m: Metric<Int>): String {
    if (!m.isAvailable) return "not available"
    val v = m.value ?: return "not available"
    return if (v == 0) "0 — reported, may mean unsupported" else v.toString()
}

private fun Source.short(): String = when (this) {
    Source.API -> "API"
    Source.SYSFS -> "sysfs"
    Source.CALCULATED -> "calc"
    Source.ESTIMATED -> "est"
    Source.UNAVAILABLE -> "—"
}

private fun Confidence.short(): String = when (this) {
    Confidence.HIGH -> "hi"
    Confidence.MEDIUM -> "med"
    Confidence.LOW -> "lo"
    Confidence.UNAVAILABLE -> "—"
}
