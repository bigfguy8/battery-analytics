package com.example.batteryanalytics.ui.health

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.data.prefs.Prefs
import com.example.batteryanalytics.data.repo.BatteryRepository
import com.example.batteryanalytics.domain.estimate.CapacityEstimator
import com.example.batteryanalytics.domain.estimate.CycleEstimator
import com.example.batteryanalytics.domain.model.BatterySnapshot
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthScreen(
    repository: BatteryRepository,
    snapshots: StateFlow<BatterySnapshot?>
) {
    val snapshot by snapshots.collectAsState()
    var sessions by remember { mutableStateOf<List<SessionRow>>(emptyList()) }
    var firstSeenMs by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) { repository.recentSessions(200) }
        val caps = withContext(Dispatchers.IO) { repository.capabilities() }
        firstSeenMs = caps.minOfOrNull { it.firstSeenTs }
    }

    val s = snapshot
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Battery Health",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.TextPrimary
        )
        Text(
            "Estimates derived from telemetry. Not a laboratory measurement.",
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextSecondary
        )

        if (s == null) {
            Text("Reading\u2026", color = GlassColors.TextSecondary)
            return@Column
        }

        // Compute capacity from both sources
        val fromCounter = CapacityEstimator.estimateFullCapacityAh(s.chargeCounterAh, s.soc)
        val fromSessions = CapacityEstimator.estimateFullCapacityFromSessions(sessions)
        // Prefer sessions when available: more stable than a single-point extrapolation
        val selectedFull = if (fromSessions.isAvailable) fromSessions else fromCounter
        val design = s.chargeFullDesignAh
        // Computed later, after designForHealth is resolved
        // (kept for reference only; the real call is after the design block)

        // Estimated full capacity
        HealthBlock(
            title = "Estimated full-charge capacity",
            primary = selectedFull,
            secondaryLabel = if (fromSessions.isAvailable)
                "from ${sessions.count { it.chargeAh != null }} sessions"
            else "single-point extrapolation"
        )

        // Design capacity — prefer device; fall back to user-supplied rated capacity
        val context = androidx.compose.ui.platform.LocalContext.current
        val prefs = remember { Prefs(context) }
        val manualMah = prefs.ratedCapacityMah

        val designForHealth: com.example.batteryanalytics.domain.model.Metric<Double> =
            if (design.isAvailable) design
            else if (manualMah != null) com.example.batteryanalytics.domain.model.Metric(
                value = manualMah / 1000.0,
                source = Source.CALCULATED,
                confidence = Confidence.LOW,
                method = "user-supplied rated capacity ($manualMah mAh)",
                unit = com.example.batteryanalytics.domain.model.Unit.AMPHOUR
            )
            else design

        HealthBlock(
            title = "Design capacity",
            primary = designForHealth,
            secondaryLabel = when {
                design.isAvailable -> "provided by firmware"
                manualMah != null -> "user-supplied, not read from device"
                else -> "device does not expose it; add it in Settings if you know it"
            }
        )

        // Health %
        val healthPctFinal = CapacityEstimator.healthPercent(selectedFull, designForHealth)
        HealthBlock(
            title = "Estimated battery capacity health",
            primary = healthPctFinal,
            secondaryLabel = if (healthPctFinal.isAvailable)
                "estimated_full / design \u00D7 100"
            else healthPctFinal.method
        )

        // Cycles
        val cycles = CycleEstimator.estimate(
            sysfsCycle = Metric.unavailable("no readable sysfs cycle_count on this device"),
            apiCycle = s.cycleCount,
            sessions = sessions,
            designCapacityAh = design
        )
        HealthBlock(
            title = "Cycle count",
            primary = cycles,
            secondaryLabel = if (cycles.isAvailable) cycles.method
                            else cycles.method
        )

        // Age
        val ageMetric: Metric<String> = if (firstSeenMs != null) {
            val days = (System.currentTimeMillis() - firstSeenMs!!) / (24L * 3600_000L)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            Metric(
                value = "$days days (since ${fmt.format(Date(firstSeenMs!!))})",
                source = Source.CALCULATED,
                confidence = Confidence.HIGH,
                method = "now \u2212 first_seen_ts (persisted in device_capabilities)",
                unit = com.example.batteryanalytics.domain.model.Unit.NONE
            )
        } else {
            Metric.unavailable("capabilities have not been persisted yet")
        }
        HealthBlock(
            title = "App-tracked age",
            primary = ageMetric,
            secondaryLabel = "since the app first probed this device"
        )

        // Confidence legend
        Spacer(Modifier.height(4.dp))
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Confidence key",
                style = MaterialTheme.typography.labelLarge,
                color = GlassColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            LegendRow(Palette.ChipGreen, "HIGH", "direct read from a documented API or sysfs file")
            LegendRow(Palette.ChipAmber, "MEDIUM", "arithmetic on direct reads, or history with enough samples")
            LegendRow(Palette.ChipRose, "LOW", "single-sample estimate or sparse history")
            LegendRow(GlassColors.TextTertiary, "UNAVAILABLE", "inputs missing; nothing is displayed")
        }

        Text(
            "These are estimates derived from telemetry. Not a laboratory measurement. " +
                "Not a warranty statement.",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextTertiary
        )
    }
}

@Composable
private fun HealthBlock(
    title: String,
    primary: Metric<*>,
    secondaryLabel: String
) {
    val available = primary.isAvailable
    val accent = when (primary.source) {
        Source.API -> Palette.Voltage
        Source.SYSFS -> Palette.SoC
        Source.CALCULATED -> Palette.ChargeCounter
        Source.ESTIMATED -> Palette.Temperature
        Source.UNAVAILABLE -> GlassColors.TextTertiary
    }
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = GlassColors.TextSecondary
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            primary.display(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (available) GlassColors.TextPrimary else GlassColors.TextTertiary
        )
        Text(
            if (available) "${primary.source.name}  \u00B7  ${primary.confidence.name}"
            else "UNAVAILABLE",
            style = MaterialTheme.typography.labelSmall,
            color = if (available) accent.copy(alpha = 0.85f) else GlassColors.TextTertiary
        )
        Text(
            secondaryLabel,
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextTertiary
        )
        if (available && primary.method.isNotBlank() && primary.method != secondaryLabel) {
            Text(
                primary.method,
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, level: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                level,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = color
            )
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }
    }
}
