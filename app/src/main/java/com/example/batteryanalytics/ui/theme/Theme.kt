package com.example.batteryanalytics.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Palette.Voltage,
    onPrimary = Color.Black,
    secondary = Palette.ChargeCounter,
    tertiary = Palette.Temperature,
    background = GlassColors.BaseBackgroundBottom,
    onBackground = GlassColors.TextPrimary,
    surface = Color(0xFF16161E),
    onSurface = GlassColors.TextPrimary,
    surfaceVariant = Color(0xFF1D1D27),
    onSurfaceVariant = GlassColors.TextSecondary,
    error = GlassColors.AccentRed,
    outline = GlassColors.BorderFaint
)

@Composable
fun BatteryAnalyticsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
