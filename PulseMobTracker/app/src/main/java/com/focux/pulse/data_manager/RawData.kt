package com.focux.pulse.data_manager

import android.app.usage.UsageEvents
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "raw_data")
data class RawData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val eventType: Int,
    val packageName: String?,
    val eventLabel: String
)

object PulseEvents {
    // Human readable tags
    const val APP_OPEN = "APP_OPEN"
    const val APP_CLOSE = "APP_CLOSE"
    const val UNLOCK = "UNLOCK"
    const val LOCK = "LOCK"
    const val SCREEN_ON = "SCREEN_ON"
    const val SCREEN_OFF = "SCREEN_OFF"
    const val UNKNOWN = "UNKNOWN"

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

