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
     * Processes APP sessions and SCREEN sessions in separate passes.
     */
    suspend fun processPendingData() {
        val lastProcessedId = analyticsDao.getState("last_processed_raw_id")?.toLong() ?: 0L
        val newEvents = rawDataDao.getEventsSince(lastProcessedId)
        if (newEvents.isEmpty()) return

        Log.d(TAG, "Processing ${newEvents.size} new raw events...")

        val sortedEvents = newEvents.sortedBy { it.timestamp }
        val allSessions = mutableListOf<AppSession>()
        
        // PASS 1: Process APP sessions (APP_OPEN -> APP_CLOSE pairs)
        val (appSessions, appLastProcessedId) = processAppSessions(sortedEvents, lastProcessedId)
        allSessions.addAll(appSessions)
        
        // PASS 2: Process SCREEN sessions (SCREEN_ON -> SCREEN_OFF cycles)
        val (screenSessions, screenLastProcessedId) = processScreenSessions(sortedEvents, lastProcessedId)
        allSessions.addAll(screenSessions)

        // Save all sessions
        if (allSessions.isNotEmpty()) {
            Log.d(TAG, "Created ${allSessions.size} sessions (${appSessions.size} APP, ${screenSessions.size} SCREEN)")
            analyticsDao.insertSessions(allSessions)
            updateDailyStats(allSessions)
        }

        // Update bookmark to the minimum of both passes (to avoid skipping incomplete data)
        val newLastProcessedId = minOf(appLastProcessedId, screenLastProcessedId)
        if (newLastProcessedId > lastProcessedId) {
            analyticsDao.updateState(SystemState("last_processed_raw_id", newLastProcessedId.toString()))
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
                        // Unmatched OPEN - stop processing APP sessions here
                        Log.d(TAG, "Unmatched APP_OPEN at ID ${event.id}, stopping APP pass.")
                        break
                    }

                    val (closeTime, indicesToMark) = closeResult
                    val duration = closeTime - event.timestamp
                    
                    // Only create session if duration is meaningful (not jitter)
                    if (duration >= PULSE_JITTER_THRESHOLD_MS) {
                        sessions.add(createSession(
                            pkg = event.packageName ?: "unknown",
                            start = event.timestamp,
                            end = closeTime,
                            type = PulseEvents.SESSION_APP,
                            date = getDateString(event.timestamp)
                        ))
                    }

                    processedIndices.addAll(indicesToMark)
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

                val (session, indicesToMark) = screenResult
                sessions.add(session)
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
     * Processes a single SCREEN_ON -> SCREEN_OFF cycle.
     */
    private fun processScreenCycle(
        events: List<RawData>,
        screenOnIndex: Int
    ): Pair<AppSession, List<Int>>? {
        val screenOnEvent = events[screenOnIndex]
        val indicesToMark = mutableListOf<Int>()

        var didUnlock = false
        var didUseApp = false
        var screenOffTime: Long? = null

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
                PulseEvents.SCREEN_OFF -> {
                    screenOffTime = event.timestamp
                    indicesToMark.add(j)
                    break
                }
                PulseEvents.SCREEN_ON -> {
                    // Jitter check
                    if (event.timestamp - screenOnEvent.timestamp < PULSE_JITTER_THRESHOLD_MS) {
                        indicesToMark.add(j)
                        continue
                    }
                    // Another SCREEN_ON without SCREEN_OFF - incomplete
                    return null
                }
            }
        }

        if (screenOffTime == null) {
            return null
        }

        val sessionType = when {
            !didUnlock -> PulseEvents.SESSION_GLANCE
            didUnlock && !didUseApp -> PulseEvents.SESSION_UNLOCK_NOAPP
            else -> PulseEvents.SESSION_UNLOCK_APP
        }

        val session = createSession(
            pkg = "system",
            start = screenOnEvent.timestamp,
            end = screenOffTime,
            type = sessionType,
            date = getDateString(screenOnEvent.timestamp)
        )

        return Pair(session, indicesToMark)
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
        val startEvent = events[startIndex]

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
            val timeDiff = nextEvent.timestamp - events[currentOpenIndex].timestamp

            // If the next event (OPEN or CLOSE) is within jitter threshold, skip it
            if (timeDiff < PULSE_JITTER_THRESHOLD_MS && !hasOtherAppInBetween) {
                indicesToMark.add(nextSamePackageIndex)
                if (nextEvent.eventLabel == PulseEvents.APP_OPEN) {
                    currentOpenIndex = nextSamePackageIndex
                }
                continue
            }

            if (nextEvent.eventLabel == PulseEvents.APP_CLOSE) {
                // Check for jitter after CLOSE
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
                // Another OPEN - use as close time but don't mark it
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
