package com.focux.focux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focux.focux.db.LogEvent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val bootCtx =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else context

        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                val logEvent = LogEvent(
                    eventType = "SYSTEM_EVENT",
                    eventAction = "BOOT_BROADCAST",
                    eventValue = action
                )
                LogWriter.append(bootCtx, logEvent)
                startSvc(bootCtx)
            }
        }
    }

    private fun startSvc(ctx: Context) {
        val svc = Intent(ctx, EventListenerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(svc)
        } else {
            ctx.startService(svc)
        }
    }
}
