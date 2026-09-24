package com.example.batteryanalytics.ui.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.domain.model.BatterySnapshot
import com.example.batteryanalytics.domain.model.Metric
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.components.MetricTile
import com.example.batteryanalytics.ui.components.CurrentFormat
import com.example.batteryanalytics.ui.components.PowerFormat
import com.example.batteryanalytics.ui.components.defaultAccentFor
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.Palette
import kotlinx.coroutines.flow.StateFlow

@Composable
fun DashboardScreen(
    snapshots: StateFlow<BatterySnapshot?>,
    repository: com.example.batteryanalytics.data.repo.BatteryRepository,
    onOpenSettings: () -> Unit = {}
) {
    val snapshot by snapshots.collectAsState()
    var dialogFor by remember { mutableStateOf<Pair<String, Metric<*>>?>(null) }

    // Load recent sessions for the ETA estimator. Refresh whenever the
    // charging state transitions so a newly-closed session feeds back in.
    var recentSessions by remember {
        mutableStateOf<List<com.example.batteryanalytics.domain.model.SessionRow>>(emptyList())
    }
    // Rolling windows for min/max display. Ring size 24 at 5 s cadence ≈ 2 min.
    // This mirrors what Ampere and similar tools call their "min / max" strip:
    // the peak observed over a short trailing window, not an instantaneous
    // reading. On Samsung devices especially, CURRENT_NOW reports *net* cell
    // current, which can sit near zero while the charger is delivering several
    // amps and the phone is consuming them. Showing the recent max makes the
    // real charging rate visible without fabricating a measurement.
    val currentWindow = remember { mutableStateListOf<Double>() }
    val powerWindow = remember { mutableStateListOf<Double>() }
    androidx.compose.runtime.LaunchedEffect(snapshot?.timestampMs) {
        snapshot?.currentNowA?.value?.let {
            currentWindow.add(it)
            while (currentWindow.size > 24) currentWindow.removeAt(0)
        }
        snapshot?.powerW?.value?.let {
            powerWindow.add(it)
            while (powerWindow.size > 24) powerWindow.removeAt(0)
        }
    }

    val statusKey = snapshot?.status?.value
    androidx.compose.runtime.LaunchedEffect(statusKey) {
        recentSessions = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repository.recentSessions(10)
        }
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
        // Colorful gradient header text
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Battery Analytics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = GlassColors.TextPrimary
                )
                Text(
                    "Battery-side values. Tap a tile for source and confidence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassColors.TextSecondary
                )
            }
            androidx.compose.foundation.layout.Box(
                Modifier
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(GlassColors.GlassTint)
                    .clickable(onClick = onOpenSettings)
                    .padding(8.dp)
            ) {
                Text(
                    "⚙",
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassColors.TextSecondary
                )
            }
        }

        if (s == null) {
            Text("Reading\u2026", color = GlassColors.TextSecondary)
            return@Column
        }

        // ---- Primary metric grid: 2 columns × 3 rows, each tile with its own accent ----
        val primary = listOf(
            "State of charge" to s.soc,
            "Temperature" to s.tempC,
            "Voltage" to s.voltageV,
            "Charge counter" to s.chargeCounterAh,
            "Current (now)" to s.currentNowA,
            "Power" to s.powerW
        )
        for (rowStart in primary.indices step 2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (i in rowStart..minOf(rowStart + 1, primary.lastIndex)) {
                    val (label, m) = primary[i]
                    val override = when (label) {
                        "Power" -> PowerFormat.format(m.value as? Double)
                        "Current (now)" -> CurrentFormat.format(m.value as? Double)
                        "Current (avg)" -> CurrentFormat.format(m.value as? Double)
                        else -> null
                    }
                    val minLbl: String? = when (label) {
                        "Current (now)" -> currentWindow.minOrNull()?.let { CurrentFormat.format(it) }
                        "Power" -> powerWindow.minOrNull()?.let { PowerFormat.format(it) }
                        else -> null
                    }
                    val maxLbl: String? = when (label) {
                        "Current (now)" -> currentWindow.maxOrNull()?.let { CurrentFormat.format(it) }
                        "Power" -> powerWindow.maxOrNull()?.let { PowerFormat.format(it) }
                        else -> null
                    }
                    MetricTile(
                        label = label,
                        metric = m,
                        accent = defaultAccentFor(label),
                        modifier = Modifier.weight(1f),
                        displayOverride = override,
                        minLabel = minLbl,
                        maxLabel = maxLbl
                    ) { dialogFor = label to m }
                }
                if (rowStart + 1 > primary.lastIndex) Spacer(Modifier.weight(1f))
            }
        }

        // ---- Battery Health card ----
        BatteryHealthCard(s, recentSessions) { label, metric ->
            dialogFor = label to metric
        }

        // ---- Estimated time (charging or discharging) ----
        EtaRow(s, recentSessions)

        // ---- Status strip: colorful chips ----
        StatusStrip(s) { label, metric -> dialogFor = label to metric }

        // ---- Secondary tiles ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                label = "Current (avg)",
                metric = s.currentAvgA,
                accent = Palette.CurrentAvg,
                modifier = Modifier.weight(1f),
                displayOverride = CurrentFormat.format(s.currentAvgA.value)
            ) { dialogFor = "Current (avg)" to s.currentAvgA }
            MetricTile(
                label = "Energy counter",
                metric = s.energyCounterWh,
                accent = Palette.EnergyCounter,
                modifier = Modifier.weight(1f)
            ) { dialogFor = "Energy counter" to s.energyCounterWh }
        }

        // ---- Unavailable metrics ----
        val unavailable = listOf(
            "Charge full" to s.chargeFullAh,
            "Charge full (design)" to s.chargeFullDesignAh,
            "Energy full" to s.energyFullWh,
            "Energy full (design)" to s.energyFullDesignWh,
            "Cycle count" to s.cycleCount
        ).filter { !it.second.isAvailable }

        if (unavailable.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Not exposed by this device",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    unavailable.joinToString(", ") { it.first },
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassColors.TextTertiary
                )
            }
        }

        // ---- Device info ----
        DeviceInfoCard()

        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(s.timestampMs))
        Text(
            "Sampled at $ts",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextTertiary
        )
    }
}

@Composable
private fun StatusStrip(s: BatterySnapshot, onClick: (String, Metric<*>) -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Status",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextSecondary
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Charging", s.status.display(), Palette.ChipGreen) {
                onClick("Charging status", s.status)
            }
            StatusChip("Plug", s.plugType.display(), Palette.ChipBlue) {
                onClick("Plugged type", s.plugType)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("Health", s.health.display(), Palette.ChipAmber) {
                onClick("Reported health", s.health)
            }
            StatusChip("Cell", s.technology.display(), Palette.ChipViolet) {
                onClick("Battery technology", s.technology)
            }
        }
    }
}

@Composable
private fun StatusChip(
    title: String,
    value: String,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        accent.copy(alpha = 0.20f),
                        accent.copy(alpha = 0.08f)
                    )
                )
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = GlassColors.TextPrimary
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
