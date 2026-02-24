package com.focux.pulse.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import com.focux.pulse.utilities.Logger
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
    
    // ── Doom Scroll Detection ────────────────────────────────────────
    private val recentSwitches = ArrayDeque<Long>()
    
    // Cached config — reloaded occasionally (default: 5 switches / 30 secs)
    @Volatile private var doomWindowMs: Long = 30_000L
    @Volatile private var doomThreshold: Int = 5
    @Volatile private var doomConfigLoadedAt: Long = 0L
    
    private var imePackages: List<String> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Dynamically get all enabled keyboards (Input Methods) so we don't have to hardcode package names
        try {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) {
            Logger.e("AppInterceptor", "Failed to get IME packages", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // Only care about when a new window/app actually opens
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: "UnknownClass"

        // Prevent self-interception
        if (packageName == "com.focux.pulse") return
        
        // Ignore system UI and keyboards. They are background overlays, not conscious "app switches"
        if (packageName == "com.android.systemui" || packageName == "android" || packageName in imePackages) {
            return
        }

        // Check if the user has switched apps
        if (packageName == lastInterceptedPackage) return
        
        // Track the newly opened app
        lastInterceptedPackage = packageName
        
        val now = System.currentTimeMillis()

        // ── Doom Scroll Check ──────────────────────────────────────────
        recentSwitches.addLast(now)
        
        // Remove timestamps older than the window
        while (recentSwitches.isNotEmpty() && (now - recentSwitches.first()) > doomWindowMs) {
            recentSwitches.removeFirst()
        }
        
        // Check if we hit the limit
        if (recentSwitches.size >= doomThreshold) {
            recentSwitches.clear() // Reset so next episode can fire immediately
            
            scope.launch {
                val db = PulseDatabase.getDatabase(applicationContext)
                val breathingDuration = db.analyticsDao().getState("limit_breathing_duration")?.toIntOrNull() ?: 4
                launchDoomScrollBreathing(packageName, breathingDuration)
            }
            return // Stop here, no need to process per-app limits
        }

        // Refresh doom scroll config from DB at most once per minute
        if (now - doomConfigLoadedAt > 60_000L) {
            doomConfigLoadedAt = now
            scope.launch {
                try {
                    val db = PulseDatabase.getDatabase(applicationContext)
                    val windowSecs = db.analyticsDao().getState("limit_doomscroll_window_secs")?.toLongOrNull()
                    val threshold = db.analyticsDao().getState("limit_doomscroll_threshold")?.toIntOrNull()
                    if (windowSecs != null) doomWindowMs = windowSecs * 1000L
                    if (threshold != null) doomThreshold = threshold
                } catch (e: Exception) {
                    Logger.e("AppInterceptor", "Error refreshing doom scroll config", e)
                }
            }
        }

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
                
                if (isSpeedbump || isDailyExceeded || isOpensExceeded) {
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
                Logger.e("AppInterceptor", "Error querying app limits", e)
            }
        }
    }

    override fun onInterrupt() {
        Logger.d("AppInterceptor", "Service interrupted")
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

    private fun launchDoomScrollBreathing(targetPackage: String, breathingDuration: Int) {
        val intent = Intent(this, BreathingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("TARGET_PACKAGE", targetPackage)
            putExtra("SESSION_LIMIT_MINS", -1)
            putExtra("DAILY_LIMIT_MINS", -1)
            putExtra("USED_DAILY_MINS", 0)
            putExtra("OPENS_LIMIT", -1)
            putExtra("USED_OPENS", 0)
            putExtra("BREATHING_DURATION", breathingDuration)
            putExtra("IS_DOOM_SCROLL", true)
        }
        startActivity(intent)
    }
}
