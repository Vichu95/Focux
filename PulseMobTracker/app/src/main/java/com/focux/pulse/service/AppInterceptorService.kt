package com.focux.pulse.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.AppCategory
import com.focux.pulse.ui.screens.Breathing.BreathingActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppInterceptorService : AccessibilityService() {

    private var lastInterceptedPackage: String = ""
    private var lastInterceptTime: Long = 0
    private val scope = CoroutineScope(Dispatchers.IO)

    companion object {
        // Map of <PackageName, ExpirationTimeMs>
        // Apps in this map won't be intercepted until their time expires
        val temporarilyAllowedApps = mutableMapOf<String, Long>()
        
        fun allowAppContinuance(packageName: String, durationMs: Long = 300000) { // Default 5 mins
            temporarilyAllowedApps[packageName] = System.currentTimeMillis() + durationMs
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AppInterceptor", "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val packageName = event.packageName?.toString() ?: return
            
            // Prevent self-interception or looping
            if (packageName == "com.focux.pulse" || packageName == "com.android.systemui") return
            
            // Cleanup expired whitelist entries occasionally
            val now = System.currentTimeMillis()
            temporarilyAllowedApps.entries.removeIf { it.value < now }

            // Check if app is currently whitelisted (user just clicked "Proceed")
            if (temporarilyAllowedApps.containsKey(packageName)) return

            // Debounce: Don't intercept the same app multiple times within a 5-second window
            if (packageName != lastInterceptedPackage || (now - lastInterceptTime) > 5000) {
                lastInterceptedPackage = packageName
                lastInterceptTime = now
                
                scope.launch {
                    try {
                        val db = PulseDatabase.getDatabase(applicationContext)
                        val appInfo = db.appInfoDao().getAppInfo(packageName)
                        
                        // We only intercept if the user explicitly categorized it as DISTRACTING
                        if (appInfo?.category == AppCategory.DISTRACTING) {
                            Log.d("AppInterceptor", "Intercepting Distracting App: $packageName (Event: ${event.eventType})")
                            launchBreathingScreen(packageName)
                        }
                    } catch (e: Exception) {
                        Log.e("AppInterceptor", "Error querying app category", e)
                    }
                }
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
