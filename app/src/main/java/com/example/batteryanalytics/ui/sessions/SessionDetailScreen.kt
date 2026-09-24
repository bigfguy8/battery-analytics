package com.example.batteryanalytics.ui.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.domain.model.SessionRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Detail view for a single session. Renders every recorded field. Missing
 * fields are shown as "not recorded" in bodySmall — never as 0.
 *
 * If the session has a taper curve (JSON), it is rendered as a simple bar
 * chart in Canvas: one bar per SOC band, height proportional to the mean
 * charge current observed in that band. Empty bands are skipped, not zero.
 */
@Composable
fun SessionDetailScreen(session: SessionRow, onBack: () -> Unit) {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) { Text("\u2190 Back") }
        Text("Session #${session.id}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Field("Start", fmt.format(Date(session.startTs)))
        Field("End", session.endTs?.let { fmt.format(Date(it)) } ?: "open")
        val durationMs = session.endTs?.let { it - session.startTs }
        Field(
            "Duration",
            durationMs?.let {
                val mins = it / 60_000.0
                if (mins < 60) "%.1f min".format(mins)
                else "%.2f h".format(mins / 60.0)
            } ?: "not recorded"
        )
        Field("SoC start", session.socStart?.let { "$it %" } ?: "not recorded")
        Field("SoC end", session.socEnd?.let { "$it %" } ?: "not recorded")
        Field("Charge added", session.chargeAh?.let { "%.3f Ah".format(it) } ?: "not recorded")
        Field("Energy added", session.energyWh?.let { "%.2f Wh".format(it) } ?: "not recorded")
        Field("Peak power", session.peakPowerW?.let { "%.2f W".format(it) } ?: "not recorded")
        Field("Avg power", session.avgPowerW?.let { "%.2f W".format(it) } ?: "not recorded")
        Field("Plug type", session.plugType?.label ?: "not recorded")
        Field("Temp min", session.tempMinC?.let { "%.1f \u00B0C".format(it) } ?: "not recorded")
        Field("Temp mean", session.tempMeanC?.let { "%.1f \u00B0C".format(it) } ?: "not recorded")
        Field("Temp max", session.tempMaxC?.let { "%.1f \u00B0C".format(it) } ?: "not recorded")
        Field("Quality", session.quality.label)

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("Taper curve", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Mean charge current observed in each 10-% SoC band during this session. " +
                "Bands with no samples are omitted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        val taper = parseTaper(session.taperJson)
        if (taper.isEmpty()) {
            Text(
                "No taper data recorded (session too short or samples missing).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            TaperChart(taper)
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Parse the taperJson string into a sorted list of (bandStart, meanAmps). */
private fun parseTaper(json: String?): List<Pair<Int, Double>> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        json.trim().removePrefix("{").removeSuffix("}")
            .split(",")
            .mapNotNull { pair ->
                val parts = pair.split(":")
                if (parts.size != 2) return@mapNotNull null
                val k = parts[0].trim().trim('"').toIntOrNull() ?: return@mapNotNull null
                val v = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                k to v
            }
            .sortedBy { it.first }
    } catch (_: Throwable) { emptyList() }
}

@Composable
private fun TaperChart(taper: List<Pair<Int, Double>>) {
    val barColor = MaterialTheme.colorScheme.primary
    val labelStyle = androidx.compose.ui.text.TextStyle(
        fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    androidx.compose.foundation.Canvas(
        Modifier.fillMaxWidth().height(160.dp)
    ) {
        val padLeft = 44.dp.toPx()
        val padRight = 8.dp.toPx()
        val padTop = 8.dp.toPx()
        val padBottom = 22.dp.toPx()
        val w = size.width - padLeft - padRight
        val h = size.height - padTop - padBottom
        if (w <= 0f || h <= 0f || taper.isEmpty()) return@Canvas

        val maxA = taper.maxOf { it.second }.coerceAtLeast(0.01)
        val barW = w / taper.size

        for ((i, pair) in taper.withIndex()) {
            val (_, a) = pair
            val bh = (a / maxA * h).toFloat()
            val x = padLeft + i * barW + barW * 0.15f
            val y = padTop + h - bh
            drawRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, y),
                size = androidx.compose.ui.geometry.Size(barW * 0.7f, bh)
            )
            drawText(
                textMeasurer = textMeasurer,
                text = "${pair.first}",
                topLeft = androidx.compose.ui.geometry.Offset(
                    padLeft + i * barW + barW * 0.15f,
                    padTop + h + 2f
                ),
                style = labelStyle
            )
        }

        // Same precision rule as the LineChart: pick decimals from the axis span.
        val maxLabel = when {
            maxA >= 1.0    -> "%.2f A".format(maxA)
            maxA >= 0.1    -> "%.3f A".format(maxA)
            maxA >= 0.01   -> "%.4f A".format(maxA)
            else           -> "%.2e A".format(maxA)
        }
        drawText(
            textMeasurer = textMeasurer,
            text = maxLabel,
            topLeft = androidx.compose.ui.geometry.Offset(0f, padTop - 6f),
            style = labelStyle
        )
        drawText(
            textMeasurer = textMeasurer,
            text = "0",
            topLeft = androidx.compose.ui.geometry.Offset(0f, padTop + h - 12f),
            style = labelStyle
        )
    }
    Text(
        "x axis: SOC % band start.  y axis: mean charge current (A).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private fun String.format(vararg args: Any): String =
    java.lang.String.format(Locale.US, this, *args)
