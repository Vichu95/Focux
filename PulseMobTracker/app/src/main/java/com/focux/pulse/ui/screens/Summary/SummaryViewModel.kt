package com.focux.pulse.ui.screens.Summary

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.focux.pulse.data.local.entities.*
import com.focux.pulse.data.local.PulseDatabase
import com.focux.pulse.utilities.*
import com.focux.pulse.utilities.AppInfoHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel for Summary screen.
 * Fetches DailyStats from database and converts to UI models.
 */
class SummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val database = PulseDatabase.getDatabase(application)
    private val analyticsDao = database.analyticsDao()
    private val appInfoDao = database.appInfoDao()

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

    // Earliest date with data available
    private val _earliestDate = MutableStateFlow<String?>(null)
    val earliestDate: StateFlow<String?> = _earliestDate

    init {
        // Load earliest date on init
        viewModelScope.launch {
            val earliest = analyticsDao.getEarliestDate()
            _earliestDate.value = earliest
        }
    }

    private var fetchJob: Job? = null

    fun loadDataForDate(dateStr: String) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            combine(
                analyticsDao.getDailyStatsFlow(dateStr),
                appInfoDao.getAllAppsFlow()
            ) { stats, _ ->
                stats
            }.collect { stats ->
                _dailyStats.value = stats

                if (stats != null) {
                    val allApps = appInfoDao.getAllApps()
                    val categoryMap = allApps.associate { it.packageName to it.category }
                    
                    val getType = { pkg: String? ->
                        when (categoryMap[pkg]) {
                            AppCategory.PRODUCTIVE -> ActivityType.Productive
                            AppCategory.DISTRACTING -> ActivityType.Distracting
                            else -> ActivityType.Neutral
                        }
                    }

                    // Convert DailyStats to UI models
                    _phoneActivity.value = PhoneActivityData(
                        totalTime = formatDuration(stats.totalScreenTime),
                        productiveTime = formatDuration(stats.productiveTime),
                        neutralTime = formatDuration(stats.neutralTime),
                        distractingTime = formatDuration(stats.distractingTime),
                        topApps = buildTop3Apps(stats, getType)
                    )

                    _deviceAccess.value = DeviceAccessData(
                        unlocks = stats.unlockAppCount,
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
                            name = getAppName(stats.firstAppPackage),
                            iconName = stats.firstAppPackage ?: "app_icon",
                            duration = "",
                            type = getType(stats.firstAppPackage)
                        ),
                        nightTime = if (stats.lastAppStartTime > 0) formatTime(stats.lastAppStartTime) else "--:--",
                        nightApp = AppUsage(
                            name = getAppName(stats.lastAppPackage),
                            iconName = stats.lastAppPackage ?: "app_icon",
                            duration = "",
                            type = getType(stats.lastAppPackage)
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
    }

    /**
     * Builds the filtered Top 3 list from DailyStats (pre-calculated and filtered in DB).
     */
    private fun buildTop3Apps(stats: DailyStats, getType: (String?) -> ActivityType): List<AppUsage> {
        val apps = mutableListOf<AppUsage>()
        if (stats.topApp1Package != null) {
            apps.add(AppUsage(name = getAppName(stats.topApp1Package), iconName = stats.topApp1Package, duration = formatDuration(stats.topApp1Duration), type = getType(stats.topApp1Package)))
        }
        if (stats.topApp2Package != null) {
            apps.add(AppUsage(name = getAppName(stats.topApp2Package), iconName = stats.topApp2Package, duration = formatDuration(stats.topApp2Duration), type = getType(stats.topApp2Package)))
        }
        if (stats.topApp3Package != null) {
            apps.add(AppUsage(name = getAppName(stats.topApp3Package), iconName = stats.topApp3Package, duration = formatDuration(stats.topApp3Duration), type = getType(stats.topApp3Package)))
        }
        return apps
    }

    private fun getAppName(packageName: String?): String {
        if (packageName == null) return "Unknown"
        return AppInfoHelper.getAppName(context, packageName)
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

