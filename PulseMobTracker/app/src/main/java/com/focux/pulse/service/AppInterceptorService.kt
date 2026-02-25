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

    companion object {
        var instance: AppInterceptorService? = null
    }

    private var lastInterceptedPackage: String = ""
    private val scope = CoroutineScope(Dispatchers.IO)
    private var activeSessionJob: kotlinx.coroutines.Job? = null
    
    // ── Doom Scroll Detection ────────────────────────────────────────
    private val recentSwitches = ArrayDeque<Long>()
    
    // Cached config — reloaded occasionally (default: 5 switches / 30 secs)
    @Volatile private var doomWindowMs: Long = 30_000L
    @Volatile private var doomThreshold: Int = 5
    @Volatile private var mindfulConfigLoadedAt: Long = 0L
    
    private var imePackages: List<String> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        
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
        if (now - mindfulConfigLoadedAt > 60_000L) {
            mindfulConfigLoadedAt = now
            scope.launch {
                try {
                    val db = PulseDatabase.getDatabase(applicationContext)
                    val windowSecs = db.analyticsDao().getState("mindful_doomscroll_window_secs")?.toLongOrNull()
                    val threshold = db.analyticsDao().getState("mindful_doomscroll_threshold")?.toIntOrNull()
                    if (windowSecs != null) doomWindowMs = windowSecs * 1000L
                    if (threshold != null) doomThreshold = threshold
                } catch (e: Exception) {
                    Logger.e("AppInterceptor", "Error refreshing mindful config", e)
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

                val baseBreathingDuration = db.analyticsDao().getState("mindful_base_duration")?.toIntOrNull() ?: 4
                val penaltyMultiplier = db.analyticsDao().getState("mindful_penalty_multiplier")?.toIntOrNull() ?: 3
                
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date())
                
                val usedDailyMins = if (actualDailyMins != null) db.analyticsDao().getAppUsageMinsForDay(packageName, todayStr) else 0
                val usedOpens = if (actualOpens != null) db.analyticsDao().getAppOpensForDay(packageName, todayStr) else 0
                
                val isDailyExceeded = actualDailyMins != null && usedDailyMins >= actualDailyMins
                val isOpensExceeded = actualOpens != null && usedOpens >= actualOpens
                
                // Only act as a launch speedbump for Distracting apps, OR if daily/open limits are exceeded.
                // If it's just a Neutral app with a session limit, we don't speedbump the launch, we just run the timer!
                val isSpeedbump = category == AppCategory.DISTRACTING
                
                // Apply penalty if they breached a daily limit
                val finalBreathingDuration = if (isDailyExceeded || isOpensExceeded) {
                    baseBreathingDuration * penaltyMultiplier
                } else {
                    baseBreathingDuration
                }
                
                if (isSpeedbump || isDailyExceeded || isOpensExceeded) {
                    // Stop any existing timer since they are in Breathing
                    activeSessionJob?.cancel()
                    launchBreathingScreen(
                        targetPackage = packageName, 
                        sessionLimitMins = actualSessionMins, 
                        dailyLimitMins = actualDailyMins, 
                        usedDailyMins = usedDailyMins, 
                        opensLimit = actualOpens, 
                        usedOpens = usedOpens, 
                        breathingDuration = finalBreathingDuration,
                        isTimeout = false
                    )
                } else if (actualSessionMins != null) {
                    // No speedbump required, but we need to start the timer!
                    startSessionTimer(packageName, actualSessionMins, finalBreathingDuration)
                } else {
                    // No limits at all for this app. Cancel any timers running from previous apps.
                    activeSessionJob?.cancel()
                }
            } catch (e: Exception) {
                Logger.e("AppInterceptor", "Error querying app limits", e)
            }
        }
    }

    override fun onInterrupt() {
        Logger.d("AppInterceptor", "Service interrupted")
        instance = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeSessionJob?.cancel()
    }
    
    fun startSessionTimer(packageName: String, sessionLimitMins: Int, breathingDuration: Int) {
        activeSessionJob?.cancel()
        if (sessionLimitMins <= 0) return
        
        Logger.d("AppInterceptor", "Starting session timer for $packageName (${sessionLimitMins}m)")
        activeSessionJob = scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(sessionLimitMins * 60 * 1000L)
            
            // If they are still using this same app when the timer fires
            if (lastInterceptedPackage == packageName) {
                Logger.d("AppInterceptor", "Session expired for $packageName!")
                launchBreathingScreen(
                    targetPackage = packageName,
                    sessionLimitMins = sessionLimitMins,
                    dailyLimitMins = null,
                    usedDailyMins = 0,
                    opensLimit = null,
                    usedOpens = 0,
                    breathingDuration = breathingDuration,
                    isTimeout = true
                )
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
        breathingDuration: Int,
        isTimeout: Boolean
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
            putExtra("IS_TIMEOUT", isTimeout)
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
            putExtra("IS_TIMEOUT", false)
        }
        startActivity(intent)
    }
}
