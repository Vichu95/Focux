package com.focux.pulse.data.processors

import android.content.Context
import android.util.Log
import com.focux.pulse.data.local.dao.*
import com.focux.pulse.data.local.entities.*
import com.focux.pulse.ui.theme.PULSE_IGNORED_APPS
import com.focux.pulse.ui.theme.PULSE_JITTER_THRESHOLD_MS
import com.focux.pulse.ui.theme.PULSE_SLEEP_THRESHOLD_MS
import com.focux.pulse.ui.theme.PULSE_SLEEP_WINDOW_START_HOUR
import com.focux.pulse.ui.theme.PULSE_SLEEP_WINDOW_END_HOUR
import com.focux.pulse.ui.theme.PULSE_UNMATCHED_SKIP_THRESHOLD
import java.text.SimpleDateFormat
import java.util.*

/**
 * The "Brain" of the analytics pipeline.
 * Processes raw events into meaningful AppSessions and aggregated DailyStats.
 *
 * Session Types:
 * - SESSION_APP: Normal app usage (package + duration)
 * - SESSION_GLANCE: Screen turned on, but no unlock
 * - SESSION_UNLOCK_NOAPP: Unlocked phone, but no app used before locking again
 * - SESSION_UNLOCK_APP: Unlocked and used apps (normal usage cycle)
 */
