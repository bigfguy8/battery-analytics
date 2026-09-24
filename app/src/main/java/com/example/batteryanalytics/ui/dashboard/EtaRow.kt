package com.example.batteryanalytics.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.batteryanalytics.domain.estimate.CapacityEstimator
import com.example.batteryanalytics.domain.estimate.ChargingEtaEstimator
import com.example.batteryanalytics.domain.estimate.DischargeEtaEstimator
import com.example.batteryanalytics.domain.model.BatterySnapshot
import com.example.batteryanalytics.domain.model.ChargeStatus
import com.example.batteryanalytics.domain.model.Confidence
import com.example.batteryanalytics.domain.model.SessionRow
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors
import com.example.batteryanalytics.ui.theme.GlassShapeSmall
import com.example.batteryanalytics.ui.theme.Palette
import com.example.batteryanalytics.ui.theme.colorfulGlass

/**
 * ETA strip on the Dashboard. Renders only when there is an ETA to show:
 *   - Charging: three tiles (to 80, 90, 100)
 *   - Discharging: one tile (to 5)
 *   - Otherwise: nothing (not a fabricated placeholder)
 *
 * Every tile carries source + confidence. Banded integration results are
 * labelled "banded taper"; naive results say so explicitly.
 */
@Composable
fun EtaRow(
    snapshot: BatterySnapshot,
    recentSessions: List<SessionRow>
) {
    val status = snapshot.status.value
    val full = remember(snapshot.timestampMs, recentSessions) {
        val fromSessions = CapacityEstimator.estimateFullCapacityFromSessions(recentSessions)
        if (fromSessions.isAvailable) fromSessions
        else CapacityEstimator.estimateFullCapacityAh(snapshot.chargeCounterAh, snapshot.soc)
    }

    when (status) {
        ChargeStatus.CHARGING, ChargeStatus.FULL -> {
            val eta = ChargingEtaEstimator.estimate(
                soc = snapshot.soc,
                status = snapshot.status,
                currentA = snapshot.currentNowA,
                currentAvgA = snapshot.currentAvgA,
                estimatedFullAh = full,
                recentSessions = recentSessions,
                targets = listOf(80, 90, 100)
            )
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (eta.any { it.available }) Palette.ChipGreen
                                else GlassColors.TextTertiary
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Estimated time to charge",
                        style = MaterialTheme.typography.labelLarge,
                        color = GlassColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        chargingRateLabel(snapshot),
                        style = MaterialTheme.typography.labelSmall,
                        color = GlassColors.TextTertiary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (p in eta) {
                        EtaTile(
                            label = "${p.targetPct} %",
                            minutes = p.minutes,
                            source = p.source.name,
                            confidence = p.confidence,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                val anyAvailable = eta.any { it.available }
                val hint = when {
                    anyAvailable ->
                        "Charging tapers above 80 %. Values are estimated from history " +
                            "when available, otherwise naive extrapolation at the current rate."
                    isCurrentBelowFloorForHint(snapshot) ->
                        "Screen-on use can mask the charging current. Turn the screen off " +
                            "briefly to let the estimator sample the true rate."
                    else ->
                        "Charging tapers above 80 %. Values are estimated from history " +
                            "when available, otherwise naive extrapolation at the current rate."
                }
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextTertiary
                )
            }
        }
        ChargeStatus.DISCHARGING -> {
            val r = DischargeEtaEstimator.estimate(
                soc = snapshot.soc,
                status = snapshot.status,
                currentAvgA = snapshot.currentAvgA,
                estimatedFullAh = full,
                targetPct = 5
            )
            if (!r.available) return
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Palette.ChipBlue)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Estimated time remaining",
                        style = MaterialTheme.typography.labelLarge,
                        color = GlassColors.TextSecondary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    EtaTile(
                        label = "to 5 %",
                        minutes = r.minutes,
                        source = r.source.name,
                        confidence = r.confidence,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Target is 5 %, not 0 %. Phones shut down above 0 %.",
                    style = MaterialTheme.typography.labelSmall,
                    color = GlassColors.TextTertiary
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun EtaTile(
    label: String,
    minutes: Int?,
    source: String,
    confidence: Confidence,
    modifier: Modifier = Modifier
) {
    val accent = when (confidence) {
        Confidence.HIGH -> Palette.ChipGreen
        Confidence.MEDIUM -> Palette.ChipAmber
        Confidence.LOW -> Palette.ChipRose
        Confidence.UNAVAILABLE -> GlassColors.TextTertiary
    }
    Column(
        modifier
            .colorfulGlass(accent = accent, shape = GlassShapeSmall, tintAlpha = 0.12f)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = GlassColors.TextSecondary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            formatMinutes(minutes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = GlassColors.TextPrimary
        )
        Text(
            "${source.lowercase()} \u00B7 ${confidence.name.lowercase()}",
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.85f)
        )
    }
}

/**
 * Live rate for the ETA card. Shows the larger of |current_now| and |current_avg|,
 * which is what the estimator uses. If both are below the trust floor, says so
 * rather than showing a tiny number that looks like zero.
 */
private fun chargingRateLabel(s: com.example.batteryanalytics.domain.model.BatterySnapshot): String {
    val now = s.currentNowA.value?.let { kotlin.math.abs(it) } ?: 0.0
    val avg = s.currentAvgA.value?.let { kotlin.math.abs(it) } ?: 0.0
    val mag = maxOf(now, avg)
    return when {
        mag == 0.0 -> "no current reading"
        mag < ChargingEtaEstimator.MIN_TRUSTED_CURRENT_A ->
            "%.1f mA \u2014 below floor".format(mag * 1000.0)
        else -> "at %.2f A".format(mag)
    }
}

/**
 * True when both instantaneous and average currents are below the estimator's
 * trust floor but the charger is connected. On most devices this is caused by
 * the phone consuming most of the input power with the screen on, leaving only
 * a small net current into the cell. It is not a bug in the reading.
 */
private fun isCurrentBelowFloorForHint(
    s: com.example.batteryanalytics.domain.model.BatterySnapshot
): Boolean {
    val now = s.currentNowA.value?.let { kotlin.math.abs(it) } ?: 0.0
    val avg = s.currentAvgA.value?.let { kotlin.math.abs(it) } ?: 0.0
    return maxOf(now, avg) < ChargingEtaEstimator.MIN_TRUSTED_CURRENT_A
}

private fun formatMinutes(minutes: Int?): String {
    if (minutes == null) return "\u2014"
    if (minutes == 0) return "now"
    if (minutes < 60) return "${minutes}m"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "${h}h" else "${h}h ${m}m"
}
