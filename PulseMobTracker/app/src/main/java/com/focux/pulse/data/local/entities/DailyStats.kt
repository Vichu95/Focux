package com.focux.pulse.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aggregated statistics for a single day.
 * Updated incrementally as new sessions are processed.
 *
 * @param date The date key in "YYYY-MM-DD" format.
 * @param totalScreenTime Total time (in ms) screen was on/apps used.
 * @param unlockNoAppCount Number of unlocks without app usage.
 * @param unlockAppCount Number of unlocks with app usage.
 * @param screenCheckCount Sum of glances + unlockNoApp (quick phone checks).
 * @param productiveTime Time spent in PRODUCTIVE apps.
 * @param neutralTime Time spent in NEUTRAL apps.
 * @param distractingTime Time spent in DISTRACTING apps.
 * @param focusScore Calculated score (0-100).
 * @param firstAppPackage First app opened after waking.
 * @param firstAppStartTime When first app session started.
 * @param firstAppEndTime When first app session ended.
 * @param lastAppPackage Last app used before sleep.
 * @param lastAppStartTime When last app session started.
 * @param lastAppEndTime When last app session ended.
 * @param offlineStreakDuration Longest non-sleep gap (ms).
 * @param offlineStreakStart Start of offline streak.
 * @param offlineStreakEnd End of offline streak.
 * @param topApp1Package Top used app #1.
 * @param topApp1Duration Duration of top app #1 (ms).
 * @param topApp2Package Top used app #2.
 * @param topApp2Duration Duration of top app #2 (ms).
 * @param topApp3Package Top used app #3.
 * @param topApp3Duration Duration of top app #3 (ms).
 */
@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey val date: String,
    val totalScreenTime: Long = 0,
    val unlockNoAppCount: Int = 0,
    val unlockAppCount: Int = 0,
    val glanceCount: Int = 0,       // Strictly SESSION_GLANCE
    val screenCheckCount: Int = 0,  // glances + unlockNoApp
    val productiveTime: Long = 0,
    val neutralTime: Long = 0,
    val distractingTime: Long = 0,
    val focusScore: Int = 0,
    // First app after wake
    val firstAppPackage: String? = null,
    val firstAppStartTime: Long = 0,
    val firstAppEndTime: Long = 0,
    // Last app before sleep
    val lastAppPackage: String? = null,
    val lastAppStartTime: Long = 0,
    val lastAppEndTime: Long = 0,
    // Offline streak (excluding sleep)
    val offlineStreakDuration: Long = 0,
    val offlineStreakStart: Long = 0,
    val offlineStreakEnd: Long = 0,
    // Top 3 apps
    val topApp1Package: String? = null,
    val topApp1Duration: Long = 0,
    val topApp2Package: String? = null,
    val topApp2Duration: Long = 0,
    val topApp3Package: String? = null,
    val topApp3Duration: Long = 0,
    
    // Sleep Schedule (New Columns)
    val sleepTimeStart: Long = 0, // Bedtime (End of last session)
    val sleepTimeEnd: Long = 0,   // Wakeup (Start of first session)
    
    // Debug/Readable Sleep Times
    val sleepReadableStart: String = "",
    val sleepReadableEnd: String = ""
)
