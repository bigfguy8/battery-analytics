package com.example.batteryanalytics.ui.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.data.repo.BatteryRepository
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionsScreen(repository: BatteryRepository) {
    var sessions by remember { mutableStateOf<List<SessionRow>?>(null) }
    var selected by remember { mutableStateOf<SessionRow?>(null) }

    LaunchedEffect(Unit) {
        sessions = withContext(Dispatchers.IO) { repository.recentSessions(limit = 100) }
    }

    val current = selected
    if (current != null) {
        SessionDetailScreen(current) { selected = null }
        return
    }

    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Charging sessions",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.TextPrimary
        )
        Text(
            "One row per contiguous charging period. Tap for details.",
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextSecondary
        )

        val list = sessions
        when {
            list == null -> Text("Loading\u2026", color = GlassColors.TextSecondary)
            list.isEmpty() -> Text(
                "No sessions recorded yet. Leave the app open while charging.",
                color = GlassColors.TextSecondary
            )
            else -> for (s in list) SessionCard(s, fmt) { selected = s }
        }
    }
}

@Composable
private fun SessionCard(s: SessionRow, fmt: SimpleDateFormat, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(
            "${fmt.format(Date(s.startTs))}" +
                (s.endTs?.let { " \u2192 " + fmt.format(Date(it)) } ?: " (open)"),
            style = MaterialTheme.typography.titleSmall,
            color = GlassColors.TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "SoC ${s.socStart ?: "?"}% \u2192 ${s.socEnd ?: "?"}%   ·   " +
                "${s.plugType?.label ?: "?"}   ·   ${s.quality.label}",
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextSecondary
        )
        val details = buildList {
            s.chargeAh?.let { add("%.3f Ah".format(it)) }
            s.energyWh?.let { add("%.2f Wh".format(it)) }
            s.peakPowerW?.let { add("peak %.2f W".format(it)) }
        }
        if (details.isNotEmpty()) {
            Text(
                details.joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = GlassColors.TextTertiary
            )
        }
    }
}

private fun String.format(vararg args: Any): String =
    java.lang.String.format(Locale.US, this, *args)
