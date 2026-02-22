package com.focux.pulse.ui.screens.Insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focux.pulse.data.local.entities.*
import com.focux.pulse.data.local.entities.PulseEvents
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.utilities.*
import com.focux.pulse.utilities.AppInfoHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val appInfoDao = database.appInfoDao()

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
        
        // Collect week start, app category changes, and UI refresh triggers to reload data
        viewModelScope.launch {
            combine(
                _currentWeekStart, 
                appInfoDao.getAllAppsFlow(),
                analyticsDao.getStateFlow("force_ui_refresh")
            ) { weekStart, _, _ ->
                weekStart
            }.collect { 
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

            val allApps = appInfoDao.getAllApps()
            val categoryMap = allApps.associate { it.packageName to it.category }
            val getType = { pkg: String? ->
                when (categoryMap[pkg]) {
                    AppCategory.PRODUCTIVE -> ActivityType.Productive
                    AppCategory.DISTRACTING -> ActivityType.Distracting
                    else -> ActivityType.Neutral
                }
            }
            val userIgnoredApps = allApps.filter { it.category == AppCategory.IGNORED }.map { it.packageName }.toSet()

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
                // User Request: "Start of week = the sleep end of the first day"
                // "End of week = latest of sleep start of last app of the last day"

                val firstDayStat = weekStatsMap[weekStart.toString()]
                // Determine the last day we have data for (either today or the end of the week)
                val lastDayDate = if (weekEnd.isAfter(today)) today else weekEnd
                val lastDayStat = weekStatsMap[lastDayDate.toString()]

                // 1. Calculate Start Time (Wake up of first day)
                // If sleepTimeEnd is > 0, use it. Else fall back to start of the week day.
                val activeWeekStart = if (firstDayStat != null && firstDayStat.sleepTimeEnd > 0) {
                    firstDayStat.sleepTimeEnd
                } else {
                    weekStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                }

                // 2. Calculate End Time (Latest of Sleep Start or Last App Usage)
                val activeWeekEnd = if (lastDayStat != null) {
                    val sleepStart = lastDayStat.sleepTimeStart
                    val lastAppEnd = lastDayStat.lastAppEndTime
                    
                    // "latest of sleep start or last app" implies max(sleepStart, lastAppEnd)
                    // But we must handle 0s (missing data).
                    val candidateEnd = maxOf(sleepStart, lastAppEnd)
                    
                    if (candidateEnd > 0) candidateEnd 
                    else lastDayDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                } else {
                    lastDayDate.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                }

                // 3. Fetch Sessions overlapping this range
                // Note: The variable 'weekSessions' fetched earlier might NOT cover the full range if 'activeWeekEnd' 
                // extends slightly beyond the strict week boundary (though unlikely for valid sleep times). 
                // However, 'weekSessions' was fetched with [weekStart, weekEnd + 1 day]. 
                // Since 'activeWeekStart' >= weekStart and 'activeWeekEnd' <= weekEnd+1day (usually), we can reuse 'weekSessions'
                // OR refetch to be perfectly safe if the logic implies dynamic bounds.
                // Refetching is safer to ensure we get exactly what we need.
                val activeSessions = analyticsDao.getSessionsOverlapping(activeWeekStart, activeWeekEnd)

                val launchers = AppInfoHelper.getLauncherPackages(context)
                val ignoredPackages = launchers + setOf("com.android.systemui", "com.google.android.inputmethod.latin") 
                val allIgnored = ignoredPackages + userIgnoredApps

                // SINGLE SOURCE OF TRUTH: All App sessions in range, minus ignored apps.
                val filteredAppSessions = activeSessions.filter { 
                    it.type == PulseEvents.SESSION_APP && !allIgnored.contains(it.packageName) 
                }

                // 1. Top Apps Calculation
                val appUsageMap = mutableMapOf<String, Long>()
                filteredAppSessions.forEach { session ->
                    val pkg = session.packageName
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
                                type = getType(pkg)
                            ),
                            type = getType(pkg),
                            duration = formatDuration(duration)
                        )
                    }

                // ... Routine (Unchanged) ...

                // --- Routine (From Daily Summary with Filter) ---
                // User: "daily sumamry already cnsiders things.. just use from daily sumamry"
                // We fetch first/last from DailyStats but exclude ignored apps (Launchers/System)
                val morningApps = statsList.mapNotNull { it.firstAppPackage }
                    .filter { !allIgnored.contains(it) }
                val nightApps = statsList.mapNotNull { it.lastAppPackage }
                    .filter { !allIgnored.contains(it) }
                
                val morningMode = morningApps.groupingBy { it }.eachCount().maxByOrNull { it.value }
                val nightMode = nightApps.groupingBy { it }.eachCount().maxByOrNull { it.value }

                _routine.value = com.focux.pulse.utilities.RoutineData(
                    morningHabit = if (morningMode != null) com.focux.pulse.utilities.AppUsage(
                        name = AppInfoHelper.getAppName(context, morningMode.key),
                        iconName = morningMode.key, 
                        duration = "",
                        type = getType(morningMode.key)
                    ) to morningMode.value else com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0,
                    
                    nightHabit = if (nightMode != null) com.focux.pulse.utilities.AppUsage(
                         name = AppInfoHelper.getAppName(context, nightMode.key),
                         iconName = nightMode.key,
                         duration = "",
                         type = getType(nightMode.key)
                    ) to nightMode.value else com.focux.pulse.utilities.AppUsage("No Data", "", "", ActivityType.Neutral) to 0
                )

                // --- Avg Session Length ---
                // 2. Average Calculation (Using the SAME filtered list)
                val validAppSessions = filteredAppSessions // Alias for clarity/min-change
                
                val totalAppCount = validAppSessions.size
                val totalSessionDuration = validAppSessions.sumOf { it.duration }
                val avgSessionDuration = if (totalAppCount > 0) totalSessionDuration / totalAppCount else 0L

                val productiveSessions = validAppSessions.filter { getType(it.packageName) == ActivityType.Productive }
                val prodCount = productiveSessions.size
                val prodTotal = productiveSessions.sumOf { it.duration }
                val prodAvg = if (prodCount > 0) prodTotal / prodCount else 0L

                val distractingSessions = validAppSessions.filter { getType(it.packageName) == ActivityType.Distracting }
                val distCount = distractingSessions.size
                val distTotal = distractingSessions.sumOf { it.duration }
                val distAvg = if (distCount > 0) distTotal / distCount else 0L

                _sessionLength.value = SessionLengthData(
                    overall = formatDuration(avgSessionDuration),
                    productive = formatDuration(prodAvg),
                    distracting = formatDuration(distAvg)
                )

                // Re-calculating Focus Score total
                val totalFocus = statsList.sumOf { 
                   if (it.totalScreenTime > 0) 
                       ((it.productiveTime.toFloat() / it.totalScreenTime.toFloat()) * 100).toInt()
                   else 0
                }
                
                _weeklyFocusScore.value = totalFocus / daysPassed
                
                // --- Deep Work & Sleep ---
                val totalOffline = weekSessions.filter { it.type == PulseEvents.SESSION_OFFLINE }.sumOf { it.duration }
                
                // Calculate Total Sleep and Count of days with valid sleep data
                var totalSleep = 0L
                var sleepDaysCount = 0
                
                for (stat in statsList) {
                    val yesterdayDateStr = try {
                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val d = sdf.parse(stat.date)
                        val cal = Calendar.getInstance()
                        cal.time = d!!
                        cal.add(Calendar.DAY_OF_YEAR, -1)
                        sdf.format(cal.time)
                    } catch (e: Exception) { null }
                    
                    val yesterdayStat = if (yesterdayDateStr != null) {
                        weekStatsMap[yesterdayDateStr] ?: analyticsDao.getDailyStats(yesterdayDateStr)
                    } else null
                    
                    // Actual sleep for "Today" is Today's wakeup (sleepTimeEnd) - Yesterday's bedtime (sleepTimeStart)
                    if (yesterdayStat != null && yesterdayStat.sleepTimeStart > 0 && stat.sleepTimeEnd > 0) {
                        val totalSleepWindow = stat.sleepTimeEnd - yesterdayStat.sleepTimeStart
                        // Subtract time user was on phone after bedtime and before wakeup
                        val realSleep = totalSleepWindow - stat.sleepPhoneDuration
                        
                        // Basic validation: Sleep should be between 1 and 16 hours
                        if (realSleep in 1..(16 * 3600 * 1000L)) {
                            totalSleep += realSleep
                            sleepDaysCount++
                        }
                    }
                }

                // Deep Work = Offline - Sleep
                val realDeepWork = (totalOffline - totalSleep).coerceAtLeast(0)
                
                // Average Offline per day
                val avgOffline = if (daysPassed > 0) realDeepWork / daysPassed else 0L
                
                // Percentage of Day Time
                // Total Duration = daysPassed * 24h
                val totalDurationMs = daysPassed * 24 * 3600 * 1000L
                val totalDayTime = (totalDurationMs - totalSleep).coerceAtLeast(1) // Avoid division by zero
                val offlinePercentage = ((realDeepWork.toFloat() / totalDayTime) * 100).toInt().coerceIn(0, 100)
                
                // Average Sleep
                val avgSleep = if (sleepDaysCount > 0) totalSleep / sleepDaysCount else 0L
                
                _deepWorkDuration.value = "You remained ${formatDuration(avgOffline)} ($offlinePercentage%) offline during the day time last week, and slept ${formatDuration(avgSleep)} average hours each day."
                
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
                _deepWorkDuration.value = "No deep work data available for this week."
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
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
