package com.focux.pulse.ui.screens.Insights

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
 * ViewModel for Insights screen.
 * Aggregates last 7 days of DailyStats.
 */
class InsightsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = PulseDatabase.getDatabase(application)
    private val analyticsDao = database.analyticsDao()

    private val _weeklyTrend = MutableStateFlow<List<WeeklyTrendItem>>(emptyList())
    val weeklyTrend: StateFlow<List<WeeklyTrendItem>> = _weeklyTrend

    private val _weeklyActivity = MutableStateFlow<WeeklyActivityData?>(null)
    val weeklyActivity: StateFlow<WeeklyActivityData?> = _weeklyActivity

    private val _topApps = MutableStateFlow<List<TopAppItem>>(emptyList())
    val topApps: StateFlow<List<TopAppItem>> = _topApps

    private val _sessionLength = MutableStateFlow<SessionLengthData?>(null)
    val sessionLength: StateFlow<SessionLengthData?> = _sessionLength

    // Navigation State: Start of the current view week (Always a Monday)
    private val _currentWeekStart = MutableStateFlow<java.time.LocalDate>(
        java.time.LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
    )
    val currentWeekStart: StateFlow<java.time.LocalDate> = _currentWeekStart
    
    // Focus Score State
    private val _weeklyFocusScore = MutableStateFlow<Int>(0)
    val weeklyFocusScore: StateFlow<Int> = _weeklyFocusScore

    // Deep Work State
    private val _deepWorkDuration = MutableStateFlow<String>("0m")
    val deepWorkDuration: StateFlow<String> = _deepWorkDuration
    
    // Routine State
    private val _routine = MutableStateFlow<com.focux.pulse.utilities.RoutineData>(
        com.focux.pulse.utilities.RoutineData(
             com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0,
             com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0
        )
    )
    val routine: StateFlow<com.focux.pulse.utilities.RoutineData> = _routine

    // Navigation Limit
    private val _earliestDate = MutableStateFlow<java.time.LocalDate?>(null)
    val earliestDate: StateFlow<java.time.LocalDate?> = _earliestDate

    init {
        // Fetch earliest date for navigation limits
        viewModelScope.launch {
            val dateStr = analyticsDao.getEarliestDate()
            if (dateStr != null) {
                try {
                     _earliestDate.value = java.time.LocalDate.parse(dateStr)
                } catch (e: Exception) {
                    // Fallback to today if parsing fails
                     _earliestDate.value = java.time.LocalDate.now()
                }
            } else {
                 _earliestDate.value = java.time.LocalDate.now()
            }
        }
        
        // Collect week start changes to reload data
        viewModelScope.launch {
            _currentWeekStart.collect { 
                loadWeeklyData() 
            }
        }
    }
    
    fun nextWeek() {
        _currentWeekStart.value = _currentWeekStart.value.plusWeeks(1)
    }
    
    fun prevWeek() {
        _currentWeekStart.value = _currentWeekStart.value.minusWeeks(1)
    }

    private fun loadWeeklyData() {
        viewModelScope.launch {
            val weekStart = _currentWeekStart.value
            val weekEnd = weekStart.plusDays(6)
            val today = java.time.LocalDate.now()
            
            // Generate full list of dates for the week (Mon-Sun)
            val weekDates = (0..6).map { weekStart.plusDays(it.toLong()) }
            
            // Map [DateString -> DailyStats]
            val weekStatsMap = mutableMapOf<String, DailyStats>()
            
            // Fetch stats for existing days
            // We fetch individual days or use range query. 
            // Range query is better: getLastNDaysStats uses limit, we need strict range.
            // AnalyticsDao needs a range query method ideally, but for now we loop or use existing logic.
            // Existing `getLastNDaysStats(7)` fetches *latest* 7 days. That's wrong for navigation.
            // We need `getStatsForDateRange(start, end)`. 
            // Assuming we don't have it, we iterate (optimization: batch later).
            
            weekDates.forEach { date ->
                val dateStr = date.toString()
                val stats = analyticsDao.getDailyStats(dateStr)
                if (stats != null) {
                    weekStatsMap[dateStr] = stats
                }
            }
            
            // 1. Calculate proper divisor (Days Passed in this week)
            // If week is in past: 7.
            // If week is current: Days up to today (inclusive).
            // If week is future: 0 (or handle as empty).
            
            // 1. Calculate proper divisor (Effective Days)
            // Start counting from: Max(WeekStart, EarliestDate)
            // Stop counting at: Min(WeekEnd, Today)
            
            val earliest = _earliestDate.value ?: weekStart
            val effectiveStart = if (weekStart.isBefore(earliest)) earliest else weekStart
            val effectiveEnd = if (weekEnd.isAfter(today)) today else weekEnd
            
            val daysPassed = if (effectiveStart.isAfter(effectiveEnd)) {
                1 // Should not happen in valid navigation, but safety fallback
            } else {
                 (java.time.temporal.ChronoUnit.DAYS.between(effectiveStart, effectiveEnd).toInt() + 1)
            }.coerceIn(1, 7)
            
            val statsList = weekStatsMap.values.toList()
            
            // Fetch Raw Sessions for the week (for granular calc)
            val weekStartMs = weekStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val weekEndMs = weekEnd.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1 
            val weekSessions = analyticsDao.getSessionsOverlapping(weekStartMs, weekEndMs)

            if (statsList.isNotEmpty()) {
                // weeklyTrend
                _weeklyTrend.value = weekDates.map { date ->
                    val stat = weekStatsMap[date.toString()]
                    val totalMs = stat?.totalScreenTime ?: 0L
                    val hours = totalMs.toFloat() / (1000 * 60 * 60)
                    
                    WeeklyTrendItem(
                        day = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                        hours = hours,
                        isSelected = date.toString() == today.toString(),
                        durationText = formatDuration(totalMs)
                    )
                }

                // Weekly Activity (Totals)
                val totalTime = statsList.sumOf { it.totalScreenTime }
                val productiveTime = statsList.sumOf { it.productiveTime }
                val neutralTime = statsList.sumOf { it.neutralTime }
                val distractingTime = statsList.sumOf { it.distractingTime }

                _weeklyActivity.value = WeeklyActivityData(
                    totalTime = formatDuration(totalTime),
                    productive = formatDuration(productiveTime),
                    neutral = formatDuration(neutralTime),
                    distracting = formatDuration(distractingTime)
                )

                // --- Top Apps (From Raw Sessions within Active Week) ---
                // User Request: "take the first day, its sleep end... till last day, sleep start"
                val firstDayStat = weekStatsMap[weekStart.toString()]
                val lastDayStat = weekStatsMap[if (weekEnd.isAfter(today)) today.toString() else weekEnd.toString()] ?: statsList.lastOrNull()
                
                // Safety: If sleepTimeEnd is 0 (missing), default to start of week range
                val activeWeekStart = firstDayStat?.sleepTimeEnd?.takeIf { it > 0 } 
                    ?: weekStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                
                // Safety: If sleepTimeStart is 0 (not slept yet), default to end of week range
                val activeWeekEnd = lastDayStat?.sleepTimeStart?.takeIf { it > 0 }
                    ?: (weekEnd.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1)

                // In-Memory Filter (Safe)
                val activeSessions = weekSessions.filter { session ->
                    // Session overlaps with [ActiveWeekStart, ActiveWeekEnd]
                    session.startTime <= activeWeekEnd && (session.startTime + session.duration) >= activeWeekStart
                }

                val launchers = AppInfoHelper.getLauncherPackages(context)
                val ignoredPackages = launchers + setOf("com.android.systemui", "com.google.android.inputmethod.latin") 

                val appUsageMap = mutableMapOf<String, Long>()
                activeSessions
                    .filter { it.type == "APP" && !ignoredPackages.contains(it.packageName) }
                    .forEach { session ->
                        val pkg = session.packageName
                        // Clip duration to the active window for precision? 
                        // User said "sum up for each package the app session time".
                        // Use full session duration if it overlaps, or clipped? 
                        // Simplest/Safest: Use full duration of sessions that started in range or overlap.
                        // Standard practice: if session starts in range.
                        appUsageMap[pkg] = (appUsageMap[pkg] ?: 0) + session.duration
                    }

                _topApps.value = appUsageMap.toList()
                    .sortedByDescending { it.second }
                    .take(5)
                    .mapIndexed { index, (pkg, duration) ->
                        TopAppItem(
                            rank = index + 1,
                            app = AppUsage(
                                name = AppInfoHelper.getAppName(context, pkg),
                                iconName = pkg,
                                duration = formatDuration(duration),
                                type = ActivityType.Neutral
                            ),
                            type = ActivityType.Neutral,
                            duration = formatDuration(duration)
                        )
                    }

                // --- Routine (From Daily Summary with Filter) ---
                // User: "daily sumamry already cnsiders things.. just use from daily sumamry"
                // We fetch first/last from DailyStats but exclude ignored apps (Launchers/System)
                val morningApps = statsList.mapNotNull { it.firstAppPackage }
                    .filter { !ignoredPackages.contains(it) }
                val nightApps = statsList.mapNotNull { it.lastAppPackage }
                    .filter { !ignoredPackages.contains(it) }
                
                val morningMode = morningApps.groupingBy { it }.eachCount().maxByOrNull { it.value }
                val nightMode = nightApps.groupingBy { it }.eachCount().maxByOrNull { it.value }

                _routine.value = com.focux.pulse.utilities.RoutineData(
                    morningHabit = if (morningMode != null) com.focux.pulse.utilities.AppUsage(
                        name = AppInfoHelper.getAppName(context, morningMode.key),
                        iconName = morningMode.key, 
                        duration = "",
                        type = ActivityType.Neutral
                    ) to morningMode.value else com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0,
                    
                    nightHabit = if (nightMode != null) com.focux.pulse.utilities.AppUsage(
                         name = AppInfoHelper.getAppName(context, nightMode.key),
                         iconName = nightMode.key,
                         duration = "",
                         type = ActivityType.Neutral
                    ) to nightMode.value else com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0
                )

                // --- Avg Session Length ---
                val appSessions = weekSessions.filter { it.type == "APP" }
                val totalAppCount = appSessions.size
                // Calculate duration from sessions directly for consistency
                val totalSessionDuration = appSessions.sumOf { it.duration }
                
                val avgSessionDuration = if (totalAppCount > 0) totalSessionDuration / totalAppCount else 0L

                _sessionLength.value = SessionLengthData(
                    overall = formatDuration(avgSessionDuration),
                    productive = formatDuration(avgSessionDuration), // Placeholder until categorization
                    distracting = formatDuration(avgSessionDuration) // Placeholder until categorization
                )

                // Re-calculating Focus Score total
                val totalFocus = statsList.sumOf { 
                   if (it.totalScreenTime > 0) 
                       ((it.productiveTime.toFloat() / it.totalScreenTime.toFloat()) * 100).toInt()
                   else 0
                }
                
                _weeklyFocusScore.value = totalFocus / daysPassed
                
                // --- Deep Work ---
                val totalOffline = weekSessions.filter { it.type == "SESSION_OFFLINE" }.sumOf { it.duration }
                // Approximate Sleep (Safe sum)
                val totalSleep = statsList.sumOf { stat ->
                    val sleepDur = stat.sleepTimeEnd - stat.sleepTimeStart
                    if (sleepDur in 1..(14 * 3600 * 1000)) sleepDur else 0
                }
                val realDeepWork = (totalOffline - totalSleep).coerceAtLeast(0)
                val totalActiveWeek = realDeepWork + totalTime
                val deepPercentage = if (totalActiveWeek > 0) ((realDeepWork.toFloat() / totalActiveWeek) * 100).toInt() else 0
                
                _deepWorkDuration.value = "${formatDuration(realDeepWork)} ($deepPercentage%)"
                
            } else {
                // Empty Week
                 _weeklyTrend.value = weekDates.map { date ->
                    WeeklyTrendItem(
                        day = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                        hours = 0f,
                        isSelected = false,
                        durationText = "0m"
                    )
                }
                _weeklyActivity.value = WeeklyActivityData("0h", "0h", "0h", "0h")
                _topApps.value = emptyList()
                _sessionLength.value = SessionLengthData("0h", "0h", "0h")
                _weeklyFocusScore.value = 0
                _deepWorkDuration.value = "0h 0m (0%)"
                _routine.value = com.focux.pulse.utilities.RoutineData(
                     com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0,
                     com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0
                )
            }
        }
    }

    private fun getDayName(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr)
            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            dayFormat.format(date!!).uppercase()
        } catch (e: Exception) {
            dateStr.takeLast(2)
        }
    }

    private fun formatDuration(ms: Long): String {
        val hours = ms / (1000 * 60 * 60)
        val minutes = (ms % (1000 * 60 * 60)) / (1000 * 60)
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
