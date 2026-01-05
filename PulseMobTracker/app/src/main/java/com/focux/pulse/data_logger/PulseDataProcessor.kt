package com.focux.pulse.data_logger

import android.util.Log
import com.focux.pulse.data_manager.*
import com.focux.pulse.ui.theme.PULSE_JITTER_THRESHOLD_MS
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
class PulseDataProcessor(
    private val rawDataDao: RawDataDao,
    private val analyticsDao: AnalyticsDao
) {
    companion object {
        private const val TAG = "PulseProcessor"
    }

    /**
     * Main processing entry point.
     * Processes both APP sessions and SCREEN sessions.
     */
    suspend fun processPendingData() {
        val lastProcessedId = analyticsDao.getState("last_processed_raw_id")?.toLong() ?: 0L
        val newEvents = rawDataDao.getEventsSince(lastProcessedId)
        if (newEvents.isEmpty()) return

        Log.d(TAG, "Processing ${newEvents.size} new raw events...")

        val sortedEvents = newEvents.sortedBy { it.timestamp }
        val sessions = mutableListOf<AppSession>()
        val processedIndices = mutableSetOf<Int>()
        var lastSuccessfullyProcessedId: Long = lastProcessedId

        var i = 0
        while (i < sortedEvents.size) {
            if (i in processedIndices) {
                i++
                continue
            }

            val event = sortedEvents[i]
            val dateStr = getDateString(event.timestamp)

            when (event.eventLabel) {
                // ==================== APP SESSIONS ====================
                PulseEvents.APP_OPEN -> {
                    val closeResult = findRealCloseEvent(sortedEvents, i, event.packageName ?: "")

                    if (closeResult == null) {
                        // Unmatched OPEN - stop processing here
                        Log.d(TAG, "Unmatched APP_OPEN at ID ${event.id}, stopping.")
                        break
                    }

                    val (closeTime, indicesToMark) = closeResult
                    sessions.add(createSession(
                        pkg = event.packageName ?: "unknown",
                        start = event.timestamp,
                        end = closeTime,
                        type = PulseEvents.SESSION_APP,
                        date = dateStr
                    ))

                    processedIndices.addAll(indicesToMark)
                    lastSuccessfullyProcessedId = if (indicesToMark.isNotEmpty()) {
                        sortedEvents[indicesToMark.last()].id
                    } else {
                        event.id
                    }
                }

                PulseEvents.APP_CLOSE -> {
                    // Orphan CLOSE - skip it
                    lastSuccessfullyProcessedId = event.id
                }

                // ==================== SCREEN SESSIONS ====================
                PulseEvents.SCREEN_ON -> {
                    val screenResult = processScreenSession(sortedEvents, i)

                    if (screenResult == null) {
                        // Incomplete screen cycle - stop here
                        Log.d(TAG, "Incomplete SCREEN_ON at ID ${event.id}, stopping.")
                        break
                    }

                    val (session, indicesToMark) = screenResult
                    sessions.add(session)
                    processedIndices.addAll(indicesToMark)
                    lastSuccessfullyProcessedId = if (indicesToMark.isNotEmpty()) {
                        sortedEvents[indicesToMark.last()].id
                    } else {
                        event.id
                    }
                }

                // Skip other events but mark as processed
                PulseEvents.SCREEN_OFF, PulseEvents.UNLOCK, PulseEvents.LOCK -> {
                    lastSuccessfullyProcessedId = event.id
                }

                else -> {
                    lastSuccessfullyProcessedId = event.id
                }
            }
            i++
        }

        // Save sessions
        if (sessions.isNotEmpty()) {
            Log.d(TAG, "Created ${sessions.size} sessions")
            analyticsDao.insertSessions(sessions)
            updateDailyStats(sessions)
        }

        // Update bookmark
        if (lastSuccessfullyProcessedId > lastProcessedId) {
            analyticsDao.updateState(SystemState("last_processed_raw_id", lastSuccessfullyProcessedId.toString()))
        }
    }

    /**
     * Processes a SCREEN_ON event to determine the session type.
     * Returns the session and indices to mark as processed, or null if incomplete.
     */
    private fun processScreenSession(
        events: List<RawData>,
        screenOnIndex: Int
    ): Pair<AppSession, List<Int>>? {
        val screenOnEvent = events[screenOnIndex]
        val indicesToMark = mutableListOf<Int>()
        
        var didUnlock = false
        var didUseApp = false
        var screenOffTime: Long? = null
        
        // Scan forward from SCREEN_ON to find SCREEN_OFF
        for (j in (screenOnIndex + 1) until events.size) {
            val event = events[j]
            
            when (event.eventLabel) {
                PulseEvents.UNLOCK -> {
                    didUnlock = true
                    indicesToMark.add(j)
                }
                PulseEvents.LOCK -> {
                    indicesToMark.add(j)
                }
                PulseEvents.APP_OPEN -> {
                    didUseApp = true
                    // Don't mark - APP sessions are processed separately
                }
                PulseEvents.APP_CLOSE -> {
                    // Don't mark - APP sessions are processed separately
                }
                PulseEvents.SCREEN_OFF -> {
                    // Found the end of this screen cycle
                    screenOffTime = event.timestamp
                    indicesToMark.add(j)
                    break
                }
                PulseEvents.SCREEN_ON -> {
                    // Jitter check: If very close to the first SCREEN_ON, skip
                    if (event.timestamp - screenOnEvent.timestamp < PULSE_JITTER_THRESHOLD_MS) {
                        indicesToMark.add(j)
                        continue
                    }
                    // Another SCREEN_ON without SCREEN_OFF - incomplete data
                    return null
                }
            }
        }
        
        if (screenOffTime == null) {
            // No SCREEN_OFF found - incomplete
            return null
        }
        
        // Determine session type
        val sessionType = when {
            !didUnlock -> PulseEvents.SESSION_GLANCE
            didUnlock && !didUseApp -> PulseEvents.SESSION_UNLOCK_NOAPP
            didUnlock && didUseApp -> PulseEvents.SESSION_UNLOCK_APP
            else -> PulseEvents.SESSION_GLANCE
        }
        
        val dateStr = getDateString(screenOnEvent.timestamp)
        val session = createSession(
            pkg = "system",
            start = screenOnEvent.timestamp,
            end = screenOffTime,
            type = sessionType,
            date = dateStr
        )
        
        return Pair(session, indicesToMark)
    }

    /**
     * Finds the real close event for an APP_OPEN, handling jitter.
     * Uses PULSE_JITTER_THRESHOLD_MS to determine if events are jitter.
     */
    private fun findRealCloseEvent(
        events: List<RawData>,
        startIndex: Int,
        packageName: String
    ): Pair<Long, List<Int>>? {
        val indicesToMark = mutableListOf<Int>()
        var currentOpenIndex = startIndex
        val startOpenEvent = events[startIndex]

        while (true) {
            var nextSamePackageIndex: Int? = null
            var hasOtherAppInBetween = false

            for (j in (currentOpenIndex + 1) until events.size) {
                val event = events[j]

                if (event.eventLabel == PulseEvents.APP_OPEN && event.packageName != packageName) {
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
                // Check for jitter using threshold
                val afterCloseIndex = nextSamePackageIndex + 1
                if (afterCloseIndex < events.size && !hasOtherAppInBetween) {
                    val afterCloseEvent = events[afterCloseIndex]
                    if (afterCloseEvent.packageName == packageName &&
                        afterCloseEvent.eventLabel == PulseEvents.APP_OPEN &&
                        (afterCloseEvent.timestamp - nextEvent.timestamp) < PULSE_JITTER_THRESHOLD_MS) {
                        // Jitter! Mark both and continue
                        indicesToMark.add(nextSamePackageIndex)
                        indicesToMark.add(afterCloseIndex)
                        currentOpenIndex = afterCloseIndex
                        continue
                    }
                }

                // Real close
                indicesToMark.add(nextSamePackageIndex)
                return Pair(nextEvent.timestamp, indicesToMark)
            } else {
                // Another OPEN - close at this timestamp
                return Pair(nextEvent.timestamp, indicesToMark)
            }
        }
    }

    /**
     * Updates DailyStats based on new sessions.
     */
    private suspend fun updateDailyStats(newSessions: List<AppSession>) {
        val sessionsByDay = newSessions.groupBy { it.date }

        for ((date, daySessions) in sessionsByDay) {
            val existingStats = analyticsDao.getDailyStats(date) ?: DailyStats(date)

            val addedScreenTime = daySessions
                .filter { it.type == PulseEvents.SESSION_APP }
                .sumOf { it.duration }
            
            val addedUnlocks = daySessions.count { 
                it.type == PulseEvents.SESSION_UNLOCK_NOAPP || it.type == PulseEvents.SESSION_UNLOCK_APP 
            }
            
            val addedGlances = daySessions.count { 
                it.type == PulseEvents.SESSION_GLANCE 
            }

            val updatedStats = existingStats.copy(
                totalScreenTime = existingStats.totalScreenTime + addedScreenTime,
                unlockCount = existingStats.unlockCount + addedUnlocks,
                screenCheckCount = existingStats.screenCheckCount + addedGlances,
                focusScore = (100 - (existingStats.unlockCount + addedUnlocks)).coerceAtLeast(0)
            )
            analyticsDao.updateDailyStats(updatedStats)
        }
    }

    private fun createSession(pkg: String, start: Long, end: Long, type: String, date: String): AppSession {
        return AppSession(
            packageName = pkg,
            startTime = start,
            endTime = end,
            duration = end - start,
            type = type,
            date = date
        )
    }

    private fun getDateString(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
