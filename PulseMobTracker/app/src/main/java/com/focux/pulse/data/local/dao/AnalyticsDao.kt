package com.focux.pulse.data.local.dao

import androidx.room.*
import com.focux.pulse.data.local.entities.*

/**
 * Data Access Object (DAO) for accessing processed analytics data.
 * Handles reading/writing the SystemState, AppSessions, and DailyStats.
 */
@Dao
interface AnalyticsDao {
    // --- System State ---
    
    /**
     * Retrieves a stored state value by its key.
     * Used often to get the 'last_processed_raw_id'.
     */
    @Query("SELECT value FROM system_state WHERE key = :key")
    suspend fun getState(key: String): String?

    /**
     * Reactive flow for a specific state key. Useful for system-wide refresh triggers.
     */
    @Query("SELECT value FROM system_state WHERE key = :key")
    fun getStateFlow(key: String): kotlinx.coroutines.flow.Flow<String?>

    /**
     * Updates or inserts a state key-value pair.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateState(state: SystemState)

    // --- App Sessions ---

    /**
     * Updates the category override for a specific session.
     */
    @Query("UPDATE app_sessions SET categoryOverride = :category WHERE id = :sessionId")
    suspend fun updateSessionCategoryOverride(sessionId: Long, category: String)

    /**
     * Batch inserts a list of processed sessions.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<AppSession>)

    /**
     * Retrieves all sessions for a specific date, ordered by time (newest first).
     * @param date Format: "YYYY-MM-DD"
     */
    @Query("SELECT * FROM app_sessions WHERE date = :date ORDER BY startTime DESC")
    suspend fun getSessionsForDay(date: String): List<AppSession>

    /**
     * Retrieves all sessions that overlap with the specified time range.
     * Used for Sleep Algorithm to find sessions crossing 'User Sleep Window'.
     * Overlap Condition: (A.Start < B.End) AND (A.End > B.Start)
     */
    @Query("SELECT * FROM app_sessions WHERE startTime < :endTimestamp AND endTime > :startTimestamp ORDER BY startTime ASC")
    suspend fun getSessionsOverlapping(startTimestamp: Long, endTimestamp: Long): List<AppSession>

    /**
     * Retrieves all sessions with ID >= given ID, ordered by Start Time.
     * Used for sequential Offline Gap calculation.
     */
    @Query("SELECT * FROM app_sessions WHERE id >= :startId ORDER BY startTime ASC")
    suspend fun getSessionsStartingFromId(startId: Long): List<AppSession>

    /**
     * Retrieves the very last processed session (ordered by end time).
     * Used to bridge the gap between historical batches and new processing batches.
     */
    @Query("SELECT * FROM app_sessions ORDER BY endTime DESC LIMIT 1")
    suspend fun getLastSession(): AppSession?

    @Query("SELECT id FROM app_sessions ORDER BY id DESC LIMIT 1")
    suspend fun getLastRowId(): Long?

    @Query("SELECT * FROM raw_data WHERE eventLabel IN ('SCREEN_ON', 'SCREEN_OFF') AND timestamp <= :timestamp ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastScreenStateEvent(timestamp: Long): RawData?

    // --- Daily Stats ---

    /**
     * Gets the earliest date with stats data.
     */
    @Query("SELECT MIN(date) FROM daily_stats")
    suspend fun getEarliestDate(): String?

    /**
     * Retrieves all daily stats, ordered mathematically so today is processed first.
     */
    @Query("SELECT * FROM daily_stats ORDER BY date DESC")
    suspend fun getAllDailyStatsDesc(): List<DailyStats>

    /**
     * Retrieves the aggregated stats for a specific day.
     * @param date Format: "YYYY-MM-DD"
     */
    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getDailyStats(date: String): DailyStats?

    @Query("SELECT COALESCE(SUM(duration) / 60000, 0) FROM app_sessions WHERE packageName = :packageName AND date = :date")
    suspend fun getAppUsageMinsForDay(packageName: String, date: String): Int

    @Query("SELECT COUNT(id) FROM app_sessions WHERE packageName = :packageName AND date = :date")
    suspend fun getAppOpensForDay(packageName: String, date: String): Int

    /**
     * Observable Flow version for UI to react to changes automatically.
     */
    @Query("SELECT * FROM daily_stats WHERE date = :date")
    fun getDailyStatsFlow(date: String): kotlinx.coroutines.flow.Flow<DailyStats?>

    /**
     * Retrieves stats for the last N days, ordered by date descending.
     */
    @Query("SELECT * FROM daily_stats ORDER BY date DESC LIMIT :days")
    suspend fun getLastNDaysStats(days: Int): List<DailyStats>

    /**
     * Updates the daily stats row.
     * Since this is often a read-modify-write operation, we use REPLACE.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateDailyStats(stats: DailyStats)

    /**
     * Deletes all sessions. Used for database reset.
     */
    @Query("DELETE FROM app_sessions")
    suspend fun deleteAllSessions()

    /**
     * Deletes all daily stats. Used for database reset.
     */
    @Query("DELETE FROM daily_stats")
    suspend fun deleteAllDailyStats()

    /**
     * Resets the auto-increment counter for app_sessions.
     */
    @Query("DELETE FROM sqlite_sequence WHERE name = 'app_sessions'")
    suspend fun resetSessionSequence()

    /**
     * Atomically deletes all sessions and resets the ID sequence.
     * This transaction guarantees that no new data is inserted between strict deletion and reset.
     */
    @Transaction
    suspend fun clearAllSessionsAndReset() {
        deleteAllSessions()
        resetSessionSequence()
    }

    @Query("DELETE FROM sqlite_sequence")
    suspend fun resetAllSequences()

    /**
     * Clears all PROCESSED data (AppSessions, DailyStats) but KEEPS Raw Data.
     * Resets processing cursors so everything is re-calculated from scratch.
     */
    @Transaction
    suspend fun clearProcessedDataAndReset() {
        deleteAllSessions()
        deleteAllDailyStats()
        resetSessionSequence()
        
        // Reset Processing Cursors
        updateState(SystemState("last_processed_app_id", "0"))
        updateState(SystemState("last_processed_screen_id", "0"))
        updateState(SystemState("last_processed_offline_id", "0"))
        updateState(SystemState("last_offline_check_id", "0"))
    }
}
