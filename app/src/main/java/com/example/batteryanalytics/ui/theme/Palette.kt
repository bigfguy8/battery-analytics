package com.example.batteryanalytics.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Extended palette. Each metric tile gets its own accent so the dashboard
 * reads as distinct information rather than a wall of identical glass.
 *
 * All colors are tuned for a dark background: mid-saturation, 65-80 % luminance,
 * so they pop against the near-black base without blowing out.
 */
object Palette {
    // Background blobs / gradient accents
    val BlobTopLeft    = Color(0xFF2A1E5C)   // deep indigo
    val BlobTopRight   = Color(0xFF103A44)   // teal
    val BlobBottomLeft = Color(0xFF3A1F33)   // plum
    val BlobBottomRight= Color(0xFF132C4A)   // navy

    // Metric accents
    val SoC           = Color(0xFF7DE0A6)   // mint green
    val Temperature   = Color(0xFFFFB168)   // warm amber
    val Voltage       = Color(0xFF7DA8FF)   // sky blue
    val ChargeCounter = Color(0xFFB58CE6)   // lilac
    val CurrentNow    = Color(0xFFFF8FA8)   // rose
    val Power         = Color(0xFF6EE7DD)   // cyan

    // Secondary
    val CurrentAvg    = Color(0xFF8FB8FF)
    val EnergyCounter = Color(0xFFD4B46A)   // gold

    // Nav
    val NavDash       = Color(0xFF7DE0A6)
    val NavHistory    = Color(0xFF7DA8FF)
    val NavSessions   = Color(0xFFB58CE6)
    val NavCaps       = Color(0xFFFFB168)

    // Status chip palette
    val ChipGreen     = Color(0xFF7DE0A6)
    val ChipBlue      = Color(0xFF7DA8FF)
    val ChipAmber     = Color(0xFFFFB168)
    val ChipRose      = Color(0xFFFF8FA8)
    val ChipViolet    = Color(0xFFB58CE6)

    /** Soft translucent version of an accent, for the tile's icon well / fill. */
    fun soft(color: Color, alpha: Float = 0.18f): Color =
        color.copy(alpha = alpha)
}
