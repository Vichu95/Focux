package com.focux.pulse.data_logger

import android.util.Log
import com.focux.pulse.data_manager.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * The "Brain" of the analytics pipeline.
 * It reads new, unprocessed raw events from the RawData table and transforms them into
 * meaningful "AppSessions" and aggregated "DailyStats".
 *
 * This separation allows us to keep the Raw Logs (source of truth) separate from the
 * Interpretation (stats), allowing for re-calculation if logic changes.
 */
class PulseDataProcessor(
    private val rawDataDao: RawDataDao,
    private val analyticsDao: AnalyticsDao
) {

    /**
     * Trigger processing of any new data.
     * This should be called periodically (e.g., by the Worker) after data collection.
     */
    suspend fun processPendingData() {
        // 1. Where did we leave off?
        // We track the last processed raw ID to avoid re-scanning the whole table.
        val lastProcessedId = analyticsDao.getState("last_processed_raw_id")?.toLong() ?: 0L

        // 2. Fetch new raw events
        val newEvents = rawDataDao.getEventsSince(lastProcessedId)
        if (newEvents.isEmpty()) return

        Log.d("PulseProcessor", "Processing ${newEvents.size} new raw events...")

        // 3. Process into Sessions
        val sessions = mutableListOf<AppSession>()
        // We iterate chronologically (Oldest -> Newest) to reconstruct the timeline
        val sortedEvents = newEvents.sortedBy { it.timestamp }
        
        var currentAppOpenTime: Long? = null
        var currentPackage: String? = null
        var lastScreenOnTime: Long? = null
        var isUnlocked = false

        for (event in sortedEvents) {
            val dateStr = getDateString(event.timestamp)

            when (event.eventLabel) {
                PulseEvents.SCREEN_ON -> {
                    lastScreenOnTime = event.timestamp
                }
                
                PulseEvents.UNLOCK -> {
                    isUnlocked = true
                    // Valid Unlock Session: Record the instant usage
                    sessions.add(createSession("system", event.timestamp, event.timestamp, "UNLOCK", dateStr))
                }
                
                PulseEvents.APP_OPEN -> {
                    if (isUnlocked) {
                        // Close previous app if exists
                        if (currentPackage != null && currentAppOpenTime != null) {
                            sessions.add(createSession(currentPackage!!, currentAppOpenTime!!, event.timestamp, "APP", dateStr))
                        }
                        // Start new app
                        currentPackage = event.packageName
                        currentAppOpenTime = event.timestamp
                    }
                }
                
                PulseEvents.APP_CLOSE -> {
                    if (event.packageName == currentPackage && currentAppOpenTime != null) {
                        sessions.add(createSession(currentPackage!!, currentAppOpenTime!!, event.timestamp, "APP", dateStr))
                        currentPackage = null
                        currentAppOpenTime = null
                    }
                }

                PulseEvents.SCREEN_OFF -> {
                    // Close any open app session
                    if (currentPackage != null && currentAppOpenTime != null) {
                        sessions.add(createSession(currentPackage!!, currentAppOpenTime!!, event.timestamp, "APP", dateStr))
                    }
                    
                    // Check if it was just a "Glance" (Screen On -> Off without Unlock)
                    // If isUnlocked is false, it means we never unlocked, so it's a notification check/glance.
                    if (!isUnlocked && lastScreenOnTime != null) {
                         sessions.add(createSession("system", lastScreenOnTime!!, event.timestamp, "SCREEN_CHECK", dateStr))
                    }

                    // Reset State
                    currentPackage = null
                    currentAppOpenTime = null
                    isUnlocked = false
                }
            }
        }

        // 4. Save Sessions & Update Stats
        if (sessions.isNotEmpty()) {
            analyticsDao.insertSessions(sessions)
            updateDailyStats(sessions)
        }

        // 5. Update Bookmark
        // Save the ID of the last event we successfully processed
        val newLastId = sortedEvents.last().id
        analyticsDao.updateState(SystemState("last_processed_raw_id", newLastId.toString()))
    }

    /**
     * Updates the daily statistics based on the newly processed sessions.
     * This uses a read-modify-write approach on the DailyStats table.
     */
    private suspend fun updateDailyStats(newSessions: List<AppSession>) {
        // Group by day to handle midnight crossovers (though rare in a single batch)
        val sessionsByDay = newSessions.groupBy { it.date }

        for ((date, daySessions) in sessionsByDay) {
            val existingStats = analyticsDao.getDailyStats(date) ?: DailyStats(date)
            
            // Calculate deltas from the new batch
            val addedScreenTime = daySessions.filter { it.type == "APP" }.sumOf { it.duration }
            val addedUnlocks = daySessions.count { it.type == "UNLOCK" }
            val addedChecks = daySessions.count { it.type == "SCREEN_CHECK" }

            // Create updated stats object
            val updatedStats = existingStats.copy(
                totalScreenTime = existingStats.totalScreenTime + addedScreenTime,
                unlockCount = existingStats.unlockCount + addedUnlocks,
                screenCheckCount = existingStats.screenCheckCount + addedChecks,
                // Simple placeholder for focus score logic (Start at 100, deduct based on distractions/unlocks)
                // TODO: Refine this algo
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
