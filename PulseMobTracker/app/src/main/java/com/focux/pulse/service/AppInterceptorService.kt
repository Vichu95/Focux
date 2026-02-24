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

    // ── Doom Scroll Detection (in-memory, no DB hit per event) ────────
    // All window changes count — no exclusions (configurable after testing)
    private val recentSwitches = ArrayDeque<Long>()

    // Cached config — only reloaded from DB once per minute
    @Volatile private var doomWindowMs: Long = 20_000L
    @Volatile private var doomThreshold: Int = 6
    @Volatile private var doomConfigLoadedAt: Long = 0L

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
            
            // Prevent self-interception
            if (packageName == "com.focux.pulse") return

            val now = System.currentTimeMillis()

            // ── Doom Scroll Detection ──────────────────────────────────────────
            // Defualt: 6 switches in 20s. We allow ALL TYPE_WINDOW_STATE_CHANGED 
            // events to count. This is required to detect "fidgeting" on the home
            // screen where the packageName and className might remain identical
            // across multiple swipes/interactions.
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                recentSwitches.addLast(now)
                // Prune entries older than the window
                while (recentSwitches.isNotEmpty() && (now - recentSwitches.first()) > doomWindowMs) {
                    recentSwitches.removeFirst()
                }
                if (recentSwitches.size >= doomThreshold) {
                    Log.d("AppInterceptor", "Doom scroll detected! ${recentSwitches.size} switches in ${doomWindowMs / 1000}s")
                    recentSwitches.clear() // Reset so next episode can fire immediately
                    scope.launch {
                        val db = PulseDatabase.getDatabase(applicationContext)
                        val breathingDuration = db.analyticsDao().getState("limit_breathing_duration")?.toIntOrNull() ?: 4
                        launchDoomScrollBreathing(packageName, breathingDuration)
                    }
                    return // Don't also apply per-app limits for the same event
                }
            }

            // Refresh doom scroll config from DB at most once per minute (non-blocking)
            if (now - doomConfigLoadedAt > 60_000L) {
                doomConfigLoadedAt = now // Set immediately so concurrent events don't also trigger a refresh
                scope.launch {
                    try {
                        val db = PulseDatabase.getDatabase(applicationContext)
                        doomWindowMs = (db.analyticsDao().getState("limit_doomscroll_window_secs")?.toLongOrNull() ?: 30L) * 1000L
                        doomThreshold = db.analyticsDao().getState("limit_doomscroll_threshold")?.toIntOrNull() ?: 4
                    } catch (e: Exception) {
                        Log.e("AppInterceptor", "Error refreshing doom scroll config", e)
                    }
                }
            }

            // ── Per-App Limit Check ─────────────────────────────────────────
            // Cleanup expired whitelist entries
            temporarilyAllowedApps.entries.removeIf { it.value < now }
            if (temporarilyAllowedApps.containsKey(packageName)) return

            // Debounce: Don't intercept the same app multiple times within 5 seconds
            if (packageName != lastInterceptedPackage || (now - lastInterceptTime) > 5000) {
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
                            Log.d("AppInterceptor", "Intercepting App: $packageName (Category: $category)")
                            launchBreathingScreen(packageName, actualSessionMins, actualDailyMins, usedDailyMins, actualOpens, usedOpens, breathingDuration)
                        }
                    } catch (e: Exception) {
                        Log.e("AppInterceptor", "Error querying app limits", e)
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

    override fun onInterrupt() {
        Log.e("AppInterceptor", "Service interrupted")
    }
}
