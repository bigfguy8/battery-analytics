package com.example.batteryanalytics.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.example.batteryanalytics.domain.model.Unit as MetricUnit
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
    var dialogFor by remember { mutableStateOf<Pair<String, Metric<*>>?>(null) }

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) { repository.recentSessions(200) }
        val caps = withContext(Dispatchers.IO) { repository.capabilities() }
        firstSeenMs = caps.minOfOrNull { it.firstSeenTs }
    }

    dialogFor?.let { (label, m) ->
        MetricDetailDialog(label = label, metric = m) { dialogFor = null }
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
        Text(
            "Tap any card for source, confidence, and method.",
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextTertiary
        )

        if (s == null) {
            Text("Reading\u2026", color = GlassColors.TextSecondary)
            return@Column
        }

        // Compute capacity from both sources
        val fromCounter = CapacityEstimator.estimateFullCapacityAh(s.chargeCounterAh, s.soc)
        val fromSessions = CapacityEstimator.estimateFullCapacityFromSessions(sessions)
        val selectedFull = if (fromSessions.isAvailable) fromSessions else fromCounter
        val deviceDesign = s.chargeFullDesignAh

        // Device design capacity, with a fallback to a user-supplied rated
        // value from Settings when the firmware does not expose one.
        val context = LocalContext.current
        val prefs = remember { Prefs(context) }
        val manualMah = prefs.ratedCapacityMah

        val designForHealth: Metric<Double> =
            if (deviceDesign.isAvailable) deviceDesign
            else if (manualMah != null) Metric(
                value = manualMah / 1000.0,
                source = Source.CALCULATED,
                confidence = Confidence.LOW,
                method = "user-supplied rated capacity ($manualMah mAh)",
                unit = MetricUnit.AMPHOUR
            )
            else deviceDesign

        val healthPctFinal = CapacityEstimator.healthPercent(selectedFull, designForHealth)

        val cycles = CycleEstimator.estimate(
            sysfsCycle = Metric.unavailable("no readable sysfs cycle_count on this device"),
            apiCycle = s.cycleCount,
            sessions = sessions,
            designCapacityAh = designForHealth
        )

        val ageMetric: Metric<String> = if (firstSeenMs != null) {
            val days = (System.currentTimeMillis() - firstSeenMs!!) / (24L * 3600_000L)
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            Metric(
                value = "$days days (since ${fmt.format(Date(firstSeenMs!!))})",
                source = Source.CALCULATED,
                confidence = Confidence.HIGH,
                method = "now \u2212 first_seen_ts (persisted in device_capabilities)",
                unit = MetricUnit.NONE
            )
        } else {
            Metric.unavailable("capabilities have not been persisted yet")
        }

        HealthBlock(
            title = "Estimated full-charge capacity",
            primary = selectedFull,
            secondaryLabel = if (fromSessions.isAvailable)
                "from ${sessions.count { it.chargeAh != null }} sessions"
            else "single-point extrapolation"
        ) { dialogFor = "Estimated full-charge capacity" to selectedFull }

        HealthBlock(
            title = "Design capacity",
            primary = designForHealth,
            secondaryLabel = when {
                deviceDesign.isAvailable -> "provided by firmware"
                manualMah != null -> "user-supplied, not read from device"
                else -> "device does not expose it; add it in Settings if you know it"
            }
        ) { dialogFor = "Design capacity" to designForHealth }

        HealthBlock(
            title = "Estimated battery capacity health",
            primary = healthPctFinal,
            secondaryLabel = if (healthPctFinal.isAvailable)
                "estimated_full / design \u00D7 100"
            else healthPctFinal.method
        ) { dialogFor = "Estimated battery capacity health" to healthPctFinal }

        HealthBlock(
            title = "Cycle count",
            primary = cycles,
            secondaryLabel = cycles.method
        ) { dialogFor = "Cycle count" to cycles }

        HealthBlock(
            title = "App-tracked age",
            primary = ageMetric,
            secondaryLabel = "since the app first probed this device"
        ) { dialogFor = "App-tracked age" to ageMetric }

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
    secondaryLabel: String,
    onClick: () -> Unit
) {
    val available = primary.isAvailable
    val accent = when (primary.source) {
        Source.API -> Palette.Voltage
        Source.SYSFS -> Palette.SoC
        Source.CALCULATED -> Palette.ChargeCounter
        Source.ESTIMATED -> Palette.Temperature
        Source.UNAVAILABLE -> GlassColors.TextTertiary
    }
    GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = if (available) GlassColors.TextPrimary else GlassColors.TextTertiary
        )
        Spacer(Modifier.height(2.dp))
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
private fun LegendRow(color: Color, level: String, description: String) {
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

@Composable
private fun MetricDetailDialog(label: String, metric: Metric<*>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(label) },
        text = {
            Column {
                Text("Value: ${metric.display()}")
                Text("Source: ${metric.source.name}")
                Text("Confidence: ${metric.confidence.name}")
                Text("Unit: ${metric.unit.label.ifEmpty { "(none)" }}")
                Text("Method: ${metric.method}")
                metric.rawString?.let {
                    Spacer(Modifier.height(8.dp))
                    Text("Raw sysfs value:")
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    )
}
