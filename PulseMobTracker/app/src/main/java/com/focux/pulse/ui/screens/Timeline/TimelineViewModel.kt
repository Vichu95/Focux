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
            
            // Filter to only APP sessions and convert to TimelineEvent
            val events = sessions
                .filter { it.type == PulseEvents.SESSION_APP }
                .sortedBy { it.startTime }
                .map { session -> 
                    TimelineEvent(
                        time = formatTime(session.startTime),
                        app = AppUsage(
                            name = AppInfoHelper.getAppName(context, session.packageName),
                            iconName = session.packageName,
                            duration = formatDuration(session.duration),
                            type = ActivityType.Neutral  // TODO: Get from AppInfo
                        ),
                        range = "${formatTime(session.startTime)} - ${formatTime(session.endTime)}",
                        isDeepWork = session.duration >= 30 * 60 * 1000,  // 30+ min = deep work
                        deepWorkDuration = if (session.duration >= 30 * 60 * 1000) 
                            formatDuration(session.duration) else null
                    )
                }
            
            _timelineEvents.value = events
        }
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
