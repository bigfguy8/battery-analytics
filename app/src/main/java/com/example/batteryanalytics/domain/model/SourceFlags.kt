package com.example.batteryanalytics.domain.model

object SourceFlags {
    const val API        = 1 shl 0
    const val SYSFS      = 1 shl 1
    const val CALCULATED = 1 shl 2
    const val ESTIMATED  = 1 shl 3

    fun maskFor(vararg sources: Source): Int {
        var m = 0
        for (s in sources) m = m or when (s) {
            Source.API -> API
            Source.SYSFS -> SYSFS
            Source.CALCULATED -> CALCULATED
            Source.ESTIMATED -> ESTIMATED
            Source.UNAVAILABLE -> 0
        }
        return m
    }
}
