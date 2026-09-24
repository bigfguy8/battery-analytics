package com.example.batteryanalytics.domain.model

/** One row of the Capabilities screen. Describes what this device exposes for a metric. */
data class CapabilityRow(
    val key: String,
    val label: String,
    val available: Boolean,
    val source: Source,
    val unit: Unit,
    val confidence: Confidence,
    val method: String,
    val rawString: String? = null
)
