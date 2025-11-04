package com.focux.focux.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LogEventDao {
    @Insert
    suspend fun insert(logEvent: LogEvent)

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC")
    suspend fun getAll(): List<LogEvent>

    @Query("DELETE FROM log_events")
    suspend fun clear()
}
