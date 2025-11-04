package com.focux.focux

import android.Manifest
import android.app.Notification
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.focux.focux.db.LogEvent

class StarterActivity : ComponentActivity() {

    private val permReq = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            logLifecycleEvent("POST_NOTIFICATIONS_DENIED")
            openNotifSettings(); finish(); return@registerForActivityResult
        }
        proceed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            permReq.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        proceed()
    }

    private fun proceed() {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            logLifecycleEvent("NOTIF_TOGGLE_DISABLED")
            openNotifSettings(); finish(); return
        }

        ForegroundNotification.createTrackingChannel(this)
        val notif: Notification = ForegroundNotification.build(this)

        NotificationManagerCompat.from(this)
            .notify(ForegroundNotification.NOTIF_ID, notif)

        val svc = Intent(this, EventListenerService::class.java).apply {
            putExtra("bootstrap_notification", notif)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc)
        else startService(svc)

        logLifecycleEvent("SERVICE_REQUESTED_WITH_NOTIF")
        finish(); overridePendingTransition(0, 0)
    }

    private fun openNotifSettings() {
        startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun logLifecycleEvent(action: String) {
        val logEvent = LogEvent(
            eventType = "STARTER_LIFECYCLE",
            eventAction = action
        )
        LogWriter.append(this, logEvent)
    }
}
