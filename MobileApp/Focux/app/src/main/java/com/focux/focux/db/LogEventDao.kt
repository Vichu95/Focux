package com.focux.focux.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LogEventDao {
    @Insert
    suspend fun insert(logEvent: LogEvent)

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC")
    suspend fun getAll(): List<LogEvent>

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentTen(): List<LogEvent>

    @Query("SELECT COUNT(*) FROM log_events WHERE eventType = 'SCREEN_EVENT' AND eventAction = :action AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun countScreenEventsToday(action: String): Int

    // Corrected Query: Look for package name in the correct column
    @Query("SELECT MAX(CAST(eventValue AS INTEGER)) FROM log_events WHERE eventType = 'APP_USAGE' AND packageName = :packageName AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun getUsageToday(packageName: String): Long?

    // Corrected Query: Look for package name in the correct column
    @Query("SELECT AVG(daily_max) FROM (SELECT MAX(CAST(eventValue AS INTEGER)) as daily_max FROM log_events WHERE eventType = 'APP_USAGE' AND packageName = :packageName GROUP BY date(timestamp / 1000, 'unixepoch'))")
    suspend fun getAverageUsage(packageName: String): Double?

    @Query("DELETE FROM log_events")
    suspend fun clear()

    @Query("DELETE FROM sqlite_sequence WHERE name='log_events'")
    suspend fun resetPrimaryKey()

    @Transaction
    suspend fun clearAndReset() {
        clear()
        resetPrimaryKey()
    }
}
