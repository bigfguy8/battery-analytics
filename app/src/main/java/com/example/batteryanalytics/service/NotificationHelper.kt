package com.example.batteryanalytics.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.batteryanalytics.MainActivity
import com.example.batteryanalytics.R

/**
 * Notification channel and notification construction for the foreground
 * service. Created lazily; safe to call from any thread.
 *
 * The channel is IMPORTANCE_LOW with no sound and no vibration: this is a
 * persistent status notification, not an alert. Users who dislike it can
 * hide it via the system's notification settings for this app, and the
 * service will keep running.
 */
object NotificationHelper {

    const val CHANNEL_ID = "battery_monitor"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(com.example.batteryanalytics.R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(com.example.batteryanalytics.R.string.notif_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun build(context: Context, subtitle: String): Notification {
        ensureChannel(context)
        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Request code 0 is safe here because this is the only PendingIntent we
        // build. If a notification action (e.g. a "Stop monitoring" button) is
        // ever added, give it a distinct request code to avoid collision.
        val pi = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.batteryanalytics.R.drawable.ic_stat_battery)
            .setContentTitle(context.getString(com.example.batteryanalytics.R.string.notif_title))
            .setContentText(subtitle)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
