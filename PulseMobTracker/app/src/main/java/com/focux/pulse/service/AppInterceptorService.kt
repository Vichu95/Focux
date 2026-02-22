package com.focux.pulse.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focux.pulse.ui.screens.Breathing.BreathingActivity

class AppInterceptorService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AppInterceptor", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            // Hardcoded testing apps for Phase 1
            // TODO: Replace with database lookup in Phase 2
            if (packageName == "com.instagram.android" || packageName == "com.google.android.youtube") {
                Log.d("AppInterceptor", "Intercepting: $packageName")
                launchBreathingScreen(packageName)
            }
        }
    }

    private fun launchBreathingScreen(targetPackage: String) {
        val intent = Intent(this, BreathingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_PACKAGE", targetPackage)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.e("AppInterceptor", "Service interrupted")
    }
}
