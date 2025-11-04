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
import com.focux.focux.db.LogEvent
import java.util.concurrent.TimeUnit

class EventListenerService : Service() {

    private val screenReceiver = ScreenReceiver()
    private var registered = false
    private var foregroundStarted = false
    private val handler = Handler(Looper.getMainLooper())
    private val usageStatsRunnable = object : Runnable {
        override fun run() {
            fetchUsageStats()
            // Schedule the next run
            handler.postDelayed(this, TimeUnit.HOURS.toMillis(2))
        }
    }

    override fun onCreate() {
        super.onCreate()
        logLifecycleEvent("SERVICE_CREATED")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val bootstrap = intent?.getParcelableExtra<Notification>("bootstrap_notification")
        ensureForegroundSafely(bootstrap)
        registerScreenReceiverSafely()
        logLifecycleEvent("SERVICE_START_STICKY")

        // Start the periodic usage stats job
        handler.post(usageStatsRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        // Stop the periodic job
        handler.removeCallbacks(usageStatsRunnable)
        logLifecycleEvent("SERVICE_DESTROYED")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForegroundSafely(bootstrap: Notification?) {
        if (foregroundStarted) return
        try {
            val n = bootstrap ?: ForegroundNotification.build(this)
            startForeground(ForegroundNotification.NOTIF_ID, n)
            foregroundStarted = true
            logLifecycleEvent("FOREGROUND_STARTED")
        } catch (t: Throwable) {
            logLifecycleEvent("FOREGROUND_FAILED", t::class.java.simpleName)
            stopSelf()
        }
    }

    private fun registerScreenReceiverSafely() {
        if (registered) return
        try {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, f)
            registered = true
            logLifecycleEvent("RECEIVER_REGISTERED")
        } catch (t: Throwable) {
            logLifecycleEvent("RECEIVER_REGISTER_FAILED", t::class.java.simpleName)
        }
    }

    private fun unregisterScreenReceiver() {
        if (!registered) return
        runCatching { unregisterReceiver(screenReceiver) }
        registered = false
        logLifecycleEvent("RECEIVER_UNREGISTERED")
    }

    private fun fetchUsageStats() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - TimeUnit.HOURS.toMillis(2) // Last 2 hours
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        stats.forEach { stat ->
            val totalTime = TimeUnit.MILLISECONDS.toMinutes(stat.totalTimeInForeground)
            if (totalTime > 0) {
                val logEvent = LogEvent(
                    eventType = "APP_USAGE",
                    eventAction = stat.packageName,
                    eventValue = totalTime.toString()
                )
                LogWriter.append(this, logEvent)
            }
        }
    }

    private fun logLifecycleEvent(action: String, value: String? = null) {
        val logEvent = LogEvent(
            eventType = "SERVICE_LIFECYCLE",
            eventAction = action,
            eventValue = value
        )
        LogWriter.append(this, logEvent)
    }
}
