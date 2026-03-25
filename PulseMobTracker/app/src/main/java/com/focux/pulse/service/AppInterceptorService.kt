package com.focux.pulse.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.data.local.entities.AppCategory
import com.focux.pulse.ui.screens.Breathing.BreathingActivity
import com.focux.pulse.utilities.Logger
import com.focux.pulse.utilities.NO_LIMIT
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

    // ── Global Cooldowns (RAM-only, volatile for thread safety) ───────────
    // Set when user taps "Snooze" in breathing screen
    @Volatile private var globalPauseExpiry: Long = 0L
    // Set automatically after ANY breathing is shown (doom or app limit)
    @Volatile private var globalCooldownExpiry: Long = 0L

    // ── Doom Scroll State ─────────────────────────────────────────────────
    private val recentSwitches = ArrayDeque<Long>()
    @Volatile private var doomWindowMs: Long = 10_000L
    @Volatile private var doomThreshold: Int = 15

    // ── System App Filtering ──────────────────────────────────────────────
    private var imePackages: Set<String> = emptySet()
    private var launcherPackages: Set<String> = emptySet()

    // ── Session Tracking (RAM for performance, per-app) ───────────────────
    private val sessionStartTimes = mutableMapOf<String, Long>()
    private val lastCloseTimestampMap = mutableMapOf<String, Long>()

    // Called by breathingScreen "Snooze" button
    fun pauseInterventions(minutes: Long) {
        globalPauseExpiry = System.currentTimeMillis() + (minutes * 60_000L)
        Logger.d("AppInterceptor", "Interventions snoozed for ${minutes}m")
    }

    // Called by BreathingActivity when user completes the breathing exercise
    fun notifyBreathingCompleted(packageName: String) {
        // Use a temporary 10s cooldown immediately; real value loaded async
        globalCooldownExpiry = System.currentTimeMillis() + 10_000L
        scope.launch {
            try {
                val db = PulseDatabase.getDatabase(applicationContext)
                val exemptionSecs = db.analyticsDao().getState("mindful_exemption_window_secs")?.toLongOrNull() ?: 10L
                globalCooldownExpiry = System.currentTimeMillis() + (exemptionSecs * 1000L)
                Logger.d("AppInterceptor", "Post-breathing global cooldown set: ${exemptionSecs}s")
            } catch (e: Exception) {
                Logger.e("AppInterceptor", "Failed to load exemption window", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        // Detect IME (keyboard) packages
        try {
            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imePackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
        } catch (e: Exception) {
            Logger.e("AppInterceptor", "Failed to get IME packages", e)
        }

        // Detect launcher/home screen packages
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            launcherPackages = packageManager.queryIntentActivities(homeIntent, 0)
                .map { it.activityInfo.packageName }.toSet()
        } catch (e: Exception) {
            Logger.e("AppInterceptor", "Failed to get launcher packages", e)
        }

        scope.launch {
            com.focux.pulse.utilities.ConfigInitializer.initializeDefaults(
                PulseDatabase.getDatabase(applicationContext)
            )
        }

        Logger.d("AppInterceptor", "Service connected. IME: $imePackages | Launchers: $launcherPackages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // ── Step 1: Filter system/app/ignored packages (fast path, no DB) ──
        if (packageName == "com.focux.pulse") return
        if (packageName == "com.android.systemui" || packageName == "android") return
        if (packageName in imePackages) return
        if (packageName in launcherPackages) return
        if (packageName == lastInterceptedPackage) return

        val now = System.currentTimeMillis()

        // Track when last app was closed (for cold-start & session reset)
        val previousPackage = lastInterceptedPackage
        if (previousPackage.isNotEmpty()) {
            lastCloseTimestampMap[previousPackage] = now
        }
        lastInterceptedPackage = packageName

        // ── Step 2: Track switch for Doom Scroll (ALL non-ignored apps) ────
        val switchCountSnapshot: Int
        synchronized(recentSwitches) {
            recentSwitches.addLast(now)
            while (recentSwitches.isNotEmpty() && (now - recentSwitches.first()) > doomWindowMs) {
                recentSwitches.removeFirst()
            }
            switchCountSnapshot = recentSwitches.size
        }

        Logger.d("AppInterceptor", "→ $packageName | switches in window: $switchCountSnapshot/$doomThreshold")

        // ── Step 3+: All decisions after loading DB config ─────────────────
        scope.launch {
            try {
                val db = PulseDatabase.getDatabase(applicationContext)
                val dao = db.analyticsDao()

                // ── Load master toggle ────────────────────────────────────
                val isMasterEnabled = dao.getState("pulse_master_enabled")?.toBoolean() ?: true
                if (!isMasterEnabled) {
                    Logger.d("AppInterceptor", "Master disabled, skipping")
                    return@launch
                }

                // ── Check global pause (user-initiated snooze) ────────────
                if (System.currentTimeMillis() < globalPauseExpiry) {
                    Logger.d("AppInterceptor", "Global pause active, skipping")
                    return@launch
                }

                // ── Check global post-breathing cooldown ──────────────────
                if (System.currentTimeMillis() < globalCooldownExpiry) {
                    Logger.d("AppInterceptor", "Global cooldown active, skipping")
                    return@launch
                }

                // ── Load feature toggles ──────────────────────────────────
                val isLimitsEnabled = dao.getState("pulse_app_limits_enabled")?.toBoolean() ?: true
                val isDoomScrollEnabled = dao.getState("pulse_doomscroll_enabled")?.toBoolean() ?: true

                // ── Load fresh doom config (always fresh so changes apply immediately) ──
                dao.getState("mindful_doomscroll_window_secs")?.toLongOrNull()?.let { doomWindowMs = it * 1000L }
                dao.getState("mindful_doomscroll_threshold")?.toIntOrNull()?.let { doomThreshold = it }

                // ── Load breathing config ─────────────────────────────────
                val breathingDuration = dao.getState("mindful_base_duration")?.toIntOrNull() ?: 4
                val penaltyMultiplier = dao.getState("mindful_penalty_multiplier")?.toIntOrNull() ?: 3
                val exemptionWindowSecs = dao.getState("mindful_exemption_window_secs")?.toLongOrNull() ?: 10L

                // ── Check user-configured ignored packages ────────────────
                val ignoredPackages = dao.getState("user_ignored_packages")
                    ?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
                if (packageName in ignoredPackages) {
                    Logger.d("AppInterceptor", "$packageName is user-ignored, skipping")
                    return@launch
                }

                // ─────────────────────────────────────────────────────────
                // PRIORITY 1: DOOM SCROLL CHECK (any non-ignored app)
                // ─────────────────────────────────────────────────────────
                if (isDoomScrollEnabled && switchCountSnapshot >= doomThreshold) {
                    Logger.d("AppInterceptor", "🌀 DOOM SCROLL! $switchCountSnapshot switches >= threshold $doomThreshold")
                    synchronized(recentSwitches) { recentSwitches.clear() }
                    activeSessionJob?.cancel()
                    launchDoomScrollBreathing(packageName, breathingDuration)
                    return@launch
                }

                // ─────────────────────────────────────────────────────────
                // APP CATEGORY LOGIC
                // ─────────────────────────────────────────────────────────
                val appInfo = db.appInfoDao().getAppInfo(packageName)
                val category = appInfo?.category ?: AppCategory.NEUTRAL
                val isDistracting = category == AppCategory.DISTRACTING

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                val todayStr = sdf.format(java.util.Date())

                // Resolve session/daily/opens limits (per-app override, else global category default)
                val sessionLimitMins = appInfo?.sessionLimitMins
                    ?: dao.getState("limit_${category.lowercase()}_session")?.toIntOrNull() ?: NO_LIMIT
                val dailyLimitMins = appInfo?.dailyLimitMins
                    ?: dao.getState("limit_${category.lowercase()}_daily")?.toIntOrNull() ?: NO_LIMIT
                val opensLimit = appInfo?.dailyOpensLimit
                    ?: dao.getState("limit_${category.lowercase()}_opens")?.toIntOrNull() ?: NO_LIMIT

                val usedDailyMins = if (dailyLimitMins != NO_LIMIT) dao.getAppUsageMinsForDay(packageName, todayStr) else 0
                val usedOpens = if (opensLimit != NO_LIMIT) dao.getAppOpensForDay(packageName, todayStr) else 0

                val isDailyExceeded = isLimitsEnabled && dailyLimitMins != NO_LIMIT && usedDailyMins >= dailyLimitMins
                val isOpensExceeded = isLimitsEnabled && opensLimit != NO_LIMIT && usedOpens >= opensLimit
                val isAnyLimitExceeded = isDailyExceeded || isOpensExceeded

                // When limits are exceeded, multiply breathing cycles as a penalty
                val breathingCycles = if (isAnyLimitExceeded) penaltyMultiplier else 1

                // ─────────────────────────────────────────────────────────
                // PRIORITY 2: DISTRACTING APP → breathing on open
                // (Unless it's a cold start for this specific app)
                // ─────────────────────────────────────────────────────────
                if (isDistracting && isLimitsEnabled) {
                    // Cold-start = this specific distracting app not used for > 1 hour
                    val lastDbEndTime = dao.getLastSessionEndTime(packageName) ?: 0L
                    val lastRamEndTime = lastCloseTimestampMap[packageName] ?: 0L
                    val lastEndTime = maxOf(lastDbEndTime, lastRamEndTime)
                    val isColdStart = lastEndTime == 0L || (System.currentTimeMillis() - lastEndTime) >= 3_600_000L

                    if (isColdStart && !isAnyLimitExceeded) {
                        Logger.d("AppInterceptor", "❄️ Cold start for $packageName, skipping opening breathing")
                        // Still set up session timer (so if they stay too long, it fires)
                        setupSessionTimer(packageName, sessionLimitMins, breathingDuration, breathingCycles)
                        return@launch
                    }

                    // Not cold start (or limits exceeded) → show breathing
                    Logger.d("AppInterceptor", "🔴 Distracting app opening: $packageName (cycles: $breathingCycles)")
                    activeSessionJob?.cancel()
                    launchBreathingScreen(
                        targetPackage = packageName,
                        sessionLimitMins = sessionLimitMins,
                        dailyLimitMins = dailyLimitMins,
                        usedDailyMins = usedDailyMins,
                        opensLimit = opensLimit,
                        usedOpens = usedOpens,
                        breathingDuration = breathingDuration,
                        breathingCycles = breathingCycles,
                        isTimeout = false
                    )
                    return@launch
                }

                // ─────────────────────────────────────────────────────────
                // PRIORITY 3: NON-DISTRACTING APPS — breathing only if limits hit
                // ─────────────────────────────────────────────────────────
                if (isLimitsEnabled && isAnyLimitExceeded) {
                    Logger.d("AppInterceptor", "🟡 $category app $packageName exceeded limits (cycles: $breathingCycles)")
                    activeSessionJob?.cancel()
                    launchBreathingScreen(
                        targetPackage = packageName,
                        sessionLimitMins = sessionLimitMins,
                        dailyLimitMins = dailyLimitMins,
                        usedDailyMins = usedDailyMins,
                        opensLimit = opensLimit,
                        usedOpens = usedOpens,
                        breathingDuration = breathingDuration,
                        breathingCycles = breathingCycles,
                        isTimeout = false
                    )
                    return@launch
                }

                // ─────────────────────────────────────────────────────────
                // ALL APP TYPES: Start/resume session timer if applicable
                // ─────────────────────────────────────────────────────────
                setupSessionTimer(packageName, sessionLimitMins, breathingDuration, breathingCycles)

            } catch (e: Exception) {
                Logger.e("AppInterceptor", "Error in event handler", e)
            }
        }
    }

    /**
     * Starts a countdown for session-based breathing.
     * Resets session if the user was away from this app for >= 5 minutes.
     * Fires breathing when the session time limit is reached.
     */
    private fun setupSessionTimer(
        packageName: String,
        sessionLimitMins: Int,
        breathingDuration: Int,
        breathingCycles: Int
    ) {
        if (sessionLimitMins <= 0 || sessionLimitMins == NO_LIMIT) {
            activeSessionJob?.cancel()
            return
        }

        val now = System.currentTimeMillis()
        val lastCloseTime = lastCloseTimestampMap[packageName] ?: 0L
        val msSinceClose = if (lastCloseTime > 0) now - lastCloseTime else Long.MAX_VALUE

        // If away for >= 5 min, reset the session clock for this app
        if (msSinceClose >= 300_000L) {
            sessionStartTimes[packageName] = now
            Logger.d("AppInterceptor", "Session reset for $packageName (was away ${msSinceClose / 1000}s)")
        }

        val sessionStart = sessionStartTimes.getOrPut(packageName) { now }
        val elapsedMs = now - sessionStart
        val remainingMs = (sessionLimitMins * 60_000L) - elapsedMs

        startSessionTimer(packageName, remainingMs, sessionLimitMins, breathingDuration, breathingCycles)
    }

    fun startSessionTimer(
        packageName: String,
        remainingMs: Long,
        sessionLimitMins: Int,
        breathingDuration: Int,
        breathingCycles: Int
    ) {
        activeSessionJob?.cancel()
        if (sessionLimitMins <= 0 || remainingMs <= 0) return

        Logger.d("AppInterceptor", "⏱ Session timer started for $packageName (${remainingMs / 1000}s remaining)")
        activeSessionJob = scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(remainingMs)

            // Session limit always fires regardless of global cooldown — priority over doom/app-limit cooldown
            if (lastInterceptedPackage == packageName) {
                Logger.d("AppInterceptor", "⏱ Session expired for $packageName!")
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

    override fun onInterrupt() {
        Logger.d("AppInterceptor", "Service interrupted")
        instance = null
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        activeSessionJob?.cancel()
    }
}
