package com.focux.focux

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.focux.focux.db.LogEvent
import com.focux.focux.LogWriter
import kotlin.math.abs

class GlobalTouchService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val eventAction = when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "CLICKED"
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> "LONG_CLICKED"
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    when {
                        abs(event.scrollDeltaY) > abs(event.scrollDeltaX) -> {
                            if (event.scrollDeltaY > 0) "SCROLL_DOWN" else "SCROLL_UP"
                        }
                        else -> {
                            if (event.scrollDeltaX > 0) "SCROLL_RIGHT" else "SCROLL_LEFT"
                        }
                    }
                } else {
                    "SCROLLED" // Fallback for older Android versions
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (event.packageName == "com.android.systemui") {
                    "SYSTEM_UI_WINDOW"
                } else {
                    "WINDOW_CHANGED"
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> "TEXT_CHANGED"
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> "FOCUSED"
            else -> null // Ignore other event types
        }

        // Only log the event if it's one we care about
        eventAction?.let {
            val logEvent = LogEvent(
                timestamp = event.eventTime,
                eventType = "UI_INTERACTION",
                packageName = event.packageName?.toString(),
                eventAction = it
            )
            LogWriter.append(this, logEvent)
        }
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
