package com.focux.pulse.ui.screens.Timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focux.pulse.data.local.entities.*
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.utilities.*
import com.focux.pulse.utilities.AppInfoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Timeline screen.
 * Fetches app sessions for a specific date and converts to TimelineEvent.
 */
class TimelineViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = PulseDatabase.getDatabase(application)
    private val analyticsDao = database.analyticsDao()

    private val _timelineEvents = MutableStateFlow<List<TimelineEvent>>(emptyList())
    val timelineEvents: StateFlow<List<TimelineEvent>> = _timelineEvents

    private val _earliestDate = MutableStateFlow<String?>(null)
    val earliestDate: StateFlow<String?> = _earliestDate

    init {
        viewModelScope.launch {
            _earliestDate.value = analyticsDao.getEarliestDate()
        }
    }

    fun loadDataForDate(dateStr: String) {
        viewModelScope.launch {
            val sessions = analyticsDao.getSessionsForDay(dateStr)
            val dailyStats = analyticsDao.getDailyStats(dateStr)
            
            // Get ignored lists
            val launcherPackages = AppInfoHelper.getLauncherPackages(context)
            
            // 1. Convert App Sessions to Wrapper for Sorting
            val sessionEvents = sessions
                .filter { 
                    it.type == PulseEvents.SESSION_APP &&
                    it.packageName !in PULSE_IGNORED_APPS &&
                    it.packageName !in launcherPackages
                }
                .map { session ->
                    val event = TimelineEvent(
                        time = formatTime(session.startTime),
                        app = AppUsage(
                            name = AppInfoHelper.getAppName(context, session.packageName),
                            iconName = session.packageName,
                            duration = formatDuration(session.duration),
                            type = ActivityType.Neutral
                        ),
                        range = "${formatTime(session.startTime)} - ${formatTime(session.endTime)}",
                        isDeepWork = session.duration >= 30 * 60 * 1000,
                        deepWorkDuration = if (session.duration >= 30 * 60 * 1000) 
                            formatDuration(session.duration) else null
                    )
                    TimedEvent(session.startTime, event)
                }
            
            val allEvents = sessionEvents.toMutableList()
            
            // 2. Inject Daily Stats Facts
            if (dailyStats != null) {
                // Morning (First App Start)
                if (dailyStats.firstAppStartTime > 0) {
                    allEvents.add(TimedEvent(
                        timestamp = dailyStats.firstAppStartTime,
                        priority = 0, // Ensure it appears BEFORE the app session
                        event = TimelineEvent(
                            time = formatTime(dailyStats.firstAppStartTime),
                            app = AppUsage("Morning", "sun_icon", "", null),
                            range = "",
                            isDeepWork = false
                        )
                    ))
                }
                
                // Night (Last App End)
                // We use EndTime for Night to signify the "End of Day"
                if (dailyStats.lastAppEndTime > 0) {
                    allEvents.add(TimedEvent(
                        timestamp = dailyStats.lastAppEndTime,
                        priority = 2, // Ensure it appears AFTER the app session
                        event = TimelineEvent(
                            time = formatTime(dailyStats.lastAppEndTime),
                            app = AppUsage("Night", "moon_icon", "", null),
                            range = "",
                            isDeepWork = false
                        )
                    ))
                }
            }

            // 3. Filter by Daily Stats Window (User Request: "Consider start day logic")
            // This removes sessions from 00:00-07:00 if the "Day" started at 07:27.
            // Also clamps to Last App if "Night" has occurred.
            val windowStart = if (dailyStats != null && dailyStats.sleepTimeEnd > 0) 
                dailyStats.sleepTimeEnd else 0L
                
            val windowEnd = if (dailyStats != null && dailyStats.lastAppEndTime > 0) 
                dailyStats.lastAppEndTime else Long.MAX_VALUE

            val filteredEvents = allEvents.filter { 
                it.timestamp >= windowStart && it.timestamp <= windowEnd
            }
            
            // 4. Sort and Extract (Timestamp -> Priority)
            // Priority ensures "Morning" (0) is before App (1) at the same second.
            _timelineEvents.value = filteredEvents
                .sortedWith(compareBy<TimedEvent> { it.timestamp }.thenBy { it.priority })
                .map { it.event }
        }
    }
    
    // Helper wrapper for sorting
    // Priority: 0=Morning, 1=App, 2=Night
    private data class TimedEvent(val timestamp: Long, val event: TimelineEvent, val priority: Int = 1)

    private fun formatDuration(ms: Long): String {
        val hours = ms / (1000 * 60 * 60)
        val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) return "--:--"
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}
