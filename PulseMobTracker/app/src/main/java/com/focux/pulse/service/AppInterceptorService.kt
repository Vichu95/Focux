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
    
    @Volatile private var exemptPackage: String? = null
    @Volatile private var exemptExpiry: Long = 0L
    
    private val recentSwitches = ArrayDeque<Long>()
    @Volatile private var doomWindowMs: Long = 60_000L
    @Volatile private var doomThreshold: Int = 8
    @Volatile private var mindfulConfigLoadedAt: Long = 0L
    private var imePackages: List<String> = emptyList()

    @Volatile private var globalPauseExpiry: Long = 0L
    private val lastCloseTimestampMap = mutableMapOf<String, Long>()
    private val sessionStartTimes = mutableMapOf<String, Long>()

    fun pauseInterventions(minutes: Long) {
        globalPauseExpiry = System.currentTimeMillis() + (minutes * 60_000L)
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }
        } catch (e: Exception) {
            Logger.e("AppInterceptor", "Failed to get IME packages", e)
        }
        
        scope.launch {
            com.focux.pulse.utilities.ConfigInitializer.initializeDefaults(
                com.focux.pulse.data.local.PulseDatabase.getDatabase(applicationContext)
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        
        val packageName = event.packageName?.toString() ?: return
        if (packageName == "com.focux.pulse") return
        if (packageName == "com.android.systemui" || packageName == "android" || packageName in imePackages) return
        if (packageName == lastInterceptedPackage) return
        
        val now = System.currentTimeMillis()
        val lastLeftPackage = lastInterceptedPackage
        if (lastLeftPackage.isNotEmpty()) {
            lastCloseTimestampMap[lastLeftPackage] = now
        }

        lastInterceptedPackage = packageName

        // ── Global Snooze/Pause Interventions Check ────────────────────
        if (now < globalPauseExpiry) {
            Logger.d("AppInterceptor", "Interventions paused. Skipping checks.")
            return
        }

        recentSwitches.addLast(now)
        while (recentSwitches.isNotEmpty() && (now - recentSwitches.first()) > doomWindowMs) {
            recentSwitches.removeFirst()
        }
        
        // Doom scroll tracking is now checked inside the coroutine after resolving category

        if (now - mindfulConfigLoadedAt > 60_000L) {
            mindfulConfigLoadedAt = now
            scope.launch {
                try {
                    val db = PulseDatabase.getDatabase(applicationContext)
                    db.analyticsDao().getState("mindful_doomscroll_window_secs")?.toLongOrNull()?.let { doomWindowMs = it * 1000L }
                    db.analyticsDao().getState("mindful_doomscroll_threshold")?.toIntOrNull()?.let { doomThreshold = it }
                } catch (e: Exception) {}
            }
        }

        scope.launch {
            try {
                val db = PulseDatabase.getDatabase(applicationContext)
                
                val isMasterEnabled = db.analyticsDao().getState("pulse_master_enabled")?.toBoolean() ?: true
                if (!isMasterEnabled) return@launch
                
                val isLimitsEnabled = db.analyticsDao().getState("pulse_app_limits_enabled")?.toBoolean() ?: true
                val isDoomScrollEnabled = db.analyticsDao().getState("pulse_doomscroll_enabled")?.toBoolean() ?: true
                
                val appInfo = db.appInfoDao().getAppInfo(packageName)
                val category = appInfo?.category ?: AppCategory.NEUTRAL
                val isSpeedbump = category == AppCategory.DISTRACTING

                val customSession = appInfo?.sessionLimitMins
                val globalSession = db.analyticsDao().getState("limit_${category.lowercase()}_session")?.toIntOrNull() ?: com.focux.pulse.utilities.NO_LIMIT
                val actualSessionMins = customSession ?: globalSession
                    
                val customDaily = appInfo?.dailyLimitMins
                val globalDaily = db.analyticsDao().getState("limit_${category.lowercase()}_daily")?.toIntOrNull() ?: com.focux.pulse.utilities.NO_LIMIT
                val actualDailyMins = customDaily ?: globalDaily

                val customOpens = appInfo?.dailyOpensLimit
                val globalOpens = db.analyticsDao().getState("limit_${category.lowercase()}_opens")?.toIntOrNull() ?: com.focux.pulse.utilities.NO_LIMIT
                val actualOpens = customOpens ?: globalOpens

                val baseBreathingDuration = db.analyticsDao().getState("mindful_base_duration")?.toIntOrNull()
                val penaltyMultiplier = db.analyticsDao().getState("mindful_penalty_multiplier")?.toIntOrNull()
                
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date())
                
                val usedDailyMins = if (actualDailyMins != com.focux.pulse.utilities.NO_LIMIT) db.analyticsDao().getAppUsageMinsForDay(packageName, todayStr) else 0
                val usedOpens = if (actualOpens != com.focux.pulse.utilities.NO_LIMIT) db.analyticsDao().getAppOpensForDay(packageName, todayStr) else 0

                val isDailyExceeded = isLimitsEnabled && actualDailyMins != com.focux.pulse.utilities.NO_LIMIT && usedDailyMins >= actualDailyMins
                val isOpensExceeded = isLimitsEnabled && actualOpens != com.focux.pulse.utilities.NO_LIMIT && usedOpens >= actualOpens
                
                val finalBreathingDuration = baseBreathingDuration ?: 4
                val finalBreathingCycles = if (isDailyExceeded || isOpensExceeded) (penaltyMultiplier ?: 3) else 1
                
                // --- Cold-Start Free Pass Detection ---
                val lastDbEndTime = db.analyticsDao().getLastSessionEndTime(packageName) ?: 0L
                val lastRamEndTime = lastCloseTimestampMap[packageName] ?: 0L
                val lastEndTime = maxOf(lastDbEndTime, lastRamEndTime)
                
                val msSinceLastClose = System.currentTimeMillis() - lastEndTime
                val isColdStart = usedOpens == 0 || (lastEndTime > 0L && msSinceLastClose >= 3_600_000L) // 1 Hour

                // If user was away for more than 5 minutes, reset their contiguous session time
                if (msSinceLastClose >= 300_000L) {
                    sessionStartTimes[packageName] = System.currentTimeMillis()
                }
                val sessionStartTime = sessionStartTimes[packageName] ?: System.currentTimeMillis()
                sessionStartTimes[packageName] = sessionStartTime
                val elapsedSessionMs = System.currentTimeMillis() - sessionStartTime

                if (packageName == exemptPackage && System.currentTimeMillis() < exemptExpiry) {
                    Logger.d("AppInterceptor", "App $packageName is currently exempt.")
                    if (isLimitsEnabled && actualSessionMins != com.focux.pulse.utilities.NO_LIMIT) {
                        val remainingMs = (actualSessionMins * 60_000L) - elapsedSessionMs
                        startSessionTimer(packageName, remainingMs, actualSessionMins, finalBreathingDuration, finalBreathingCycles)
                    }
                    return@launch
                }

                if (isSpeedbump && isColdStart && !isDailyExceeded && !isOpensExceeded) {
                    Logger.d("AppInterceptor", "Cold start for $packageName. Opening straight with 10s cooldown.")
                    exemptPackage = packageName
                    exemptExpiry = System.currentTimeMillis() + 10_000L // 10s Exemption cooldown
                    
                    if (isLimitsEnabled && actualSessionMins != com.focux.pulse.utilities.NO_LIMIT) {
                        val remainingMs = (actualSessionMins * 60_000L) - elapsedSessionMs
                        startSessionTimer(packageName, remainingMs, actualSessionMins, finalBreathingDuration, finalBreathingCycles)
                    }
                    return@launch
                }
                
                // Doom Scroll Check (Only trigger for distracting apps)
                if (isDoomScrollEnabled && isSpeedbump && recentSwitches.size >= doomThreshold) {
                    recentSwitches.clear()
                    activeSessionJob?.cancel()
                    launchDoomScrollBreathing(packageName, finalBreathingDuration)
                    return@launch
                }

                if (isLimitsEnabled && (isSpeedbump || isDailyExceeded || isOpensExceeded)) {
                    activeSessionJob?.cancel()
                    launchBreathingScreen(
                        targetPackage = packageName, 
                        sessionLimitMins = actualSessionMins, 
                        dailyLimitMins = actualDailyMins, 
                        usedDailyMins = usedDailyMins, 
                        opensLimit = actualOpens, 
                        usedOpens = usedOpens, 
                        breathingDuration = finalBreathingDuration,
                        breathingCycles = finalBreathingCycles,
                        isTimeout = false
                    )
                } else if (isLimitsEnabled && actualSessionMins != com.focux.pulse.utilities.NO_LIMIT) {
                        val remainingMs = (actualSessionMins * 60_000L) - elapsedSessionMs
                        startSessionTimer(packageName, remainingMs, actualSessionMins, finalBreathingDuration, finalBreathingCycles)
                    } else {
                    activeSessionJob?.cancel()
                }
            } catch (e: Exception) {
                Logger.e("AppInterceptor", "Error querying app limits", e)
            }
        }
    }

    // Keep rest of the file

    override fun onInterrupt() {
        Logger.d("AppInterceptor", "Service interrupted")
        instance = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeSessionJob?.cancel()
    }
    
    fun notifyBreathingCompleted(packageName: String) {
        exemptPackage = packageName
        exemptExpiry = System.currentTimeMillis() + 10_000L // Instant fallback to block race conditions
        
        scope.launch {
            try {
                val db = PulseDatabase.getDatabase(applicationContext)
                val exemptionSecs = db.analyticsDao().getState("mindful_exemption_window_secs")?.toLongOrNull() ?: 10L
                exemptExpiry = System.currentTimeMillis() + (exemptionSecs * 1000L)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    fun startSessionTimer(packageName: String, remainingMs: Long, sessionLimitMins: Int, breathingDuration: Int, breathingCycles: Int) {
        activeSessionJob?.cancel()
        if (sessionLimitMins <= 0 || remainingMs <= 0) return
        
        Logger.d("AppInterceptor", "Starting session timer for $packageName (${remainingMs/1000}s remaining)")
        activeSessionJob = scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(remainingMs)
            
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
                    breathingCycles = breathingCycles,
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
        breathingCycles: Int,
        isTimeout: Boolean
    ) {
        val intent = Intent(this, BreathingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_PACKAGE", targetPackage)
            putExtra("SESSION_LIMIT_MINS", sessionLimitMins ?: -1)
            putExtra("DAILY_LIMIT_MINS", dailyLimitMins ?: -1)
            putExtra("USED_DAILY_MINS", usedDailyMins)
            putExtra("OPENS_LIMIT", opensLimit ?: -1)
            putExtra("USED_OPENS", usedOpens)
            putExtra("BREATHING_DURATION", breathingDuration)
            putExtra("BREATHING_CYCLES", breathingCycles)
            putExtra("IS_DOOM_SCROLL", false)
            putExtra("IS_TIMEOUT", isTimeout)
        }
        startActivity(intent)
    }

    private fun launchDoomScrollBreathing(targetPackage: String, breathingDuration: Int) {
        val intent = Intent(this, BreathingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TARGET_PACKAGE", targetPackage)
            putExtra("SESSION_LIMIT_MINS", -1)
            putExtra("DAILY_LIMIT_MINS", -1)
            putExtra("USED_DAILY_MINS", 0)
            putExtra("OPENS_LIMIT", -1)
            putExtra("USED_OPENS", 0)
            putExtra("BREATHING_DURATION", breathingDuration)
            putExtra("BREATHING_CYCLES", 1)
            putExtra("IS_DOOM_SCROLL", true)
            putExtra("IS_TIMEOUT", false)
        }
        startActivity(intent)
    }
}
