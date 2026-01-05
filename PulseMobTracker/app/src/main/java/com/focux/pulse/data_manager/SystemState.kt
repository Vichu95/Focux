package com.focux.pulse.data_manager

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores key-value pairs for internal app state persistence.
 * Primarily used to track the 'Cursor' of our data processing pipeline (e.g., the ID of the last processed RawData event).
 * This allows the processor to resume efficiently without re-scanning the entire history.
 *
 * @param key The unique identifier for the state item (e.g., "last_processed_raw_id").
 * @param value The value associated with the key.
 */
@Entity(tableName = "system_state")
data class SystemState(
    @PrimaryKey val key: String,
    val value: String
)
