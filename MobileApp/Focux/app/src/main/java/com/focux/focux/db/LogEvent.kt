package com.focux.focux.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single structured log event in the database.
 */
@Entity(tableName = "log_events")
data class LogEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String, // e.g., SCREEN_EVENT, ACCESSIBILITY_EVENT, APP_USAGE
    val packageName: String? = null, // e.g., com.whatsapp
    val eventAction: String = "UNKNOWN", // e.g., SCREEN_OFF, TYPE_VIEW_CLICKED
    val eventValue: String? = null // e.g., for usage duration
)
