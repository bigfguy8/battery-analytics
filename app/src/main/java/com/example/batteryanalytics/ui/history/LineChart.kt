package com.example.batteryanalytics.ui.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.batteryanalytics.domain.model.TelemetrySample

/**
 * A minimal line chart. Draws a single scalar channel from a list of samples.
 *
 * Handles null values by breaking the path. Does not interpolate across gaps.
 * If fewer than two non-null points exist, renders "not enough data" instead
 * of an empty box.
 *
 * [thresholds] is a list of (value, color) pairs drawn as horizontal dashed
 * lines, used for the temperature caution levels.
 */
@Composable
fun LineChart(
    samples: List<TelemetrySample>,
    selector: (TelemetrySample) -> Double?,
    color: Color,
    label: String,
    unit: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 130.dp,
    thresholds: List<Pair<Double, Color>> = emptyList()
) {
    val points = samples.mapNotNull { s ->
        selector(s)?.let { v -> s.tsMs to v }
    }
    val textMeasurer = rememberTextMeasurer()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        if (points.size < 2) {
            Box(Modifier.fillMaxWidth().height(height).padding(8.dp)) {
                Text(
                    "Not enough data in this window",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        val tMin = points.first().first
        val tMax = points.last().first
        val tSpan = (tMax - tMin).coerceAtLeast(1L).toDouble()

        var vMin = points.minOf { it.second }
        var vMax = points.maxOf { it.second }
        for ((thr, _) in thresholds) {
            if (thr < vMin) vMin = thr
            if (thr > vMax) vMax = thr
        }
        if (vMin == vMax) {
            vMin -= 0.5
            vMax += 0.5
        }
        val vSpan = vMax - vMin

        val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        val labelStyle = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Canvas(Modifier.fillMaxWidth().height(height)) {
            val padLeft = 42.dp.toPx()
            val padRight = 8.dp.toPx()
            val padTop = 8.dp.toPx()
            val padBottom = 18.dp.toPx()
            val w = size.width - padLeft - padRight
            val h = size.height - padTop - padBottom
            if (w <= 0f || h <= 0f) return@Canvas

            fun xFor(t: Long): Float =
                padLeft + ((t - tMin).toDouble() / tSpan * w).toFloat()
            fun yFor(v: Double): Float =
                padTop + ((vMax - v) / vSpan * h).toFloat()

            // Axis lines
            drawLine(axisColor, Offset(padLeft, padTop), Offset(padLeft, padTop + h), strokeWidth = 1f)
            drawLine(axisColor, Offset(padLeft, padTop + h), Offset(padLeft + w, padTop + h), strokeWidth = 1f)

            // Thresholds (dashed)
            for ((thr, thrColor) in thresholds) {
                if (thr < vMin || thr > vMax) continue
                val y = yFor(thr)
                drawLine(
                    color = thrColor.copy(alpha = 0.6f),
                    start = Offset(padLeft, y),
                    end = Offset(padLeft + w, y),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }

            // Path, broken at gaps
            val path = Path()
            var started = false
            var prevSampleTs: Long? = null
            val maxGapMs = ((tMax - tMin) / 200).coerceAtLeast(60_000L)
            for (s in samples) {
                val v = selector(s) ?: continue
                val px = xFor(s.tsMs)
                val py = yFor(v)
                val gapTooBig = prevSampleTs?.let { (s.tsMs - it) > maxGapMs } == true
                if (!started || gapTooBig) {
                    path.moveTo(px, py)
                    started = true
                } else {
                    path.lineTo(px, py)
                }
                prevSampleTs = s.tsMs
            }
            drawPath(path, color = color, style = Stroke(width = 2f))

            // Y-axis min/max labels
            val span = vMax - vMin
            drawText(
                textMeasurer = textMeasurer,
                text = formatAxisValue(vMax, span),
                topLeft = Offset(0f, padTop - 6f),
                style = labelStyle
            )
            drawText(
                textMeasurer = textMeasurer,
                text = formatAxisValue(vMin, span),
                topLeft = Offset(0f, padTop + h - 12f),
                style = labelStyle
            )

            // X-axis endpoints (HH:mm:ss or date)
            val fmt = java.text.SimpleDateFormat(
                if (tSpan > 36L * 3600_000L) "MM-dd" else "HH:mm",
                java.util.Locale.US
            )
            drawText(
                textMeasurer = textMeasurer,
                text = fmt.format(java.util.Date(tMin)),
                topLeft = Offset(padLeft, padTop + h + 2f),
                style = labelStyle
            )
            val endLabel = fmt.format(java.util.Date(tMax))
            val endMeasured = textMeasurer.measure(endLabel, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = endLabel,
                topLeft = Offset(padLeft + w - endMeasured.size.width, padTop + h + 2f),
                style = labelStyle
            )
        }
        Text(
            "unit: $unit",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Small helper so internal format specifiers stay Locale.US. */
private fun String.format(vararg args: Any): String =
    java.lang.String.format(java.util.Locale.US, this, *args)

/**
 * Format a chart axis label with enough precision to distinguish two values
 * that are close together. Uses the span between vMin and vMax to choose
 * decimals; falls back to scientific notation for tiny spans.
 *
 * Rule of thumb:
 *   span >= 10     -> 1 decimal  (e.g. SoC in %)
 *   span >= 1      -> 2 decimals (e.g. voltage, current)
 *   span >= 0.1    -> 3 decimals
 *   span >= 0.01   -> 4 decimals
 *   span >= 0.001  -> 5 decimals
 *   else           -> scientific notation, 2 mantissa decimals
 */
private fun formatAxisValue(v: Double, span: Double): String {
    val a = kotlin.math.abs(v)
    if (span >= 10.0)   return "%.1f".format(v)
    if (span >= 1.0)    return "%.2f".format(v)
    if (span >= 0.1)    return "%.3f".format(v)
    if (span >= 0.01)   return "%.4f".format(v)
    if (span >= 0.001)  return "%.5f".format(v)
    // Absolute value below 1 mV / 1 mW etc. Show in scientific notation.
    return "%.2e".format(v)
}

