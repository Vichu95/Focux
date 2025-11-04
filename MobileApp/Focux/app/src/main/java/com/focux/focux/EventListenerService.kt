package com.focux.focux

import android.app.Notification
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import com.focux.focux.db.LogEvent
import java.util.concurrent.TimeUnit

class EventListenerService : Service() {

    private val screenReceiver = ScreenReceiver()
    private var registered = false
    private var foregroundStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private var isStopping = false

    private val usageStatsRunnable = object : Runnable {
        override fun run() {
            fetchUsageStats()
            handler.postDelayed(this, TimeUnit.HOURS.toMillis(2))
        }
    }

    override fun onCreate() {
        super.onCreate()
        logLifecycleEvent("SERVICE_LIFECYCLE", "SERVICE_CREATED")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            isStopping = true
            stopSelf()
            return START_NOT_STICKY
        }

        isStopping = false
        logLifecycleEvent("SERVICE_LIFECYCLE", "SERVICE_START_COMMAND_RECEIVED")
        val bootstrap = intent?.getParcelableExtra<Notification>("bootstrap_notification")
        ensureForegroundSafely(bootstrap)
        registerScreenReceiverSafely()
        logLifecycleEvent("SERVICE_LIFECYCLE", "SERVICE_START_STICKY")

        handler.removeCallbacks(usageStatsRunnable)
        handler.post(usageStatsRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        handler.removeCallbacks(usageStatsRunnable)
        logLifecycleEvent("SERVICE_LIFECYCLE", "SERVICE_DESTROYED")

        if (!isStopping) {
            val notification = ForegroundNotification.buildTrackingStoppedNotification(this)
            NotificationManagerCompat.from(this).notify(ForegroundNotification.ALERT_NOTIF_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForegroundSafely(bootstrap: Notification?) {
        if (foregroundStarted) return
        try {
            logLifecycleEvent("SERVICE_LIFECYCLE", "ENSURE_FOREGROUND_CALLED")
            val n = bootstrap ?: ForegroundNotification.build(this)
            startForeground(ForegroundNotification.NOTIF_ID, n)
            foregroundStarted = true
            logLifecycleEvent("SERVICE_LIFECYCLE", "FOREGROUND_STARTED_OK")
        } catch (t: Throwable) {
            logLifecycleEvent("SERVICE_LIFECYCLE", "FOREGROUND_START_FAILED", t.message)
            stopSelf()
        }
    }

    private fun registerScreenReceiverSafely() {
        if (registered) return
        try {
            logLifecycleEvent("SERVICE_LIFECYCLE", "REGISTER_RECEIVER_CALLED")
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, f)
            registered = true
            logLifecycleEvent("SERVICE_LIFECYCLE", "RECEIVER_REGISTERED_OK")
        } catch (t: Throwable) {
            logLifecycleEvent("SERVICE_LIFECYCLE", "RECEIVER_REGISTER_FAILED", t.message)
        }
    }

    private fun unregisterScreenReceiver() {
        if (!registered) return
        runCatching { unregisterReceiver(screenReceiver) }
        registered = false
        logLifecycleEvent("SERVICE_LIFECYCLE", "RECEIVER_UNREGISTERED")
    }

    private fun fetchUsageStats() {
        // ... (implementation is the same)
    }

    private fun logLifecycleEvent(type: String, action: String, value: String? = null) {
        val logEvent = LogEvent(
            eventType = type,
            eventAction = action,
            eventValue = value
        )
        LogWriter.append(this, logEvent)
    }
}
