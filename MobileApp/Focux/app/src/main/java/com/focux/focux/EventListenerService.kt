package com.focux.focux

import android.app.Notification
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationManagerCompat
import com.focux.focux.db.LogEvent
import com.focux.focux.LogWriter
import java.util.concurrent.TimeUnit

class EventListenerService : Service() {

    companion object {
        @Volatile
        var isRunning = false
    }

    private val screenReceiver = ScreenReceiver()
    private var registered = false
    private var foregroundStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private var isStopping = false
    private var lastLoggedTimestamp = 0L

    private val usageStatsRunnable = object : Runnable {
        override fun run() {
            fetchUsageStats()
            //// Schedule the next run for 1 minute later
            //handler.postDelayed(this, TimeUnit.MINUTES.toMillis(1))
            // Poll more frequently for near real-time updates
            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(5))
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        lastLoggedTimestamp = System.currentTimeMillis()
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
        isRunning = false
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
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastLoggedTimestamp, now)
        var latestTimestampInBatch = 0L

        while (events.hasNextEvent()) {
            val event = UsageEvents.Event()
            events.getNextEvent(event)

            if (event.timeStamp > latestTimestampInBatch) {
                latestTimestampInBatch = event.timeStamp
            }

            val eventAction = eventTypeToString(event.eventType)

            val logEvent = LogEvent(
                timestamp = event.timeStamp,
                eventType = "USAGE_EVENT",
                packageName = event.packageName,
                eventAction = eventAction
            )
            LogWriter.append(this, logEvent)
        }
        // Update the timestamp to the time of the last event processed to avoid re-logging
        if (latestTimestampInBatch > lastLoggedTimestamp) {
            lastLoggedTimestamp = latestTimestampInBatch
        }
    }

    private fun eventTypeToString(eventType: Int): String = when (eventType) {
        UsageEvents.Event.NONE -> "NONE"

        // NOTE: These share values with the deprecated MOVE_TO_* constants.
        UsageEvents.Event.ACTIVITY_RESUMED -> "ACTIVITY_RESUMED"   // (= MOVE_TO_FOREGROUND)
        UsageEvents.Event.ACTIVITY_PAUSED -> "ACTIVITY_PAUSED"     // (= MOVE_TO_BACKGROUND)

        UsageEvents.Event.ACTIVITY_STOPPED -> "ACTIVITY_STOPPED"
        UsageEvents.Event.CONFIGURATION_CHANGE -> "CONFIGURATION_CHANGE"
        UsageEvents.Event.USER_INTERACTION -> "USER_INTERACTION"
        UsageEvents.Event.SHORTCUT_INVOCATION -> "SHORTCUT_INVOCATION"
        UsageEvents.Event.STANDBY_BUCKET_CHANGED -> "STANDBY_BUCKET_CHANGED"

        UsageEvents.Event.SCREEN_INTERACTIVE -> "SCREEN_INTERACTIVE"
        UsageEvents.Event.SCREEN_NON_INTERACTIVE -> "SCREEN_NON_INTERACTIVE"

        UsageEvents.Event.KEYGUARD_SHOWN -> "KEYGUARD_SHOWN"
        UsageEvents.Event.KEYGUARD_HIDDEN -> "KEYGUARD_HIDDEN"

        UsageEvents.Event.FOREGROUND_SERVICE_START -> "FOREGROUND_SERVICE_START"
        UsageEvents.Event.FOREGROUND_SERVICE_STOP -> "FOREGROUND_SERVICE_STOP"

        UsageEvents.Event.DEVICE_SHUTDOWN -> "DEVICE_SHUTDOWN"
        UsageEvents.Event.DEVICE_STARTUP -> "DEVICE_STARTUP"

        10 -> "NOTIFICATION_INTERRUPTION"
        12 -> "NOTIFICATION_SEEN"

        else -> "UNKNOWN ($eventType)"
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
