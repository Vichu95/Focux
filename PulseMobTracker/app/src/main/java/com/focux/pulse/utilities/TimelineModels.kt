package com.focux.pulse.utilities

enum class ActivityType {
    Productive, Distracting, Neutral, Ignored
}

data class AppUsage(
    val name: String,
    val iconName: String, // Package Name for icon loading
    val duration: String,
    val type: ActivityType?
)

data class TimelineEvent(
    val time: String,
    val app: AppUsage,
    val range: String,
    val isDeepWork: Boolean,
    val deepWorkDuration: String? = null
)
