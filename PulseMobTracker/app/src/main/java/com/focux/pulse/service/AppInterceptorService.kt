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
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Only care about when a new window/app actually opens
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: "UnknownClass"

        // Prevent self-interception
        if (packageName == "com.focux.pulse") return

        // Check if the user has switched apps
        if (packageName == lastInterceptedPackage) return
        
        // Track the newly opened app
        Log.d("AppInterceptor", "App Opened: $packageName | Class: $className")
        lastInterceptedPackage = packageName

        // Evaluate limits for the new app
        scope.launch {
            try {
                val db = PulseDatabase.getDatabase(applicationContext)
                val appInfo = db.appInfoDao().getAppInfo(packageName)
                
                val category = appInfo?.category ?: AppCategory.NEUTRAL
                val customSession = appInfo?.sessionLimitMins
                val globalSessionStr = db.analyticsDao().getState("limit_${category.lowercase()}_session")
                val actualSessionMins = customSession
                    ?: globalSessionStr?.toIntOrNull()
                    ?: if (category == AppCategory.DISTRACTING) 5 else null
                    
                val customDaily = appInfo?.dailyLimitMins
                val globalDailyStr = db.analyticsDao().getState("limit_${category.lowercase()}_daily")
                val actualDailyMins = customDaily
                    ?: globalDailyStr?.toIntOrNull()
                    ?: if (category == AppCategory.DISTRACTING) 30 else null

                val customOpens = appInfo?.dailyOpensLimit
                val globalOpensStr = db.analyticsDao().getState("limit_${category.lowercase()}_opens")
                val actualOpens = customOpens
                    ?: globalOpensStr?.toIntOrNull()
                    ?: if (category == AppCategory.DISTRACTING) 10 else null

                val breathingDuration = db.analyticsDao().getState("limit_breathing_duration")?.toIntOrNull() ?: 4
                
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date())
                
                val usedDailyMins = if (actualDailyMins != null) db.analyticsDao().getAppUsageMinsForDay(packageName, todayStr) else 0
                val usedOpens = if (actualOpens != null) db.analyticsDao().getAppOpensForDay(packageName, todayStr) else 0
                
                val isDailyExceeded = actualDailyMins != null && usedDailyMins >= actualDailyMins
                val isOpensExceeded = actualOpens != null && usedOpens >= actualOpens
                val isSpeedbump = category == AppCategory.DISTRACTING || actualSessionMins != null
                
                Log.d("AppInterceptor", "Limits for $packageName -> Category: $category | Speedbump: $isSpeedbump | Daily: $usedDailyMins/$actualDailyMins | Opens: $usedOpens/$actualOpens")
                
                if (isSpeedbump || isDailyExceeded || isOpensExceeded) {
                    Log.d("AppInterceptor", "Intercepting App: $packageName (Category: $category)")
                    launchBreathingScreen(
                        packageName, 
                        actualSessionMins, 
                        actualDailyMins, 
                        usedDailyMins, 
                        actualOpens, 
                        usedOpens, 
                        breathingDuration
                    )
                }
            } catch (e: Exception) {
                Log.e("AppInterceptor", "Error querying app limits", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.d("AppInterceptor", "Service interrupted")
    }

    private fun launchBreathingScreen(
        targetPackage: String, 
        sessionLimitMins: Int?,
        dailyLimitMins: Int?,
        usedDailyMins: Int,
        opensLimit: Int?,
        usedOpens: Int,
        breathingDuration: Int
    ) {
        val intent = Intent(this, BreathingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_PACKAGE", targetPackage)
            putExtra("SESSION_LIMIT_MINS", sessionLimitMins ?: -1)
            putExtra("DAILY_LIMIT_MINS", dailyLimitMins ?: -1)
            putExtra("USED_DAILY_MINS", usedDailyMins)
            putExtra("OPENS_LIMIT", opensLimit ?: -1)
            putExtra("USED_OPENS", usedOpens)
            putExtra("BREATHING_DURATION", breathingDuration)
            putExtra("IS_DOOM_SCROLL", false)
        }
        startActivity(intent)
    }
}
