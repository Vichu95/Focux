package com.focux.pulse.data_manager

import android.app.usage.UsageEvents
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a single raw system event from UsageStatsManager.
 * This is the lowest level of data stored in the database.
 *
 * @param id Primary key, auto-generated.
 * @param timestamp The time the event occurred (in milliseconds).
 * @param eventType The raw event type ID from UsageEvents.Event.
 * @param packageName The package name of the app associated with the event (can be null for some system events).
 * @param eventLabel A human-readable label for the event type (e.g., "APP_OPEN", "SCREEN_ON").
 */
@Entity(tableName = "raw_data")
data class RawData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val eventType: Int,
    val packageName: String?,
    val eventLabel: String
)

/**
 * Helper object to map Android's raw UsageEvents types to readable internal constants.
 * This abstraction helps separate Android-specific constants from our domain logic.
 */
object PulseEvents {
    // Human readable tags for raw events
    const val APP_OPEN = "APP_OPEN"
    const val APP_CLOSE = "APP_CLOSE"
    const val UNLOCK = "UNLOCK"
    const val LOCK = "LOCK"
    const val SCREEN_ON = "SCREEN_ON"
    const val SCREEN_OFF = "SCREEN_OFF"
    const val UNKNOWN = "UNKNOWN"
    
    // Session types for processed data
    const val SESSION_APP = "SESSION_APP"                     // Normal app usage session
    const val SESSION_GLANCE = "SESSION_GLANCE"               // Screen on, no unlock
    const val SESSION_UNLOCK_NOAPP = "SESSION_UNLOCK_NOAPP"   // Unlocked but no app used
    const val SESSION_UNLOCK_APP = "SESSION_UNLOCK_APP"       // Unlocked and used apps

    /**
     * Maps a UsageEvents.Event type to a PulseEvents constant string.
     */
    fun getLabel(eventType: Int): String {
        return when (eventType) {
            UsageEvents.Event.MOVE_TO_FOREGROUND -> APP_OPEN
            UsageEvents.Event.MOVE_TO_BACKGROUND -> APP_CLOSE
            UsageEvents.Event.KEYGUARD_HIDDEN -> UNLOCK
            UsageEvents.Event.KEYGUARD_SHOWN -> LOCK
            UsageEvents.Event.SCREEN_INTERACTIVE -> SCREEN_ON
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> SCREEN_OFF
            else -> UNKNOWN
        }
    }
}

