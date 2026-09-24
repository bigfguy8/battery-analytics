package com.example.batteryanalytics.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the sampling engine alive while the user has
 * opted in to background monitoring in Settings.
 *
 * The service itself does nothing beyond:
 *   - holding a SamplingController reference so the engine keeps running
 *   - posting a persistent notification (required by Android for any
 *     foreground service)
 *   - updating that notification with a live status line
 *
 * It does not run its own loop, does not touch the DB directly, and does not
 * start additional work. All sampling is inside SamplingEngine.
 *
 * Started and stopped only by explicit user action: enabling the toggle in
 * Settings, or the app re-checking the toggle on launch. It is never started
 * implicitly by the system.
 */
class BatteryMonitorService : Service() {

    companion object {
        private const val ACTION_START = "com.example.batteryanalytics.action.START_MONITOR"
        private const val ACTION_STOP = "com.example.batteryanalytics.action.STOP_MONITOR"

        /**
         * Ask the system to start the monitor service. Swallows the specific
         * exceptions that Android may throw when the process is in the
         * background and the FGS type is not allowed at that moment; the
         * service will be retried on the next UI foreground.
         */
        fun start(context: Context) {
            val i = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_START
            }
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, i)
            } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
                // Called from a background context that Android does not
                // currently permit. Harmless: user will retry from the UI.
            } catch (_: IllegalStateException) {
                // Some OEMs throw IllegalStateException on denied FGS start.
            }
        }

        fun stop(context: Context) {
            val i = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var notifJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = com.example.batteryanalytics.data.prefs.Prefs(this)

        // If Android restarted us with a null intent (START_STICKY recovery)
        // but the user has since disabled background monitoring, do not come
        // back to life.
        if (intent == null && !prefs.backgroundMonitoringEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START or null (system restart).
                try {
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID,
                        NotificationHelper.build(this, getString(com.example.batteryanalytics.R.string.notif_text_initial))
                    )
                } catch (_: SecurityException) {
                    // POST_NOTIFICATIONS denied. On Android 13+ we cannot show
                    // the required notification, so the service cannot run.
                    // Stop cleanly and let the Settings toggle reflect it.
                    stopSelf()
                    return START_NOT_STICKY
                } catch (_: android.app.ForegroundServiceStartNotAllowedException) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                SamplingController.get(this).onServiceStart()
                startNotificationUpdates()
                return START_STICKY
            }
        }
    }

    private fun startNotificationUpdates() {
        if (notifJob?.isActive == true) return
        val controller = SamplingController.get(this)
        notifJob = scope.launch {
            controller.snapshots.collect { snap ->
                val text = if (snap == null) {
                    getString(com.example.batteryanalytics.R.string.notif_text_waiting)
                } else {
                    val soc = snap.soc.value?.let { "$it%" } ?: "SoC ?"
                    val temp = snap.tempC.value?.let { "%.1f\u00B0C".format(it) } ?: "? \u00B0C"
                    "$soc  \u00B7  $temp"
                }
                try {
                    NotificationManagerCompat.from(this@BatteryMonitorService)
                        .notify(
                            NotificationHelper.NOTIFICATION_ID,
                            NotificationHelper.build(this@BatteryMonitorService, text)
                        )
                } catch (_: SecurityException) {
                    // POST_NOTIFICATIONS revoked at runtime. Android allows the
                    // foreground service to keep running even then; the
                    // notification is simply not shown.
                }
            }
        }
    }

    override fun onDestroy() {
        notifJob?.cancel()
        notifJob = null
        SamplingController.get(this).onServiceStop()
        scope.cancel()
        super.onDestroy()
    }
}

private fun String.format(v: Double): String =
    java.lang.String.format(java.util.Locale.US, this, v)
