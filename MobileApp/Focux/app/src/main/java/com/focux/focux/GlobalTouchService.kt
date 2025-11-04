package com.focux.focux

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.focux.focux.db.LogEvent

class GlobalTouchService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val eventAction = AccessibilityEvent.eventTypeToString(eventType)
        val packageName = event.packageName?.toString()

        val logEvent = LogEvent(
            eventType = "ACCESSIBILITY_EVENT",
            eventAction = eventAction,
            packageName = packageName
        )
        LogWriter.append(this, logEvent)
    }

    override fun onInterrupt() {
        val logEvent = LogEvent(
            eventType = "SERVICE_LIFECYCLE",
            eventAction = "ACCESSIBILITY_SERVICE_INTERRUPTED"
        )
        LogWriter.append(this, logEvent)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val logEvent = LogEvent(
            eventType = "SERVICE_LIFECYCLE",
            eventAction = "ACCESSIBILITY_SERVICE_CONNECTED"
        )
        LogWriter.append(this, logEvent)
    }
}
