package com.focux.pulse.utilities

enum class ActivityType {
    Productive, Distracting, Neutral, Ignored
}

data class AppUsage(
    val name: String,
    val iconName: String, // Package Name for icon loading
    val duration: String,
    val type: ActivityType?,
    val sessionId: Long = 0,
    val categoryOverride: String? = null
)

data class TimelineEvent(
    val time: String,
    val app: AppUsage,
    val range: String,
    val isDeepWork: Boolean,
    val deepWorkDuration: String? = null
)
