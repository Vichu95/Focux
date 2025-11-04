package com.focux.focux

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * An AccessibilityService that captures and logs various user interaction events.
 * This service requires the user to grant special accessibility permissions to function.
 */
class GlobalTouchService : AccessibilityService() {

    /**
     * This callback is invoked when an accessibility event occurs.
     * We use it to log the type of event that happened and the app it occurred in.
     *
     * @param event The accessibility event that was captured.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val packageName = event.packageName ?: "Unknown"
        // We can get a human-readable string for the event type.
        val eventDescription = AccessibilityEvent.eventTypeToString(eventType)
        val logMessage = "ACCESSIBILITY_EVENT: $eventDescription in $packageName"
        LogWriter.append(this, logMessage)
    }

    /**
     * This callback is invoked when the system wants to interrupt the feedback your service is providing.
     */
    override fun onInterrupt() {
        LogWriter.append(this, "ACCESSIBILITY_SERVICE_INTERRUPTED")
    }

    /**
     * This callback is invoked when the system successfully connects to your service.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        LogWriter.append(this, "ACCESSIBILITY_SERVICE_CONNECTED")
    }
}
