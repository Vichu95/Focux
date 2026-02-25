package com.focux.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

/**
 * specialized DAO for database maintenance/admin operations.
 * Handles "Nuclear" resets and cleanup logic.
 */
@Dao
interface MaintenanceDao {

    @Query("DELETE FROM raw_data")
    suspend fun clearRawData()

    @Query("DELETE FROM app_sessions")
    suspend fun clearAppSessions()
    
    @Query("DELETE FROM app_sessions WHERE startTime < :timestampThreshold")
    suspend fun clearOldAppSessions(timestampThreshold: Long)

    @Query("DELETE FROM daily_stats")
    suspend fun clearDailyStats()
    
    @Query("DELETE FROM daily_stats WHERE date < :dateThreshold")
    suspend fun clearOldDailyStats(dateThreshold: String)
    
    @Query("DELETE FROM raw_data WHERE timestamp < :timestampThreshold")
    suspend fun clearOldRawData(timestampThreshold: Long)
    
    @Query("DELETE FROM app_info")
    suspend fun clearAppInfo()
    
    @Query("DELETE FROM system_state")
    suspend fun clearSystemState()

    /**
     * Resets the internal SQLite sequence table.
     * This is critical for resetting AUTOINCREMENT counters (like AppSession ID).
     */
    @Query("DELETE FROM sqlite_sequence")
    suspend fun resetSqliteSequence()

    /**
     * Performs a complete wipe of all user data and resets all ID counters.
     * This transaction guarantees an atomic "Fresh Start".
     */
    @Transaction
    suspend fun clearAllAndReset() {
        clearRawData()
        clearAppSessions()
        clearDailyStats()
        clearAppInfo()
        clearSystemState()
        
        // The magic wand: Reset all auto-increment counters to 1
        resetSqliteSequence()
    }
}
