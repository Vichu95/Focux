package com.focux.pulse.data.processors

import com.focux.pulse.data.local.entities.AppSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Shared utility for splitting time ranges into day-bound sessions.
 */
object SessionSplitter {

    /**
     * Creates one or multiple AppSessions, splitting at Day Boundary if needed.
     * This handles sessions spanning across midnight (11:50 PM - 12:10 AM).
     */
    fun createSessions(
        pkg: String,
        start: Long,
        end: Long,
        type: String
    ): List<AppSession> {
        val sessions = mutableListOf<AppSession>()
        var currentStart = start

        while (currentStart < end) {
            // Find the boundary for the current "Day"
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
                    date = getDateString(currentStart),
                    startTimeStr = com.focux.pulse.utilities.TimeUtils.format(currentStart),
                    endTimeStr = com.focux.pulse.utilities.TimeUtils.format(splitEnd)
                ))
            }

            // Move start to next chunk (boundary)
            currentStart = boundary
        }
        return sessions
    }

    /**
     * Returns the timestamp of the NEXT day start (Midnight).
     */
    private fun getNextDayBoundary(timestamp: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getDateString(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
