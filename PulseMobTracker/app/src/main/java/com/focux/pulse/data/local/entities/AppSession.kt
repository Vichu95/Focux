package com.focux.pulse.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Represents a processed "Session" of activity.
 * Unlike RawData (which are point-in-time events), an AppSession has a duration.
 * It is the result of processing a stream of raw events.
 *
 * @param id Unique ID for the session (auto-generated).
 * @param packageName The package name of the app used, or "system" for general checks.
 * @param startTime The timestamp when the session started (in ms).
 * @param endTime The timestamp when the session ended (in ms).
 * @param duration The duration of the session in milliseconds (endTime - startTime).
 * @param type The type of session: "APP", "SCREEN_CHECK", "UNLOCK".
 * @param date The date string (YYYY-MM-DD) for fast daily grouping and querying.
 */
@Entity(
    tableName = "app_sessions",
    indices = [
        Index(value = ["startTime"]), 
        Index(value = ["date"]),
        Index(value = ["packageName", "startTime", "endTime", "date", "type"], unique = true)
    ]
)
data class AppSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val type: String,
    val date: String,
    val startTimeStr: String = "",
    val endTimeStr: String = "",
    val categoryOverride: String? = null
)
