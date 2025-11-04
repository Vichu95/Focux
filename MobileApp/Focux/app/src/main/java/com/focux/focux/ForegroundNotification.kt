package com.focux.focux

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

object ForegroundNotification {
    private const val TRACKING_CHANNEL_ID = "focux_tracking"
    private const val ALERT_CHANNEL_ID = "focux_alerts"

    const val NOTIF_ID = 1001
    const val ALERT_NOTIF_ID = 1002

    fun build(context: Context): Notification {
        createTrackingChannel(context)
        val tapIntent = Intent(context, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            context, 0, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, TRACKING_CHANNEL_ID)
            .setContentTitle("Focux tracking active")
            .setContentText("Logging screen & unlock events")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    fun buildTrackingStoppedNotification(context: Context): Notification {
        createAlertChannel(context)
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        val contentIntent = PendingIntent.getActivity(
            context, 1, tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setContentTitle("Focux Tracking Stopped")
            .setContentText("Tap to restart the tracking service.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    }

    fun createTrackingChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(TRACKING_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    TRACKING_CHANNEL_ID, "Focux Background Tracking", NotificationManager.IMPORTANCE_MIN
                )
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun createAlertChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    ALERT_CHANNEL_ID, "Focux Alerts", NotificationManager.IMPORTANCE_HIGH
                )
                nm.createNotificationChannel(ch)
            }
        }
    }
}
