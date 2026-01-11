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
        // Sort keys to process chronologically (Oldest -> Newest)
        // This is crucial so we "Correct" yesterday before processing today? 
        // Actually, "Correction" happens when looking back from Today. So order matters.
        val sessionsByDay = newSessions.groupBy { it.date }.toSortedMap()

        // Fetch launchers to exclude from Total Screen Time
        val launcherPackages = AppInfoHelper.getLauncherPackages(context)
        
        val sdfDay = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val now = System.currentTimeMillis()
        
        // We will process each day independently, but with a look-back correction.
        for (date in sessionsByDay.keys) {
            val currentDateObj = sdfDay.parse(date) ?: java.util.Date()
            val calendar = Calendar.getInstance()
            calendar.time = currentDateObj
            
            // -------------------------------------------------------------
            // 1. DETERMINE "PENDING" vs "FINALIZED" PHASE
            // -------------------------------------------------------------
            
            // Target Wakeup: Today 07:00
            calendar.set(Calendar.HOUR_OF_DAY, com.focux.pulse.utilities.PULSE_SLEEP_TARGET_WAKEUP_HOUR)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            val targetWakeUpTime = calendar.timeInMillis
            
            // Target Bedtime (Start of Sleep Window): Yesterday 22:00 (or User Set)
            // Note: This matches simple logic. Ideally, we read from User Preferences.
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            calendar.set(Calendar.HOUR_OF_DAY, com.focux.pulse.utilities.PULSE_SLEEP_TARGET_BEDTIME_HOUR)
            val targetSleepWindowStart = calendar.timeInMillis
            
            val isToday = (sdfDay.format(java.util.Date(now)) == date)
            val isPendingPhase = isToday && (now < targetWakeUpTime)

            // -------------------------------------------------------------
            // 2. DEFINE BOUNDARIES & SLEEP (Scenario A vs Scenario B)
            // -------------------------------------------------------------
            
            var dayMetricStart: Long
            var finalSleepStart: Long
            var finalSleepEnd: Long
            
            // Needed for Correction Check
            var actualSleepSession: AppSession? = null

            if (isPendingPhase) {
                // SCENARIO A: PENDING (Before 7 AM Today)
                // - Sleep: Defaults (Target)
                // - Day Start: Midnight (00:00)
                
                finalSleepStart = targetSleepWindowStart
                finalSleepEnd = targetWakeUpTime
                
                // Midnight
                val midCal = Calendar.getInstance().apply { time = currentDateObj; set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0) }
                dayMetricStart = midCal.timeInMillis

            } else {
                // SCENARIO B: FINALIZED (Yesterday or Today after 7 AM)
                // - Sleep: Find actual largest offline gap in [Yesterday 22:00 - Today 07:00]
                // - Day Start: Actual Sleep End
                
                val potentialSleepSessions = analyticsDao.getSessionsOverlapping(targetSleepWindowStart, targetWakeUpTime)
                
                val validOfflineSessions = potentialSleepSessions.filter { 
                    it.type == PulseEvents.SESSION_OFFLINE && 
                    it.duration >= com.focux.pulse.utilities.PULSE_MIN_SLEEP_OFFLINE_THRESHOLD_MS
                }
                
                // Find the "Best" sleep session (e.g. longest in window)
                actualSleepSession = validOfflineSessions.maxByOrNull { it.duration }
                
                if (actualSleepSession != null) {
                    finalSleepStart = actualSleepSession!!.startTime
                    finalSleepEnd = actualSleepSession!!.endTime
                } else {
                    // Fallback to Defaults if no sleep found
                    finalSleepStart = targetSleepWindowStart
                    finalSleepEnd = targetWakeUpTime
                }
                
                dayMetricStart = finalSleepEnd
            }
            
            // -------------------------------------------------------------
            // 3. FETCH DAY DATA & CALCULATE METRICS
            // -------------------------------------------------------------
            
            val allDaySessions = analyticsDao.getSessionsForDay(date)
            // Only count sessions that started AFTER our metric start
            // (e.g. If day started at 8 AM, ignore 7 AM noise)
            val metricSessions = allDaySessions.filter { it.startTime >= dayMetricStart }
            
            val appSessions = metricSessions.filter { it.type == PulseEvents.SESSION_APP }

            // --- First App ---
            val firstApp = appSessions
                .filter { it.packageName !in ignoredApps && it.packageName !in launcherPackages }
                .minByOrNull { it.startTime }
                
            // --- Last App ---
            // Just satisfy the "Latest" available. 
            // Note: If finalizing history, this is "Latest app used on that calendar day".
            // Correction logic usually fixes Yesterday's Last App to be "Before Sleep".
            val lastApp = appSessions
                .filter { it.packageName !in ignoredApps && it.packageName !in launcherPackages }
                .maxByOrNull { it.startTime }

            // --- Offline Streak ---
            // "Max offline session after day start"
            val offlineStreakSession = metricSessions
                .filter { it.type == PulseEvents.SESSION_OFFLINE }
                .maxByOrNull { it.duration }

            // --- Standard Totals ---
            val appTime = metricSessions
                .filter { 
                    it.type == PulseEvents.SESSION_APP && 
                    it.packageName !in com.focux.pulse.utilities.PULSE_IGNORED_APPS 
                }
                .sumOf { it.duration }
                
            val rawGlanceCount = metricSessions.count { it.type == PulseEvents.SESSION_GLANCE }
            val glanceTime = rawGlanceCount * com.focux.pulse.utilities.PULSE_GLANCE_ESTIMATE_MS
            val totalScreenTime = appTime + glanceTime
            
            val unlockAppCount = metricSessions.count { it.type == PulseEvents.SESSION_UNLOCK_APP }
            val unlockNoAppCount = metricSessions.count { it.type == PulseEvents.SESSION_UNLOCK_NOAPP }
            val screenCheckCount = metricSessions.count { 
                it.type == PulseEvents.SESSION_GLANCE || it.type == PulseEvents.SESSION_UNLOCK_NOAPP 
            }
            val totalUnlocks = unlockNoAppCount + unlockAppCount
            
            // --- Top 3 Apps ---
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
            
            // Save TODAY Stats
            val dailyStats = DailyStats(
                date = date,
                totalScreenTime = totalScreenTime,
                unlockNoAppCount = unlockNoAppCount,
                unlockAppCount = unlockAppCount,
                glanceCount = rawGlanceCount,
                screenCheckCount = screenCheckCount,
                focusScore = (100 - totalUnlocks * 2).coerceIn(0, 100),
                productiveTime = 0,
                neutralTime = totalScreenTime,
                distractingTime = 0,
                firstAppPackage = firstApp?.packageName,
                firstAppStartTime = firstApp?.startTime ?: 0,
                firstAppEndTime = firstApp?.endTime ?: 0,
                lastAppPackage = lastApp?.packageName,
                lastAppStartTime = lastApp?.startTime ?: 0,
                lastAppEndTime = lastApp?.endTime ?: 0,
                offlineStreakDuration = offlineStreakSession?.duration ?: 0,
                offlineStreakStart = offlineStreakSession?.startTime ?: 0,
                offlineStreakEnd = offlineStreakSession?.endTime ?: 0,
                topApp1Package = topApp1?.first,
                topApp1Duration = topApp1?.second ?: 0,
                topApp2Package = topApp2?.first,
                topApp2Duration = topApp2?.second ?: 0,
                topApp3Package = topApp3?.first,
                topApp3Duration = topApp3?.second ?: 0,
                sleepTimeStart = finalSleepStart,
                sleepTimeEnd = finalSleepEnd,
                sleepBreakCount = 0,
                sleepPhoneDuration = 0,
                sleepReadableStart = com.focux.pulse.utilities.TimeUtils.format(finalSleepStart),
                sleepReadableEnd = com.focux.pulse.utilities.TimeUtils.format(finalSleepEnd)
            )
            analyticsDao.updateDailyStats(dailyStats)
            registerNewApps(appSessions, ignoredApps)


            // -------------------------------------------------------------
            // 4. SCENARIO C: HISTORICAL CORRECTION (YESTERDAY)
            // -------------------------------------------------------------
            // If we found a valid sleep start (e.g. 2:15 AM today), we must update Yesterday's stats.
            // Yesterday's "Active Day" ended at 2:15 AM (Sleep Start).
            
            if (!isPendingPhase && actualSleepSession != null) {
                val yesterdayCal = Calendar.getInstance().apply { time = currentDateObj; add(Calendar.DAY_OF_YEAR, -1) }
                val yesterdayDate = sdfDay.format(yesterdayCal.time)
                
                val yesterdayStats = analyticsDao.getDailyStats(yesterdayDate)
                
                if (yesterdayStats != null) {
                    // Fetch LAST session closest to Sleep Start
                    // Query window: [Yesterday Noon -> Today Sleep Start]
                    // This catches late night usage (e.g. 1 AM)
                    val lateWindowStart = yesterdayCal.apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis
                    
                    val lateSessions = analyticsDao.getSessionsOverlapping(lateWindowStart, finalSleepStart)
                    
                    val correctedLastApp = lateSessions
                        .filter { 
                             it.type == PulseEvents.SESSION_APP && 
                             it.packageName !in ignoredApps && it.packageName !in launcherPackages
                        }
                        .maxByOrNull { it.startTime }
                        
                    if (correctedLastApp != null) {
                        // Update Yesterday's Last App
                        val updatedYesterday = yesterdayStats.copy(
                            lastAppPackage = correctedLastApp.packageName,
                            lastAppStartTime = correctedLastApp.startTime,
                            lastAppEndTime = correctedLastApp.endTime
                        )
                        analyticsDao.updateDailyStats(updatedYesterday)
                    }
                }
            }
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
