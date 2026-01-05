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
    ): Pair<AppSession, List<Int>>? {
        val screenOnEvent = events[screenOnIndex]
        val indicesToMark = mutableListOf<Int>()

        var hasUnlock = false
        var endTime: Long? = null

        for (j in (screenOnIndex + 1) until events.size) {
            val event = events[j]

            when (event.eventLabel) {
                PulseEvents.UNLOCK -> {
                    hasUnlock = true
                    indicesToMark.add(j)
                }
                PulseEvents.LOCK -> {
                    // LOCK ends UNLOCK_NOAPP sessions
                    if (hasUnlock) {
                        endTime = event.timestamp
                        indicesToMark.add(j)
                        // Create UNLOCK_NOAPP session
                        val session = createSession(
                            pkg = "system",
                            start = screenOnEvent.timestamp,
                            end = endTime,
                            type = PulseEvents.SESSION_UNLOCK_NOAPP,
                            date = getDateString(screenOnEvent.timestamp)
                        )
                        return Pair(session, indicesToMark)
                    }
                    // LOCK without prior UNLOCK - skip
                    indicesToMark.add(j)
                }
                PulseEvents.APP_OPEN -> {
                    // APP_OPEN = UNLOCK_APP, end immediately
                    endTime = event.timestamp
                    // Don't mark APP_OPEN - APP sessions are processed separately
                    val session = createSession(
                        pkg = "system",
                        start = screenOnEvent.timestamp,
                        end = endTime,
                        type = PulseEvents.SESSION_UNLOCK_APP,
                        date = getDateString(screenOnEvent.timestamp)
                    )
                    return Pair(session, indicesToMark)
                }
                PulseEvents.SCREEN_OFF -> {
                    // SCREEN_OFF ends GLANCE (if no unlock) or UNLOCK_NOAPP (if unlock but no lock yet)
                    if (!hasUnlock) {
                        // GLANCE: no unlock, ended with SCREEN_OFF
                        endTime = event.timestamp
                        indicesToMark.add(j)
                        val session = createSession(
                            pkg = "system",
                            start = screenOnEvent.timestamp,
                            end = endTime,
                            type = PulseEvents.SESSION_GLANCE,
                            date = getDateString(screenOnEvent.timestamp)
                        )
                        return Pair(session, indicesToMark)
                    }
                    // If unlock happened, SCREEN_OFF can be fallback end for UNLOCK_NOAPP
                    endTime = event.timestamp
                    indicesToMark.add(j)
                    val session = createSession(
                        pkg = "system",
                        start = screenOnEvent.timestamp,
                        end = endTime,
                        type = PulseEvents.SESSION_UNLOCK_NOAPP,
                        date = getDateString(screenOnEvent.timestamp)
                    )
                    return Pair(session, indicesToMark)
                }
                PulseEvents.SCREEN_ON -> {
                    // Jitter check
                    if (event.timestamp - screenOnEvent.timestamp < PULSE_JITTER_THRESHOLD_MS) {
                        indicesToMark.add(j)
                        continue
                    }
                    // Another SCREEN_ON - incomplete
                    return null
                }
            }
        }

        // No end marker found - incomplete
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
