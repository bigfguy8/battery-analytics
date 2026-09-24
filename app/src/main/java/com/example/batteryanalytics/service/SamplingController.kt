package com.example.batteryanalytics.service

import android.content.Context
import com.example.batteryanalytics.data.repo.BatteryRepository
import com.example.batteryanalytics.data.repo.SamplingEngine
import com.example.batteryanalytics.domain.model.BatterySnapshot
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-scoped owner of the single SamplingEngine instance.
 *
 * Two parties can hold the engine open: the visible UI (MainActivity between
 * onStart and onStop) and the background service (between its onCreate and
 * onDestroy). The engine runs while at least one holder is active. When the
 * last holder releases, the engine flushes any in-progress session and stops.
 *
 * This is the only place that decides whether the engine is running. Nothing
 * else should call SamplingEngine.start() or .stop() directly.
 */
class SamplingController private constructor(context: Context) {

    private val engine = SamplingEngine(context.applicationContext)

    private var uiHolding = false
    private var serviceHolding = false

    val snapshots: StateFlow<BatterySnapshot?> get() = engine.snapshots
    fun repository(): BatteryRepository = engine.repository()

    @Synchronized
    fun onUiForeground() {
        uiHolding = true
        ensureRunning()
    }

    @Synchronized
    fun onUiBackground() {
        uiHolding = false
        if (!serviceHolding) {
            engine.flushOnShutdown()
            engine.stop()
        }
    }

    @Synchronized
    fun onServiceStart() {
        serviceHolding = true
        ensureRunning()
    }

    @Synchronized
    fun onServiceStop() {
        serviceHolding = false
        if (!uiHolding) {
            engine.flushOnShutdown()
            engine.stop()
        }
    }

    private fun ensureRunning() {
        if (!engine.isRunning()) engine.start()
    }

    companion object {
        @Volatile private var instance: SamplingController? = null

        fun get(context: Context): SamplingController =
            instance ?: synchronized(this) {
                instance ?: SamplingController(context.applicationContext).also { instance = it }
            }
    }
}
