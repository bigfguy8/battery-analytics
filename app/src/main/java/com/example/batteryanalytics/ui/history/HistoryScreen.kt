package com.example.batteryanalytics.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.data.repo.BatteryRepository
import com.example.batteryanalytics.domain.model.DailyRollup
import com.example.batteryanalytics.domain.model.TelemetrySample
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Window(val label: String, val millis: Long, val maxPoints: Int) {
    H1("1 h",   1L * 3600_000L,             400),
    H6("6 h",   6L * 3600_000L,             500),
    H24("24 h", 24L * 3600_000L,            600),
    D7("7 d",   7L * 24L * 3600_000L,       600),
    D30("30 d", 30L * 24L * 3600_000L,      700)
}

@Composable
fun HistoryScreen(repository: BatteryRepository) {
    var window by remember { mutableStateOf(Window.H24) }
    var samples by remember { mutableStateOf<List<TelemetrySample>?>(null) }
    var rollups by remember { mutableStateOf<List<DailyRollup>>(emptyList()) }

    LaunchedEffect(window) {
        val pair = withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val s = repository.samplesInRangeStrided(now - window.millis, now, window.maxPoints)
            val r = repository.recentRollups(days = 7)
            s to r
        }
        samples = pair.first
        rollups = pair.second
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "History",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.TextPrimary
        )
        Text(
            "From the local database. Downsampled for display.",
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextSecondary
        )

        Row {
            for (w in Window.entries) {
                FilterChip(
                    selected = w == window,
                    onClick = { window = w },
                    label = { Text(w.label) },
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
        }

        // Daily summary from precomputed rollups (independent of the window above)
        if (rollups.isNotEmpty()) {
            DailySummaryStrip(rollups)
            Spacer(Modifier.height(4.dp))
        }

        val s = samples
        if (s == null) {
            Text("Loading\u2026", color = GlassColors.TextSecondary)
            return@Column
        }
        if (s.isEmpty()) {
            Text("No samples in this window yet.", color = GlassColors.TextSecondary)
            return@Column
        }

        val chartColor = GlassColors.AccentBlue
        val tempCaution = GlassColors.AccentAmber
        val tempWarn = GlassColors.AccentRed

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            LineChart(
                samples = s,
                selector = { it.socPct?.toDouble() },
                color = chartColor,
                label = "State of charge",
                unit = "%",
                thresholds = listOf(
                    20.0 to GlassColors.TextTertiary,
                    80.0 to GlassColors.TextTertiary
                )
            )
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            LineChart(
                samples = s,
                selector = { it.powerW },
                color = chartColor,
                label = "Power (battery side)",
                unit = "W",
                thresholds = listOf(0.0 to GlassColors.TextSecondary)
            )
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            LineChart(
                samples = s,
                selector = { it.tempC },
                color = chartColor,
                label = "Temperature",
                unit = "\u00B0C",
                thresholds = listOf(40.0 to tempCaution, 45.0 to tempWarn)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Informational only. Not a safety device.",
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            LineChart(
                samples = s,
                selector = { it.voltageV },
                color = chartColor,
                label = "Voltage",
                unit = "V"
            )
        }
    }
}
