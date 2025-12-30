package com.focux.pulse.data

// Base Types
enum class ActivityType { Productive, Neutral, Distracting }

// Shared/Common
data class AppUsage(
    val name: String,
    val iconName: String, // Matches file name without .svg if using R.drawable logic, but here we might just map it manually
    val duration: String,
    val type: ActivityType? = null
)

// Feature: Summary
data class PhoneActivityData(
    val totalTime: String,
    val productiveTime: String,
    val neutralTime: String,
    val distractingTime: String,
    val topApps: List<AppUsage>
)

data class DeviceAccessData(
    val unlocks: Int,
    val glances: Int
)

data class OfflineStreakData(
    val duration: String,
    val startTime: String,
    val endTime: String
)

data class FirstLastAppsData(
    val morningTime: String,
    val morningApp: AppUsage,
    val nightTime: String,
    val nightApp: AppUsage
)

// Feature: Timeline
data class TimelineEvent(
    val time: String,
    val app: AppUsage,
    val range: String,
    val isDeepWork: Boolean = false,
    val deepWorkDuration: String? = null
)

// Feature: Insights
data class WeeklyTrendItem(
    val day: String,
    val hours: Float,
    val isSelected: Boolean = false,
    val durationText: String
)

data class WeeklyActivityData(
    val totalTime: String,
    val productive: String,
    val neutral: String,
    val distracting: String
)

data class RoutineData(
    val morningHabit: Pair<AppUsage, Int>, // App + Count
    val nightHabit: Pair<AppUsage, Int>
)

data class SessionLengthData(
    val overall: String,
    val productive: String,
    val distracting: String
)

data class TopAppItem(
    val rank: Int,
    val app: AppUsage,
    val type: ActivityType,
    val duration: String
)

// --- DUMMY DATA ---

val dummyPhoneActivity = PhoneActivityData(
    totalTime = "5h 12m",
    productiveTime = "3h 24m",
    neutralTime = "3h 24m",
    distractingTime = "3h 24m",
    topApps = listOf(
        AppUsage("Instagram", "instagram_logo", "5h 12m", ActivityType.Distracting),
        AppUsage("Maps", "maps_logo", "5h 12m", ActivityType.Productive),
        AppUsage("Gmail", "gmail_logo", "5h 12m", ActivityType.Productive)
    )
)

val dummyDeviceAccess = DeviceAccessData(
    unlocks = 43,
    glances = 15
)

val dummyOfflineStreak = OfflineStreakData(
    duration = "5h 12m",
    startTime = "14:00",
    endTime = "16:40"
)

val dummyFirstLastApps = FirstLastAppsData(
    morningTime = "06:00",
    morningApp = AppUsage("Instagram", "instagram_logo", "", ActivityType.Distracting),
    nightTime = "23:15",
    nightApp = AppUsage("Gmail", "gmail_logo", "", ActivityType.Productive)
)

val dummyTimelineEvents = listOf(
    TimelineEvent(
        time = "06:00",
        app = AppUsage("Morning", "sun_icon", "", null),
        range = "",
        isDeepWork = false
    ),
    TimelineEvent(
        time = "14:02",
        app = AppUsage("Instagram", "instagram_logo", "14:02 - 14:16", ActivityType.Distracting),
        range = "14:02 - 14:16",
        isDeepWork = false
    ),
    TimelineEvent(
        time = "",
        app = AppUsage("Deep Work", "deep_work_icon", "45m", null),
        range = "",
        isDeepWork = true,
        deepWorkDuration = "45m"
    ),
    TimelineEvent(
        time = "15:01",
        app = AppUsage("Gmail", "gmail_logo", "15:01 - 15:05", ActivityType.Productive),
        range = "15:01 - 15:05",
        isDeepWork = false
    ),
    TimelineEvent(
        time = "22:00",
        app = AppUsage("Night", "moon_icon", "", null),
        range = "",
        isDeepWork = false
    )
)

val dummyWeeklyTrend = listOf(
    WeeklyTrendItem("Mon", 2.8f, false, "2h 48m"),
    WeeklyTrendItem("Tue", 21.8f, true, "21h 48m"), // Selected (Max)
    WeeklyTrendItem("Wed", 2.36f, false, "2h 22m"), // Low
    WeeklyTrendItem("Thu", 6.8f, false, "6h 48m"),
    WeeklyTrendItem("Fri", 4.8f, false, "4h 48m"),
    WeeklyTrendItem("Sat", 0.5f, false, "30m"), // Smallest
    WeeklyTrendItem("Sun", 3.8f, false, "3h 48m")
)

val dummyWeeklyActivity = WeeklyActivityData(
    totalTime = "35h 12m",
    productive = "8h 24m",
    neutral = "23h 24m",
    distracting = "3h 24m"
)

val dummyRoutine = RoutineData(
    morningHabit = Pair(AppUsage("Instagram New App Testing Mode", "instagram_logo", "", null), 5),
    nightHabit = Pair(AppUsage("Gmail", "gmail_logo", "", null), 4)
)

val dummyTopApps = listOf(
    TopAppItem(1, AppUsage("Instagram", "instagram_logo", "", null), ActivityType.Distracting, "5h 20m"),
    TopAppItem(2, AppUsage("Gmail", "gmail_logo", "", null), ActivityType.Productive, "2h 10m"),
    TopAppItem(3, AppUsage("Maps", "maps_logo", "", null), ActivityType.Productive, "16m")
)

val dummySessionLength = SessionLengthData(
    overall = "25min",
    productive = "15min",
    distracting = "38min"
)
