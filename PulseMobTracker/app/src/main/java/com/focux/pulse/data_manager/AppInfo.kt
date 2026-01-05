package com.focux.pulse.data_manager

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores app metadata and user-defined category.
 * Apps are auto-discovered during processing from APP sessions.
 * Categories: PRODUCTIVE, NEUTRAL, DISTRACTING
 * 
 * @param packageName Unique package identifier (e.g., "com.instagram.android")
 * @param appName Human-readable name (e.g., "Instagram") - saved for uninstalled apps
 * @param category User-assigned category for time tracking
 */
@Entity(tableName = "app_info")
data class AppInfo(
    @PrimaryKey val packageName: String,
    val appName: String = "",  // Resolved app name from PackageManager
    val category: String = "NEUTRAL"  // PRODUCTIVE, NEUTRAL, DISTRACTING
)

/**
 * App category constants for type safety.
 */
object AppCategory {
    const val PRODUCTIVE = "PRODUCTIVE"
    const val NEUTRAL = "NEUTRAL"
    const val DISTRACTING = "DISTRACTING"
}