class SessionProcessor(
    private val context: Context,
    private val rawDataDao: RawDataDao,
    private val analyticsDao: AnalyticsDao,
    private val appInfoDao: AppInfoDao
) {
    companion object {
        private const val TAG = "SessionProcessor"
        private const val MAX_SESSION_DURATION_MS = 8 * 60 * 60 * 1000L // 8 hours
    }

    /**
     * Main processing entry point.
     * Processes APP sessions and SCREEN sessions with SEPARATE bookmarks.
     * This ensures one type doesn't block the other.
     */
    suspend fun processPendingData() {
        val allSessions = mutableListOf<AppSession>()
        
        // PASS 1: Process APP sessions (independent bookmark)
        val lastAppId = analyticsDao.getState("last_processed_app_id")?.toLong() ?: 0L
        val appEvents = rawDataDao.getEventsSince(lastAppId)
        if (appEvents.isNotEmpty()) {
            val sortedAppEvents = appEvents.sortedBy { it.timestamp }
            val (appSessions, newAppId) = processAppSessions(sortedAppEvents, lastAppId)
            allSessions.addAll(appSessions)
            if (newAppId > lastAppId) {
                analyticsDao.updateState(SystemState("last_processed_app_id", newAppId.toString()))
            }
            Log.d(TAG, "APP pass: ${appSessions.size} sessions, bookmark: $lastAppId -> $newAppId")
        }
        
        // PASS 2: Process SCREEN sessions (independent bookmark)
        val lastScreenId = analyticsDao.getState("last_processed_screen_id")?.toLong() ?: 0L
        val screenEvents = rawDataDao.getEventsSince(lastScreenId)
        if (screenEvents.isNotEmpty()) {
            val sortedScreenEvents = screenEvents.sortedBy { it.timestamp }
            val (screenSessions, newScreenId) = processScreenSessions(sortedScreenEvents, lastScreenId)
            allSessions.addAll(screenSessions)
            if (newScreenId > lastScreenId) {
                analyticsDao.updateState(SystemState("last_processed_screen_id", newScreenId.toString()))
            }
            Log.d(TAG, "SCREEN pass: ${screenSessions.size} sessions, bookmark: $lastScreenId -> $newScreenId")
        }

        // Save all sessions
        if (allSessions.isNotEmpty()) {
            Log.d(TAG, "Created ${allSessions.size} total sessions")
            analyticsDao.insertSessions(allSessions)
            updateDailyStats(allSessions)
        }
    }

    /**
     * PASS 1: Process APP_OPEN -> APP_CLOSE pairs into SESSION_APP entries.
     */
    private fun processAppSessions(
        events: List<RawData>,
        initialLastProcessedId: Long
    ): Pair<List<AppSession>, Long> {
        val sessions = mutableListOf<AppSession>()
        val processedIndices = mutableSetOf<Int>()
        var lastSuccessfullyProcessedId = initialLastProcessedId

        for (i in events.indices) {
            if (i in processedIndices) continue

            val event = events[i]

            when (event.eventLabel) {
                PulseEvents.APP_OPEN -> {
                    val closeResult = findRealCloseEvent(events, i, event.packageName ?: "")

                    if (closeResult == null) {
                        // Unmatched OPEN - check if we should skip or wait
                        val eventsAfter = events.size - i - 1
                        
                        if (eventsAfter >= PULSE_UNMATCHED_SKIP_THRESHOLD) {
                            // Enough events have passed, safe to skip this unmatched OPEN
                            Log.d(TAG, "Skipping unmatched APP_OPEN at ID ${event.id} for ${event.packageName} (${eventsAfter} events after)")
                            lastSuccessfullyProcessedId = event.id
                            continue
                        } else {
                            // Not enough events yet, wait for more data
                            Log.d(TAG, "Unmatched APP_OPEN at ID ${event.id}, waiting for more data (${eventsAfter} events after)")
                            break
                        }
                    }

                    val (closeTime, indicesToMark) = closeResult
                    val duration = closeTime - event.timestamp
                    
                    // Only create session if duration is meaningful (not jitter)
                    if (duration >= PULSE_JITTER_THRESHOLD_MS) {
                        val finalCloseTime = if (duration > MAX_SESSION_DURATION_MS) {
                            Log.w(TAG, "Cap session > 8h: $duration for ${event.packageName}")
                            event.timestamp + MAX_SESSION_DURATION_MS
                        } else {
                            closeTime
                        }

                        // Use createSessions to handle Day Boundary splitting
                        sessions.addAll(createSessions(
                            pkg = event.packageName ?: "unknown",
                            start = event.timestamp,
                            end = finalCloseTime,
                            type = PulseEvents.SESSION_APP
                        ))
                    }

                    processedIndices.addAll(indicesToMark)
                    processedIndices.add(i)  // Mark the OPEN as processed too
                    lastSuccessfullyProcessedId = if (indicesToMark.isNotEmpty()) {
                        events[indicesToMark.last()].id
                    } else {
                        event.id
                    }
                }

                PulseEvents.APP_CLOSE -> {
                    // Orphan CLOSE - skip
                    lastSuccessfullyProcessedId = event.id
                }

                else -> {
                    // Skip non-APP events in this pass
                    lastSuccessfullyProcessedId = event.id
                }
            }
        }

        return Pair(sessions, lastSuccessfullyProcessedId)
    }

    /**
     * PASS 2: Process SCREEN_ON -> SCREEN_OFF cycles into GLANCE/UNLOCK sessions.
     */
    private fun processScreenSessions(
        events: List<RawData>,
        initialLastProcessedId: Long
    ): Pair<List<AppSession>, Long> {
        val sessions = mutableListOf<AppSession>()
        val processedIndices = mutableSetOf<Int>()
        var lastSuccessfullyProcessedId = initialLastProcessedId

        for (i in events.indices) {
            if (i in processedIndices) continue

            val event = events[i]

            if (event.eventLabel == PulseEvents.SCREEN_ON) {
                val screenResult = processScreenCycle(events, i)

                if (screenResult == null) {
                    // Incomplete screen cycle - stop processing SCREEN sessions here
                    Log.d(TAG, "Incomplete SCREEN_ON at ID ${event.id}, stopping SCREEN pass.")
                    break
                }

                val (resultSessions, indicesToMark) = screenResult
                sessions.addAll(resultSessions)
                processedIndices.addAll(indicesToMark)
                lastSuccessfullyProcessedId = if (indicesToMark.isNotEmpty()) {
                    events[indicesToMark.last()].id
                } else {
                    event.id
                }
            } else {
                // Skip non-SCREEN_ON events in this pass
                lastSuccessfullyProcessedId = event.id
            }
        }

        return Pair(sessions, lastSuccessfullyProcessedId)
    }

    /**
     * Processes a single SCREEN_ON cycle to determine session type.
     * 
     * Final Logic:
     * - SESSION_GLANCE: SCREEN_ON → SCREEN_OFF (no APP_OPEN, no UNLOCK)
     * - SESSION_UNLOCK_NOAPP: SCREEN_ON → UNLOCK → LOCK/SCREEN_OFF (no APP_OPEN)
     * - SESSION_UNLOCK_APP: SCREEN_ON → APP_OPEN (ends immediately at first app open)
     * 
     * Key: If ANY app opened, it's UNLOCK_APP and session ends there.
     */
    private fun processScreenCycle(
        events: List<RawData>,
        screenOnIndex: Int
    ): Pair<List<AppSession>, List<Int>>? {
        val screenOnEvent = events[screenOnIndex]
        val indicesToMark = mutableListOf<Int>()

        var hasUnlock = false
        var isUnlockApp = false
        var endTime: Long? = null

        for (j in (screenOnIndex + 1) until events.size) {
            val event = events[j]

            when (event.eventLabel) {
                PulseEvents.UNLOCK -> {
                    hasUnlock = true
                    indicesToMark.add(j)
                }
                PulseEvents.APP_OPEN -> {
                    // Skip ignored apps
                    val packageName = event.packageName ?: ""
                    if (packageName in PULSE_IGNORED_APPS) {
                        continue
                    }
                    // Valid app used! Mark this session as UNLOCK_APP
                    isUnlockApp = true
                    
                    // Don't mark index - leave for app processing
                }
                PulseEvents.LOCK -> {
                    if (hasUnlock) {
                        endTime = event.timestamp
                        indicesToMark.add(j)
                        
                        // Determine type: UNLOCK_APP (if app used) or UNLOCK_NOAPP (if no app used)
                        val type = if (isUnlockApp) PulseEvents.SESSION_UNLOCK_APP else PulseEvents.SESSION_UNLOCK_NOAPP
                        
                        val sessions = createSessions(
                            pkg = "system",
                            start = screenOnEvent.timestamp,
                            end = endTime,
                            type = type
                        )
                        return Pair(sessions, indicesToMark)
                    }
                    // LOCK without unlock - skip
                    indicesToMark.add(j)
                }
                PulseEvents.SCREEN_OFF -> {
                    if (!hasUnlock) {
                        // GLANCE: no unlock
                        endTime = event.timestamp
                        indicesToMark.add(j)
                        val sessions = createSessions(
                            pkg = "system",
                            start = screenOnEvent.timestamp,
                            end = endTime,
                            type = PulseEvents.SESSION_GLANCE
                        )
                        return Pair(sessions, indicesToMark)
                    }
                    
                    // Fallback: If unlocked but no LOCK event before SCREEN_OFF
                    endTime = event.timestamp
                    indicesToMark.add(j)
                    
                    val type = if (isUnlockApp) PulseEvents.SESSION_UNLOCK_APP else PulseEvents.SESSION_UNLOCK_NOAPP
                    
                    val sessions = createSessions(
                        pkg = "system",
                        start = screenOnEvent.timestamp,
                        end = endTime,
                        type = type
                    )
                    return Pair(sessions, indicesToMark)
                }
                PulseEvents.SCREEN_ON -> {
                    // Jitter check
                    if (event.timestamp - screenOnEvent.timestamp < PULSE_JITTER_THRESHOLD_MS) {
                        indicesToMark.add(j)
                        continue
                    }
                    // Incomplete cycle
                    return null 
                }
            }
        }
        return null
    }


    /**
     * Finds the real close event for an APP_OPEN, handling jitter.
     */
    private fun findRealCloseEvent(
        events: List<RawData>,
        startIndex: Int,
        packageName: String
    ): Pair<Long, List<Int>>? {
        val indicesToMark = mutableListOf<Int>()
        var currentOpenIndex = startIndex

        while (true) {
            var nextSamePackageIndex: Int? = null
            var hasOtherAppInBetween = false

            for (j in (currentOpenIndex + 1) until events.size) {
                val event = events[j]

                // Global Terminators: Lock or Screen Off ends the session immediately
                if (event.eventLabel == PulseEvents.LOCK || event.eventLabel == PulseEvents.SCREEN_OFF) {
                    // Session ends at lock/screen off
                    // We mark this event as "visited" for the bookmark, logic-wise it's fine
                    indicesToMark.add(j) 
                    return Pair(event.timestamp, indicesToMark)
                }

                if (event.eventLabel == PulseEvents.APP_OPEN && event.packageName != packageName) {
                     // Check ignore list - if it's a valid app switch, terminate current session
                     if (event.packageName !in PULSE_IGNORED_APPS) {
                         // Session ends at the start of the next app
                         // Do NOT mark this event as consumed, it will be processed as next session
                         return Pair(event.timestamp, indicesToMark)
                     }
                    hasOtherAppInBetween = true
                }

                if (event.packageName == packageName &&
                    (event.eventLabel == PulseEvents.APP_OPEN || event.eventLabel == PulseEvents.APP_CLOSE)) {
                    nextSamePackageIndex = j
                    break
                }
            }

            if (nextSamePackageIndex == null) {
                return null
            }

            val nextEvent = events[nextSamePackageIndex]

            if (nextEvent.eventLabel == PulseEvents.APP_CLOSE) {
                // Found a CLOSE. Now check if there's immediate jitter (OPEN right after this CLOSE)
                // Look for the next same-package event after this CLOSE
                for (k in (nextSamePackageIndex + 1) until events.size) {
                    val afterEvent = events[k]
                    
                    // If another app opened, no jitter - break
                    if (afterEvent.eventLabel == PulseEvents.APP_OPEN && afterEvent.packageName != packageName) {
                        break
                    }
                    
                    // Found same package event
                    if (afterEvent.packageName == packageName) {
                        if (afterEvent.eventLabel == PulseEvents.APP_OPEN) {
                            val gap = afterEvent.timestamp - nextEvent.timestamp
                            if (gap < PULSE_JITTER_THRESHOLD_MS) {
                                // JITTER! Mark CLOSE and OPEN, continue from new OPEN
                                indicesToMark.add(nextSamePackageIndex) // Mark CLOSE
                                indicesToMark.add(k)                     // Mark jitter OPEN
                                currentOpenIndex = k
                                break // Break inner loop, continue outer while
                            }
                        }
                        // Either not OPEN or gap too large - this is real close
                        indicesToMark.add(nextSamePackageIndex)
                        return Pair(nextEvent.timestamp, indicesToMark)
                    }
                }
                
                // If we get here from break (jitter found), continue the while loop
                if (currentOpenIndex != startIndex && indicesToMark.contains(nextSamePackageIndex)) {
                    continue
                }
                
                // No event found after CLOSE, this is real close
                indicesToMark.add(nextSamePackageIndex)
                return Pair(nextEvent.timestamp, indicesToMark)
                
            } else {
                // Another OPEN without CLOSE in between
                if (!hasOtherAppInBetween) {
                    // Same app opened again - use this as implicit close
                    return Pair(nextEvent.timestamp, indicesToMark)
                } else {
                    // Other app in between, this OPEN is real close point
                    return Pair(nextEvent.timestamp, indicesToMark)
                }
            }
        }
    }

    /**
     * Updates DailyStats based on new sessions.
     * Calculates: screen time, unlocks, glances, top apps, first/last app.
     * Note: Offline streak and sleep detection require full-day recalculation.
     */
    private suspend fun updateDailyStats(newSessions: List<AppSession>) {
        val sessionsByDay = newSessions.groupBy { it.date }

        for ((date, daySessions) in sessionsByDay) {
            val existingStats = analyticsDao.getDailyStats(date) ?: DailyStats(date)

            // Get ALL APP sessions for this day (for top apps calculation)
            val allDaySessions = analyticsDao.getSessionsForDay(date)
            val appSessions = allDaySessions.filter { it.type == PulseEvents.SESSION_APP }

            // Calculate total screen time (Sum of ALL session types)
            // UNLOCK_APP covers "Navigation time" (Screen On -> App Open)
            // SESSION_APP covers "App Usage" (App Open -> ...)
            // UNLOCK_NOAPP covers "Phone Check" (Unlock -> Lock)
            // GLANCE covers "Locked Check" (Screen On -> Off)
            // Since they are sequential and non-overlapping, simple sum works.
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
            // TODO: Fetch categories from AppInfo table for proper categorization
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
     * Sleep is defined as: gap >= 3 hours AND within 12AM-6AM window.
     * 
     * @return Triple(duration, startTime, endTime) or null if no gaps found
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
     * Uses PackageManager to resolve human-readable app names.
     */
    private suspend fun registerNewApps(appSessions: List<AppSession>) {
        val packages = appSessions
            .map { it.packageName }
            .filter { it !in PULSE_IGNORED_APPS }
            .distinct()
        
        // Create AppInfo with resolved app names
        val newApps = packages.map { pkg ->
            val appName = com.focux.pulse.utilities.AppInfoHelper.getAppName(context, pkg)
            AppInfo(packageName = pkg, appName = appName)
        }
        appInfoDao.insertAllIfNotExists(newApps)
    }



    /**
     * Creates one or multiple AppSessions, splitting at Day Boundary if needed.
     * This handles sessions spanning across midnight (11:50 PM - 12:10 AM).
     */
    private fun createSessions(
        pkg: String,
        start: Long,
        end: Long,
        type: String
    ): List<AppSession> {
        val sessions = mutableListOf<AppSession>()
        var currentStart = start

        while (currentStart < end) {
            // Find the boundary for the current "Day"
            // For now, hardcoded to 12 AM midnight boundary
            // In future, can be updated to support custom day start hours
            val boundary = getNextDayBoundary(currentStart)
            
            // If the session ends before the boundary, it's a single session
            val splitEnd = if (end <= boundary) end else boundary
            
            // Add session for this chunk
            if (splitEnd > currentStart) { // Safety check
                sessions.add(AppSession(
                    packageName = pkg,
                    startTime = currentStart,
                    endTime = splitEnd,
                    duration = splitEnd - currentStart,
                    type = type,
                    date = getDateString(currentStart)
                ))
            }

            // Move start to next chunk (boundary)
            currentStart = boundary
        }
        return sessions
    }

    /**
     * Returns the timestamp of the NEXT day start (Midnight).
     * Helps in splitting sessions.
     */
    private fun getNextDayBoundary(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0) // Future: Configurable Day Start Hour
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getDateString(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
