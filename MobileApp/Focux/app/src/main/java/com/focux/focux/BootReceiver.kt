package com.focux.focux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.focux.focux.db.LogEvent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        LogWriter.append(context, LogEvent(eventType = "SYSTEM_EVENT", eventAction = "BOOT_RECEIVER_TRIGGERED", eventValue = action))

        val bootCtx =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.createDeviceProtectedStorageContext()
            } else context

        when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                LogWriter.append(bootCtx, LogEvent(eventType = "SYSTEM_EVENT", eventAction = "BOOT_ACTION_RECEIVED", eventValue = action))
                startSvc(bootCtx)
            }
        }
    }

    private fun startSvc(ctx: Context) {
        try {
            val svc = Intent(ctx, EventListenerService::class.java)
            LogWriter.append(ctx, LogEvent(eventType = "SYSTEM_EVENT", eventAction = "BOOT_SERVICE_STARTING"))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(svc)
            } else {
                ctx.startService(svc)
            }
            LogWriter.append(ctx, LogEvent(eventType = "SYSTEM_EVENT", eventAction = "BOOT_SERVICE_STARTED_OK"))
        } catch (t: Throwable) {
            LogWriter.append(ctx, LogEvent(eventType = "SYSTEM_EVENT", eventAction = "BOOT_SERVICE_START_FAILED", eventValue = t.message))
        }
    }
}
