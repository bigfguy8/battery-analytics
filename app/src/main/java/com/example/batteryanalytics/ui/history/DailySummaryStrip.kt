package com.example.batteryanalytics.ui.history

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Every field is shown as an em-dash when null OR when the value carries no
 * information. A day with 0.00 Ah discharged is a day when the phone was on
 * the charger all day, or barely used: the meaningful answer is "no discharge
 * happened", not "0.00 Ah". Same reasoning for 0.00 average temperature,
 * which cannot physically occur and therefore means "no data".
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
        for (r in rollups) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                BodyCell(formatDate(r.dateYyyymmdd), 1.2f)
                BodyCell(socRange(r), 1.4f)
                BodyCell(tempCell(r.avgTempC), 0.9f)
                BodyCell(ahCell(r.dischargeAh), 0.9f)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Computed from samples on this device. Days with no sampling are omitted.",
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextTertiary
        )
    }
}

private fun socRange(r: DailyRollup): String = when {
    r.minSoc == null || r.maxSoc == null -> "\u2014"
    r.minSoc == r.maxSoc -> "${r.minSoc}%"
    else -> "${r.minSoc}\u2013${r.maxSoc}%"
}

private fun tempCell(v: Double?): String {
    // 0.0 °C is not a plausible battery temperature; treat it as no data.
    if (v == null || v == 0.0) return "\u2014"
    return String.format(Locale.US, "%.1f\u00B0", v)
}

private fun ahCell(v: Double?): String {
    // 0.00 Ah discharged means the phone was charging or idle all day.
    // The honest answer is "no discharge", not "0.00".
    if (v == null || v == 0.0) return "\u2014"
    return String.format(Locale.US, "%.2f", v)
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = GlassColors.TextTertiary,
        modifier = Modifier.weight(weight)
    )
}

@Composable
private fun RowScope.BodyCell(text: String, weight: Float) {
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
