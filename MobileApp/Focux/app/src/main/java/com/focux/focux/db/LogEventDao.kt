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

    @Query("SELECT * FROM log_events ORDER BY timestamp DESC LIMIT 10")
    suspend fun getRecentTen(): List<LogEvent>

    @Query("SELECT COUNT(*) FROM log_events WHERE eventType = 'SCREEN_EVENT' AND eventAction = :action AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun countScreenEventsToday(action: String): Int

    @Query("SELECT SUM(eventValue) FROM log_events WHERE eventType = 'APP_USAGE' AND eventAction = :packageName AND date(timestamp / 1000, 'unixepoch') = date('now')")
    suspend fun getUsageToday(packageName: String): Long?

    @Query("SELECT AVG(daily_total) FROM (SELECT SUM(eventValue) as daily_total FROM log_events WHERE eventType = 'APP_USAGE' AND eventAction = :packageName GROUP BY date(timestamp / 1000, 'unixepoch'))")
    suspend fun getAverageUsage(packageName: String): Double?

    @Query("DELETE FROM log_events")
    suspend fun clear()
}
