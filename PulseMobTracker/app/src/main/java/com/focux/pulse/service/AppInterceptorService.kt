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
        
        fun allowAppContinuance(packageName: String, durationMs: Long) {
            temporarilyAllowedApps[packageName] = if (durationMs == -1L) Long.MAX_VALUE else System.currentTimeMillis() + durationMs
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
                
                // If we switched to a different app, clear the whitelist
                // so revisiting the previous app initiates a new breathing session.
                if (packageName != lastInterceptedPackage) {
                    temporarilyAllowedApps.clear()
                }

                lastInterceptedPackage = packageName
                lastInterceptTime = now
                
                scope.launch {
                    try {
                        val db = PulseDatabase.getDatabase(applicationContext)
                        val appInfo = db.appInfoDao().getAppInfo(packageName)
                        
                        val category = appInfo?.category ?: AppCategory.NEUTRAL
                        val customSession = appInfo?.sessionLimitMins
                        val globalSessionStr = db.analyticsDao().getState("limit_${category.lowercase()}_session")
                        
                        val actualSessionMins = if (customSession != null) customSession
                            else if (globalSessionStr != null) globalSessionStr.toIntOrNull()
                            else if (category == AppCategory.DISTRACTING) 5
                            else null
                            
                        val customDaily = appInfo?.dailyLimitMins
                        val globalDailyStr = db.analyticsDao().getState("limit_${category.lowercase()}_daily")
                        val actualDailyMins = if (customDaily != null) customDaily
                            else if (globalDailyStr != null) globalDailyStr.toIntOrNull()
                            else if (category == AppCategory.DISTRACTING) 30
                            else null

                        val customOpens = appInfo?.dailyOpensLimit
                        val globalOpensStr = db.analyticsDao().getState("limit_${category.lowercase()}_opens")
                        val actualOpens = if (customOpens != null) customOpens
                            else if (globalOpensStr != null) globalOpensStr.toIntOrNull()
                            else if (category == AppCategory.DISTRACTING) 10
                            else null

                        val breathingStr = db.analyticsDao().getState("limit_breathing_duration")
                        val breathingDuration = breathingStr?.toIntOrNull() ?: 4
                        
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        val todayStr = sdf.format(java.util.Date())
                        
                        val usedDailyMins = if (actualDailyMins != null) db.analyticsDao().getAppUsageMinsForDay(packageName, todayStr) else 0
                        val usedOpens = if (actualOpens != null) db.analyticsDao().getAppOpensForDay(packageName, todayStr) else 0
                        
                        val isDailyExceeded = actualDailyMins != null && usedDailyMins >= actualDailyMins
                        val isOpensExceeded = actualOpens != null && usedOpens >= actualOpens
                        val isSpeedbump = category == AppCategory.DISTRACTING || actualSessionMins != null
                        
                        // We intercept if it's DISTRACTING, or a Session Limit exists, OR if daily/opens limits are exceeded.
                        if (isSpeedbump || isDailyExceeded || isOpensExceeded) {
                            Log.d("AppInterceptor", "Intercepting App: $packageName (Category: $category)")
                            launchBreathingScreen(
                                packageName, actualSessionMins, actualDailyMins, usedDailyMins, 
                                actualOpens, usedOpens, breathingDuration
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("AppInterceptor", "Error querying app category", e)
                    }
                }
            }
        }
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
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        Log.e("AppInterceptor", "Service interrupted")
    }
}
