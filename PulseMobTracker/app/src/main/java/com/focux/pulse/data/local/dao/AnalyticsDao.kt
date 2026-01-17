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
}
