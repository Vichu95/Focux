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

    // --- Filter State ---
    data class FilterState(
        val timeRange: ClosedFloatingPointRange<Float> = 0f..24f,
        val selectedCategories: Set<ActivityType> = emptySet(),
        val selectedApps: Set<String> = emptySet(), // Package Names
        val searchQuery: String = ""
    )
    
    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState

    // Master list of events for the day (unfiltered)
    private val _allDayEvents = MutableStateFlow<List<TimedEvent>>(emptyList())
    
    // Available apps for the filter list
    private val _availableApps = MutableStateFlow<List<String>>(emptyList()) // Package names
    val availableApps: StateFlow<List<String>> = _availableApps

    init {
        viewModelScope.launch {
            _earliestDate.value = analyticsDao.getEarliestDate()
        }
        
        // Combine allEvents and filterState to produce timelineEvents
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_allDayEvents, _filterState) { events, filter ->
                filterEvents(events, filter)
            }.collect { filtered ->
                _timelineEvents.value = mergeAdjacentDeepWork(filtered)
            }
        }
    }

    private val _dayBounds = MutableStateFlow(0f..24f)
    val dayBounds: StateFlow<ClosedFloatingPointRange<Float>> = _dayBounds

    private fun getFloatTime(timestamp: Long): Float {
        if (timestamp == 0L) return 0f
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        return hour + (minute / 60f)
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
                                type = ActivityType.Neutral // TODO: Fetch category dynamically if possible
                            ),
                            range = "${formatTime(session.startTime)} - ${formatTime(session.endTime)}",
                            isDeepWork = session.duration >= 30 * 60 * 1000,
                            deepWorkDuration = if (session.duration >= 30 * 60 * 1000) 
                                formatDuration(session.duration) else null
                        )
                    }
                    
                    TimedEvent(session.startTime, event, 
                        isOffline = isOffline, 
                        packageName = session.packageName,
                        endTime = session.endTime
                    )
                }
            
            val allEvents = sessionEvents.toMutableList()
            
            // 2. Inject Daily Stats Facts
            if (dailyStats != null) {
                // Morning
                val dayStartTime = if (dailyStats.sleepTimeEnd > 0) dailyStats.sleepTimeEnd else dailyStats.firstAppStartTime
                if (dayStartTime > 0) {
                    allEvents.add(TimedEvent(
                        timestamp = dayStartTime,
                        priority = 0,
                        event = TimelineEvent(
                            time = formatTime(dayStartTime),
                            app = AppUsage("Morning", "sun_icon", "", ActivityType.Neutral),
                            range = "",
                            isDeepWork = false
                        ),
                        packageName = "Morning"
                    ))
                }
                
                // Night
                if (dailyStats.lastAppEndTime > 0) {
                    allEvents.add(TimedEvent(
                        timestamp = dailyStats.lastAppEndTime,
                        priority = 2,
                        event = TimelineEvent(
                            time = formatTime(dailyStats.lastAppEndTime),
                            app = AppUsage("Night", "moon_icon", "", ActivityType.Neutral),
                            range = "",
                            isDeepWork = false
                        ),
                        packageName = "Night"
                    ))
                }
            }
            
            // Update available apps for filter
            val appPackages = sessionEvents
                .filter { !it.isOffline }
                .map { it.packageName }
                .distinct()
                .sortedBy { AppInfoHelper.getAppName(context, it) }
            _availableApps.value = appPackages

            // Store raw list
            _allDayEvents.value = allEvents
            
            // Calculate Day Bounds for Filter Slider
            val startTs = if (dailyStats != null && dailyStats.sleepTimeEnd > 0) 
                dailyStats.sleepTimeEnd 
            else if (dailyStats != null && dailyStats.firstAppStartTime > 0) 
                dailyStats.firstAppStartTime 
            else 0L
            
            val startHour = getFloatTime(startTs)
            // If startHour is e.g. 23:00 (weird), handle it. If 0, use 0f.
            // End default 24f. Ideally could be next sleep start if > day start.
            val endHour = 24f 
            
            _dayBounds.value = startHour..endHour
            
            // Note: We used to clamp _filterState here. 
            // Removed to allow filter preference (e.g. 7-9) to persist across days with different start times.
            // The intersection logic in filterEvents() handles the effective filtering.
        }
    }
    
    private fun filterEvents(allEvents: List<TimedEvent>, filter: FilterState): List<TimelineEvent> {
        val windowStart = (filter.timeRange.start * 60 * 60 * 1000).toLong() // Offset from start of day? No, timestamp is absolute.
        // Wait, Time Range Slider (08:30 - 15:00) refers to time of day.
        // We need to convert absolute timestamps to hour-of-day float.
        
        return allEvents.filter { item ->
            // 1. Time Range Filter
            val calendar = Calendar.getInstance().apply { timeInMillis = item.timestamp }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val timeFloat = hour + (minute / 60f)
            val inTimeRange = timeFloat >= filter.timeRange.start && timeFloat <= filter.timeRange.endInclusive
            
            // 2. Category Filter (If any selected)
            // Note: Currently we default to Neutral. Need real categories to test this effectively.
            // For now, if "Productive" selected -> show Deep Work?
            val inCategory = if (filter.selectedCategories.isEmpty()) true else {
                item.event.app.type in filter.selectedCategories
            }
            
            // 3. Specific App Filter (If any selected)
            val inAppList = if (filter.selectedApps.isEmpty()) true else {
                item.packageName in filter.selectedApps || item.packageName == "Deep Work" // Keep logic simple
            }
            
            // 4. Search Filter
            val matchesSearch = if (filter.searchQuery.isEmpty()) true else {
                item.event.app.name.contains(filter.searchQuery, ignoreCase = true)
            }
            
            inTimeRange && inCategory && inAppList && matchesSearch
        }
        .sortedWith(compareBy<TimedEvent> { it.timestamp }.thenBy { it.priority })
        .map { it.event }
    }

    // --- Filter Actions ---
    fun updateTimeRange(range: ClosedFloatingPointRange<Float>) {
        _filterState.value = _filterState.value.copy(timeRange = range)
    }

    fun toggleCategory(category: ActivityType) {
        val current = _filterState.value.selectedCategories
        val newSet = if (category in current) current - category else current + category
        _filterState.value = _filterState.value.copy(selectedCategories = newSet)
    }

    fun toggleApp(packageName: String) {
        val current = _filterState.value.selectedApps
        val newSet = if (packageName in current) current - packageName else current + packageName
        _filterState.value = _filterState.value.copy(selectedApps = newSet)
    }

    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun clearFilters() {
        _filterState.value = FilterState()
    }
    
    // Note: "Apply" is implicit since we use StateFlow, but if we want manual Apply button behavior,
    // we would need a separate "draft" state vs "applied" state.
    // The UI has "Apply" button. This implies we should hold changes in UI state and only commit here when "Apply" is clicked?
    // OR we commit to ViewModel but have a separate "activeFilter" vs "editingFilter".
    // For simplicity with the provided UI code (which had local state), let's assume UI manages draft state 
    // and calls these functions on "Apply".
    // The previous UI code I wrote had `var timeRange by remember...`. So UI handles draft.
    // VM just needs `applyFilter(newState)`?
    // Or individual setters and assume immediate update?
    // Given the "Apply" button, I will refactor UI to pass the final state to VM.
    
    fun applyFilters(
        timeRange: ClosedFloatingPointRange<Float>, 
        categories: Set<ActivityType>, 
        apps: Set<String>, 
        query: String
    ) {
        _filterState.value = FilterState(timeRange, categories, apps, query)
    }

    private fun mergeAdjacentDeepWork(events: List<TimelineEvent>): List<TimelineEvent> {
        if (events.isEmpty()) return events
        
        val merged = mutableListOf<TimelineEvent>()
        var current = events[0]
        
        for (i in 1 until events.size) {
            val next = events[i]
            
            if (current.isDeepWork && next.isDeepWork) {
                val newRange = "${current.range.substringBefore(" - ")} - ${next.range.substringAfter(" - ")}"
                
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
    private data class TimedEvent(
        val timestamp: Long, 
        val event: TimelineEvent, 
        val priority: Int = 1,
        val isOffline: Boolean = false,
        val packageName: String = "",
        val endTime: Long = 0L
    )

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
