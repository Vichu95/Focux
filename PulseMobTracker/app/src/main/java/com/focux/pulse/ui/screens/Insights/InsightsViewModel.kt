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

    init {
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
            
            val daysPassed = when {
                weekEnd.isBefore(today) -> 7
                weekStart.isAfter(today) -> 0
                else -> java.time.temporal.ChronoUnit.DAYS.between(weekStart, today).toInt() + 1
            }.coerceIn(1, 7) // Avoid divide by zero, min 1 for safety
            
            val statsList = weekStatsMap.values.toList()
            
            if (statsList.isNotEmpty()) {
                // weeklyTrend needs to return 7 items (Mon-Sun), filling missing with empty
                _weeklyTrend.value = weekDates.map { date ->
                    val stat = weekStatsMap[date.toString()]
                    val totalMs = stat?.totalScreenTime ?: 0L
                    val hours = totalMs.toFloat() / (1000 * 60 * 60)
                    
                    WeeklyTrendItem(
                        day = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()),
                        hours = hours,
                        isSelected = date.toString() == today.toString(), // Highlight today if in view
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

                // Top Apps (Aggregated)
                val appTotals = mutableMapOf<String, Long>()
                statsList.forEach { stat ->
                    stat.topApp1Package?.let { appTotals[it] = (appTotals[it] ?: 0) + stat.topApp1Duration }
                    stat.topApp2Package?.let { appTotals[it] = (appTotals[it] ?: 0) + stat.topApp2Duration }
                    stat.topApp3Package?.let { appTotals[it] = (appTotals[it] ?: 0) + stat.topApp3Duration }
                }

                _topApps.value = appTotals.toList()
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

                // Session Length & Averages
                // Use 'daysPassed' for divisor
                val avgScreenTime = totalTime / daysPassed
                val avgProductive = productiveTime / daysPassed
                val avgDistracting = distractingTime / daysPassed
                
                _sessionLength.value = SessionLengthData(
                    overall = formatDuration(avgScreenTime),
                    productive = formatDuration(avgProductive),
                    distracting = formatDuration(avgDistracting)
                )

                // Re-calculating Focus Score total
                val totalFocus = statsList.sumOf { 
                   if (it.totalScreenTime > 0) 
                       ((it.productiveTime.toFloat() / it.totalScreenTime.toFloat()) * 100).toInt()
                   else 0
                }
                
                _weeklyFocusScore.value = totalFocus / daysPassed
                
                // Deep Work (Offline Duration)
                // DailyStats lacks offline totals, must query raw sessions for the week range
                val weekStartMs = weekStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val weekEndMs = weekEnd.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1 
                val weekSessions = analyticsDao.getSessionsOverlapping(weekStartMs, weekEndMs)
                val totalOffline = weekSessions.filter { it.type == "SESSION_OFFLINE" }.sumOf { it.duration }
                
                _deepWorkDuration.value = formatDuration(totalOffline)
                
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
                _deepWorkDuration.value = "0h 0m"
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
