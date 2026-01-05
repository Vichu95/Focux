package com.focux.pulse.data_manager

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Aggregated statistics for a single day.
 * This table allows for instant UI loading of daily summaries without needing to sum up thousands of sessions on the fly.
 * It serves as a cache that is updated incrementally as new sessions are processed.
 *
 * @param date The date key in "YYYY-MM-DD" format.
 * @param totalScreenTime Total time (in ms) screen was on/apps used.
 * @param unlockCount Number of times the device was unlocked.
 * @param screenCheckCount Number of "glances" (screen on -> off without unlock).
 * @param productiveTime Time spent in apps marked as productive (future feature, currently usage time).
 * @param distractingTime Time spent in apps marked as distracting.
 * @param focusScore Calculated score representing the user's digital wellness for the day (0-100).
 */
@Entity(tableName = "daily_stats")
data class DailyStats(
    @PrimaryKey val date: String,
    val totalScreenTime: Long = 0,
    val unlockCount: Int = 0,
    val screenCheckCount: Int = 0,
    val productiveTime: Long = 0,
    val distractingTime: Long = 0,
    val focusScore: Int = 0
)
