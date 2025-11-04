package com.focux.focux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focux.focux.db.LogEvent

class ScreenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            Intent.ACTION_SCREEN_ON -> "SCREEN_ON"
            Intent.ACTION_SCREEN_OFF -> "SCREEN_OFF"
            Intent.ACTION_USER_PRESENT -> "UNLOCKED"
            else -> return // Ignore unknown actions
        }

        val logEvent = LogEvent(
            eventType = "SCREEN_EVENT",
            eventAction = action
        )
        LogWriter.append(context, logEvent)
    }
}
