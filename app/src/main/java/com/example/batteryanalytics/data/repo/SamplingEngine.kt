package com.example.batteryanalytics.data.repo

import android.content.Context
import com.example.batteryanalytics.data.prefs.Prefs
import com.example.batteryanalytics.data.source.SourceResolver
import com.example.batteryanalytics.domain.model.BatterySnapshot
import com.example.batteryanalytics.domain.model.QualityFlags
import com.example.batteryanalytics.domain.model.Source
import com.example.batteryanalytics.domain.model.SourceFlags
import com.example.batteryanalytics.domain.model.TelemetrySample
import com.example.batteryanalytics.domain.model.SessionQuality
import com.example.batteryanalytics.domain.session.SessionAccumulator
import com.example.batteryanalytics.domain.session.SessionDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the sampling loop. Reads a snapshot from SourceResolver every
 * [intervalMs] milliseconds, persists it, feeds the session detector,
 * and publishes the snapshot on [snapshots] for the UI to consume.
 *
 * The engine is lifecycle-scoped: MainActivity starts it in onCreate and
 * stops it on destroy. It does not run in the background — that requires
 * a foreground service, which is out of scope for Phase 3 (spec §17).
 *
 * Capabilities are persisted once, after the very first successful sample.
 * Retention runs once on engine start.
 */
class SamplingEngine(
    context: Context,
    private val repository: BatteryRepository = BatteryRepository(context)
) {

    private val prefs = Prefs(context)
    private val appContext = context.applicationContext
    private val resolver = SourceResolver(appContext)
    private val detector = SessionDetector()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var capabilitiesPersisted = false

    private val _snapshots = MutableStateFlow<BatterySnapshot?>(null)
    val snapshots: StateFlow<BatterySnapshot?> = _snapshots.asStateFlow()

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            recoverInterruptedSession()
            runRetentionOnce()
            refreshRecentRollups()
            var tick = 0L
            while (isActive) {
                try {
                    tickOnce()
                } catch (t: Throwable) {
                    // Never let the sampling loop die from a transient read failure.
                    // The DB stays the source of truth; a single missed tick is fine.
                }
                tick++
                delay(prefs.samplingIntervalMs)
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    /**
     * True when the sampling loop coroutine is currently active. Used by
     * SamplingController to avoid starting twice when both the UI and the
     * background service are holding a reference.
     */
    fun isRunning(): Boolean = loopJob?.isActive == true

    /** Force-close any open session and persist it. Call from onDestroy. */
    fun flushOnShutdown() {
        val closed = detector.flush(System.currentTimeMillis()) ?: return
        if (closed is SessionDetector.Output.Closed) {
            try { repository.insertSession(closed.session) } catch (_: Throwable) {}
        }
    }

    fun shutdown() {
        flushOnShutdown()
        stop()
        scope.cancel()
    }

    fun repository(): BatteryRepository = repository

    // --------------------------------------------------------------- internals

    private suspend fun runRetentionOnce() {
        try {
            repository.applyRetention(System.currentTimeMillis())
        } catch (_: Throwable) {
            // Retention failure is non-fatal; we retry on next launch.
        }
    }

    private suspend fun tickOnce() {
        val snap = resolver.read()
        _snapshots.value = snap

        if (!capabilitiesPersisted) {
            try {
                repository.persistCapabilities(snap.capabilityRows, snap.timestampMs)
                capabilitiesPersisted = true
            } catch (_: Throwable) { /* retry next tick */ }
        }

        val sample = toSample(snap)
        try {
            repository.insertSample(sample)
        } catch (_: Throwable) {
            return  // skip detector on write failure: session and DB would diverge
        }

        for (out in detector.onSample(sample)) {
            when (out) {
                is SessionDetector.Output.Opened -> {
                    prefs.pendingSessionStartTs = out.startTs
                }
                is SessionDetector.Output.Closed -> {
                    try {
                        repository.insertSession(out.session)
                        // Recompute today's rollup now, so History does not have
                        // to wait for the next app launch to reflect the session.
                        val today = BatteryRepository.yyyymmdd(System.currentTimeMillis())
                        repository.recomputeDailyRollup(today)
                    } catch (_: Throwable) {}
                    prefs.pendingSessionStartTs = null
                }
            }
        }
    }

    /**
     * If the previous run ended without a normal close (hard kill, low-memory
     * eviction, reboot), the detector never wrote the session and never
     * cleared the pending-session marker. The raw samples it collected are
     * already in the DB. This method reconstructs the session from those
     * samples, closes it with quality = REBOOT_BOUNDARY, and clears the marker.
     *
     * Nothing is invented: if the DB has no samples in the window, no session
     * is written.
     */
    /**
     * Recompute daily rollups for the last three days, plus today. Runs on
     * engine start so that whatever History shows is up to date even if the
     * device has been idle overnight. Days with no samples are skipped inside
     * BatteryRepository.recomputeDailyRollup.
     */
    private fun refreshRecentRollups() {
        try {
            val now = System.currentTimeMillis()
            val dayMs = 24L * 3600_000L
            for (offset in 0..3) {
                val dayStart = now - offset * dayMs
                repository.recomputeDailyRollup(BatteryRepository.yyyymmdd(dayStart))
            }
        } catch (_: Throwable) { /* non-fatal */ }
    }

    private fun recoverInterruptedSession() {
        val startTs = prefs.pendingSessionStartTs ?: return
        try {
            val now = System.currentTimeMillis()
            val samples = repository.samplesInRange(startTs, now)
            if (samples.isEmpty()) return

            val acc = SessionAccumulator.open(samples.first())
            for (i in 1 until samples.size) {
                acc.add(samples[i])
            }
            val row = acc.close(
                endTs = samples.last().tsMs,
                quality = SessionQuality.REBOOT_BOUNDARY
            )
            repository.insertSession(row)
            val today = BatteryRepository.yyyymmdd(System.currentTimeMillis())
            repository.recomputeDailyRollup(today)
        } catch (_: Throwable) {
            // Recovery failure is non-fatal: we lose the aggregated session
            // statistics but never corrupt the DB.
        } finally {
            prefs.pendingSessionStartTs = null
        }
    }

    private fun toSample(s: BatterySnapshot): TelemetrySample {
        val sourceFlags = SourceFlags.maskFor(
            s.soc.source, s.voltageV.source, s.currentNowA.source,
            s.powerW.source, s.tempC.source
        )
        // Quality flag if we know the current sign disagrees with status. This is
        // an informational flag only; the value is still persisted as read.
        var quality = QualityFlags.NONE
        val status = s.status.value
        val i = s.currentNowA.value
        if (status != null && i != null) {
            val signInconsistent =
                (status == com.example.batteryanalytics.domain.model.ChargeStatus.CHARGING && i < 0.0) ||
                (status == com.example.batteryanalytics.domain.model.ChargeStatus.DISCHARGING && i > 0.0)
            if (signInconsistent) quality = quality or QualityFlags.SIGN_INCONSISTENT
        }
        if (s.soc.value == null || s.voltageV.value == null) {
            quality = quality or QualityFlags.PARTIAL_DATA
        }
        return TelemetrySample(
            tsMs = s.timestampMs,
            socPct = s.soc.value,
            voltageV = s.voltageV.value,
            currentA = s.currentNowA.value,
            powerW = s.powerW.value,
            tempC = s.tempC.value,
            statusTxt = s.status.value?.name,
            plugTxt = s.plugType.value?.name,
            sourceFlags = sourceFlags,
            qualityFlags = quality
        )
    }
}
