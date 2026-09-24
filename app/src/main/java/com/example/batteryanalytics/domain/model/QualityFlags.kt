package com.example.batteryanalytics.domain.model

object QualityFlags {
    const val NONE             = 0
    const val GAP_BEFORE       = 1 shl 0
    const val GAP_AFTER        = 1 shl 1
    const val COUNTER_RESET    = 1 shl 2
    const val SIGN_INCONSISTENT = 1 shl 3
    const val PARTIAL_DATA     = 1 shl 4
}
