package com.focux.focux

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focux.focux.db.AppDatabase
import com.focux.focux.db.LogEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ContentCaptureService : AccessibilityService() {

    private val db by lazy { AppDatabase.getDatabase(this) }
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val rootInActiveWindow = rootInActiveWindow
            rootInActiveWindow?.let {
                logVisibleText(it, event.packageName?.toString())
            }
        }
    }

    private fun logVisibleText(nodeInfo: AccessibilityNodeInfo, packageName: String?) {
        if (nodeInfo.childCount == 0) {
            if (nodeInfo.text != null && nodeInfo.text.isNotEmpty()) {
                Log.d("ContentCaptureService", "Text: ${nodeInfo.text}")
                scope.launch {
                    db.logEventDao().insert(
                        LogEvent(
                            eventType = "ACCESSIBILITY_EVENT",
                            packageName = packageName,
                            eventAction = "CONTENT_CAPTURE",
                            eventValue = nodeInfo.text.toString()
                        )
                    )
                }
            }
        } else {
            for (i in 0 until nodeInfo.childCount) {
                val child = nodeInfo.getChild(i)
                child?.let {
                    logVisibleText(it, packageName)
                }
            }
        }
    }

    override fun onInterrupt() {
        // Not used
    }
}
