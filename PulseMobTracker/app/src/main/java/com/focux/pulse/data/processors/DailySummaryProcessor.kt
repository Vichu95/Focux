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
    suspend fun updateDailyStats(newSessions: List<AppSession>) {
        val sessionsByDay = newSessions.groupBy { it.date }

        for ((date, daySessions) in sessionsByDay) {
            val existingStats = analyticsDao.getDailyStats(date) ?: DailyStats(date)

            // Get ALL APP sessions for this day (for top apps calculation)
            val allDaySessions = analyticsDao.getSessionsForDay(date)
            val appSessions = allDaySessions.filter { it.type == PulseEvents.SESSION_APP }

            // Calculate total screen time (Sum of ALL session types)
            val totalScreenTime = allDaySessions.sumOf { it.duration }

            // Count unlocks
            val unlockNoAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_NOAPP }
            val unlockAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_APP }
            val screenCheckCount = allDaySessions.count { it.type == PulseEvents.SESSION_GLANCE }

            // Top 3 apps by duration (excluding ignored apps)
            val appDurations = appSessions
                .filter { it.packageName !in PULSE_IGNORED_APPS }
                .groupBy { it.packageName }
                .mapValues { (_, sessions) -> sessions.sumOf { it.duration } }
                .toList()
                .sortedByDescending { it.second }
                .take(3)

            val topApp1 = appDurations.getOrNull(0)
            val topApp2 = appDurations.getOrNull(1)
            val topApp3 = appDurations.getOrNull(2)

            // First and last app of the day (by start time)
            val sortedAppSessions = appSessions
                .filter { it.packageName !in PULSE_IGNORED_APPS }
                .sortedBy { it.startTime }
            
            val firstApp = sortedAppSessions.firstOrNull()
            val lastApp = sortedAppSessions.lastOrNull()

            // Focus score: simple formula (can be enhanced later)
            val totalUnlocks = unlockNoAppCount + unlockAppCount
            val focusScore = (100 - totalUnlocks * 2).coerceIn(0, 100)

            // Calculate offline streak (longest gap excluding sleep)
            val offlineStreak = calculateOfflineStreak(allDaySessions)

            // Calculate time by category (for now, all apps are NEUTRAL)
            val productiveTime = 0L
            val neutralTime = totalScreenTime  // All time is neutral until apps are categorized
            val distractingTime = 0L

            val updatedStats = DailyStats(
                date = date,
                totalScreenTime = totalScreenTime,
                unlockNoAppCount = unlockNoAppCount,
                unlockAppCount = unlockAppCount,
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
                offlineStreakDuration = offlineStreak?.first ?: 0,
                offlineStreakStart = offlineStreak?.second ?: 0,
                offlineStreakEnd = offlineStreak?.third ?: 0,
                // Top 3 apps
                topApp1Package = topApp1?.first,
                topApp1Duration = topApp1?.second ?: 0,
                topApp2Package = topApp2?.first,
                topApp2Duration = topApp2?.second ?: 0,
                topApp3Package = topApp3?.first,
                topApp3Duration = topApp3?.second ?: 0
            )
            analyticsDao.updateDailyStats(updatedStats)

            // Auto-discover new apps and add to AppInfo table
            registerNewApps(appSessions)
        }
    }

    /**
     * Calculates the longest offline streak (gap between sessions) excluding sleep.
     */
    private fun calculateOfflineStreak(sessions: List<AppSession>): Triple<Long, Long, Long>? {
        if (sessions.size < 2) return null

        // Sort sessions by start time
        val sortedSessions = sessions.sortedBy { it.startTime }
        
        // Calculate gaps between sessions
        val gaps = mutableListOf<Triple<Long, Long, Long>>()  // (duration, start, end)
        
        for (i in 0 until sortedSessions.size - 1) {
            val currentEnd = sortedSessions[i].endTime
            val nextStart = sortedSessions[i + 1].startTime
            
            if (nextStart > currentEnd) {
                val gapDuration = nextStart - currentEnd
                gaps.add(Triple(gapDuration, currentEnd, nextStart))
            }
        }
        
        if (gaps.isEmpty()) return null
        
        // Filter out sleep gaps: >= threshold AND within sleep window (12AM-6AM)
        val calendar = Calendar.getInstance()
        val nonSleepGaps = gaps.filter { (duration, start, end) ->
            // Check if this is a sleep gap
            val isSleepDuration = duration >= PULSE_SLEEP_THRESHOLD_MS
            
            // Check if gap overlaps with sleep window
            calendar.timeInMillis = start
            val startHour = calendar.get(Calendar.HOUR_OF_DAY)
            calendar.timeInMillis = end
            val endHour = calendar.get(Calendar.HOUR_OF_DAY)
            
            val isInSleepWindow = (startHour >= PULSE_SLEEP_WINDOW_START_HOUR && startHour < PULSE_SLEEP_WINDOW_END_HOUR) ||
                                  (endHour >= PULSE_SLEEP_WINDOW_START_HOUR && endHour < PULSE_SLEEP_WINDOW_END_HOUR) ||
                                  (startHour < PULSE_SLEEP_WINDOW_START_HOUR && endHour >= PULSE_SLEEP_WINDOW_END_HOUR)
            
            // Exclude if both conditions are met (it's a sleep gap)
            !(isSleepDuration && isInSleepWindow)
        }
        
        // Return the longest non-sleep gap
        return nonSleepGaps.maxByOrNull { it.first }
    }

    /**
     * Registers new apps discovered in sessions to the AppInfo table.
     */
    private suspend fun registerNewApps(appSessions: List<AppSession>) {
        val packages = appSessions
            .map { it.packageName }
            .filter { it !in PULSE_IGNORED_APPS }
            .distinct()
        
        // Create AppInfo with resolved app names
        val newApps = packages.map { pkg ->
            val appName = AppInfoHelper.getAppName(context, pkg)
            AppInfo(packageName = pkg, appName = appName)
        }
        appInfoDao.insertAllIfNotExists(newApps)
    }
}
