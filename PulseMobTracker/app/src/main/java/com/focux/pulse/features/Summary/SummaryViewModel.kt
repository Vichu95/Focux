package com.focux.pulse.features.Summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focux.pulse.data_manager.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Summary screen.
 * Fetches DailyStats from database and converts to UI models.
 */
class SummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val database = PulseDatabase.getDatabase(application)
    private val analyticsDao = database.analyticsDao()

    private val _dailyStats = MutableStateFlow<DailyStats?>(null)
    val dailyStats: StateFlow<DailyStats?> = _dailyStats

    private val _phoneActivity = MutableStateFlow<PhoneActivityData?>(null)
    val phoneActivity: StateFlow<PhoneActivityData?> = _phoneActivity

    private val _deviceAccess = MutableStateFlow<DeviceAccessData?>(null)
    val deviceAccess: StateFlow<DeviceAccessData?> = _deviceAccess

    private val _offlineStreak = MutableStateFlow<OfflineStreakData?>(null)
    val offlineStreak: StateFlow<OfflineStreakData?> = _offlineStreak

    private val _firstLastApps = MutableStateFlow<FirstLastAppsData?>(null)
    val firstLastApps: StateFlow<FirstLastAppsData?> = _firstLastApps

    fun loadDataForDate(dateStr: String) {
        viewModelScope.launch {
            val stats = analyticsDao.getDailyStats(dateStr)
            _dailyStats.value = stats

            if (stats != null) {
                // Convert DailyStats to UI models
                _phoneActivity.value = PhoneActivityData(
                    totalTime = formatDuration(stats.totalScreenTime),
                    productiveTime = formatDuration(stats.productiveTime),
                    neutralTime = formatDuration(stats.neutralTime),
                    distractingTime = formatDuration(stats.distractingTime),
                    topApps = buildTopAppsList(stats)
                )

                _deviceAccess.value = DeviceAccessData(
                    unlocks = stats.unlockNoAppCount + stats.unlockAppCount,
                    glances = stats.screenCheckCount
                )

                _offlineStreak.value = if (stats.offlineStreakDuration > 0) {
                    OfflineStreakData(
                        duration = formatDuration(stats.offlineStreakDuration),
                        startTime = formatTime(stats.offlineStreakStart),
                        endTime = formatTime(stats.offlineStreakEnd)
                    )
                } else {
                    OfflineStreakData("0m", "--:--", "--:--")
                }

                _firstLastApps.value = FirstLastAppsData(
                    morningTime = if (stats.firstAppStartTime > 0) formatTime(stats.firstAppStartTime) else "--:--",
                    morningApp = AppUsage(
                        name = extractAppName(stats.firstAppPackage),
                        iconName = "app_icon",
                        duration = "",
                        type = ActivityType.Neutral
                    ),
                    nightTime = if (stats.lastAppStartTime > 0) formatTime(stats.lastAppStartTime) else "--:--",
                    nightApp = AppUsage(
                        name = extractAppName(stats.lastAppPackage),
                        iconName = "app_icon",
                        duration = "",
                        type = ActivityType.Neutral
                    )
                )
            } else {
                // No data for this date
                _phoneActivity.value = PhoneActivityData("0h 0m", "0h 0m", "0h 0m", "0h 0m", emptyList())
                _deviceAccess.value = DeviceAccessData(0, 0)
                _offlineStreak.value = OfflineStreakData("0m", "--:--", "--:--")
                _firstLastApps.value = FirstLastAppsData(
                    "--:--",
                    AppUsage("No data", "app_icon", "", null),
                    "--:--",
                    AppUsage("No data", "app_icon", "", null)
                )
            }
        }
    }

    private fun buildTopAppsList(stats: DailyStats): List<AppUsage> {
        val apps = mutableListOf<AppUsage>()
        if (stats.topApp1Package != null) {
            apps.add(AppUsage(
                name = extractAppName(stats.topApp1Package),
                iconName = "app_icon",
                duration = formatDuration(stats.topApp1Duration),
                type = ActivityType.Neutral
            ))
        }
        if (stats.topApp2Package != null) {
            apps.add(AppUsage(
                name = extractAppName(stats.topApp2Package),
                iconName = "app_icon",
                duration = formatDuration(stats.topApp2Duration),
                type = ActivityType.Neutral
            ))
        }
        if (stats.topApp3Package != null) {
            apps.add(AppUsage(
                name = extractAppName(stats.topApp3Package),
                iconName = "app_icon",
                duration = formatDuration(stats.topApp3Duration),
                type = ActivityType.Neutral
            ))
        }
        return apps
    }

    private fun extractAppName(packageName: String?): String {
        if (packageName == null) return "Unknown"
        // Extract last part of package name as app name
        val parts = packageName.split(".")
        return parts.lastOrNull()?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    }

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
