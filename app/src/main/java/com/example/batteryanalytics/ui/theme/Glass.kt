package com.example.batteryanalytics.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

object GlassColors {
    val BaseBackgroundTop = Color(0xFF0E0E17)
    val BaseBackgroundBottom = Color(0xFF08080E)

    val GlassTint = Color.White.copy(alpha = 0.06f)
    val GlassTintFaint = Color.White.copy(alpha = 0.03f)

    val BorderBright = Color.White.copy(alpha = 0.24f)
    val BorderFaint  = Color.White.copy(alpha = 0.05f)
    val BorderMedium = Color.White.copy(alpha = 0.12f)

    val TextPrimary   = Color(0xFFF2F2F7)
    val TextSecondary = Color(0xFFA8A8B6)
    val TextTertiary  = Color(0xFF6E6E7C)

    // Kept for compatibility with anything still referencing the old names.
    val AccentBlue   = Palette.Voltage
    val AccentGreen  = Palette.SoC
    val AccentAmber  = Palette.Temperature
    val AccentRed    = Color(0xFFE68383)
    val AccentViolet = Palette.ChargeCounter
}

val GlassShape: Shape = RoundedCornerShape(18.dp)
val GlassShapeSmall: Shape = RoundedCornerShape(14.dp)

/**
 * Standard glass treatment: translucent white tint + top-lit gradient border.
 */
fun Modifier.glass(
    shape: Shape = GlassShape,
    tint: Color = GlassColors.GlassTint
): Modifier = this
    .clip(shape)
    .background(tint)
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                GlassColors.BorderBright,
                GlassColors.BorderFaint,
                GlassColors.BorderMedium
            )
        ),
        shape = shape
    )

/**
 * Colorful glass: same idea, but the tint is a soft accent and the border
 * carries the accent color at the top-left. Use for metric tiles so each one
 * has its own identity.
 */
fun Modifier.colorfulGlass(
    accent: Color,
    shape: Shape = GlassShapeSmall,
    tintAlpha: Float = 0.10f
): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = tintAlpha),
                Color.White.copy(alpha = 0.03f)
            )
        )
    )
    .border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = 0.55f),
                accent.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.10f)
            )
        ),
        shape = shape
    )
