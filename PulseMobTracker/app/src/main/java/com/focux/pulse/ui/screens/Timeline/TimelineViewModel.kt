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
        val searchQuery: String = "",
        val isTimeFilterActive: Boolean = false,
        val activePreset: String? = null // e.g. "Whole Day", "Till 12pm", "From 6pm"
    )
    
    // Master list of events for the day (unfiltered)
    private val _allDayEvents = MutableStateFlow<List<TimedEvent>>(emptyList())
    
    // Available apps for the filter list
    private val _availableApps = MutableStateFlow<List<String>>(emptyList()) // Package names
    val availableApps: StateFlow<List<String>> = _availableApps

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState
    


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
            // Strategy: Use DailyStats if available (Morning/Sleep facts).
            // Fallback: If missing (e.g. today live), derive from actual sessions range.
            
            // Calculate Start Hour (Wake Up / Day Start)
            // User Requirement: Strict adherence to DailyStats.sleepTimeEnd
            var derivedStartTs = if (dailyStats != null && dailyStats.sleepTimeEnd > 0) 
                dailyStats.sleepTimeEnd 
            else 0L
            
            // Fallback for Start (Only if DailyStats is missing)
            if (derivedStartTs == 0L && sessionEvents.isNotEmpty()) {
                derivedStartTs = sessionEvents.minOf { it.timestamp }
            }
            
            val startHour = getFloatTime(derivedStartTs)
            
            // Calculate End Hour (End of Timeline)
            // User Requirement: Use LATEST of "Sleep Start" (Bedtime) or "Last App Used".
            // This handles cases where one might be later than the other (e.g. ignored info vs calculated sleep).
            
            var derivedEndTs = 0L
            if (dailyStats != null) {
                derivedEndTs = maxOf(dailyStats.sleepTimeStart, dailyStats.lastAppEndTime)
            }
            
            // Fallback (Only if DailyStats is missing or 0)
            if (derivedEndTs == 0L) {
                 // Use max known activity from raw sessions
                 val sessionLast = if (sessionEvents.isNotEmpty()) sessionEvents.maxOf { it.endTime } else 0L
                 derivedEndTs = sessionLast
            }
            
            // Safety: Ensure End > Start (basic sanity check only)
             if (derivedEndTs > 0 && derivedEndTs < derivedStartTs) {
                 // Even if DB says so, this is physically impossible for a single day view.
                 // Fallback to max session time to prevent crash/weirdness.
                 if (sessionEvents.isNotEmpty()) derivedEndTs = sessionEvents.maxOf { it.endTime }
             }

            val rawEndHour = getFloatTime(derivedEndTs)
            // Handle Midnight Crossing:
            // If rawEndHour (e.g. 1.33) < startHour (e.g. 8.5) and derivedEndTs > derivedStartTs,
            // it means we crossed midnight. We represent 01:20 as 25.33f.
            val endHour = if (derivedEndTs > 0) {
                 if (rawEndHour < startHour) rawEndHour + 24f else rawEndHour
            } else 24f
            


            _dayBounds.value = startHour..endHour
            
            // Note: We used to clamp _filterState here. 
            // Removed to allow filter preference (e.g. 7-9) to persist across days with different start times.
            
            // IF the filter is NOT actively set by the user, 
            // we should default it to the actual Day Window (Start..End) to show relevant data only.
            // OR if a SEMANTIC PRESET is active (e.g. "Till 12pm"), we must re-calculate for the new day.
            if (!_filterState.value.isTimeFilterActive) {
                 _filterState.value = _filterState.value.copy(timeRange = startHour..endHour)
            } else if (_filterState.value.activePreset != null) {
                // Re-apply preset logic for the NEW day
                val preset = _filterState.value.activePreset
                val newRange = when (preset) {
                    "Whole Day" -> startHour..endHour
                    "Till 12pm" -> startHour..12f
                    "12pm to 6pm" -> 12f..18f
                    "From 6pm" -> 18f..endHour
                    else -> _filterState.value.timeRange
                }
                
                // Ensure valid bounds
                val clampedStart = maxOf(startHour, newRange.start)
                val clampedEnd = minOf(endHour, newRange.endInclusive)
                
                if (clampedStart <= clampedEnd) {
                     _filterState.value = _filterState.value.copy(timeRange = clampedStart..clampedEnd)
                }
            }
            // If user has a custom range (isTimeFilterActive = true, activePreset = null), we leave it alone.
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
            val rawTimeFloat = hour + (minute / 60f)
            
            // Handle Midnight Crossing Logic relative to Day Start
            // If the day starts at 8AM, and we have an event at 1AM, it's 25.0 (Next Day).
            val dayStart = _dayBounds.value.start
            
            // Only apply +24 logic if dayStart is substantially late (e.g. > 4AM) 
            // to avoid issues if dayStart is 00:00.
            // Actually, simply: if time < dayStart, it must be the next day (assuming sessions are chronological and belong to this "Logical Day").
            // Note: allEvents are already filtered to belong to this "Logical Day" by DailySummaryProcessor logic?
            // DailySummaryProcessor filters by timestamp >= dayMetricStart.
            // So if dayMetricStart is Yesterday 23:00, then Today 01:00 is definitely > start.
            // But converted to float: 23.0 vs 1.0. 
            
            val effectiveTimeFloat = if (rawTimeFloat < dayStart) rawTimeFloat + 24f else rawTimeFloat
            
            val inTimeRange = effectiveTimeFloat >= filter.timeRange.start && effectiveTimeFloat <= filter.timeRange.endInclusive
            
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
        // Reset everything, including the active flag.
        // Also reset range to current day bounds immediately for better UX.
        _filterState.value = FilterState(
            timeRange = _dayBounds.value,
            selectedCategories = emptySet(),
            selectedApps = emptySet(),
            searchQuery = "",
            isTimeFilterActive = false,
            activePreset = null
        )
    }
    
    fun applyFilters(
        timeRange: ClosedFloatingPointRange<Float>, 
        categories: Set<ActivityType>, 
        apps: Set<String>, 
        query: String,
        preset: String?
    ) {
        // User explicitly applied filters, so we mark time filter as active
        _filterState.value = FilterState(
            timeRange = timeRange, 
            selectedCategories = categories, 
            selectedApps = apps, 
            searchQuery = query, 
            isTimeFilterActive = true,
            activePreset = preset
        )
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
