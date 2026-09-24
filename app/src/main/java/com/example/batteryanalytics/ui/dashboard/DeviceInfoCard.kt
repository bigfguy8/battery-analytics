package com.example.batteryanalytics.ui.dashboard

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.example.batteryanalytics.ui.components.GlassCard
import com.example.batteryanalytics.ui.theme.GlassColors

@Composable
fun DeviceInfoCard() {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Device",
            style = MaterialTheme.typography.labelLarge,
            color = GlassColors.TextSecondary,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        InfoLine("Manufacturer", Build.MANUFACTURER ?: "unknown")
        InfoLine("Model", Build.MODEL ?: "unknown")
        InfoLine("Android", "API ${Build.VERSION.SDK_INT}  (${Build.VERSION.RELEASE})")
        InfoLine("Build", Build.ID ?: "unknown")
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = GlassColors.TextPrimary,
            fontWeight = FontWeight.Medium
        )
    }
}
