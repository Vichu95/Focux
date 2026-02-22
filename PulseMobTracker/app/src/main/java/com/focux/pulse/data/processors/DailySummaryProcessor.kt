package com.focux.pulse.data.processors

import android.content.Context
import com.focux.pulse.data.local.dao.AnalyticsDao
import com.focux.pulse.data.local.dao.AppInfoDao
import com.focux.pulse.data.local.entities.AppInfo
import com.focux.pulse.data.local.entities.AppCategory
import com.focux.pulse.data.local.entities.AppSession
import com.focux.pulse.data.local.entities.DailyStats
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.data.local.entities.SystemState
import com.focux.pulse.utilities.PULSE_IGNORED_APPS
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
     * Recalculates all daily stats from existing sessions lightning-fast.
     * Rebuilds ONLY category-dependent fields using in-memory structures to guarantee a 0-second UI bounce.
     */
    suspend fun recalculateAllDailyStats() {
        val allDailyStatsList = analyticsDao.getAllDailyStatsDesc()
        if (allDailyStatsList.isEmpty()) return

        val allApps = appInfoDao.getAllApps()
        val appCategories = allApps.associate { it.packageName to it.category }
        val ignoredApps = allApps.filter { it.category == AppCategory.IGNORED }.map { it.packageName }.toSet()
        val launcherPackages = AppInfoHelper.getLauncherPackages(context)
        
        // Fetch all sessions in memory to prevent slow DB lookups in the loop
        val allSessions = analyticsDao.getSessionsStartingFromId(0L)
        val sessionsByDay = allSessions.groupBy { it.date }

        for (dailyStat in allDailyStatsList) {
            val allDaySessions = sessionsByDay[dailyStat.date] ?: continue
            val appSessions = allDaySessions.filter { 
                it.type == PulseEvents.SESSION_APP && 
                it.startTime >= dailyStat.sleepTimeEnd 
            }

            var calcProductive = 0L
            var calcDistracting = 0L
            var calcNeutral = dailyStat.glanceCount * com.focux.pulse.utilities.PULSE_GLANCE_ESTIMATE_MS.toLong()

            appSessions.filter { it.packageName !in launcherPackages }.forEach { session ->
                val activeCategoryStr = session.categoryOverride ?: appCategories[session.packageName]
                when (activeCategoryStr) {
                    AppCategory.PRODUCTIVE -> calcProductive += session.duration
                    AppCategory.DISTRACTING -> calcDistracting += session.duration
                    else -> calcNeutral += session.duration
                }
            }

            // --- Top 3 Apps & First/Last ---
            val validApps = appSessions.filter { it.packageName !in ignoredApps && it.packageName !in launcherPackages }
            
            val firstApp = validApps.minByOrNull { it.startTime }
            val lastApp = validApps.maxByOrNull { it.startTime }
            
            val appDurations = validApps
                .groupBy { it.packageName }
                .mapValues { (_, sessions) -> sessions.sumOf { it.duration } }
                .toList()
                .sortedByDescending { it.second }
                .take(3)
                
            val topApp1 = appDurations.getOrNull(0)
            val topApp2 = appDurations.getOrNull(1)
            val topApp3 = appDurations.getOrNull(2)

            val updatedStats = dailyStat.copy(
                productiveTime = calcProductive,
                neutralTime = calcNeutral,
                distractingTime = calcDistracting,
                firstAppPackage = firstApp?.packageName,
                firstAppStartTime = firstApp?.startTime ?: 0,
                firstAppEndTime = firstApp?.endTime ?: 0,
                lastAppPackage = lastApp?.packageName,
                lastAppStartTime = lastApp?.startTime ?: 0,
                lastAppEndTime = lastApp?.endTime ?: 0,
                topApp1Package = topApp1?.first,
                topApp1Duration = topApp1?.second ?: 0,
                topApp2Package = topApp2?.first,
                topApp2Duration = topApp2?.second ?: 0,
                topApp3Package = topApp3?.first,
                topApp3Duration = topApp3?.second ?: 0
            )

            analyticsDao.updateDailyStats(updatedStats)
        }
        
        // Fire UI refresh trigger now that mass recalculation is strictly complete
        analyticsDao.updateState(SystemState("force_ui_refresh", System.currentTimeMillis().toString()))
    }

    /**
     * Updates DailyStats based on new sessions.
     * Calculates: screen time, unlocks, glances, top apps, first/last app.
     */
    suspend fun updateDailyStats(newSessions: List<AppSession>, isRecalculation: Boolean = false) {
        // Sort keys to process chronologically (Oldest -> Newest)
        // This is crucial so we "Correct" yesterday before processing today? 
        // Actually, "Correction" happens when looking back from Today. So order matters.
        val sessionsByDay = newSessions.groupBy { it.date }.toSortedMap()

        // Fetch launchers to exclude from Total Screen Time
        val launcherPackages = AppInfoHelper.getLauncherPackages(context)
        
        // Fetch User Categories
        val allApps = appInfoDao.getAllApps()
        val appCategories = allApps.associate { it.packageName to it.category }
        val ignoredApps = allApps.filter { it.category == AppCategory.IGNORED }.map { it.packageName }.toSet()
        
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
            var sleepBreakCount = 0
            var sleepPhoneDuration = 0L

            if (isPendingPhase) {
                // SCENARIO A: PENDING (Before 7 AM Today)
                // - Sleep: Defaults (Target)
                // - Day Start: Midnight (00:00)
                
                // Midnight
                val midCal = Calendar.getInstance().apply { time = currentDateObj; set(Calendar.HOUR_OF_DAY,0); set(Calendar.MINUTE,0); set(Calendar.SECOND,0) }
                dayMetricStart = midCal.timeInMillis

                finalSleepStart = targetSleepWindowStart
                
                // Fix for Today Live View: 
                // If Target Wakeup (7 AM) is in the future, fallback to Midnight.
                // This ensures early usage (e.g. 00:12 AM) is considered "After Wakeup" 
                // and visible on Yesterday/Today boundary correctly? 
                // Actually, this makes 00:12 visible on TODAY's timeline.
                finalSleepEnd = if (targetWakeUpTime > now) dayMetricStart else targetWakeUpTime

            } else {
                // SCENARIO B: FINALIZED (Yesterday or Today after 7 AM)
                // - Sleep: Find actual largest offline gap in [Yesterday 22:00 - Today 07:00]
                // - Day Start: Actual Sleep End
                
                val potentialSleepSessions = analyticsDao.getSessionsOverlapping(targetSleepWindowStart, targetWakeUpTime)
                
                // 2. Filter sessions less than threshold
                val validOfflineSessions = potentialSleepSessions.filter { 
                    it.type == PulseEvents.SESSION_OFFLINE && 
                    it.duration >= com.focux.pulse.utilities.PULSE_MIN_SLEEP_OFFLINE_THRESHOLD_MS
                }
                
                // Variables now updated in outer scope

                if (validOfflineSessions.isNotEmpty()) {
                    // 3. Find Earliest Start and Largest End
                    finalSleepStart = validOfflineSessions.minOf { it.startTime }
                    finalSleepEnd = validOfflineSessions.maxOf { it.endTime }
                    
                    // 4. Get all offline sessions in between (inclusive)
                    // We reuse potentialSleepSessions as it already covers the target window. 
                    // Technically we should ensure we don't miss anything if sleep extends way out, 
                    // but potentialSleepSessions is the best source we have loaded.
                    val allSleepParts = potentialSleepSessions.filter { 
                        it.type == PulseEvents.SESSION_OFFLINE &&
                        it.startTime >= finalSleepStart &&
                        it.endTime <= finalSleepEnd
                    }
                    
                    // 5. Calculate Metrics
                    // Breaks = Number of offline chunks - 1 
                    // (e.g. [Sleep]--break--[Sleep] is 2 chunks, 1 break)
                    if (allSleepParts.isNotEmpty()) {
                        sleepBreakCount = (allSleepParts.size - 1).coerceAtLeast(0)
                        
                        val totalSleepWindow = finalSleepEnd - finalSleepStart
                        val totalOfflineDuration = allSleepParts.sumOf { it.duration }
                        
                        sleepPhoneDuration = (totalSleepWindow - totalOfflineDuration).coerceAtLeast(0)
                    }
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
            
            // Wait for DB insertion to settle (Simple fix for Race Condition)
            if (!isRecalculation) {
                kotlinx.coroutines.delay(500)
            }

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
                    it.packageName !in launcherPackages
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
            
            // --- Semantics Update ---
            // User Request: "Sleep Start" column should semantically mean "End of Active Day" (Bedtime).
            // For Today (Live): This is the Last App usage time.
            // For Yesterday (Historical): This is the Bedtime (Sleep Onset).
            
            val activeDayEnd = lastApp?.endTime ?: (if (firstApp != null) System.currentTimeMillis() else 0L)

            var calcProductive = 0L
            var calcDistracting = 0L
            var calcNeutral = glanceTime // Glances are neutral Time

            appSessions.filter { it.packageName !in launcherPackages }.forEach { session ->
                val activeCategoryStr = session.categoryOverride ?: appCategories[session.packageName]
                when (activeCategoryStr) {
                    AppCategory.PRODUCTIVE -> calcProductive += session.duration
                    AppCategory.DISTRACTING -> calcDistracting += session.duration
                    else -> calcNeutral += session.duration
                }
            }

            // Save TODAY Stats
            val dailyStats = DailyStats(
                date = date,
                totalScreenTime = totalScreenTime,
                unlockNoAppCount = unlockNoAppCount,
                unlockAppCount = unlockAppCount,
                glanceCount = rawGlanceCount,
                screenCheckCount = screenCheckCount,
                focusScore = (100 - totalUnlocks * 2).coerceIn(0, 100),
                productiveTime = calcProductive,
                neutralTime = calcNeutral,
                distractingTime = calcDistracting,
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
                // For Today, we store "Last App Time" as the Day End.
                sleepTimeStart = activeDayEnd, 
                sleepTimeEnd = finalSleepEnd, // Wakeup Time (Day Start) - Correct
                sleepBreakCount = sleepBreakCount,
                sleepPhoneDuration = sleepPhoneDuration,
                sleepReadableStart = com.focux.pulse.utilities.TimeUtils.format(activeDayEnd),
                sleepReadableEnd = com.focux.pulse.utilities.TimeUtils.format(finalSleepEnd)
            )
            analyticsDao.updateDailyStats(dailyStats)
            registerNewApps(appSessions)


            // -------------------------------------------------------------
            // 4. SCENARIO C: HISTORICAL CORRECTION (YESTERDAY)
            // -------------------------------------------------------------
            
            // -------------------------------------------------------------
            // 4. SCENARIO C: HISTORICAL CORRECTION (YESTERDAY)
            // -------------------------------------------------------------
            
            if (!isPendingPhase) {
                val yesterdayCal = Calendar.getInstance().apply { time = currentDateObj; add(Calendar.DAY_OF_YEAR, -1) }
                val yesterdayDate = sdfDay.format(yesterdayCal.time)
                
                val yesterdayStats = analyticsDao.getDailyStats(yesterdayDate)
                
                if (yesterdayStats != null) {
                    // Effective Day for Yesterday: [Yesterday WakeUp -> Today Sleep Start]
                    // If Yesterday's sleep wasn't calculated, default to 07:00 AM yesterday.
                    val yesterdayStart = if (yesterdayStats.sleepTimeEnd > 0) yesterdayStats.sleepTimeEnd else {
                        yesterdayCal.apply { set(Calendar.HOUR_OF_DAY, com.focux.pulse.utilities.PULSE_SLEEP_TARGET_WAKEUP_HOUR); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                    }
                    
                    // User Request:
                    // 1. Last App: "Last app used till today sleep end"
                    //    This captures any late night usage (e.g. 3 AM woke up, check phone, slept 4 AM). 
                    //    The 3 AM usage belongs to Yesterday's active timeline.
                    val extendedWindowEnd = finalSleepEnd 
                    
                    // 2. Offline Streak: "max offline streak till today sleep start"
                    //    This excludes the sleep session itself.
                    val sleepOnset = finalSleepStart

                    // Fetch full window for Last App search
                    val effectiveDaySessions = analyticsDao.getSessionsOverlapping(yesterdayStart, extendedWindowEnd)
                    
                    // 1. Recalculate Last App (Latest session in the full window)
                    val correctedLastApp = effectiveDaySessions
                        .filter { 
                             it.type == PulseEvents.SESSION_APP && 
                             it.packageName !in ignoredApps && it.packageName !in launcherPackages
                        }
                        .maxByOrNull { it.startTime }

                    // 2. Recalculate Offline Streak (Max offline BEFORE Sleep Onset)
                    val correctedOfflineStreak = effectiveDaySessions
                        .filter { 
                            it.type == PulseEvents.SESSION_OFFLINE && 
                            it.endTime <= sleepOnset 
                        }
                        .maxByOrNull { it.duration }

                    // Update Yesterday's Stats
                    val updatedYesterday = yesterdayStats.copy(
                        lastAppPackage = correctedLastApp?.packageName ?: yesterdayStats.lastAppPackage,
                        lastAppStartTime = correctedLastApp?.startTime ?: yesterdayStats.lastAppStartTime,
                        lastAppEndTime = correctedLastApp?.endTime ?: yesterdayStats.lastAppEndTime,
                        
                        offlineStreakDuration = correctedOfflineStreak?.duration ?: yesterdayStats.offlineStreakDuration,
                        offlineStreakStart = correctedOfflineStreak?.startTime ?: yesterdayStats.offlineStreakStart,
                        offlineStreakEnd = correctedOfflineStreak?.endTime ?: yesterdayStats.offlineStreakEnd,
                        
                        // Semantic Update: Correct Yesterday's "Active Day End" to Actual Bedtime
                        sleepTimeStart = sleepOnset, 
                        sleepReadableStart = com.focux.pulse.utilities.TimeUtils.format(sleepOnset)
                    )
                    analyticsDao.updateDailyStats(updatedYesterday)
                }
            }
        }
    }



    /**
     * Registers new apps discovered in sessions to the AppInfo table.
     */
    private suspend fun registerNewApps(appSessions: List<AppSession>) {
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
