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
            
            // 1. Convert App Sessions + Offline Sessions to Wrapper for Sorting
            val sessionEvents = sessions
                .filter { 
                    (it.type == PulseEvents.SESSION_APP || it.type == PulseEvents.SESSION_OFFLINE) &&
                    it.packageName !in PULSE_IGNORED_APPS &&
                    it.packageName !in launcherPackages
                }
                .map { session ->
                    val isOffline = session.type == PulseEvents.SESSION_OFFLINE
                    
                    val event = if (isOffline) {
                        TimelineEvent(
                            time = formatTime(session.startTime),
                            app = AppUsage(
                                name = "Deep Work",
                                iconName = "deep_work_icon",
                                duration = formatDuration(session.duration),
                                type = ActivityType.Productive
                            ),
                            range = "${formatTime(session.startTime)} - ${formatTime(session.endTime)}",
                            isDeepWork = true,
                            deepWorkDuration = formatDuration(session.duration)
                        )
                    } else {
                        TimelineEvent(
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
                    }
                    
                    TimedEvent(session.startTime, event)
                }
            
            val allEvents = sessionEvents.toMutableList()
            
            // 2. Inject Daily Stats Facts
            if (dailyStats != null) {
                // Morning (Data Start / Wake Up)
                // USER REQUEST: Anchor to "Day Start" so it's always the first card, even before Deep Work.
                val dayStartTime = if (dailyStats.sleepTimeEnd > 0) dailyStats.sleepTimeEnd else dailyStats.firstAppStartTime
                
                if (dayStartTime > 0) {
                    allEvents.add(TimedEvent(
                        timestamp = dayStartTime,
                        priority = 0, // Priority 0 = Highest (Top)
                        event = TimelineEvent(
                            time = formatTime(dayStartTime),
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
                        priority = 2, // Priority 2 = Lowest (Bottom)
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
            
            // 4. Sort (Timestamp -> Priority)
            val sortedEvents = filteredEvents
                .sortedWith(compareBy<TimedEvent> { it.timestamp }.thenBy { it.priority })
                .map { it.event }
                
            // 5. Merge Adjacent Deep Work (User Request: "Saw two deep work sessions together")
            _timelineEvents.value = mergeAdjacentDeepWork(sortedEvents)
        }
    }
    
    private fun mergeAdjacentDeepWork(events: List<TimelineEvent>): List<TimelineEvent> {
        if (events.isEmpty()) return events
        
        val merged = mutableListOf<TimelineEvent>()
        var current = events[0]
        
        for (i in 1 until events.size) {
            val next = events[i]
            
            if (current.isDeepWork && next.isDeepWork) {
                // Merge Logic
                // We sum durations if possible or just extend the range.
                // Assuming simple range extension for now.
                // Duration parsing is tricky ("1h 20m"), so we'll just merge representationally if needed 
                // BUT better to just keep the first one's start and last one's end?
                // Actually, re-parsing duration string is annoying.
                // Simpler: Just skip the second one?
                // No, duration needs to be correct.
                // Ideally we merge at Session level (before mapping), but here we are post-Facts.
                // Let's assume we consume 'next' into 'current'.
                
                // For simplicity/robustness without parsing strings:
                // We won't sum text durations. We'll simply output them as separate if complex,
                // BUT user specifically asked to fix "two together".
                // Since this is a UI string manipulation, let's look at skipping 'next' but using its End Time?
                // "14:02 - 14:16" + "14:16 - 15:00" -> "14:02 - 15:00"
                
                val newRange = "${current.range.substringBefore(" - ")} - ${next.range.substringAfter(" - ")}"
                // Duration? We can't easily sum "1h" + "10m" without helper.
                // Let's rely on range. Duration text might be slightly off (showing only first part) 
                // UNLESS we have access to raw duration. We don't here.
                // Workaround: Since we don't have raw duration, we'll keep them separate 
                // IF we can't merge safely. 
                // BUT, most adjacent offline sessions are Split by Phantom Notifications (0s).
                // So the gap is negligible.
                
                // Let's try to pass raw duration in TimelineEvent? No, data class is fixed.
                // OK, I will just emit them as is for now but if they are identical?
                // wait, if I can't merge durations, I shouldn't merge.
                // I'll skip merging implementation complexity here and focus on "Morning" fix + Filtering?
                // No, User explicitly asked "saw two deep work sessions together... what could be reason".
                // I MUST merge them.
                
                // Hack: Pass raw duration in 'TimelineEvent' or parse it?
                // Parsing "1h 20m" is doable.
                
                val dur1 = parseDurationStr(current.deepWorkDuration ?: "0m")
                val dur2 = parseDurationStr(next.deepWorkDuration ?: "0m")
                val totalDur = dur1 + dur2
                
                current = current.copy(
                    range = newRange,
                    deepWorkDuration = formatDuration(totalDur),
                    app = current.app.copy(duration = formatDuration(totalDur))
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
    
    private fun parseDurationStr(dur: String): Long {
        var ms = 0L
        try {
            val parts = dur.split(" ")
            for (part in parts) {
                if (part.contains("h")) {
                    ms += part.replace("h", "").toLong() * 60 * 60 * 1000
                } else if (part.contains("m")) {
                    ms += part.replace("m", "").toLong() * 60 * 1000
                }
            }
        } catch (e: Exception) {
            return 0L
        }
        return ms
    }
    
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
