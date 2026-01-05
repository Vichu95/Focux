package com.focux.pulse.data_manager

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores app metadata and user-defined category.
 * Apps are auto-discovered during processing from APP sessions.
 * Categories: PRODUCTIVE, NEUTRAL, DISTRACTING
 */
@Entity(tableName = "app_info")
data class AppInfo(
    @PrimaryKey val packageName: String,
    val appName: String = "",
    val category: String = "NEUTRAL"  // PRODUCTIVE, NEUTRAL, DISTRACTING
)

/**
 * App category enum for type safety in UI code.
 */
object AppCategory {
    const val PRODUCTIVE = "PRODUCTIVE"
    const val NEUTRAL = "NEUTRAL"
    const val DISTRACTING = "DISTRACTING"
}
