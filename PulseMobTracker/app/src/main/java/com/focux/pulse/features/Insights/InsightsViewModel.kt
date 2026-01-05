package com.focux.pulse.features.Insights

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focux.pulse.data_manager.*
import com.focux.pulse.utils.AppInfoHelper
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

    init {
        loadWeeklyData()
    }

    fun loadWeeklyData() {
        viewModelScope.launch {
            val stats = analyticsDao.getLastNDaysStats(7)
            
            if (stats.isNotEmpty()) {
                // Weekly Trend (bar chart data)
                _weeklyTrend.value = stats.reversed().map { stat ->
                    val hours = stat.totalScreenTime.toFloat() / (1000 * 60 * 60)
                    WeeklyTrendItem(
                        day = getDayName(stat.date),
                        hours = hours,
                        isSelected = stat.date == stats.first().date,
                        durationText = formatDuration(stat.totalScreenTime)
                    )
                }

                // Weekly Activity (totals)
                val totalTime = stats.sumOf { it.totalScreenTime }
                val productiveTime = stats.sumOf { it.productiveTime }
                val neutralTime = stats.sumOf { it.neutralTime }
                val distractingTime = stats.sumOf { it.distractingTime }

                _weeklyActivity.value = WeeklyActivityData(
                    totalTime = formatDuration(totalTime),
                    productive = formatDuration(productiveTime),
                    neutral = formatDuration(neutralTime),
                    distracting = formatDuration(distractingTime)
                )

                // Top Apps (aggregate from all days)
                val appTotals = mutableMapOf<String, Long>()
                stats.forEach { stat ->
                    stat.topApp1Package?.let { 
                        appTotals[it] = (appTotals[it] ?: 0) + stat.topApp1Duration 
                    }
                    stat.topApp2Package?.let { 
                        appTotals[it] = (appTotals[it] ?: 0) + stat.topApp2Duration 
                    }
                    stat.topApp3Package?.let { 
                        appTotals[it] = (appTotals[it] ?: 0) + stat.topApp3Duration 
                    }
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

                // Session Length (average)
                val avgScreenTime = totalTime / stats.size
                _sessionLength.value = SessionLengthData(
                    overall = formatDuration(avgScreenTime),
                    productive = formatDuration(productiveTime / stats.size),
                    distracting = formatDuration(distractingTime / stats.size)
                )
            } else {
                // No data
                _weeklyTrend.value = emptyList()
                _weeklyActivity.value = WeeklyActivityData("0h", "0h", "0h", "0h")
                _topApps.value = emptyList()
                _sessionLength.value = SessionLengthData("0h", "0h", "0h")
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
