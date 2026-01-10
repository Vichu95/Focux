package com.focux.pulse.data.processors

import android.content.Context
import com.focux.pulse.data.local.dao.AnalyticsDao
import com.focux.pulse.data.local.dao.AppInfoDao
import com.focux.pulse.data.local.entities.AppInfo
import com.focux.pulse.data.local.entities.AppSession
import com.focux.pulse.data.local.entities.DailyStats
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.data.local.entities.SystemState
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
        // Sort keys to process chronologically (Oldest -> Newest) to maintain Watermark logic
        val sessionsByDay = newSessions.groupBy { it.date }.toSortedMap()

        // Fetch launchers to exclude from Total Screen Time (independent of general ignore list)
        val launcherPackages = AppInfoHelper.getLauncherPackages(context)
        
        val sdfDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val now = System.currentTimeMillis()
        
        // OPTIMIZATION: Get the "High Water Mark" (Last successfully calculated date)
        // Format: "YYYY-MM-DD". Default to empty/null if fresh.
        var lastCalculatedDate = analyticsDao.getState("sleep_calculated") ?: ""

        for ((date, daySessions) in sessionsByDay) {
            val existingStats = analyticsDao.getDailyStats(date) ?: DailyStats(date)
            
            // Check Watermark: Is this date already settled?
            // String comparison works for YYYY-MM-DD: "2024-01-01" < "2024-01-02"
            val isAlreadyFinalized = lastCalculatedDate.isNotEmpty() && date <= lastCalculatedDate

            // Get ALL APP sessions for this day
            val allDaySessions = analyticsDao.getSessionsForDay(date)
            val appSessions = allDaySessions.filter { it.type == PulseEvents.SESSION_APP }

            val currentDateObj = sdfDay.parse(date) ?: java.util.Date()
            val calendar = Calendar.getInstance()
            calendar.time = currentDateObj

            // 1. Determine "Active Day" Boundaries (Sleep Logic)
            
            // Set User-Defined Sleep Window (Target)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            calendar.set(Calendar.HOUR_OF_DAY, com.focux.pulse.utilities.PULSE_SLEEP_TARGET_BEDTIME_HOUR)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val targetSleepStart = calendar.timeInMillis

            // End: Today 07:00 (Back to Today)
            calendar.add(Calendar.DAY_OF_YEAR, 1) 
            calendar.set(Calendar.HOUR_OF_DAY, com.focux.pulse.utilities.PULSE_SLEEP_TARGET_WAKEUP_HOUR)
            val targetSleepEnd = calendar.timeInMillis
            
            // Midnight Today (00:00)
            calendar.time = currentDateObj
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val midnightToday = calendar.timeInMillis

            var finalSleepStart = 0L
            var finalSleepEnd = 0L
            
            // LOGIC SPLIT: PENDING vs FINALIZED
            
            val isToday = (sdfDay.format(java.util.Date(now)) == date)
            val isPendingPhase = isToday && (now < targetSleepEnd)

            if (isAlreadyFinalized) {
                // OPTIMIZATION: Date is <= lastCalculatedDate. Reuse stable values.
                finalSleepStart = existingStats.sleepTimeStart
                finalSleepEnd = existingStats.sleepTimeEnd
            } else if (isPendingPhase) {
                // PHASE 1: PENDING (During Sleep Window)
                // Use Defaults
                finalSleepStart = targetSleepStart
                finalSleepEnd = targetSleepEnd
            } else {
                // PHASE 2: FINALIZED (After Sleep Window / History)
                // Calculate ACTUAL sleep from offline sessions
                val potentialSleepSessions = analyticsDao.getSessionsOverlapping(targetSleepStart, targetSleepEnd)
                
                val windowOfflineSessions = potentialSleepSessions.filter { 
                    it.type == PulseEvents.SESSION_OFFLINE 
                }

                val thresholdSessions = windowOfflineSessions.filter { 
                    it.duration >= com.focux.pulse.utilities.PULSE_MIN_SLEEP_OFFLINE_THRESHOLD_MS
                }
                
                if (thresholdSessions.isNotEmpty()) {
                    finalSleepStart = thresholdSessions.minOf { it.startTime }
                    finalSleepEnd = thresholdSessions.maxOf { it.endTime }
                    
                    // Mark as FINALIZED (Update Watermark)
                    // We found a valid sleep session -> This day is now settled.
                    // Only update if this date is NEWER than current watermark
                    if (date > lastCalculatedDate) {
                        analyticsDao.updateState(SystemState("sleep_calculated", date))
                        lastCalculatedDate = date // Update local var for next iteration logic
                    }
                } else {
                    // Fallback: No sleep detected
                    finalSleepStart = 0L
                    finalSleepEnd = 0L
                    
                    // If day is seemingly over (not today), allowing finalizing even if no sleep found?
                    // User logic: "after each succesful parsing... update"
                    // If no sleep is found but we are actively processing history, we should probably mark it done to avoid perpetual re-check.
                    if (!isToday && date > lastCalculatedDate) {
                        analyticsDao.updateState(SystemState("sleep_calculated", date))
                        lastCalculatedDate = date
                    }
                }
            }

            // 2. Define "Day Start" for Metrics
            // If Pending: Midnight (00:00)
            // If Finalized: Actual Sleep End (Wake Up) OR Midnight if no sleep (or 0L, or re-used existing)
            val dayMetricStart = if (isPendingPhase) midnightToday else finalSleepEnd

            // 3. Calculate Metrics
            
            // --- First App ---
            // OPTIMIZATION: Reuse if finalized
            var resolvedFirstAppPackage = existingStats.firstAppPackage
            var resolvedFirstAppStart = existingStats.firstAppStartTime
            var resolvedFirstAppEnd = existingStats.firstAppEndTime
            
            if (!isAlreadyFinalized) {
                val fa = appSessions
                    .filter { 
                        it.packageName !in ignoredApps && 
                        it.packageName !in launcherPackages &&
                        it.startTime >= dayMetricStart
                    }
                    .sortedBy { it.startTime }
                    .firstOrNull()
                
                resolvedFirstAppPackage = fa?.packageName
                resolvedFirstAppStart = fa?.startTime ?: 0
                resolvedFirstAppEnd = fa?.endTime ?: 0
            }

            // --- Last App --- (ALWAYS Recalculate - user might be using phone right now)
            val lastApp = appSessions
                .filter { 
                    it.packageName !in ignoredApps && 
                    it.packageName !in launcherPackages &&
                    it.startTime >= dayMetricStart
                }
                .sortedBy { it.startTime }
                .lastOrNull()

            // --- Offline Streak --- (ALWAYS Recalculate)
            val offlineStreakSession = allDaySessions
                .filter { 
                    it.type == PulseEvents.SESSION_OFFLINE &&
                    it.startTime >= dayMetricStart
                }
                .maxByOrNull { it.duration }

            // --- Standard Metrics (Screen Time, Unlocks etc.) ---
            val appTime = allDaySessions
                .filter { 
                    it.type == PulseEvents.SESSION_APP && 
                    it.packageName !in com.focux.pulse.utilities.PULSE_IGNORED_APPS 
                }
                .sumOf { it.duration }
                
            val rawGlanceCount = allDaySessions.count { it.type == PulseEvents.SESSION_GLANCE }
            val glanceTime = rawGlanceCount * com.focux.pulse.utilities.PULSE_GLANCE_ESTIMATE_MS
            val totalScreenTime = appTime + glanceTime
            
            val unlockAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_APP }
            val unlockNoAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_NOAPP }
            val screenCheckCount = allDaySessions.count { 
                it.type == PulseEvents.SESSION_GLANCE || it.type == PulseEvents.SESSION_UNLOCK_NOAPP 
            }
            
            val totalUnlocks = unlockNoAppCount + unlockAppCount
            val focusScore = (100 - totalUnlocks * 2).coerceIn(0, 100)

            // Top 3 apps
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

            val updatedStats = DailyStats(
                date = date,
                totalScreenTime = totalScreenTime,
                unlockNoAppCount = unlockNoAppCount,
                unlockAppCount = unlockAppCount,
                glanceCount = rawGlanceCount,
                screenCheckCount = screenCheckCount,
                focusScore = focusScore,
                productiveTime = 0,
                neutralTime = totalScreenTime,
                distractingTime = 0,
                // First app (Reused or New)
                firstAppPackage = resolvedFirstAppPackage,
                firstAppStartTime = resolvedFirstAppStart,
                firstAppEndTime = resolvedFirstAppEnd,
                // Last app
                lastAppPackage = lastApp?.packageName,
                lastAppStartTime = lastApp?.startTime ?: 0,
                lastAppEndTime = lastApp?.endTime ?: 0,
                // Offline streak
                offlineStreakDuration = offlineStreakSession?.duration ?: 0,
                offlineStreakStart = offlineStreakSession?.startTime ?: 0,
                offlineStreakEnd = offlineStreakSession?.endTime ?: 0,
                // Top 3 apps
                topApp1Package = topApp1?.first,
                topApp1Duration = topApp1?.second ?: 0,
                topApp2Package = topApp2?.first,
                topApp2Duration = topApp2?.second ?: 0,
                topApp3Package = topApp3?.first,
                topApp3Duration = topApp3?.second ?: 0,
                // Sleep Schedule
                sleepTimeStart = finalSleepStart,
                sleepTimeEnd = finalSleepEnd,
                sleepBreakCount = 0,
                sleepPhoneDuration = 0,
                // Debug values
                sleepReadableStart = if (finalSleepStart > 0) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(finalSleepStart)) else "--:--",
                sleepReadableEnd = if (finalSleepEnd > 0) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(finalSleepEnd)) else "--:--"
            )
            analyticsDao.updateDailyStats(updatedStats)
            
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
