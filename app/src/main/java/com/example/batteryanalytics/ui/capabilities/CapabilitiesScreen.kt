package com.example.batteryanalytics.ui.capabilities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.data.source.SourceResolver
import com.example.batteryanalytics.domain.model.CapabilityRow
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.GlassShapeSmall
import com.example.batteryanalytics.ui.theme.Palette
import com.example.batteryanalytics.ui.theme.colorfulGlass
import com.example.batteryanalytics.ui.theme.glass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CapabilitiesScreen() {
    val context = LocalContext.current
    val resolver = remember { SourceResolver(context) }
    var rows by remember { mutableStateOf<List<CapabilityRow>?>(null) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) { resolver.read().capabilityRows }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Device capabilities",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.TextPrimary
        )
        Text(
            "What this device exposes and from where.",
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextSecondary
        )

        val r = rows
        if (r == null) {
            Text("Probing\u2026", color = GlassColors.TextSecondary)
            return@Column
        }

        val available = r.filter { it.available }
        val missing = r.filter { !it.available }

        // ---- Summary strip ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryPill(
                count = available.size,
                label = "available",
                accent = Palette.ChipGreen,
                modifier = Modifier.weight(1f)
            )
            SummaryPill(
                count = missing.size,
                label = "not exposed",
                accent = GlassColors.TextTertiary,
                modifier = Modifier.weight(1f)
            )
            SummaryPill(
                count = available.count { it.source == Source.API },
                label = "from API",
                accent = Palette.ChipBlue,
                modifier = Modifier.weight(1f)
            )
        }

        // ---- Group by source, only showing available ones ----
        if (available.isNotEmpty()) {
            SectionLabel("Available", Palette.ChipGreen)
            for (row in available) CapabilityRowCompact(row)
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            SectionLabel("Not exposed by this device", GlassColors.TextTertiary)
            for (row in missing) CapabilityRowCompact(row)
        }
    }
}

@Composable
private fun SummaryPill(
    count: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .colorfulGlass(accent = accent, tintAlpha = 0.12f)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            count.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextSecondary
        )
    }
}

@Composable
private fun SectionLabel(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = GlassColors.TextSecondary
        )
    }
}

/**
 * Compact one-liner. Layout:
 *
 *   ● Label                          [source]  [conf]
 *     method — truncated to one line
 *
 * Available rows get a colored left border and accent-tinted glass. Unavailable
 * rows are dimmed but still readable. Tapping would open a detail dialog; not
 * wired yet because the long-press dialogs on Dashboard already serve that role.
 */
@Composable
private fun CapabilityRowCompact(row: CapabilityRow) {
    val accent = when {
        !row.available -> GlassColors.TextTertiary
        row.source == Source.API -> Palette.Voltage
        row.source == Source.SYSFS -> Palette.SoC
        row.source == Source.CALCULATED -> Palette.ChargeCounter
        else -> Palette.Voltage
    }

    val shape = GlassShapeSmall
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = if (row.available) 0.10f else 0.03f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                row.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (row.available) GlassColors.TextPrimary
                        else GlassColors.TextTertiary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SourceChip(row.source, row.available)
            Spacer(Modifier.width(4.dp))
            ConfidenceChip(row.confidence, row.available)
        }
        Text(
            if (row.available) row.method else "not exposed",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 15.dp, top = 2.dp)
        )
    }
}

@Composable
private fun SourceChip(source: Source, available: Boolean) {
    val (label, color) = when (source) {
        Source.API -> "API" to Palette.Voltage
        Source.SYSFS -> "sysfs" to Palette.SoC
        Source.CALCULATED -> "calc" to Palette.ChargeCounter
        Source.ESTIMATED -> "est" to Palette.Temperature
        Source.UNAVAILABLE -> "—" to GlassColors.TextTertiary
    }
    Chip(label, if (available) color else GlassColors.TextTertiary)
}

@Composable
private fun ConfidenceChip(confidence: Confidence, available: Boolean) {
    val (label, color) = when (confidence) {
        Confidence.HIGH -> "hi" to Palette.ChipGreen
        Confidence.MEDIUM -> "med" to Palette.ChipAmber
        Confidence.LOW -> "lo" to Palette.ChipRose
        Confidence.UNAVAILABLE -> "—" to GlassColors.TextTertiary
    }
    Chip(label, if (available) color else GlassColors.TextTertiary)
}

@Composable
private fun Chip(text: String, accent: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Medium
        )
    }
}
