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
                
            // 2. Glances/Checks: "Distraction Penalty"
            //    - SESSION_GLANCE: Locked -> Screen On -> Screen Off (No Unlock).
            //    - SESSION_UNLOCK_NOAPP: Unlocked -> Launcher (No "Real" App) -> Locked.
            //    Reflects the cognitive load of "checking" the phone, even if brief.
            val glanceCount = allDaySessions.count { 
                it.type == PulseEvents.SESSION_GLANCE || it.type == PulseEvents.SESSION_UNLOCK_NOAPP 
            }
            
            // 3. Weighting: Each check adds a fixed estimate (e.g. 2s) to the total.
            val glanceTime = glanceCount * com.focux.pulse.utilities.PULSE_GLANCE_ESTIMATE_MS
            val totalScreenTime = appTime + glanceTime

            // Count unlocks
            val unlockNoAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_NOAPP }
            val unlockAppCount = allDaySessions.count { it.type == PulseEvents.SESSION_UNLOCK_APP }
            
            // "Glances" = User checked phone (Glance) OR User unlocked but didn't open an app (Unlock No App)
            val screenCheckCount = allDaySessions.count { 
                it.type == PulseEvents.SESSION_GLANCE || it.type == PulseEvents.SESSION_UNLOCK_NOAPP 
            }

            // Top 3 apps by duration (excluding ignored apps)
            val appDurations = appSessions
                .filter { it.packageName !in ignoredApps }
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
                .filter { it.packageName !in ignoredApps && it.packageName !in launcherPackages }
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
            registerNewApps(appSessions, ignoredApps)
        }
    }

    /**
     * Calculates the longest offline streak (gap between sessions) excluding sleep.
     */
    private fun calculateOfflineStreak(sessions: List<AppSession>): Triple<Long, Long, Long>? {
        // Filter out passive sessions (Notifications) - they don't break the streak
        val activeSessions = sessions.filter { 
            it.type != PulseEvents.SESSION_NOTIFICATION 
        }

        if (activeSessions.size < 2) return null

        // Sort sessions by start time
        val sortedSessions = activeSessions.sortedBy { it.startTime }
        
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
        
        // Filter out sleep gaps: >= threshold AND overlaps with sleep window (00:00 - 06:00)
        val calendar = Calendar.getInstance()
        val nonSleepGaps = gaps.filter { (duration, start, end) ->
            // Check if this is a sleep gap (long enough)
            val isSleepDuration = duration >= PULSE_SLEEP_THRESHOLD_MS
            
            if (!isSleepDuration) {
                true // Keep short gaps (not sleep)
            } else {
                // Check if gap overlaps with sleep window (cross-midnight support)
                calendar.timeInMillis = start
                val startHour = calendar.get(Calendar.HOUR_OF_DAY)
                calendar.timeInMillis = end
                val endHour = calendar.get(Calendar.HOUR_OF_DAY)
                
                // Logic: Does the gap intersect 00:00-06:00?
                // Simplest check: start or end is in window, OR gap covers the entire window
                
                // Helper to check if hour is in window
                fun isHourInWindow(h: Int) = h in PULSE_SLEEP_WINDOW_START_HOUR until PULSE_SLEEP_WINDOW_END_HOUR
                
                val startInWindow = isHourInWindow(startHour)
                val endInWindow = isHourInWindow(endHour)
                
                // If it starts before window and ends after window (e.g. 23:00 to 07:00), it covers the window.
                // Since window starts at 0, checking if start > end (wrapping) handles this roughly, 
                // but checking strict containment is better for 0-6 range.
                // Assuming sleep window is strictly 00:00 to 06:00 for now.
                
                val spansWindow = startHour > endHour && endHour >= PULSE_SLEEP_WINDOW_END_HOUR 
                // e.g. Start 23, End 7. 23 > 7, 7 >= 6.
                
                val isSleepOverlap = startInWindow || endInWindow || spansWindow
                
                !isSleepOverlap // Keep only if it DOES NOT overlap sleep
            }
        }
        
        // Return the longest non-sleep gap
        return nonSleepGaps.maxByOrNull { it.first }
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
