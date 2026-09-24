package com.example.batteryanalytics.domain.estimate

/**
 * Parses the taperJson blob written by SessionAccumulator:
 *   {"20":1.45,"30":1.52,"40":1.48,...}
 * Returns a map from SOC band start (multiple of 10) to mean charge current (A).
 * Returns an empty map on any parse failure; never throws.
 */
internal object TaperParse {
    fun parse(json: String?): Map<Int, Double> {
        if (json.isNullOrBlank()) return emptyMap()
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
                .toMap()
        } catch (_: Throwable) { emptyMap() }
    }

    /** Merge many sessions' taper curves into one band -> mean-rate map. */
    fun merge(sessions: List<com.example.batteryanalytics.domain.model.SessionRow>): Map<Int, Double> {
        val sums = HashMap<Int, Double>()
        val counts = HashMap<Int, Int>()
        for (s in sessions) {
            for ((band, rate) in parse(s.taperJson)) {
                sums[band] = (sums[band] ?: 0.0) + rate
                counts[band] = (counts[band] ?: 0) + 1
            }
        }
        return sums.mapValues { (b, v) -> v / counts[b]!! }
    }
}
