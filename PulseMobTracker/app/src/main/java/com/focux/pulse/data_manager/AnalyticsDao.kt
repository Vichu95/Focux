package com.focux.pulse.data_manager

import androidx.room.*

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
     * Updates or inserts a state key-value pair.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateState(state: SystemState)

    // --- App Sessions ---

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

    // --- Daily Stats ---

    /**
     * Gets the earliest date with stats data.
     */
    @Query("SELECT MIN(date) FROM daily_stats")
    suspend fun getEarliestDate(): String?

    /**
     * Retrieves the aggregated stats for a specific day.
     * @param date Format: "YYYY-MM-DD"
     */
    @Query("SELECT * FROM daily_stats WHERE date = :date")
    suspend fun getDailyStats(date: String): DailyStats?

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
}
