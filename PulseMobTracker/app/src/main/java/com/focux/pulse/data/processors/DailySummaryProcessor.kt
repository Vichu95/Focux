package com.focux.pulse.data.processors

import android.content.Context
import com.focux.pulse.data.local.dao.AnalyticsDao
import com.focux.pulse.data.local.dao.AppInfoDao
import com.focux.pulse.data.local.entities.AppInfo
import com.focux.pulse.data.local.entities.AppSession
import com.focux.pulse.data.local.entities.DailyStats
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.utilities.PULSE_IGNORED_APPS
import com.focux.pulse.utilities.PULSE_SLEEP_THRESHOLD_MS
import com.focux.pulse.utilities.PULSE_SLEEP_WINDOW_END_HOUR
import com.focux.pulse.utilities.PULSE_SLEEP_WINDOW_START_HOUR
import com.focux.pulse.utilities.AppInfoHelper
import java.util.Calendar

/**
 * Aggregates AppSessions into DailyStats.
 */
class DailySummaryProcessor(
    private val context: Context,
    private val analyticsDao: AnalyticsDao,
    private val appInfoDao: AppInfoDao
) {

    /**
     * Updates DailyStats based on new sessions.
     * Calculates: screen time, unlocks, glances, top apps, first/last app.
     */
    /**
     * Updates DailyStats based on new sessions.
     * Calculates: screen time, unlocks, glances, top apps, first/last app.
     * @param ignoredApps Set of package names to exclude from Top Apps and First/Last app logic.
     */
    suspend fun updateDailyStats(newSessions: List<AppSession>, ignoredApps: Set<String>) {
        val sessionsByDay = newSessions.groupBy { it.date }

        // Fetch launchers to exclude from Total Screen Time (independent of general ignore list)
        val launcherPackages = AppInfoHelper.getLauncherPackages(context)

        for ((date, daySessions) in sessionsByDay) {
            val existingStats = analyticsDao.getDailyStats(date) ?: DailyStats(date)

            // Get ALL APP sessions for this day (for top apps calculation)
            val allDaySessions = analyticsDao.getSessionsForDay(date)
            val appSessions = allDaySessions.filter { it.type == PulseEvents.SESSION_APP }

            // --- SLEEP ANALYSIS ALGORITHM (User Defined) ---
            // MOVED UP to support "Functional Day" metrics (First App after waking up)
            
            // 1. Define Window: Yesterday 10 PM (22:00) to Today 7 AM (07:00)
            val sdfFull = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val sdfDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val currentDateObj = sdfDay.parse(date) ?: java.util.Date()
            val calendar = Calendar.getInstance()
            calendar.time = currentDateObj
            
            // Set Window End (Today 07:00)
            calendar.set(Calendar.HOUR_OF_DAY, com.focux.pulse.utilities.PULSE_SLEEP_TARGET_WAKEUP_HOUR)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val sleepWindowEndMs = calendar.timeInMillis
            
            // Set Window Start (Yesterday 22:00)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            calendar.set(Calendar.HOUR_OF_DAY, com.focux.pulse.utilities.PULSE_SLEEP_TARGET_BEDTIME_HOUR)
            val sleepWindowStartMs = calendar.timeInMillis
            
            // 2. Fetch Overlapping Sessions (Cross-Day)
            val potentialSleepSessions = analyticsDao.getSessionsOverlapping(sleepWindowStartMs, sleepWindowEndMs)
            
            // 3. Step 1: Filter ALL Offline Sessions in Window
            val windowOfflineSessions = potentialSleepSessions.filter { 
                it.type == PulseEvents.SESSION_OFFLINE 
            }

            // 4. Step 2: Threshold Filter (Determine Bounds)
            val thresholdSessions = windowOfflineSessions.filter { 
                it.duration >= com.focux.pulse.utilities.PULSE_MIN_SLEEP_OFFLINE_THRESHOLD_MS
            }
            
            var derivedSleepStart = 0L
            var derivedSleepEnd = 0L
            var sleepBreakCount = 0
            var sleepPhoneDuration = 0L
            
            if (thresholdSessions.isNotEmpty()) {
                // Determine Bounds from Anchor Sessions
                derivedSleepStart = thresholdSessions.minOf { it.startTime }
                derivedSleepEnd = thresholdSessions.maxOf { it.endTime }
                
                // 5. Step 3: Refine List (Re-include small offline sessions WITHIN bounds)
                val finalOfflineSegments = windowOfflineSessions.filter { 
                    it.startTime >= derivedSleepStart && it.endTime <= derivedSleepEnd
                }
                
                // 6. Metrics Calculation
                sleepBreakCount = (finalOfflineSegments.size - 1).coerceAtLeast(0)
                
                val totalSleepSpan = derivedSleepEnd - derivedSleepStart
                val totalOfflineDuration = finalOfflineSegments.sumOf { it.duration }
                sleepPhoneDuration = (totalSleepSpan - totalOfflineDuration).coerceAtLeast(0)
            } else {
                // Fallback: Use calculated Window End as "Wake Up Time" if no sleep detected?
                // Or stick to 0L so we don't filter arbitrarily?
                // User requirement: "Day starts when I wake up".
                // If we don't detect sleep, we have no "Wake Up Time". 
                // Using 0L ensures we consume ALL sessions (fallback to standard day).
                derivedSleepStart = 0L
                derivedSleepEnd = 0L
            }

            // --- HYBRID METRICS CALCULATION ---
            // Formula: Total Time = (Sum of App Durations) + (Glance Count * Penalty)
            //
            // 1. App Usage: Precise duration of all valid apps.
            //    - Includes: Normal Apps, Launchers (Menu), Settings.
            //    - Excludes: "System" apps (Now filtered HERE, because raw table contains everything).
            val appTime = allDaySessions
                .filter { 
                    it.type == PulseEvents.SESSION_APP && 
                    it.packageName !in com.focux.pulse.utilities.PULSE_IGNORED_APPS 
                }
                .sumOf { it.duration }
                
            // 2. Glances: "Distraction Penalty"
            //    Since we reverted the Ignore List, "Launcher" and "System UI" are now valid APP SESSIONS.
            //    So their duration is ALREADY included in `appTime` above.
            //    We ONLY need to add penalty for strict Lockscreen Glances (where no app/launcher was ever shown).
            
            // Raw Glance Count (Strictly Lockscreen Checks) - Requested by User
            val rawGlanceCount = allDaySessions.count { it.type == PulseEvents.SESSION_GLANCE }

            // 3. Weighting: Only penalize strict glances (2s each)
            val glanceTime = rawGlanceCount * com.focux.pulse.utilities.PULSE_GLANCE_ESTIMATE_MS
            val totalScreenTime = appTime + glanceTime

            // --- COUNT LOGIC (MUTUALLY EXCLUSIVE BUCKETS) ---
            
            // 1. Unlocks (Sessions)
            //    User unlocked AND opened a meaningful app.
            //    This is the "Productive/Active" count.
            val unlockAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_APP }
            
            // 2. Glances (Checks)
            //    User checked the phone but didn't open a meaningful app.
            //    - SESSION_GLANCE: Looked at Lockscreen only.
            //    - SESSION_UNLOCK_NOAPP: Unlocked -> Checked Launcher/Widget -> Locked.
            //    This is the "Passive/Checking" count.
            val screenCheckCount = allDaySessions.count { 
                it.type == PulseEvents.SESSION_GLANCE || it.type == PulseEvents.SESSION_UNLOCK_NOAPP 
            }
            
            // For DB completeness, we still track the raw NO_APP unlocks separate from glances
            val unlockNoAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_NOAPP }
            
            // Summary for Dashboard:
            // "Sessions" = unlockAppCount
            // "Checks"   = screenCheckCount

            // Top 3 apps by duration (excluding ignored apps and launchers)
            val appDurations = appSessions
                .filter { it.packageName !in ignoredApps && it.packageName !in launcherPackages }
                .groupBy { it.packageName }
                .mapValues { (_, sessions) -> sessions.sumOf { it.duration } }
                .toList()
                .sortedByDescending { it.second }
                .take(3)

            val topApp1 = appDurations.getOrNull(0)
            val topApp2 = appDurations.getOrNull(1)
            val topApp3 = appDurations.getOrNull(2)

            // First and last app of the day (by start time)
            // UPDATED: Filter OUT sessions before "Wake Up" (derivedSleepEnd)
            val sortedAppSessions = appSessions
                .filter { 
                    it.packageName !in ignoredApps && 
                    it.packageName !in launcherPackages &&
                    it.startTime > derivedSleepEnd // First App MUST be after waking up
                }
                .sortedBy { it.startTime }
            
            val firstApp = sortedAppSessions.firstOrNull()
            val lastApp = sortedAppSessions.lastOrNull() // Last app logic remains "Last of Day"

            // Focus score: simple formula (can be enhanced later)
            val totalUnlocks = unlockNoAppCount + unlockAppCount
            val focusScore = (100 - totalUnlocks * 2).coerceIn(0, 100)

            // Calculate offline streak (longest gap excluding sleep)
            // UPDATED: Only calculate streaks during Active Day (After Wake Up)
            val activeOfflineSessions = allDaySessions.filter { 
                it.type == PulseEvents.SESSION_OFFLINE &&
                it.startTime > derivedSleepEnd // Only after waking up
            }
            
            val maxOfflineSession = activeOfflineSessions.maxByOrNull { it.duration }
            
            val offlineStreakDuration = maxOfflineSession?.duration ?: 0L
            val offlineStreakStart = maxOfflineSession?.startTime ?: 0L
            val offlineStreakEnd = maxOfflineSession?.endTime ?: 0L

            // Calculate time by category (for now, all apps are NEUTRAL)
            val productiveTime = 0L
            val neutralTime = totalScreenTime  // All time is neutral until apps are categorized
            val distractingTime = 0L
            
            // Auto-discover new apps (at end)

            val updatedStats = DailyStats(
                date = date,
                totalScreenTime = totalScreenTime,
                unlockNoAppCount = unlockNoAppCount,
                unlockAppCount = unlockAppCount,
                glanceCount = rawGlanceCount,
                screenCheckCount = screenCheckCount,
                focusScore = focusScore,
                productiveTime = productiveTime,
                neutralTime = neutralTime,
                distractingTime = distractingTime,
                // First app
                firstAppPackage = firstApp?.packageName,
                firstAppStartTime = firstApp?.startTime ?: 0,
                firstAppEndTime = firstApp?.endTime ?: 0,
                // Last app
                lastAppPackage = lastApp?.packageName,
                lastAppStartTime = lastApp?.startTime ?: 0,
                lastAppEndTime = lastApp?.endTime ?: 0,
                // Offline streak
                offlineStreakDuration = offlineStreakDuration,
                offlineStreakStart = offlineStreakStart,
                offlineStreakEnd = offlineStreakEnd,
                // Top 3 apps
                topApp1Package = topApp1?.first,
                topApp1Duration = topApp1?.second ?: 0,
                topApp2Package = topApp2?.first,
                topApp2Duration = topApp2?.second ?: 0,
                topApp3Package = topApp3?.first,
                topApp3Duration = topApp3?.second ?: 0,
                // Sleep Schedule (New Algorithm)
                sleepTimeStart = derivedSleepStart,
                sleepTimeEnd = derivedSleepEnd,
                sleepBreakCount = sleepBreakCount,
                sleepPhoneDuration = sleepPhoneDuration,
                // Debug/Readable Strings
                sleepReadableStart = if (derivedSleepStart > 0) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(derivedSleepStart)) else "--:--",
                sleepReadableEnd = if (derivedSleepEnd > 0) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(derivedSleepEnd)) else "--:--"
            )
            analyticsDao.updateDailyStats(updatedStats)
            
            // --- BACK-UPDATE YESTERDAY'S LAST APP ---
            // "Last app of yesterday" = The absolute last app used before Sleep Start (even if early AM Today)
            if (derivedSleepStart > 0) {
                calendar.time = currentDateObj
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                val prevDate = sdfDay.format(calendar.time)
                
                val prevStats = analyticsDao.getDailyStats(prevDate)
                
                if (prevStats != null) {
                    val prevDaySessions = analyticsDao.getSessionsForDay(prevDate)
                    
                    // Handle Late Night usage (Today's sessions showing up before derived sleep start)
                    // We include sessions that STARTED before sleep (even if they end after, we'll clip them)
                    val earlyTodaySessions = if (derivedSleepStart > 0) {
                        allDaySessions.filter { it.startTime < derivedSleepStart }
                    } else {
                        emptyList()
                    }
                    
                    val combinedSessions = prevDaySessions + earlyTodaySessions
                    var statsToUpdate = prevStats
                    
                    val correctedLastApp = combinedSessions
                        .filter { 
                            it.type == PulseEvents.SESSION_APP &&
                            it.packageName !in ignoredApps && 
                            it.packageName !in launcherPackages &&
                            it.endTime <= derivedSleepStart
                        }
                        .maxByOrNull { it.startTime }
                        
                    // 1. Correct Last App
                    if (correctedLastApp != null && (correctedLastApp.packageName != prevStats.lastAppPackage || prevStats.lastAppPackage == null)) {
                        statsToUpdate = statsToUpdate.copy(
                            lastAppPackage = correctedLastApp.packageName,
                            lastAppStartTime = correctedLastApp.startTime,
                            lastAppEndTime = correctedLastApp.endTime
                        )
                    }
                    
                    // 2. Correct Offline Streak
                    // Calculate "Effective" offline streak by clipping sessions at Sleep Start
                    val bestOfflineData = combinedSessions
                        .filter { it.type == PulseEvents.SESSION_OFFLINE }
                        .mapNotNull { session ->
                            if (session.startTime >= derivedSleepStart) {
                                // Started after/at sleep start: Ignore
                                null
                            } else if (session.endTime <= derivedSleepStart) {
                                // Entirely before sleep: Keep full
                                Triple(session.duration, session.startTime, session.endTime)
                            } else {
                                // Overlaps start of sleep: Clip it
                                val clippedDuration = derivedSleepStart - session.startTime
                                Triple(clippedDuration, session.startTime, derivedSleepStart)
                            }
                        }
                        .maxByOrNull { it.first }
                    
                    // Always update (even if smaller)
                    val correctedDuration = bestOfflineData?.first ?: 0L
                    val correctedStart = bestOfflineData?.second ?: 0L
                    val correctedEnd = bestOfflineData?.third ?: 0L
                    
                    if (correctedDuration != statsToUpdate.offlineStreakDuration) {
                        statsToUpdate = statsToUpdate.copy(
                             offlineStreakDuration = correctedDuration,
                             offlineStreakStart = correctedStart,
                             offlineStreakEnd = correctedEnd
                        )
                    }

                    if (statsToUpdate != prevStats) {
                        analyticsDao.updateDailyStats(statsToUpdate)
                    }
                }
            }

            // Auto-discover new apps and add to AppInfo table
            registerNewApps(appSessions, ignoredApps)
        }
    }



    /**
     * Registers new apps discovered in sessions to the AppInfo table.
     */
    private suspend fun registerNewApps(appSessions: List<AppSession>, ignoredApps: Set<String>) {
        val packages = appSessions
            .map { it.packageName }
            .distinct()
        
        // Create AppInfo with resolved app names
        val appInfos = packages.map { pkg ->
            val appName = AppInfoHelper.getAppName(context, pkg)
            AppInfo(packageName = pkg, appName = appName)
        }
        
        // 1. Insert new apps (ignore conflicts)
        appInfoDao.insertAllIfNotExists(appInfos)
        
        // 2. Update names for ALL processed apps 
        // This fixes stale "App" names if permission was granted later
        appInfos.forEach { info ->
            if (info.appName != "App" && info.appName != "Android" && info.appName != info.packageName) {
               appInfoDao.updateAppName(info.packageName, info.appName)
            }
        }
    }
}
