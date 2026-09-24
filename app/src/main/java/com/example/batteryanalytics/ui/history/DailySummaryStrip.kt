package com.example.batteryanalytics.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.domain.model.DailyRollup
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import java.util.Locale

/**
 * Compact summary of the last N days, one row per day. Reads the precomputed
 * daily_rollups table, so it is cheap regardless of how many raw samples exist.
 *
 * Every field is shown as "—" when null. Never as 0. A day with no data is
 * simply absent from the list, and the caption says so.
 */
@Composable
fun DailySummaryStrip(rollups: List<DailyRollup>) {
    if (rollups.isEmpty()) return

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Last days",
            style = MaterialTheme.typography.labelLarge,
            color = GlassColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            HeaderCell("Date", 1.2f)
            HeaderCell("SoC range", 1.4f)
            HeaderCell("Avg T", 0.9f)
            HeaderCell("Ah out", 0.9f)
        }
        Spacer(Modifier.height(4.dp))
        for (r in rollups) Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            val date = formatDate(r.dateYyyymmdd)
            BodyCell(date, 1.2f)
            val soc = when {
                r.minSoc != null && r.maxSoc != null ->
                    if (r.minSoc == r.maxSoc) "${r.minSoc}%"
                    else "${r.minSoc}–${r.maxSoc}%"
                else -> "—"
            }
            BodyCell(soc, 1.4f)
            BodyCell(r.avgTempC?.let { "%.1f°".format(it) } ?: "—", 0.9f)
            BodyCell(r.dischargeAh?.let { "%.2f".format(it) } ?: "—", 0.9f)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Computed from samples on this device. Days with no sampling are omitted.",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextTertiary
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = GlassColors.TextTertiary,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyCell(text: String, weight: Float) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = GlassColors.TextPrimary,
        modifier = Modifier.weight(weight)
    )
}

private fun formatDate(yyyymmdd: Int): String {
    val y = yyyymmdd / 10000
    val m = (yyyymmdd / 100) % 100
    val d = yyyymmdd % 100
    return String.format(Locale.US, "%04d-%02d-%02d", y, m, d)
}

private fun String.format(vararg args: Any): String =
    String.format(Locale.US, this, *args)
