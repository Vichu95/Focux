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
     * Creates a single AppSession. 
     * Logic Updated: No longer splits at Midnight. Sessions are attributed to their Start Date.
     */
    fun createSessions(
        pkg: String,
        start: Long,
        end: Long,
        type: String
    ): List<AppSession> {
        if (start >= end) return emptyList()

        return listOf(AppSession(
            packageName = pkg,
            startTime = start,
            endTime = end,
            duration = end - start,
            type = type,
            date = getDateString(start),
            startTimeStr = com.focux.pulse.utilities.TimeUtils.format(start),
            endTimeStr = com.focux.pulse.utilities.TimeUtils.format(end)
        ))
    }

    private fun getDateString(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
