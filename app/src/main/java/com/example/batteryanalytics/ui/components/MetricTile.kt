package com.example.batteryanalytics.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.Palette
import com.example.batteryanalytics.ui.theme.colorfulGlass

@Composable
fun MetricTile(
    label: String,
    metric: Metric<*>,
    accent: Color,
    modifier: Modifier = Modifier,
    displayOverride: String? = null,
    rateLabel: String? = null,
    rangeLabel: String? = null,
    onClick: () -> Unit = {}
) {
    val available = metric.isAvailable
    Column(
        modifier
            .colorfulGlass(accent = accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (available) accent else GlassColors.TextTertiary)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        // For unavailable metrics show an em-dash rather than the truncated
        // "Not available on th...". The sub-label already reads "not exposed",
        // and the full literal string is shown in the tile detail dialog.
        // Metric.display() itself is unchanged and still returns the
        // spec-mandated "Not available on this device".
        val primaryText = displayOverride
            ?: if (available) metric.display() else "\u2014"
        Text(
            primaryText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (available) GlassColors.TextPrimary else GlassColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (available) {
            if (rateLabel != null) {
                Text(
                    rateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Medium
                )
            }
            if (rangeLabel != null) {
                Text(
                    rangeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                "not exposed",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }
    }
}

private fun Source.short(): String = when (this) {
    Source.API -> "API"
    Source.SYSFS -> "sysfs"
    Source.CALCULATED -> "calc"
    Source.ESTIMATED -> "est"
    Source.UNAVAILABLE -> "\u2014"
}

private fun Confidence.short(): String = when (this) {
    Confidence.HIGH -> "hi"
    Confidence.MEDIUM -> "med"
    Confidence.LOW -> "lo"
    Confidence.UNAVAILABLE -> "\u2014"
}

/** Convenience: default accent per metric label. Falls back to voltage blue. */
fun defaultAccentFor(label: String): Color = when (label) {
    "State of charge" -> Palette.SoC
    "Temperature" -> Palette.Temperature
    "Voltage" -> Palette.Voltage
    "Charge counter" -> Palette.ChargeCounter
    "Current (now)" -> Palette.CurrentNow
    "Current (avg)" -> Palette.CurrentAvg
    "Power" -> Palette.Power
    "Energy counter" -> Palette.EnergyCounter
    else -> Palette.Voltage
}
