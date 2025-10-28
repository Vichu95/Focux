package com.focux.focux

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder

class EventListenerService : Service() {

    private val screenReceiver = ScreenReceiver()
    private var registered = false
    private var foregroundStarted = false

    override fun onCreate() {
        super.onCreate()
        LogWriter.append(this, "SERVICE_CREATED")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Use the exact notification passed from StarterActivity if present
        val bootstrap = intent?.getParcelableExtra<Notification>("bootstrap_notification")
        ensureForegroundSafely(bootstrap)
        registerScreenReceiverSafely()
        LogWriter.append(this, "SERVICE_START_STICKY")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        LogWriter.append(this, "SERVICE_DESTROYED")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForegroundSafely(bootstrap: Notification?) {
        if (foregroundStarted) return
        try {
            val n = bootstrap ?: ForegroundNotification.build(this)
            // Adopt the SAME ID used by StarterActivity
            startForeground(ForegroundNotification.NOTIF_ID, n)
            foregroundStarted = true
            LogWriter.append(this, "FOREGROUND_STARTED")
        } catch (t: Throwable) {
            LogWriter.append(this, "FOREGROUND_FAILED:${t::class.java.simpleName}")
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
            LogWriter.append(this, "RECEIVER_REGISTERED")
        } catch (t: Throwable) {
            LogWriter.append(this, "RECEIVER_REGISTER_FAILED:${t::class.java.simpleName}")
        }
    }

    private fun unregisterScreenReceiver() {
        if (!registered) return
        runCatching { unregisterReceiver(screenReceiver) }
        registered = false
        LogWriter.append(this, "RECEIVER_UNREGISTERED")
    }
}
